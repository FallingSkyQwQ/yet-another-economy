package com.yae.api.loan;

import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.LoanType;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Loan application entity containing all application data
 * Supports 5-step application process with comprehensive validation
 */
public class LoanApplication {
    
    private String applicationId;
    private final UUID playerId;
    private LoanType loanType;
    private double requestedAmount;
    private int termMonths;
    private String loanPurpose;
    private List<ItemStack> collateralItems;
    private double collateralValue;
    private double collateralDiscountRate;
    
    // Auto-calculated fields
    private transient int creditScore;
    private transient CreditGrade creditGrade;
    private transient LoanTerms finalTerms;
    private transient CollateralAssessment collateralAssessment;
    
    // Application status
    private Status status;
    private String approvedBy;
    private LocalDateTime approvalDate;
    private String approvalReason;
    private AutoApprovalResult autoApproval;
    private final LocalDateTime applicationDate;
    private String rejectionReason;
    
    // Processing metadata
    private String collateralType;
    private double processingFee;
    private double disbursedAmount;
    private LocalDateTime expectedDisbursementDate;
    
    public LoanApplication(UUID playerId) {
        this.playerId = playerId;
        this.applicationDate = LocalDateTime.now();
        this.status = Status.NEW;
        this.collateralItems = new ArrayList<>();
    }
    
    /**
     * Creates copy of an existing application
     */
    public LoanApplication(LoanApplication other) {
        this.applicationId = other.applicationId;
        this.playerId = other.playerId;
        this.loanType = other.loanType;
        this.requestedAmount = other.requestedAmount;
        this.termMonths = other.termMonths;
        this.loanPurpose = other.loanPurpose;
        this.collateralItems = new ArrayList<>(other.collateralItems);
        this.collateralValue = other.collateralValue;
        this.collateralDiscountRate = other.collateralDiscountRate;
        this.creditScore = other.creditScore;
        this.creditGrade = other.creditGrade;
        this.finalTerms = other.finalTerms;
        this.collateralAssessment = other.collateralAssessment;
        this.status = other.status;
        this.approvedBy = other.approvedBy;
        this.approvalDate = other.approvalDate;
        this.approvalReason = other.approvalReason;
        this.autoApproval = other.autoApproval;
        this.applicationDate = other.applicationDate;
        this.rejectionReason = other.rejectionReason;
        this.collateralType = other.collateralType;
        this.processingFee = other.processingFee;
        this.disbursedAmount = other.disbursedAmount;
        this.expectedDisbursementDate = other.expectedDisbursementDate;
    }
    
    /**
     * Basic validation for incomplete applications
     */
    public boolean isValid() {
        return playerId != null && loanType != null && requestedAmount > 0 && 
               termMonths > 0 && loanPurpose != null && !loanPurpose.trim().isEmpty();
    }
    
    /**
     * Progressive validation based on current step
     */
    public boolean isStep1Valid() {
        return playerId != null && loanType != null;
    }
    
    public boolean isStep2Valid() {
        return isStep1Valid() && requestedAmount > 0 && termMonths > 0;
    }
    
    public boolean isStep3Valid() {
        return isStep2Valid() && loanPurpose != null && !loanPurpose.trim().isEmpty();
    }
    
    public boolean isStep4Valid() {
        if (loanType != LoanType.MORTGAGE) {
            return isStep3Valid();
        }
        return isStep3Valid() && hasValidCollateral();
    }
    
    public boolean isStep5Valid() {
        return isStep4Valid() && finalTerms != null;
    }
    
    /**
     * Get total collateral value with discount applied
     */
    public double getEffectiveCollateralValue() {
        if (collateralValue <= 0) return 0;
        return collateralValue * (1 - collateralDiscountRate);
    }
    
    /**
     * Check if collateral is sufficient for requested amount
     */
    public boolean hasSufficientCollateral() {
        if (loanType != LoanType.MORTGAGE) return true;
        return getEffectiveCollateralValue() >= requestedAmount * 0.8;
    }
    
