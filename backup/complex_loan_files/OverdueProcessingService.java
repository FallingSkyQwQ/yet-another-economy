package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.database.DatabaseService;
import com.yae.utils.Logging;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.*;

/**
 * Comprehensive overdue loan processing service
 * Includes penalty calculation, collection attempts, and escalation policies
 */
public class OverdueProcessingService implements Service {
    
    private final YAECore plugin;
    private ServiceConfig config;
    private boolean enabled = false;
    private boolean initialized = false;
    private DatabaseService databaseService;
    private RepaymentService repaymentService;
    
    // Configuration defaults
    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_PENALTY_RATE = 0.05; // 5% per day
    private static final double DEFAULT_COLLECTION_FEE = 50.0;
    private static final int DEFAULT_GRACE_PERIOD_DAYS = 7;
    private static final int DEFAULT_SUSPENSION_THRESHOLD = 3;
    private static final int DEFAULT_BLACKLIST_THRESHOLD = 6;
    private static final int DEFAULT_MAX_COLLECTION_ATTEMPTS = 5;
    
    // Collection tracking
    private final Map<String, CollectionWorkflow> collectionWorkflows = new ConcurrentHashMap<>();
    
    public OverdueProcessingService(YAECore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getName() {
        return "Overdue Processing Service";
    }
    
    @Override
    public @NotNull ServiceType getType() {
        return ServiceType.LOAN;
    }
    
    @Override
    public boolean initialize() {
        this.config = getConfig();
        if (config == null) {
            return false;
        }
        
        this.enabled = config.getBoolean("enabled", DEFAULT_ENABLED);
        
        if (!enabled) {
            Logging.info("Overdue processing service is disabled");
            return true;
        }
        
        // Initialize dependencies
        if (!initializeDependencies()) {
            Logging.error("Failed to initialize overdue processing dependencies");
            return false;
        }
        
        this.initialized = true;
        Logging.info("Overdue processing service initialized successfully");
        
        // Start overdue processing scheduler
        startOverdueProcessor();
        
        return true;
    }
    
    @Override
    public boolean reload() {
        return initialize();
    }
    
    @Override
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        Logging.info("Shutting down overdue processing service...");
        
        // Save active workflows
        saveActiveWorkflows();
        
        // Clear caches
        collectionWorkflows.clear();
        
        this.initialized = false;
        Logging.info("Overdue processing service shut down successfully");
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return serviceType == ServiceType.DATABASE || serviceType == ServiceType.LOAN;
    }
    
    @Override
    public ServiceConfig getConfig() {
        return config;
    }
    
    @Override
    public void setConfig(ServiceConfig config) {
        this.config = config;
    }
    
    /**
     * Process overdue loans - main entry point
     */
    public void processOverdueLoans() {
        try {
            List<OverdueLoan> overdueLoans = getOverdueLoans(LocalDateTime.now());
            
            for (OverdueLoan overdueLoan : overdueLoans) {
                try {
                    processIndividualOverdue(overdueLoan);
                } catch (Exception e) {
                    Logging.error("Failed to process overdue loan " + overdueLoan.getLoanId(), e);
                }
            }
            
        } catch (Exception e) {
            Logging.error("Failed to process overdue loans batch", e);
        }
    }
    
    /**
     * Calculate and apply penalties for overdue loan
     */
    public PenaltyCalculation calculatePenalties(String loanId, double overdueAmount, int overdueDays) {
        try {
            double penaltyRate = config.getDouble("overdue_penalty_rate", DEFAULT_PENALTY_RATE);
            double dailyPenalty = overdueAmount * penaltyRate;
            double totalPenalty = dailyPenalty * overdueDays;
            
            // Apply compounding if configured
            if (config.getBoolean("penalty_compounding", false)) {
                totalPenalty = applyPenaltyCompounding(overdueAmount, penaltyRate, overdueDays);
            }
            
            // Apply caps
            double maxPenalty = overdueAmount * config.getDouble("max_penalty_percentage", 1.0);
            totalPenalty = Math.min(totalPenalty, maxPenalty);
            
            return new PenaltyCalculation(overdueAmount, overdueDays, totalPenalty, penaltyRate);
            
        } catch (Exception e) {
            Logging.error("Failed to calculate penalties for loan " + loanId, e);
            return PenaltyCalculation.error("计算惩罚失败");
        }
    }
    
