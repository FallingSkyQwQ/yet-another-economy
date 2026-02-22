package com.yae.api.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for all YAE services.
 * Defines the contract that all services must implement.
 */
public interface Service {
    
    /**
     * Gets the unique name of this service.
     * @return the service name
     */
    @NotNull
    String getName();
    
    /**
     * Gets the service type.
     * @return the service type
     */
    @NotNull
    ServiceType getType();
    
    /**
     * Gets the service configuration.
     * @return the service configuration, or null if not configured
     */
    @Nullable
    ServiceConfig getConfig();
    
    /**
     * Sets the service configuration.
     * @param config the service configuration
     */
    void setConfig(@Nullable ServiceConfig config);
    
    /**
     * Initializes the service.
     * Called when the service is first registered.
     * @return true if initialization was successful
     */
    boolean initialize();
    
    /**
     * Reloads the service with new configuration.
     * @return true if reload was successful
     */
    boolean reload();
    
    /**
     * Shuts down the service and releases resources.
     */
    void shutdown();
    
    /**
     * Checks if the service is currently enabled.
     * @return true if the service is enabled
     */
    boolean isEnabled();
    
    /**
     * Sets the enabled state of the service.
     * @param enabled whether the service should be enabled
     */
    void setEnabled(boolean enabled);
    
    /**
     * Gets the service priority.
     * Higher priority services are initialized earlier and shutdown later.
     * @return the service priority, default is 0
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Gets the service version.
     * @return the service version, defaults to "1.0.0"
     */
    @NotNull
    default String getVersion() {
        return "1.0.0";
    }
    
    /**
     * Gets the service description.
     * @return the service description, defaults to empty string
     */
    @NotNull
    default String getDescription() {
        return "";
    }
    
    /**
     * Checks if this service depends on another service.
     * @param serviceType the type of service to check
     * @return true if this service depends on the given service type
     */
    boolean dependsOn(@NotNull ServiceType serviceType);
    
    /**
     * Gets the service status as a human-readable string.
     * @return the service status
     */
    @NotNull
    default String getStatus() {
        if (!isEnabled()) {
            return "DISABLED";
        }
        return "ENABLED";
    }
    
    /**
     * Validates the service configuration.
     * @return true if configuration is valid
     */
    default boolean validateConfig() {
        return true;
    }
    
    /**
     * Gets the service health status.
     * @return true if the service is healthy
     */
    default boolean isHealthy() {
        return isEnabled();
    }
    
    /**
     * Gets diagnostic information about the service.
     * @return diagnostic information, or empty string if not available
     */
    @NotNull
    default String getDiagnosticInfo() {
        return "";
    }
}
