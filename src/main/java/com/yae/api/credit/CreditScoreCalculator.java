package com.yae.api.credit;

import com.yae.api.core.ServiceConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Credit score calculator for evaluating player creditworthiness
 * Uses multiple factors including transaction history, repayment record, and account behavior
 */
public class CreditScoreCalculator {
    
    // Base score range
    public static final int MIN_SCORE = 300;
    public static final int MAX_SCORE = 850;
    public static final int BASE_SCORE = 650;
    
    // Score weights for different factors (total = 100%)
    public static final double TRANSACTION_FREQUENCY_WEIGHT = 0.25;
    public static final double TRANSACTION_AMOUNT_WEIGHT = 0.20;
    public static final double REPAYMENT_HISTORY_WEIGHT = 0.35;
    public static final double ACCOUNT_AGE_WEIGHT = 0.10;
    public static final double CURRENT_BALANCE_WEIGHT = 0.10;
    
    // Penalty weights
    public static final double OVERDUE_PAYMENT_PENALTY = 50.0;
    public static final double DEFAULT_PENALTY = 200.0;
    public static final double RECOVERY_BONUS = 30.0;
    
    private final ServiceConfig config;
    
    public CreditScoreCalculator(ServiceConfig config) {
        this.config = config;
    }
    
    /**
     * Calculate credit score for a player based on their financial history
     * 
     * @param playerId The player's UUID
     * @param transactionHistory List of transaction data
     * @param loanHistory List of loan data
     * @param accountData Current account information
     * @return Calculated credit score (300-850)
     */
    public int calculateCreditScore(UUID playerId, 
                                  List<TransactionData> transactionHistory, 
                                  List<LoanData> loanHistory, 
                                  AccountData accountData) {
        
        double baseScore = BASE_SCORE;
        
        // Calculate component scores
        double transactionFrequencyScore = calculateTransactionFrequencyScore(transactionHistory);
        double transactionAmountScore = calculateTransactionAmountScore(transactionHistory);
        double repaymentHistoryScore = calculateRepaymentHistoryScore(loanHistory);
        double accountAgeScore = calculateAccountAgeScore(accountData);
        double currentBalanceScore = calculateCurrentBalanceScore(accountData);
        
        // Apply weights to each component
        double weightedScore = baseScore +
            (transactionFrequencyScore * TRANSACTION_FREQUENCY_WEIGHT) +
            (transactionAmountScore * TRANSACTION_AMOUNT_WEIGHT) +
            (repaymentHistoryScore * REPAYMENT_HISTORY_WEIGHT) +
            (accountAgeScore * ACCOUNT_AGE_WEIGHT) +
            (currentBalanceScore * CURRENT_BALANCE_WEIGHT);
        
        // Apply penalties for negative records
        double penaltyScore = applyPenalties(loanHistory, weightedScore);
        
        // Ensure score is within valid range
        int finalScore = (int) Math.round(Math.max(MIN_SCORE, Math.min(MAX_SCORE, penaltyScore)));
        
        return finalScore;
    }
    
    /**
     * Calculate transaction frequency score (0-100)
     * Based on number of transactions over time period
     */
    private double calculateTransactionFrequencyScore(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return 50.0; // Neutral score for no history
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sixMonthsAgo = now.minusMonths(6);
        
        long recentTransactions = transactions.stream()
            .filter(t -> t.getTimestamp().isAfter(sixMonthsAgo))
            .count();
        
        // Score based on transaction frequency (0-100)
        // Target: 20+ transactions in 6 months = 100 points
        double frequencyScore = Math.min(100.0, (recentTransactions / 20.0) * 100);
        
        return frequencyScore;
    }
    
    /**
     * Calculate transaction amount score (0-100)
     * Based on average transaction amounts and consistency
     */
    private double calculateTransactionAmountScore(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return 50.0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sixMonthsAgo = now.minusMonths(6);
        
        List<TransactionData> recentTransactions = transactions.stream()
            .filter(t -> t.getTimestamp().isAfter(sixMonthsAgo))
            .toList();
        
        if (recentTransactions.isEmpty()) {
            return 50.0;
        }
        
        double totalAmount = recentTransactions.stream()
            .mapToDouble(TransactionData::getAmount)
            .sum();
        
        double averageAmount = totalAmount / recentTransactions.size();
        
        // Score based on transaction amounts (0-100)
        // Using logarithmic scale to avoid extreme scores
        double amountScore = Math.min(100.0, Math.log10(averageAmount + 1) * 20);
        
        return amountScore;
    }
    
