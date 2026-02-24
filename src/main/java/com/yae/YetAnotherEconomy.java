package com.yae;

import com.yae.api.core.YAECoreBase;
import com.yae.api.core.YAECore.PluginState;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.config.ConfigurationManager;
import com.yae.api.core.config.ConfigurationManager.ConfigurationInitializationException;
import com.yae.api.core.config.Configuration;
import com.yae.api.database.DatabaseService;
import com.yae.api.loan.LoanService;
import com.yae.api.loan.SimpleOverdueService;
import com.yae.api.loan.command.LoanCommand;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Simplified version of YetAnotherEconomy with basic loan functionality
 */
public class YetAnotherEconomy extends YAECoreBase {
    
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
        
        info("Starting Simplified YetAnotherEconomy v" + getPluginVersion());
        
        // Initialize core
        if (!initialize()) {
            logger.severe("Failed to initialize Simplified YetAnotherEconomy! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Startup
        if (!startup()) {
            logger.severe("Failed to start Simplified YetAnotherEconomy! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        info("Simplified YetAnotherEconomy v" + getPluginVersion() + " has been enabled!");
        setPluginState(PluginState.RUNNING);
    }
    
    @Override
    public void onDisable() {
        info("Disabling Simplified YetAnotherEconomy v" + getPluginVersion());
        setPluginState(PluginState.STOPPING);
        
        shutdown();
        
        instance = null;
        info("Simplified YetAnotherEconomy has been disabled!");
        setPluginState(PluginState.STOPPED);
    }
    
    // YAECore interface implementation
    @Override
    public String getPluginName() {
        return "YetAnotherEconomy";
    }
    
    @Override
    public String getPluginVersion() {
        return "1.0.0-BASIC";
    }
    
    @Override
    public boolean initialize() {
        debug("Initializing Simplified YetAnotherEconomy...");
        setPluginState(PluginState.INITIALIZING);
        
        try {
            // Initialize configuration manager
            configurationManager = new ConfigurationManager(this);
            configurationManager.initialize();
            
            // Load configuration
            loadConfiguration();
            
            // Register core services (simplified)
            registerCoreServices();
            
            // Add this as event listener
            addEventListener(this);
            
            debug("Simplified YetAnotherEconomy initialization completed");
            return true;
            
        } catch (ConfigurationInitializationException e) {
            error("Failed to initialize Simplified YetAnotherEconomy configuration", e);
            setPluginState(PluginState.FAILED);
            return false;
        } catch (Exception e) {
            error("Failed to initialize Simplified YetAnotherEconomy", e);
            setPluginState(PluginState.FAILED);
            return false;
        }
    }
    
    @Override
    public boolean startup() {
        debug("Starting Simplified YetAnotherEconomy...");
        setPluginState(PluginState.STARTING);
        
        try {
            // Initialize basic services in priority order
            services.values().stream()
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .forEach(service -> {
                    debug("Initializing service: " + service.getName());
                    boolean success = service.initialize();
                    if (!success) {
                        warn("Service " + service.getName() + " failed to initialize");
                    }
                });
            
            debug("Simplified YetAnotherEconomy startup completed");
            return true;
            
        } catch (Exception e) {
            error("Failed to start Simplified YetAnotherEconomy", e);
            setPluginState(PluginState.FAILED);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        debug("Shutting down Simplified YetAnotherEconomy...");
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
            
            debug("Simplified YetAnotherEconomy shutdown completed");
            
        } catch (Exception e) {
            error("Error during Simplified YetAnotherEconomy shutdown", e);
        }
    }
    
    // Configuration management
    private void loadConfiguration() {
        debug("Loading configuration...");
        
        // Configuration is already loaded by ConfigurationManager during initialization
        Configuration config = getMainConfiguration();
        debug("Configuration loaded: Currency=" + (config != null ? config.getCurrency().getName() : "default") + 
              ", Database=" + (config != null ? config.getDatabase().getType() : "sqlite"));
    }
    
    private void registerCoreServices() {
        debug("Registering simplified core services...");
        
        // Register DatabaseService
        DatabaseService databaseService = new DatabaseService(this);
        registerService(databaseService);
        debug("Registered DatabaseService");
        
        // Register basic EconomyService
        try {
            com.yae.api.services.EconomyService economyService = new com.yae.api.services.EconomyService(this);
            registerService(economyService);
            debug("Registered EconomyService");
        } catch (Exception e) {
            warn("Could not register EconomyService, skipping: " + e.getMessage());
        }
        
        // Register basic ShopManager
        try {
            com.yae.api.shop.ShopManager shopManager = new com.yae.api.shop.ShopManager(this);
            registerService(shopManager);
            debug("Registered ShopManager");
        } catch (Exception e) {
            warn("Could not register ShopManager, skipping: " + e.getMessage());
        }
        
        // Register PurchaseService
        try {
            com.yae.api.shop.PurchaseService purchaseService = new com.yae.api.shop.PurchaseService(this);
            registerService(purchaseService);
            debug("Registered PurchaseService");
        } catch (Exception e) {
            warn("Could not register PurchaseService, skipping: " + e.getMessage());
        }
        
        // Register BankAccountManager
        try {
            com.yae.api.bank.BankAccountManager bankService = new com.yae.api.bank.BankAccountManager(this);
            registerService(bankService);
            debug("Registered BankAccountManager");
        } catch (Exception e) {
            warn("Could not register BankAccountManager, skipping: " + e.getMessage());
        }
        
        // Register basic LoanService (simplified)
        try {
            LoanService loanService = new LoanService(this);
            registerService(loanService);
            debug("Registered simplified LoanService");
        } catch (Exception e) {
            warn("Could not register LoanService, skipping: " + e.getMessage());
        }
        
        // Register basic OverdueService (simplified)
        try {
            LoanService loanService = (LoanService) getService(ServiceType.LOAN);
            if (loanService != null) {
                SimpleOverdueService overdueService = new SimpleOverdueService(loanService);
                registerService(overdueService);
                debug("Registered simplified OverdueService");
            }
        } catch (Exception e) {
            warn("Could not register OverdueService, skipping: " + e.getMessage());
        }
        
        // Register LoanCommand
        try {
            LoanCommand loanCommand = new LoanCommand(this);
            debug("LoanCommand created");
        } catch (Exception e) {
            warn("Could not create LoanCommand, skipping: " + e.getMessage());
        }
        
        debug("Simplified core services registration completed");
    }
    
    private void setConfiguration(ServiceConfig config) {
        this.configuration = config;
    }
    
    @Override
    public void setServiceConfiguration(@NotNull ServiceType serviceType, @org.jetbrains.annotations.Nullable ServiceConfig config) {
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
    
    @Override
    public boolean reload() {
        info("Reloading Simplified YetAnotherEconomy...");
        setPluginState(PluginState.RELOADING);
        
        try {
            // Simple reload - just reinitialize configuration
            if (configurationManager != null) {
                boolean configReloaded = configurationManager.reload();
                if (!configReloaded) {
                    warn("Configuration reload failed");
                }
                return configReloaded;
            }
            return true;
        } catch (Exception e) {
            error("Failed to reload Simplified YetAnotherEconomy", e);
            setPluginState(PluginState.FAILED);
            return false;
        } finally {
            setPluginState(PluginState.RUNNING);
        }
    }
}
