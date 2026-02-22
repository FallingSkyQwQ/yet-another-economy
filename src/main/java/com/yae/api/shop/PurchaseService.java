package com.yae.api.shop;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceType;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.YAECore;
import com.yae.api.core.event.YAEEvent;
import com.yae.api.core.event.EconomyEvent;
import com.yae.api.services.EconomyService;
import com.yae.api.database.DatabaseManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Purchase service that handles shop transactions and purchase confirmation system.
 */
public class PurchaseService implements Service {
    
    private final YAECore plugin;
    private final Logger logger;
    private final ServiceType serviceType;
    private final AtomicBoolean enabled;
    private final AtomicBoolean healthy;
    private final int priority;
    
    private ServiceConfig config;
    private ShopManager shopManager;
    private EconomyService economyService;
    private DatabaseManager databaseManager;
    
    // Purchase confirmation tracking
    private final Map<UUID, PendingPurchase> pendingPurchases;
    private final long confirmationTimeout = 10000L; // 10 seconds
    
    // Purchase amount presets - corresponds to the user's requested buttons
    private static final int[] AMOUNT_PRESETS = {-64, -10, -1, 1, 10, 64};
    
    public PurchaseService(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getLogger();
        this.serviceType = ServiceType.SHOP;
        this.enabled = new AtomicBoolean(false);
        this.healthy = new AtomicBoolean(true);
        this.priority = 85;
        this.pendingPurchases = new ConcurrentHashMap<>();
    }
    
    @Override
    @NotNull
    public String getName() {
        return "PurchaseService";
    }
    
