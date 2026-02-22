package com.yae.api.database;

import com.yae.api.core.Service;
import com.yae.api.core.config.Configuration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Database connection manager with connection pooling, health checks, and automatic reconnection.
 * Supports SQLite, MySQL, and MariaDB with thread-safe operations.
 */
public final class DatabaseManager implements Service {
    
    private static final String SERVICE_NAME = "DatabaseManager";
    private static final int SERVICE_PRIORITY = 10;
    private static final long HEALTH_CHECK_INTERVAL = 30000; // 30 seconds
    private static final long RECONNECT_DELAY = 5000; // 5 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    
    private final Logger logger;
    private final Configuration.DatabaseConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ReentrantReadWriteLock connectionLock = new ReentrantReadWriteLock();
    
    private volatile HikariDataSource dataSource;
    private volatile Thread healthCheckThread;
    private volatile boolean shutdown = false;
    
    public DatabaseManager(@NotNull Logger logger, @NotNull Configuration.DatabaseConfig config) {
        this.logger = logger;
        this.config = config;
    }
    
    @Override
    @NotNull
    public String getName() {
        return SERVICE_NAME;
    }
    
    @Override
    @NotNull
    public com.yae.api.core.ServiceType getType() {
        return com.yae.api.core.ServiceType.DATABASE;
    }
    
    @Override
    public int getPriority() {
        return SERVICE_PRIORITY;
    }
    
    @Override
    public boolean dependsOn(@NotNull com.yae.api.core.ServiceType serviceType) {
        return false; // DatabaseManager doesn't depend on other services
    }
    
