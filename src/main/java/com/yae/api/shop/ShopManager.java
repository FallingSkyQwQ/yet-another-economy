package com.yae.api.shop;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceType;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.YAECore;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.event.YAEEvent;
import com.yae.api.database.DatabaseManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shop management service that handles item configuration, inventory, and purchase limits.
 */
public class ShopManager implements Service {
    
    private final YAECore plugin;
    private final Logger logger;
    private final ServiceType serviceType;
    private final AtomicBoolean enabled;
    private final AtomicBoolean healthy;
    private final int priority;
    
    private ServiceConfig config;
    private Map<String, ShopItem> items;
    private Map<String, ShopCategory> categories;
    private DatabaseManager databaseManager;
    
    public ShopManager(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getLogger();
        this.serviceType = ServiceType.SHOP;
        this.enabled = new AtomicBoolean(false);
        this.healthy = new AtomicBoolean(true);
        this.priority = 90;
        this.items = new ConcurrentHashMap<>();
        this.categories = new ConcurrentHashMap<>();
    }
    
    @Override
    @NotNull
    public String getName() {
        return "ShopManager";
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
        logger.log(Level.INFO, "ShopManager " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public boolean isHealthy() {
        return healthy.get() && isEnabled();
    }
    
    @Override
    public boolean initialize() {
        logger.log(Level.INFO, "Initializing ShopManager...");
        
        try {
            // Get database manager from plugin
            this.databaseManager = plugin.getService(ServiceType.DATABASE);
            if (databaseManager == null || !databaseManager.isEnabled()) {
                logger.log(Level.SEVERE, "Database service is not available");
                healthy.set(false);
                return false;
            }
            
            // Load shop configuration
            if (!loadShopConfiguration()) {
                logger.log(Level.SEVERE, "Failed to load shop configuration");
                healthy.set(false);
                return false;
            }
            
            // Initialize database tables
            if (!initializeDatabase()) {
                logger.log(Level.SEVERE, "Failed to initialize shop database tables");
                healthy.set(false);
                return false;
            }
            
            // Load items from database
            loadItemsFromDatabase();
            
            setEnabled(true);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize ShopManager", e);
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        logger.log(Level.INFO, "Shutting down ShopManager...");
        setEnabled(false);
        items.clear();
        categories.clear();
    }
    
    @Override
    public boolean reload() {
        logger.log(Level.INFO, "Reloading ShopManager...");
        
        try {
            // Clear existing data
            items.clear();
            categories.clear();
            
            // Reload configuration
            if (!loadShopConfiguration()) {
                logger.log(Level.SEVERE, "Failed to reload shop configuration");
                return false;
            }
            
            // Reload items from database
            loadItemsFromDatabase();
            
            logger.log(Level.INFO, "ShopManager reloaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to reload ShopManager", e);
            return false;
        }
    }
    
    private boolean loadShopConfiguration() {
        try {
            File configFile = new File(plugin.getDataFolder(), "shop.yml");
            Yaml yaml = new Yaml();
            
            InputStream inputStream;
            if (configFile.exists()) {
                inputStream = new FileInputStream(configFile);
            } else {
                // Load default from resources
                inputStream = getClass().getResourceAsStream("/shop.yml");
                if (inputStream == null) {
                    logger.log(Level.SEVERE, "Default shop.yml not found in resources");
                    return false;
                }
            }
            
            Map<String, Object> config = yaml.load(inputStream);
            
            if (config != null && config.containsKey("shop")) {
                Map<String, Object> shopConfig = (Map<String, Object>) config.get("shop");
                
                // Load categories
                if (shopConfig.containsKey("categories")) {
                    Map<String, Object> categoriesConfig = (Map<String, Object>) shopConfig.get("categories");
                    for (Map.Entry<String, Object> entry : categoriesConfig.entrySet()) {
                        String categoryId = entry.getKey();
                        Map<String, Object> categoryData = (Map<String, Object>) entry.getValue();
                        
                        ShopCategory category = new ShopCategory(
                            categoryId,
                            (String) categoryData.get("display-name"),
                            (String) categoryData.get("icon"),
                            (List<String>) categoryData.get("default-description"),
                            (String) categoryData.get("color"),
                            new ArrayList<>()
                        );
                        categories.put(categoryId, category);
                    }
                }
                
                // Load items
                if (shopConfig.containsKey("items")) {
                    Map<String, Object> itemsConfig = (Map<String, Object>) shopConfig.get("items");
                    for (Map.Entry<String, Object> entry : itemsConfig.entrySet()) {
                        String categoryId = entry.getKey();
                        List<Map<String, Object>> categoryItems = (List<Map<String, Object>>) entry.getValue();
                        
                        ShopCategory category = categories.get(categoryId);
                        if (category != null) {
                            for (Map<String, Object> itemData : categoryItems) {
                                ShopItem item = new ShopItem(
                                    (String) itemData.get("id"),
                                    (String) itemData.get("display-name"),
                                    categoryId,
                                    ((Number) itemData.get("price")).doubleValue(),
                                    ((Number) itemData.get("sell-price")).doubleValue(),
                                    ((Number) itemData.get("stock")).intValue(),
                                    ((Number) itemData.get("daily-limit")).intValue(),
                                    ((Number) itemData.get("player-limit")).intValue(),
                                    (List<String>) itemData.get("description"),
                                    (Boolean) itemData.getOrDefault("enabled", true)
                                );
                                
                                items.put(item.getId(), item);
                                category.getItems().add(item);
                            }
                        }
                    }
                }
            }
            
            inputStream.close();
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load shop configuration", e);
            return false;
        }
    }
    
    private boolean initializeDatabase() {
        try (Connection connection = databaseManager.getConnection()) {
            // Create shop items table
            String createShopItemsTable = """
                CREATE TABLE IF NOT EXISTS yae_shop_items (
                    id VARCHAR(64) PRIMARY KEY,
                    material VARCHAR(64) NOT NULL,
                    display_name VARCHAR(128) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    price DECIMAL(10, 2) NOT NULL,
                    sell_price DECIMAL(10, 2),
                    stock INT DEFAULT 0,
                    daily_limit INT DEFAULT 0,
                    player_limit INT DEFAULT 0,
                    description TEXT,
                    enabled BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            
            // Create shop inventory table
            String createShopInventoryTable = """
                CREATE TABLE IF NOT EXISTS yae_shop_inventory (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_id VARCHAR(64) NOT NULL,
                    current_stock INT DEFAULT 0,
                    date DATE NOT NULL,
                    FOREIGN KEY (item_id) REFERENCES yae_shop_items(id) ON DELETE CASCADE
                )
                """;
            
            // Create daily limits table
            String createDailyLimitsTable = """
                CREATE TABLE IF NOT EXISTS yae_daily_limits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_id VARCHAR(64) NOT NULL,
                    date DATE NOT NULL,
                    pur_quantity_sold INT DEFAULT 0,
                    FOREIGN KEY (item_id) REFERENCES yae_shop_items(id) ON DELETE CASCADE
                )
                """;
            
            // Create player limits table
            String createPlayerLimitsTable = """
                CREATE TABLE IF NOT EXISTS yae_player_limits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_id VARCHAR(64) NOT NULL,
                    total_purchased INT DEFAULT 0,
                    last_purchase_date DATE,
                    FOREIGN KEY (item_id) REFERENCES yae_shop_items(id) ON DELETE CASCADE
                )
                """;
            
            // Create shop transactions table
            String createShopTransactionsTable = """
                CREATE TABLE IF NOT EXISTS yae_shop_transactions (
                    id VARCHAR(32) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_id VARCHAR(64) NOT NULL,
                    quantity INT NOT NULL,
                    unit_price DECIMAL(10, 2) NOT NULL,
                    total_price DECIMAL(10, 2) NOT NULL,
                    tax_amount DECIMAL(10, 2) DEFAULT 0,
                    final_price DECIMAL(10, 2) NOT NULL,
                    transaction_type VARCHAR(16) NOT NULL, -- 'BUY', 'SELL', 'CANCEL'
                    status VARCHAR(16) DEFAULT 'COMPLETED', -- 'PENDING', 'COMPLETED', 'CANCELLED', 'REVERSED'
                    expires_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_at TIMESTAMP,
                    FOREIGN KEY (item_id) REFERENCES yae_shop_items(id) ON DELETE CASCADE
                )
                """;
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createShopItemsTable);
                stmt.execute(createShopInventoryTable);
                stmt.execute(createDailyLimitsTable);
                stmt.execute(createPlayerLimitsTable);
                stmt.execute(createShopTransactionsTable);
            }
            
            return true;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize shop database tables", e);
            return false;
        }
    }
    
    private void loadItemsFromDatabase() {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = "SELECT * FROM yae_shop_items WHERE enabled = TRUE";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    ShopItem item = new ShopItem(
                        rs.getString("material"),
                        rs.getString("display_name"),
                        rs.getString("category"),
                        rs.getDouble("price"),
                        rs.getDouble("sell_price"),
                        rs.getInt("stock"),
                        rs.getInt("daily_limit"),
                        rs.getInt("player_limit"),
                        new ArrayList<>(), // Description will be loaded separately
                        rs.getBoolean("enabled")
                    );
                    
                    items.put(item.getId(), item);
                }
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load items from database", e);
        }
    }
    
    // Public API methods
    
    @Nullable
    public ShopItem getItem(String itemId) {
        return items.get(itemId);
    }
    
    @NotNull
    public List<ShopItem> getItemsByCategory(String categoryId) {
        ShopCategory category = categories.get(categoryId);
        return category != null ? new ArrayList<>(category.getItems()) : new ArrayList<>();
    }
    
    @NotNull
    public List<ShopCategory> getCategories() {
        return new ArrayList<>(categories.values());
    }
    
    @Nullable
    public ShopCategory getCategory(String categoryId) {
        return categories.get(categoryId);
    }
    
    @NotNull
    public List<ShopItem> searchItems(String keyword) {
        List<ShopItem> results = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        
        for (ShopItem item : items.values()) {
            if (item.getDisplayName().toLowerCase().contains(lowerKeyword) ||
                item.getId().toLowerCase().contains(lowerKeyword)) {
                results.add(item);
            }
        }
        
        return results;
    }
    
    public boolean isDailyLimitReached(String itemId, UUID playerId) {
        ShopItem item = items.get(itemId);
        if (item == null || item.getDailyLimit() <= 0) {
            return false;
        }
        
        try (Connection connection = databaseManager.getConnection()) {
            LocalDate today = LocalDate.now();
            
            String sql = "SELECT pur_quantity_sold FROM yae_daily_limits WHERE item_id = ? AND date = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, itemId);
                stmt.setDate(2, java.sql.Date.valueOf(today));
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int soldToday = rs.getInt("pur_quantity_sold");
                    return soldToday >= item.getDailyLimit();
                }
                
                return false;
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check daily limit", e);
            return true; // Fail safe
        }
    }
    
    public boolean isPlayerLimitReached(String itemId, UUID playerId) {
        ShopItem item = items.get(itemId);
        if (item == null || item.getPlayerLimit() <= 0) {
            return false;
        }
        
        try (Connection connection = databaseManager.getConnection()) {
            String sql = "SELECT total_purchased FROM yae_player_limits WHERE player_uuid = ? AND item_id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, itemId);
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int totalPurchased = rs.getInt("total_purchased");
                    return totalPurchased >= item.getPlayerLimit();
                }
                
                return false;
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check player limit", e);
            return true; // Fail safe
        }
    }
    
    public boolean hasEnoughStock(String itemId, int quantity) {
        ShopItem item = items.get(itemId);
        if (item == null) {
            return false;
        }
        
        return item.getStock() >= quantity;
    }
    
    public int getAvailableQuantity(String itemId, UUID playerId) {
        ShopItem item = items.get(itemId);
        if (item == null) {
            return 0;
        }
        
        int availableByStock = item.getStock();
        int availableByDailyLimit = Integer.MAX_VALUE;
        int availableByPlayerLimit = Integer.MAX_VALUE;
        
        if (item.getDailyLimit() > 0) {
            availableByDailyLimit = item.getDailyLimit() - getPlayerDailyPurchases(itemId, playerId);
        }
        
        if (item.getPlayerLimit() > 0) {
            availableByPlayerLimit = item.getPlayerLimit() - getPlayerTotalPurchases(itemId, playerId);
        }
        
        return Math.min(Math.min(availableByStock, availableByDailyLimit), availableByPlayerLimit);
    }
    
    private int getPlayerDailyPurchases(String itemId, UUID playerId) {
        try (Connection connection = databaseManager.getConnection()) {
            LocalDate today = LocalDate.now();
            
            String sql = """
                SELECT SUM(quantity) as total 
                FROM yae_shop_transactions 
                WHERE item_id = ? AND player_uuid = ? 
                AND DATE(created_at) = ? AND transaction_type = 'BUY'
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, itemId);
                stmt.setString(2, playerId.toString());
                stmt.setDate(3, java.sql.Date.valueOf(today));
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("total");
                }
                
                return 0;
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get daily purchases", e);
            return 0;
        }
    }
    
    private int getPlayerTotalPurchases(String itemId, UUID playerId) {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = """
                SELECT SUM(quantity) as total 
                FROM yae_shop_transactions 
                WHERE item_id = ? AND player_uuid = ? AND transaction_type = 'BUY'
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, itemId);
                stmt.setString(2, playerId.toString());
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("total");
                }
                
                return 0;
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get total purchases", e);
            return 0;
        }
    }
    
    @Override
    @NotNull
    public String getStatus() {
        return String.format("ShopManager[%s, %s, %d items, %d categories]", 
                           isEnabled() ? "ENABLED" : "DISABLED",
                           isHealthy() ? "HEALTHY" : "UNHEALTHY",
                           items.size(),
                           categories.size());
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return serviceType == ServiceType.DATABASE;
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
}
