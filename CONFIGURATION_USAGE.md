# YAE Configuration System Usage Guide

This document explains how to use the comprehensive configuration management system implemented for YetAnotherEconomy (YAE).

## Overview

The YAE configuration system provides:

1. **Thread-safe configuration management** with read/write locks
2. **Multi-language support** with lang.yml localization
3. **Comprehensive validation** with built-in and custom validators
4. **Hot-reload functionality** for configuration changes without restart
5. **Type-safe access** to configuration parameters
6. **Centralized management** with unified configuration classes

## Architecture

### Core Components

1. **ConfigManager** - Handles loading/saving of YAML configuration files
2. **LanguageManager** - Manages multi-language text and translations
3. **Configuration** - Main configuration class with type-safe access to system parameters
4. **ConfigurationManager** - Orchestrates all configuration functionality
5. **ConfigValidator** - Utility class for configuration validation

### File Structure

```
src/main/resources/
├── config.yml          # Main plugin configuration
├── lang.yml            # Language translations (Chinese by default)
└── plugin.yml          # Bukkit plugin configuration

src/main/java/com/yae/api/core/config/
├── ConfigManager.java          # Configuration file management
├── ConfigFile.java            # Individual configuration file wrapper
├── LanguageManager.java       # Multi-language support
├── Configuration.java         # Main configuration class
├── ConfigurationManager.java  # Centralized configuration management
└── ConfigValidator.java       # Validation utilities
```

## Basic Usage

### 1. Accessing Configuration

```java
// Get the configuration manager
ConfigurationManager configManager = plugin.getConfigurationManager();

// Get the main configuration
Configuration config = plugin.getMainConfiguration();

// Access configuration sections
CurrencyConfig currency = config.getCurrency();
DatabaseConfig database = config.getDatabase();
TransactionConfig transactions = config.getTransactions();
```

### 2. Using Language System

```java
LanguageManager lang = configManager.getLanguageManager();

// Get translated messages
String welcome = lang.get("common.welcome");
String error = lang.get("errors.invalid-amount");

// With placeholders
Map<String, Object> placeholders = new HashMap<>();
placeholders.put("player", playerName);
placeholders.put("amount", totalAmount);
String message = lang.get("economy.balance.format", placeholders);

// Currency formatting
String formatted = lang.getCurrency("economy.pay.success", amount, currency.getName(), placeholders);
```

### 3. Configuration Validation

```java
// Built-in validation (automatic during load)
Configuration config = new Configuration(configFile, logger);
if (!config.validate()) {
    logger.severe("Configuration validation failed!");
    return;
}

// Custom validation
ConfigValidator.ValidationChain chain = ConfigValidator.chain()
    .requireNotBlank(currency.getName(), "Currency name")
    .requirePositive(amount, "Transaction amount")
    .requireRange(taxRate, 0.0, 1.0, "Tax rate");

if (chain.hasErrors()) {
    logger.warning("Validation errors: " + chain.getErrors());
}
```

## Advanced Usage

### Hot-Reload Configuration

```java
// Enable hot reload (monitors file changes)
configManager.enableHotReload(5000); // Check every 5 seconds

// Manual reload
boolean success = configManager.reload();
if (success) {
    logger.info("Configuration reloaded successfully");
}
```

### Custom Configuration Validation

```java
public class CustomValidator implements Configuration.ConfigurationValidator {
    @Override
    public void validate(Configuration config) throws ConfigurationValidationException {
        // Validate specific business logic
        if (config.getTransactions().getTaxRate() > 0.5) {
            throw new ConfigurationValidationException("Tax rate cannot exceed 50%");
        }
        
        // Use config validator utilities
        ConfigValidator.requireValidIpAddress(dbConfig.getHost(), "Database host");
        ConfigValidator.requirePositive(mysqlConfig.getPoolSize(), "Connection pool size");
    }
}
```

### Service Integration

```java
public class EconomyService implements Service {
    private Configuration configuration;
    private LanguageManager languageManager;
    
    @Override
    public boolean initialize() {
        // Get configuration from plugin
        this.configuration = plugin.getMainConfiguration();
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        
        // Use configuration
        double startingBalance = configuration.getCurrency().getStartingBalance();
        double maxTransfer = configuration.getTransactions().getMaxTransferAmount();
        
        return true;
    }
    
    @Override
    public boolean reload() {
        // Update configuration reference during reload
        this.configuration = plugin.getMainConfiguration();
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        return true;
    }
}
```

