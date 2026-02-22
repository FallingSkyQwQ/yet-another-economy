package com.yae.api.core;

import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.ConfigurationManager;
import com.yae.api.core.event.YAEEvent;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface for the YetAnotherEconomy plugin.
 * Provides access to all services, configuration, event bus, and core functionality.
 */
public interface YAECore {
    
    /**
     * Gets the plugin instance name.
     * @return the plugin name
     */
    @NotNull
    String getPluginName();
    
    /**
     * Gets the plugin version.
     * @return the plugin version
     */
    @NotNull
    String getPluginVersion();
    
    // Service Management
    /**
     * Registers a service.
     * @param service the service to register
     * @return true if registration was successful
     */
    boolean registerService(@NotNull Service service);
    
    /**
     * Unregisters a service.
     * @param serviceType the type of service to unregister
     * @return true if unregistration was successful
     */
    boolean unregisterService(@NotNull ServiceType serviceType);
    
    /**
     * Gets a registered service by type.
     * @param serviceType the type of service to get
     * @param <T> the service type
     * @return the service, or null if not found
     */
    @Nullable
    <T extends Service> T getService(@NotNull ServiceType serviceType);
    
    /**
     * Gets all registered services.
     * @return collection of all registered services
     */
    @NotNull
    Set<Service> getAllServices();
    
    /**
     * Gets all services of a specific type.
     * @param typeClass the service interface class
     * @param <T> the service type
     * @return collection of services of the specified type
     */
    @NotNull
    <T extends Service> Set<T> getServicesByType(@NotNull Class<T> typeClass);
    
    /**
     * Checks if a service is registered.
     * @param serviceType the type of service to check
     * @return true if the service is registered
     */
    boolean isServiceRegistered(@NotNull ServiceType serviceType);
    
    /**
     * Checks if a service is enabled.
     * @param serviceType the type of service to check
     * @return true if the service is enabled
     */
    boolean isServiceEnabled(@NotNull ServiceType serviceType);
    
    /**
     * Enables a service.
     * @param serviceType the type of service to enable
     * @return true if enabling was successful
     */
    boolean enableService(@NotNull ServiceType serviceType);
    
    /**
     * Disables a service.
     * @param serviceType the type of service to disable
     * @return true if disabling was successful
     */
    boolean disableService(@NotNull ServiceType serviceType);
    
    /**
     * Reloads a service.
     * @param serviceType the type of service to reload
     * @return true if reload was successful
     */
    boolean reloadService(@NotNull ServiceType serviceType);
    
    /**
     * Reloads all services.
     * @return true if reload was successful
     */
    boolean reloadAllServices();
    
    // Configuration Management
    /**
     * Gets the configuration manager.
     * @return the configuration manager
     */
    @NotNull
    ConfigurationManager getConfigurationManager();
    
    /**
     * Gets the main configuration.
     * @return the main configuration
     */
    @NotNull
    Configuration getMainConfiguration();
    
    /**
     * Saves the plugin configuration.
     * @return true if save was successful
     */
    boolean saveConfiguration();
    
    /**
     * Reloads the plugin configuration.
     * @return true if reload was successful
     */
    boolean reloadConfiguration();
    
    /**
     * Gets the main plugin configuration (legacy method).
     * @return the main configuration
     * @deprecated Use getMainConfiguration() instead
     */
    @NotNull
    @Deprecated
    ServiceConfig getConfiguration();
    
    /**
     * Sets configuration for a specific service.
     * @param serviceType the type of service
     * @param config the service configuration
     */
    void setServiceConfiguration(@NotNull ServiceType serviceType, @Nullable ServiceConfig config);

    /**
     * Gets configuration for a specific service.
     * @param serviceType the type of service
     * @return the service configuration, may be null
     */
    @Nullable
    ServiceConfig getServiceConfiguration(@NotNull ServiceType serviceType);
    
    // Event Management
    /**
     * Registers an event listener.
     * @param listener the event listener
     */
    void addEventListener(@NotNull Object listener);
    
    /**
     * Unregisters an event listener.
     * @param listener the event listener
     */
    void removeEventListener(@NotNull Object listener);
    
