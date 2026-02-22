package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.core.event.LoanEvent;
import com.yae.api.credit.CreditService;
import com.yae.api.credit.CreditGrade;
import com.yae.api.user.UserService;
import com.yae.utils.Logging;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Overdue Processing Service - Handles loan delinquency and collection processes
 * Manages penalty calculations, collection attempts, and default procedures
 */
public class OverdueProcessingService implements Service {
    
    private final YAECore plugin;
    private CreditService creditService;
    private UserService userService;
    private LoanService loanService;
    private ServiceConfig config;
    
    private boolean enabled = false;
    private boolean initialized = false;
    
    // Penalty tracking
    private final Map<String, OverdueRecord> overdueRecords = new ConcurrentHashMap<>();
    private final Map<String, CollectionAttempt> collectionAttempts = new ConcurrentHashMap<>();
    private final Set<String> suspendedAccounts = ConcurrentHashMap.newKeySet();
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    
    // Configuration defaults
    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_PENALTY_RATE = 0.05; // 5% daily penalty
    private static final double DEFAULT_COLLECTION_FEE = 50.0; // Fixed collection fee
    private static final int DEFAULT_GRACE_PERIOD_DAYS = 7;
    private static final int DEFAULT_SUSPENSION_THRESHOLD = 3; // 3 overdue payments
    private static final int DEFAULT_BLACKLIST_THRESHOLD = 6; // 6 overdue payments or default
    private static final int DEFAULT_MAX_COLLECTION_ATTEMPTS = 5;
    private static final long DEFAULT_COLLECTION_INTERVAL_HOURS = 24L;
    
    // Penalty calculation state
    private volatile double penaltyRate;
    private volatile double collectionFee;
    private volatile int gracePeriodDays;
    private volatile int suspensionThreshold;
    private volatile int blacklistThreshold;
    private volatile int maxCollectionAttempts;
    private volatile long collectionIntervalHours;
    
