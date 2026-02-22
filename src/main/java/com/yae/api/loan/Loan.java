package com.yae.api.loan;

import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.LoanType;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Loan entity representing a loan in the system
 * Contains all loan details, payment schedule, and status tracking
 */
public class Loan {
    
    // Basic loan information
    private final String loanId;
    private final UUID borrowerId;
    private final UUID lenderId; // System or player lending
    private final LoanType loanType;
    private final String loanPurpose;
    
    // Financial details
    private final double principalAmount;
    private final double currentBalance;
    private final double interestRate; // Annual rate
    private final double originalInterestRate;
    
    // Loan terms
    private final int termMonths;
    private final LocalDateTime startDate;
    private final LocalDateTime maturityDate;
    private final LocalDateTime nextPaymentDate;
    
    // Payment details
    private final double monthlyPayment;
    private final PaymentMethod paymentMethod;
    private final RepaymentMethod repaymentMethod;
    
    // Status and tracking
    private final LoanStatus status;
    private final int paymentsMade;
    private final int totalPayments;
    private final double totalInterestPaid;
    private final double totalPrincipalPaid;
    
    // Overdue tracking
    private final int overduePayments;
    private final double overdueAmount;
    private final LocalDateTime lastOverdueDate;
    private final boolean isInDefault;
    private final LocalDateTime defaultDate;
    
    // Collateral information (for secured loans)
    private final String collateralType;
    private final double collateralValue;
    private final double collateralDiscountRate;
    private final String collateralDescription;
    
    // Credit information
    private final int borrowerCreditScore;
    private final CreditGrade borrowerCreditGrade;
    
    // Administrative information
    private final LocalDateTime applicationDate;
    private final LocalDateTime approvalDate;
    private final LocalDateTime disbursementDate;
    private final String approvedBy;
    private final String notes;
    
    // Flags and settings
    private final boolean autoPayEnabled;
    private final boolean penaltyWaived;
    private final double penaltyRate; // Penalty interest rate
    private final boolean isRefinanced;
    private final String originalLoanId;
    
    // Constructors
    
    private Loan(Builder builder) {
        this.loanId = builder.loanId;
        this.borrowerId = builder.borrowerId;
        this.lenderId = builder.lenderId;
        this.loanType = builder.loanType;
        this.loanPurpose = builder.loanPurpose;
        this.principalAmount = builder.principalAmount;
        this.currentBalance = builder.currentBalance;
        this.interestRate = builder.interestRate;
        this.originalInterestRate = builder.originalInterestRate;
        this.termMonths = builder.termMonths;
        this.startDate = builder.startDate;
        this.maturityDate = builder.maturityDate;
        this.nextPaymentDate = builder.nextPaymentDate;
        this.monthlyPayment = builder.monthlyPayment;
        this.paymentMethod = builder.paymentMethod;
        this.repaymentMethod = builder.repaymentMethod;
        this.status = builder.status;
        this.paymentsMade = builder.paymentsMade;
        this.totalPayments = builder.totalPayments;
        this.totalInterestPaid = builder.totalInterestPaid;
        this.totalPrincipalPaid = builder.totalPrincipalPaid;
        this.overduePayments = builder.overduePayments;
        this.overdueAmount = builder.overdueAmount;
        this.lastOverdueDate = builder.lastOverdueDate;
        this.isInDefault = builder.isInDefault;
        this.defaultDate = builder.defaultDate;
        this.collateralType = builder.collateralType;
        this.collateralValue = builder.collateralValue;
        this.collateralDiscountRate = builder.collateralDiscountRate;
        this.collateralDescription = builder.collateralDescription;
        this.borrowerCreditScore = builder.borrowerCreditScore;
        this.borrowerCreditGrade = builder.borrowerCreditGrade;
        this.applicationDate = builder.applicationDate;
        this.approvalDate = builder.approvalDate;
        this.disbursementDate = builder.disbursementDate;
        this.approvedBy = builder.approvedBy;
        this.notes = builder.notes;
        this.autoPayEnabled = builder.autoPayEnabled;
        this.penaltyWaived = builder.penaltyWaived;
        this.penaltyRate = builder.penaltyRate;
        this.isRefinanced = builder.isRefinanced;
        this.originalLoanId = builder.originalLoanId;
    }
    
    // Getters
    
    public String getLoanId() {
        return loanId;
    }
    
    public UUID getBorrowerId() {
        return borrowerId;
    }
    