## Configuration Sections

### Currency Configuration
```yaml
currency:
  name: "金币"                    # Currency name
  symbol: "§6💰"                # Currency symbol with color codes
  decimals: 2                   # Decimal places
  starting-balance: 100.0       # Initial player balance
```

### Database Configuration
```yaml
database:
  type: sqlite                  # Options: sqlite, mysql, mariadb
  sqlite:
    filename: "economy.db"
  mysql:
    host: "localhost"
    port: 3306
    database: "yet_another_economy"
    username: "root"
    password: ""
    pool-size: 10
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
```

### Transaction Configuration
```yaml
transactions:
  log-transactions: true        # Enable transaction logging
  history-days: 30              # Days to keep transaction history
  max-transfer-amount: 1000000  # Maximum transfer amount (0 = unlimited)
  min-transfer-amount: 0.01     # Minimum transfer amount
  tax-rate: 0.0                 # Transfer tax rate (0.0-1.0)
  enable-transfer-cooldown: false
  transfer-cooldown: 60         # Cooldown in seconds
```

### Language Configuration
```yaml
language-info:
  name: "简体中文"
  code: "zh_cn"
  author: "YAE Team"
  version: "1.0.0"

common:
  prefix: "§6[§eYAE§6] §f"
  success: "§a✓ §f"
  error: "§c✗ §f"
  no-permission: "§c你没有权限执行此操作！"

economy:
  balance:
    format: "§6{amount} §f{currency}"
  pay:
    success: "§a成功转账 §6{amount} §f给 {player}"
```

## Best Practices

### 1. Configuration Access
- Always use the configuration manager to access configuration
- Store configuration reference in your service during initialization
- Update the reference during reload operations
- Validate configuration values before using them

### 2. Error Handling
- Always check if configuration is enabled/available before accessing
- Provide meaningful error messages for configuration issues
- Use try-catch blocks around configuration operations
- Log configuration load/validation errors appropriately

### 3. Validation
- Validate configuration during initialization and reload
- Use the built-in ConfigValidator utility methods
- Implement custom validators for specific business logic
- Provide clear error messages for validation failures

### 4. Thread Safety
- The configuration system is thread-safe
- Configuration objects are immutable after creation
- Use the provided lock mechanisms for custom operations
- Avoid modifying configuration data directly

### 5. Performance Considerations
- Cache frequently accessed configuration values in your service
- Use the configuration manager's caching mechanisms
- Avoid repeated configuration lookups in performance-critical code
- Consider enabling hot-reload only for development

## Migration Guide

### From Legacy Configuration
If you have existing configuration code, migrate to the new system:

```java
// Old way (direct file access)
File configFile = new File(plugin.getDataFolder(), "config.yml");
YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
double taxRate = yaml.getDouble("tax-rate", 0.1);

// New way
Configuration config = plugin.getMainConfiguration();
double taxRate = config.getTransactions().getTaxRate();
```

### Custom Configuration Files
For additional configuration files:

```java
// Load custom configuration
ConfigFile customConfig = configManager.loadConfig("custom.yml");

// Access with type safety
String setting = customConfig.getString("section.subsection.setting", "default");
int number = customConfig.getInt("section.number", 0);
boolean flag = customConfig.getBoolean("section.flag", true);
```

## Troubleshooting

### Common Issues

1. **Configuration not loading**: Check file paths and YAML syntax
2. **Validation failures**: Review error messages and configuration values
3. **Hot-reload not working**: Verify file permissions and watch service availability
4. **Language not loading**: Ensure lang.yml exists and has proper syntax

### Debug Mode
Enable debug mode to get detailed configuration loading information:

```java
plugin.setDebugMode(true);
```

### Logging Configuration Issues
The configuration system provides detailed logging. Check the logs for:
- Configuration validation results
- Hot-reload events
- Language loading status
- File system errors

## Conclusion

The YAE configuration system provides a robust, thread-safe, and user-friendly way to manage all plugin configuration. With built-in validation, hot-reload support, and comprehensive language management, it ensures your plugin configuration is reliable and maintainable.
