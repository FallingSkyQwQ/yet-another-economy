package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.event.LoanEvent;
import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.CreditScoreCalculator;
import com.yae.api.credit.CreditService;
import com.yae.api.credit.LoanType;
import com.yae.api.database.DatabaseService;
import com.yae.utils.Logging;
import com.yae.YetAnotherEconomy;

import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loan service for managing loan applications, approvals, and repayment
 * Handles all loan-related operations including mortgage and credit loans
 */
public class LoanService implements Service {
    
    private final YetAnotherEconomy plugin;
    private ServiceConfig config;
    private boolean enabled = false;
    private boolean initialized = false;
    
    // In-memory cache for loans
    private final Map<String, Loan> loanCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerLoansCache = new ConcurrentHashMap<>();
    
    private CreditService creditService;
    private DatabaseService databaseService;
    private UserService userService;
    
    // Configuration defaults
    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_PROCESSING_FEE_RATE = 0.01; // 1% processing fee
    private static final double DEFAULT_LATE_PAYMENT_FEE = 50.0;
    private static final double DEFAULT_PENALTY_RATE = 0.05; // 5% penalty rate
    private static final int DEFAULT_GRACE_PERIOD_DAYS = 15;
    private static final int DEFAULT_AUTO_PAY_RETRY_DAYS = 3;
    
    public LoanService(YetAnotherEconomy plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public ServiceType getType() {
        return ServiceType.LOAN;
    }
    
    @Override
    public String getName() {
        return "Loan Service";
    }
    
    @Override
    public String getDescription() {
        return "Manages loan applications, approvals, and repayment operations";
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
            Logging.info("Loan service is disabled");
            return;
        }
        
        try {
            // Get required services
            this.creditService = plugin.getService(ServiceType.CREDIT);
            this.databaseService = plugin.getService(ServiceType.DATABASE);
            this.userService = plugin.getService(ServiceType.USER);
            
            if (creditService == null || !creditService.isEnabled()) {
                throw new IllegalStateException("Credit service is required but not available");
            }
            
            if (databaseService == null || !databaseService.isEnabled()) {
                throw new IllegalStateException("Database service is required but not available");
            }
            
            if (userService == null || !userService.isEnabled()) {
                throw new IllegalStateException("User service is required but not available");
            }
            
            // Create database tables
            createDatabaseTables();
            
            // Load existing loans into cache
            loadLoansFromDatabase();
            
            // Start scheduled tasks
            startScheduledTasks();
            
            this.initialized = true;
            Logging.info("Loan service initialized successfully");
            
            // Fire initialization event
            plugin.fireEvent(new LoanEvent.LoanServiceInitializedEvent(this));
            
        } catch (Exception e) {
            Logging.log(Level.SEVERE, "Failed to initialize loan service", e);
            this.enabled = false;
        }
    }
    
    @Override
    public void reload(ServiceConfig newConfig) {
        Logging.info("Reloading loan service...");
        
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
            // Update settings
            // Clear cache if settings changed
            boolean clearCache = !oldConfig.getString("cache.settings").equals(
                newConfig.getString("cache.settings"));
            
            if (clearCache) {
                loanCache.clear();
                playerLoansCache.clear();
                loadLoansFromDatabase();
            }
        }
        
