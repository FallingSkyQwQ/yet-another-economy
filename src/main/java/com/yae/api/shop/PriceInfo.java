package com.yae.api.shop;

import org.jetbrains.annotations.NotNull;

/**
 * Represents comprehensive price information for a shop item.
 */
public class PriceInfo {
    
    private final double shopPrice;        // Shop buy price (what player pays to buy)
    private final double standardPrice;    // Standard/sell price (what player gets when selling)
    private final ShopItem.PriceRange marketPriceRange; // Market price range (player-to-player trading)
    private final double taxAmount;        // Tax amount for purchase
    private final double finalPrice;       // Final price including tax
    
    public PriceInfo(double shopPrice, double standardPrice, 
                    @NotNull ShopItem.PriceRange marketPriceRange,
                    double taxAmount, double finalPrice) {
        this.shopPrice = Math.max(0, shopPrice);
        this.standardPrice = Math.max(0, standardPrice);
        this.marketPriceRange = marketPriceRange;
        this.taxAmount = Math.max(0, taxAmount);
        this.finalPrice = Math.max(0, finalPrice);
    }
    
    public double getShopPrice() {
        return shopPrice;
    }
    
    public double getStandardPrice() {
        return standardPrice;
    }
    
    @NotNull
    public ShopItem.PriceRange getMarketPriceRange() {
        return marketPriceRange;
    }
    
    public double getMarketMinPrice() {
        return marketPriceRange.getMinPrice();
    }
    
    public double getMarketMaxPrice() {
        return marketPriceRange.getMaxPrice();
    }
    
    public double getMarketAveragePrice() {
        return marketPriceRange.getAveragePrice();
    }
    
    public double getTaxAmount() {
        return taxAmount;
    }
    
    public double getFinalPrice() {
        return finalPrice;
    }
    
    /**
     * Gets the base price (before tax).
     */
    public double getBasePrice() {
        return shopPrice;
    }
    
    /**
     * Gets the profit margin for selling back to the shop.
     */
    public double getSellBackProfit() {
        return ((shopPrice - standardPrice) / standardPrice) * 100;
    }
    
    /**
     * Checks if the item is sold at a profit to the shop.
     */
    public boolean isSoldAtProfit() {
        return standardPrice > shopPrice;
    }
    
    /**
     * Gets the tax rate applied.
     */
    public double getTaxRate() {
        return taxAmount / shopPrice;
    }
    
    @Override
    public String toString() {
        return String.format("PriceInfo[shop=%.2f, standard=%.2f, market=%s, tax=%.2f, final=%.2f]",
                           shopPrice, standardPrice, marketPriceRange, taxAmount, finalPrice);
    }
}
