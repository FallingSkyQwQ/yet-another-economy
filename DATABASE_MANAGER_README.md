# Database Connection Manager

This document describes the database connection manager implementation for YetAnotherEconomy.

## Overview

The Database Connection Manager provides a comprehensive solution for database connectivity with the following features:

- **Multi-database support**: SQLite, MySQL, and MariaDB
- **Connection pooling**: Using HikariCP for optimal performance
- **Health monitoring**: Automatic health checks and reconnection
- **Thread safety**: Complete thread-safe operations
- **Error handling**: Comprehensive exception handling and logging
- **Async operations**: Non-blocking database operations

## Architecture

### Core Components

1. **DatabaseManager** - Main database connection manager class
2. **DatabaseService** - Service wrapper for YAE core integration
3. **DatabaseException** - Custom exception for database errors
4. **DatabaseHealthChecker** - Interface for custom health checks

### Database Types Supported

- **SQLite**: File-based database for development and small deployments
- **MySQL**: Full-featured database for production environments  
- **MariaDB**: MySQL-compatible database with additional features

## Configuration

The database configuration is defined in `config.yml`:

```yaml
database:
  # Database type: sqlite, mysql, mariadb
  type: sqlite
  # SQLite settings
  sqlite:
    filename: "economy.db"
  # MySQL/MariaDB settings  
  mysql:
    host: "localhost"
    port: 3306
    database: "yet_another_economy"
    username: "root"
    password: ""
    # Connection pool settings
    pool-size: 10
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
```

## Features

### Connection Pooling

- Uses HikariCP for high-performance connection pooling
- Configurable pool size and connection timeouts
- Automatic connection validation and recovery
- Database-specific optimizations

### Health Monitoring

- Periodic health checks (every 30 seconds)
- Automatic reconnection on connection failure
- Configurable retry attempts and delays
- Connection pool statistics

### Thread Safety

- All operations are thread-safe using ReentrantReadWriteLock
- Concurrent database operations supported
- Safe connection acquisition and release
- Atomic state management

### Error Handling

- Comprehensive exception wrapping with DatabaseException
- Connection-specific error detection
- Timeout error identification
- Detailed logging with query context

### Async Operations

- Non-blocking query execution with CompletableFuture
- Automatic resource management
- Connection lifecycle management
- Error propagation in async context

## Usage Examples

### Basic Usage

```java
// Initialize database manager
DatabaseManager dbManager = new DatabaseManager(logger, config);
dbManager.initialize();

// Get connection
Connection conn = dbManager.getConnection();
if (conn != null) {
    // Use connection
    conn.close();
}
```

### Async Queries

```java
// Execute query asynchronously
CompletableFuture<ResultSet> future = dbManager.executeQuery(
    "SELECT * FROM accounts WHERE name = ?", 
    "player_name"
);

future.thenAccept(resultSet -> {
    // Process results
}).exceptionally(throwable -> {
    // Handle errors
    return null;
});
```

### Health Monitoring

```java
// Check database health
if (dbManager.isHealthy()) {
    // Database is ready
}

// Get connection statistics
DatabaseManager.DatabaseStats stats = dbManager.getStats();
System.out.println("Active connections: " + stats.getActiveConnections());
```

## Integration with YAE Core

The DatabaseService integrates seamlessly with the YAE core architecture:

```java
// In YetAnotherEconomy.java
DatabaseService databaseService = new DatabaseService(this);
registerService(databaseService);

// Access database service
DatabaseService dbService = (DatabaseService) getService(ServiceType.DATABASE);
if (dbService != null && dbService.isDatabaseHealthy()) {
    DatabaseManager dbManager = dbService.getDatabaseManager();
    // Use database manager
}
```

## Error Handling

### DatabaseException Features

- SQL state and error code preservation
- Query context in error messages
- Connection error detection
- Timeout error identification

### Error Types

```java
try {
    // Database operation
} catch (DatabaseException e) {
    if (e.isConnectionError()) {
        // Handle connection issues
    } else if (e.isTimeoutError()) {
        // Handle timeout issues
    } else {
        // Handle other database errors
    }
}
```

## Performance Considerations

### Connection Pool Settings

- **Pool Size**: Set based on expected concurrent connections
- **Connection Timeout**: Balance between responsiveness and reliability
- **Idle Timeout**: Prevent resource wastage on idle connections
- **Max Lifetime**: Prevent connection leaks

### Database-Specific Optimizations

- **SQLite**: WAL mode, memory temp store, optimized cache
- **MySQL**: Prepared statement caching, Unicode support
- **MariaDB**: Same optimizations as MySQL

### Thread Safety

- Use read locks for connection acquisition
- Use write locks for pool reconfiguration
- Minimize lock contention with atomic operations
- Separate health check thread

## Testing

Comprehensive test suite includes:

- Integration tests for all database types
- Concurrency tests for thread safety
- Error handling and recovery tests
- Performance benchmarks
- Health check verification

## Security Considerations

- Connection string sanitization
- SQL injection prevention with prepared statements
- Secure credential handling
- Connection timeout protection

## Monitoring and Logging

- Health check results logging
- Connection pool statistics
- Error details with context
- Performance metrics

## Future Enhancements

- Connection pool metrics export
- Custom health check implementations
- Database migration support
- Performance profiling tools
- Replication support for MySQL/MariaDB
