package com.yae.api.core.config;

import com.yae.api.core.YAECore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration manager responsible for loading, saving, and managing configuration files.
 * Provides thread-safe operations and hot-reload functionality.
 */
public final class ConfigManager {
    
    private final YAECore plugin;
    private final Logger logger;
    private final Map<String, ConfigFile> configs;
    private final ReadWriteLock lock;
    private final Map<String, ConfigReloadListener> reloadListeners;
    private final Yaml yaml;
    
    public ConfigManager(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getLogger();
        this.configs = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.reloadListeners = new ConcurrentHashMap<>();
        
        // Configure YAML dumper for better formatting
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        this.yaml = new Yaml(options);
    }
    
    /**
     * Load a configuration file from the plugin's data folder
     * @param fileName the name of the configuration file
     * @param saveDefault whether to save the default configuration if it doesn't exist
     * @return the loaded configuration file
     * @throws ConfigLoadException if the configuration could not be loaded
     */
    @NotNull
    public ConfigFile loadConfig(@NotNull String fileName, boolean saveDefault) throws ConfigLoadException {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        
        lock.writeLock().lock();
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            
            // Save default configuration if it doesn't exist and requested
            if (!configFile.exists() && saveDefault) {
                saveDefaultConfig(fileName);
            }
            
            if (!configFile.exists()) {
                throw new ConfigLoadException("Configuration file not found: " + fileName);
            }
            
            // Load the configuration
            Map<String, Object> data;
            try (FileReader reader = new FileReader(configFile)) {
                data = yaml.load(reader);
            } catch (Exception e) {
                throw new ConfigLoadException("Failed to parse configuration file: " + fileName, e);
            }
            
            if (data == null) {
                data = new HashMap<>();
            }
            
            ConfigFile configFileObj = new ConfigFile(fileName, data, configFile);
            configs.put(fileName, configFileObj);
            
            logger.log(Level.INFO, "Loaded configuration file: {0}", fileName);
            return configFileObj;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Load a configuration file from the plugin's data folder with default save enabled
     * @param fileName the name of the configuration file
     * @return the loaded configuration file
     * @throws ConfigLoadException if the configuration could not be loaded
     */
    @NotNull
    public ConfigFile loadConfig(@NotNull String fileName) throws ConfigLoadException {
        return loadConfig(fileName, true);
    }
    
    /**
     * Reload a specific configuration file
     * @param fileName the name of the configuration file to reload
     * @throws ConfigLoadException if the configuration could not be reloaded
     */
    public void reloadConfig(@NotNull String fileName) throws ConfigLoadException {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        
        lock.writeLock().lock();
        try {
            ConfigFile config = configs.get(fileName);
            if (config == null) {
                throw new ConfigLoadException("Configuration file not loaded: " + fileName);
            }
            
            // Reload the configuration
            Map<String, Object> data;
            try (FileReader reader = new FileReader(config.getFile())) {
                data = yaml.load(reader);
            } catch (Exception e) {
                throw new ConfigLoadException("Failed to parse configuration file: " + fileName, e);
            }
            
            if (data == null) {
                data = new HashMap<>();
            }
            
            config.updateData(data);
            
            // Notify reload listeners
            ConfigReloadListener listener = reloadListeners.get(fileName);
            if (listener != null) {
                try {
                    listener.onConfigReload(config);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in reload listener for " + fileName, e);
                }
            }
            
            logger.log(Level.INFO, "Reloaded configuration file: {0}", fileName);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Reload all loaded configuration files
     * @return number of files successfully reloaded
     */
    public int reloadAllConfigs() {
        lock.readLock().lock();
        try {
            Set<String> fileNames = new HashSet<>(configs.keySet());
            int successCount = 0;
            
            for (String fileName : fileNames) {
                try {
                    reloadConfig(fileName);
                    successCount++;
                } catch (ConfigLoadException e) {
                    logger.log(Level.WARNING, "Failed to reload configuration: " + fileName, e);
                }
            }
            
            logger.log(Level.INFO, "Reloaded {0} out of {1} configuration files", 
                      new Object[]{successCount, fileNames.size()});
            return successCount;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Save a configuration file
     * @param fileName the name of the configuration file to save
     * @throws ConfigSaveException if the configuration could not be saved
     */
    public void saveConfig(@NotNull String fileName) throws ConfigSaveException {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        
        lock.readLock().lock();
        try {
            ConfigFile config = configs.get(fileName);
            if (config == null) {
                throw new ConfigSaveException("Configuration file not loaded: " + fileName);
            }
            
            saveConfigFile(config);
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Save all loaded configuration files
     * @return number of files successfully saved
     */
    public int saveAllConfigs() {
        lock.readLock().lock();
        try {
            int successCount = 0;
            
            for (ConfigFile config : configs.values()) {
                try {
                    saveConfigFile(config);
                    successCount++;
                } catch (ConfigSaveException e) {
                    logger.log(Level.WARNING, "Failed to save configuration: " + config.getFileName(), e);
                }
            }
            
            logger.log(Level.INFO, "Saved {0} configuration files", successCount);
            return successCount;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get a loaded configuration file
     * @param fileName the name of the configuration file
     * @return the configuration file, or null if not loaded
     */
    @Nullable
    public ConfigFile getConfig(@NotNull String fileName) {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        
        lock.readLock().lock();
        try {
            return configs.get(fileName);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Register a reload listener for a specific configuration file
     * @param fileName the name of the configuration file
     * @param listener the reload listener
     */
    public void registerReloadListener(@NotNull String fileName, @NotNull ConfigReloadListener listener) {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        
        lock.writeLock().lock();
        try {
            reloadListeners.put(fileName, listener);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Unregister a reload listener
     * @param fileName the name of the configuration file
     */
    public void unregisterReloadListener(@NotNull String fileName) {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        
        lock.writeLock().lock();
        try {
            reloadListeners.remove(fileName);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if a configuration file is loaded
     * @param fileName the name of the configuration file
     * @return true if the file is loaded
     */
    public boolean isConfigLoaded(@NotNull String fileName) {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        
        lock.readLock().lock();
        try {
            return configs.containsKey(fileName);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all loaded configuration file names
     * @return set of loaded configuration file names
     */
    @NotNull
    public Set<String> getLoadedConfigs() {
        lock.readLock().lock();
        try {
            return new HashSet<>(configs.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Internal methods
    
    private void saveDefaultConfig(@NotNull String fileName) throws ConfigLoadException {
        try (InputStream inputStream = plugin.getResource(fileName)) {
            if (inputStream == null) {
                logger.log(Level.WARNING, "Default configuration not found in resources: {0}", fileName);
                // Create empty file
                File configFile = new File(plugin.getDataFolder(), fileName);
                configFile.getParentFile().mkdirs();
                
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("# Configuration file\n");
                } catch (IOException e) {
                    throw new ConfigLoadException("Failed to create empty configuration file: " + fileName, e);
                }
                return;
            }
            
            File configFile = new File(plugin.getDataFolder(), fileName);
            configFile.getParentFile().mkdirs();
            
            Files.copy(inputStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.log(Level.INFO, "Saved default configuration file: {0}", fileName);
            
        } catch (IOException e) {
            throw new ConfigLoadException("Failed to save default configuration: " + fileName, e);
        }
    }
    
    private void saveConfigFile(@NotNull ConfigFile config) throws ConfigSaveException {
        try (FileWriter writer = new FileWriter(config.getFile())) {
            yaml.dump(config.getData(), writer);
            logger.log(Level.FINE, "Saved configuration file: {0}", config.getFileName());
            
        } catch (IOException e) {
            throw new ConfigSaveException("Failed to save configuration file: " + config.getFileName(), e);
        }
    }
    
    /**
     * Interface for configuration reload listeners
     */
    @FunctionalInterface
    public interface ConfigReloadListener {
        /**
         * Called when a configuration file is reloaded
         * @param config the reloaded configuration file
         */
        void onConfigReload(@NotNull ConfigFile config) throws Exception;
    }
    
    /**
     * Exception thrown when configuration loading fails
     */
    public static class ConfigLoadException extends Exception {
        public ConfigLoadException(String message) {
            super(message);
        }
        
        public ConfigLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown when configuration saving fails
     */
    public static class ConfigSaveException extends Exception {
        public ConfigSaveException(String message) {
            super(message);
        }
        
        public ConfigSaveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
