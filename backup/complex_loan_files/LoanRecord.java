package com.yae.api.loan;

import com.yae.api.credit.LoanType;
import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.CreditScoreCalculator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Load record with comprehensive loan information
 * Used by repayment and overdue processing services
 */
public class LoanRecord {
    
    // Core loan information
    private final String loanId;
    private final UUID borrowerId;
    private final String lenderId;
    private final LoanType loanType;
    private final String loanPurpose;
    private final double principalAmount;
    private double currentBalance;
    private final double interestRate;
    private final int termMonths;
    private final LocalDate startDate;
    private final LocalDateTime maturityDate;
    private final LocalDateTime nextPaymentDate;
    private final double monthlyPayment;
    
    // Status tracking
    private String status;
    private int paymentsMade;
    private int totalPayments;
    private double totalInterestPaid;
    private double totalPrincipalPaid;
    private int overduePayments;
    private double overdueAmount;
    private LocalDateTime lastOverdueDate;
    private boolean isInDefault;
    private LocalDateTime defaultDate;
    
    // Credit information
    private final int borrowerCreditScore;
    private final CreditGrade borrowerCreditGrade;
    private boolean autoPayEnabled;
    
    // Processing settings
    private double penaltyRate;
    private boolean penaltyWaived;
    
    // Collateral information (for secured loans)
    private String collateralType;
    private double collateralValue;
    private double collateralDiscountRate;
    
    public LoanRecord(
            String loanId, UUID borrowerId, String lenderId, LoanType loanType,
            String loanPurpose, double principalAmount, double currentBalance,
            double interestRate, int termMonths, LocalDate startDate,
            LocalDateTime maturityDate, LocalDateTime nextPaymentDate,
            double monthlyPayment, String status, int paymentsMade,
            int totalPayments, double totalInterestPaid, double totalPrincipalPaid,
            int overduePayments, double overdueAmount, LocalDateTime lastOverdueDate,
            boolean isInDefault, LocalDateTime defaultDate, int borrowerCreditScore,
            CreditGrade borrowerCreditGrade, boolean autoPayEnabled,
            double penaltyRate, boolean penaltyWaived, String collateralType,
            double collateralValue, double collateralDiscountRate
    ) {
        this.loanId = loanId;
        this.borrowerId = borrowerId;
        this.lenderId = lenderId;
        this.loanType = loanType;
        this.loanPurpose = loanPurpose;
        this.principalAmount = principalAmount;
        this.currentBalance = currentBalance;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.startDate = startDate;
        this.maturityDate = maturityDate;
        this.nextPaymentDate = nextPaymentDate;
        this.monthlyPayment = monthlyPayment;
        this.status = status;
        this.paymentsMade = paymentsMade;
        this.totalPayments = totalPayments;
        this.totalInterestPaid = totalInterestPaid;
        this.totalPrincipalPaid = totalPrincipalPaid;
        this.overduePayments = overduePayments;
        this.overdueAmount = overdueAmount;
        this.lastOverdueDate = lastOverdueDate;
        this.isInDefault = isInDefault;
        this.defaultDate = defaultDate;
        this.borrowerCreditScore = borrowerCreditScore;
        this.borrowerCreditGrade = borrowerCreditGrade;
        this.autoPayEnabled = autoPayEnabled;
        this.penaltyRate = penaltyRate;
        this.penaltyWaived = penaltyWaived;
        this.collateralType = collateralType;
        this.collateralValue = collateralValue;
        this.collateralDiscountRate = collateralDiscountRate;
    }
    
    /**
     * Check if loan allows manual payments
     */
    public boolean allowsManualPayments() {
        return "ACTIVE".equals(status) || "OVERDUE".equals(status);
    }
    
    /**
     * Check if automatic payments are enabled
     */
    public boolean isAutoPayEnabled() {
        return autoPayEnabled && isActive();
    }
    
