package com.yae.api.core.event;

import com.yae.api.core.Service;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a service is reloaded.
 */
public class ServiceReloadedEvent extends ServiceEvent {
    
    private final boolean success;
    
    public ServiceReloadedEvent(@NotNull Service service, boolean success) {
        super("service-reloaded", service, "Service " + service.getName() + (success ? " reloaded successfully" : " failed to reload"));
        this.success = success;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    @Override
    @NotNull
    public EventSeverity getSeverity() {
        return success ? EventSeverity.INFO : EventSeverity.ERROR;
    }
}