    public OverdueProcessingService(YAECore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public ServiceType getType() {
        return ServiceType.RISK; // Using RISK type for risk management services
    }
    
    @Override
    public String getName() {
        return "Overdue Processing Service";
    }
    
    @Override
    public String getDescription() {
        return "Handles loan delinquency processing, penalties, and collection procedures";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void initialize(ServiceConfig config) {
        this.config = config;
        this.enabled = config.getBoolean("enabled", DEFAULT_ENABLED);
        
        if (!enabled) {
            Logging.info("Overdue processing service is disabled");
            return;
        }
        
        try {
            // Get required services
            this.creditService = plugin.getService(ServiceType.CREDIT);
            this.userService = plugin.getService(ServiceType.USER);
            this.loanService = plugin.getService(ServiceType.LOAN);
            
            if (creditService == null || !creditService.isEnabled()) {
                throw new IllegalStateException("Credit service is required but not available");
            }
            
            if (userService == null || !userService.isEnabled()) {
                throw new IllegalStateException("User service is required but not available");
            }
            
            if (loanService == null || !loanService.isEnabled()) {
                throw new IllegalStateException("Loan service is required but not available");
            }
            
            // Load configuration
            loadConfiguration();
            
            // Create database tables
            createDatabaseTables();
            
            // Load existing overdue records
            loadOverdueRecords();
            
            // Start collection scheduler
            startCollectionScheduler();
            
            this.initialized = true;
            Logging.info("Overdue processing service initialized successfully");
            
            // Fire initialization event
            plugin.fireEvent(new LoanEvent.OverdueProcessingInitializedEvent(this));
            
        } catch (Exception e) {
            Logging.log(Level.SEVERE, "Failed to initialize overdue processing service", e);
            this.enabled = false;
        }
    }
    
    @Override
    public void reload(ServiceConfig newConfig) {
        Logging.info("Reloading overdue processing service...");
        
        boolean wasEnabled = this.enabled;
        ServiceConfig oldConfig = this.config;
        this.config = newConfig;
        this.enabled = newConfig.getBoolean("enabled", DEFAULT_ENABLED);
        
        if (!enabled && wasEnabled) {
            shutdown();
            return;
        }
        
        if (enabled && !wasEnabled) {
            initialize(newConfig);
            return;
        }
        
        if (enabled) {
            // Update configuration
            loadConfiguration();
            
            // Reschedule tasks if interval changed
            if (oldConfig.getLong("collection-interval-hours", DEFAULT_COLLECTION_INTERVAL_HOURS) != 
                newConfig.getLong("collection-interval-hours", DEFAULT_COLLECTION_INTERVAL_HOURS)) {
                stopCollectionScheduler();
                startCollectionScheduler();
            }
        }
        
        Logging.info("Overdue processing service reloaded successfully");
    }
    
    @Override
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        Logging.info("Shutting down overdue processing service...");
        
        try {
            // Save all pending overdue records
            saveAllOverdueRecords();
            
            // Stop collection scheduler
            stopCollectionScheduler();
            
            // Clear in-memory data
            overdueRecords.clear();
            collectionAttempts.clear();
            suspendedAccounts.clear();
            blacklist.clear();
            
            this.initialized = false;
            Logging.info("Overdue processing service shut down successfully");
            
        } catch (Exception e) {
            Logging.log(Level.SEVERE, "Error during overdue processing service shutdown", e);
        }
    }
    
    /**
     * Process loan payment overdue
     * Called when a loan payment becomes overdue
     */
    public CompletableFuture<Void> processOverdue(Loan loan) {
        return CompletableFuture.runAsync(() -> {
            try {
                Logging.info(String.format("Processing overdue for loan %s, overdue amount: %.2f", 
                    loan.getLoanId(), loan.getOverdueAmount()));
                
                // Check if record already exists
                OverdueRecord existingRecord = overdueRecords.get(loan.getLoanId());
                if (existingRecord != null && !existingRecord.isResolved()) {
                    Logging.debug("Overdue record already exists for loan " + loan.getLoanId());
                    return;
                }
                
                // Create new overdue record
                OverdueRecord record = createOverdueRecord(loan);
                
                if (existingRecord != null) {
                    record = mergeOverdueRecords(existingRecord, record);
                }
                
                overdueRecords.put(loan.getLoanId(), record);
                
                // Apply penalty to credit score
                creditService.applyPenalty(loan.getBorrowerId(), 
                    CreditService.PenaltyType.LATE_PAYMENT, 10).join();
                
                // Check suspension threshold
                if (record.totalOverduePayments >= suspensionThreshold) {
                    suspendAccount(loan.getBorrowerId(), loan.getLoanId(), 
                        "连续" + suspensionThreshold + "次逾期还款");
                }
                
                // Fire overdue event
                plugin.fireEvent(new LoanEvent.LoanOverdueProcessedEvent(loan.getBorrowerId(), loan.getLoanId(), record));
                
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Error processing overdue for loan " + loan.getLoanId(), e);
            }
        });
    }
    
    /**
     * Process loan default
     * Called when a loan enters default status
     */
    public CompletableFuture<Void> processDefault(Loan loan) {
        return CompletableFuture.runAsync(() -> {
            try {
                Logging.warning(String.format("Processing default for loan %s", loan.getLoanId()));
                
                // Apply severe credit penalty
                creditService.applyPenalty(loan.getBorrowerId(), 
                    CreditService.PenaltyType.DEFAULT, 50).join();
                
                // Blacklist the borrower
                blacklistBorrower(loan.getBorrowerId(), loan.getLoanId(), "贷款违约");
                
                // Process collateral if secured loan
                if (loan.getLoanType().isSecured() && loan.getCollateralType() != null) {
                    seizeCollateral(loan);
                }
                
                // Record collection attempt
                recordCollectionAttempt(loan.getLoanId(), CollectionAttempt.Method.DEFAULT_ACTION, 
                    "贷款违约处理", true);
                
                // Fire default event
                plugin.fireEvent(new LoanEvent.LoanDefaultProcessedEvent(loan.getBorrowerId(), loan.getLoanId()));
                
            } catch (Exception e) {
                Logging.log(Level.SEVERE, "Error processing default for loan " + loan.getLoanId(), e);
            }
        });
    }
    
    /**
     * Process regular collection attempt
     * Called on a schedule for overdue loans
     */
    public CompletableFuture<Void> processCollectionAttempt(Loan loan) {
        return CompletableFuture.runAsync(() -> {
            try {
                OverdueRecord record = overdueRecords.get(loan.getLoanId());
                if (record == null) {
                    Logging.debug("No overdue record found for loan " + loan.getLoanId());
                    return;
                }
                
                int attemptNumber = record.collectionAttemptCount + 1;
                
                if (attemptNumber > maxCollectionAttempts) {
                    Logging.warning(String.format("Max collection attempts reached for loan %s, escalating to default", 
                        loan.getLoanId()));
                    escalateToDefault(loan, "催收失败 - 达到最大尝试次数");
                    return;
                }
                
                // Determine collection method based on attempt number and loan status
                CollectionAttempt.Method method = determineCollectionMethod(attemptNumber, record);
                
                // Execute collection method
                boolean success = executeCollectionMethod(loan, method, attemptNumber);
                
                record.collectionAttemptCount = attemptNumber;
                record.lastCollectionAttempt = LocalDateTime.now();
                
                // Record the attempt
                recordCollectionAttempt(loan.getLoanId(), method, 
                    "催收尝试 " + attemptNumber + "/" + maxCollectionAttempts, success);
                
                if (success) {
                    record.status = OverdueRecord.Status.RESOLVED;
                    Logging.info(String.format("Collection attempt %d successful for loan %s", 
                        attemptNumber, loan.getLoanId()));
                } else {
                    Logging.debug(String.format("Collection attempt %d failed for loan %s", 
                        attemptNumber, loan.getLoanId()));
                }
                
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Error processing collection attempt for loan " + loan.getLoanId(), e);
            }
        });
    }
    
    /**
     * Calculate penalty for overdue amount
     */
    public double calculatePenalty(OverdueRecord record) {
        if (record.isResolved()) {
            return 0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long daysOverdue = java.time.Duration.between(record.firstOverdueDate, now).toDays();
        
        if (daysOverdue <= gracePeriodDays) {
            return 0; // Still in grace period
        }
        
        double penaltyAmount = record.overdueAmount * penaltyRate * (daysOverdue - gracePeriodDays);
        return Math.max(penaltyAmount, collectionFee);
    }
    
    /**
     * Check if borrower is blacklisted
     */
    public boolean isBlacklisted(UUID borrowerId) {
        return blacklist.contains(borrowerId.toString());
    }
    
    /**
     * Check if borrower account is suspended
     */
    public boolean isAccountSuspended(UUID borrowerId) {
        return suspendedAccounts.contains(borrowerId.toString());
    }
    
    /**
     * Get borrower's overdue records
     */
    public List<OverdueRecord> getBorrowerOverdueRecords(UUID borrowerId) {
        List<OverdueRecord> borrowerRecords = new ArrayList<>();
        
        for (OverdueRecord record : overdueRecords.values()) {
            if (record.borrowerId.equals(borrowerId) && !record.isResolved()) {
                borrowerRecords.add(record);
            }
        }
        
        return borrowerRecords;
    }
    
    /**
     * Calculate total overdue amount for borrower
     */
    public double getTotalOverdueAmount(UUID borrowerId) {
        List<OverdueRecord> records = getBorrowerOverdueRecords(borrowerId);
        double total = 0;
        
        for (OverdueRecord record : records) {
            total += record.overdueAmount + calculatePenalty(record);
        }
        
        return total;
    }
    
    // Private helper methods
    
    private void loadConfiguration() {
        this.penaltyRate = config.getDouble("penalty-rate", DEFAULT_PENALTY_RATE);
        this.collectionFee = config.getDouble("collection-fee", DEFAULT_COLLECTION_FEE);
        this.gracePeriodDays = config.getInt("grace-period-days", DEFAULT_GRACE_PERIOD_DAYS);
        this.suspensionThreshold = config.getInt("suspension-threshold", DEFAULT_SUSPENSION_THRESHOLD);
        this.blacklistThreshold = config.getInt("blacklist-threshold", DEFAULT_BLACKLIST_THRESHOLD);
        this.maxCollectionAttempts = config.getInt("max-collection-attempts", DEFAULT_MAX_COLLECTION_ATTEMPTS);
        this.collectionIntervalHours = config.getLong("collection-interval-hours", DEFAULT_COLLECTION_INTERVAL_HOURS);
        
        Logging.debug("Overdue processing configuration loaded: " +
            "penaltyRate=" + penaltyRate + ", " +
            "collectionFee=" + collectionFee + ", " +
            "gracePeriodDays=" + gracePeriodDays + ", " +
            "suspensionThreshold=" + suspensionThreshold + ", " +
            "blacklistThreshold=" + blacklistThreshold);
    }
    
    private OverdueRecord createOverdueRecord(Loan loan) {
        return new OverdueRecord(
            loan.getLoanId(),
            loan.getBorrowerId(),
            loan.getPrincipalAmount(),
            loan.getCurrentBalance(),
            loan.getOverdueAmount(),
            loan.getOverduePayments(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            OverdueRecord.Status.ACTIVE,
            0,
            LocalDateTime.now(),
            0
        );
    }
    
    private OverdueRecord mergeOverdueRecords(OverdueRecord existing, OverdueRecord newRecord) {
        existing.totalOverduePayments += newRecord.totalOverduePayments;
        existing.overdueAmount += newRecord.overdueAmount;
        existing.lastOverdueDate = newRecord.lastOverdueDate;
        existing.collectionAttemptCount = 0; // Reset attempts
        return existing;
    }
    
    private void suspendAccount(UUID borrowerId, String loanId, String reason) {
        suspendedAccounts.add(borrowerId.toString());
        Logging.warning(String.format("Account suspended for borrower %s (loan %s): %s", 
            borrowerId, loanId, reason));
        
        // Apply additional credit penalty
        creditService.applyPenalty(borrowerId, CreditService.PenaltyType.ACCOUNT_SUSPENSION, 25).join();
        
        // Fire account suspension event
        plugin.fireEvent(new LoanEvent.AccountSuspendedEvent(borrowerId, loanId, reason));
    }
    
    private void blacklistBorrower(UUID borrowerId, String loanId, String reason) {
        blacklist.add(borrowerId.toString());
        Logging.error(String.format("Borrower blacklisted: %s (loan %s): %s", 
            borrowerId, loanId, reason));
        
        // Apply severe credit penalty
        creditService.applyPenalty(borrowerId, CreditService.PenaltyType.FRAUD, 100).join();
        
        // Set credit score to minimum
        creditService.updateCreditScore(borrowerId, CreditGrade.F.getMinScore());
        
        // Fire blacklist event
        plugin.fireEvent(new LoanEvent.BorrowerBlacklistedEvent(borrowerId, loanId, reason));
    }
    
    private void seizeCollateral(Loan loan) {
        Logging.warning(String.format("Seizing collateral for loan %s: %s worth %.2f", 
            loan.getLoanId(), loan.getCollateralType(), loan.getCollateralValue()));
        
        // In a real implementation, this would interact with an asset management system
        // For now, just record the seizure and reduce loan balance
        double seizedValue = loan.getCollateralValue() * 0.8; // 80% of collateral value
        
        // Update loan with seized collateral value
        Loan reducedLoan = Loan.builder(loan)
            .currentBalance(Math.max(0, loan.getCurrentBalance() - seizedValue))
            .notes("抵押物已扣押，价值 " + seizedValue + "")
            .build();
        
        loanService.updateLoan(reducedLoan);
        
        Logging.info(String.format("Collateral seized: %.2f, remaining balance: %.2f", 
            seizedValue, reducedLoan.getCurrentBalance()));
    }
    
    private void escalateToDefault(Loan loan, String reason) {
        Logging.error(String.format("Escalating loan %s to default: %s", loan.getLoanId(), reason));
        
        // Update loan status to default
        Loan defaultedLoan = Loan.builder(loan)
            .status(LoanStatus.DEFAULT)
            .defaultDate(LocalDateTime.now())
            .notes("升级为违约: " + reason)
            .build();
        
        loanService.updateLoan(defaultedLoan);
        
        // Process the default
        processDefault(defaultedLoan);
    }
    
    private CollectionAttempt.Method determineCollectionMethod(int attemptNumber, OverdueRecord record) {
        if (attemptNumber <= 2) {
            return CollectionAttempt.Method.EMAIL;
        } else if (attemptNumber <= 3) {
            return CollectionAttempt.Method.SMS;
        } else if (attemptNumber <= 4) {
            return CollectionAttempt.Method.PHONE_CALL;
        } else {
            return CollectionAttempt.Method.SYSTEM_NOTIFICATION;
        }
    }
    
    private boolean executeCollectionMethod(Loan loan, CollectionAttempt.Method method, int attemptNumber) {
        Logging.debug(String.format("Executing collection method %s for loan %s (attempt %d)", 
            method, loan.getLoanId(), attemptNumber));
        
        // In a real implementation, this would actually execute the collection method
        // For now, we'll assume email and SMS are always successful attempts
        // Phone calls and system notifications have lower success rates
        
        switch (method) {
            case EMAIL:
            case SMS:
                return true; // Assume success for these methods
            case PHONE_CALL:
                return attemptNumber % 2 == 0; // 50% success rate
            case SYSTEM_NOTIFICATION:
                return attemptNumber >= 4; // Higher success rate for final attempts
            case DEFAULT_ACTION:
                return false; // Default actions are never "successful" collection
            default:
                return false;
        }
    }
    
    private void recordCollectionAttempt(String loanId, CollectionAttempt.Method method, 
                                       String notes, boolean success) {
        CollectionAttempt attempt = new CollectionAttempt(
            loanId, method, LocalDateTime.now(), notes, success
        );
        
        collectionAttempts.put(loanId + "_" + System.currentTimeMillis(), attempt);
        
        Logging.info(String.format("Collection attempt recorded: loan=%s, method=%s, success=%b", 
            loanId, method, success));
    }
    
    private void startCollectionScheduler() {
        long intervalTicks = collectionIntervalHours * 60 * 60 * 20L;
        
        plugin.scheduleAsyncRepeatingTask("overdue-collection", () -> {
            try {
                runCollectionCycle();
            } catch (Exception e) {
                Logging.log(Level.SEVERE, "Error during collection cycle", e);
            }
        }, intervalTicks, intervalTicks);
        
        Logging.info(String.format("Collection scheduler started with %d hours interval", collectionIntervalHours));
    }
    
    private void stopCollectionScheduler() {
        plugin.cancelTask("overdue-collection");
        Logging.info("Collection scheduler stopped");
    }
    
    private void runCollectionCycle() {
        Logging.debug("Running overdue collection cycle...");
        
        List<OverdueRecord> activeRecords = new ArrayList<>();
        for (OverdueRecord record : overdueRecords.values()) {
            if (record.isActive() && !record.isResolved()) {
                activeRecords.add(record);
            }
        }
        
        if (activeRecords.isEmpty()) {
            Logging.debug("No active overdue remains from records to process");
            return;
        }
        
        Logging.info(String.format("Processing %d overdue records for collection attempts", activeRecords.size()));
        
        for (OverdueRecord record : activeRecords) {
            Loan loan = loanService.getLoan(record.loanId);
            if (loan != null && loan.getStatus() == LoanStatus.OVERDUE) {
                processCollectionAttempt(loan);
            }
        }
        
        Logging.debug("Collection cycle completed");
    }
    
    private void loadOverdueRecords() {
        // Load existing the database in a real implementation
        Logging.debug("Loading overdue records from database...");
        // For now, start with empty collections
    }
    
    private void saveAllOverdueRecords() {
        // Save all overdue records to database in a real implementation
        Logging.debug("Saving " + overdueRecords.size() + " overdue records to database");
    }
    
    // Inner classes
    
    public static class OverdueRecord {
        private final String loanId;
        private final UUID borrowerId;
        private final double originalBalance;
        private double currentBalance;
        private double overdueAmount;
        private int totalOverduePayments;
        private final LocalDateTime firstOverdueDate;
        private LocalDateTime lastOverdueDate;
        private volatile Status status;
        private volatile int collectionAttemptCount;
        private volatile LocalDateTime lastCollectionAttempt;
        private volatile double penaltyAmount = 0.0;
        
        public OverdueRecord(String loanId, UUID borrowerId, double originalBalance, double currentBalance, 
                           double overdueAmount, int totalOverduePayments, LocalDateTime firstOverdueDate, 
                           LocalDateTime lastOverdueDate, Status status, int collectionAttemptCount, 
                           LocalDateTime lastCollectionAttempt, double penaltyAmount) {
            this.loanId = loanId;
            this.borrowerId = borrowerId;
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
        }
        
        public String getLoanId() { return loanId; }
        public UUID getBorrowerId() { return borrowerId; }
        public double getOverdueAmount() { return overdueAmount; }
        public int getTotalOverduePayments() { return totalOverduePayments; }
        public LocalDateTime getFirstOverdueDate() { return firstOverdueDate; }
        public LocalDateTime getLastOverdueDate() { return lastOverdueDate; }
        public Status getStatus() { return status; }
        public int getCollectionAttemptCount() { return collectionAttemptCount; }
        public LocalDateTime getLastCollectionAttempt() { return lastCollectionAttempt; }
        public double getPenaltyAmount() { return penaltyAmount; }
        
        public boolean isActive() { return status == Status.ACTIVE; }
        public boolean isResolved() { return status == Status.RESOLVED || status == Status.WRITTEN_OFF; }
        
        public void setStatus(Status status) { this.status = status; }
        public void setPenaltyAmount(double penaltyAmount) { this.penaltyAmount = penaltyAmount; }
        public void setCurrentBalance(double currentBalance) { this.currentBalance = currentBalance; }
        public void setOverdueAmount(double overdueAmount) { this.overdueAmount = overdueAmount; }
        
        public enum Status {
            ACTIVE("活跃"),
            RESOLVED("已解决"),
            WRITTEN_OFF("已核销"),
            ESCALATED("已升级");
            
            private final String chineseName;
            Status(String chineseName) { this.chineseName = chineseName; }
            public String getChineseName() { return chineseName; }
        }
    }
    
    public static class CollectionAttempt {
        private final String loanId;
        private final Method method;
        private final LocalDateTime attemptTime;
        private final String notes;
        private final boolean success;
        
        public CollectionAttempt(String loanId, Method method, LocalDateTime attemptTime, String notes, boolean success) {
            this.loanId = loanId;
            this.method = method;
            this.attemptTime = attemptTime;
            this.notes = notes;
            this.success = success;
        }
        
        public String getLoanId() { return loanId; }
        public Method getMethod() { return method; }
        public LocalDateTime getAttemptTime() { return attemptTime; }
        public String getNotes() { return notes; }
        public boolean isSuccess() { return success; }
        
        public enum Method {
            EMAIL("邮件"),
            SMS("短信"),
            PHONE_CALL("电话"),
            SYSTEM_NOTIFICATION("系统通知"),
            DEFAULT_ACTION("默认动作");
            
            private final String chineseName;
            Method(String chineseName) { this.chineseName = chineseName; }
            public String getChineseName() { return chineseName; }
        }
    }
    
    @Override
    public String getStatus() {
        return String.format("Enabled: %s, Active overdue records: %d, Suspended accounts: %d, Blacklisted: %d",
            enabled, overdueRecords.size(), suspendedAccounts.size(), blacklist.size());
    }
    
    @Override
    public ServiceConfig getConfiguration() {
        return config;
    }
}
