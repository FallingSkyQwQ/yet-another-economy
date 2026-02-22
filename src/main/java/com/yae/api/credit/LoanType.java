package com.yae.api.credit;

/**
 * Types of loans available in the system
 * Each type has different requirements, interest rates, and terms
 */
public enum LoanType {
    /**
     * Credit loan - Unsecured personal loan based on credit score
     * - Higher interest rates
     * - Shorter terms
     * - Credit score based
     */
    CREDIT("credit", "信用贷款", "§e信用贷", 1.2, 12),
    
    /**
     * Mortgage loan - Secured loan with property as collateral
     * - Lower interest rates
     * - Longer terms
     * - Collateral required
     */
    MORTGAGE("mortgage", "抵押贷款", "§6抵押贷", 0.8, 240),
    
    /**
     * Business loan - Loan for business purposes
     * - Moderate interest rates
     * - Variable terms
     * - Business verification required
     */
    BUSINESS("business", "商业贷款", "§b商业贷", 1.5, 60),
    
    /**
     * Emergency loan - Short-term emergency funding
     * - Highest interest rates
     * - Very short terms
     * - Fast approval
     */
    EMERGENCY("emergency", "应急贷款", "§c应急贷", 2.0, 3);
    
    private final String key;
    private final String chineseName;
    private final String displayName;
    private final double rateMultiplier; // Multiplier for base interest rate
    private final int maxTermMonths;      // Maximum term in months
    
    LoanType(String key, String chineseName, String displayName, 
             double rateMultiplier, int maxTermMonths) {
        this.key = key;
        this.chineseName = chineseName;
        this.displayName = displayName;
        this.rateMultiplier = rateMultiplier;
        this.maxTermMonths = maxTermMonths;
    }
    
    /**
     * Get the configuration key for this loan type
     * @return configuration key
     */
    public String getKey() {
        return key;
    }
    
    /**
     * Get the Chinese name for this loan type
     * @return Chinese name
     */
    public String getChineseName() {
        return chineseName;
    }
    
    /**
     * Get the display name for this loan type
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the interest rate multiplier for this loan type
     * @return rate multiplier
     */
    public double getRateMultiplier() {
        return rateMultiplier;
    }
    
    /**
     * Get the maximum term in months for this loan type
     * @return maximum term in months
     */
    public int getMaxTermMonths() {
        return maxTermMonths;
    }
    
    /**
     * Get loan type from configuration key
     * @param key the configuration key
     * @return corresponding loan type, or null if not found
     */
    public static LoanType fromKey(String key) {
        for (LoanType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Check if this loan type requires collateral
     * @return true if collateral is required
     */
    public boolean requiresCollateral() {
        return this == MORTGAGE;
    }
    
    /**
     * Check if this loan type requires business verification
     * @return true if business verification is required
     */
    public boolean requiresBusinessVerification() {
        return this == BUSINESS;
    }
    
    /**
     * Check if this is a high-risk loan type
     * @return true if high-risk
     */
    public boolean isHighRisk() {
        return this == CREDIT || this == EMERGENCY;
    }
    
    /**
     * Check if this is a secured loan type
     * @return true if secured
     */
    public boolean isSecured() {
        return this == MORTGAGE;
    }
    
    /**
     * Get the minimum credit score required for this loan type
     * @param creditGrade the credit grade
     * @return minimum credit score required
     */
    public int getMinCreditScore(CreditGrade creditGrade) {
        switch (this) {
            case CREDIT:
                return 600;
            case MORTGAGE:
                return 650;
            case BUSINESS:
                return 700;
            case EMERGENCY:
                return 500; // Emergency loans have lower requirements
            default:
                return 580;
        }
    }
    
    /**
     * Get the maximum loan amount multiplier based on credit grade
     * @param creditGrade the credit grade
     * @return maximum amount multiplier
     */
    public double getMaxAmountMultiplier(CreditGrade creditGrade) {
        double baseMultiplier = 1.0;
        
        // Adjust multiplier based on loan type and credit grade
        switch (this) {
            case CREDIT:
                baseMultiplier = creditGrade.getMaxCreditLimit() / 10000.0;
                break;
            case MORTGAGE:
                // Mortgages can be higher due to collateral
                baseMultiplier = (creditGrade.getMaxCreditLimit() / 10000.0) * 2.0;
                break;
            case BUSINESS:
                baseMultiplier = creditGrade.getMaxCreditLimit() / 15000.0;
                break;
            case EMERGENCY:
                baseMultiplier = Math.min(1.0, creditGrade.getMaxCreditLimit() / 20000.0);
                break;
        }
        
        return Math.max(0.1, Math.min(5.0, baseMultiplier));
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s)", displayName, chineseName);
    }
}
