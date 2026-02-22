package com.yae.api.database;

import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.core.config.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Database service that integrates with the YAE core service architecture.
 */
public final class DatabaseService implements com.yae.api.core.Service {
    
    private final YAECore core;
    private DatabaseManager databaseManager;
    private DatabaseHealthChecker healthChecker;
    private com.yae.api.core.ServiceConfig config;
    private boolean enabled = true;
    
    public DatabaseService(@NotNull YAECore core) {
        this.core = core;
        this.healthChecker = DatabaseHealthChecker.DEFAULT;
    }
    
    @Override
    public String getName() {
        return "DatabaseService";
    }
    
    @Override
    public ServiceType getType() {
        return ServiceType.DATABASE;
    }
    
    @Override
    public com.yae.api.core.ServiceConfig getConfig() {
        return config;
    }
    
    @Override
    public void setConfig(com.yae.api.core.ServiceConfig config) {
        this.config = config;
    }
    
    @Override
    public boolean dependsOn(ServiceType serviceType) {
        // DatabaseService doesn't depend on any other specific services
        return false;
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
    public boolean initialize() {
        core.info("Initializing DatabaseService...");
        
        try {
            Configuration.DatabaseConfig dbConfig = core.getMainConfiguration().getDatabase();
            
            if (!validateDatabaseConfig(dbConfig)) {
                core.error("Database configuration validation failed");
                return false;
            }
            
            databaseManager = new DatabaseManager(core.getLogger(), dbConfig);
            
            if (!databaseManager.initialize()) {
                core.error("Failed to initialize DatabaseManager");
                return false;
            }
            
            core.info("DatabaseService initialized successfully");
            return true;
            
        } catch (Exception e) {
            core.error("Failed to initialize DatabaseService", e);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        core.info("Shutting down DatabaseService...");
        
        try {
            if (databaseManager != null) {
                databaseManager.shutdown();
                core.info("DatabaseService shutdown completed");
            }
            
        } catch (Exception e) {
            core.error("Error during DatabaseService shutdown", e);
        } finally {
            databaseManager = null;
        }
    }
    
    @Override
    public boolean reload() {
        core.info("Reloading DatabaseService...");
        
        try {
            if (databaseManager != null) {
                boolean success = databaseManager.reload();
                if (success) {
                    core.info("DatabaseService reloaded successfully");
                } else {
                    core.warn("DatabaseService reload completed with warnings");
                }
                return success;
            }
            
            // If no current manager, initialize a new one
            return initialize();
            
        } catch (Exception e) {
            core.error("Failed to reload DatabaseService", e);
            return false;
        }
    }
    
    /**
     * Gets the database manager instance.
     * @return the database manager, or null if not initialized
     */
    @Nullable
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * Checks if the database is healthy and ready for operations.
     * @return true if database is healthy
     */
    public boolean isDatabaseHealthy() {
        return databaseManager != null && databaseManager.isHealthy();
    }
    
    /**
     * Performs a database health check using the configured health checker.
     * @return true if health check passes
     */
    public boolean performHealthCheck() {
        if (databaseManager == null) {
            return false;
        }
        
        return healthChecker.checkHealth(databaseManager).isHealthy();
    }
    
    /**
     * Sets a custom health checker for the database.
     * @param healthChecker the health checker to use
     */
    public void setHealthChecker(@NotNull DatabaseHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }
    
    /**
     * Validates the database configuration.
     * @param dbConfig the database configuration to validate
     * @return true if configuration is valid
     */
    private boolean validateDatabaseConfig(@NotNull Configuration.DatabaseConfig dbConfig) {
        if (dbConfig == null) {
            core.error("Database configuration is null");
            return false;
        }
        
        switch (dbConfig.getType()) {
            case SQLITE:
                return validateSQLiteConfig(dbConfig.getSqlite());
            case MYSQL:
            case MARIADB:
                return validateMySQLConfig(dbConfig.getMysql());
            default:
                core.error("Unsupported database type: " + dbConfig.getType());
                return false;
        }
    }
    
    private boolean validateSQLiteConfig(@NotNull Configuration.DatabaseConfig.SqliteConfig sqliteConfig) {
        if (sqliteConfig.getFilename() == null || sqliteConfig.getFilename().trim().isEmpty()) {
            core.error("SQLite filename cannot be empty");
            return false;
        }
        
        if (sqliteConfig.getFilename().contains("..") || sqliteConfig.getFilename().contains("/") || 
            sqliteConfig.getFilename().contains("\\")) {
            core.error("SQLite filename contains invalid characters");
            return false;
        }
        
        return true;
    }
    
    private boolean validateMySQLConfig(@NotNull Configuration.DatabaseConfig.MysqlConfig mysqlConfig) {
        if (mysqlConfig.getHost() == null || mysqlConfig.getHost().trim().isEmpty()) {
            core.error("MySQL host cannot be empty");
            return false;
        }
        
        if (mysqlConfig.getPort() < 1 || mysqlConfig.getPort() > 65535) {
            core.error("MySQL port must be between 1 and 65535");
            return false;
        }
        
        if (mysqlConfig.getDatabase() == null || mysqlConfig.getDatabase().trim().isEmpty()) {
            core.error("MySQL database name cannot be empty");
            return false;
        }
        
        if (mysqlConfig.getUsername() == null) {
            core.error("MySQL username cannot be null");
            return false;
        }
        
        if (mysqlConfig.getPoolSize() < 1 || mysqlConfig.getPoolSize() > 100) {
            core.error("MySQL pool size must be between 1 and 100");
            return false;
        }
        
        if (mysqlConfig.getConnectionTimeout() < 1000) {
            core.error("MySQL connection timeout must be at least 1000ms");
            return false;
        }
        
        return true;
    }
}
