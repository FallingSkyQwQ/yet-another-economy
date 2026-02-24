package com.yae.api.credit;

/**
 * Credit grade classifications based on credit scores
 * Standard A-F grading system
 */
public enum CreditGrade {
    /**
     * Excellent credit (750-850)
     * - Lowest interest rates
     * - Highest credit limits
     * - Premium loan products
     */
    A(750, 850, "优秀", "§a★★★★★", 0.035, 1000000.0),
    
    /**
     * Good credit (650-749)
     * - Low interest rates
     * - High credit limits
     * - Good loan products
     */
    B(650, 749, "良好", "§2★★★★☆", 0.045, 500000.0),
    
    /**
     * Average credit (550-649)
     * - Moderate interest rates
     * - Moderate credit limits
     * - Standard loan products
     */
    C(550, 649, "一般", "§e★★★☆☆", 0.065, 200000.0),
    
    /**
     * Below average credit (450-549)
     * - Higher interest rates
     * - Lower credit limits
     * - Limited loan products
     */
    D(450, 549, "较差", "§c★★☆☆☆", 0.095, 50000.0),
    
    /**
     * Poor credit (300-449)
     * - Highest interest rates
     * - Very low credit limits
     * - Minimal loan products
     */
    F(300, 449, "很差", "§4★☆☆☆☆", 0.150, 10000.0);
    
    private final int minScore;
    private final int maxScore;
    private final String chineseName;
    private final String displaySymbol;
    private final double baseInterestRate; // Base annual interest rate
    private final double maxCreditLimit;   // Maximum credit limit
    
    CreditGrade(int minScore, int maxScore, String chineseName, String displaySymbol, 
                double baseInterestRate, double maxCreditLimit) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.chineseName = chineseName;
        this.displaySymbol = displaySymbol;
        this.baseInterestRate = baseInterestRate;
        this.maxCreditLimit = maxCreditLimit;
    }
    
    /**
     * Get the minimum score for this grade
     * @return minimum score
     */
    public int getMinScore() {
        return minScore;
    }
    
    /**
     * Get the maximum score for this grade
     * @return maximum score
     */
    public int getMaxScore() {
        return maxScore;
    }
    
    /**
     * Get the Chinese name for this grade
     * @return Chinese name
     */
    public String getChineseName() {
        return chineseName;
    }
    
    /**
     * Get the display symbol for this grade
     * @return display symbol
     */
    public String getDisplaySymbol() {
        return displaySymbol;
    }
    
    /**
     * Get the base annual interest rate for this grade
     * @return base interest rate
     */
    public double getBaseInterestRate() {
        return baseInterestRate;
    }
    
    /**
     * Get the maximum credit limit for this grade
     * @return maximum credit limit
     */
    public double getMaxCreditLimit() {
        return maxCreditLimit;
    }
    
    /**
     * Get credit grade from score
     * @param score credit score (300-850)
     * @return corresponding credit grade
     */
    public static CreditGrade fromScore(int score) {
        for (CreditGrade grade : values()) {
            if (score >= grade.minScore && score <= grade.maxScore) {
                return grade;
            }
        }
        return F; // Default to lowest grade
    }
    
    /**
     * Get the next better grade
     * @return next grade or same if already highest
     */
    public CreditGrade getNextGrade() {
        switch (this) {
            case F: return D;
            case D: return C;
            case C: return B;
            case B: return A;
            case A: return A;
            default: return this;
        }
    }
    
    /**
     * Get the next worse grade
     * @return next grade or same if already lowest
     */
    public CreditGrade getPreviousGrade() {
        switch (this) {
            case A: return B;
            case B: return C;
            case C: return D;
            case D: return F;
            case F: return F;
            default: return this;
        }
    }
    
    /**
     * Check if this grade qualifies for a specific loan type
     * @param loanType the loan type
     * @return true if qualifies
     */
    public boolean qualifiesForLoan(LoanType loanType) {
        switch (loanType) {
            case CREDIT:
                return this.ordinal() <= C.ordinal(); // C or better
            case MORTGAGE:
                return this.ordinal() <= B.ordinal(); // B or better
            case BUSINESS:
                return this == A; // Only A grade
            default:
                return this.ordinal() <= D.ordinal(); // D or better
        }
    }
    
    /**
     * Get interest rate for specific loan type
     * @param loanType the loan type
     * @return interest rate for this grade and loan type
     */
    public double getInterestRate(LoanType loanType) {
        double multiplier = 1.0;
        
        switch (loanType) {
            case CREDIT:
                multiplier = 1.2; // 20% premium for credit loans
                break;
            case MORTGAGE:
                multiplier = 0.8; // 20% discount for mortgages
                break;
            case BUSINESS:
                multiplier = 1.5; // 50% premium for business loans
                break;
            default:
                multiplier = 1.0;
        }
        
        return Math.min(0.25, baseInterestRate * multiplier); // Cap at 25%
    }
    
    /**
     * Get display name with Chinese name and symbol
     * @return formatted display name
     */
    public String getDisplayName() {
        return String.format("%s %s", displaySymbol, chineseName);
    }
    
    /**
     * Check if this is a good credit grade
     * @return true if A or B grade
     */
    public boolean isGoodCredit() {
        return this == A || this == B;
    }
    
    /**
     * Check if this is an average credit grade
     * @return true if C grade
     */
    public boolean isAverageCredit() {
        return this == C;
    }
    
    /**
     * Check if this is a poor credit grade
     * @return true if D or F grade
     */
    public boolean isPoorCredit() {
        return this == D || this == F;
    }
    
    @Override
    public String toString() {
        return String.format("%s(%d-%d)", chineseName, minScore, maxScore);
    }
}
