package com.yae.api.core.config;

import com.yae.api.core.YAECore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main configuration manager that orchestrates all configuration-related functionality.
 * Provides hot-reload capabilities, thread-safe access, and centralized configuration management.
 */
public final class ConfigurationManager {
    
    private static final String MAIN_CONFIG_FILE = "config.yml";
    private static final String LANGUAGE_FILE = "lang.yml";
    
    private final YAECore plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final ScheduledExecutorService scheduler;
    private final ReadWriteLock lock;
    
    private Configuration mainConfiguration;
    private boolean hotReloadEnabled;
    private long hotReloadInterval;
    private WatchService watchService;
    private Thread watchThread;
    
    public ConfigurationManager(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getLogger();
        this.configManager = new ConfigManager(plugin);
        this.languageManager = new LanguageManager(plugin, configManager);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "YAE-Config-Reloader");
            thread.setDaemon(true);
            return thread;
        });
        this.lock = new ReentrantReadWriteLock();
        this.hotReloadEnabled = false;
        this.hotReloadInterval = 5000; // 5 seconds default
    }
    
    /**
     * Initialize the configuration manager
     * @throws ConfigurationInitializationException if initialization fails
     */
    public void initialize() throws ConfigurationInitializationException {
        logger.log(Level.INFO, "Initializing configuration manager...");
        
        try {
            // Load main configuration
            ConfigFile mainConfigFile = configManager.loadConfig(MAIN_CONFIG_FILE);
            this.mainConfiguration = new Configuration(mainConfigFile, logger);
            
            // Initialize language manager
            languageManager.initialize();
            
            // Setup configuration reload listener
            configManager.registerReloadListener(MAIN_CONFIG_FILE, this::onMainConfigReload);
            configManager.registerReloadListener(LANGUAGE_FILE, this::onLanguageConfigReload);
            
            // Setup hot reload if enabled
            setupHotReload();
            
            logger.log(Level.INFO, "Configuration manager initialized successfully");
            
        } catch (Exception e) {
            throw new ConfigurationInitializationException("Failed to initialize configuration manager", e);
        }
    }
    
    /**
     * Shutdown the configuration manager
     */
    public void shutdown() {
        logger.log(Level.INFO, "Shutting down configuration manager...");
        
        try {
            // Stop hot reload service
            if (hotReloadEnabled) {
                stopHotReload();
            }
            
            // Shutdown scheduler
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // Unregister listeners
            configManager.unregisterReloadListener(MAIN_CONFIG_FILE);
            configManager.unregisterReloadListener(LANGUAGE_FILE);
            
            logger.log(Level.INFO, "Configuration manager shut down");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error shutting down configuration manager", e);
        }
    }
    
    /**
     * Reload all configurations
     * @return true if reload was successful
     */
    public boolean reload() {
        lock.writeLock().lock();
        try {
            logger.log(Level.INFO, "Reloading all configurations...");
            
            // Reload configurations
            configManager.reloadConfig(MAIN_CONFIG_FILE);
            configManager.reloadConfig(LANGUAGE_FILE);
            
            // Update configuration objects
            ConfigFile mainConfigFile = configManager.getConfig(MAIN_CONFIG_FILE);
            if (mainConfigFile != null) {
                mainConfiguration.reload(mainConfigFile);
            }
            
            logger.log(Level.INFO, "All configurations reloaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to reload configurations", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Enable hot reload functionality
     * @param interval the interval to check for file changes (in milliseconds)
     */
    public void enableHotReload(long interval) {
        lock.writeLock().lock();
        try {
            if (hotReloadEnabled) {
                logger.log(Level.WARNING, "Hot reload is already enabled");
                return;
            }
            
            this.hotReloadEnabled = true;
            this.hotReloadInterval = interval;
            
            setupHotReload();
            
            logger.log(Level.INFO, "Hot reload enabled with interval: {0}ms", interval);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Disable hot reload functionality
     */
    public void disableHotReload() {
        lock.writeLock().lock();
        try {
            if (!hotReloadEnabled) {
                logger.log(Level.WARNING, "Hot reload is already disabled");
                return;
            }
            
            stopHotReload();
            hotReloadEnabled = false;
            
            logger.log(Level.INFO, "Hot reload disabled");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if hot reload is enabled
     * @return true if hot reload is enabled
     */
    public boolean isHotReloadEnabled() {
        lock.readLock().lock();
        try {
            return hotReloadEnabled;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Getter methods
    
    @NotNull
    public Configuration getConfiguration() {
        lock.readLock().lock();
        try {
            return mainConfiguration;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @NotNull
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    @NotNull
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    // Internal event handlers
    
    private void onMainConfigReload(@NotNull ConfigFile config) throws Exception {
        logger.log(Level.INFO, "Main configuration file reloaded");
        
        // Validate and update main configuration
        Configuration newConfiguration = new Configuration(config, logger);
        if (newConfiguration.validate()) {
            lock.writeLock().lock();
            try {
                this.mainConfiguration = newConfiguration;
                logger.log(Level.INFO, "Main configuration updated successfully");
            } finally {
                lock.writeLock().unlock();
            }
        } else {
            logger.log(Level.SEVERE, "Main configuration validation failed after reload");
        }
    }
    
    private void onLanguageConfigReload(@NotNull ConfigFile config) throws Exception {
        logger.log(Level.INFO, "Language configuration file reloaded");
        
        languageManager.reload();
    }
    
    // Hot reload implementation
    
    private void setupHotReload() {
        if (!hotReloadEnabled) {
            return;
        }
        
        try {
            // Create watch service for file system monitoring
            watchService = FileSystems.getDefault().newWatchService();
            
            // Register plugin data folder for watching
            Path dataFolder = plugin.getDataFolder().toPath();
            if (Files.exists(dataFolder)) {
                dataFolder.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            }
            
            // Start watch thread
            watchThread = new Thread(this::watchForChanges, "YAE-Config-Watcher");
            watchThread.setDaemon(true);
            watchThread.start();
            
            // Schedule periodic full reload as backup
            scheduler.scheduleWithFixedDelay(this::periodicReloadCheck, hotReloadInterval, hotReloadInterval, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to setup hot reload", e);
            hotReloadEnabled = false;
        }
    }
    
    private void stopHotReload() {
        try {
            if (watchService != null) {
                watchService.close();
                watchService = null;
            }
            
            if (watchThread != null) {
                watchThread.interrupt();
                watchThread = null;
            }
            
            scheduler.shutdownNow();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error stopping hot reload", e);
        }
    }
    
    private void watchForChanges() {
        while (hotReloadEnabled && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    
                    String fileName = filename.toString();
                    
                    if (MAIN_CONFIG_FILE.equals(fileName) || LANGUAGE_FILE.equals(fileName)) {
                        logger.log(Level.INFO, "Configuration file changed: {0}", fileName);
                        
                        // Small delay to ensure file write is complete
                        Thread.sleep(500);
                        
                        try {
                            if (MAIN_CONFIG_FILE.equals(fileName)) {
                                configManager.reloadConfig(fileName);
                            } else if (LANGUAGE_FILE.equals(fileName)) {
                                configManager.reloadConfig(fileName);
                            }
                        } catch (ConfigManager.ConfigLoadException e) {
                            logger.log(Level.SEVERE, "Failed to reload changed configuration file: " + fileName, e);
                        }
                    }
                }
                
                key.reset();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in watch service", e);
                break;
            }
        }
    }
    
    private void periodicReloadCheck() {
        if (!hotReloadEnabled) {
            return;
        }
        
        try {
            // Check if files have been modified since last load
            java.nio.file.Path configPath = plugin.getDataFolder().toPath().resolve(MAIN_CONFIG_FILE);
            java.nio.file.Path langPath = plugin.getDataFolder().toPath().resolve(LANGUAGE_FILE);
            
            long currentTime = System.currentTimeMillis();
            
            if (Files.exists(configPath)) {
                long lastModified = Files.getLastModifiedTime(configPath).toMillis();
                ConfigFile currentConfig = configManager.getConfig(MAIN_CONFIG_FILE);
                
                if (currentConfig != null && lastModified > System.currentTimeMillis() - hotReloadInterval * 2) {
                    logger.log(Level.FINE, "Triggering hot reload for main configuration");
                    configManager.reloadConfig(MAIN_CONFIG_FILE);
                }
            }
            
            if (Files.exists(langPath)) {
                long lastModified = Files.getLastModifiedTime(langPath).toMillis();
                ConfigFile currentConfig = configManager.getConfig(LANGUAGE_FILE);
                
                if (currentConfig != null && lastModified > System.currentTimeMillis() - hotReloadInterval * 2) {
                    logger.log(Level.FINE, "Triggering hot reload for language configuration");
                    configManager.reloadConfig(LANGUAGE_FILE);
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in periodic reload check", e);
        }
    }
    
    /**
     * Exception thrown when configuration manager initialization fails
     */
    public static class ConfigurationInitializationException extends Exception {
        public ConfigurationInitializationException(String message) {
            super(message);
        }
        
        public ConfigurationInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