    /**
     * Get current interest due
     */
    public double getCurrentInterestDue() {
        LocalDate today = LocalDate.now();
        if (today.isAfter(nextPaymentDate.toLocalDate())) {
            // Calculate interest for overdue period
            int daysOverdue = (int) java.time.temporal.ChronoUnit.DAYS.between(nextPaymentDate.toLocalDate(), today);
            double dailyInterestRate = interestRate / 100.0 / 365.0;
            return monthlyPayment * 0.3 + (currentBalance * dailyInterestRate * daysOverdue); // Rough estimate
        }
        return monthlyPayment * 0.3; // Approximate interest portion
    }
    
    /**
     * Get current principal due
     */
    public double getCurrentPrincipalDue() {
        return monthlyPayment - getCurrentInterestDue();
    }
    
    /**
     * Get outstanding penalties
     */
    public double getOutstandingPenalties() {
        return overdueAmount > 0 ? overdueAmount * penaltyRate * overduePayments : 0;
    }
    
    /**
     * Check if payment is overdue
     */
    public boolean isPaymentOverdue() {
        return LocalDateTime.now().isAfter(nextPaymentDate) && overdueAmount > 0;
    }
    
    /**
     * Check if loan is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(status) || "OVERDUE".equals(status);
    }
    
    /**
     * Check if loan is closed
     */
    public boolean isClosed() {
        return "CLOSED".equals(status) || "DEFAULTED".equals(status);
    }
    
    /**
     * Check if loan is in default
     */
    public boolean isInDefault() {
        return isInDefault || "DEFAULTED".equals(status);
    }
    
    /**
     * Check if active collection workflow exists
     */
    public boolean hasActiveCollection() {
        return overduePayments > 0 && overdueAmount > 0;
    }
    
    /**
     * Get remaining payments count
     */
    public int getRemainingPayments() {
        return totalPayments - paymentsMade;
    }
    
    /**
     * Calculate loan terms
     */
    public LoanTerms getLoanTerms() {
        return new LoanTerms(new LoanTerms.TermsOption(termMonths, interestRate, currentBalance));
    }
    
    /**
     * Get loan summary for display
     */
    public LoanSummary getLoanSummary() {
        return new LoanSummary(
            loanId, loanType, borrowerCreditScore, borrowerCreditGrade,
            principalAmount, currentBalance, interestRate, monthlyPayment,
            status, paymentsMade, totalPayments, nextPaymentDate
        );
    }
    