    /**
     * Initiate collection workflow for overdue loan
     */
    public CollectionWorkflow initiateCollection(String loanId, CollectionInitiationRequest request) {
        try {
            OverdueLoan overdue = getOverdueLoan(loanId);
            if (overdue == null) {
                throw new IllegalArgumentException("Overdue loan not found: " + loanId);
            }
            
            CollectionWorkflow workflow = new CollectionWorkflow(overdue, request);
            
            // Determine collection strategy based on overdue days and amount
            determineCollectionStrategy(workflow);
            
            // Save workflow
            saveCollectionWorkflow(workflow);
            collectionWorkflows.put(workflow.getWorkflowId(), workflow);
            
            return workflow;
            
        } catch (Exception e) {
            Logging.error("Failed to initiate collection for loan " + loanId, e);
            return null;
        }
    }
    
    /**
     * Process collection attempt
     */
    public CollectionResult processCollectionAttempt(CollectionAttempt attempt) {
        try {
            CollectionWorkflow workflow = getCollectionWorkflow(attempt.getWorkflowId());
            if (workflow == null) {
                return CollectionResult.error("未找到催收工作流程");
            }
            
            // Execute collection attempt based on method
            CollectionResult result = executeCollectionAttempt(attempt, workflow);
            
            // Update workflow status
            updateWorkflowAfterAttempt(workflow, attempt, result);
            
            return result;
            
        } catch (Exception e) {
            Logging.error("Failed to process collection attempt " + attempt.getAttemptId(), e);
            return CollectionResult.error("催收尝试失败");
        }
    }
    
    /**
     * Suspend borrower account due to delinquency
     */
    public CompletableFuture<Boolean> suspendBorrower(UUID borrowerId, String reason, Object permissionHolder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!hasSuspensionPermission(permissionHolder)) {
                    throw new SecurityException("无权限执行账户暂停");
                }
                
                // Update borrower suspension status
                updateBorrowerStatus(borrowerId, "SUSPENDED", reason, "System");
                
                // Log suspension event
                logAccountAction(borrowerId, "SUSPENDED", reason, "System");
                
