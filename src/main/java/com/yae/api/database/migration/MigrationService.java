package com.yae.api.database.migration;

import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.core.Service;
import com.yae.api.database.DatabaseManager;
import com.yae.api.database.DatabaseService;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Database migration service that manages database schema changes using a Flyway/Liquibase-style approach.
 * Supports version control, rollback functionality, and automatic execution on startup.
 */
public final class MigrationService implements com.yae.api.core.Service {
    
    private final YAECore core;
    private DatabaseManager databaseManager;
    private final MigrationConfig config;
    private final Logger logger;
    private final Map<String, Migration> migrations;
    private final List<String> executedMigrations;
    
    public MigrationService(@NotNull YAECore core, @NotNull MigrationConfig config) {
        this.core = core;
        this.config = config;
        this.logger = core.getLogger();
        this.migrations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.executedMigrations = new CopyOnWriteArrayList<>();
    }
    
    @Override
    public boolean initialize() {
        logger.info("Initializing MigrationService...");
        
        try {
            // Get database manager from the registered database service
            DatabaseService databaseService = core.getService(ServiceType.DATABASE);
            if (databaseService == null) {
                logger.severe("DatabaseService not found, cannot initialize MigrationService");
                return false;
            }
            
            this.databaseManager = databaseService.getDatabaseManager();
            if (this.databaseManager == null) {
                logger.severe("DatabaseManager not available, cannot initialize MigrationService");
                return false;
            }
            
            // Ensure schema history table exists
            if (!ensureSchemaHistoryTable()) {
                logger.severe("Failed to create schema history table");
                return false;
            }
            
            // Discover available migrations
            discoverMigrations();
            
            // Load executed migration history
            loadMigrationHistory();
            
            // Execute pending migrations if enabled
            if (config.isAutoRun()) {
                CompletableFuture<MigrationResult> result = executeMigrations();
                try {
                    MigrationResult migrationResult = result.get();
                    if (migrationResult.isSuccess()) {
                        logger.info("Migrations completed successfully");
                    } else {
                        logger.severe("Migration execution failed: " + migrationResult.getErrorMessage());
                        return false;
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to execute migrations", e);
                    return false;
                }
            }
            
            logger.info("MigrationService initialized successfully with " + migrations.size() + " available migrations");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize MigrationService", e);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down MigrationService...");
        migrations.clear();
        executedMigrations.clear();
        databaseManager = null;
        logger.info("MigrationService shutdown completed");
    }
    
    @Override
    @NotNull
    public String getName() {
        return "MigrationService";
    }
    
    @Override
    @NotNull
    public ServiceType getType() {
        return ServiceType.DATABASE;
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return serviceType == ServiceType.DATABASE;
    }
    
    @Override
    public boolean isEnabled() {
        return true; // Always enabled
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        // MigrationService is always enabled
    }
    
    @Override
    public com.yae.api.core.ServiceConfig getConfig() {
        return null; // No specific config
    }
    
    @Override
    public void setConfig(com.yae.api.core.ServiceConfig config) {
        // No specific config
    }
    
    @Override
    public boolean reload() {
        // No reload functionality needed
        return true;
    }
    
    /**
     * Executes pending migrations synchronously.
     * @return migration execution result
     */
    @NotNull
    public CompletableFuture<MigrationResult> executeMigrations() {
        return CompletableFuture.supplyAsync(() -> {
            return executePendingMigrations();
        });
    }
    
    /**
     * Rolls back migrations to a specific version.
     * @param targetVersion the target version to rollback to, or empty for baseline
     * @return rollback result
     */
    @NotNull
    public CompletableFuture<MigrationResult> rollbackToVersion(@Nullable String targetVersion) {
        return CompletableFuture.supplyAsync(() -> {
            return performRollback(targetVersion);
        });
    }
    
    /**
     * Gets the current schema version.
     * @return current version or null if not tracked
     */
    @Nullable
    public String getCurrentVersion() {
        if (executedMigrations.isEmpty()) {
            return null;
        }
        return executedMigrations.get(executedMigrations.size() - 1);
    }
    
    /**
     * Gets the list of pending migrations.
     * @return list of pending migration versions
     */
    @NotNull
    public List<String> getPendingMigrations() {
        List<String> pending = new ArrayList<>();
        for (String version : migrations.keySet()) {
            if (!executedMigrations.contains(version)) {
                pending.add(version);
            }
        }
        return pending;
    }
    
    /**
     * Gets migration history.
     * @return list of executed migrations
     */
    @NotNull
    public List<MigrationHistory> getMigrationHistory() {
        List<MigrationHistory> history = new ArrayList<>();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        
        try {
            connection = databaseManager.getConnection();
            if (connection == null) {
                logger.warning("Cannot get migration history: no database connection");
                return history;
            }
            
            String sql = "SELECT version, description, execution_time, success, executed_at, checksum " +
                        "FROM " + config.getSchemaHistoryTable() + " ORDER BY version";
            
            statement = connection.prepareStatement(sql);
            resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                history.add(new MigrationHistory(
                    resultSet.getString("version"),
                    resultSet.getString("description"),
                    resultSet.getLong("execution_time"),
                    resultSet.getBoolean("success"),
                    resultSet.getTimestamp("executed_at"),
                    resultSet.getString("checksum")
                ));
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load migration history", e);
        } finally {
            closeResource(resultSet);
            closeResource(statement);
            closeResource(connection);
        }
        
        return history;
    }
    
    /**
     * Validates a migration before execution.
     * @param migration the migration to validate
     * @return true if migration is valid
     */
    public boolean validateMigration(@NotNull Migration migration) {
        if (migration.getVersion() == null || migration.getVersion().trim().isEmpty()) {
            logger.warning("Migration version cannot be empty");
            return false;
        }
        
        if (migration.getDescription() == null || migration.getDescription().trim().isEmpty()) {
            logger.warning("Migration description cannot be empty");
            return false;
        }
        
        if (migration.getUpScript() == null || migration.getUpScript().trim().isEmpty()) {
            logger.warning("Migration up script cannot be empty");
            return false;
        }
        
        return true;
    }
    
    /**
     * Registers a new migration.
     * @param migration the migration to register
     */
    public void registerMigration(@NotNull Migration migration) {
        migrations.put(migration.getVersion(), migration);
        logger.fine("Registered migration: " + migration.getVersion());
    }
    
    private boolean ensureSchemaHistoryTable() {
        Connection connection = null;
        PreparedStatement statement = null;
        
        try {
            connection = databaseManager.getConnection();
            if (connection == null) {
                logger.severe("Cannot create schema history table: no database connection");
                return false;
            }
            
            String createTableSql = config.getSchemaHistoryTableDDL(getDatabaseType());
            statement = connection.prepareStatement(createTableSql);
            statement.execute();
            connection.commit();
            
            logger.info("Schema history table created successfully");
            return true;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create schema history table", e);
            return false;
        } finally {
            closeResource(statement);
            closeResource(connection);
        }
    }
    
    private void discoverMigrations() {
        logger.info("Discovering migrations...");
        
        // Create built-in migrations based on database design
        createBuiltInMigrations();
        
        logger.info("Discovered " + migrations.size() + " migrations");
    }
    
    private void createBuiltInMigrations() {
        // V1__Initial_Schema.sql
        registerMigration(Migration.builder()
                .version("1")
                .description("Initial Schema")
                .upScript(getInitialSchemaScript())
                .downScript(getInitialSchemaRollbackScript())
                .priority(1)
                .build());
        
        // V2__Initial_Data.sql
        registerMigration(Migration.builder()
                .version("2")
                .description("Initial Data")
                .upScript(getInitialDataScript())
                .downScript(getInitialDataRollbackScript())
                .priority(2)
                .build());
        
        // V3__Index_Optimizations.sql
        registerMigration(Migration.builder()
                .version("3")
                .description("Index Optimizations")
                .upScript(getIndexOptimizationsScript())
                .downScript(getIndexOptimizationsRollbackScript())
                .priority(3)
                .build());
    }
    
    private void loadMigrationHistory() {
        executedMigrations.clear();
        
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        
        try {
            connection = databaseManager.getConnection();
            if (connection == null) {
                logger.warning("Cannot load migration history: no database connection");
                return;
            }
            
            String sql = "SELECT version FROM " + config.getSchemaHistoryTable() + 
                        " WHERE success = 1 ORDER BY version";
            
            statement = connection.prepareStatement(sql);
            resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                executedMigrations.add(resultSet.getString("version"));
            }
            
            logger.info("Loaded migration history: " + executedMigrations.size() + " migrations executed");
            
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load migration history (this might be expected on first run)", e);
        } finally {
            closeResource(resultSet);
            closeResource(statement);
            closeResource(connection);
        }
    }
    