    // Getters
    public String getLoanId() { return loanId; }
    public UUID getBorrowerId() { return borrowerId; }
    public String getLenderId() { return lenderId; }
    public LoanType getLoanType() { return loanType; }
    public String getLoanPurpose() { return loanPurpose; }
    public double getPrincipalAmount() { return principalAmount; }
    public double getCurrentBalance() { return currentBalance; }
    public double getInterestRate() { return interestRate; }
    public int getTermMonths() { return termMonths; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDateTime getMaturityDate() { return maturityDate; }
    public LocalDateTime getNextPaymentDate() { return nextPaymentDate; }
    public double getMonthlyPayment() { return monthlyPayment; }
    public String getStatus() { return status; }
    public int getPaymentsMade() { return paymentsMade; }
    public int getTotalPayments() { return totalPayments; }
    public double getTotalInterestPaid() { return totalInterestPaid; }
    public double getTotalPrincipalPaid() { return totalPrincipalPaid; }
    public int getOverduePayments() { return overduePayments; }
    public double getOverdueAmount() { return overdueAmount; }
    public LocalDateTime getLastOverdueDate() { return lastOverdueDate; }
    public boolean isInDefaultStatus() { return isInDefault; }
    public LocalDateTime getDefaultDate() { return defaultDate; }
    public int getBorrowerCreditScore() { return borrowerCreditScore; }
    public CreditGrade getBorrowerCreditGrade() { return borrowerCreditGrade; }
    public boolean isAutoPayEnabled() { return autoPayEnabled; }
    public double getPenaltyRate() { return penaltyRate; }
    public boolean isPenaltyWaived() { return penaltyWaived; }
    public String getCollateralType() { return collateralType; }
    public double getCollateralValue() { return collateralValue; }
    public double getCollateralDiscountRate() { return collateralDiscountRate; }
    
    // Basic setters  
    public void setStatus(String status) { this.status = status; }
    public void setPaymentsMade(int paymentsMade) { this.paymentsMade = paymentsMade; }
    public void setOverduePayments(int overduePayments) { this.overduePayments = overduePayments; }
    public void setOverdueAmount(double overdueAmount) { this.overdueAmount = overdueAmount; }
    public void setLastOverdueDate(LocalDateTime lastOverdueDate) { this.lastOverdueDate = lastOverdueDate; }
    public void setInDefault(boolean inDefault) { this.isInDefault = inDefault; }
    public void setDefaultDate(LocalDateTime defaultDate) { this.defaultDate = defaultDate; }
    public void setAutoPayEnabled(boolean autoPayEnabled) { this.autoPayEnabled = autoPayEnabled; }
    public void setPenaltyRate(double penaltyRate) { this.penaltyRate = penaltyRate; }
    public void setPenaltyWaived(boolean penaltyWaived) { this.penaltyWaived = penaltyWaived; }
    public void setCurrentBalance(double currentBalance) { this.currentBalance = currentBalance; }
    public void setTotalInterestPaid(double totalInterestPaid) { this.totalInterestPaid = totalInterestPaid; }
    public void setTotalPrincipalPaid(double totalPrincipalPaid) { this.totalPrincipalPaid = totalPrincipalPaid; }
    
    /**
     * Update after payment received
     */
    public void updateAfterPayment(double principalPaid, double interestPaid, double penaltyPaid) {
        this.paymentsMade++;
        this.totalPrincipalPaid += principalPaid;
        this.totalInterestPaid += interestPaid;
        this.currentBalance -= principalPaid;
        this.lastOverdueDate = overdueAmount == 0 ? null : LocalDateTime.now();
    }
    
    /**
     * Loan summary for display
     */
    public static class LoanSummary {
        private final String loanId;
        private final LoanType loanType;
        private final int creditScore;
        private final CreditGrade creditGrade;
        private final double principalAmount;
        private final double currentBalance;
        private final double interestRate;
        private final double monthlyPayment;
        private final String status;
        private final int paymentsMade;
        private final int totalPayments;
        private final LocalDateTime nextPaymentDate;
        
        public LoanSummary(String loanId, LoanType loanType, int creditScore, CreditGrade creditGrade,
                         double principalAmount, double currentBalance, double interestRate,
                         double monthlyPayment, String status, int paymentsMade, int totalPayments,
                         LocalDateTime nextPaymentDate) {
            this.loanId = loanId;
            this.loanType = loanType;
            this.creditScore = creditScore;
            this.creditGrade = creditGrade;
            this.principalAmount = principalAmount;
            this.currentBalance = currentBalance;
            this.interestRate = interestRate;
            this.monthlyPayment = monthlyPayment;
            this.status = status;
            this.paymentsMade = paymentsMade;
            this.totalPayments = totalPayments;
            this.nextPaymentDate = nextPaymentDate;
        }
        
        public String getFormattedSummary() {
            return String.format("""
                贷款概览 - %s
                类型: %s | 评分: %s(%d) | 利率: %.2f%%
                本金: ¥%.2f | 余额: ¥%.2f | 月供: ¥%.2f
                状态: %s | 已还: %d/%d | 下次: %s
                """, 
                loanId, loanType.getChineseName(), creditGrade.getChineseName(), creditScore,
                interestRate, principalAmount, currentBalance, monthlyPayment,
                status, paymentsMade, totalPayments, 
                nextPaymentDate != null ? nextPaymentDate.toLocalDate().toString() : "已完成"
            );
        }
        
        // Getters
        public String getLoanId() { return loanId; }
        public LoanType getLoanType() { return loanType; }
        public int getCreditScore() { return creditScore; }
        public CreditGrade getCreditGrade() { return creditGrade; }
        public double getPrincipalAmount() { return principalAmount; }
        public double getCurrentBalance() { return currentBalance; }
        public double getInterestRate() { return interestRate; }
        public double getMonthlyPayment() { return monthlyPayment; }
        public String getStatus() { return status; }
        public int getPaymentsMade() { return paymentsMade; }
        public int getTotalPayments() { return totalPayments; }
        public LocalDateTime getNextPaymentDate() { return nextPaymentDate; }
    }
}