    @Override
    public boolean isEnabled() {
        return healthy.get();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && isEnabled()) {
            shutdown();
        }
    }
    
    @Override
    public com.yae.api.core.ServiceConfig getConfig() {
        return null; // No specific config for DatabaseManager
    }
    
    @Override
    public void setConfig(com.yae.api.core.ServiceConfig config) {
        // DatabaseManager doesn't use ServiceConfig
    }
    
    @Override
    @NotNull
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    @NotNull
    public String getDescription() {
        return "Database connection manager with connection pooling";
    }
    
    @Override
    public boolean validateConfig() {
        return config != null;
    }
    
    @Override
    @NotNull
    public String getDiagnosticInfo() {
        try {
            boolean connected = initialized.get() && healthy.get();
            return String.format("DatabaseManager{%s, connected=%s, healthy=%s}",
                getName(), connected, connected);
        } catch (Exception e) {
            return "DatabaseManager{unavailable: " + e.getMessage() + "}";
        }
    }
    
    /**
     * Checks if the database connection is healthy.
     * @return true if the database is healthy and enabled
     */
    @Override
    public boolean isHealthy() {
        return initialized.get() && healthy.get() && isEnabled();
    }
    
    @Override
    public boolean initialize() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("DatabaseManager is already initialized");
            return true;
        }
        
        logger.info("Initializing DatabaseManager...");
        
        try {
            // Initialize connection pool
            initializeConnectionPool();
            
            // Test initial connection
            if (!testConnection()) {
                logger.severe("Initial database connection test failed");
                return false;
            }
            
            // Start health check thread
            startHealthCheckThread();
            
            healthy.set(true);
            logger.info("DatabaseManager initialized successfully");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize DatabaseManager", e);
            initialized.set(false);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        if (!initialized.compareAndSet(true, false)) {
            return;
        }
        
        logger.info("Shutting down DatabaseManager...");
        shutdown = true;
        
        try {
            // Stop health check thread
            if (healthCheckThread != null) {
                healthCheckThread.interrupt();
                healthCheckThread = null;
            }
            
            // Close connection pool
            connectionLock.writeLock().lock();
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                    logger.info("Database connection pool closed");
                }
            } finally {
                connectionLock.writeLock().unlock();
            }
            
            healthy.set(false);
            logger.info("DatabaseManager shutdown completed");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during DatabaseManager shutdown", e);
        }
    }
    
    @Override
    public boolean reload() {
        logger.info("Reloading DatabaseManager...");
        
        // Shutdown current instance
        if (initialized.get()) {
            shutdown();
        }
        
        // Re-initialize
        return initialize();
    }
    
    /**
     * Gets a database connection from the pool.
     * @return a database connection, or null if unavailable
     */
    @Nullable
    public Connection getConnection() {
        if (!isHealthy()) {
            logger.warning("Attempted to get connection while database is unhealthy");
            return null;
        }
        
        connectionLock.readLock().lock();
        try {
            if (dataSource == null || dataSource.isClosed()) {
                logger.warning("Connection pool is not available");
                return null;
            }
            
            Connection connection = dataSource.getConnection();
            if (connection == null || connection.isClosed()) {
                logger.warning("Failed to obtain valid connection from pool");
                return null;
            }
            
            return connection;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get database connection", e);
            markUnhealthy();
            return null;
        } finally {
            connectionLock.readLock().unlock();
        }
    }
    
    /**
     * Executes a database query with automatic resource management.
     * @param query the SQL query to execute
     * @param params query parameters
     * @return CompletableFuture with ResultSet, or null on error
     */
    @NotNull
    public CompletableFuture<ResultSet> executeQuery(@NotNull String query, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            Connection connection = null;
            PreparedStatement statement = null;
            
            try {
                connection = getConnection();
                if (connection == null) {
                    throw new SQLException("Failed to obtain database connection");
                }
                
                statement = connection.prepareStatement(query);
                setParameters(statement, params);
                
                return statement.executeQuery();
                
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database query execution failed: " + query, e);
                closeQuietly(statement);
                closeQuietly(connection);
                throw new RuntimeException("Database query failed", e);
            }
        });
    }
    
    /**
     * Executes a database update with automatic resource management.
     * @param query the SQL update statement
     * @param params query parameters
     * @return CompletableFuture with number of affected rows, or -1 on error
     */
    @NotNull
    public CompletableFuture<Integer> executeUpdate(@NotNull String query, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            Connection connection = null;
            PreparedStatement statement = null;
            
            try {
                connection = getConnection();
                if (connection == null) {
                    throw new SQLException("Failed to obtain database connection");
                }
                
                statement = connection.prepareStatement(query);
                setParameters(statement, params);
                
                return statement.executeUpdate();
                
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database update execution failed: " + query, e);
                throw new RuntimeException("Database update failed", e);
            } finally {
                closeQuietly(statement);
                closeQuietly(connection);
            }
        });
    }
    
    /**
     * Performs a health check on the database connection.
     * @return true if the connection is healthy
     */
    public boolean performHealthCheck() {
        if (shutdown) {
            return false;
        }
        
        boolean checkResult = testConnection();
        
        if (checkResult) {
            if (!healthy.get()) {
                logger.info("Database connection restored to healthy state");
                healthy.set(true);
                reconnectAttempts.set(0);
            }
        } else {
            if (healthy.get()) {
                logger.warning("Database connection health check failed");
                markUnhealthy();
            }
        }
        
        return checkResult;
    }
    
    /**
     * Gets database connection statistics.
     * @return connection pool statistics
     */
    @NotNull
    public DatabaseStats getStats() {
        connectionLock.readLock().lock();
        try {
            if (dataSource == null || dataSource.isClosed()) {
                return new DatabaseStats(false, 0, 0, 0, 0);
            }
            
            return new DatabaseStats(
                true,
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
        } finally {
            connectionLock.readLock().unlock();
        }
    }
    
    private void initializeConnectionPool() {
        connectionLock.writeLock().lock();
        try {
            HikariConfig hikariConfig = createHikariConfig();
            dataSource = new HikariDataSource(hikariConfig);
            logger.info("Database connection pool initialized for " + config.getType());
        } finally {
            connectionLock.writeLock().unlock();
        }
    }
    
    @NotNull
    private HikariConfig createHikariConfig() {
        HikariConfig hikariConfig = new HikariConfig();
        
        switch (config.getType()) {
            case SQLITE:
                configureSQLite(hikariConfig);
                break;
            case MYSQL:
                configureMySQL(hikariConfig);
                break;
            case MARIADB:
                configureMariaDB(hikariConfig);
                break;
            default:
                throw new IllegalArgumentException("Unsupported database type: " + config.getType());
        }
        
        // Common configuration
        hikariConfig.setPoolName("YAEDatabasePool");
        hikariConfig.setConnectionTimeout(config.getMysql().getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getMysql().getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMysql().getMaxLifetime());
        hikariConfig.setMaximumPoolSize(config.getMysql().getPoolSize());
        
        // Connection testing
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setValidationTimeout(5000);
        
        // Performance optimizations
        hikariConfig.setAutoCommit(false);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return hikariConfig;
    }
    
    private void configureSQLite(@NotNull HikariConfig hikariConfig) {
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        String jdbcUrl = "jdbc:sqlite:" + config.getSqlite().getFilename();
        hikariConfig.setJdbcUrl(jdbcUrl);
        
        // SQLite specific optimizations
        hikariConfig.addDataSourceProperty("foreign_keys", "on");
        hikariConfig.addDataSourceProperty("synchronous", "normal");
        hikariConfig.addDataSourceProperty("journal_mode", "wal");
        hikariConfig.addDataSourceProperty("temp_store", "memory");
        hikariConfig.addDataSourceProperty("cache_size", "8192");
    }
    
    private void configureMySQL(@NotNull HikariConfig hikariConfig) {
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            config.getMysql().getHost(),
            config.getMysql().getPort(),
            config.getMysql().getDatabase());
        
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getMysql().getUsername());
        hikariConfig.setPassword(config.getMysql().getPassword());
        
        // MySQL specific optimizations
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8mb4");
        hikariConfig.addDataSourceProperty("useUnicode", "true");
    }
    
    private void configureMariaDB(@NotNull HikariConfig hikariConfig) {
        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            config.getMysql().getHost(),
            config.getMysql().getPort(),
            config.getMysql().getDatabase());
        
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getMysql().getUsername());
        hikariConfig.setPassword(config.getMysql().getPassword());
        
        // MariaDB specific optimizations
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8mb4");
        hikariConfig.addDataSourceProperty("useUnicode", "true");
    }
    
    private boolean testConnection() {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        
        try {
            connection = getConnection();
            if (connection == null) {
                return false;
            }
            
            statement = connection.prepareStatement("SELECT 1");
            resultSet = statement.executeQuery();
            return resultSet.next();
            
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Database connection test failed", e);
            return false;
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }
    
    private void startHealthCheckThread() {
        healthCheckThread = new Thread(() -> {
            while (!shutdown && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(HEALTH_CHECK_INTERVAL);
                    performHealthCheck();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Health check thread encountered error", e);
                }
            }
        }, "DatabaseHealthCheck");
        
        healthCheckThread.setDaemon(true);
        healthCheckThread.start();
        logger.info("Database health check thread started");
    }
    
    private void markUnhealthy() {
        if (healthy.compareAndSet(true, false)) {
            logger.warning("Database connection marked as unhealthy");
            
            // Attempt reconnection in background
            CompletableFuture.runAsync(() -> {
                int attempts = reconnectAttempts.incrementAndGet();
                if (attempts <= MAX_RECONNECT_ATTEMPTS) {
                    logger.info("Attempting database reconnection (attempt " + attempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    
                    try {
                        Thread.sleep(RECONNECT_DELAY * attempts);
                        if (performHealthCheck()) {
                            logger.info("Database reconnection successful");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    logger.severe("Max reconnection attempts reached, manual intervention required");
                }
            });
        }
    }
    
    private static void setParameters(@NotNull PreparedStatement statement, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
        }
    }
    
    private static void closeQuietly(@Nullable AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                // Ignore close exceptions
            }
        }
    }
    
    /**
     * Database connection statistics
     */
    public static final class DatabaseStats {
        private final boolean connected;
        private final int activeConnections;
        private final int idleConnections;
        private final int totalConnections;
        private final int waitingForConnection;
        
        public DatabaseStats(boolean connected, int activeConnections, int idleConnections, 
                           int totalConnections, int waitingForConnection) {
            this.connected = connected;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
            this.waitingForConnection = waitingForConnection;
        }
        
        public boolean isConnected() { return connected; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getTotalConnections() { return totalConnections; }
        public int getWaitingForConnection() { return waitingForConnection; }
        
        @Override
        public String toString() {
            return String.format("DatabaseStats{connected=%s, active=%d, idle=%d, total=%d, waiting=%d}",
                connected, activeConnections, idleConnections, totalConnections, waitingForConnection);
        }
    }
}
