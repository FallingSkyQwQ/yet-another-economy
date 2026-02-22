package com.yae.api.core;

import com.yae.api.core.ServiceType;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.ConfigurationManager;
import com.yae.api.core.event.YAEEvent;
import com.yae.api.core.event.ServiceRegisteredEvent;
import com.yae.api.core.event.ServiceUnregisteredEvent;
import com.yae.api.core.event.ServiceStateChangedEvent;
import com.yae.api.core.event.ServiceReloadedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base implementation of the YAECore interface.
 * Provides common functionality that can be shared by all implementations.
 */
public abstract class YAECoreBase extends org.bukkit.plugin.java.JavaPlugin implements YAECore, Listener {
    
    protected final Logger logger;
    protected final Map<ServiceType, Service> services = new ConcurrentHashMap<>();
    protected final Map<String, Set<Consumer<YAEEvent>>> eventHandlers = new ConcurrentHashMap<>();
    protected final Set<Object> eventListeners = new CopyOnWriteArraySet<>();
    
    protected PluginState state = PluginState.INITIALIZING;
    protected boolean debugMode = false;
    protected ServiceConfig configuration;
    protected ConfigurationManager configurationManager;
    
    public YAECoreBase() {
        this.logger = Logger.getLogger(getPluginName());
        this.configuration = ServiceConfig.empty();
    }
    
    @Override
    public boolean registerService(@NotNull Service service) {
        try {
            // Check for existing service
            if (services.containsKey(service.getType())) {
                warn("Service " + service.getType() + " is already registered. Unregistering first...");
                unregisterService(service.getType());
            }
            
            // Initialize service
            boolean initialized = service.initialize();
            if (!initialized) {
                error("Failed to initialize service: " + service.getName());
                return false;
            }
            
            // Register service
            services.put(service.getType(), service);
            info("Service " + service.getName() + " registered successfully");
            
            // Fire service registered event
            emitEvent(new ServiceRegisteredEvent(service));
            
            return true;
        } catch (Exception e) {
            error("Failed to register service: " + service.getName(), e);
            return false;
        }
    }
    
    @Override
    public boolean unregisterService(@NotNull ServiceType serviceType) {
        Service service = services.remove(serviceType);
        if (service != null) {
            try {
                service.shutdown();
                info("Service " + service.getName() + " unregistered");
                emitEvent(new ServiceUnregisteredEvent(service));
                return true;
            } catch (Exception e) {
                error("Failed to unregister service: " + service.getName(), e);
                return false;
            }
        }
        return false;
    }
    
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends Service> T getService(@NotNull ServiceType serviceType) {
        return (T) services.get(serviceType);
    }
    
    @Override
    @NotNull
    public Set<Service> getAllServices() {
        return new HashSet<>(services.values());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Service> Set<T> getServicesByType(@NotNull Class<T> typeClass) {
        Set<T> result = new HashSet<>();
        for (Service service : services.values()) {
            if (typeClass.isAssignableFrom(service.getClass())) {
                result.add((T) service);
            }
        }
        return result;
    }
    
    @Override
    public boolean isServiceRegistered(@NotNull ServiceType serviceType) {
        return services.containsKey(serviceType);
    }
    
    @Override
    public boolean isServiceEnabled(@NotNull ServiceType serviceType) {
        Service service = services.get(serviceType);
        return service != null && service.isEnabled();
    }
    
    @Override
    public boolean enableService(@NotNull ServiceType serviceType) {
        Service service = services.get(serviceType);
        if (service != null) {
            service.setEnabled(true);
            emitEvent(new ServiceStateChangedEvent(service, true));
            return true;
        }
        return false;
    }
    
    @Override
    public boolean disableService(@NotNull ServiceType serviceType) {
        Service service = services.get(serviceType);
        if (service != null) {
            service.setEnabled(false);
            emitEvent(new ServiceStateChangedEvent(service, false));
            return true;
        }
        return false;
    }
    
    @Override
    public boolean reloadService(@NotNull ServiceType serviceType) {
        Service service = services.get(serviceType);
        if (service != null) {
            boolean success = service.reload();
            emitEvent(new ServiceReloadedEvent(service, success));
            return success;
        }
        return false;
    }
    
    @Override
    public boolean reloadAllServices() {
        boolean allSuccess = true;
        List<Service> sortedServices = new ArrayList<>(services.values());
        sortedServices.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        
        for (Service service : sortedServices) {
            boolean success = service.reload();
            emitEvent(new ServiceReloadedEvent(service, success));
            if (!success) {
                allSuccess = false;
                error("Failed to reload service: " + service.getName());
            }
        }
        return allSuccess;
    }
    
    @Override
    public boolean isHealthy() {
        if (state != PluginState.RUNNING) {
            return false;
        }
        
        for (Service service : services.values()) {
            if (!service.isHealthy()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    @NotNull
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Plugin State: ").append(state).append("\n");
        info.append("Debug Mode: ").append(debugMode).append("\n");
        info.append("Services Registered: ").append(services.size()).append("\n");
        
        for (Map.Entry<ServiceType, Service> entry : services.entrySet()) {
            Service service = entry.getValue();
            info.append("  ").append(entry.getKey()).append(": ")
                .append(service.getStatus()).append(" (").append(service.isHealthy() ? "HEALTHY" : "UNHEALTHY").append(")")
                .append("\n");
        }
        
        return info.toString();
    }
    
    @Override
    @NotNull
    public String getServiceDiagnosticInfo(@NotNull ServiceType serviceType) {
        Service service = services.get(serviceType);
        if (service != null) {
            return service.getDiagnosticInfo();
        }
        return "Service not found: " + serviceType;
    }
    
    @Override
    public boolean performMaintenance() {
        debug("Performing maintenance...");
        boolean allHealthy = true;
        
        for (Service service : services.values()) {
            if (!service.isHealthy()) {
                warn("Service " + service.getName() + " is not healthy during maintenance");
                allHealthy = false;
            }
        }
        
        return allHealthy;
    }
    
    @Override
    @NotNull
    public CompletableFuture<Void> emitEvent(@NotNull YAEEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Process generic event handlers
                Set<Consumer<YAEEvent>> handlers = eventHandlers.get(event.getEventType());
                if (handlers != null) {
                    for (Consumer<YAEEvent> handler : handlers) {
                        try {
                            handler.accept(event);
                        } catch (Exception e) {
                            error("Error processing event handler for " + event.getEventType(), e);
                        }
                    }
                }
                
                // Post to Bukkit's event system
                Bukkit.getPluginManager().callEvent(event);
                
            } catch (Exception e) {
                error("Error emitting event: " + event.getEventType(), e);
            }
        });
    }
    
