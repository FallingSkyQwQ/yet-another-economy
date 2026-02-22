package com.yae.api.core;

/**
 * Represents all available service types in the YAE economy plugin.
 * Each service type corresponds to a specific functionality area.
 */
public enum ServiceType {
    /**
     * Service Product Standard - Service product standardization
     */
    SPS("sps", "Service Product Standard"),
    
    /**
     * Shop management service
     */
    SHOP("shop", "Shop Management"),
    
    /**
     * Selling service - Handles item selling to system
     */
    SELL("sell", "System Selling"),
    
    /**
     * Market service - Handles player-to-player trading
     */
    MARKET("market", "Player Market"),
    
    /**
     * Banking service - Handles bank accounts and interest
     */
    BANK("bank", "Banking Service"),
    
    /**
     * Organization service - Handles guilds and organizations
     */
    ORG("org", "Organization Management"),
    
    /**
     * Risk management service - Handles risk assessment
     */
    RISK("risk", "Risk Management"),
    
    /**
     * Ledger service - Handles transaction logging and auditing
     */
    LEDGER("ledger", "Transaction Ledger"),
    
    /**
     * Configuration service - Handles plugin configuration
     */
    CONFIG("config", "Configuration Management"),
    
    /**
     * User service - Handles user data and permissions
     */
    USER("user", "User Management"),
    
    /**
     * Economy core service - Core economy functionality
     */
    ECONOMY("economy", "Core Economy"),
    
    /**
     * Command service - Command handling and registration
     */
    COMMAND("command", "Command Management"),
    
    /**
     * Event service - Event handling and processing
     */
    EVENT("event", "Event Management"),
    
    /**
     * Database service - Data persistence
     */
    DATABASE("database", "Database Management"),
    
    /**
     * Cache service - Caching functionality
     */
    CACHE("cache", "Cache Management"),
    
    /**
     * Messaging service - Message handling and localization
     */
    MESSAGING("messaging", "Message Management"),
    
    /**
     * Credit scoring service - Handles credit score calculation and management
     */
    CREDIT("credit", "Credit Scoring Service"),
    
    /**
     * Loan service - Handles loan applications and management
     */
    LOAN("loan", "Loan Service");
    
    private final String key;
    private final String description;
    
    ServiceType(String key, String description) {
        this.key = key;
        this.description = description;
    }
    
    /**
     * Gets the configuration key for this service type
     * @return the configuration key
     */
    public String getKey() {
        return key;
    }
    
    /**
     * Gets the human-readable description of this service type
     * @return the description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets a service type by its key
     * @param key the configuration key
     * @return the service type, or null if not found
     */
    public static ServiceType fromKey(String key) {
        for (ServiceType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Checks if this service type is associated with economy modules
     * @return true if economy-related
     */
    public boolean isEconomyRelated() {
        return this == ECONOMY || this == BANK || this == LEDGER || 
               this == SELL || this == MARKET || this == SHOP;
    }
    
    /**
     * Checks if this service type is a core infrastructure service
     * @return true if core infrastructure
     */
    public boolean isCoreInfrastructure() {
        return this == CONFIG || this == DATABASE || this == CACHE || 
               this == EVENT || this == COMMAND || this == MESSAGING || 
               this == USER;
    }
}
