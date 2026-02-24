package com.yae.api.loan;

import org.bukkit.inventory.ItemStack;

/**
 * Represents an item used as collateral for a secured loan
 */
public class CollateralItem {
    
    private final String type;
    private final String description;
    private final double quantity;
    private final ItemStack itemStack;
    private final double assessedValue;
    private final double discountRate;
    
    public CollateralItem(String type, String description, double quantity, 
                         ItemStack itemStack, double assessedValue, double discountRate) {
        this.type = type;
        this.description = description;
        this.quantity = quantity;
        this.itemStack = itemStack;
        this.assessedValue = assessedValue;
        this.discountRate = discountRate;
    }
    
    /**
     * Calculate discounted value for loan collateral
     */
    public double getDiscountedValue() {
        return assessedValue * discountRate * quantity;
    }
    
    /**
     * Check if collateral is sufficient for loan amount
     */
    public boolean isSufficientForLoan(double requiredLoanAmount) {
        return getDiscountedValue() >= requiredLoanAmount * 0.8; // 80% loan-to-value ratio
    }
    
    /**
     * Validate collateral item
     */
    public boolean isValid() {
        return type != null && !type.trim().isEmpty() && 
               assessedValue > 0 && discountRate > 0 && quantity > 0;
    }
    
    // Getters
    public String getType() { return type; }
    public String getDescription() { return description; }
    public double getQuantity() { return quantity; }
    public ItemStack getItemStack() { return itemStack; }
    public double getAssessedValue() { return assessedValue; }
    public double getDiscountRate() { return discountRate; }
    
    /**
     * Collateral type configuration
     */
    public static class CollateralTypeInfo {
        public final String collateralKey;
        public final String collateralName;
        public final String materialType;
        public final double baseValue;
        public final double discountRate;
        public final boolean isActive;
        
        public CollateralTypeInfo(String collateralKey, String collateralName, String materialType,
                                double baseValue, double discountRate, boolean isActive) {
            this.collateralKey = collateralKey;
            this.collateralName = collateralName;
            this.materialType = materialType;
            this.baseValue = baseValue;
            this.discountRate = discountRate;
            this.isActive = isActive;
        }
    }
}
