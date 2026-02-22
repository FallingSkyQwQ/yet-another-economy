package com.yae.api.core.event;

import com.yae.api.core.Service;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a service is initialized.
 */
public class ServiceInitializedEvent extends ServiceEvent {
    
    private final boolean success;
    
    public ServiceInitializedEvent(@NotNull Service service, boolean success) {
        super("service-initialized", service, "Service " + service.getName() + (success ? " initialized successfully" : " failed to initialize"));
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
