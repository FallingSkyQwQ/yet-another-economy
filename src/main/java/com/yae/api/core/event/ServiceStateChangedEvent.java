package com.yae.api.core.event;

import com.yae.api.core.Service;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a service is enabled or disabled.
 */
public class ServiceStateChangedEvent extends ServiceEvent {
    
    private final boolean enabled;
    
    public ServiceStateChangedEvent(@NotNull Service service, boolean enabled) {
        super("service-state-changed", service, "Service " + service.getName() + " has been " + (enabled ? "enabled" : "disabled"));
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    @NotNull
    public EventSeverity getSeverity() {
        return EventSeverity.INFO;
    }
}