    @Override
    public void emitEventSync(@NotNull YAEEvent event) {
        try {
            Set<Consumer<YAEEvent>> handlers = eventHandlers.get(event.getEventType());
            if (handlers != null) {
                for (Consumer<YAEEvent> handler : handlers) {
                    try {
                        handler.accept(event);
                    } catch (Exception e) {
                        error("Error processing event handler for " + event.getEventType(), e);
                    }
                }
            }
            
            Bukkit.getPluginManager().callEvent(event);
            
        } catch (Exception e) {
            error("Error emitting synchronous event: " + event.getEventType(), e);
        }
    }
    
    @Override
    public void addEventHandler(@NotNull String eventType, @NotNull Consumer<YAEEvent> handler) {
        eventHandlers.computeIfAbsent(eventType, k -> new CopyOnWriteArraySet<>()).add(handler);
    }
    
    @Override
    public void removeEventHandler(@NotNull String eventType, @NotNull Consumer<YAEEvent> handler) {
        Set<Consumer<YAEEvent>> handlers = eventHandlers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }
    
    @Override
    public void addEventListener(@NotNull Object listener) {
        eventListeners.add(listener);
        Bukkit.getPluginManager().registerEvents((Listener) listener, this);
    }
    
    @Override
    public void removeEventListener(@NotNull Object listener) {
        eventListeners.remove(listener);
        HandlerList.unregisterAll((org.bukkit.event.Listener) this);
        // Re-register remaining listeners
        for (Object remainingListener : eventListeners) {
            Bukkit.getPluginManager().registerEvents((Listener) remainingListener, this);
        }
    }
    
    @Override
    @NotNull
    public String getSystemInfo() {
        return String.format("Java: %s, OS: %s, Cores: %d", 
                           System.getProperty("java.version"),
                           System.getProperty("os.name"),
                           Runtime.getRuntime().availableProcessors());
    }
    
    @Override
    @NotNull
    public String getStats() {
        return String.format("Services: %d, Event Handlers: %d, Memory Used: %d MB",
                           services.size(),
                           eventHandlers.size(),
                           (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);
    }
    
    @Override
    public boolean isDebugMode() {
        return debugMode;
    }
    
    @Override
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        debug("Debug mode " + (debug ? "enabled" : "disabled"));
    }

    @Override
    @NotNull
    public ConfigurationManager getConfigurationManager() {
        if (configurationManager == null) {
            throw new IllegalStateException("Configuration manager not initialized");
        }
        return configurationManager;
    }
    
    @Override
    @NotNull
    public Configuration getMainConfiguration() {
        if (configurationManager == null) {
            throw new IllegalStateException("Configuration manager not initialized");
        }
        return configurationManager.getConfiguration();
    }
    
    @Override
    public void debug(@NotNull String message) {
        if (debugMode) {
            logger.log(Level.INFO, "[DEBUG] " + message);
        }
    }
    
    @Override
    public void info(@NotNull String message) {
        logger.info(message);
    }
    
    @Override
    public void warn(@NotNull String message) {
        logger.warning(message);
    }
    
    @Override
    public void error(@NotNull String message) {
        logger.severe(message);
    }
    
    @Override
    public void error(@NotNull String message, @NotNull Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
    
    @Override
    @NotNull
    public java.util.logging.Logger getLogger() {
        return logger;
    }
    
    @Override
    @NotNull
    public PluginState getPluginState() {
        return state;
    }
    
    /**
     * Sets the plugin state.
     * @param state the new state
     */
    protected void setPluginState(@NotNull PluginState state) {
        this.state = state;
        info("Plugin state changed to: " + state);
    }
    
    @Override
    @SuppressWarnings("deprecation")
    @Nullable
    public ServiceConfig getConfiguration() {
        return this.configuration;
    }
    
    @Override
    @Nullable
    public ServiceConfig getServiceConfiguration(@NotNull ServiceType serviceType) {
        Service service = services.get(serviceType);
        return service != null ? service.getConfig() : null;
    }

    @Override
    public boolean saveConfiguration() {
        if (configurationManager != null) {
            // TODO: Implement save functionality in ConfigurationManager
            return true; // Assuming success for now
        }
        return false;
    }
}