    @NotNull
    private MigrationResult executePendingMigrations() {
        List<String> pending = getPendingMigrations();
        
        if (pending.isEmpty()) {
            return MigrationResult.success("All migrations are up to date");
        }
        
        logger.info("Executing " + pending.size() + " pending migrations...");
        
        for (String version : pending) {
            Migration migration = migrations.get(version);
            if (migration == null) {
                return MigrationResult.failure("Migration not found: " + version);
            }
            
            if (!executeMigration(migration)) {
                return MigrationResult.failure("Migration failed: " + version);
            }
        }
        
        return MigrationResult.success("All migrations executed successfully");
    }
    
    private boolean executeMigration(@NotNull Migration migration) {
        logger.info("Executing migration: " + migration.getVersion() + " - " + migration.getDescription());
        
        if (!validateMigration(migration)) {
            logger.severe("Migration validation failed: " + migration.getVersion());
            return false;
        }
        
        Connection connection = null;
        
        try {
            connection = databaseManager.getConnection();
            if (connection == null) {
                logger.severe("Cannot execute migration: no database connection");
                return false;
            }
            
            connection.setAutoCommit(false);
            
            long startTime = System.currentTimeMillis();
            boolean success = executeMigrationScript(connection, migration.getUpScript());
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (success) {
                recordMigration(connection, migration, executionTime, true);
                connection.commit();
                
                executedMigrations.add(migration.getVersion());
                logger.info("Migration completed successfully: " + migration.getVersion() + " (" + executionTime + "ms)");
                return true;
            } else {
                connection.rollback();
                recordMigration(connection, migration, executionTime, false);
                logger.severe("Migration failed: " + migration.getVersion());
                return false;
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during migration: " + migration.getVersion(), e);
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "Migration rollback failed", rollbackEx);
                }
            }
            return false;
        } finally {
            closeResource(connection);
        }
    }
    
    @NotNull
    private MigrationResult performRollback(@Nullable String targetVersion) {
        logger.info("Performing rollback to version: " + (targetVersion != null ? targetVersion : "baseline"));
        
        List<String> migrationsToRollback = new ArrayList<>();
        
        if (targetVersion == null) {
            // Rollback all
            migrationsToRollback.addAll(executedMigrations);
            Collections.reverse(migrationsToRollback);
        } else {
            // Rollback to specific version
            boolean found = false;
            for (int i = executedMigrations.size() - 1; i >= 0; i--) {
                String version = executedMigrations.get(i);
                if (version.equals(targetVersion)) {
                    found = true;
                    break;
                }
                migrationsToRollback.add(version);
            }
            
            if (!found) {
                return MigrationResult.failure("Target version not found in migration history: " + targetVersion);
            }
        }
        
        logger.info("Rolling back " + migrationsToRollback.size() + " migrations...");
        
        for (String version : migrationsToRollback) {
            Migration migration = migrations.get(version);
            if (migration == null) {
                logger.warning("Migration not found for rollback: " + version);
                continue;
            }
            
            if (!rollbackMigration(migration)) {
                return MigrationResult.failure("Rollback failed: " + version);
            }
        }
        
        return MigrationResult.success("Rollback completed successfully");
    }
    
    private boolean rollbackMigration(@NotNull Migration migration) {
        logger.info("Rolling back migration: " + migration.getVersion());
        
        Connection connection = null;
        
        try {
            connection = databaseManager.getConnection();
            if (connection == null) {
                logger.severe("Cannot rollback migration: no database connection");
                return false;
            }
            
            connection.setAutoCommit(false);
            
            boolean success = executeMigrationScript(connection, migration.getDownScript());
            
            if (success) {
                removeMigrationRecord(connection, migration.getVersion());
                connection.commit();
                
                executedMigrations.remove(migration.getVersion());
                logger.info("Migration rolled back successfully: " + migration.getVersion());
                return true;
            } else {
                connection.rollback();
                logger.severe("Migration rollback failed: " + migration.getVersion());
                return false;
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during rollback: " + migration.getVersion(), e);
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "Rollback rollback failed", rollbackEx);
                }
            }
            return false;
        } finally {
            closeResource(connection);
        }
    }
    
    private boolean executeMigrationScript(@NotNull Connection connection, @NotNull String script) throws SQLException {
        String[] statements = script.split(";");
        
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            try (PreparedStatement pstmt = connection.prepareStatement(trimmed)) {
                pstmt.execute();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error executing SQL statement: " + trimmed, e);
                return false;
            }
        }
        
        return true;
    }
    
    private void recordMigration(@NotNull Connection connection, @NotNull Migration migration, 
                                 long executionTime, boolean success) throws SQLException {
        String sql = "INSERT INTO " + config.getSchemaHistoryTable() + 
                    " (version, description, checksum, execution_time, success, executed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, migration.getVersion());
            statement.setString(2, migration.getDescription());
            statement.setString(3, calculateChecksum(migration.getUpScript()));
            statement.setLong(4, executionTime);
            statement.setBoolean(5, success);
            statement.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));
            statement.executeUpdate();
        }
    }
    
    private void removeMigrationRecord(@NotNull Connection connection, @NotNull String version) throws SQLException {
        String sql = "DELETE FROM " + config.getSchemaHistoryTable() + " WHERE version = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, version);
            statement.executeUpdate();
        }
    }
    
    @NotNull
    private String calculateChecksum(@NotNull String content) {
        return String.valueOf(content.hashCode());
    }
    
    private DatabaseType getDatabaseType() {
        // This would need to be implemented based on the actual database type from configuration
        return DatabaseType.SQLITE; // Default fallback
    }
    
    private void closeResource(@Nullable AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                logger.warning("Failed to close resource: " + e.getMessage());
            }
        }
    }
    
    // Built-in migration scripts
    
    @NotNull
    private String getInitialSchemaScript() {
        return """
            -- Price table
            CREATE TABLE IF NOT EXISTS prices (
                item_hash TEXT PRIMARY KEY,
                item_nbt BLOB,
                standard_price REAL,
                min_price REAL,
                max_price REAL,
                liquidity TEXT,
                update_count INTEGER DEFAULT 0,
                last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            -- Price history table
            CREATE TABLE IF NOT EXISTS price_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_hash TEXT,
                price REAL,
                amount INTEGER,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (item_hash) REFERENCES prices(item_hash)
            );
            
            -- Ledger table
            CREATE TABLE IF NOT EXISTS ledger (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                player_uuid TEXT,
                counterparty_uuid TEXT,
                item_hash TEXT,
                amount INTEGER,
                price REAL,
                total REAL,
                tax REAL,
                fee REAL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status TEXT DEFAULT 'COMPLETED',
                failure_reason TEXT
            );
            
            -- Accounts table
            CREATE TABLE IF NOT EXISTS accounts (
                uuid TEXT PRIMARY KEY,
                type TEXT DEFAULT 'PLAYER',
                current_balance REAL DEFAULT 0.0,
                credit_score INTEGER DEFAULT 100,
                daily_buy_limit_used INTEGER DEFAULT 0,
                daily_sell_limit_used INTEGER DEFAULT 0,
                last_limit_reset TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            -- Fixed deposits table
            CREATE TABLE IF NOT EXISTS fixed_deposits (
                id TEXT PRIMARY KEY,
                account_uuid TEXT,
                principal REAL,
                interest_rate REAL,
                start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                maturity_time TIMESTAMP,
                auto_renew BOOLEAN DEFAULT FALSE,
                FOREIGN KEY (account_uuid) REFERENCES accounts(uuid)
            );
            
            -- Loans table
            CREATE TABLE IF NOT EXISTS loans (
                id TEXT PRIMARY KEY,
                borrower_uuid TEXT,
                type TEXT,
                principal REAL,
                interest_rate REAL,
                remaining REAL,
                status TEXT DEFAULT 'ACTIVE',
                next_due_date TIMESTAMP,
                FOREIGN KEY (borrower_uuid) REFERENCES accounts(uuid)
            );
            
            -- Organizations table
            CREATE TABLE IF NOT EXISTS organizations (
                id TEXT PRIMARY KEY,
                name TEXT UNIQUE,
                creator_uuid TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            -- Organization members table
            CREATE TABLE IF NOT EXISTS org_members (
                org_id TEXT,
                player_uuid TEXT,
                role TEXT DEFAULT 'MEMBER',
                joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (org_id, player_uuid),
                FOREIGN KEY (org_id) REFERENCES organizations(id)
            );
            
            -- Risk records table
            CREATE TABLE IF NOT EXISTS risk_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT,
                score INTEGER,
                factors TEXT,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            -- Device links table
            CREATE TABLE IF NOT EXISTS device_links (
                player_uuid TEXT,
                ip_address TEXT,
                device_id TEXT,
                last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, ip_address, device_id)
            );
            """;
    }
    
    @NotNull
    private String getInitialSchemaRollbackScript() {
        return """
            DROP TABLE IF EXISTS device_links;
            DROP TABLE IF EXISTS risk_records;
            DROP TABLE IF EXISTS org_members;
            DROP TABLE IF EXISTS organizations;
            DROP TABLE IF EXISTS loans;
            DROP TABLE IF EXISTS fixed_deposits;
            DROP TABLE IF EXISTS accounts;
            DROP TABLE IF EXISTS ledger;
            DROP TABLE IF EXISTS price_history;
            DROP TABLE IF EXISTS prices;
            """;
    }
    
    @NotNull
    private String getInitialDataScript() {
        return """
            -- Insert default price records for common items (empty initially)
            
            -- Insert admin account for testing (UUID is a placeholder)
            INSERT OR IGNORE INTO accounts (uuid, type, current_balance, credit_score) VALUES
            ('00000000-0000-0000-0000-000000000000', 'ADMIN', 999999.0, 999);
            
            -- Insert system organization for managing system funds
            INSERT OR IGNORE INTO organizations (id, name, creator_uuid) VALUES
            ('SYSTEM', 'YetAnotherEconomy_System', '00000000-0000-0000-0000-000000000000');
            """;
    }
    
    @NotNull
    private String getInitialDataRollbackScript() {
        return """
            DELETE FROM accounts WHERE uuid = '00000000-0000-0000-0000-000000000000';
            DELETE FROM organizations WHERE id = 'SYSTEM';
            """;
    }
    
    @NotNull
    private String getIndexOptimizationsScript() {
        return """
            -- Create indexes for performance
            CREATE INDEX IF NOT EXISTS idx_ledger_player_uuid ON ledger(player_uuid);
            CREATE INDEX IF NOT EXISTS idx_ledger_timestamp ON ledger(timestamp);
            CREATE INDEX IF NOT EXISTS idx_ledger_status ON ledger(status);
            
            CREATE INDEX IF NOT EXISTS idx_price_history_item_hash ON price_history(item_hash);
            CREATE INDEX IF NOT EXISTS idx_price_history_timestamp ON price_history(timestamp);
            
            CREATE INDEX IF NOT EXISTS idx_accounts_uuid ON accounts(uuid);
            CREATE INDEX IF NOT EXISTS idx_accounts_type ON accounts(type);
            
            CREATE INDEX IF NOT EXISTS idx_fixed_deposits_account_uuid ON fixed_deposits(account_uuid);
            CREATE INDEX IF NOT EXISTS idx_fixed_deposits_maturity_time ON fixed_deposits(maturity_time);
            
            CREATE INDEX IF NOT EXISTS idx_loans_borrower_uuid ON loans(borrower_uuid);
            CREATE INDEX IF NOT EXISTS idx_loans_status ON loans(status);
            CREATE INDEX IF NOT EXISTS idx_loans_next_due_date ON loans(next_due_date);
            
            CREATE INDEX IF NOT EXISTS idx_org_members_org_id ON org_members(org_id);
            CREATE INDEX IF NOT EXISTS idx_org_members_player_uuid ON org_members(player_uuid);
            
            CREATE INDEX IF NOT EXISTS idx_risk_records_player_uuid ON risk_records(player_uuid);
            CREATE INDEX IF NOT EXISTS idx_risk_records_timestamp ON risk_records(timestamp);
            
            CREATE INDEX IF NOT EXISTS idx_device_links_player_uuid ON device_links(player_uuid);
            CREATE INDEX IF NOT EXISTS idx_device_links_ip_address ON device_links(ip_address);
            """;
    }
    
    @NotNull
    private String getIndexOptimizationsRollbackScript() {
        return """
            DROP INDEX IF EXISTS idx_ledger_player_uuid;
            DROP INDEX IF EXISTS idx_ledger_timestamp;
            DROP INDEX IF EXISTS idx_ledger_status;
            
            DROP INDEX IF EXISTS idx_price_history_item_hash;
            DROP INDEX IF EXISTS idx_price_history_timestamp;
            
            DROP INDEX IF EXISTS idx_accounts_uuid;
            DROP INDEX IF EXISTS idx_accounts_type;
            
            DROP INDEX IF EXISTS idx_fixed_deposits_account_uuid;
            DROP INDEX IF EXISTS idx_fixed_deposits_maturity_time;
            
            DROP INDEX IF EXISTS idx_loans_borrower_uuid;
            DROP INDEX IF EXISTS idx_loans_status;
            DROP INDEX IF EXISTS idx_loans_next_due_date;
            
            DROP INDEX IF EXISTS idx_org_members_org_id;
            DROP INDEX IF EXISTS idx_org_members_player_uuid;
            
            DROP INDEX IF EXISTS idx_risk_records_player_uuid;
            DROP INDEX IF EXISTS idx_risk_records_timestamp;
            
            DROP INDEX IF EXISTS idx_device_links_player_uuid;
            DROP INDEX IF EXISTS idx_device_links_ip_address;
            """;
    }
    
    // Result classes
    
    public static final class MigrationResult {
        private final boolean success;
        private final String message;
        private final String errorMessage;
        
        private MigrationResult(boolean success, String message, String errorMessage) {
            this.success = success;
            this.message = message;
            this.errorMessage = errorMessage;
        }
        
        public static MigrationResult success(String message) {
            return new MigrationResult(true, message, null);
        }
        
        public static MigrationResult failure(String errorMessage) {
            return new MigrationResult(false, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getErrorMessage() { return errorMessage; }
        
        @Override
        public String toString() {
            return success ? "SUCCESS: " + message : "FAILURE: " + errorMessage;
        }
    }
    
    public static final class MigrationHistory {
        private final String version;
        private final String description;
        private final long executionTime;
        private final boolean success;
        private final java.util.Date executedAt;
        private final String checksum;
        
        public MigrationHistory(String version, String description, long executionTime, 
                              boolean success, java.util.Date executedAt, String checksum) {
            this.version = version;
            this.description = description;
            this.executionTime = executionTime;
            this.success = success;
            this.executedAt = executedAt;
            this.checksum = checksum;
        }
        
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public long getExecutionTime() { return executionTime; }
        public boolean isSuccess() { return success; }
        public java.util.Date getExecutedAt() { return executedAt; }
        public String getChecksum() { return checksum; }
    }
    
    public enum DatabaseType {
        SQLITE, MYSQL, MARIADB
    }
}
