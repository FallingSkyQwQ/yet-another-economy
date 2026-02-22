package com.yae.api.services;

import com.yae.api.core.*;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.LanguageManager;
import com.yae.api.core.config.Configuration.CurrencyConfig;
import com.yae.api.core.config.Configuration.TransactionConfig;
import com.yae.api.core.event.YAEEvent;
import com.yae.api.core.event.EconomyEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.logging.Logger;

/**
 * Sample economy service that demonstrates configuration system usage.
 * This service manages player balances and transactions using the configuration system.
 */
public class EconomyService implements Service {
    
    private final YAECore plugin;
    private final Logger logger;
    private final ServiceType serviceType;
    private final AtomicBoolean enabled;
    private final AtomicBoolean healthy;
    private final int priority;
    
    private Configuration configuration;
    private LanguageManager languageManager;
    private Map<UUID, Double> balances; // In-memory balance cache
    
    public EconomyService(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getLogger();
        this.serviceType = ServiceType.ECONOMY;
        this.enabled = new AtomicBoolean(false);
        this.healthy = new AtomicBoolean(true);
        this.priority = 100;
        this.balances = new ConcurrentHashMap<>();
    }
    
    @Override
    @NotNull
    public String getName() {
        return "EconomyService";
    }
    
    @Override
    @NotNull
    public ServiceType getType() {
        return serviceType;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled.get();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        logger.log(Level.INFO, "EconomyService " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public boolean isHealthy() {
        return healthy.get() && isEnabled();
    }
    
    @Override
    public boolean initialize() {
        logger.log(Level.INFO, "Initializing EconomyService...");
        
        try {
            // Get configuration from plugin
            this.configuration = plugin.getMainConfiguration();
            this.languageManager = plugin.getConfigurationManager().getLanguageManager();
            
            // Validate configuration
            CurrencyConfig currencyConfig = configuration.getCurrency();
            TransactionConfig transactionConfig = configuration.getTransactions();
            
            logger.log(Level.INFO, "Currency: {0} (Symbol: {1})", 
                      new Object[]{currencyConfig.getName(), currencyConfig.getSymbol()});
            logger.log(Level.INFO, "Transactions enabled: {0}, Tax rate: {1}", 
                      new Object[]{transactionConfig.isLogTransactions(), transactionConfig.getTaxRate()});
            
            // Initialize balance cache with starting balance
            // In a real implementation, this would load from database
            double startingBalance = currencyConfig.getStartingBalance();
            logger.log(Level.INFO, "Starting balance set to: {0}", startingBalance);
            
            setEnabled(true);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize EconomyService", e);
            healthy.set(false);
            return false;
        }
    }
    

    
    @Override
    public void shutdown() {
        logger.log(Level.INFO, "Shutting down EconomyService...");
        setEnabled(false);
        clearCaches();
    }
    
    @Override
    public boolean reload() {
        logger.log(Level.INFO, "Reloading EconomyService...");
        
        try {
            // Update configuration reference
            this.configuration = plugin.getMainConfiguration();
            this.languageManager = plugin.getConfigurationManager().getLanguageManager();
            
            logger.log(Level.INFO, "EconomyService reloaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to reload EconomyService", e);
            return false;
        }
    }
    
    @Override
    @NotNull
    public String getStatus() {
        return String.format("EconomyService[%s, %s, %d balances]", 
                           isEnabled() ? "ENABLED" : "DISABLED",
                           isHealthy() ? "HEALTHY" : "UNHEALTHY",
                           balances.size());
    }
    
    @Override
    @NotNull
    public String getDiagnosticInfo() {
        CurrencyConfig currency = configuration.getCurrency();
        TransactionConfig transactions = configuration.getTransactions();
        
        return String.format("EconomyService Diagnostic:\n" +
                           "  Status: %s\n" +
                           "  Currency: %s (%s)\n" +
                           "  Starting Balance: %.2f\n" +
                           "  Log Transactions: %s\n" +
                           "  Tax Rate: %.2f%%\n" +
                           "  Cached Balances: %d",
                           getStatus(),
                           currency.getName(), currency.getSymbol(),
                           currency.getStartingBalance(),
                           transactions.isLogTransactions(),
                           transactions.getTaxRate() * 100,
                           balances.size());
    }
    
    // Economy service methods
    
    /**
     * Get player balance
     * @param playerId the player's UUID
     * @return the player's balance
     */
    public double getBalance(@NotNull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        
        if (!isEnabled()) {
            throw new IllegalStateException("EconomyService is not enabled");
        }
        
        return balances.getOrDefault(playerId, configuration.getCurrency().getStartingBalance());
    }
    
    /**
     * Set player balance
     * @param playerId the player's UUID
     * @param amount the new balance
     */
    public void setBalance(@NotNull UUID playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        
        if (!isEnabled()) {
            throw new IllegalStateException("EconomyService is not enabled");
        }
        
        amount = Math.max(0, amount); // Ensure non-negative
        balances.put(playerId, amount);
        
        // Emit balance change event
        emitBalanceChangeEvent(playerId, amount, "SET");
    }
    
    /**
     * Add money to player balance
     * @param playerId the player's UUID
     * @param amount the amount to add
     * @return the new balance
     */
    public double addMoney(@NotNull UUID playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        
        if (!isEnabled()) {
            throw new IllegalStateException("EconomyService is not enabled");
        }
        
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        double currentBalance = getBalance(playerId);
        double newBalance = currentBalance + amount;
        balances.put(playerId, newBalance);
        
        // Emit balance change event
        emitBalanceChangeEvent(playerId, newBalance, "ADD");
        
        return newBalance;
    }
    
    /**
     * Remove money from player balance
     * @param playerId the player's UUID
     * @param amount the amount to remove
     * @return the new balance
     */
    public double removeMoney(@NotNull UUID playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        
        if (!isEnabled()) {
            throw new IllegalStateException("EconomyService is not enabled");
        }
        
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        double currentBalance = getBalance(playerId);
        double newBalance = Math.max(0, currentBalance - amount);
        balances.put(playerId, newBalance);
        
        // Emit balance change event
        emitBalanceChangeEvent(playerId, newBalance, "REMOVE");
        
        return newBalance;
    }
    
    /**
     * Transfer money between players
     * @param fromPlayerId the sender's UUID
     * @param toPlayerId the recipient's UUID
     * @param amount the amount to transfer
     * @return true if transfer was successful
     */
    public boolean transferMoney(@NotNull UUID fromPlayerId, @NotNull UUID toPlayerId, double amount) {
        Objects.requireNonNull(fromPlayerId, "fromPlayerId cannot be null");
        Objects.requireNonNull(toPlayerId, "toPlayerId cannot be null");
        
        if (!isEnabled()) {
            throw new IllegalStateException("EconomyService is not enabled");
        }
        
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        // Check minimum and maximum transfer limits
        TransactionConfig transactions = configuration.getTransactions();
        if (amount < transactions.getMinTransferAmount()) {
            throw new IllegalArgumentException("Transfer amount below minimum: " + amount);
        }
        
        if (transactions.getMaxTransferAmount() > 0 && amount > transactions.getMaxTransferAmount()) {
            throw new IllegalArgumentException("Transfer amount above maximum: " + amount);
        }
        
        // Check sender's balance
        double fromBalance = getBalance(fromPlayerId);
        if (fromBalance < amount) {
            return false; // Insufficient funds
        }
        
        // Calculate tax
        double tax = amount * transactions.getTaxRate();
        double transferAmount = amount - tax;
        
        // Perform transfer
        double newFromBalance = removeMoney(fromPlayerId, amount);
        double newToBalance = addMoney(toPlayerId, transferAmount);
        
        // Emit transfer event
        emitTransferEvent(fromPlayerId, toPlayerId, amount, transferAmount, tax);
        
        return true;
    }
    
    /**
     * Check if player has sufficient balance
     * @param playerId the player's UUID
     * @param amount the amount to check
     * @return true if player has sufficient balance
     */
    public boolean hasMoney(@NotNull UUID playerId, double amount) {
        return getBalance(playerId) >= amount;
    }
    
    /**
     * Format currency amount with proper formatting and symbol
     * @param amount the amount to format
     * @return formatted currency string
     */
    @NotNull
    public String formatCurrency(double amount) {
        CurrencyConfig currency = configuration.getCurrency();
        String formatted = currency.format(amount);
        return currency.getSymbol() + " " + formatted;
    }
    
    // Event emission methods
    
    private void emitBalanceChangeEvent(UUID playerId, double newBalance, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("newBalance", newBalance);
        data.put("reason", reason);
        
        EconomyEvent event = new EconomyEvent("balance-change", this, 
                                            "Player balance changed: " + playerId + " -> " + newBalance + " (" + reason + ")", data);
        plugin.emitEvent(event);
    }
    
    private void emitTransferEvent(UUID fromPlayerId, UUID toPlayerId, double originalAmount, double transferAmount, double tax) {
        Map<String, Object> data = new HashMap<>();
        data.put("fromPlayerId", fromPlayerId);
        data.put("toPlayerId", toPlayerId);
        data.put("originalAmount", originalAmount);
        data.put("transferAmount", transferAmount);
        data.put("tax", tax);
        
        EconomyEvent event = new EconomyEvent("transfer", this, 
                                            "Money transferred from " + fromPlayerId + " to " + toPlayerId + 
                                            ": " + originalAmount + " (tax: " + tax + ")", data);
        plugin.emitEvent(event);
    }
    
    // Utility methods
    
    private void clearCaches() {
        balances.clear();
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return false; // EconomyService doesn't depend on other services
    }
    
    @Override
    public ServiceConfig getConfig() {
        return null; // No specific config for this service
    }
    
    @Override
    public void setConfig(@Nullable ServiceConfig config) {
        // No specific config for this service
    }
    
    @Override
    public String toString() {
        return getStatus();
    }
}
