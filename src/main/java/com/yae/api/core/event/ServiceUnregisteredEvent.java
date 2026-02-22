package com.yae.api.core.event;

import com.yae.api.core.Service;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a service is unregistered.
 */
public class ServiceUnregisteredEvent extends ServiceEvent {
    
    public ServiceUnregisteredEvent(@NotNull Service service) {
        super("service-unregistered", service, "Service " + service.getName() + " has been unregistered");
    }
    
    @Override
    @NotNull
    public EventSeverity getSeverity() {
        return EventSeverity.WARNING;
    }
}