    public UUID getLenderId() {
        return lenderId;
    }
    
    public LoanType getLoanType() {
        return loanType;
    }
    
    public String getLoanPurpose() {
        return loanPurpose;
    }
    
    public double getPrincipalAmount() {
        return principalAmount;
    }
    
    public double getCurrentBalance() {
        return currentBalance;
    }
    
    public double getInterestRate() {
        return interestRate;
    }
    
    public double getOriginalInterestRate() {
        return originalInterestRate;
    }
    
    public int getTermMonths() {
        return termMonths;
    }
    
    public LocalDateTime getStartDate() {
        return startDate;
    }
    
    public LocalDateTime getMaturityDate() {
        return maturityDate;
    }
    
    public LocalDateTime getNextPaymentDate() {
        return nextPaymentDate;
    }
    
    public double getMonthlyPayment() {
        return monthlyPayment;
    }
    
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }
    
    public RepaymentMethod getRepaymentMethod() {
        return repaymentMethod;
    }
    
    public LoanStatus getStatus() {
        return status;
    }
    
    public int getPaymentsMade() {
        return paymentsMade;
    }
    
    public int getTotalPayments() {
        return totalPayments;
    }
    
    public double getTotalInterestPaid() {
        return totalInterestPaid;
    }
    
    public double getTotalPrincipalPaid() {
        return totalPrincipalPaid;
    }
    
    public int getOverduePayments() {
        return overduePayments;
    }
    
    public double getOverdueAmount() {
        return overdueAmount;
    }
    
    public LocalDateTime getLastOverdueDate() {
        return lastOverdueDate;
    }
    
    public boolean isInDefault() {
        return isInDefault;
    }
    
    public LocalDateTime getDefaultDate() {
        return defaultDate;
    }
    
    public String getCollateralType() {
        return collateralType;
    }
    
    public double getCollateralValue() {
        return collateralValue;
    }
    
    public double getCollateralDiscountRate() {
        return collateralDiscountRate;
    }
    
    public String getCollateralDescription() {
        return collateralDescription;
    }
    
    public int getBorrowerCreditScore() {
        return borrowerCreditScore;
    }
    
    public CreditGrade getBorrowerCreditGrade() {
        return borrowerCreditGrade;
    }
    
    public LocalDateTime getApplicationDate() {
        return applicationDate;
    }
    
    public LocalDateTime getApprovalDate() {
        return approvalDate;
    }
    
    public LocalDateTime getDisbursementDate() {
        return disbursementDate;
    }
    
    public String getApprovedBy() {
        return approvedBy;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public boolean isAutoPayEnabled() {
        return autoPayEnabled;
    }
    
    public boolean isPenaltyWaived() {
        return penaltyWaived;
    }
    
    public double getPenaltyRate() {
        return penaltyRate;
    }
    
    public boolean isRefinanced() {
        return isRefinanced;
    }
    
    public String getOriginalLoanId() {
        return originalLoanId;
    }
    
    // Business logic methods
    
    /**
     * Calculate remaining payments
     * @return number of remaining payments
     */
    public int getRemainingPayments() {
        return Math.max(0, totalPayments - paymentsMade);
    }
    
    /**
     * Calculate current penalty interest amount
     * @return penalty interest amount
     */
    public double getCurrentPenaltyInterest() {
        if (overdueAmount <= 0 || penaltyRate <= 0) {
            return 0.0;
        }
        
        // Calculate penalty interest based on overdue amount and time
        long daysOverdue = java.time.Duration.between(lastOverdueDate, LocalDateTime.now()).toDays();
        return overdueAmount * penaltyRate * (daysOverdue / 365.0);
    }
    
    /**
     * Check if loan is current (no overdue payments)
     * @return true if current
     */
    public boolean isCurrent() {
        return overduePayments == 0 && !isInDefault;
    }
    
    /**
     * Check if loan is overdue
     * @return true if overdue
     */
    public boolean isOverdue() {
        return overduePayments > 0 || overdueAmount > 0;
    }
    
    /**
     * Check if loan can be refinanced
     * @return true if can be refinanced
     */
    public boolean canRefinance() {
        return !isInDefault && paymentsMade >= 6; // At least 6 payments made
    }
    
    /**
     * Get loan-to-value ratio for secured loans
     * @return LTV ratio or 0 for unsecured loans
     */
    public double getLoanToValueRatio() {
        if (collateralValue <= 0) {
            return 0.0;
        }
        return (currentBalance / collateralValue) * 100;
    }
    
    // Builder class
    
    public static class Builder {
        // Required fields
        private String loanId;
        private UUID borrowerId;
        private LoanType loanType;
        private double principalAmount;
        private double interestRate;
        private int termMonths;
        
        // Optional fields with defaults
        private UUID lenderId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // System
        private String loanPurpose = "";
        private double currentBalance = 0;
        private double originalInterestRate = 0;
        private LocalDateTime startDate = LocalDateTime.now();
        private LocalDateTime maturityDate;
        private LocalDateTime nextPaymentDate;
        private double monthlyPayment = 0;
        private PaymentMethod paymentMethod = PaymentMethod.AUTOMATIC;
        private RepaymentMethod repaymentMethod = RepaymentMethod.EQUAL_INSTALLMENT;
        private LoanStatus status = LoanStatus.PENDING;
        private int paymentsMade = 0;
        private int totalPayments = 0;
        private double totalInterestPaid = 0;
        private double totalPrincipalPaid = 0;
        private int overduePayments = 0;
        private double overdueAmount = 0;
        private LocalDateTime lastOverdueDate;
        private boolean isInDefault = false;
        private LocalDateTime defaultDate;
        private String collateralType = "";
        private double collateralValue = 0;
        private double collateralDiscountRate = 0;
        private String collateralDescription = "";
        private int borrowerCreditScore = 0;
        private CreditGrade borrowerCreditGrade = CreditGrade.F;
        private LocalDateTime applicationDate = LocalDateTime.now();
        private LocalDateTime approvalDate;
        private LocalDateTime disbursementDate;
        private String approvedBy = "";
        private String notes = "";
        private boolean autoPayEnabled = true;
        private boolean penaltyWaived = false;
        private double penaltyRate = 0.05; // 5% penalty rate
        private boolean isRefinanced = false;
        private String originalLoanId = "";
        
        public Builder(String loanId, UUID borrowerId, LoanType loanType, 
                      double principalAmount, double interestRate, int termMonths) {
            this.loanId = loanId;
            this.borrowerId = borrowerId;
            this.loanType = loanType;
            this.principalAmount = principalAmount;
            this.interestRate = interestRate;
            this.termMonths = termMonths;
            
            // Calculate derived dates
            this.maturityDate = startDate.plusMonths(termMonths);
            this.nextPaymentDate = startDate.plusMonths(1);
            this.currentBalance = principalAmount;
            this.originalInterestRate = interestRate;
        }
        
        // Builder methods for all fields
        public Builder lenderId(UUID lenderId) { this.lenderId = lenderId; return this; }
        public Builder loanPurpose(String loanPurpose) { this.loanPurpose = loanPurpose; return this; }
        public Builder currentBalance(double currentBalance) { this.currentBalance = currentBalance; return this; }
        public Builder originalInterestRate(double originalInterestRate) { this.originalInterestRate = originalInterestRate; return this; }
        public Builder startDate(LocalDateTime startDate) { this.startDate = startDate; return this; }
        public Builder maturityDate(LocalDateTime maturityDate) { this.maturityDate = maturityDate; return this; }
        public Builder nextPaymentDate(LocalDateTime nextPaymentDate) { this.nextPaymentDate = nextPaymentDate; return this; }
        public Builder monthlyPayment(double monthlyPayment) { this.monthlyPayment = monthlyPayment; return this; }
        public Builder paymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; return this; }
        public Builder repaymentMethod(RepaymentMethod repaymentMethod) { this.repaymentMethod = repaymentMethod; return this; }
        public Builder status(LoanStatus status) { this.status = status; return this; }
        public Builder paymentsMade(int paymentsMade) { this.paymentsMade = paymentsMade; return this; }
        public Builder totalPayments(int totalPayments) { this.totalPayments = totalPayments; return this; }
        public Builder totalInterestPaid(double totalInterestPaid) { this.totalInterestPaid = totalInterestPaid; return this; }
        public Builder totalPrincipalPaid(double totalPrincipalPaid) { this.totalPrincipalPaid = totalPrincipalPaid; return this; }
        public Builder overduePayments(int overduePayments) { this.overduePayments = overduePayments; return this; }
        public Builder overdueAmount(double overdueAmount) { this.overdueAmount = overdueAmount; return this; }
        public Builder lastOverdueDate(LocalDateTime lastOverdueDate) { this.lastOverdueDate = lastOverdueDate; return this; }
        public Builder isInDefault(boolean isInDefault) { this.isInDefault = isInDefault; return this; }
        public Builder defaultDate(LocalDateTime defaultDate) { this.defaultDate = defaultDate; return this; }
        public Builder collateralType(String collateralType) { this.collateralType = collateralType; return this; }
        public Builder collateralValue(double collateralValue) { this.collateralValue = collateralValue; return this; }
        public Builder collateralDiscountRate(double collateralDiscountRate) { this.collateralDiscountRate = collateralDiscountRate; return this; }
        public Builder collateralDescription(String collateralDescription) { this.collateralDescription = collateralDescription; return this; }
        public Builder borrowerCreditScore(int borrowerCreditScore) { this.borrowerCreditScore = borrowerCreditScore; return this; }
        public Builder borrowerCreditGrade(CreditGrade borrowerCreditGrade) { this.borrowerCreditGrade = borrowerCreditGrade; return this; }
        public Builder applicationDate(LocalDateTime applicationDate) { this.applicationDate = applicationDate; return this; }
        public Builder approvalDate(LocalDateTime approvalDate) { this.approvalDate = approvalDate; return this; }
        public Builder disbursementDate(LocalDateTime disbursementDate) { this.disbursementDate = disbursementDate; return this; }
        public Builder approvedBy(String approvedBy) { this.approvedBy = approvedBy; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }
        public Builder autoPayEnabled(boolean autoPayEnabled) { this.autoPayEnabled = autoPayEnabled; return this; }
        public Builder penaltyWaived(boolean penaltyWaived) { this.penaltyWaived = penaltyWaived; return this; }
        public Builder penaltyRate(double penaltyRate) { this.penaltyRate = penaltyRate; return this; }
        public Builder isRefinanced(boolean isRefinanced) { this.isRefinanced = isRefinanced; return this; }
        public Builder originalLoanId(String originalLoanId) { this.originalLoanId = originalLoanId; return this; }
        
        public Loan build() {
            return new Loan(this);
        }
    }
    
    // Enum classes for loan properties
    
    public enum LoanStatus {
        PENDING("待审核", "§e待审核", "Loan application is pending review"),
        APPROVED("已批准", "§a已批准", "Loan has been approved"),
        REJECTED("已拒绝", "§c已拒绝", "Loan application was rejected"),
        ACTIVE("正常", "§2正常", "Loan is active and current"),
        OVERDUE("逾期", "§c逾期", "Loan has overdue payments"),
        DEFAULT("违约", "§4违约", "Loan is in default"),
        PAID_OFF("已还清", "§b已还清", "Loan has been paid off"),
        FORECLOSED("止赎", "§8止赎", "Loan has been foreclosed"),
        REFINANCED("已再融资", "§9已再融资", "Loan has been refinanced");
        
        private final String chineseName;
        private final String displayName;
        private final String description;
        
        LoanStatus(String chineseName, String displayName, String description) {
            this.chineseName = chineseName;
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getChineseName() { return chineseName; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        
        public boolean isActive() {
            return this == ACTIVE || this == OVERDUE;
        }
        
        public boolean isFinal() {
            return this == PAID_OFF || this == FORECLOSED || this == DEFAULT;
        }
        
        public boolean isNegative() {
            return this == OVERDUE || this == DEFAULT || this == FORECLOSED || this == REJECTED;
        }
    }
    
    public enum PaymentMethod {
        AUTOMATIC("自动扣款", "Automatic deduction from account"),
        MANUAL("手动还款", "Manual payment by borrower"),
        COLLATERAL("抵押物处置", "Payment through collateral liquidation");
        
        private final String chineseName;
        private final String description;
        
        PaymentMethod(String chineseName, String description) {
            this.chineseName = chineseName;
            this.description = description;
        }
        
        public String getChineseName() { return chineseName; }
        public String getDescription() { return description; }
    }
    
    public enum RepaymentMethod {
        EQUAL_INSTALLMENT("等额本息", "Equal monthly payments of principal and interest"),
        EQUAL_PRINCIPAL("等额本金", "Equal principal payments with decreasing interest"),
        BULLET("到期还本", "Interest only payments with principal at maturity"),
        CUSTOM("自定义", "Custom payment schedule");
        
        private final String chineseName;
        private final String description;
        
        RepaymentMethod(String chineseName, String description) {
            this.chineseName = chineseName;
            this.description = description;
        }
        
        public String getChineseName() { return chineseName; }
        public String getDescription() { return description; }
    }
}