                Logging.info("Borrower account suspended: {} for reason: {}", borrowerId, reason);
                return true;
                
            } catch (Exception e) {
                Logging.error("Failed to suspend borrower " + borrowerId, e);
                return false;
            }
        });
    }
    
    /**
     * Add borrower to blacklist
     */
    public CompletableFuture<Boolean> blacklistBorrower(UUID borrowerId, String reason, boolean isPermanent, Object permissionHolder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!hasBlacklistPermission(permissionHolder)) {
                    throw new SecurityException("无权限执行黑名单操作");
                }
                
                // Add to blacklist table
                addToBlacklist(borrowerId, reason, isPermanent, "System");
                
                // Log blacklist action
                logAccountAction(borrowerId, "BLACKLISTED", reason, "System");
                
                Logging.warn("Borrower added to blacklist: {} for reason: {}", borrowerId, reason);
                return true;
                
            } catch (Exception e) {
                Logging.error("Failed to blacklist borrower " + borrowerId, e);
                return false;
            }
        });
    }
    
    /**
     * Waive penalties for overdue loan
     */
    public PenaltyWaiverResult waivePenalties(String loanId, double waiverAmount, String reason, Object permissionHolder) {
        try {
            if (!hasWaiverPermission(permissionHolder)) {
                throw new SecurityException("无权限执行罚息豁免");
            }
            
            OverdueLoan overdue = getOverdueLoan(loanId);
            if (overdue == null) {
                return PenaltyWaiverResult.error("未找到逾期记录");
            }
            
            // Validate waiver amount
            if (waiverAmount > overdue.getPenaltyAmount()) {
                return PenaltyWaiverResult.error("豁免金额超过实际罚息");
            }
            
            // Apply waiver
            applyPenaltyWaiver(loanId, waiverAmount, reason, "System");
            
            // Log waiver for audit purposes
            logWaiverAction(loanId, waiverAmount, reason, "System");
            
            return PenaltyWaiverResult.success(waiverAmount, overdue.getPenaltyAmount() - waiverAmount);
            
        } catch (Exception e) {
            Logging.error("Failed to waive penalties for loan " + loanId, e);
            return PenaltyWaiverResult.error("豁免操作失败");
        }
    }
    
    /**
     * Get overdue statistics for reporting
     */
    public OverdueStatistics getOverdueStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<OverdueLoan> overdueLoans = getOverdueLoansInPeriod(startDate, endDate);
            
            int totalOverdueCount = overdueLoans.size();
            double totalOverdueAmount = overdueLoans.stream().mapToDouble(OverdueLoan::getOverdueAmount).sum();
            double totalPenaltyAmount = overdueLoans.stream().mapToDouble(OverdueLoan::getPenaltyAmount).sum();
            
            Map<String, Integer> overdueDistributionByDays = getOverdueDistribution(overdueLoans);
            
            int resolvedCount = (int) overdueLoans.stream().filter(loan -> "RESOLVED".equals(loan.getStatus())).count();
            int inCollectionCount = (int) overdueLoans.stream().filter(loan -> "IN_COLLECTION".equals(loan.getStatus())).count();
            int suspendedCount = (int) overdueLoans.stream().filter(loan -> "SUSPENDED".equals(loan.getStatus())).count();
            
            return new OverdueStatistics(
                totalOverdueCount,
                totalOverdueAmount,
                totalPenaltyAmount,
                overdueDistributionByDays,
                resolvedCount,
                inCollectionCount,
                suspendedCount
            );
            
        } catch (Exception e) {
            Logging.error("Failed to generate overdue statistics", e);
            return OverdueStatistics.empty();
        }
    }
    
    // === Internal processing methods ===
    
    private void processIndividualOverdue(OverdueLoan overdue) {
        // Apply penalties
        if (overdue.getOverdueAmount() > 0 && overdue.getDaysOverdue() > config.getInt("grace_period_days", DEFAULT_GRACE_PERIOD_DAYS)) {
            applyPenalties(overdue);
        }
        
        // Determine escalation level
        String escalationLevel = determineEscalationLevel(overdue);
        
        // Take appropriate action based on escalation level
        switch (escalationLevel) {
            case "NOTIFICATION":
                sendNotification(overdue);
                break;
            case "INITIAL_COLLECTION":
                startCollectionProcess(overdue);
                break;
            case "INTENSIVE_COLLECTION":
                intensifyCollection(overdue);
                break;
            case "ACCOUNT_SUSPENSION":
                suspendAccount(overdue);
                break;
            case "BLACKLISTING":
                blacklistBorrower(overdue);
                break;
            case "LEGAL_ACTION":
                prepareLegalAction(overdue);
                break;
        }
        
        // Update overdue record
        updateOverdueRecord(overdue);
    }
    
    private void applyPenalties(OverdueLoan overdue) {
        PenaltyCalculation penalties = calculatePenalties(
            overdue.getLoanId(), 
            overdue.getOverdueAmount(), 
            overdue.getDaysOverdue()
        );
        
        if (penalties.isValid()) {
            // Update overdue record with new penalty amounts
            updateOverduePenalties(overdue.getLoanId(), penalties.getTotalPenalty());
            
            // Log penalty application
            logPenaltyApplication(overdue.getLoanId(), overdue.getDaysOverdue(), penalties.getTotalPenalty());
        }
    }
    
    private double applyPenaltyCompounding(double baseAmount, double penaltyRate, int days) {
        double totalPenalty = 0;
        double currentBase = baseAmount;
        
        for (int day = 1; day <= days; day++) {
            double dailyPenalty = currentBase * penaltyRate;
            totalPenalty += dailyPenalty;
            currentBase += dailyPenalty;
        }
        
        return totalPenalty;
    }
    
    private String determineEscalationLevel(OverdueLoan overdue) {
        int daysOverdue = overdue.getDaysOverdue();
        int overduePaymentCount = overdue.getTotalOverduePayments();
        double overdueAmount = overdue.getOverdueAmount();
        
        if (daysOverdue < config.getInt("grace_period_days", DEFAULT_GRACE_PERIOD_DAYS)) {
            return "NOTIFICATION";
        } else if (daysOverdue < 15 && overduePaymentCount < 2) {
            return "INITIAL_COLLECTION";
        } else if (daysOverdue < 30 && overduePaymentCount < config.getInt("suspension_threshold", DEFAULT_SUSPENSION_THRESHOLD)) {
            return "INTENSIVE_COLLECTION";
        } else if (overduePaymentCount >= config.getInt("suspension_threshold", DEFAULT_SUSPENSION_THRESHOLD)) {
            return "ACCOUNT_SUSPENSION";
        } else if (overduePaymentCount >= config.getInt("blacklist_threshold", DEFAULT_BLACKLIST_THRESHOLD)) {
            return "BLACKLISTING";
        } else if (daysOverdue > 90) {
            return "LEGAL_ACTION";
        }
        
        return "CONTINUOUS_COLLECTION";
    }
    
    private void startCollectionProcess(OverdueLoan overdue) {
        // Check if collection already initiated
        if (hasActiveCollectionWorkflow(overdue.getBorrowerId())) {
            return;
        }
        
        CollectionInitiationRequest request = new CollectionInitiationRequest(
            overdue.getBorrowerId(),
            "AUTOMATED_RISK_EVALUATION",
            "START_WORKFLOW"
        );
        
        initiateCollection(overdue.getLoanId(), request);
    }
    
    private void intensifyCollection(OverdueLoan overdue) {
        // Increase collection frequency and methods
        CollectionWorkflow workflow = getActiveCollectionWorkflow(overdue.getLoanId());
        if (workflow != null) {
            workflow.setCollectionIntensity("HIGH");
            addCollectionAttempt(workflow, "INTENSIFIED_CONTACT", "Multiple contact attempts");
        }
    }
    
    private void suspendAccount(OverdueLoan overdue) {
        String reason = String.format("连续逾期%d期，需暂停服务处理", overdue.getTotalOverduePayments());
        suspendBorrower(overdue.getBorrowerId(), reason, null); // Replace null with actual permission holder
        
        // Update overdue record
        updateBorrowerStatus(overdue.getBorrowerId(), "SUSPENDED", reason, "System");
    }
    
    private void blacklistBorrower(OverdueLoan overdue) {
        String reason = String.format("极度逾期%d期，恶意违约，列入黑名单", overdue.getTotalOverduePayments());
        blacklistBorrower(overdue.getBorrowerId(), reason, false, null); // Replace null with permission holder
        
        // Update borrower status
        updateBorrowerStatus(overdue.getBorrowerId(), "BLACKLISTED", reason, "System");
    }
    
    private void prepareLegalAction(OverdueLoan overdue) {
        // Log for legal department
        logLegalActionPreparation(overdue.getLoanId(), overdue.getOverdueAmount(), overdue.getDaysOverdue());
        
        // Update overdue record
        updateOverdueRecordWithLegalStatus(overdue.getLoanId(), "LEGAL_ACTION_PENDING");
    }
    
    private CollectionResult executeCollectionAttempt(CollectionAttempt attempt, CollectionWorkflow workflow) {
        try {
            OverdueLoan loan = getOverdueLoan(workflow.getLoanId());
            if (loan == null) {
                return CollectionResult.error("未找到逾期贷款");
            }
            
            CollectionStrategy strategy = workflow.getCurrentStrategy();
            if (strategy == null) {
                return CollectionResult.error("无有效催收策略");
            }
            
            // Execute attempt based on method
            switch (attempt.getMethod()) {
                case EMAIL:
                    return attemptEmailCollection(loan, attempt.getNotes());
                case SMS:
                    return attemptSmsCollection(loan, attempt.getNotes());
                case PHONE:
                    return attemptPhoneCollection(loan, attempt.getNotes());
                case IN_APP:
                    return attemptInAppCollection(loan, attempt.getNotes());
                case ACCOUNT_RESTRICTION:
                    return applyAccountRestriction(loan);
                default:
                    return CollectionResult.error("不支持催收方法");
            }
            
        } catch (Exception e) {
            Logging.error("Failed to execute collection attempt " + attempt.getAttemptId(), e);
            return CollectionResult.error("催收执行失败");
        }
    }
    
    private CollectionResult attemptEmailCollection(OverdueLoan loan, String message) {
        // Send email collection notice
        String subject = "贷款逾期催缴通知";
        String content = formatCollectionMessage(loan);
        
        logCollectionAttempt(loan.getLoanId(), CollectionAttempt.Method.EMAIL, message, "SENDING");
        
        return CollectionResult.success("催收邮件已发送");
    }
    
    private CollectionResult attemptSmsCollection(OverdueLoan loan, String message) {
        // Send SMS collection notice
        String smsContent = String.format("【YAE贷款提醒】您有贷款逾期%d天，欠款¥%.2f，请尽快处理，避免信用影响。",
            loan.getDaysOverdue(), loan.getOverdueAmount());
        
        logCollectionAttempt(loan.getLoanId(), CollectionAttempt.Method.SMS, smsContent, "SENDING");
        
        return CollectionResult.success("催收短信已发送");
    }
    
    private CollectionResult attemptPhoneCollection(OverdueLoan loan, String message) {
        // Phone collection attempt (automated or manual)
        logCollectionAttempt(loan.getLoanId(), CollectionAttempt.Method.PHONE, "电话催收尝试", "PLANNED");
        
        return CollectionResult.success("电话催收已安排");
    }
    
    private CollectionResult attemptInAppCollection(OverdueLoan loan, String message) {
        // In-app notification
        String notificationId = sendInAppNotification(loan.getBorrowerId(), message);
        
        logCollectionAttempt(loan.getLoanId(), CollectionAttempt.Method.IN_APP, notificationId, "SENT");
        
        return CollectionResult.success("应用内通知已发送");
    }
    
    private CollectionResult applyAccountRestriction(OverdueLoan loan) {
        // Apply account limitations
        restrictAccountActivities(loan.getBorrowerId());
        
        logCollectionAttempt(loan.getLoanId(), CollectionAttempt.Method.ACCOUNT_RESTRICTION, "账户功能限制", "APPLIED");
        
        return CollectionResult.success("账户限制已启用");
    }
    
    private void determineCollectionStrategy(CollectionWorkflow workflow) {
        OverdueLoan loan = workflow.getSourceLoan();
        
        // Determine intensity, methods, and timeline
        int intensity = determineCollectionIntensity(loan);
        timeline strategy = determineCollectionTimeline(loan);
        CollectionAttempt.Method[] methods = getCollectionMethods(loan);
        
        workflow.setCollectionIntensity(String.valueOf(intensity));
        workflow.setCollectionStrategy(new CollectionStrategy(strategy, methods));
    }
    
    private String formatCollectionMessage(OverdueLoan loan) {
        return String.format("""
            尊敬的%s，
            您的贷款（ID：%s）已逾期%d天，欠款金额为¥%.2f（含罚息¥%.2f）。
            这影响了您的信用评分和后续贷款资格。请在3个工作日内处理，
            否则将采取更进一步的催收措施。如需帮助，请联系客服400-xxx-xxxx。""",
            loan.getBorrowerName(), loan.getLoanId(), loan.getDaysOverdue(), 
            loan.getCurrentBalance(), loan.getPenaltyAmount());
    }
    
    // === Database support methods ===
    
    private List<OverdueLoan> getOverdueLoans(LocalDateTime cutoffTime) {
        List<OverdueLoan> overdueLoans = new ArrayList<>();
        
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                SELECT l.*, o.* 
                FROM yae_loans l 
                JOIN yae_overdue_records o ON l.loan_id = o.loan_id 
                WHERE l.status = 'OVERDUE' 
                AND o.status = 'ACTIVE'
                AND o.last_overdue_date < ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, Timestamp.valueOf(cutoffTime));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        OverdueLoan loan = createOverdueLoanFromResultSet(rs);
                        if (loan != null) {
                            overdueLoans.add(loan);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Logging.error("Failed to get overdue loans", e);
        }
        
        return overdueLoans;
    }
    
    private OverdueLoan getOverdueLoan(String loanId) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                SELECT l.*, o.* 
                FROM yae_loans l 
                JOIN yae_overdue_records o ON l.loan_id = o.loan_id 
                WHERE l.loan_id = ? AND l.status = 'OVERDUE'
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, loanId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return createOverdueLoanFromResultSet(rs);
                    }
                }
            }
        } catch (SQLException e) {
            Logging.error("Failed to get overdue loan " + loanId, e);
        }
        return null;
    }
    
    private boolean updateOverduePenalties(String loanId, double penaltyAmount) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                UPDATE yae_overdue_records 
                SET penalty_amount = penalty_amount + ?, updated_at = CURRENT_TIMESTAMP
                WHERE loan_id = ? AND status = 'ACTIVE'
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, penaltyAmount);
                stmt.setString(2, loanId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            Logging.error("Failed to update overdue penalties for loan " + loanId, e);
            return false;
        }
    }
    
    private boolean addToBlacklist(UUID borrowerId, String reason, boolean isPermanent, String appliedBy) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                INSERT INTO yae_loan_blacklist 
                (player_uuid, blacklist_reason, blacklist_date, blacklisted_by, is_permanent) 
                VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?)
                ON DUPLICATE KEY UPDATE 
                blacklist_reason = ?, blacklist_date = CURRENT_TIMESTAMP, 
                blacklisted_by = ?, is_permanent = ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, borrowerId.toString());
                stmt.setString(2, reason);
                stmt.setString(3, appliedBy);
                stmt.setBoolean(4, isPermanent);
                stmt.setString(5, reason);
                stmt.setString(6, appliedBy);
                stmt.setBoolean(7, isPermanent);
                
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            Logging.error("Failed to add borrower to blacklist: " + borrowerId, e);
            return false;
        }
    }
    
    private boolean applyPenaltyWaiver(String loanId, double waiverAmount, String reason, String approvedBy) {
        try (Connection conn = databaseService.getConnection()) {
            // Insert waiver record
            String sql = """
                INSERT INTO yae_penalty_waivers 
                (loan_id, waiver_amount, reason, approved_by, approved_date) 
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, loanId);
                stmt.setDouble(2, waiverAmount);
                stmt.setString(3, reason);
                stmt.setString(4, approvedBy);
                
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            Logging.error("Failed to apply penalty waiver for loan " + loanId, e);
            return false;
        }
    }
    
    private boolean saveCollectionWorkflow(CollectionWorkflow workflow) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                INSERT INTO yae_collection_workflows 
                (workflow_id, loan_id, borrower_uuid, initiation_date, collection_strategy, status) 
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                ON DUPLICATE KEY UPDATE 
                collection_strategy = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, workflow.getWorkflowId());
                stmt.setString(2, workflow.getLoanId());
                stmt.setString(3, workflow.getBorrowerId().toString());
                stmt.setString(4, workflow.getCollectionStrategy().toString());
                stmt.setString(5, workflow.getWorkflowStatus());
                stmt.setString(6, workflow.getCollectionStrategy().toString());
                stmt.setString(7, workflow.getWorkflowStatus());
                
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            Logging.error("Failed to save collection workflow " + workflow.getWorkflowId(), e);
            return false;
        }
    }
    
    // === Configuration and assistance methods ===
    
    private boolean initializeDependencies() {
        try {
            databaseService = (DatabaseService) plugin.getService(ServiceType.DATABASE);
            repaymentService = (RepaymentService) plugin.getService(ServiceType.LOAN);
            
            return databaseService != null && repaymentService != null;
            
        } catch (Exception e) {
            Logging.error("Failed to initialize overdue processing dependencies", e);
            return false;
        }
    }
    
    private void startOverdueProcessor() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(),
            this::processOverdueLoans,
            1800L, // Start after 30 minutes
            3600L  // Run every hour
        );
    }
    
    private List<OverdueLoan> getOverdueLoansInPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        // Implementation to get overdue loans within specified period
        return Collections.emptyList(); // Simplified
    }
    
    private Map<String, Integer> getOverdueDistribution(List<OverdueLoan> overdueLoans) {
        Map<String, Integer> distribution = new HashMap<>();
        
        for (OverdueLoan loan : overdueLoans) {
            String bucket = getOverdueBucket(loan.getDaysOverdue());
            distribution.merge(bucket, 1, Integer::sum);
        }
        
        return distribution;
    }
    
    private String getOverdueBucket(int daysOverdue) {
        if (daysOverdue < 15) return "1-14 days";
        if (daysOverdue < 30) return "15-29 days";
        if (daysOverdue < 60) return "30-59 days";
        if (daysOverdue < 90) return "60-89 days";
        return "90+ days";
    }
    
    private OverdueLoan createOverdueLoanFromResultSet(ResultSet rs) throws SQLException {
        // Create overdue loan from database result
        return new OverdueLoan(
            // Populate from ResultSet fields
        );
    }
    
    private boolean hasSuspensionPermission(Object permissionHolder) {
        // Check permission - implement based on your permission system
        return true; // Simplified
    }
    
    private boolean hasBlacklistPermission(Object permissionHolder) {
        // Check permission - implement based on your permission system
        return true; // Simplified
    }
    
    private boolean hasWaiverPermission(Object permissionHolder) {
        // Check permission - implement based on your permission system
        return true; // Simplified
    }
    
    private void updateBorrowerStatus(UUID borrowerId, String status, String reason, String updatedBy) {
        // Update borrower status in database
    }
    
    private void logAccountAction(UUID borrowerId, String action, String reason, String performedBy) {
        // Log account action for audit
    }
    
    private void logPenaltyApplication(String loanId, int daysOverdue, double penaltyAmount) {
        String message = String.format("Applied penalty to loan %s: %d days overdue, penalty ¥%.2f", 
                                     loanId, daysOverdue, penaltyAmount);
        Logging.info(message);
        // Save to audit log
    }
    
    private void logWaiverAction(String loanId, double waiverAmount, String reason, String approvedBy) {
        String message = String.format("Waived penalty of ¥%.2f for loan %s (reason: %s) approved by %s", 
                                     waiverAmount, loanId, reason, approvedBy);
        Logging.info(message);
    }
    
    private void logLegalActionPreparation(String loanId, double overdueAmount, int daysOverdue) {
        String message = String.format("Prepared legal action for loan %s: overdue ¥%.2f for %d days", 
                                     loanId, overdueAmount, daysOverdue);
        Logging.warn(message);
    }
    
    private void saveActiveWorkflows() {
        for (CollectionWorkflow workflow : collectionWorkflows.values()) {
            saveCollectionWorkflow(workflow);
        }
    }
    
    private String getLoanTypeDescription(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return "信用贷款";
            case MORTGAGE: return "抵押贷款";
            case BUSINESS: return "商业贷款";
            case EMERGENCY: return "应急贷款";
            default: return "通用贷款";
        }
    }
    
    private String sendInAppNotification(UUID playerId, String message) {
        // Send in-app notification
        // Return notification ID
        return "INAPP" + System.currentTimeMillis();
    }
    
    private void restrictAccountActivities(UUID playerId) {
        // Restrict user account activities
        // Implementation depends on your system
    }
    
    private int determineCollectionIntensity(OverdueLoan loan) {
        return Math.min(10, loan.getDaysOverdue() / 3);
    }
    
    private timeline determineCollectionTimeline(OverdueLoan loan) {
        return standard; // Implementation would determine specific timeline
    }
    
    private CollectionAttempt.Method[] getCollectionMethods(OverdueLoan loan) {
        return new CollectionAttempt.Method[] {
            CollectionAttempt.Method.EMAIL,
            CollectionAttempt.Method.SMS,
            CollectionAttempt.Method.IN_APP
        };
    }
    
    private boolean hasActiveCollectionWorkflow(UUID borrowerId) {
        // Check if borrower has active collection workflow
        return false; // Simplified
    }
    
    private CollectionWorkflow getActiveCollectionWorkflow(String loanId) {
        // Return active workflow for loan
        return null; // Simplified
    }
    
    private CollectionWorkflow getCollectionWorkflow(String workflowId) {
        return collectionWorkflows.get(workflowId);
    }
    
    private void updateWorkflowAfterAttempt(CollectionWorkflow workflow, CollectionAttempt attempt, CollectionResult result) {
        // Update workflow based on attempt result
        workflow.addAttempt(result);
        if (result.isSuccess()) {
            workflow.incrementSuccessCount();
        }
    }
    
    private void updateOverdueRecord(OverdueLoan overdue) {
        // Update overdue record in database
    }
    
    private void logCollectionAttempt(String loanId, CollectionAttempt.Method method, String content, String status) {
        // Log collection attempt for audit
    }
    
    private void sendNotification(OverdueLoan overdue) {
        // Send email/SMS notification
    }
    
    private void addCollectionAttempt(CollectionWorkflow workflow, String method, String notes) {
        // Add collection attempt to workflow
    }
    
    private YetAnotherEconomy getPlugin() {
        return (YetAnotherEconomy) plugin;
    }
    
    // === Data classes ===
    
    public static class PenaltyCalculation {
        private final double baseAmount;
        private final int daysOverdue;
        private final double totalPenalty;
        private final double penaltyRate;
        private final boolean valid;
        private final String errorMessage;
        
        public PenaltyCalculation(double baseAmount, int daysOverdue, double totalPenalty, double penaltyRate) {
            this.baseAmount = baseAmount;
            this.daysOverdue = daysOverdue;
            this.totalPenalty = totalPenalty;
            this.penaltyRate = penaltyRate;
            this.valid = true;
            this.errorMessage = null;
        }
        
        public PenaltyCalculation(String errorMessage) {
            this.baseAmount = 0;
            this.daysOverdue = 0;
            this.totalPenalty = 0;
            this.penaltyRate = 0;
            this.valid = false;
            this.errorMessage = errorMessage;
        }
        
        public static PenaltyCalculation error(String message) {
            return new PenaltyCalculation(message);
        }
        
        public boolean isValid() { return valid; }
        public double getBaseAmount() { return baseAmount; }
        public int getDaysOverdue() { return daysOverdue; }
        public double getTotalPenalty() { return totalPenalty; }
        public double getPenaltyRate() { return penaltyRate; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class CollectionWorkflow {
        private final String workflowId;
        private final String loanId;
        private final UUID borrowerId;
        private final OverdueLoan sourceLoan;
        private final LocalDateTime initiationDate;
        private final List<CollectionAttempt> attempts = new ArrayList<>();
        private CollectionStrategy collectionStrategy;
        private String collectionIntensity;
        private String workflowStatus;
        private LocalDateTime lastAttemptDate;
        
        public CollectionWorkflow(OverdueLoan sourceLoan, CollectionInitiationRequest request) {
            this.workflowId = generateWorkflowId();
            this.loanId = sourceLoan.getLoanId();
            this.borrowerId = sourceLoan.getBorrowerId();
            this.sourceLoan = sourceLoan;
            this.initiationDate = LocalDateTime.now();
            this.workflowStatus = "ACTIVE";
        }
        
        private String generateWorkflowId() {
            return "COL" + System.currentTimeMillis();
        }
        
        public void addAttempt(CollectionResult result) {
            // Add result to workflow
            this.lastAttemptDate = LocalDateTime.now();
        }
        
        public void incrementSuccessCount() {
            // Increment success counter
        }
        
        public void setCollectionIntensity(String intensity) {
            this.collectionIntensity = intensity;
        }
        
        public void setCollectionStrategy(CollectionStrategy strategy) {
            this.collectionStrategy = strategy;
        }
        
        // Getters
        public String getWorkflowId() { return workflowId; }
        public String getLoanId() { return loanId; }
        public UUID getBorrowerId() { return borrowerId; }
        public OverdueLoan getSourceLoan() { return sourceLoan; }
        public LocalDateTime getInitiationDate() { return initiationDate; }
        public List<CollectionAttempt> getAttempts() { return new ArrayList<>(attempts); }
        public CollectionStrategy getCurrentStrategy() { return collectionStrategy; }
        public String getWorkflowIntensity() { return collectionIntensity; }
        public String getWorkflowStatus() { return workflowStatus; }
    }
    
    public static class CollectionStrategy {
        private final timeline timeline;
        private final CollectionAttempt.Method[] methods;
        
        public CollectionStrategy(timeline timeline, CollectionAttempt.Method[] methods) {
            this.timeline = timeline;
            this.methods = methods;
        }
        
        @Override
        public String toString() {
            return "CollectionStrategy{" +
                   "timeline=" + timeline + 
                   ", methods=" + Arrays.toString(methods) +
                   '}';
        }
    }
    
    public static class CollectionInitiationRequest {
        private final UUID initiatorId;
        private final String reason;
        private final String action;
        
        public CollectionInitiationRequest(UUID initiatorId, String reason, String action) {
            this.initiatorId = initiatorId;
            this.reason = reason;
            this.action = action;
        }
        
        // Getters
        public UUID getInitiatorId() { return initiatorId; }
        public String getReason() { return reason; }
        public String getAction() { return action; }
    }
    
    public static class CollectionResult {
        private final boolean success;
        private final String message;
        private final LocalDateTime timestamp;
        
        public CollectionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }
        
        public static CollectionResult success(String message) {
            return new CollectionResult(true, message);
        }
        
        public static CollectionResult error(String message) {
            return new CollectionResult(false, message);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class OverdueStatistics {
        private final int totalOverdueCount;
        private final double totalOverdueAmount;
        private final double totalPenaltyAmount;
        private final Map<String, Integer> overdueDistributionByDays;
        private final int resolvedCount;
        private final int inCollectionCount;
        private final int suspendedCount;
        
        public OverdueStatistics(int totalOverdueCount, double totalOverdueAmount, double totalPenaltyAmount,
                               Map<String, Integer> overdueDistributionByDays, 
                               int resolvedCount, int inCollectionCount, int suspendedCount) {
            this.totalOverdueCount = totalOverdueCount;
            this.totalOverdueAmount = totalOverdueAmount;
            this.totalPenaltyAmount = totalPenaltyAmount;
            this.overdueDistributionByDays = new HashMap<>(overdueDistributionByDays);
            this.resolvedCount = resolvedCount;
            this.inCollectionCount = inCollectionCount;
            this.suspendedCount = suspendedCount;
        }
        
        public static OverdueStatistics empty() {
            return new OverdueStatistics(0, 0, 0, new HashMap<>(), 0, 0, 0);
        }
        
        // Getters
        public int getTotalOverdueCount() { return totalOverdueCount; }
        public double getTotalOverdueAmount() { return totalOverdueAmount; }
        public double getTotalPenaltyAmount() { return totalPenaltyAmount; }
        public Map<String, Integer> getOverdueDistributionByDays() { return new HashMap<>(overdueDistributionByDays); }
        public int getResolvedCount() { return resolvedCount; }
        public int getInCollectionCount() { return inCollectionCount; }
        public int getSuspendedCount() { return suspendedCount; }
    }
    
    public static class PenaltyWaiverResult {
        private final double waivedAmount;
        private final double remainingAmount;
        private final boolean success;
        private final String errorMessage;
        
        public PenaltyWaiverResult(double waivedAmount, double remainingAmount) {
            this.waivedAmount = waivedAmount;
            this.remainingAmount = remainingAmount;
            this.success = true;
            this.errorMessage = null;
        }
        
        public PenaltyWaiverResult(String errorMessage) {
            this.waivedAmount = 0;
            this.remainingAmount = 0;
            this.success = false;
            this.errorMessage = errorMessage;
        }
        
        public static PenaltyWaiverResult success(double waivedAmount, double remainingAmount) {
            return new PenaltyWaiverResult(waivedAmount, remainingAmount);
        }
        
        public static PenaltyWaiverResult error(String message) {
            return new PenaltyWaiverResult(message);
        }
        
        public boolean isSuccess() { return success; }
        public double getWaivedAmount() { return waivedAmount; }
        public double getRemainingAmount() { return remainingAmount; }
        public String getErrorMessage() { return errorMessage; }
    }
}