    /**
     * Emits an event asynchronously.
     * @param event the event to emit
     * @return a future that completes when the event processing is done
     */
    @NotNull
    CompletableFuture<Void> emitEvent(@NotNull YAEEvent event);
    
    /**
     * Emits an event synchronously.
     * @param event the event to emit
     */
    void emitEventSync(@NotNull YAEEvent event);
    
    /**
     * Adds an event handler for a specific event type.
     * @param eventType the event type to handle
     * @param handler the event handler
     */
    void addEventHandler(@NotNull String eventType, @NotNull Consumer<YAEEvent> handler);
    
    /**
     * Removes an event handler.
     * @param eventType the event type
     * @param handler the event handler
     */
    void removeEventHandler(@NotNull String eventType, @NotNull Consumer<YAEEvent> handler);
    
    // Health and Diagnostics
    /**
     * Gets the overall health status of the plugin.
     * @return true if the plugin is healthy
     */
    boolean isHealthy();
    
    /**
     * Gets diagnostic information about the plugin.
     * @return diagnostic information
     */
    @NotNull
    String getDiagnosticInfo();
    
    /**
     * Gets diagnostic information for a specific service.
     * @param serviceType the type of service
     * @return diagnostic information for the service
     */
    @NotNull
    String getServiceDiagnosticInfo(@NotNull ServiceType serviceType);
    
    /**
     * Performs maintenance operations.
     * @return true if maintenance was successful
     */
    boolean performMaintenance();
    
    // Lifecycle Management
    /**
     * Called when the plugin is being initialized.
     * @return true if initialization was successful
     */
    boolean initialize();
    
    /**
     * Called when the plugin is being started.
     * @return true if startup was successful
     */
    boolean startup();
    
    /**
     * Called when the plugin is being stopped.
     */
    void shutdown();
    
    /**
     * Called when the plugin is being reloaded.
     * @return true if reload was successful
     */
    boolean reload();
    
    /**
     * Gets the current plugin state.
     * @return the current state
     */
    @NotNull
    PluginState getPluginState();
    
    /**
     * Plugin lifecycle states
     */
    enum PluginState {
        INITIALIZING("Initializing"),
        READY("Ready"),
        STARTING("Starting"),
        RUNNING("Running"),
        STOPPING("Stopping"),
        STOPPED("Stopped"),
        FAILED("Failed"),
        RELOADING("Reloading");
        
        private final String description;
        
        PluginState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isRunning() {
            return this == RUNNING || this == RELOADING;
        }
        
        public boolean isStopped() {
            return this == STOPPED || this == FAILED;
        }
    }
    
    // Utility Methods
    /**
     * Gets system information.
     * @return system information
     */
    @NotNull
    String getSystemInfo();
    
    /**
     * Gets plugin statistics.
     * @return plugin statistics
     */
    @NotNull
    String getStats();
    
    /**
     * Checks if debug mode is enabled.
     * @return true if debug mode is enabled
     */
    boolean isDebugMode();
    
    /**
     * Sets debug mode.
     * @param debug whether to enable debug mode
     */
    void setDebugMode(boolean debug);
    
    /**
     * Logs a debug message.
     * @param message the debug message
     */
    void debug(@NotNull String message);
    
    /**
     * Logs an info message.
     * @param message the info message
     */
    void info(@NotNull String message);
    
    /**
     * Logs a warning message.
     * @param message the warning message
     */
    void warn(@NotNull String message);
    
    /**
     * Logs an error message.
     * @param message the error message
     */
    void error(@NotNull String message);
    
    /**
     * Logs an error message with exception.
     * @param message the error message
     * @param throwable the exception
     */
    void error(@NotNull String message, @NotNull Throwable throwable);

    /**
     * Gets the logger for this plugin.
     * @return the plugin logger
     */
    @NotNull
    java.util.logging.Logger getLogger();

    /**
     * Gets the data folder for this plugin.
     * @return the plugin data folder
     */
    @NotNull
    java.io.File getDataFolder();

    /**
     * Gets a resource from the plugin's jar file.
     * @param filename the resource filename
     * @return the input stream for the resource, or null if not found
     */
    @Nullable
    java.io.InputStream getResource(@NotNull String filename);
}
