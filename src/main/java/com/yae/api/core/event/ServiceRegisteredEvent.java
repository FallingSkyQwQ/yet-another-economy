package com.yae.api.core.event;

import com.yae.api.core.Service;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a service is registered.
 */
public class ServiceRegisteredEvent extends ServiceEvent {
    
    public ServiceRegisteredEvent(@NotNull Service service) {
        super("service-registered", service, "Service " + service.getName() + " has been registered");
    }
    
    @Override
    @NotNull
    public EventSeverity getSeverity() {
        return EventSeverity.INFO;
    }
}
