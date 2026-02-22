package com.yae.api.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Represents a shop item with all its properties.
 */
public class ShopItem {
    
    private final String id;
    private final String displayName;
    private final String category;
    private final double price;
    private final double sellPrice;
    private final int stock;
    private final int dailyLimit;
    private final int playerLimit;
    private final List<String> description;
    private final boolean enabled;
    
    public ShopItem(@NotNull String id, 
                   @NotNull String displayName,
                   @NotNull String category,
                   double price,
                   double sellPrice,
                   int stock,
                   int dailyLimit,
                   int playerLimit,
                   @Nullable List<String> description,
                   boolean enabled) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.price = Math.max(0, price);
        this.sellPrice = Math.max(0, sellPrice);
        this.stock = Math.max(0, stock);
        this.dailyLimit = Math.max(0, dailyLimit);
        this.playerLimit = Math.max(0, playerLimit);
        this.description = description != null ? List.copyOf(description) : List.of();
        this.enabled = enabled;
    }
    
    @NotNull
    public String getId() {
        return id;
    }
    
    @NotNull
    public String getDisplayName() {
        return displayName;
    }
    
    @NotNull
    public String getCategory() {
        return category;
    }
    
    public double getPrice() {
        return price;
    }
    
    public double getSellPrice() {
        return sellPrice;
    }
    
    public int getStock() {
        return stock;
    }
    
    public int getDailyLimit() {
        return dailyLimit;
    }
    
    public int getPlayerLimit() {
        return playerLimit;
    }
    
    @NotNull
    public List<String> getDescription() {
        return description;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the market price range for this item.
     * This represents the player's market value range.
     */
    @NotNull
    public PriceRange getMarketPriceRange() {
        // Market price is typically higher than shop sell price but lower than shop buy price
        double minMarketPrice = sellPrice * 1.1;  // 10% above sell price
        double maxMarketPrice = price * 0.9;      // 10% below shop buy price
        
        return new PriceRange(minMarketPrice, maxMarketPrice);
    }
    
    /**
     * Checks if this item has stock limits.
     */
    public boolean hasStockLimit() {
        return stock > 0;
    }
    
    /**
     * Checks if this item has daily purchase limits.
     */
    public boolean hasDailyLimit() {
        return dailyLimit > 0;
    }
    
    /**
     * Checks if this item has per-player purchase limits.
     */
    public boolean hasPlayerLimit() {
        return playerLimit > 0;
    }
    
    /**
     * Gets the tax amount for purchasing this item.
     */
    public double getTaxAmount(double taxRate) {
        return price * taxRate;
    }
    
    /**
     * Gets the final price including tax.
     */
    public double getFinalPrice(double taxRate) {
        return price + getTaxAmount(taxRate);
    }
    
    /**
     * Gets the profit margin for the shop.
     */
    public double getProfitMargin() {
        return ((price - sellPrice) / sellPrice) * 100;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShopItem shopItem = (ShopItem) o;
        return id.equals(shopItem.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("ShopItem{id='%s', name='%s', category='%s', price=%.2f, sellPrice=%.2f, stock=%d}",
                           id, displayName, category, price, sellPrice, stock);
    }
    
    /**
     * Represents a price range with minimum and maximum values.
     */
    public static class PriceRange {
        private final double minPrice;
        private final double maxPrice;
        
        public PriceRange(double minPrice, double maxPrice) {
            this.minPrice = Math.min(minPrice, maxPrice);
            this.maxPrice = Math.max(minPrice, maxPrice);
        }
        
        public double getMinPrice() {
            return minPrice;
        }
        
        public double getMaxPrice() {
            return maxPrice;
        }
        
        public double getAveragePrice() {
            return (minPrice + maxPrice) / 2;
        }
        
        public boolean isInRange(double price) {
            return price >= minPrice && price <= maxPrice;
        }
        
        @Override
        public String toString() {
            return String.format("%.2f - %.2f", minPrice, maxPrice);
        }
    }
}
