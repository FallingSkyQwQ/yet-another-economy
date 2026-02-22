package com.yae.api.core.config;

import com.yae.api.core.ServiceConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main configuration class that encapsulates all system parameters for YetAnotherEconomy.
 * Provides type-safe access to configuration values with validation and defaults.
 */
public final class Configuration {
    
    // Configuration sections
    private final ConfigFile config;
    private final Logger logger;
    private final List<ConfigurationValidator> validators;
    
    // Cached configuration sections
    private CurrencyConfig currencyConfig;
    private DatabaseConfig databaseConfig;
    private TransactionConfig transactionConfig;
    private MessageConfig messageConfig;
    private FeaturesConfig featuresConfig;
    private PerformanceConfig performanceConfig;
    
    public Configuration(@NotNull ConfigFile config, @NotNull Logger logger) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.validators = new ArrayList<>();
        
        initializeValidators();
        validateConfiguration();
        initializeSectionConfigs();
    }
    
    /**
     * Validate the configuration
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        try {
            for (ConfigurationValidator validator : validators) {
                validator.validate(this);
            }
            logger.log(Level.INFO, "Configuration validation passed");
            return true;
        } catch (ConfigurationValidationException e) {
            logger.log(Level.SEVERE, "Configuration validation failed: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Reload the configuration
     * @param newConfig the new configuration file
     */
    public void reload(@NotNull ConfigFile newConfig) {
        Objects.requireNonNull(newConfig, "newConfig cannot be null");
        
        logger.log(Level.INFO, "Reloading configuration...");
        
        Configuration newConfiguration = new Configuration(newConfig, logger);
        if (newConfiguration.validate()) {
            // Update section configs
            this.currencyConfig = newConfiguration.currencyConfig;
            this.databaseConfig = newConfiguration.databaseConfig;
            this.transactionConfig = newConfiguration.transactionConfig;
            this.messageConfig = newConfiguration.messageConfig;
            this.featuresConfig = newConfiguration.featuresConfig;
            this.performanceConfig = newConfiguration.performanceConfig;
            
            logger.log(Level.INFO, "Configuration reloaded successfully");
        } else {
            logger.log(Level.SEVERE, "Configuration reload failed: validation failed");
        }
    }
    
    // Currency configuration
    
    @NotNull
    public CurrencyConfig getCurrency() {
        return currencyConfig;
    }
    
    @NotNull
    public DatabaseConfig getDatabase() {
        return databaseConfig;
    }
    
    @NotNull
    public TransactionConfig getTransactions() {
        return transactionConfig;
    }
    
    @NotNull
    public MessageConfig getMessages() {
        return messageConfig;
    }
    
    @NotNull
    public FeaturesConfig getFeatures() {
        return featuresConfig;
    }
    
    @NotNull
    public PerformanceConfig getPerformance() {
        return performanceConfig;
    }
    
    // Validation helper method
    
    private void validateConfiguration() {
        if (!validate()) {
            throw new ConfigurationValidationException("Initial configuration validation failed");
        }
    }
    
    private void initializeValidators() {
        validators.add(new CurrencyValidator());
        validators.add(new DatabaseValidator());
        validators.add(new TransactionValidator());
        validators.add(new PerformanceValidator());
        validators.add(new FeaturesValidator());
    }
    
    private void initializeSectionConfigs() {
        this.currencyConfig = new CurrencyConfig(config.getSection("currency"));
        this.databaseConfig = new DatabaseConfig(config.getSection("database"));
        this.transactionConfig = new TransactionConfig(config.getSection("transactions"));
        this.messageConfig = new MessageConfig(config.getSection("messages"));
        this.featuresConfig = new FeaturesConfig(config.getSection("features"));
        this.performanceConfig = new PerformanceConfig(config.getSection("performance"));
    }
    
    // Configuration section classes
    
    public static final class CurrencyConfig {
        private final ConfigFile section;
        private final String name;
        private final String symbol;
        private final int decimals;
        private final double startingBalance;
        
        public CurrencyConfig(@Nullable ConfigFile section) {
            this.section = section;
            this.name = section != null ? section.getString("name", "金币") : "金币";
            this.symbol = section != null ? section.getString("symbol", "§6💰") : "§6💰";
            this.decimals = section != null ? section.getInt("decimals", 2) : 2;
            this.startingBalance = section != null ? section.getDouble("starting-balance", 100.0) : 100.0;
        }
        
        @NotNull
        public String getName() {
            return name;
        }
        
        @NotNull
        public String getSymbol() {
            return symbol;
        }
        
        public int getDecimals() {
            return decimals;
        }
        
        public double getStartingBalance() {
            return startingBalance;
        }
        
        @NotNull
        public String format(double amount) {
            String format = "%" + (decimals > 0 ? "." + decimals + "f" : "d");
            return String.format(format, amount);
        }
    }
    
    public static final class DatabaseConfig {
        private final ConfigFile section;
        private final DatabaseType type;
        private final SqliteConfig sqliteConfig;
        private final MysqlConfig mysqlConfig;
        
        public DatabaseConfig(@Nullable ConfigFile section) {
            this.section = section;
            String typeStr = section != null ? section.getString("type", "sqlite") : "sqlite";
            this.type = parseDatabaseType(typeStr);
            this.sqliteConfig = new SqliteConfig(section != null ? section.getSection("sqlite") : null);
            this.mysqlConfig = new MysqlConfig(section != null ? section.getSection("mysql") : null);
        }
        
        @NotNull
        public DatabaseType getType() {
            return type;
        }
        
        @NotNull
        public SqliteConfig getSqlite() {
            return sqliteConfig;
        }
        
        @NotNull
        public MysqlConfig getMysql() {
            return mysqlConfig;
        }
        
        private static DatabaseType parseDatabaseType(@NotNull String type) {
            try {
                return DatabaseType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DatabaseType.SQLITE;
            }
        }
        
        public enum DatabaseType {
            SQLITE, MYSQL, MARIADB
        }
        
        public static final class SqliteConfig {
            private final String filename;
            
            public SqliteConfig(@Nullable ConfigFile section) {
                this.filename = section != null ? section.getString("filename", "economy.db") : "economy.db";
            }
            
            @NotNull
            public String getFilename() {
                return filename;
            }
        }
        
        public static final class MysqlConfig {
            private final String host;
            private final int port;
            private final String database;
            private final String username;
            private final String password;
            private final int poolSize;
            private final long connectionTimeout;
            private final long idleTimeout;
            private final long maxLifetime;
            
            public MysqlConfig(@Nullable ConfigFile section) {
                this.host = section != null ? section.getString("host", "localhost") : "localhost";
                this.port = section != null ? section.getInt("port", 3306) : 3306;
                this.database = section != null ? section.getString("database", "yet_another_economy") : "yet_another_economy";
                this.username = section != null ? section.getString("username", "root") : "root";
                this.password = section != null ? section.getString("password", "") : "";
                this.poolSize = section != null ? section.getInt("pool-size", 10) : 10;
                this.connectionTimeout = section != null ? section.getInt("connection-timeout", 30000) : 30000;
                this.idleTimeout = section != null ? section.getInt("idle-timeout", 600000) : 600000;
                this.maxLifetime = section != null ? section.getInt("max-lifetime", 1800000) : 1800000;
            }
            
            @NotNull
            public String getHost() {
                return host;
            }
            
            public int getPort() {
                return port;
            }
            
            @NotNull
            public String getDatabase() {
                return database;
            }
            
            @NotNull
            public String getUsername() {
                return username;
            }
            
            @NotNull
            public String getPassword() {
                return password;
            }
            
            public int getPoolSize() {
                return poolSize;
            }
            
            public long getConnectionTimeout() {
                return connectionTimeout;
            }
            
            public long getIdleTimeout() {
                return idleTimeout;
            }
            
            public long getMaxLifetime() {
                return maxLifetime;
            }
        }
    }
    
    public static final class TransactionConfig {
        private final ConfigFile section;
        private final boolean logTransactions;
        private final int historyDays;
        private final double maxTransferAmount;
        private final double minTransferAmount;
        private final double taxRate;
        private final boolean enableTransferCooldown;
        private final int transferCooldown;
        
        public TransactionConfig(@Nullable ConfigFile section) {
            this.section = section;
            this.logTransactions = section != null ? section.getBoolean("log-transactions", true) : true;
            this.historyDays = section != null ? section.getInt("history-days", 30) : 30;
            this.maxTransferAmount = section != null ? section.getDouble("max-transfer-amount", 1000000.0) : 1000000.0;
            this.minTransferAmount = section != null ? section.getDouble("min-transfer-amount", 0.01) : 0.01;
            this.taxRate = section != null ? section.getDouble("tax-rate", 0.0) : 0.0;
            this.enableTransferCooldown = section != null ? section.getBoolean("enable-transfer-cooldown", false) : false;
            this.transferCooldown = section != null ? section.getInt("transfer-cooldown", 60) : 60;
        }
        
        public boolean isLogTransactions() {
            return logTransactions;
        }
        
        public int getHistoryDays() {
            return historyDays;
        }
        
        public double getMaxTransferAmount() {
            return maxTransferAmount;
        }
        
        public double getMinTransferAmount() {
            return minTransferAmount;
        }
        
        public double getTaxRate() {
            return taxRate;
        }
        
        public boolean isTransferCooldownEnabled() {
            return enableTransferCooldown;
        }
        
        public int getTransferCooldown() {
            return transferCooldown;
        }
        
        @NotNull
        public String getTransferCooldownFormatted() {
            long seconds = transferCooldown;
            if (seconds < 60) {
                return seconds + "秒";
            } else if (seconds < 3600) {
                return (seconds / 60) + "分钟";
            } else {
                return (seconds / 3600) + "小时";
            }
        }
    }
    
    public static final class MessageConfig {
        private final ConfigFile section;
        private final String language;
        private final String prefix;
        
        public MessageConfig(@Nullable ConfigFile section) {
            this.section = section;
            this.language = section != null ? section.getString("language", "zh_cn") : "zh_cn";
            this.prefix = section != null ? section.getString("prefix", "§6[§eYAE§6] §f") : "§6[§eYAE§6] §f";
        }
        
        @NotNull
        public String getLanguage() {
            return language;
        }
        
        @NotNull
        public String getPrefix() {
            return prefix;
        }
        
        @NotNull
        public String addPrefix(@NotNull String message) {
            Objects.requireNonNull(message, "message cannot be null");
            return prefix + message;
        }
    }
    
    public static final class FeaturesConfig {
        private final ConfigFile section;
        private final InterestConfig interestConfig;
        private final BankingConfig bankingConfig;
        private final PvPConfig pvpConfig;
        
        public FeaturesConfig(@Nullable ConfigFile section) {
            this.section = section;
            this.interestConfig = new InterestConfig(section != null ? section.getSection("interest") : null);
            this.bankingConfig = new BankingConfig(section != null ? section.getSection("banking") : null);
            this.pvpConfig = new PvPConfig(section != null ? section.getSection("pvp") : null);
        }
        
        @NotNull
        public InterestConfig getInterest() {
            return interestConfig;
        }
        
        @NotNull
        public BankingConfig getBanking() {
            return bankingConfig;
        }
        
        @NotNull
        public PvPConfig getPvp() {
            return pvpConfig;
        }
        
        public static final class InterestConfig {
            private final boolean enabled;
            private final double rate;
            private final double minBalance;
            private final double maxInterest;
            
            public InterestConfig(@Nullable ConfigFile section) {
                this.enabled = section != null ? section.getBoolean("enabled", false) : false;
                this.rate = section != null ? section.getDouble("rate", 0.01) : 0.01;
                this.minBalance = section != null ? section.getDouble("min-balance", 100.0) : 100.0;
                this.maxInterest = section != null ? section.getDouble("max-interest", 1000.0) : 1000.0;
            }
            
            public boolean isEnabled() {
                return enabled;
            }
            
            public double getRate() {
                return rate;
            }
            
            public double getMinBalance() {
                return minBalance;
            }
            
            public double getMaxInterest() {
                return maxInterest;
            }
            
            public double calculate(double balance) {
                if (!enabled || balance < minBalance) {
                    return 0.0;
                }
                
                double interest = balance * rate;
                return Math.min(interest, maxInterest);
            }
        }
        
        public static final class BankingConfig {
            private final boolean enabled;
            private final int maxAccountsPerOwner;
            private final double defaultInterestRate;
            private final double savingsInterestRate;
            private final double earlyWithdrawalPenaltyRate;
            private final long interestCalculationInterval;
            private final long saveInterval;
            private final int[] availableTerms;
            private final Map<Integer, Double> termInterestRates;
            private final TransactionLimitsConfig transactionLimits;
            
            public BankingConfig(@Nullable ConfigFile section) {
                this.enabled = section != null ? section.getBoolean("enabled", false) : false;
                this.maxAccountsPerOwner = section != null ? section.getInt("max-accounts-per-owner", 3) : 3;
                this.defaultInterestRate = section != null ? section.getDouble("default-interest-rate", 0.01) : 0.01;
                this.savingsInterestRate = section != null ? section.getDouble("savings-interest-rate", 0.015) : 0.015;
                this.earlyWithdrawalPenaltyRate = section != null ? section.getDouble("early-withdrawal-penalty-rate", 0.05) : 0.05;
                this.interestCalculationInterval = section != null ? section.getLong("interest-calculation-interval", 3600L) : 3600L;
                this.saveInterval = section != null ? section.getLong("save-interval", 300L) : 300L;
                
                // 读取存期配置
                ConfigFile fixedDepositsSection = section != null ? section.getSection("fixed-deposits") : null;
                this.availableTerms = fixedDepositsSection != null ? 
                    fixedDepositsSection.getIntegerList("available-terms", Arrays.asList(3, 6, 12, 24, 36, 60))
                        .stream().mapToInt(Integer::intValue).toArray() : 
                    new int[]{3, 6, 12, 24, 36, 60};
                
                // 读取存期利率配置
                this.termInterestRates = new HashMap<>();
                if (fixedDepositsSection != null) {
                    ConfigFile ratesSection = fixedDepositsSection.getSection("term-interest-rates");
                    if (ratesSection != null) {
                        for (int term : availableTerms) {
                            double rate = ratesSection.getDouble(String.valueOf(term), 0.02);
                            termInterestRates.put(term, rate);
                        }
                    }
                }
                // 设置默认利率
                if (termInterestRates.isEmpty()) {
                    termInterestRates.put(3, 0.0125);
                    termInterestRates.put(6, 0.015);
                    termInterestRates.put(12, 0.02);
                    termInterestRates.put(24, 0.025);
                    termInterestRates.put(36, 0.03);
                    termInterestRates.put(60, 0.035);
                }
                
                // 读取交易限额配置
                ConfigFile transactionLimitsSection = section != null ? section.getSection("transaction-limits") : null;
                this.transactionLimits = new TransactionLimitsConfig(transactionLimitsSection);
            }
            
            public boolean isEnabled() {
                return enabled;
            }
            
            public int getMaxAccountsPerOwner() {
                return maxAccountsPerOwner;
            }
            
            public double getDefaultInterestRate() {
                return defaultInterestRate;
            }
            
            public double getSavingsInterestRate() {
                return savingsInterestRate;
            }
            
            public double getEarlyWithdrawalPenaltyRate() {
                return earlyWithdrawalPenaltyRate;
            }
            
            public long getInterestCalculationInterval() {
                return interestCalculationInterval;
            }
            
            public long getSaveInterval() {
                return saveInterval;
            }
            
            public int[] getAvailableTerms() {
                return availableTerms;
            }
            
            public double getTermInterestRate(int termMonths) {
                return termInterestRates.getOrDefault(termMonths, 0.02);
            }
            
            public TransactionLimitsConfig getTransactionLimits() {
                return transactionLimits;
            }
            
            public DepositsConfig getDeposits() {
                return new DepositsConfig();
            }
            
            public static final class TransactionLimitsConfig {
                private final double maxDepositPerTransaction;
                private final double maxWithdrawalPerTransaction;
                private final double maxDailyTransactionAmount;
                private final double maxTransferAmount;
                
                public TransactionLimitsConfig(@Nullable ConfigFile section) {
                    this.maxDepositPerTransaction = section != null ? 
                        section.getDouble("max-deposit-per-transaction", 1000000.0) : 1000000.0;
                    this.maxWithdrawalPerTransaction = section != null ? 
                        section.getDouble("max-withdrawal-per-transaction", 500000.0) : 500000.0;
                    this.maxDailyTransactionAmount = section != null ? 
                        section.getDouble("max-daily-transaction-amount", 5000000.0) : 5000000.0;
                    this.maxTransferAmount = section != null ? 
                        section.getDouble("max-transfer-amount", 1000000.0) : 1000000.0;
                }
                
                public double getMaxDepositPerTransaction() {
                    return maxDepositPerTransaction;
                }
                
                public double getMaxWithdrawalPerTransaction() {
                    return maxWithdrawalPerTransaction;
                }
                
                public double getMaxDailyTransactionAmount() {
                    return maxDailyTransactionAmount;
                }
                
                public double getMaxTransferAmount() {
                    return maxTransferAmount;
                }
            }
            
            // 兼容旧配置的getter方法
            public int getMaxAccounts() {
                return maxAccountsPerOwner;
            }
        }
        
        public static final class PvPConfig {
            private final boolean enabled;
            private final double killReward;
            private final double maxReward;
            
            public PvPConfig(@Nullable ConfigFile section) {
                this.enabled = section != null ? section.getBoolean("enabled", false) : false;
                this.killReward = section != null ? section.getDouble("kill-reward", 0.05) : 0.05;
                this.maxReward = section != null ? section.getDouble("max-reward", 1000.0) : 1000.0;
            }
            
            public boolean isEnabled() {
                return enabled;
            }
            
            public double getKillReward() {
                return killReward;
            }
            
            public double getMaxReward() {
                return maxReward;
            }
            
            public double calculate(double victimBalance) {
                if (!enabled) {
                    return 0.0;
                }
                
                double reward = victimBalance * killReward;
                return Math.min(reward, maxReward);
            }
        }
    }
    
    public static final class PerformanceConfig {
        private final ConfigFile section;
        private final long saveInterval;
        private final boolean asyncSave;
        private final int cacheSize;
        private final long cacheExpire;
        
        public PerformanceConfig(@Nullable ConfigFile section) {
            this.section = section;
            this.saveInterval = section != null ? TimeUnit.SECONDS.toMillis(section.getInt("save-interval", 300)) : TimeUnit.SECONDS.toMillis(300);
            this.asyncSave = section != null ? section.getBoolean("async-save", true) : true;
            this.cacheSize = section != null ? section.getInt("cache-size", 1000) : 1000;
            this.cacheExpire = section != null ? TimeUnit.MINUTES.toMillis(section.getInt("cache-expire", 30)) : TimeUnit.MINUTES.toMillis(30);
        }
        
        public long getSaveInterval() {
            return saveInterval;
        }
        
        public boolean isAsyncSave() {
            return asyncSave;
        }
        
        public int getCacheSize() {
            return cacheSize;
        }
        
        public long getCacheExpire() {
            return cacheExpire;
        }
    }
    
    public static final class DepositsConfig {
        private final double minDepositAmount;
        private final double maxDepositAmount;
        private final boolean enabled;
        
        public DepositsConfig() {
            this.enabled = true;
            this.minDepositAmount = 0.01;
            this.maxDepositAmount = 999999999.99;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public double getMinDepositAmount() {
            return minDepositAmount;
        }
        
        public double getMaxDepositAmount() {
            return maxDepositAmount;
        }
    }
    
    // Configuration validation
    
    /**
     * Configuration validator interface
     */
    public interface ConfigurationValidator {
        void validate(@NotNull Configuration config) throws ConfigurationValidationException;
    }
    
    /**
     * Exception thrown when configuration validation fails
     */
    public static class ConfigurationValidationException extends RuntimeException {
        public ConfigurationValidationException(String message) {
            super(message);
        }
        
        public ConfigurationValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Built-in validators
    
    private static class CurrencyValidator implements ConfigurationValidator {
        @Override
        public void validate(@NotNull Configuration config) throws ConfigurationValidationException {
            CurrencyConfig currency = config.getCurrency();
            
            if (currency.getDecimals() < 0 || currency.getDecimals() > 10) {
                throw new ConfigurationValidationException("Currency decimals must be between 0 and 10");
            }
            
            if (currency.getStartingBalance() < 0) {
                throw new ConfigurationValidationException("Starting balance cannot be negative");
            }
        }
    }
    
    private static class DatabaseValidator implements ConfigurationValidator {
        @Override
        public void validate(@NotNull Configuration config) throws ConfigurationValidationException {
            DatabaseConfig dbConfig = config.getDatabase();
            
            if (dbConfig.getType() == DatabaseConfig.DatabaseType.MYSQL || 
                dbConfig.getType() == DatabaseConfig.DatabaseType.MARIADB) {
                
                if (dbConfig.getMysql().getHost().trim().isEmpty()) {
                    throw new ConfigurationValidationException("MySQL host cannot be empty");
                }
                
                if (dbConfig.getMysql().getPort() < 1 || dbConfig.getMysql().getPort() > 65535) {
                    throw new ConfigurationValidationException("MySQL port must be between 1 and 65535");
                }
                
                if (dbConfig.getMysql().getDatabase().trim().isEmpty()) {
                    throw new ConfigurationValidationException("MySQL database name cannot be empty");
                }
                
                if (dbConfig.getMysql().getUsername().trim().isEmpty()) {
                    throw new ConfigurationValidationException("MySQL username cannot be empty");
                }
            }
        }
    }
    
    private static class TransactionValidator implements ConfigurationValidator {
        @Override
        public void validate(@NotNull Configuration config) throws ConfigurationValidationException {
            TransactionConfig transactions = config.getTransactions();
            
            if (transactions.getMaxTransferAmount() < 0) {
                throw new ConfigurationValidationException("Maximum transfer amount cannot be negative");
            }
            
            if (transactions.getMinTransferAmount() < 0) {
                throw new ConfigurationValidationException("Minimum transfer amount cannot be negative");
            }
            
            if (transactions.getMinTransferAmount() > transactions.getMaxTransferAmount() && 
                transactions.getMaxTransferAmount() > 0) {
                throw new ConfigurationValidationException("Minimum transfer amount cannot exceed maximum transfer amount");
            }
            
            if (transactions.getTaxRate() < 0 || transactions.getTaxRate() > 1) {
                throw new ConfigurationValidationException("Tax rate must be between 0.0 and 1.0");
            }
            
            if (transactions.getTransferCooldown() < 0) {
                throw new ConfigurationValidationException("Transfer cooldown cannot be negative");
            }
        }
    }
    
    private static class PerformanceValidator implements ConfigurationValidator {
        @Override
        public void validate(@NotNull Configuration config) throws ConfigurationValidationException {
            PerformanceConfig performance = config.getPerformance();
            
            if (performance.getSaveInterval() < 1000) {
                throw new ConfigurationValidationException("Save interval must be at least 1000ms (1 second)");
            }
            
            if (performance.getCacheSize() <= 0) {
                throw new ConfigurationValidationException("Cache size must be positive");
            }
            
            if (performance.getCacheExpire() <= 0) {
                throw new ConfigurationValidationException("Cache expire time must be positive");
            }
        }
    }
    
    private static class FeaturesValidator implements ConfigurationValidator {
        @Override
        public void validate(@NotNull Configuration config) throws ConfigurationValidationException {
            FeaturesConfig features = config.getFeatures();
            
            // Interest system validation
            FeaturesConfig.InterestConfig interest = features.getInterest();
            if (interest.getRate() < 0) {
                throw new ConfigurationValidationException("Interest rate cannot be negative");
            }
            
            if (interest.getMinBalance() < 0) {
                throw new ConfigurationValidationException("Minimum balance for interest cannot be negative");
            }
            
            if (interest.getMaxInterest() < 0) {
                throw new ConfigurationValidationException("Maximum interest amount cannot be negative");
            }
            
            // Banking system validation
            FeaturesConfig.BankingConfig banking = features.getBanking();
            if (banking.getMaxAccountsPerOwner() <= 0) {
                throw new ConfigurationValidationException("Maximum number of bank accounts must be positive");
            }
            
            if (banking.getDefaultInterestRate() < 0 || banking.getDefaultInterestRate() > 1) {
                throw new ConfigurationValidationException("Default interest rate must be between 0.0 and 1.0");
            }
            
            if (banking.getEarlyWithdrawalPenaltyRate() < 0 || banking.getEarlyWithdrawalPenaltyRate() > 1) {
                throw new ConfigurationValidationException("Early withdrawal penalty rate must be between 0.0 and 1.0");
            }
            
            // Validate deposit configuration
            if (banking.getTransactionLimits().getMaxDepositPerTransaction() < 0) {
                throw new ConfigurationValidationException("Maximum deposit per transaction cannot be negative");
            }
            
            if (banking.getTransactionLimits().getMaxWithdrawalPerTransaction() < 0) {
                throw new ConfigurationValidationException("Maximum withdrawal per transaction cannot be negative");
            }
            
            // PvP system validation
            FeaturesConfig.PvPConfig pvp = features.getPvp();
            if (pvp.getKillReward() < 0 || pvp.getKillReward() > 1) {
                throw new ConfigurationValidationException("PvP kill reward rate must be between 0.0 and 1.0");
            }
            
            if (pvp.getMaxReward() < 0) {
                throw new ConfigurationValidationException("PvP maximum reward cannot be negative");
            }
        }
    }
}