        Logging.info("Loan service reloaded successfully");
    }
    
    @Override
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        Logging.info("Shutting down loan service...");
        
        try {
            // Save all cached loans to database
            saveAllLoans();
            
            // Clear cache
            loanCache.clear();
            playerLoansCache.clear();
            
            // Stop scheduled tasks
            plugin.cancelTask("loan-auto-payment");
            plugin.cancelTask("loan-overdue-check");
            plugin.cancelTask("loan-penalty-calculation");
            
            this.initialized = false;
            Logging.info("Loan service shut down successfully");
            
        } catch (Exception e) {
            Logging.log(Level.SEVERE, "Error during loan service shutdown", e);
        }
    }
    
    /**
     * Submit loan application
     * 
     * @param player The player applying for the loan
     * @param loanType Type of loan
     * @param amount Requested amount
     * @param termMonths Loan term in months
     * @param loanPurpose Purpose of the loan
     * @param collateralType Type of collateral (for secured loans)
     * @param collateralValue Value of collateral
     * @return CompletableFuture with loan ID or error message
     */
    public CompletableFuture<String> submitLoanApplication(Player player, LoanType loanType, 
                                                         double amount, int termMonths, 
                                                         String loanPurpose, String collateralType, 
                                                         double collateralValue) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID playerId = player.getUniqueId();
                
                // Check if player already has too many active loans
                if (hasTooManyActiveLoans(playerId)) {
                    throw new IllegalStateException("您已经有太多活跃的贷款");
                }
                
                // Check credit score and qualification
                Integer creditScore = creditService.getCreditScore(playerId).join();
                if (creditScore == null) {
                    throw new IllegalStateException("无法获取您的信用评分");
                }
                
                if (!CreditScoreCalculator.qualifiesForLoan(creditScore, loanType)) {
                    throw new IllegalStateException("您的信用评分不符合" + loanType.getDisplayName() + "的申请条件");
                }
                
                // Validate loan parameters
                validateLoanParameters(loanType, amount, termMonths, collateralType, collateralValue);
                
                // Calculate interest rate based on credit grade and loan type
                CreditGrade creditGrade = CreditScoreCalculator.getCreditGrade(creditScore);
                double interestRate = creditGrade.getInterestRate(loanType);
                
                // Create loan application
                String loanId = generateLoanId();
                Loan loan = new Loan.Builder(loanId, playerId, loanType, amount, interestRate, termMonths)
                    .loanPurpose(loanPurpose)
                    .borrowerCreditScore(creditScore)
                    .borrowerCreditGrade(creditGrade)
                    .collateralType(collateralType)
                    .collateralValue(collateralValue)
                    .originalInterestRate(interestRate)
                    .build();
                
                // Fire loan application submitted event
                plugin.fireEvent(new LoanEvent.LoanApplicationSubmittedEvent(
                    playerId, loanId, loanType, amount, creditScore, creditGrade));
                
                return loanId;
                
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Failed to submit loan application for player " + player.getName(), e);
                throw new RuntimeException(e.getMessage());
            }
        });
    }
    
    /**
     * Approve loan application
     * 
     * @param loanId The loan ID to approve
     * @param approvedBy Who approved the loan
     * @param notes Approval notes
     * @return CompletableFuture with approved loan
     */
    public CompletableFuture<Loan> approveLoan(String loanId, String approvedBy, String notes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Loan loan = getLoan(loanId);
                if (loan == null) {
                    throw new IllegalArgumentException("贷款不存在: " + loanId);
                }
                
                if (loan.getStatus() != Loan.LoanStatus.PENDING) {
                    throw new IllegalStateException("贷款状态不是待审核");
                }
                
                // Update loan status
                Loan approvedLoan = new Loan.Builder(loan)
                    .status(Loan.LoanStatus.APPROVED)
                    .approvalDate(LocalDateTime.now())
                    .approvedBy(approvedBy)
                    .notes(notes)
                    .build();
                
                // Update cache
                loanCache.put(loanId, approvedLoan);
                
                // Update database
                saveLoanToDatabase(approvedLoan);
                
                // Fire loan approved event
                plugin.fireEvent(new LoanEvent.LoanApprovedEvent(
                    loan.getBorrowerId(), loanId, approvedBy, notes));
                
                return approvedLoan;
                
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Failed to approve loan " + loanId, e);
                throw new RuntimeException(e.getMessage());
            }
        });
    }
    
    /**
     * Reject loan application
     * 
     * @param loanId The loan ID to reject
     * @param rejectedBy Who rejected the loan
     * @param reason Rejection reason
     * @return CompletableFuture with rejected loan
     */
    public CompletableFuture<Loan> rejectLoan(String loanId, String rejectedBy, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Loan loan = getLoan(loanId);
                if (loan == null) {
                    throw new IllegalArgumentException("贷款不存在: " + loanId);
                }
                
                if (loan.getStatus() != Loan.LoanStatus.PENDING) {
                    throw new IllegalStateException("贷款状态不是待审核");
                }
                
                // Update loan status
                Loan rejectedLoan = new Loan.Builder(loan)
                    .status(Loan.LoanStatus.REJECTED)
                    .notes(reason)
                    .build();
                
                // Update cache
                loanCache.put(loanId, rejectedLoan);
                
                // Update database
                saveLoanToDatabase(rejectedLoan);
                
                // Fire loan rejected event
                plugin.fireEvent(new LoanEvent.LoanRejectedEvent(
                    loan.getBorrowerId(), loanId, rejectedBy, reason));
                
                return rejectedLoan;
                
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Failed to reject loan " + loanId, e);
                throw new RuntimeException(e.getMessage());
            }
        });
    }
    
    /**
     * Disburse loan funds
     * 
     * @param loanId The loan ID to disburse
     * @return CompletableFuture with disbursed loan
     */
    public CompletableFuture<Loan> disburseLoan(String loanId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Loan loan = getLoan(loanId);
                if (loan == null) {
                    throw new IllegalArgumentException("贷款不存在: " + loanId);
                }
                
                if (loan.getStatus() != Loan.LoanStatus.APPROVED) {
                    throw new IllegalStateException("贷款状态不是已批准");
                }
                
                // Calculate monthly payment using repayment plan generator
                double monthlyPayment = calculateMonthlyPayment(
                    loan.getPrincipalAmount(), loan.getInterestRate(), loan.getTermMonths(), loan.getRepaymentMethod());
                
                // Generate repayment schedule
                List<PaymentSchedule> schedule = generateRepaymentSchedule(loan);
                
                // Update loan status
                Loan disbursedLoan = new Loan.Builder(loan)
                    .status(Loan.LoanStatus.ACTIVE)
                    .disbursementDate(LocalDateTime.now())
                    .monthlyPayment(monthlyPayment)
                    .totalPayments(schedule.size())
                    .build();
                
                // Update cache
                loanCache.put(loanId, disbursedLoan);
                
                // Update database
                saveLoanToDatabase(disbursedLoan);
                saveRepaymentSchedule(loanId, schedule);
                
                // Fire loan disbursed event
                plugin.fireEvent(new LoanEvent.LoanDisbursedEvent(
                    loan.getBorrowerId(), loanId, loan.getPrincipalAmount()));
                
                return disbursedLoan;
                
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Failed to disburse loan " + loanId, e);
                throw new RuntimeException(e.getMessage());
            }
        });
    }
    
    /**
     * Make loan payment
     * 
     * @param loanId The loan ID
     * @param amount Payment amount
     * @param paymentMethod Payment method
     * @return CompletableFuture with payment result
     */
    public CompletableFuture<PaymentResult> makePayment(String loanId, double amount, Loan.PaymentMethod paymentMethod) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Loan loan = getLoan(loanId);
                if (loan == null) {
                    throw new IllegalArgumentException("贷款不存在: " + loanId);
                }
                
                if (!loan.getStatus().isActive()) {
                    throw new IllegalStateException("贷款状态不支持还款");
                }
                
                // Calculate payment allocation
                double interestPayment = 0;
                double principalPayment = 0;
                double penaltyPayment = 0;
                
                if (loan.isOverdue()) {
                    // Pay overdue amount first
                    double overduePayment = Math.min(amount, loan.getOverdueAmount());
                    penaltyPayment = Math.min(overduePayment, loan.getCurrentPenaltyInterest());
                    double remainingOverdue = overduePayment - penaltyPayment;
                    
                    principalPayment += remainingOverdue;
                    amount -= overduePayment;
                }
                
                if (amount > 0) {
                    // Pay current month interest and principal
                    double monthlyInterest = calculateMonthlyInterest(loan);
                    interestPayment = Math.min(amount, monthlyInterest);
                    principalPayment += amount - interestPayment;
                }
                
                double totalPayment = interestPayment + principalPayment + penaltyPayment;
                
                // Update loan balance and payment counters
                double newBalance = loan.getCurrentBalance() - principalPayment;
                int newPaymentsMade = loan.getPaymentsMade() + 1;
                LoanStatus newStatus = loan.getStatus();
                
                if (overduePayments > 0) {
                    newStatus = Loan.LoanStatus.ACTIVE; // Loan is current again
                }
                
                // Update loan
                Loan updatedLoan = new Loan.Builder(loan)
                    .currentBalance(newBalance)
                    .paymentsMade(newPaymentsMade)
                    .status(newStatus)
                    .totalInterestPaid(loan.getTotalInterestPaid() + interestPayment)
                    .totalPrincipalPaid(loan.getTotalPrincipalPaid() + principalPayment)
                    .build();
                
                // Check if loan is paid off
                if (Math.abs(newBalance) < 0.01 || newPaymentsMade >= loan.getTotalPayments()) {
                    updatedLoan = new Loan.Builder(updatedLoan)
                        .status(Loan.LoanStatus.PAID_OFF)
                        .currentBalance(0)
                        .build();
                    
                    // Update credit score bonus for completing loan
                    creditService.applyBonus(loan.getBorrowerId(), 20).join();
                    
                    // Fire loan paid off event
                    plugin.fireEvent(new LoanEvent.LoanPaidOffEvent(
                        loan.getBorrowerId(), loanId, totalPayment));
                }
                
                // Update cache and database
                loanCache.put(loanId, updatedLoan);
                saveLoanToDatabase(updatedLoan);
                
                // Record payment transaction
                recordPayment(loanId, totalPayment, interestPayment, principalPayment, penaltyPayment, paymentMethod);
                
                // Fire payment made event
                plugin.fireEvent(new LoanEvent.LoanPaymentMadeEvent(
                    loan.getBorrowerId(), loanId, totalPayment, interestPayment, principalPayment, penaltyPayment));
                
                return new PaymentResult(true, totalPayment, interestPayment, principalPayment, penaltyPayment, updatedLoan);
                
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Failed to make payment for loan " + loanId, e);
                return new PaymentResult(false, 0, 0, 0, 0, null);
            }
        });
    }
    
    /**
     * Get loan by ID
     * 
     * @param loanId The loan ID
     * @return Loan or null if not found
     */
    public Loan getLoan(String loanId) {
        return loanCache.get(loanId);
    }
    
    /**
     * Get all loans for a player
     * 
     * @param playerId The player's UUID
     * @return List of player's loans
     */
    public List<Loan> getPlayerLoans(UUID playerId) {
        List<String> loanIds = playerLoansCache.get(playerId);
        if (loanIds == null) {
            return new ArrayList<>();
        }
        
        return loanIds.stream()
            .map(this::getLoan)
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * Get active loans for a player
     * 
     * @param playerId The player's UUID
     * @return List of active loans
     */
    public List<Loan> getPlayerActiveLoans(UUID playerId) {
        return getPlayerLoans(playerId).stream()
            .filter(loan -> loan.getStatus().isActive())
            .toList();
    }
    
    /**
     * Generate repayment schedule for a loan
     * 
     * @param loan The loan
     * @return List of payment schedule entries
     */
    public List<PaymentSchedule> generateRepaymentSchedule(Loan loan) {
        List<PaymentSchedule> schedule = new ArrayList<>();
        
        double principal = loan.getPrincipalAmount();
        double rate = loan.getInterestRate() / 12; // Monthly rate
        int months = loan.getTermMonths();
        
        LocalDateTime paymentDate = loan.getStartDate().plusMonths(1);
        double remainingBalance = principal;
        
        switch (loan.getRepaymentMethod()) {
            case EQUAL_INSTALLMENT:
                // Equal monthly payments (EMI)
                double monthlyPayment = calculateEMI(principal, rate, months);
                
                for (int i = 1; i <= months; i++) {
                    double interestPayment = remainingBalance * rate;
                    double principalPayment = monthlyPayment - interestPayment;
                    remainingBalance -= principalPayment;
                    
                    schedule.add(new PaymentSchedule(i, paymentDate, monthlyPayment, 
                                                   principalPayment, interestPayment, remainingBalance));
                    paymentDate = paymentDate.plusMonths(1);
                }
                break;
                
            case EQUAL_PRINCIPAL:
                // Equal principal payments
                double monthlyPrincipal = principal / months;
                
                for (int i = 1; i <= months; i++) {
                    double interestPayment = remainingBalance * rate;
                    double totalPayment = monthlyPrincipal + interestPayment;
                    remainingBalance -= monthlyPrincipal;
                    
                    schedule.add(new PaymentSchedule(i, paymentDate, totalPayment,
                                                   monthlyPrincipal, interestPayment, remainingBalance));
                    paymentDate = paymentDate.plusMonths(1);
                }
                break;
                
            case BULLET:
                // Interest only, principal at maturity
                double monthlyInterest = principal * rate;
                
                for (int i = 1; i < months; i++) {
                    schedule.add(new PaymentSchedule(i, paymentDate, monthlyInterest,
                                                   0.0, monthlyInterest, principal));
                    paymentDate = paymentDate.plusMonths(1);
                }
                
                // Final payment includes principal
                schedule.add(new PaymentSchedule(months, paymentDate, principal + monthlyInterest,
                                               principal, monthlyInterest, 0.0));
                break;
        }
        
        return schedule;
    }
    
    // Helper methods
    
    private double calculateMonthlyPayment(double principal, double annualRate, int months, Loan.RepaymentMethod method) {
        switch (method) {
            case EQUAL_INSTALLMENT:
                return calculateEMI(principal, annualRate / 12, months);
            case EQUAL_PRINCIPAL:
                double monthlyPrincipal = principal / months;
                double avgInterest = principal * (annualRate / 12) / 2;
                return monthlyPrincipal + avgInterest;
            case BULLET:
                return principal * (annualRate / 12); // Interest only
            default:
                return calculateEMI(principal, annualRate / 12, months);
        }
    }
    
    private double calculateEMI(double principal, double monthlyRate, int months) {
        if (monthlyRate == 0) {
            return principal / months;
        }
        
        double emi = principal * monthlyRate * Math.pow(1 + monthlyRate, months) /
                    (Math.pow(1 + monthlyRate, months) - 1);
        return emi;
    }
    
    private double calculateMonthlyInterest(Loan loan) {
        return loan.getCurrentBalance() * (loan.getInterestRate() / 12);
    }
    
    private boolean validateLoanParameters(LoanType loanType, double amount, int termMonths, 
                                         String collateralType, double collateralValue) {
        if (amount <= 0) {
            throw new IllegalArgumentException("贷款金额必须大于0");
        }
        
        if (termMonths <= 0 || termMonths > loanType.getMaxTermMonths()) {
            throw new IllegalArgumentException("贷款期限必须在1-" + loanType.getMaxTermMonths() + "个月之间");
        }
        
        if (loanType.requiresCollateral() && (collateralType == null || collateralValue <= 0)) {
            throw new IllegalArgumentException("抵押贷款需要提供有效的抵押物");
        }
        
        return true;
    }
    
    private boolean hasTooManyActiveLoans(UUID playerId) {
        List<Loan> activeLoans = getPlayerActiveLoans(playerId);
        int maxLoans = config.getInt("max-active-loans-per-player", 5);
        return activeLoans.size() >= maxLoans;
    }
    
    private String generateLoanId() {
        return "LOAN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private void createDatabaseTables() {
        // Loans table
        String createLoansTable = """
            CREATE TABLE IF NOT EXISTS yae_loans (
                loan_id VARCHAR(50) PRIMARY KEY,
                borrower_uuid VARCHAR(36) NOT NULL,
                lender_uuid VARCHAR(36) NOT NULL,
                loan_type VARCHAR(20) NOT NULL,
                loan_purpose TEXT,
                principal_amount DECIMAL(15,2) NOT NULL,
                current_balance DECIMAL(15,2) NOT NULL,
                interest_rate DECIMAL(5,4) NOT NULL,
                original_interest_rate DECIMAL(5,4) NOT NULL,
                term_months INTEGER NOT NULL,
                start_date TIMESTAMP,
                maturity_date TIMESTAMP NOT NULL,
                next_payment_date TIMESTAMP,
                monthly_payment DECIMAL(12,2) NOT NULL,
                payment_method VARCHAR(20) NOT NULL,
                repayment_method VARCHAR(20) NOT NULL,
                status VARCHAR(20) NOT NULL,
                payments_made INTEGER DEFAULT 0,
                total_payments INTEGER NOT NULL,
                total_interest_paid DECIMAL(15,2) DEFAULT 0,
                total_principal_paid DECIMAL(15,2) DEFAULT 0,
                overdue_payments INTEGER DEFAULT 0,
                overdue_amount DECIMAL(12,2) DEFAULT 0,
                last_overdue_date TIMESTAMP,
                is_in_default BOOLEAN DEFAULT FALSE,
                default_date TIMESTAMP,
                collateral_type VARCHAR(50),
                collateral_value DECIMAL(15,2),
                collateral_discount_rate DECIMAL(5,4),
                collateral_description TEXT,
                borrower_credit_score INTEGER,
                borrower_credit_grade VARCHAR(10),
                application_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                approval_date TIMESTAMP,
                disbursement_date TIMESTAMP,
                approved_by VARCHAR(50),
                notes TEXT,
                auto_pay_enabled BOOLEAN DEFAULT TRUE,
                penalty_waived BOOLEAN DEFAULT FALSE,
                penalty_rate DECIMAL(5,4) DEFAULT 0.05,
                is_refinanced BOOLEAN DEFAULT FALSE,
                original_loan_id VARCHAR(50)
            )
            """;
        
        // Loan payments table
        String createLoanPaymentsTable = """
            CREATE TABLE IF NOT EXISTS yae_loan_payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                loan_id VARCHAR(50) NOT NULL,
                payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                payment_amount DECIMAL(12,2) NOT NULL,
                interest_payment DECIMAL(12,2) NOT NULL,
                principal_payment DECIMAL(12,2) NOT NULL,
                penalty_payment DECIMAL(12,2) NOT NULL,
                payment_method VARCHAR(20) NOT NULL,
                payment_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
                transaction_id VARCHAR(50),
                notes TEXT,
                FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)
            )
            """;
        
        // Loan payment schedule table
        String createLoanScheduleTable = """
            CREATE TABLE IF NOT EXISTS yae_loan_schedule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                loan_id VARCHAR(50) NOT NULL,
                payment_number INTEGER NOT NULL,
                payment_date TIMESTAMP NOT NULL,
                scheduled_payment DECIMAL(12,2) NOT NULL,
                principal_payment DECIMAL(12,2) NOT NULL,
                interest_payment DECIMAL(12,2) NOT NULL,
                remaining_balance DECIMAL(15,2) NOT NULL,
                payment_status VARCHAR(20) DEFAULT 'PENDING',
                actual_payment_date TIMESTAMP,
                actual_payment_amount DECIMAL(12,2),
                FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)
            )
            """;
        
        databaseService.executeUpdate(createLoansTable);
        databaseService.executeUpdate(createLoanPaymentsTable);
        databaseService.executeUpdate(createLoanScheduleTable);
    }
    
    private void loadLoansFromDatabase() {
        // Load active loans into cache
        String query = "SELECT * FROM yae_loans WHERE status IN ('PENDING', 'APPROVED', 'ACTIVE', 'OVERDUE')";
        
        databaseService.executeQuery(query, resultSet -> {
            while (resultSet.next()) {
                Loan loan = createLoanFromResultSet(resultSet);
                loanCache.put(loan.getLoanId(), loan);
                
                // Update player loans cache
                UUID borrowerId = loan.getBorrowerId();
                playerLoansCache.computeIfAbsent(borrowerId, k -> new ArrayList<>())
                    .add(loan.getLoanId());
            }
            return null;
        });
    }
    
    private Loan createLoanFromResultSet(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        // This would map database fields to Loan object
        // Implementation depends on actual database schema
        String loanId = resultSet.getString("loan_id");
        UUID borrowerId = UUID.fromString(resultSet.getString("borrower_uuid"));
        LoanType loanType = LoanType.fromKey(resultSet.getString("loan_type"));
        double principalAmount = resultSet.getDouble("principal_amount");
        double interestRate = resultSet.getDouble("interest_rate");
        int termMonths = resultSet.getInt("term_months");
        
        return new Loan.Builder(loanId, borrowerId, loanType, principalAmount, interestRate, termMonths)
            .lenderId(UUID.fromString(resultSet.getString("lender_uuid")))
            .loanPurpose(resultSet.getString("loan_purpose"))
            .currentBalance(resultSet.getDouble("current_balance"))
            .originalInterestRate(resultSet.getDouble("original_interest_rate"))
            .startDate(resultSet.getTimestamp("start_date").toLocalDateTime())
            .maturityDate(resultSet.getTimestamp("maturity_date").toLocalDateTime())
            .nextPaymentDate(resultSet.getTimestamp("next_payment_date").toLocalDateTime())
            .monthlyPayment(resultSet.getDouble("monthly_payment"))
            .paymentMethod(Loan.PaymentMethod.valueOf(resultSet.getString("payment_method")))
            .repaymentMethod(Loan.RepaymentMethod.valueOf(resultSet.getString("repayment_method")))
            .status(Loan.LoanStatus.valueOf(resultSet.getString("status")))
            .paymentsMade(resultSet.getInt("payments_made"))
            .totalPayments(resultSet.getInt("total_payments"))
            .totalInterestPaid(resultSet.getDouble("total_interest_paid"))
            .totalPrincipalPaid(resultSet.getDouble("total_principal_paid"))
            .overduePayments(resultSet.getInt("overdue_payments"))
            .overdueAmount(resultSet.getDouble("overdue_amount"))
            .isInDefault(resultSet.getBoolean("is_in_default"))
            .collateralType(resultSet.getString("collateral_type"))
            .collateralValue(resultSet.getDouble("collateral_value"))
            .borrowerCreditScore(resultSet.getInt("borrower_credit_score"))
            .build();
    }
    
    private void saveLoanToDatabase(Loan loan) {
        // Implementation depends on actual database schema
        // This would insert or update the loan record
    }
    
    private void saveRepaymentSchedule(String loanId, List<PaymentSchedule> schedule) {
        // Implementation to save repayment schedule to database
    }
    
    private void recordPayment(String loanId, double totalPayment, double interestPayment, 
                             double principalPayment, double penaltyPayment, Loan.PaymentMethod paymentMethod) {
        // Implementation to record payment transaction in database
    }
    
    private void saveAllLoans() {
        for (Loan loan : loanCache.values()) {
            saveLoanToDatabase(loan);
        }
    }
    
    private void startScheduledTasks() {
        // Auto-payment task (daily)
        plugin.scheduleAsyncRepeatingTask("loan-auto-payment", () -> {
            processAutoPayments();
        }, 20 * 60 * 60 * 24L, 20 * 60 * 60 * 24L);
        
        // Overdue check task (daily)
        plugin.scheduleAsyncRepeatingTask("loan-overdue-check", () -> {
            checkOverdueLoans();
        }, 20 * 60 * 60 * 24L, 20 * 60 * 60 * 24L);
        
        // Penalty calculation task (daily)
        plugin.scheduleAsyncRepeatingTask("loan-penalty-calculation", () -> {
            calculatePenalties();
        }, 20 * 60 * 60 * 24L, 20 * 60 * 60 * 24L);
    }
    
    private void processAutoPayments() {
        // Process automatic payments for loans with auto-pay enabled
        LocalDateTime today = LocalDateTime.now();
        
        for (Loan loan : loanCache.values()) {
            if (loan.getStatus() == Loan.LoanStatus.ACTIVE && loan.isAutoPayEnabled()) {
                if (loan.getNextPaymentDate() != null && 
                    loan.getNextPaymentDate().toLocalDate().equals(today.toLocalDate())) {
                    
                    // Attempt auto-payment
                    makePayment(loan.getLoanId(), loan.getMonthlyPayment(), Loan.PaymentMethod.AUTOMATIC);
                }
            }
        }
    }
    
    private void checkOverdueLoans() {
        // Check for overdue loans and update status
        LocalDateTime today = LocalDateTime.now();
        
        for (Loan loan : loanCache.values()) {
            if (loan.getStatus() == Loan.LoanStatus.ACTIVE) {
                if (loan.getNextPaymentDate() != null && loan.getNextPaymentDate().isBefore(today)) {
                    // Loan is overdue
                    int overduePayments = loan.getOverduePayments() + 1;
                    double monthlyPayment = loan.getMonthlyPayment();
                    
                    Loan updatedLoan = new Loan.Builder(loan)
                        .status(Loan.LoanStatus.OVERDUE)
                        .overduePayments(overduePayments)
                        .overdueAmount(loan.getOverdueAmount() + monthlyPayment)
                        .lastOverdueDate(today)
                        .build();
                    
                    loanCache.put(loan.getLoanId(), updatedLoan);
                    saveLoanToDatabase(updatedLoan);
                    
                    // Apply credit penalty
                    creditService.applyPenalty(loan.getBorrowerId(), CreditService.PenaltyType.LATE_PAYMENT, 10).join();
                    
                    // Fire overdue event
                    plugin.fireEvent(new LoanEvent.LoanOverdueEvent(
                        loan.getBorrowerId(), loan.getLoanId(), overduePayments, loan.getOverdueAmount() + monthlyPayment));
                    
                    // Check for default
                    if (overduePayments >= 3) {
                        markLoanAsDefault(loan.getLoanId(), "连续3次逾期付款");
                    }
                }
            }
        }
    }
    
    private void calculatePenalties() {
        // Calculate penalty interest for overdue loans
        for (Loan loan : loanCache.values()) {
            if (loan.isOverdue() && !loan.isPenaltyWaived()) {
                double penaltyInterest = loan.getCurrentPenaltyInterest();
                
                if (penaltyInterest > 0) {
                    // Add penalty to overdue amount
                    Loan updatedLoan = new Loan.Builder(loan)
                        .overdueAmount(loan.getOverdueAmount() + penaltyInterest)
                        .build();
                    
                    loanCache.put(loan.getLoanId(), updatedLoan);
                    saveLoanToDatabase(updatedLoan);
                    
                    // Fire penalty calculation event
                    plugin.fireEvent(new LoanEvent.LoanPenaltyCalculatedEvent(
                        loan.getBorrowerId(), loan.getLoanId(), penaltyInterest));
                }
            }
        }
    }
    
    private void markLoanAsDefault(String loanId, String reason) {
        Loan loan = getLoan(loanId);
        if (loan != null && loan.getStatus() != Loan.LoanStatus.DEFAULT) {
            Loan defaultedLoan = new Loan.Builder(loan)
                .status(Loan.LoanStatus.DEFAULT)
                .defaultDate(LocalDateTime.now())
                .build();
            
            loanCache.put(loanId, defaultedLoan);
            saveLoanToDatabase(defaultedLoan);
            
            // Apply default credit penalty
            creditService.applyPenalty(loan.getBorrowerId(), CreditService.PenaltyType.DEFAULT, 50).join();
            
            // Fire default event
            plugin.fireEvent(new LoanEvent.LoanDefaultedEvent(
                loan.getBorrowerId(), loanId, reason));
        }
    }
    
    // Payment result class
    public static class PaymentResult {
        private final boolean success;
        private final double totalPayment;
        private final double interestPayment;
        private final double principalPayment;
        private final double penaltyPayment;
        private final Loan updatedLoan;
        
        public PaymentResult(boolean success, double totalPayment, double interestPayment, 
                           double principalPayment, double penaltyPayment, Loan updatedLoan) {
            this.success = success;
            this.totalPayment = totalPayment;
            this.interestPayment = interestPayment;
            this.principalPayment = principalPayment;
            this.penaltyPayment = penaltyPayment;
            this.updatedLoan = updatedLoan;
        }
        
        public boolean isSuccess() { return success; }
        public double getTotalPayment() { return totalPayment; }
        public double getInterestPayment() { return interestPayment; }
        public double getPrincipalPayment() { return principalPayment; }
        public double getPenaltyPayment() { return penaltyPayment; }
        public Loan getUpdatedLoan() { return updatedLoan; }
    }
    
    // Payment schedule class
    public static class PaymentSchedule {
        private final int paymentNumber;
        private final LocalDateTime paymentDate;
        private final double scheduledPayment;
        private final double principalPayment;
        private final double interestPayment;
        private final double remainingBalance;
        private final boolean isPaid;
        private final LocalDateTime actualPaymentDate;
        private final double actualPaymentAmount;
        
        public PaymentSchedule(int paymentNumber, LocalDateTime paymentDate, double scheduledPayment,
                             double principalPayment, double interestPayment, double remainingBalance) {
            this(paymentNumber, paymentDate, scheduledPayment, principalPayment, interestPayment, 
                 remainingBalance, false, null, 0);
        }
        
        public PaymentSchedule(int paymentNumber, LocalDateTime paymentDate, double scheduledPayment,
                             double principalPayment, double interestPayment, double remainingBalance,
                             boolean isPaid, LocalDateTime actualPaymentDate, double actualPaymentAmount) {
            this.paymentNumber = paymentNumber;
            this.paymentDate = paymentDate;
            this.scheduledPayment = scheduledPayment;
            this.principalPayment = principalPayment;
            this.interestPayment = interestPayment;
            this.remainingBalance = remainingBalance;
            this.isPaid = isPaid;
            this.actualPaymentDate = actualPaymentDate;
            this.actualPaymentAmount = actualPaymentAmount;
        }
        
        public int getPaymentNumber() { return paymentNumber; }
        public LocalDateTime getPaymentDate() { return paymentDate; }
        public double getScheduledPayment() { return scheduledPayment; }
        public double getPrincipalPayment() { return principalPayment; }
        public double getInterestPayment() { return interestPayment; }
        public double getRemainingBalance() { return remainingBalance; }
        public boolean isPaid() { return isPaid; }
        public LocalDateTime getActualPaymentDate() { return actualPaymentDate; }
        public double getActualPaymentAmount() { return actualPaymentAmount; }
    }
    
    @Override
    public String getStatus() {
        if (!initialized) {
            return "Not initialized";
        }
        
        return String.format("Enabled: %s, Active loans: %d, Cached loans: %d",
            enabled, getActiveLoansCount(), loanCache.size());
    }
    
    private long getActiveLoansCount() {
        return loanCache.values().stream()
            .filter(loan -> loan.getStatus().isActive())
            .count();
    }
    
    @Override
    public ServiceConfig getConfiguration() {
        return config;
    }
}
