package com.yae.api.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the result of a purchase calculation.
 */
public class PurchaseCalculation {
    
    private final PurchaseResult result;
    private final String message;
    private final ShopItem item;
    private final int quantity;
    private final double unitPrice;
    private final double totalPrice;
    private final double taxAmount;
    private final double finalPrice;
    
    public PurchaseCalculation(@NotNull PurchaseResult result,
                              @NotNull String message,
                              @Nullable ShopItem item,
                              double unitPrice,
                              double totalPrice,
                              double taxAmount,
                              double finalPrice) {
        this.result = result;
        this.message = message;
        this.item = item;
        this.quantity = item != null ? Math.abs(result == PurchaseResult.SUCCESS ? 1 : 0) : 0;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.taxAmount = taxAmount;
        this.finalPrice = finalPrice;
    }
    
    public PurchaseCalculation(@NotNull PurchaseResult result,
                              @NotNull String message,
                              @Nullable ShopItem item,
                              int quantity,
                              double totalPrice,
                              double taxAmount,
                              double finalPrice) {
        this.result = result;
        this.message = message;
        this.item = item;
        this.quantity = Math.abs(quantity);
        this.unitPrice = quantity != 0 ? totalPrice / Math.abs(quantity) : 0;
        this.totalPrice = totalPrice;
        this.taxAmount = taxAmount;
        this.finalPrice = finalPrice;
    }
    
    @NotNull
    public PurchaseResult getResult() {
        return result;
    }
    
    @NotNull
    public String getMessage() {
        return message;
    }
    
    @Nullable
    public ShopItem getItem() {
        return item;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public double getUnitPrice() {
        return unitPrice;
    }
    
    public double getTotalPrice() {
        return totalPrice;
    }
    
    public double getTaxAmount() {
        return taxAmount;
    }
    
    public double getFinalPrice() {
        return finalPrice;
    }
    
    /**
     * Checks if the calculation was successful.
     */
    public boolean isSuccessful() {
        return result == PurchaseResult.SUCCESS;
    }
    
    /**
     * Checks if this is a purchase (buying) operation.
     */
    public boolean isPurchase() {
        return result == PurchaseResult.SUCCESS && getTotalPrice() > 0;
    }
    
    /**
     * Checks if this is a sale operation.
     */
    public boolean isSale() {
        return result == PurchaseResult.SUCCESS && getTotalPrice() < 0;
    }
    
    @Override
    public String toString() {
        return String.format("PurchaseCalculation[result=%s, quantity=%d, total=%.2f, final=%.2f, tax=%.2f]",
                           result, quantity, totalPrice, finalPrice, taxAmount);
    }
}