    /**
     * Check if collateral items are valid
     */
    public boolean hasValidCollateral() {
        if (loanType != LoanType.MORTGAGE) return true;
        return collateralItems != null && !collateralItems.isEmpty() && collateralValue > 0;
    }
    
    /**
     * Calculate processing fee
     */
    public double calculateProcessingFee() {
        if (processingFee > 0) return processingFee;
        
        // Default processing fee calculation
        double baseRate = getProcessingFeeRate();
        processingFee = requestedAmount * baseRate;
        return processingFee;
    }
    
    /**
     * Calculate final amount to be disbursed (amount minus processing fee)
     */
    public double calculateDisbursedAmount() {
        return requestedAmount - calculateProcessingFee();
    }
    
    /**
     * Generate summary for preview
     */
    public ApplicationSummary getSummary() {
        return new ApplicationSummary(this);
    }
    
    /**
     * Get collateral type from selected items
     */
    private void determineCollateralType() {
        if (collateralItems != null && !collateralItems.isEmpty()) {
            ItemStack firstItem = collateralItems.get(0);
            collateralType = firstItem.getType().name();
        }
    }
    
    /**
     * Update status with audit trail
     */
    public void updateStatus(Status newStatus, String updatedBy, String reason) {
        this.status = newStatus;
        if (newStatus == Status.APPROVED || newStatus == Status.AUTO_APPROVED) {
            this.approvedBy = updatedBy;
            this.approvalDate = LocalDateTime.now();
            this.approvalReason = reason;
        } else if (newStatus == Status.REJECTED) {
            this.rejectionReason = reason;
        }
    }
    
    /**
     * Check if application requires manual review
     */
    public boolean requiresManualReview() {
        return autoApproval != null && !autoApproval.isApproved() && 
               (status == Status.PENDING_REVIEW || status == Status.NEW);
    }
    
    /**
     * Check if application is in final states
     */
    public boolean isFinalized() {
        return status == Status.APPROVED || status == Status.AUTO_APPROVED ||
               status == Status.REJECTED || status == Status.CANCELLED ||
               status == Status.DISBURSED;
    }
    
    /**
     * Get processing fee rate based on loan type
     */
    private double getProcessingFeeRate() {
        switch (loanType) {
            case CREDIT: return 0.01;
            case MORTGAGE: return 0.02;
            case BUSINESS: return 0.015;
            case EMERGENCY: return 0.005;
            default: return 0.01;
        }
    }
    
    // Setters with validation
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public void setLoanType(LoanType loanType) { this.loanType = loanType; }
    public void setRequestedAmount(double requestedAmount) { this.requestedAmount = requestedAmount; }
    public void setTermMonths(int termMonths) { this.termMonths = termMonths; }
    public void setLoanPurpose(String loanPurpose) { this.loanPurpose = loanPurpose; }
    public void setCollateralItems(List<ItemStack> collateralItems) { 
        this.collateralItems = collateralItems; 
        determineCollateralType();
    }
    public void addCollateralItem(ItemStack item) { 
        this.collateralItems.add(item); 
        determineCollateralType();
    }
    public void setCollateralValue(double collateralValue) { this.collateralValue = collateralValue; }
    public void setCollateralDiscountRate(double collateralDiscountRate) { this.collateralDiscountRate = collateralDiscountRate; }
    
    // Auto-calculated field setters
    public void setCreditScore(int creditScore) { this.creditScore = creditScore; }
    public void setCreditGrade(CreditGrade creditGrade) { this.creditGrade = creditGrade; }
    public void setFinalTerms(LoanTerms finalTerms) { this.finalTerms = finalTerms; }
    public void setCollateralAssessment(CollateralAssessment collateralAssessment) {
        this.collateralAssessment = collateralAssessment;
    }
    
