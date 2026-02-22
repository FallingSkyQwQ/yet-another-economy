package com.yae.api.core.event;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for service-related events.
 */
public abstract class ServiceEvent extends YAEEvent {
    private final Service service;
    
    /**
     * Creates a new service event.
     * @param eventType the type of service event
     * @param service the service involved
     * @param message the event message
     */
    protected ServiceEvent(@NotNull String eventType, @NotNull Service service, @Nullable String message) {
        super(eventType, "service:" + service.getType().getKey(), message);
        this.service = service;
    }
    
    /**
     * Gets the service associated with this event.
     * @return the service
     */
    @NotNull
    public Service getService() {
        return service;
    }
    
    /**
     * Gets the service type.
     * @return the service type
     */
    @NotNull
    public ServiceType getServiceType() {
        return service.getType();
    }
}
