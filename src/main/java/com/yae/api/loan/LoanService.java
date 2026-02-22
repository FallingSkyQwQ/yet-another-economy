package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal Loan service - stub implementation for basic compilation
 * This is a simplified version that provides basic service infrastructure
 * Full implementation requires extensive database support and credit integration
 */
public class LoanService implements Service {
    
    private ServiceConfig config;
    private boolean enabled = false;
    
    public LoanService() {
    }

    @Override
    public @NotNull String getName() {
        return "Loan Service";
    }

    @Override
    public @NotNull ServiceType getType() {
        return ServiceType.LOAN;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public ServiceConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(@org.jetbrains.annotations.Nullable ServiceConfig config) {
        this.config = config;
    }

    @Override
    public boolean initialize() {
        this.enabled = true;
        return true;
    }

    @Override
    public boolean reload() {
        return true;
    }

    @Override
    public void shutdown() {
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return false;
    }
}