    // Status setters
    public void setStatus(Status status) { this.status = status; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovalDate(LocalDateTime approvalDate) { this.approvalDate = approvalDate; }
    public void setApprovalReason(String approvalReason) { this.approvalReason = approvalReason; }
    public void setAutoApproval(AutoApprovalResult autoApproval) { this.autoApproval = autoApproval; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setCollateralType(String collateralType) { this.collateralType = collateralType; }
    public void setProcessingFee(double processingFee) { this.processingFee = processingFee; }
    public void setDisbursedAmount(double disbursedAmount) { this.disbursedAmount = disbursedAmount; }
    public void setExpectedDisbursementDate(LocalDateTime expectedDisbursementDate) {
        this.expectedDisbursementDate = expectedDisbursementDate;
    }
    
    // Getters
    public String getApplicationId() { return applicationId; }
    public UUID getPlayerId() { return playerId; }
    public LoanType getLoanType() { return loanType; }
    public double getRequestedAmount() { return requestedAmount; }
    public int getTermMonths() { return termMonths; }
    public String getLoanPurpose() { return loanPurpose; }
    public List<ItemStack> getCollateralItems() { return collateralItems; }
    public double getCollateralValue() { return collateralValue; }
    public double getCollateralDiscountRate() { return collateralDiscountRate; }
    public int getCreditScore() { return creditScore; }
    public CreditGrade getCreditGrade() { return creditGrade; }
    public LoanTerms getFinalTerms() { return finalTerms; }
    public CollateralAssessment getCollateralAssessment() { return collateralAssessment; }
    public Status getStatus() { return status; }
    public String getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovalDate() { return approvalDate; }
    public String getApprovalReason() { return approvalReason; }
    public AutoApprovalResult getAutoApproval() { return autoApproval; }
    public LocalDateTime getApplicationDate() { return applicationDate; }
    public String getRejectionReason() { return rejectionReason; }
    public String getCollateralType() { return collateralType; }
    public double getProcessingFee() { return processingFee; }
    public double getDisbursedAmount() { return disbursedAmount; }
    public LocalDateTime getExpectedDisbursementDate() { return expectedDisbursementDate; }
    
    /**
     * Application status enum
     */
    public enum Status {
        NEW,                    // Just created, not submitted
        PENDING_REVIEW,         // Submitted, waiting for review
        AUTO_APPROVED,          // Auto-approved by system
        APPROVED,              // Manually approved by admin
        REJECTED,              // Application rejected
        CANCELLED,             // Application cancelled by user
        DISBURSED              // Loan disbursed and active
    }
    
    /**
     * Application summary for display
     */
    public static class ApplicationSummary {
        private final String applicationId;
        private final LoanType loanType;
        private final double requestAmount;
        private final int termMonths;
        private final double interestRate;
        private final double monthlyPayment;
        private final double processingFee;
        private final String status;
        
        public ApplicationSummary(LoanApplication application) {
            this.applicationId = application.getApplicationId();
            this.loanType = application.getLoanType();
            this.requestAmount = application.getRequestedAmount();
            this.termMonths = application.getTermMonths();
            this.interestRate = application.getFinalTerms() != null ? 
                application.getFinalTerms().getInterestRate() : 0;
            this.monthlyPayment = application.getFinalTerms() != null ? 
                application.getFinalTerms().getMonthlyPayment() : 0;
            this.processingFee = application.getProcessingFee();
            this.status = application.getStatus().name();
        }
        
        public String getFormattedSummary() {
            return String.format("""
                贷款申请概览
                申请ID: %s
                贷款类型: %s
                申请金额: ¥%.2f
                贷款期限: %d个月
                年利率: %.2f%%
                月供: ¥%.2f
                手续费: ¥%.2f
                状态: %s
                """, applicationId, loanType, requestAmount, termMonths, 
                   interestRate, monthlyPayment, processingFee, status);
        }
        
        // Getters
        public String getApplicationId() { return applicationId; }
        public LoanType getLoanType() { return loanType; }
        public double getRequestAmount() { return requestAmount; }
        public int getTermMonths() { return termMonths; }
        public double getInterestRate() { return interestRate; }
        public double getMonthlyPayment() { return monthlyPayment; }
        public double getProcessingFee() { return processingFee; }
        public String getStatus() { return status; }
    }
}
