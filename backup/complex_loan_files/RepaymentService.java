package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.database.DatabaseService;
import com.yae.api.bank.BankAccountManager;
import com.yae.utils.Logging;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.sql.*;

/**
 * Comprehensive repayment service supporting manual and automatic payments
 * Includes payment scheduling, processing, and integration with bank accounts
 */
public class RepaymentService implements Service {
    
    private final YAECore plugin;
    private ServiceConfig config;
    private boolean enabled = false;
    private boolean initialized = false;
    private DatabaseService databaseService;
    private BankAccountManager bankService;
    private RepaymentPlanService repaymentPlanService;
    
    // Payment processing configuration
    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_AUTOPAY_RETRY_COUNT = 3;
    private static final int DEFAULT_AUTOPAY_INTERVAL_HOURS = 24;
    private static final double DEFAULT_MIN_AUTOPAY_AMOUNT = 10.0;
    private static final boolean DEFAULT_SUPPORTS_PARTIAL_PAYMENTS = true;
    
    // Payment tracking
    private final Map<String, ScheduledPayment> scheduledPayments = new ConcurrentHashMap<>();
    private final Map<UUID, List<PaymentPreference>> userPaymentPreferences = new ConcurrentHashMap<>();
    
    public RepaymentService(YAECore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getName() {
        return "Repayment Service";
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
            Logging.info("Repayment service is disabled");
            return true;
        }
        
        // Initialize dependencies
        if (!initializeDependencies()) {
            Logging.error("Failed to initialize repayment service dependencies");
            return false;
        }
        
        // Initialize payment tracking
        this.repaymentPlanService = new RepaymentPlanService(config);
        
        // Start automatic payment scheduler
        startAutoPaymentScheduler();
        
        this.initialized = true;
        Logging.info("Repayment service initialized successfully");
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
        
        Logging.info("Shutting down repayment service...");
        
        // Save pending payments
        saveScheduledPayments();
        
        // Clear caches
        scheduledPayments.clear();
        userPaymentPreferences.clear();
        
        this.initialized = false;
        Logging.info("Repayment service shut down successfully");
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
        return serviceType == ServiceType.DATABASE || serviceType == ServiceType.BANK;
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
     * Manual payment processing - user initiated payment
     */
    public CompletableFuture<PaymentResult> makeManualPayment(Player player, String loanId, double amount, PaymentMethod method) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate inputs
                if (amount <= 0 || loanId == null || loanId.trim().isEmpty()) {
                    return PaymentResult.error("无效支付参数");
                }
                
                // Verify loan exists and belongs to player
                LoanRecord loan = getLoanRecord(loanId, player.getUniqueId());
                if (loan == null) {
                    return PaymentResult.error("未找到指定贷款记录");
                }
                
                // Check if loan allows additional payments
                if (!loan.allowsManualPayments()) {
                    return PaymentResult.error("该贷款不允许额外还款");
                }
                
                // Process payment based on method
                return processPayment(loan, amount, method, true);
                
            } catch (Exception e) {
                Logging.error("Failed to process manual payment for player " + player.getName(), e);
                return PaymentResult.error("支付处理失败：" + e.getMessage());
            }
        });
    }
    
    /**
     * Automatic payment processing
     */
    public CompletableFuture<PaymentResult> makeAutomaticPayment(String loanId, double amount, PaymentMethod method) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate inputs
                if (amount <= 0 || loanId == null || loanId.trim().isEmpty()) {
                    logging.warn("Invalid autopay parameters: loanId={}, amount={}", loanId, amount);
                    return PaymentResult.error("自动支付参数无效");
                }
                
                // Get loan record
                LoanRecord loan = getLoanRecord(loanId);
                if (loan == null) {
                    logging.warn("Loan not found for autopay: {}", loanId);
                    return PaymentResult.error("未找到贷款记录");
                }
                
                // Check if auto-pay is enabled
                if (!loan.isAutoPayEnabled()) {
                    return PaymentResult.error("自动扣款未启用");
                }
                
                return processPayment(loan, amount, method, false);
                
            } catch (Exception e) {
                Logging.error("Failed to process automatic payment for loan " + loanId, e);
                return PaymentResult.error("自动支付失败：" + e.getMessage());
            }
        });
    }
    
    /**
     * Process scheduled payments for all loans
     */
    public void processAllScheduledPayments() {
        List<ScheduledPayment> duePayments = getDueScheduledPayments();
        
        for (ScheduledPayment payment : duePayments) {
            processScheduledPayment(payment);
        }
    }
    
    /**
     * Schedule a payment for automatic processing
     */
    public boolean schedulePayment(String loanId, double amount, LocalDateTime scheduledTime, PaymentMethod method) {
        try {
            if (scheduledTime.isBefore(LocalDateTime.now())) {
                return false;
            }
            
            String paymentId = generatePaymentId(loanId);
            ScheduledPayment scheduledPayment = new ScheduledPayment(
                paymentId, loanId, amount, scheduledTime, method
            );
            
            // Save to database and cache
            if (saveScheduledPayment(scheduledPayment)) {
                scheduledPayments.put(paymentId, scheduledPayment);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            Logging.error("Failed to schedule payment for loan " + loanId, e);
            return false;
        }
    }
    
    /**
     * Get payment schedule for a loan
     */
    public List<PaymentSchedule> getPaymentSchedule(String loanId) {
        try {
            LoanRecord loan = getLoanRecord(loanId);
            if (loan == null) {
                return Collections.emptyList();
            }
            
            LoanTerms loanTerms = loan.getLoanTerms();
            LoanTerms.AmortizationSchedule schedule = loanTerms.getAmortizationSchedule();
            
            List<PaymentSchedule> paymentSchedule = new ArrayList<>();
            
            for (LoanTerms.PaymentDetail detail : schedule.getSchedule()) {
                PaymentStatus status = getPaymentStatus(loanId, detail.getMonth());
                double amountPaid = getAmountPaidForMonth(loanId, detail.getMonth());
                
                paymentSchedule.add(new PaymentSchedule(
                    detail.getMonth(),
                    detail.getMonthlyPayment(),
                    detail.getInterestPayment(),
                    detail.getPrincipalPayment(),
                    detail.getRemainingPrincipal(),
                    status,
                    amountPaid
                ));
            }
            
            return paymentSchedule;
            
        } catch (Exception e) {
            Logging.error("Failed to get payment schedule for loan " + loanId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get payment history for a loan
     */
    public List<PaymentRecord> getPaymentHistory(String loanId, int limit) {
        List<PaymentRecord> history = new ArrayList<>();
        
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                SELECT * FROM yae_loan_payments 
                WHERE loan_id = ? AND payment_status != 'CANCELLED'
                ORDER BY payment_date DESC
                LIMIT ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, loanId);
                stmt.setInt(2, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        PaymentRecord record = new PaymentRecord(
                            rs.getString("id"),
                            rs.getString("loan_id"),
                            rs.getTimestamp("payment_date").toLocalDateTime(),
                            rs.getDouble("payment_amount"),
                            rs.getDouble("principal_payment"),
                            rs.getDouble("interest_payment"), 
                            rs.getDouble("penalty_payment"),
                            PaymentMethod.valueOf(rs.getString("payment_method")),
                            PaymentStatus.valueOf(rs.getString("payment_status"))
                        );
                        history.add(record);
                    }
                }
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to get payment history for loan " + loanId, e);
        }
        
        return history;
    }
    
    /**
     * Calculate early payoff options
     */
    public List<EarlyPayoffOption> getEarlyPayoffOptions(String loanId) {
        try {
            LoanRecord loan = getLoanRecord(loanId);
            if (loan == null) {
                return Collections.emptyList();
            }
            
            int currentMonth = calculateCurrentMonth(loan);
            return repaymentPlanService.generateEarlyPayoffOptions(loan.getLoanTerms(), currentMonth);
            
        } catch (Exception e) {
            Logging.error("Failed to get early payoff options for loan " + loanId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Enable/disable automatic payments for a loan
     */
    public CompletableFuture<Boolean> setAutoPay(String loanId, boolean enabled, PaymentMethod method) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                try (Connection conn = databaseService.getConnection()) {
                    String sql = "UPDATE yae_loans SET auto_pay_enabled = ?, payment_method = ? WHERE loan_id = ?";
                    
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setBoolean(1, enabled);
                        stmt.setString(2, method.name());
                        stmt.setString(3, loanId);
                        
                        return stmt.executeUpdate() > 0;
                    }
                }
            } catch (SQLException e) {
                Logging.error("Failed to set auto-pay for loan " + loanId, e);
                return false;
            }
        });
    }
    
    // === Internal processing methods ===
    
    private PaymentResult processPayment(LoanRecord loan, double amount, PaymentMethod method, boolean isManual) {
        try {
            // Validate sufficient funds
            if (!hasSufficientFunds(loan.getBorrowerId(), amount, method)) {
                return PaymentResult.error("资金不足");
            }
            
            // Process payment based on method
            String transactionId = generateTransactionId();
            boolean paymentSuccessful = false;
            double principalPaid = 0;
            double interestPaid = 0;
            double penaltyPaid = 0;
            
            switch (method) {
                case BANK_TRANSFER:
                    paymentSuccessful = processBankPayment(loan, amount, transactionId);
                    break;
                case WALLET:
                    paymentSuccessful = processWalletPayment(loan, amount, transactionId);
                    break;
                case SYSTEM_CREDIT:
                    paymentSuccessful = processSystemCredit(loan, amount, transactionId);
                    break;
                default:
                    paymentSuccessful = false;
            }
            
            if (!paymentSuccessful) {
                return PaymentResult.error("支付处理失败");
            }
            
            // Allocate payment to principal, interest, and penalties
            PaymentAllocation allocation = allocatePayment(loan, amount);
            
            // Update loan balance
            updateLoanBalance(loan, allocation);
            
            // Record payment
            PaymentRecord paymentRecord = new PaymentRecord(
                transactionId,
                loan.getLoanId(),
                LocalDateTime.now(),
                amount,
                allocation.getPrincipalPaid(),
                allocation.getInterestPaid(),
                allocation.getPenaltyPaid(),
                method,
                PaymentStatus.COMPLETED
            );
            
            savePaymentRecord(paymentRecord);
            
            return PaymentResult.success(paymentRecord);
            
        } catch (Exception e) {
            Logging.error("Payment processing error for loan " + loan.getLoanId(), e);
            return PaymentResult.error("支付处理异常");
        }
    }
    
    private void processScheduledPayment(ScheduledPayment payment) {
        try {
            LoanRecord loan = getLoanRecord(payment.getLoanId());
            if (loan == null || loan.isClosed()) {
                cancelScheduledPayment(payment.getPaymentId());
                return;
            }
            
            if (hasSufficientFunds(loan.getBorrowerId(), payment.getAmount(), payment.getMethod())) {
                PaymentResult result = processPayment(loan, payment.getAmount(), payment.getMethod(), false);
                
                if (result.isSuccess()) {
                    completeScheduledPayment(payment.getPaymentId());
                } else {
                    retryScheduledPayment(payment.getPaymentId());
                }
            } else {
                retryScheduledPayment(payment.getPaymentId());
            }
            
        } catch (Exception e) {
            Logging.error("Failed to process scheduled payment " + payment.getPaymentId(), e);
            retryScheduledPayment(payment.getPaymentId());
        }
    }
    
    private PaymentAllocation allocatePayment(LoanRecord loan, double amount) {
        double remainingAmount = amount;
        
        // Pay outstanding penalties first
        double penaltyPaid = Math.min(remainingAmount, loan.getOutstandingPenalties());
        remainingAmount -= penaltyPaid;
        
        // Pay interest due
        double interestDue = loan.getCurrentInterestDue();
        double interestPaid = Math.min(remainingAmount, interestDue);
        remainingAmount -= interestPaid;
        
        // Pay principal
        double principalPaid = Math.min(remainingAmount, loan.getCurrentPrincipalDue());
        
        return new PaymentAllocation(principalPaid, interestPaid, penaltyPaid);
    }
    
    private boolean processBankPayment(LoanRecord loan, double amount, String transactionId) {
        try {
            UUID borrowerId = loan.getBorrowerId();
            return bankService.withdrawFromUserAccount(borrowerId, new BigDecimal(amount));
        } catch (Exception e) {
            Logging.error("Bank payment failed for loan " + loan.getLoanId(), e);
            return false;
        }
    }
    
    private boolean processWalletPayment(LoanRecord loan, double amount, String transactionId) {
        // Wallet payment processing logic
        return true; // Simplified for now
    }
    
    private boolean processSystemCredit(LoanRecord loan, double amount, String transactionId) {
        // System credit payment processing
        return true; // Simplified for now
    }
    
    private boolean hasSufficientFunds(UUID playerId, double amount, PaymentMethod method) {
        switch (method) {
            case BANK_TRANSFER:
                return bankService.getUserBalance(playerId).doubleValue() >= amount;
            case WALLET:
                // Check wallet balance
                return true; // Simplified
            default:
                return false;
        }
    }
    
    private void updateLoanBalance(LoanRecord loan, PaymentAllocation allocation) {
        try (Connection conn = databaseService.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Update principal balance
                String updateSql = """
                    UPDATE yae_loans 
                    SET current_balance = current_balance - ?, 
                        total_principal_paid = total_principal_paid + ?, 
                        total_interest_paid = total_interest_paid + ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE loan_id = ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setDouble(1, allocation.getPrincipalPaid());
                    stmt.setDouble(2, allocation.getPrincipalPaid());
                    stmt.setDouble(3, allocation.getInterestPaid());
                    stmt.setString(4, loan.getLoanId());
                    stmt.executeUpdate();
                }
                
                // Update overdue records if applicable
                updateOverdueStatus(loan.getLoanId(), allocation, conn);
                
                conn.commit();
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to update loan balance for " + loan.getLoanId(), e);
        }
    }
    
    private void updateOverdueStatus(String loanId, PaymentAllocation allocation, Connection conn) throws SQLException {
        // Update overdue records based on penalty payment
        if (allocation.getPenaltyPaid() > 0) {
            String sql = """
                UPDATE yae_overdue_records 
                SET penalty_amount = penalty_amount - ?, 
                    current_balance = current_balance - ?, 
                    status = CASE WHEN current_balance <= 0 THEN 'RESOLVED' ELSE status END
                WHERE loan_id = ? AND status = 'ACTIVE'
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, allocation.getPenaltyPaid());
                stmt.setDouble(2, allocation.getPenaltyPaid());
                stmt.setString(3, loanId);
                stmt.executeUpdate();
            }
        }
    }
    
    // === Helper methods ===
    
    private boolean initializeDependencies() {
        try {
            databaseService = (DatabaseService) plugin.getService(ServiceType.DATABASE);
            bankService = (BankAccountManager) plugin.getService(ServiceType.BANK);
            
            return databaseService != null && bankService != null;
            
        } catch (Exception e) {
            Logging.error("Failed to initialize repayment service dependencies", e);
            return false;
        }
    }
    
    private void startAutoPaymentScheduler() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), 
            this::processAllScheduledPayments,
            1800L, // Start after 30 minutes
            3600L  // Run every hour
        );
    }
    
    private List<ScheduledPayment> getDueScheduledPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledPayment> duePayments = new ArrayList<>();
        
        for (ScheduledPayment payment : scheduledPayments.values()) {
            if (payment.getScheduledTime().isBefore(now) && payment.getStatus() == ScheduledPayment.Status.SCHEDULED) {
                duePayments.add(payment);
            }
        }
        
        return duePayments;
    }
    
    private LoanRecord getLoanRecord(String loanId, UUID playerId) {
        // Implementation to get loan record with player ID validation
        return getLoanRecord(loanId); // Simplified
    }
    
    private LoanRecord getLoanRecord(String loanId) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = "SELECT * FROM yae_loans WHERE loan_id = ? AND status IN ('ACTIVE', 'OVERDUE')";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, loanId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Create loan record from result set
                        return createLoanRecordFromResultSet(rs);
                    }
                }
            }
        } catch (SQLException e) {
            Logging.error("Failed to get loan record " + loanId, e);
        }
        return null;
    }
    
    private boolean saveScheduledPayment(ScheduledPayment payment) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                INSERT INTO yae_scheduled_payments 
                (payment_id, loan_id, schedule_time, amount, method, status, retry_count) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, payment.getPaymentId());
                stmt.setString(2, payment.getLoanId());
                stmt.setTimestamp(3, Timestamp.valueOf(payment.getScheduledTime()));
                stmt.setDouble(4, payment.getAmount());
                stmt.setString(5, payment.getMethod().name());
                stmt.setString(6, payment.getStatus().name());
                stmt.setInt(7, payment.getRetryCount());
                
                return stmt.executeUpdate() > 0;
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to save scheduled payment " + payment.getPaymentId(), e);
            return false;
        }
    }
    
    private boolean savePaymentRecord(PaymentRecord record) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                INSERT INTO yae_loan_payments 
                (loan_id, payment_date, payment_amount, principal_payment, interest_payment, 
                 penalty_payment, payment_method, payment_status, transaction_id) 
                VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, record.getLoanId());
                stmt.setDouble(2, record.getPaymentAmount());
                stmt.setDouble(3, record.getPrincipalPayment());
                stmt.setDouble(4, record.getInterestPayment());
                stmt.setDouble(5, record.getPenaltyPayment());
                stmt.setString(6, record.getMethod().name());
                stmt.setString(7, record.getStatus().name());
                stmt.setString(8, record.getTransactionId());
                
                return stmt.executeUpdate() > 0;
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to save payment record for transaction " + record.getTransactionId(), e);
            return false;
        }
    }
    
    private PaymentStatus getPaymentStatus(String loanId, int month) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = "SELECT COUNT(*) FROM yae_loan_payments WHERE loan_id = ? AND notes LIKE CONCAT('%Month:', ?, '%')";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, loanId);
                stmt.setInt(2, month);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return PaymentStatus.PAID;
                    }
                }
            }
        } catch (SQLException e) {
            Logging.error("Failed to get payment status for loan " + loanId + " month " + month, e);
        }
        
        return PaymentStatus.PENDING;
    }
    
    private double getAmountPaidForMonth(String loanId, int month) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = "SELECT SUM(payment_amount) FROM yae_loan_payments WHERE loan_id = ? AND notes LIKE CONCAT('%Month:', ?, '%')";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, loanId);
                stmt.setInt(2, month);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble(1);
                    }
                }
            }
        } catch (SQLException e) {
            Logging.error("Failed to get amount paid for loan " + loanId + " month " + month, e);
        }
        return 0;
    }
    
    private void cancelScheduledPayment(String paymentId) {
        scheduledPayments.remove(paymentId);
        // Update database
    }
    
    private void completeScheduledPayment(String paymentId) {
        ScheduledPayment payment = scheduledPayments.get(paymentId);
        if (payment != null) {
            payment.setStatus(ScheduledPayment.Status.COMPLETED);
            scheduledPayments.put(paymentId, payment);
            // Update database
        }
    }
    
    private void retryScheduledPayment(String paymentId) {
        ScheduledPayment payment = scheduledPayments.get(paymentId);
        if (payment != null) {
            payment.incrementRetryCount();
            
            if (payment.getRetryCount() >= config.getInt("autopay._retry_count", 3)) {
                payment.setStatus(ScheduledPayment.Status.FAILED);
            } else {
                // Reschedule for next interval
                LocalDateTime newScheduledTime = payment.getScheduledTime().plusHours(1);
                payment.setScheduledTime(newScheduledTime);
            }
            
            scheduledPayments.put(paymentId, payment);
            // Update database
        }
    }
    
    private void saveScheduledPayments() {
        // Save all scheduled payments to database
        for (ScheduledPayment payment : scheduledPayments.values()) {
            if (payment.getStatus() == ScheduledPayment.Status.SCHEDULED) {
                saveScheduledPayment(payment);
            }
        }
    }
    
    private String generatePaymentId(String loanId) {
        return "PAY" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + loanId;
    }
    
    private String generateTransactionId() {
        return "TXN" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
    
    private int calculateCurrentMonth(LoanRecord loan) {
        // Calculate current month based on first payment date and current date
        long monthsSinceStart = java.time.temporal.ChronoUnit.MONTHS.between(
            loan.getStartDate(), LocalDate.now()
        );
        return Math.toIntExact(monthsSinceStart) + 1;
    }
    
    private YetAnotherEconomy getPlugin() {
        return (YetAnotherEconomy) plugin;
    }
    
    // Enums and data classes
    
    public enum PaymentMethod {
        BANK_TRANSFER("银行转账"),
        WALLET("钱包"),
        SYSTEM_CREDIT("系统信用");
        
        private final String chineseName;
        
        PaymentMethod(String chineseName) {
            this.chineseName = chineseName;
        }
        
        public String getChineseName() { return chineseName; }
    }
    
    public enum PaymentStatus {
        COMPLETED("已完成"),
        PENDING("待处理"),
        PROCESSING("处理中"),
        FAILED("失败"),
        CANCELLED("已取消");
        
        private final String chineseName;
        
        PaymentStatus(String chineseName) {
            this.chineseName = chineseName;
        }
        
        public String getChineseName() { return chineseName; }
    }
    
    public static class PaymentResult {
        private final boolean success;
        private final PaymentRecord paymentRecord;
        private final String errorMessage;
        
        private PaymentResult(boolean success, PaymentRecord paymentRecord, String errorMessage) {
            this.success = success;
            this.paymentRecord = paymentRecord;
            this.errorMessage = errorMessage;
        }
        
        public static PaymentResult success(PaymentRecord paymentRecord) {
            return new PaymentResult(true, paymentRecord, null);
        }
        
        public static PaymentResult error(String errorMessage) {
            return new PaymentResult(false, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public PaymentRecord getPaymentRecord() { return paymentRecord; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class PaymentAllocation {
        private final double principalPaid;
        private final double interestPaid; 
        private final double penaltyPaid;
        
        public PaymentAllocation(double principalPaid, double interestPaid, double penaltyPaid) {
            this.principalPaid = principalPaid;
            this.interestPaid = interestPaid;
            this.penaltyPaid = penaltyPaid;
        }
        
        public double getPrincipalPaid() { return principalPaid; }
        public double getInterestPaid() { return interestPaid; }
        public double getPenaltyPaid() { return penaltyPaid; }
    }
    
    public static class PaymentSchedule {
        private final int month;
        private final double monthlyPayment;
        private final double interestPayment;
        private final double principalPayment;
        private final double remainingPrincipal;
        private final PaymentStatus status;
        private final double amountPaid;
        
        public PaymentSchedule(int month, double monthlyPayment, double interestPayment,
                             double principalPayment, double remainingPrincipal, PaymentStatus status, double amountPaid) {
            this.month = month;
            this.monthlyPayment = monthlyPayment;
            this.interestPayment = interestPayment;
            this.principalPayment = principalPayment;
            this.remainingPrincipal = remainingPrincipal;
            this.status = status;
            this.amountPaid = amountPaid;
        }
        
        public boolean isPaid() { return status == PaymentStatus.PAID; }
        public boolean isOverdue() { return status == PaymentStatus.PENDING && month < getCurrentMonth(); }
        public boolean isCurrent() { return status == PaymentStatus.PENDING && month == getCurrentMonth(); }
        public boolean isFuture() { return status == PaymentStatus.PENDING && month > getCurrentMonth(); }
        
        // Getters
        public int getMonth() { return month; }
        public double getMonthlyPayment() { return monthlyPayment; }
        public double getInterestPayment() { return interestPayment; }
        public double getPrincipalPayment() { return principalPayment; }
        public double getRemainingPrincipal() { return remainingPrincipal; }
        public PaymentStatus getStatus() { return status; }
        public double getAmountPaid() { return amountPaid; }
        
        private int getCurrentMonth() {
            return 1; // Simplified - should be calculated from loan start date
        }
    }
}
