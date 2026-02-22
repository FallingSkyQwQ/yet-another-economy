package com.yae;

import com.yae.api.core.YAECoreBase;
import com.yae.api.core.YAECore.PluginState;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.config.ConfigurationManager;
import com.yae.api.core.config.ConfigurationManager.ConfigurationInitializationException;
import com.yae.api.core.config.Configuration;
import com.yae.api.database.DatabaseService;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;

public final class YetAnotherEconomy extends YAECoreBase {
    
    private static YetAnotherEconomy instance;
    
    /**
     * Gets the singleton instance of YetAnotherEconomy.
     * @return the plugin instance
     */
    public static YetAnotherEconomy getInstance() {
        return instance;
    }
    
    // JavaPlugin lifecycle methods
    @Override
    public void onEnable() {
        instance = this;
        
        info("Starting YetAnotherEconomy v" + getPluginVersion());
        
        // Initialize core
        if (!initialize()) {
            logger.severe("Failed to initialize YetAnotherEconomy! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Startup
        if (!startup()) {
            logger.severe("Failed to start YetAnotherEconomy! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        info("YetAnotherEconomy v" + getPluginVersion() + " has been enabled!");
        setPluginState(PluginState.RUNNING);
    }
    
    @Override
    public void onDisable() {
        info("Disabling YetAnotherEconomy v" + getPluginVersion());
        setPluginState(PluginState.STOPPING);
        
        shutdown();
        
        instance = null;
        info("YetAnotherEconomy has been disabled!");
        setPluginState(PluginState.STOPPED);
    }
    
    // YAECore interface implementation
    @Override
    public String getPluginName() {
        return "YetAnotherEconomy";
    }
    
    @Override
    public String getPluginVersion() {
        return "1.0.0";
    }
    
    @Override
    public boolean initialize() {
        debug("Initializing YetAnotherEconomy...");
        setPluginState(PluginState.INITIALIZING);
        
        try {
            // Initialize configuration manager
            configurationManager = new ConfigurationManager(this);
            configurationManager.initialize();
            
            // Load configuration
            loadConfiguration();
            
            // Register core services
            registerCoreServices();
            
            // Add this as event listener
            addEventListener(this);
            
            debug("YetAnotherEconomy initialization completed");
            return true;
            
        } catch (ConfigurationInitializationException e) {
            error("Failed to initialize YetAnotherEconomy configuration", e);
            setPluginState(PluginState.FAILED);
            return false;
        } catch (Exception e) {
            error("Failed to initialize YetAnotherEconomy", e);
            setPluginState(PluginState.FAILED);
            return false;
        }
    }
    
    @Override
    public boolean startup() {
        debug("Starting YetAnotherEconomy...");
        setPluginState(PluginState.STARTING);
        
        try {
            // Initialize all registered services in priority order
            services.values().stream()
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .forEach(service -> {
                    debug("Initializing service: " + service.getName());
                    boolean success = service.initialize();
                    emitEvent(new com.yae.ServiceInitializedEvent(service, success));
                    if (!success) {
                        warn("Service " + service.getName() + " failed to initialize");
                    }
                });
            
            debug("YetAnotherEconomy startup completed");
            return true;
            
        } catch (Exception e) {
            error("Failed to start YetAnotherEconomy", e);
            setPluginState(PluginState.FAILED);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        debug("Shutting down YetAnotherEconomy...");
        setPluginState(PluginState.STOPPING);
        
        try {
            // Shutdown configuration manager
            if (configurationManager != null) {
                configurationManager.shutdown();
                configurationManager = null;
            }
            
            // Shutdown all services in reverse priority order
            services.values().stream()
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .forEach(service -> {
                    debug("Shutting down service: " + service.getName());
                    try {
                        service.shutdown();
                    } catch (Exception e) {
                        error("Error shutting down service: " + service.getName(), e);
                    }
                });
            
            services.clear();
            
            debug("YetAnotherEconomy shutdown completed");
            
        } catch (Exception e) {
            error("Error during YetAnotherEconomy shutdown", e);
        }
    }
    
    @Override
    public boolean reload() {
        info("Reloading YetAnotherEconomy...");
        setPluginState(PluginState.RELOADING);
        
        try {
            // Reload configuration using configuration manager
            boolean configReloaded = configurationManager.reload();
            if (!configReloaded) {
                warn("Configuration reload failed");
            }
            
            // Reload all services
            boolean servicesReloaded = reloadAllServices();
            if (!servicesReloaded) {
                warn("Some services failed to reload");
            }
            
            info("YetAnotherEconomy reload completed");
            setPluginState(PluginState.RUNNING);
            return true;
            
        } catch (Exception e) {
            error("Failed to reload YetAnotherEconomy", e);
            setPluginState(PluginState.FAILED);
            return false;
        }
    }
    
    // Configuration management
    private void loadConfiguration() {
        debug("Loading configuration...");
        
        // Configuration is already loaded by ConfigurationManager during initialization
        Configuration config = getMainConfiguration();
        debug("Configuration loaded: Currency=" + config.getCurrency().getName() + 
              ", Database=" + config.getDatabase().getType());
    }
    
    private void registerCoreServices() {
        debug("Registering core services...");
        
        // Register DatabaseService
        com.yae.api.database.DatabaseService databaseService = new com.yae.api.database.DatabaseService(this);
        registerService(databaseService);
        debug("Registered DatabaseService");
        
        // Register EconomyService
        com.yae.api.services.EconomyService economyService = new com.yae.api.services.EconomyService(this);
        registerService(economyService);
        debug("Registered EconomyService");
        
        // Register ShopManager
        com.yae.api.shop.ShopManager shopManager = new com.yae.api.shop.ShopManager(this);
        registerService(shopManager);
        debug("Registered ShopManager");
        
        // Register PurchaseService
        com.yae.api.shop.PurchaseService purchaseService = new com.yae.api.shop.PurchaseService(this);
        registerService(purchaseService);
        debug("Registered PurchaseService");
        
        // Register BankAccountManager
        com.yae.api.bank.BankAccountManager bankService = new com.yae.api.bank.BankAccountManager(this);
        registerService(bankService);
        debug("Registered BankAccountManager");
        
        // Register InterestCalculator
        com.yae.api.bank.InterestCalculator interestService = new com.yae.api.bank.InterestCalculator(this);
        registerService(interestService);
        debug("Registered InterestCalculator");
        
        // Register DepositService
        com.yae.api.bank.DepositService depositService = new com.yae.api.bank.DepositService(this);
        registerService(depositService);
        debug("Registered DepositService");
        
        // Register ShopCommand
        com.yae.api.shop.ShopCommand shopCommand = new com.yae.api.shop.ShopCommand(this);
        registerCommand(shopCommand);
        debug("Registered ShopCommand");
        
        debug("Core services registration completed");
    }
    
    private void setConfiguration(ServiceConfig config) {
        this.configuration = config;
    }
    
    @Override
    public void setServiceConfiguration(@NotNull com.yae.api.core.ServiceType serviceType, @org.jetbrains.annotations.Nullable com.yae.api.core.ServiceConfig config) {
        // Implementation for setting service configuration
        debug("Setting service configuration for " + serviceType + ": " + (config != null ? "config" : "null"));
    }
    
    @Override
    public boolean reloadConfiguration() {
        info("Reloading configuration...");
        try {
            if (configurationManager != null) {
                return configurationManager.reload();
            }
            return false;
        } catch (Exception e) {
            error("Failed to reload configuration", e);
            return false;
        }
    }
    
}

/**
 * Placeholder classes for events referenced in the main class
 */
class ServiceInitializedEvent extends com.yae.api.core.event.ServiceEvent {
    
    private final boolean success;
    
    public ServiceInitializedEvent(com.yae.api.core.Service service, boolean success) {
        super("service-initialized", service, "Service " + service.getName() + (success ? " initialized successfully" : " failed to initialize"));
        this.success = success;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    @Override
    public com.yae.api.core.event.YAEEvent.EventSeverity getSeverity() {
        return success ? com.yae.api.core.event.YAEEvent.EventSeverity.INFO : com.yae.api.core.event.YAEEvent.EventSeverity.ERROR;
    }
}