    /**
     * Calculate repayment history score (0-100)
     * Based on on-time payments, defaults, and recovery
     */
    private double calculateRepaymentHistoryScore(List<LoanData> loanHistory) {
        if (loanHistory == null || loanHistory.isEmpty()) {
            return 70.0; // Good starting score for no history
        }
        
        int totalPayments = 0;
        int onTimePayments = 0;
        int latePayments = 0;
        int defaults = 0;
        int recoveries = 0;
        
        for (LoanData loan : loanHistory) {
            totalPayments += loan.getTotalPayments();
            onTimePayments += loan.getOnTimePayments();
            latePayments += loan.getLatePayments();
            
            if (loan.isDefaulted()) {
                defaults++;
                if (loan.isRecovered()) {
                    recoveries++;
                }
            }
        }
        
        if (totalPayments == 0) {
            return 70.0;
        }
        
        // Calculate payment performance ratios
        double onTimeRatio = (double) onTimePayments / totalPayments;
        double lateRatio = (double) latePayments / totalPayments;
        double defaultRatio = (double) defaults / loanHistory.size();
        double recoveryRatio = defaults > 0 ? (double) recoveries / defaults : 0;
        
        // Base score from on-time payments (0-70)
        double baseScore = onTimeRatio * 70;
        
        // Penalties for late payments and defaults
        double latePenalty = lateRatio * 30; // Up to -30 points
        double defaultPenalty = defaultRatio * 40; // Up to -40 points
        
        // Bonus for recoveries
        double recoveryBonus = recoveryRatio * 20; // Up to +20 points
        
        double finalScore = baseScore - latePenalty - defaultPenalty + recoveryBonus;
        
        return Math.max(0.0, Math.min(100.0, finalScore));
    }
    
    /**
     * Calculate account age score (0-100)
     * Based on how long the account has been active
     */
    private double calculateAccountAgeScore(AccountData accountData) {
        if (accountData == null || accountData.getCreatedDate() == null) {
            return 30.0; // Low score for unknown account age
        }
        
        long daysSinceCreation = java.time.Duration.between(
            accountData.getCreatedDate(), 
            LocalDateTime.now()
        ).toDays();
        
        // Score based on account age (0-100)
        // Target: 1 year = 100 points
        double ageScore = Math.min(100.0, (daysSinceCreation / 365.0) * 100);
        
        return ageScore;
    }
    
    /**
     * Calculate current balance score (0-100)
     * Based on current account balance relative to history
     */
    private double calculateCurrentBalanceScore(AccountData accountData) {
        if (accountData == null) {
            return 30.0;
        }
        
        double currentBalance = accountData.getCurrentBalance();
        double averageBalance = accountData.getAverageBalance();
        
        if (averageBalance <= 0) {
            return currentBalance > 0 ? 70.0 : 30.0;
        }
        
        // Score based on balance relative to average (0-100)
        double ratio = currentBalance / averageBalance;
        
        // Logarithmic scale to avoid extreme scores
        double balanceScore = Math.min(100.0, Math.log10(ratio + 1) * 50);
        
        return Math.max(0.0, balanceScore);
    }
    
