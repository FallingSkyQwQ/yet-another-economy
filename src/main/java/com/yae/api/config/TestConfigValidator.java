package com.yae.api.config;

import com.yae.api.core.config.ConfigFile;
import com.yae.api.core.config.ConfigValidator;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.ConfigurationManager;
import com.yae.api.core.YAECore;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Example custom configuration validator that demonstrates advanced validation
 * for economy plugin specific settings.
 */
public class TestConfigValidator implements Configuration.ConfigurationValidator {
    
    private final YAECore plugin;
    private final Logger logger;
    
    public TestConfigValidator(@NotNull YAECore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    @Override
    public void validate(@NotNull Configuration config) throws Configuration.ConfigurationValidationException {
        logger.info("Running comprehensive configuration validation...");
        
        // Use validation chain for organized validation
        ConfigValidator.ValidationChain chain = ConfigValidator.chain();
        
        // Currency validation
        Configuration.CurrencyConfig currency = config.getCurrency();
        chain.requireNotBlank(currency.getName(), "Currency name")
             .requireNotBlank(currency.getSymbol(), "Currency symbol")
             .requireRange(currency.getDecimals(), 0, 10, "Currency decimals")
             .requireNonNegative(currency.getStartingBalance(), "Starting balance");
        
        // Transaction validation
        Configuration.TransactionConfig transactions = config.getTransactions();
        chain.requireNonNegative(transactions.getTaxRate(), "Tax rate")
             .requireRange(transactions.getTaxRate(), 0.0, 1.0, "Tax rate")
             .requirePositive(transactions.getHistoryDays(), "History days")
             .requireNonNegative(transactions.getMinTransferAmount(), "Minimum transfer amount")
             .requireNonNegative(transactions.getMaxTransferAmount(), "Maximum transfer amount");
        
        // Validate transfer amount limits consistency
        if (transactions.getMaxTransferAmount() > 0 && 
            transactions.getMinTransferAmount() > transactions.getMaxTransferAmount()) {
            chain.validate(() -> {
                throw new Configuration.ConfigurationValidationException(
                    "Minimum transfer amount cannot exceed maximum transfer amount");
            });
        }
        
        // Database validation
        Configuration.DatabaseConfig database = config.getDatabase();
        if (database.getType() == Configuration.DatabaseConfig.DatabaseType.MYSQL || 
            database.getType() == Configuration.DatabaseConfig.DatabaseType.MARIADB) {
            
            Configuration.DatabaseConfig.MysqlConfig mysql = database.getMysql();
            chain.requireValidIpAddress(mysql.getHost(), "MySQL host")
                 .requireValidPort(mysql.getPort(), "MySQL port")
                 .requireNotBlank(mysql.getDatabase(), "MySQL database name")
                 .requireNotBlank(mysql.getUsername(), "MySQL username")
                 .requirePositive(mysql.getPoolSize(), "MySQL pool size")
                 .requirePositive((int) mysql.getConnectionTimeout(), "MySQL connection timeout")
                 .requirePositive((int) mysql.getConnectionTimeout(), "MySQL connection timeout");
        }
        
        // Features validation
        Configuration.FeaturesConfig features = config.getFeatures();
        
        // Interest system validation
        Configuration.FeaturesConfig.InterestConfig interest = features.getInterest();
        chain.requireRange(interest.getRate(), 0.0, 1.0, "Interest rate")
             .requireNonNegative(interest.getMinBalance(), "Interest minimum balance")
             .requireNonNegative(interest.getMaxInterest(), "Maximum interest amount");
        
        // Banking system validation
        Configuration.FeaturesConfig.BankingConfig banking = features.getBanking();
        chain.requirePositive(banking.getMaxAccounts(), "Maximum bank accounts");
        
        // PvP system validation
        Configuration.FeaturesConfig.PvPConfig pvp = features.getPvp();
        chain.requireRange(pvp.getKillReward(), 0.0, 1.0, "PvP kill reward rate")
             .requireNonNegative(pvp.getMaxReward(), "PvP maximum reward");
        
        // Performance validation
        Configuration.PerformanceConfig performance = config.getPerformance();
        chain.requirePositive((int) performance.getSaveInterval(), "Save interval")
             .requirePositive(performance.getCacheSize(), "Cache size")
             .requirePositive((int) performance.getCacheExpire(), "Cache expire time");
        
        // Check for any validation errors
        if (chain.hasErrors()) {
            throw new Configuration.ConfigurationValidationException(
                "Configuration validation failed: " + String.join("; ", chain.getErrors()));
        }
        
        logger.info("Configuration validation passed successfully!");
    }
    
    /**
     * Example method showing how to validate specific transaction scenarios
     */
    public void validateTransaction(@NotNull Configuration config, double amount) throws Configuration.ConfigurationValidationException {
        Configuration.TransactionConfig transactions = config.getTransactions();
        
        // Use validator utility methods directly for specific validation
        ConfigValidator.requirePositive(amount, "Transaction amount");
        ConfigValidator.requireRange(amount, 
                                     transactions.getMinTransferAmount(), 
                                     transactions.getMaxTransferAmount(), 
                                     "Transaction amount");
        
        logger.info("Transaction amount validation passed for: " + amount);
    }
    
    /**
     * Example method showing how to validate database connection details
     */
    public void validateDatabaseConnection(@NotNull Configuration config) throws Configuration.ConfigurationValidationException {
        Configuration.DatabaseConfig database = config.getDatabase();
        
        if (database.getType() == Configuration.DatabaseConfig.DatabaseType.MYSQL) {
            Configuration.DatabaseConfig.MysqlConfig mysql = database.getMysql();
            
            // Comprehensive MySQL validation
            String connectionString = String.format("mysql://%s:%d/%s", 
                                                    mysql.getHost(), mysql.getPort(), mysql.getDatabase());
            
            logger.info("Validating database connection for: " + connectionString);
            
            // Perform validation checks
            ConfigValidator.requireNotBlank(mysql.getHost(), "Database host");
            ConfigValidator.requireValidPort(mysql.getPort(), "Database port");
            ConfigValidator.requireNotBlank(mysql.getDatabase(), "Database name");
            ConfigValidator.requireNotBlank(mysql.getUsername(), "Database username");
            
            // Connection pool validation
            ConfigValidator.requirePositive(mysql.getPoolSize(), "Connection pool size");
            ConfigValidator.requireRange(mysql.getPoolSize(), 1, 50, "Connection pool size");
            
            logger.info("Database connection validation passed for: " + connectionString);
        } else {
            logger.info("SQLite database detected, connection validation not required");
        }
    }
}
