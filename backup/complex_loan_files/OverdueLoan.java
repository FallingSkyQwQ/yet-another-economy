package com.yae.api.loan;

import com.yae.api.credit.LoanType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Overdue loan record with detailed overdue information
 */
public class OverdueLoan {
    
    // Core loan data
    private final String loanId;
    private final UUID borrowerId;
    private final LoanType loanType;
    private final double originalBalance;
    private double currentBalance;
    private double overdueAmount;
    private int totalOverduePayments;
    private final LocalDateTime firstOverdueDate;
    private LocalDateTime lastOverdueDate;
    private String status;
    
    // Collection information
    private int collectionAttemptCount;
    private LocalDateTime lastCollectionAttempt;
    private double penaltyAmount;
    private double penaltyRate;
    
    // Resolution information
    private LocalDateTime resolvedDate;
    private String resolvedBy;
    private String resolutionNotes;
    
    // Borrower information for collection
    private final String borrowerName;
    private final String borrowerContactEmail;
    private final String borrowerContactPhone;
    
    // Calculated fields
    private int daysOverdue;
    private String escalationLevel;
    
    public OverdueLoan(
            String loanId, UUID borrowerId, LoanType loanType,
            double originalBalance, double currentBalance, double overdueAmount,
            int totalOverduePayments, LocalDateTime firstOverdueDate,
            LocalDateTime lastOverdueDate, String status,
            int collectionAttemptCount, LocalDateTime lastCollectionAttempt,
            double penaltyAmount, double penaltyRate,
            LocalDateTime resolvedDate, String resolvedBy, String resolutionNotes,
            String borrowerName, String borrowerContactEmail, String borrowerContactPhone
    ) {
        this.loanId = loanId;
        this.borrowerId = borrowerId;
        this.loanType = loanType;
        this.originalBalance = originalBalance;
        this.currentBalance = currentBalance;
        this.overdueAmount = overdueAmount;
        this.totalOverduePayments = totalOverduePayments;
        this.firstOverdueDate = firstOverdueDate;
        this.lastOverdueDate = lastOverdueDate;
        this.status = status;
        this.collectionAttemptCount = collectionAttemptCount;
        this.lastCollectionAttempt = lastCollectionAttempt;
        this.penaltyAmount = penaltyAmount;
        this.penaltyRate = penaltyRate;
        this.resolvedDate = resolvedDate;
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = resolutionNotes;
        this.borrowerName = borrowerName;
        this.borrowerContactEmail = borrowerContactEmail;
        this.borrowerContactPhone = borrowerContactPhone;
        
        // Calculate derived fields
        calculateDerivedFields();
    }
    
    /**
     * Calculate derived fields based on current status
     */
    private void calculateDerivedFields() {
        if (firstOverdueDate != null) {
            this.daysOverdue = daysBetween(firstOverdueDate, LocalDateTime.now());
        } else {
            this.daysOverdue = 0;
        }
        
        // Determine escalation level based on days overdue and other factors
        this.escalationLevel = determineEscalation();
    }
    
    /**
     * Calculate days between two LocalDateTimes
     */
    private int daysBetween(LocalDateTime start, LocalDateTime end) {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(start, end);
    }
    
    /**
     * Determine escalation level for collection
     */
    private String determineEscalation() {
        if (daysOverdue < 7) return "GRACE_PERIOD";
        if (daysOverdue < 15) return "NOTIFICATION";
        if (daysOverdue < 30) return "INITIAL_COLLECTION";
        if (daysOverdue < 60) return "INTENSIVE_COLLECTION";
        if (daysOverdue < 90) return "ACCOUNT_SUSPENSION";
        if (daysOverdue < 180) return "BLACKLISTING";
        return "LEGAL_ACTION";
    }
    
    /**
     * Calculate outstanding penalty amount
     */
    public double calculateOutstandingPenalty() {
        if (daysOverdue > 7) { // Grace period of 7 days
            return overdueAmount * penaltyRate * daysOverdue;
        }
        return penaltyAmount;
    }
    
    /**
     * Check if collection is currently active
     */
    public boolean isInActiveCollection() {
        return "ACTIVE".equals(status) && overdueAmount > 0;
    }
    
    /**
     * Check if resolved
     */
    public boolean isResolved() {
        return "RESOLVED".equals(status) && resolvedDate != null;
    }
    
    /**
     * Check if escalation is appropriate
     */
    public boolean shouldEscalate() {
        return daysOverdue > calculateEscalationThreshold();
    }
    
    /**
     * Calculate escalation threshold based on loan type and amount
     */
    private int calculateEscalationThreshold() {
        // Basic escalation thresholds
        int baseThreshold = 14;
        
        // Adjust for loan type
        switch (loanType) {
            case EMERGENCY:
                return baseThreshold - 7; // Faster escalation for emergency loans
            case MORTGAGE:
                return baseThreshold + 30; // Slower escalation for mortgages
            case BUSINESS:
                return baseThreshold + 15; // Medium escalation for business loans
            default:
                return baseThreshold;
        }
    }
    