    /**
     * Apply penalties for negative records
     */
    private double applyPenalties(List<LoanData> loanHistory, double currentScore) {
        if (loanHistory == null || loanHistory.isEmpty()) {
            return currentScore;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneYearAgo = now.minusYears(1);
        
        // Count recent penalties
        int recentOverduePayments = 0;
        int recentDefaults = 0;
        int recentRecoveries = 0;
        
        for (LoanData loan : loanHistory) {
            if (loan.getLastOverdueDate() != null && 
                loan.getLastOverdueDate().isAfter(oneYearAgo)) {
                recentOverduePayments++;
            }
            
            if (loan.isDefaulted() && loan.getDefaultDate() != null &&
                loan.getDefaultDate().isAfter(oneYearAgo)) {
                recentDefaults++;
            }
            
            if (loan.isRecovered() && loan.getRecoveryDate() != null &&
                loan.getRecoveryDate().isAfter(oneYearAgo)) {
                recentRecoveries++;
            }
        }
        
        // Apply penalties
        double penaltyScore = currentScore;
        penaltyScore -= recentOverduePayments * OVERDUE_PAYMENT_PENALTY;
        penaltyScore -= recentDefaults * DEFAULT_PENALTY;
        penaltyScore += recentRecoveries * RECOVERY_BONUS;
        
        return penaltyScore;
    }
    
    /**
     * Get credit grade based on credit score
     * 
     * @param score The credit score (300-850)
     * @return The corresponding credit grade
     */
    public static CreditGrade getCreditGrade(int score) {
        if (score >= 800) {
            return CreditGrade.A;
        } else if (score >= 740) {
            return CreditGrade.B;
        } else if (score >= 670) {
            return CreditGrade.C;
        } else if (score >= 580) {
            return CreditGrade.D;
        } else {
            return CreditGrade.F;
        }
    }
    
    /**
     * Check if credit score qualifies for specific loan type
     * 
     * @param score The credit score
     * @param loanType The type of loan
     * @return true if qualifies
     */
    public static boolean qualifiesForLoan(int score, LoanType loanType) {
        switch (loanType) {
            case CREDIT:
                return score >= 600; // Minimum 600 for credit loans
            case MORTGAGE:
                return score >= 650; // Minimum 650 for mortgages
            case BUSINESS:
                return score >= 700; // Minimum 700 for business loans
            default:
                return score >= 580; // Minimum 580 for other loans
        }
    }
    
    // Inner classes for data structures
    
    public static class TransactionData {
        private final double amount;
        private final LocalDateTime timestamp;
        private final String type;
        
        public TransactionData(double amount, LocalDateTime timestamp, String type) {
            this.amount = amount;
            this.timestamp = timestamp;
            this.type = type;
        }
        
        public double getAmount() { return amount; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getType() { return type; }
    }
    
    public static class LoanData {
        private final int totalPayments;
        private final int onTimePayments;
        private final int latePayments;
        private final boolean isDefaulted;
        private final boolean isRecovered;
        private final LocalDateTime lastOverdueDate;
        private final LocalDateTime defaultDate;
        private final LocalDateTime recoveryDate;
        
        public LoanData(int totalPayments, int onTimePayments, int latePayments,
                       boolean isDefaulted, boolean isRecovered,
                       LocalDateTime lastOverdueDate, LocalDateTime defaultDate,
                       LocalDateTime recoveryDate) {
            this.totalPayments = totalPayments;
            this.onTimePayments = onTimePayments;
            this.latePayments = latePayments;
            this.isDefaulted = isDefaulted;
            this.isRecovered = isRecovered;
            this.lastOverdueDate = lastOverdueDate;
            this.defaultDate = defaultDate;
            this.recoveryDate = recoveryDate;
        }
        
        public int getTotalPayments() { return totalPayments; }
        public int getOnTimePayments() { return onTimePayments; }
        public int getLatePayments() { return latePayments; }
        public boolean isDefaulted() { return isDefaulted; }
        public boolean isRecovered() { return isRecovered; }
        public LocalDateTime getLastOverdueDate() { return lastOverdueDate; }
        public LocalDateTime getDefaultDate() { return defaultDate; }
        public LocalDateTime getRecoveryDate() { return recoveryDate; }
    }
    
    public static class AccountData {
        private final double currentBalance;
        private final double averageBalance;
        private final LocalDateTime createdDate;
        
        public AccountData(double currentBalance, double averageBalance, LocalDateTime createdDate) {
            this.currentBalance = currentBalance;
            this.averageBalance = averageBalance;
            this.createdDate = createdDate;
        }
        
        public double getCurrentBalance() { return currentBalance; }
        public double getAverageBalance() { return averageBalance; }
        public LocalDateTime getCreatedDate() { return createdDate; }
    }
}