    @Override
    @NotNull
    public ServiceType getType() {
        return serviceType;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled.get();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        logger.log(Level.INFO, "PurchaseService " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public boolean isHealthy() {
        return healthy.get() && isEnabled();
    }
    
    @Override
    public boolean initialize() {
        logger.log(Level.INFO, "Initializing PurchaseService...");
        
        try {
            // Get required services
            this.shopManager = plugin.getService(ServiceType.SHOP);
            this.economyService = plugin.getService(ServiceType.ECONOMY);
            this.databaseManager = plugin.getService(ServiceType.DATABASE);
            
            if (shopManager == null || !shopManager.isEnabled()) {
                logger.log(Level.SEVERE, "ShopManager service is not available");
                healthy.set(false);
                return false;
            }
            
            if (economyService == null || !economyService.isEnabled()) {
                logger.log(Level.SEVERE, "EconomyService is not available");
                healthy.set(false);
                return false;
            }
            
            if (databaseManager == null || !databaseManager.isEnabled()) {
                logger.log(Level.SEVERE, "DatabaseManager service is not available");
                healthy.set(false);
                return false;
            }
            
            setEnabled(true);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize PurchaseService", e);
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        logger.log(Level.INFO, "Shutting down PurchaseService...");
        setEnabled(false);
        pendingPurchases.clear();
    }
    
    @Override
    public boolean reload() {
        logger.log(Level.INFO, "Reloading PurchaseService...");
        
        try {
            // Update service references
            this.shopManager = plugin.getService(ServiceType.SHOP);
            this.economyService = plugin.getService(ServiceType.ECONOMY);
            this.databaseManager = plugin.getService(ServiceType.DATABASE);
            
            logger.log(Level.INFO, "PurchaseService reloaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to reload PurchaseService", e);
            return false;
        }
    }
    
    /**
     * Gets the price information for an item.
     * @param itemId The item ID
     * @return Price information or null if item not found
     */
    @Nullable
    public PriceInfo getItemPriceInfo(String itemId) {
        ShopItem item = shopManager.getItem(itemId);
        if (item == null) {
            return null;
        }
        
        // Get tax rate from configuration (default 5%)
        double purchaseTax = 0.05; // This should come from config
        
        return new PriceInfo(
            item.getPrice(),           // Shop price (buy price)
            item.getSellPrice(),       // Standard price (sell price)
            item.getMarketPriceRange(), // Market price range
            item.getTaxAmount(purchaseTax),
            item.getFinalPrice(purchaseTax)
        );
    }
    
    /**
     * Calculates the total cost for purchasing a quantity of items.
     * @param itemId The item ID
     * @param quantity The quantity (negative values represent selling)
     * @return Purchase calculation result or null if invalid
     */
    @Nullable
    public PurchaseCalculation calculatePurchase(@NotNull String itemId, int quantity, @NotNull UUID buyerId) {
        ShopItem item = shopManager.getItem(itemId);
        if (item == null) {
            return new PurchaseCalculation(PurchaseResult.ITEM_NOT_FOUND, "Item not found", null, 0, 0, 0, 0);
        }
        
        if (quantity == 0) {
            return new PurchaseCalculation(PurchaseResult.INVALID_QUANTITY, "Quantity cannot be zero", null, 0, 0, 0, 0);
        }
        
        boolean isBuying = quantity > 0;
        
        // Validate limits and stock
        if (isBuying) {
            if (!shopManager.hasEnoughStock(itemId, quantity)) {
                return new PurchaseCalculation(PurchaseResult.INSUFFICIENT_STOCK, 
                    "Not enough stock available", null, 0, 0, 0, 0);
            }
            
            if (shopManager.isDailyLimitReached(itemId, buyerId)) {
                ShopItem actualItem = shopManager.getItem(itemId);
                return new PurchaseCalculation(PurchaseResult.DAILY_LIMIT_REACHED, 
                    String.format("Daily limit reached (max %d per day)", actualItem.getDailyLimit()), 
                    null, 0, 0, 0, 0);
            }
            
            if (shopManager.isPlayerLimitReached(itemId, buyerId)) {
                ShopItem actualItem = shopManager.getItem(itemId);
                return new PurchaseCalculation(PurchaseResult.PLAYER_LIMIT_REACHED, 
                    String.format("Player limit reached (max %d forever)", actualItem.getPlayerLimit()), 
                    null, 0, 0, 0, 0);
            }
        }
        
        // Calculate prices
        double unitPrice = isBuying ? item.getPrice() : item.getSellPrice();
        double totalPrice = unitPrice * Math.abs(quantity);
        double taxRate = isBuying ? 0.05 : 0.0; // 5% tax on purchases
        double taxAmount = totalPrice * taxRate;
        double finalPrice = totalPrice + taxAmount;
        
        // Check buyer's balance for purchases
        if (isBuying && !economyService.hasMoney(buyerId, finalPrice)) {
            return new PurchaseCalculation(PurchaseResult.INSUFFICIENT_FUNDS, 
                "Insufficient funds for this purchase", null, 0, 0, 0, 0);
        }
        
        return new PurchaseCalculation(PurchaseResult.SUCCESS, "Purchase calculation successful", 
            item, quantity, totalPrice, taxAmount, finalPrice);
    }
    
    /**
     * Creates a pending purchase that requires confirmation.
     * @param itemId The item ID
     * @param quantity The quantity to purchase
     * @param playerId The player ID
     * @return Pending purchase or null if creation failed
     */
    @Nullable
    public PendingPurchase createPendingPurchase(@NotNull String itemId, int quantity, @NotNull UUID playerId) {
        PurchaseCalculation calculation = calculatePurchase(itemId, quantity, playerId);
        if (calculation.getResult() != PurchaseResult.SUCCESS) {
            return null;
        }
        
        PendingPurchase pendingPurchase = new PendingPurchase(
            System.currentTimeMillis(),
            playerId,
            itemId,
            quantity,
            calculation.getUnitPrice(),
            calculation.getTotalPrice(),
            calculation.getTaxAmount(),
            calculation.getFinalPrice(),
            LocalDateTime.now().plusSeconds(confirmationTimeout / 1000)
        );
        
        pendingPurchases.put(playerId, pendingPurchase);
        return pendingPurchase;
    }
    
    /**
     * Executes a pending purchase.
     * @param playerId The player ID
     * @param transactionId Optional transaction ID for tracking
     * @return Purchase result
     */
    @NotNull
    public PurchaseResult executePurchase(@NotNull UUID playerId, @Nullable String transactionId) {
        PendingPurchase pending = pendingPurchases.get(playerId);
        if (pending == null) {
            return PurchaseResult.NO_PENDING_PURCHASE;
        }
        
        if (pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            pendingPurchases.remove(playerId);
            return PurchaseResult.CONFIRMATION_TIMEOUT;
        }
        
        ShopItem item = shopManager.getItem(pending.getItemId());
        if (item == null) {
            return PurchaseResult.ITEM_NOT_FOUND;
        }
        
        try {
            // Start database transaction
            Connection connection = databaseManager.getConnection();
            connection.setAutoCommit(false);
            
            try {
                // Validate limits and stock again (in case of concurrent access)
                if (pending.isPurchase()) {
                    if (!shopManager.hasEnoughStock(pending.getItemId(), pending.getQuantity())) {
                        connection.rollback();
                        return PurchaseResult.INSUFFICIENT_STOCK;
                    }
                    
                    if (shopManager.isDailyLimitReached(pending.getItemId(), playerId)) {
                        connection.rollback();
                        return PurchaseResult.DAILY_LIMIT_REACHED;
                    }
                    
                    if (shopManager.isPlayerLimitReached(pending.getItemId(), playerId)) {
                        connection.rollback();
                        return PurchaseResult.PLAYER_LIMIT_REACHED;
                    }
                    
                    // Check buyer's balance
                    if (!economyService.hasMoney(playerId, pending.getFinalPrice())) {
                        connection.rollback();
                        return PurchaseResult.INSUFFICIENT_FUNDS;
                    }
                }
                
                // Create transaction record
                String transactionSql = """
                    INSERT INTO yae_shop_transactions 
                    (id, player_uuid, item_id, quantity, unit_price, total_price, tax_amount, final_price, transaction_type, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                
                try (PreparedStatement stmt = connection.prepareStatement(transactionSql)) {
                    stmt.setString(1, transactionId != null ? transactionId : UUID.randomUUID().toString());
                    stmt.setString(2, playerId.toString());
                    stmt.setString(3, pending.getItemId());
                    stmt.setInt(4, Math.abs(pending.getQuantity()));
                    stmt.setDouble(5, pending.getUnitPrice());
                    stmt.setDouble(6, pending.getTotalPrice());
                    stmt.setDouble(7, pending.getTaxAmount());
                    stmt.setDouble(8, pending.getFinalPrice());
                    stmt.setString(9, pending.isPurchase() ? "BUY" : "SELL");
                    stmt.setTimestamp(10, pending.isPurchase() ? Timestamp.valueOf(LocalDateTime.now().plusSeconds(10)) : null);
                    stmt.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
                    
                    stmt.executeUpdate();
                }
                
                // Update limits and inventory
                if (pending.isPurchase()) {
                    // Update daily limit
                    updateDailyLimit(pending.getItemId(), pending.getQuantity(), connection);
                    
                    // Update player limit
                    updatePlayerLimit(playerId, pending.getItemId(), pending.getQuantity(), connection);
                    
                    // Update inventory
                    updateInventory(pending.getItemId(), -pending.getQuantity(), connection);
                    
                    // Process payment (for purchases)
                    economyService.removeMoney(playerId, pending.getFinalPrice());
                    
                    // Give items to player (this would need to be implemented with Bukkit API)
                    giveItemsToPlayer(playerId, pending.getItemId(), pending.getQuantity());
                }
                
                // Update transaction status to completed
                updateTransactionStatus(transactionId != null ? transactionId : UUID.randomUUID().toString(), 
                                      "COMPLETED", connection);
                
                connection.commit();
                pendingPurchases.remove(playerId);
                
                // Emit purchase event
                emitPurchaseEvent(playerId, pending);
                
                return PurchaseResult.SUCCESS;
                
            } catch (Exception e) {
                connection.rollback();
                logger.log(Level.SEVERE, "Failed to execute purchase", e);
                return PurchaseResult.TRANSACTION_ERROR;
            } finally {
                connection.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during purchase execution", e);
            return PurchaseResult.DATABASE_ERROR;
        }
    }
    
    /**
     * Cancels a pending purchase.
     * @param playerId The player ID
     * @param transactionId The transaction ID
     * @return True if cancellation was successful
     */
    public boolean cancelPurchase(@NotNull UUID playerId, @NotNull String transactionId) {
        PendingPurchase pending = pendingPurchases.remove(playerId);
        if (pending == null) {
            return false;
        }
        
        try (Connection connection = databaseManager.getConnection()) {
            String sql = "UPDATE yae_shop_transactions SET status = 'CANCELLED' WHERE id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, transactionId);
                return stmt.executeUpdate() > 0;
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to cancel purchase", e);
            return false;
        }
    }
    
    /**
     * Gets a pending purchase for a player.
     * @param playerId The player ID
     * @return Pending purchase or null if not found
     */
    @Nullable
    public PendingPurchase getPendingPurchase(@NotNull UUID playerId) {
        PendingPurchase pending = pendingPurchases.get(playerId);
        if (pending != null && pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            pendingPurchases.remove(playerId);
            return null;
        }
        return pending;
    }
    
    // Helper methods
    
    private void updateDailyLimit(String itemId, int quantity, Connection connection) throws SQLException {
        String sql = """
            INSERT INTO yae_daily_limits (item_id, date, pur_quantity_sold) VALUES (?, DATE('now'), ?)
            ON CONFLICT(item_id, date) DO UPDATE SET pur_quantity_sold = pur_quantity_sold + ?
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            stmt.setInt(2, quantity);
            stmt.setInt(3, quantity);
            stmt.executeUpdate();
        }
    }
    
    private void updatePlayerLimit(UUID playerId, String itemId, int quantity, Connection connection) throws SQLException {
        String sql = """
            INSERT INTO yae_player_limits (player_uuid, item_id, total_purchased, last_purchase_date) VALUES (?, ?, ?, DATE('now'))
            ON CONFLICT(player_uuid, item_id) DO UPDATE SET total_purchased = total_purchased + ?, last_purchase_date = DATE('now')
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, itemId);
            stmt.setInt(3, quantity);
            stmt.setInt(4, quantity);
            stmt.executeUpdate();
        }
    }
    
    private void updateInventory(String itemId, int stockChange, Connection connection) throws SQLException {
        String sql = """
            UPDATE yae_shop_items SET stock = stock + ? WHERE id = ?
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, stockChange);
            stmt.setString(2, itemId);
            stmt.executeUpdate();
        }
    }
    
    private void updateTransactionStatus(String transactionId, String status, Connection connection) throws SQLException {
        String sql = """
            UPDATE yae_shop_transactions 
            SET status = ?, completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END 
            WHERE id = ?
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, status);
            stmt.setString(3, transactionId);
            stmt.executeUpdate();
        }
    }
    
    private void giveItemsToPlayer(UUID playerId, String itemId, int quantity) {
        // This is a placeholder - in a real implementation, you would use Bukkit API
        // to give items to the player
        logger.log(Level.INFO, "Giving {0} x {1} to player {2}", 
                  new Object[]{quantity, itemId, playerId});
    }
    
    private void emitPurchaseEvent(UUID playerId, PendingPurchase purchase) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("itemId", purchase.getItemId());
        data.put("quantity", purchase.getQuantity());
        data.put("totalPrice", purchase.getTotalPrice());
        data.put("finalPrice", purchase.getFinalPrice());
        data.put("taxAmount", purchase.getTaxAmount());
        
        EconomyEvent event = new EconomyEvent("shop-purchase", this, 
                                            String.format("Player %s purchased %d x %s for %.2f (tax: %.2f)", 
                                            playerId, purchase.getQuantity(), purchase.getItemId(), 
                                            purchase.getFinalPrice(), purchase.getTaxAmount()), data);
        plugin.emitEvent(event);
    }
    
    @Override
    @NotNull
    public String getStatus() {
        return String.format("PurchaseService[%s, %s, %d pending purchases]", 
                           isEnabled() ? "ENABLED" : "DISABLED",
                           isHealthy() ? "HEALTHY" : "UNHEALTHY",
                           pendingPurchases.size());
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return serviceType == ServiceType.SHOP || serviceType == ServiceType.ECONOMY || serviceType == ServiceType.DATABASE;
    }
    
    @Override
    @Nullable
    public ServiceConfig getConfig() {
        return config;
    }
    
    @Override
    public void setConfig(@Nullable ServiceConfig config) {
        this.config = config;
    }
    
    // Inner classes
    
    /**
     * Represents a pending purchase awaiting confirmation.
     */
    public static class PendingPurchase {
        private final long timestamp;
        private final UUID playerId;
        private final String itemId;
        private final int quantity;
        private final double unitPrice;
        private final double totalPrice;
        private final double taxAmount;
        private final double finalPrice;
        private final LocalDateTime expiresAt;
        
        public PendingPurchase(long timestamp, UUID playerId, String itemId, int quantity,
                              double unitPrice, double totalPrice, double taxAmount, double finalPrice,
                              LocalDateTime expiresAt) {
            this.timestamp = timestamp;
            this.playerId = playerId;
            this.itemId = itemId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
            this.taxAmount = taxAmount;
            this.finalPrice = finalPrice;
            this.expiresAt = expiresAt;
        }
        
        public boolean isPurchase() {
            return quantity > 0;
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public UUID getPlayerId() { return playerId; }
        public String getItemId() { return itemId; }
        public int getQuantity() { return Math.abs(quantity); }
        public double getUnitPrice() { return unitPrice; }
        public double getTotalPrice() { return totalPrice; }
        public double getTaxAmount() { return taxAmount; }
        public double getFinalPrice() { return finalPrice; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
}
