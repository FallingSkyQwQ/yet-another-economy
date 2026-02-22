package com.yae.api.database;

import org.jetbrains.annotations.NotNull;

/**
 * Interface for implementing custom database health checks.
 */
@FunctionalInterface
public interface DatabaseHealthChecker {
    
    /**
     * Performs a health check on the database.
     * @param databaseManager the database manager to use for the check
     * @return HealthCheckResult indicating the health status
     */
    @NotNull
    HealthCheckResult checkHealth(@NotNull DatabaseManager databaseManager);
    
    /**
     * Default health checker that performs a simple connection test.
     */
    DatabaseHealthChecker DEFAULT = databaseManager -> {
        boolean isHealthy = databaseManager.performHealthCheck();
        return new HealthCheckResult(isHealthy, isHealthy ? "Database connection healthy" : "Database connection failed");
    };
    
    /**
     * Result of a health check operation.
     */
    final class HealthCheckResult {
        private final boolean healthy;
        private final String message;
        private final Throwable error;
        
        public HealthCheckResult(boolean healthy, @NotNull String message) {
            this(healthy, message, null);
        }
        
        public HealthCheckResult(boolean healthy, @NotNull String message, @NotNull Throwable error) {
            this.healthy = healthy;
            this.message = message;
            this.error = error;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        @NotNull
        public String getMessage() {
            return message;
        }
        
        @NotNull
        public Throwable getError() {
            return error;
        }
        
        @Override
        public String toString() {
            return "HealthCheckResult{healthy=" + healthy + ", message='" + message + "'" + 
                   (error != null ? ", error=" + error.getMessage() : "") + "}";
        }
    }
}
