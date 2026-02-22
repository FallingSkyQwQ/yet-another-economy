package com.yae.api.shop;

/**
 * Represents the result of a purchase operation.
 */
public enum PurchaseResult {
    // Success
    SUCCESS("Purchase successful"),
    
    // Validation failures
    ITEM_NOT_FOUND("Item not found in shop"),
    INVALID_QUANTITY("Invalid quantity specified"),
    INSUFFICIENT_STOCK("Not enough stock available"),
    INSUFFICIENT_FUNDS("Insufficient funds for this purchase"),
    DAILY_LIMIT_REACHED("Daily purchase limit reached"),
    PLAYER_LIMIT_REACHED("Player purchase limit reached"),
    
    // Transaction failures
    TRANSACTION_ERROR("Transaction failed"),
    DATABASE_ERROR("Database error occurred"),
    
    // Confirmation failures
    NO_PENDING_PURCHASE("No pending purchase found"),
    CONFIRMATION_TIMEOUT("Purchase confirmation timed out"),
    CONFIRMATION_CANCELLED("Purchase was cancelled");
    
    private final String defaultMessage;
    
    PurchaseResult(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }
    
    public String getDefaultMessage() {
        return defaultMessage;
    }
    
    
    /**
     * Checks if this represents a successful operation.
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
    
    /**
     * Checks if this represents a failure due to insufficient stock.
     */
    public boolean isInsufficientStock() {
        return this == INSUFFICIENT_STOCK;
    }
    
    /**
     * Checks if this represents a failure due to insufficient funds.
     */
    public boolean isInsufficientFunds() {
        return this == INSUFFICIENT_FUNDS;
    }
    
    /**
     * Checks if this represents a failure due to limit restrictions.
     */
    public boolean isLimitReached() {
        return this == DAILY_LIMIT_REACHED || this == PLAYER_LIMIT_REACHED;
    }
    
    /**
     * Checks if this represents a timeout failure.
     */
    public boolean isTimeout() {
        return this == CONFIRMATION_TIMEOUT;
    }
    
    /**
     * Checks if this represents a cancellation.
     */
    public boolean isCancelled() {
        return this == CONFIRMATION_CANCELLED;
    }
}