    /**
     * Check if ready for next collection attempt
     */
    public boolean isReadyForNextAttempt() {
        if (!isInActiveCollection()) return false;
        
        // Minimum interval between attempts (24 hours)
        if (lastCollectionAttempt != null) {
            daysBetweenHours(lastCollectionAttempt, LocalDateTime.now());
            return daysOverdue > defaultCollectionInterval();
        }
        return true;
    }
    
    /**
     * Default collection interval in hours
     */
    private int defaultCollectionInterval() {
        return 24; // 24 hours between attempts
    }
    
    /**
     * Get collection priority (higher = more urgent)
     */
    public int getCollectionPriority() {
        int basePriority = Math.min(100, daysOverdue * 2);
        
        // Boost priority for higher amounts
        double amountMultiplier = Math.min(2.0, overdueAmount / 10000.0);
        basePriority *= amountMultiplier;
        
        // Boost for repeat overdues
        basePriority += collectionAttemptCount * 5;
        
        return Math.min(1000, basePriority);
    }
    
    /**
     * Get collection message based on status
     */
    public String getCollectionMessage() {
        switch (escalationLevel) {
            case "GRACE_PERIOD":
                return String.format("您的贷款（ID：%s）%s已逾期%d，请在逾期前处理。",
                    loanId, loanType.getChineseName(), daysOverdue);
                    
            case "NOTIFICATION":
                return String.format("您有贷款逾期%d天，逾期金额¥%.2f，还请尽快处理以维护信用。",
                    daysOverdue, overdueAmount);
                    
            case "INITIAL_COLLECTION":
                return String.format("贷款（%s）已逾期%d天，逾期金额¥%.2f，系统将进行处理，请及时处理以免造成更大影响。",
                    loanType.getChineseName(), daysOverdue, calculateOutstandingPenalty()); // Including penalties
                    
            case "INTENSIVE_COLLECTION":
                return String.format("贷款（%s）已逾期%d天，逾期金额¥%.2f＋罚息¥%.2f，请立即处理。",
                    loanType.getChineseName(), daysOverdue, overdueAmount, calculateOutstandingPenalty());
                    
            case "ACCOUNT_SUSPENSION":
                return String.format("您当前有严重逾期记录（%d天，¥%.2f），账户启动暂停处理，请及时联系客服处理后续事宜。",
                    daysOverdue, calculateOutstandingPenalty());
                    
            default:
                return String.format("存在严重逾期记录（%d天，¥%.2f+罚息），请联系客服了解后续处理流程。",
                    daysOverdue, overdueAmount);
        }
    }
    
    /**
     * Get recommended collection method based on escalation level
     */
    public CollectionAttempt.Method getRecommendedCollectionMethod() {
        switch (escalationLevel) {
            case "GRACE_PERIOD":
            case "NOTIFICATION":
                return CollectionAttempt.Method.EMAIL;
            case "INITIAL_COLLECTION":
                return CollectionAttempt.Method.SMS;
            case "INTENSIVE_COLLECTION":
                return CollectionAttempt.Method.PHONE;
            case "ACCOUNT_SUSPENSION":
                return CollectionAttempt.Method.IN_APP;
            case "BLACKLISTING":
                return CollectionAttempt.Method.ACCOUNT_RESTRICTION;
            default:
                return CollectionAttempt.Method.EMAIL;
        }
    }
    
    /**
     * Update overdue data
     */
    public void updateOverdueAmount(double amount) {
        this.overdueAmount = amount;
        this.lastOverdueDate = LocalDateTime.now();
        updateEscalation();
    }
    
    /**
     * Add collection attempt
     */
    public void recordCollectionAttempt(String method, String notes) {
        this.collectionAttemptCount++;
        this.lastCollectionAttempt = LocalDateTime.now();
        
        // Update escalation based on attempts
        if (collectionAttemptCount > 5) {
            escalateCollection();
        }
    }
    
    /**
     * Apply penalty
     */
    public void applyPenalty(double penaltyAmount) {
        this.penaltyAmount += penaltyAmount;
        this.currentBalance += penaltyAmount;
        updateEscalation();
    }
    
    /**
     * Update escalation level based on current status
     */
    private void updateEscalation() {
        this.escalationLevel = determineEscalation();
    }
    
    /**
     * Escalate collection to next level
     */
    private void escalateCollection() {
        switch (escalationLevel) {
            case "GRACE_PERIOD":
                escalateTo("NOTIFICATION");
                break;
            case "NOTIFICATION":
                escalateTo("INITIAL_COLLECTION");
                break;
            case "INITIAL_COLLECTION":
                escalateTo("INTENSIVE_COLLECTION");
                break;
            case "INTENSIVE_COLLECTION":
                escalateTo("ACCOUNT_SUSPENSION");
                break;
            case "ACCOUNT_SUSPENSION":
                escalateTo("BLACKLISTING");
                break;
            case "BLACKLISTING":
                escalateTo("LEGAL_ACTION");
                break;
        }
    }
    
