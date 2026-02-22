package com.yae.api.database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when database operations fail.
 */
public class DatabaseException extends Exception {
    
    private final String sqlState;
    private final int errorCode;
    private final String query;
    
    public DatabaseException(@NotNull String message) {
        super(message);
        this.sqlState = null;
        this.errorCode = -1;
        this.query = null;
    }
    
    public DatabaseException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.sqlState = null;
        this.errorCode = -1;
        this.query = null;
    }
    
    public DatabaseException(@NotNull String message, @Nullable String query, @Nullable Throwable cause) {
        super(message, cause);
        this.sqlState = cause instanceof java.sql.SQLException ? ((java.sql.SQLException) cause).getSQLState() : null;
        this.errorCode = cause instanceof java.sql.SQLException ? ((java.sql.SQLException) cause).getErrorCode() : -1;
        this.query = query;
    }
    
    public DatabaseException(@NotNull String message, @Nullable String query, @NotNull String sqlState, int errorCode) {
        super(message);
        this.sqlState = sqlState;
        this.errorCode = errorCode;
        this.query = query;
    }
    
    @Nullable
    public String getSqlState() {
        return sqlState;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    @Nullable
    public String getQuery() {
        return query;
    }
    
    @Override
    @NotNull
    public String getMessage() {
        StringBuilder message = new StringBuilder(super.getMessage());
        
        if (errorCode != -1) {
            message.append(" [Error Code: ").append(errorCode).append("]");
        }
        
        if (sqlState != null) {
            message.append(" [SQL State: ").append(sqlState).append("]");
        }
        
        if (query != null) {
            message.append(" [Query: ").append(truncateQuery(query)).append("]");
        }
        
        return message.toString();
    }
    
    @NotNull
    private String truncateQuery(@NotNull String query) {
        if (query.length() <= 100) {
            return query;
        }
        return query.substring(0, 97) + "...";
    }
    
    /**
     * Checks if this exception is due to a connection issue.
     * @return true if this is a connection-related error
     */
    public boolean isConnectionError() {
        if (errorCode == -1 || sqlState == null) {
            return false;
        }
        
        // Common SQL states for connection issues
        return sqlState.startsWith("08") || // Connection exception
               sqlState.equals("08001") ||   // SQL client unable to establish SQL connection
               sqlState.equals("08003") ||   // Connection does not exist
               sqlState.equals("08004") ||   // SQL server rejected establishment of SQL connection
               sqlState.equals("08006") ||   // Connection failure
               sqlState.equals("08007") ||   // Transaction resolution unknown
               sqlState.startsWith("HY000"); // General error (often connection related)
    }
    
    /**
     * Checks if this exception is due to a timeout.
     * @return true if this is a timeout error
     */
    public boolean isTimeoutError() {
        if (errorCode == -1 || sqlState == null) {
            return false;
        }
        
        return sqlState.equals("HYT00") || // Timeout expired
               sqlState.equals("HYT01");   // Connection timeout expired
    }
}