    /**
     * Escalate to specific level
     */
    private void escalateTo(String newLevel) {
        this.escalationLevel = newLevel;
        this.lastOverdueDate = LocalDateTime.now();
    }
    
    /**
     * Mark as resolved
     */
    public void markAsResolved(LocalDateTime resolvedDate, String resolvedBy, String notes) {
        this.status = "RESOLVED";
        this.resolvedDate = resolvedDate;
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = notes;
        this.overdueAmount = 0;
    }
    
    /**
     * Calculate hours passed between dates  
     */
    private int daysBetweenHours(LocalDateTime start, LocalDateTime end) {
        return (int) (java.time.temporal.ChronoUnit.HOURS.between(start, end) / 24);
    }
    
    // Getters
    public String getLoanId() { return loanId; }
    public UUID getBorrowerId() { return borrowerId; }
    public LoanType getLoanType() { return loanType; }
    public double getOriginalBalance() { return originalBalance; }
    public double getCurrentBalance() { return currentBalance; }
    public double getOverdueAmount() { return overdueAmount; }
    public int getTotalOverduePayments() { return totalOverduePayments; }
    public LocalDateTime getFirstOverdueDate() { return firstOverdueDate; }
    public LocalDateTime getLastOverdueDate() { return lastOverdueDate; }
    public String getStatus() { return status; }
    public int getCollectionAttemptCount() { return collectionAttemptCount; }
    public LocalDateTime getLastCollectionAttempt() { return lastCollectionAttempt; }
    public double getPenaltyAmount() { return penaltyAmount; }
    public double getPenaltyRate() { return penaltyRate; }
    public LocalDateTime getResolvedDate() { return resolvedDate; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolutionNotes() { return resolutionNotes; }
    public String getBorrowerName() { return borrowerName; }
    public String getBorrowerContactEmail() { return borrowerContactEmail; }
    public String getBorrowerContactPhone() { return borrowerContactPhone; }
    public int getDaysOverdue() { return daysOverdue; }
    public String getEscalationLevel() { return escalationLevel; }
    
    // Basic setters
    public void setCurrentBalance(double currentBalance) { this.currentBalance = currentBalance; }
    public void setOverdueAmount(double overdueAmount) { this.overdueAmount = overdueAmount; }
    public void setStatus(String status) { this.status = status; }
    public void setCollectionAttemptCount(int collectionAttemptCount) { this.collectionAttemptCount = collectionAttemptCount; }
    public void setLastCollectionAttempt(LocalDateTime lastCollectionAttempt) { this.lastCollectionAttempt = lastCollectionAttempt; }
    public void setPenaltyAmount(double penaltyAmount) { this.penaltyAmount = penaltyAmount; }
    public void setPenaltyRate(double penaltyRate) { this.penaltyRate = penaltyRate; }
    public void setResolvedDate(LocalDateTime resolvedDate) { this.resolvedDate = resolvedDate; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    
    /**
     * @return formatted overdue summary
     */
    public String getFormattedSummary() {
        return String.format("""
            逾期记录 - %s
            借款人: %s (%s)
            类型: %s | 逾期天数: %d | 等级: %s
            金额: ¥%.2f | 罚息: ¥%.2f | 试过: %d
            状态: %s | 最终: %s
            """,
            loanId, borrowerName, borrowerContactEmail,
            loanType.getChineseName(), daysOverdue, escalationLevel,
            overdueAmount, penaltyAmount, collectionAttemptCount,
            status, resolvedBy != null ? resolvedBy : "处理中"
        );
    }
    
    public static class OverdueAttempt {
        private final LocalDateTime attemptDate;
        private final String method;
        private final String result;
        private final String notes;
        
        public OverdueAttempt(LocalDateTime attemptDate, String method, String result, String notes) {
            this.attemptDate = attemptDate;
            this.method = method;
            this.result = result;
            this.notes = notes;
        }
        
        public LocalDateTime getAttemptDate() { return attemptDate; }
        public String getMethod() { return method; }
        public String getResult() { return result; }
        public String getNotes() { return notes; }
    }
    
    public static class OverdueNotification {
        private final LocalDateTime notificationDate;
        private final String type;
        private final String recipient;
        private final String content;
        private final boolean delivered;
        
        public OverdueNotification(LocalDateTime notificationDate, String type, 
                                  String recipient, String content, boolean delivered) {
            this.notificationDate = notificationDate;
            this.type = type;
            this.recipient = recipient;
            this.content = content;
            this.delivered = delivered;
        }
        
        public LocalDateTime getNotificationDate() { return notificationDate; }
        public String getType() { return type; }
        public String getRecipient() { return recipient; }
        public String getContent() { return content; }
        public boolean isDelivered() { return delivered; }
    }
}
