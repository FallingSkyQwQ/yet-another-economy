package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.credit.CreditService;
import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.CreditScoreCalculator;
import com.yae.api.credit.LoanType;
import com.yae.api.database.DatabaseService;
import com.yae.utils.Logging;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Professional loan application service with 5-step application process
 * Implements comprehensive eligibility checking, approval workflow, and repayment planning
 */
public class LoanApplicationService implements Service {
    
    private final YAECore plugin;
    private ServiceConfig config;
    private boolean enabled = false;
    private boolean initialized = false;
    
    // Service dependencies
    private CreditService creditService;
    private DatabaseService databaseService;
    private RepaymentPlanService repaymentPlanService;
    private LoanApprovalService approvalService;
    
    // Configuration defaults
    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_CREDIT_LOAN_THRESHOLD = 0.8; // 80% of collateral
    private static final double DEFAULT_MORTGAGE_LOAN_THRESHOLD = 0.7; // 70% of collateral
    private static final double DEFAULT_BUSINESS_LOAN_THRESHOLD = 0.6; // 60% of collateral
    private static final int DEFAULT_PROCESSING_TIME_HOURS = 24;
    private static final double DEFAULT_PROCESSING_FEE_RATE = 0.01;
    
    // Loan application cache
    private final Map<UUID, LoanApplication> applicationCache = new HashMap<>();
    
    public LoanApplicationService(YAECore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getName() {
        return "Loan Application Service";
    }
    
    @Override
    public @NotNull ServiceType getType() {
        return ServiceType.LOAN;
    }
    
    @Override
    public String getDescription() {
        return "Professional loan application processing with 5-step workflow";
    }
    
    @Override
    public boolean initialize() {
        this.config = getConfig();
        if (config == null) {
            return false;
        }
        
        this.enabled = config.getBoolean("enabled", DEFAULT_ENABLED);
        
        if (!enabled) {
            Logging.info("Loan application service is disabled");
            return true;
        }
        
        // Initialize service dependencies
        if (!initializeDependencies()) {
            Logging.error("Failed to initialize loan application service dependencies");
            return false;
        }
        
        // Initialize supporting services
        this.repaymentPlanService = new RepaymentPlanService(config);
        this.approvalService = new LoanApprovalService(config);
        
        this.initialized = true;
        Logging.info("Loan application service initialized successfully");
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
        
        Logging.info("Shutting down loan application service...");
        
        // Clear application cache
        applicationCache.clear();
        
        this.initialized = false;
        Logging.info("Loan application service shut down successfully");
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
        return serviceType == ServiceType.CREDIT || serviceType == ServiceType.DATABASE;
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
     * 5-Step Loan Application Process
     * Step 1: Eligibility Check - Credit score and account status verification
     */
    public EligibilityResult checkEligibility(UUID playerId, LoanType loanType) {
        try {
            // Get player's credit score and grade
            int creditScore = creditService.getCreditScore(playerId);
            CreditGrade creditGrade = creditService.getCreditGrade(playerId);
            
            // Check minimum credit score requirements
            boolean creditScoreQualified = creditService.qualifiesForLoan(playerId, loanType);
            
            // Check existing loans and overdue status
            LoanStatusSummary loanStatus = getLoanStatus(playerId);
            
            // Check account verification status
            boolean accountVerified = isAccountVerified(playerId);
            
            EligibilityResult result = new EligibilityResult(
                playerId, loanType, creditScore, creditGrade,
                creditScoreQualified, loanStatus, accountVerified
            );
            
            result.setEligible(creditScoreQualified && loanStatus.isClean() && accountVerified);
            result.setRecommendations(generateEligibilityRecommendations(creditScore, loanStatus));
            
            return result;
            
        } catch (Exception e) {
            Logging.error("Failed to check eligibility for player " + playerId, e);
            return EligibilityResult.error(playerId, loanType, "系统错误，请稍后重试");
        }
    }
    
    /**
     * Step 2: Loan Amount and Term Selection with validation
     */
    public LoanValidationResult validateLoanAmount(UUID playerId, LoanType loanType, 
                                                 double requestedAmount, int termMonths) {
        try {
            // Get eligibility results
            EligibilityResult eligibility = checkEligibility(playerId, loanType);
            
            if (!eligibility.isEligible()) {
                return LoanValidationResult.ineligible(playerId, "不满足贷款申请条件");
            }
            
            // Calculate maximum allowed amount based on credit score and type
            double maximumAmount = calculateMaximumLoanAmount(playerId, loanType, termMonths);
            
            // Calculate interest rate based on credit score and loan type
            double interestRate = calculateInterestRate(playerId, loanType, requestedAmount, termMonths);
            
            // Validate terms
            int minTerm = getMinimumTerm(loanType);
            int maxTerm = getMaximumTerm(loanType);
            
            boolean amountValid = requestedAmount <= maximumAmount && requestedAmount >= getMinimumAmount(loanType);
            boolean termValid = termMonths >= minTerm && termMonths <= maxTerm;
            
            LoanValidationResult result = new LoanValidationResult(
                playerId, loanType, requestedAmount, termMonths,
                amountValid, termValid, maximumAmount, interestRate
            );
            
            if (!amountValid) {
                result.addMessage(String.format("贷款金额必须在 ¥%.2f - ¥%.2f 之间", 
                    getMinimumAmount(loanType), maximumAmount));
            }
            
            if (!termValid) {
                result.addMessage(String.format("贷款期限必须在 %d-%d 个月之间", minTerm, maxTerm));
            }
            
            return result;
            
        } catch (Exception e) {
            Logging.error("Failed to validate loan amount for player " + playerId, e);
            return LoanValidationResult.error(playerId, "验证失败，请稍后重试");
        }
    }
    
    /**
     * Step 3: Loan Type Selection with specialized terms
     */
    public LoanTypeResult getLoanTypeDetails(LoanType loanType, double amount, int termMonths) {
        try {
            double interestRate = getBaseInterestRate(loanType);
            double processingFeeRate = getProcessingFeeRate(loanType);
            String description = getLoanTypeDescription(loanType);
            
            // Calculate processing fee
            double processingFee = amount * processingFeeRate;
            
            // Get special requirements for loan type
            List<String> requirements = getLoanTypeRequirements(loanType, amount);            
            
            LoanTypeResult result = new LoanTypeResult(
                loanType, amount, termMonths, interestRate, processingFee, description
            );
            
            result.setRequirements(requirements);
            result.setAdvantages(getLoanTypeAdvantages(loanType));
            result.setRisks(getLoanTypeRisks(loanType));
            
            return result;
            
        } catch (Exception e) {
            Logging.error("Failed to get loan type details for " + loanType, e);
            return LoanTypeResult.error(loanType, "获取贷款类型信息失败");
        }
    }
    
    /**
     * Step 4: Collateral Assessment (for secured loans)
     */
    public CollateralAssessment assessCollateral(LoanType loanType, List<CollateralItem> collateralItems) {
        try {
            if (loanType != LoanType.MORTGAGE) {
                return CollateralAssessment.notRequired();
            }
            
            double totalCollateralValue = 0;
            Map<String, Double> assessedValues = new HashMap<>();
            Map<String, Double> discountRates = new HashMap<>();
            
            for (CollateralItem item : collateralItems) {
                // Get collateral type info from database
                CollateralTypeInfo typeInfo = getCollateralTypeInfo(item.getType());
                
                if (typeInfo == null || !typeInfo.isActive()) {
                    return CollateralAssessment.error("抵押物类型不支持: " + item.getType());
                }
                
                // Calculate discounted value
                double itemValue = item.getQuantity() * typeInfo.getBaseValue();
                double discountedValue = itemValue * typeInfo.getDiscountRate();
                
                totalCollateralValue += discountedValue;
                assessedValues.put(item.getType(), discountedValue);
                discountRates.put(item.getType(), typeInfo.getDiscountRate());
            }
            
            if (collateralItems.isEmpty()) {
                return CollateralAssessment.error("抵押物列表为空");
            }
            
            return new CollateralAssessment(
                true, totalCollateralValue, assessedValues, discountRates,
                "评估完成 - 总价值: " + String.format("¥%.2f", totalCollateralValue)
            );
            
        } catch (Exception e) {
            Logging.error("Failed to assess collateral", e);
            return CollateralAssessment.error("抵押物评估失败");
        }
    }
    
    /**
     * Step 5: Final Confirmation and Auto-Approval
     */
    public LoanApplicationResult submitApplication(LoanApplication application) {
        try {
            if (!application.isValid()) {
                return LoanApplicationResult.error(application.getPlayerId(), "申请信息不完整");
            }
            
            // Assign application ID
            String applicationId = generateApplicationId(application.getPlayerId());
            application.setApplicationId(applicationId);
            
            // Calculate final terms based on all information
            LoanTerms finalTerms = calculateFinalTerms(application);
            application.setFinalTerms(finalTerms);
            
            // Attempt auto-approval for qualified applications
            AutoApprovalResult autoApproval = attemptAutoApproval(application);
            application.setAutoApproval(autoApproval);
            
            if (autoApproval.isApproved()) {
                application.setStatus(LoanApplication.Status.AUTO_APPROVED);
                application.setApprovedBy("SYSTEM");
                application.setApprovalDate(LocalDateTime.now());
                application.setApprovalReason(autoApproval.getReason());
            } else {
                application.setStatus(LoanApplication.Status.PENDING_REVIEW);
                application.setApprovalReason(autoApproval.getReason());
            }
            
            // Save application to database
            boolean saved = saveApplication(application);
            
            if (saved) {
                // Cache for quick access
                applicationCache.put(application.getPlayerId(), application);
                
                return LoanApplicationResult.success(application, autoApproval);
            } else {
                return LoanApplicationResult.error(application.getPlayerId(), "保存申请失败");
            }
            
        } catch (Exception e) {
            Logging.error("Failed to submit loan application for player " + application.getPlayerId(), e);
            return LoanApplicationResult.error(application.getPlayerId(), "申请提交失败，请稍后重试");
        }
    }
    
    // === Supporting Methods ===
    
    private boolean initializeDependencies() {
        try {
            creditService = (CreditService) plugin.getService(ServiceType.CREDIT);
            databaseService = (DatabaseService) plugin.getService(ServiceType.DATABASE);
            
            if (creditService == null || databaseService == null) {
                Logging.error("Required services not available - Credit: " + creditService + ", DB: " + databaseService);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Logging.error("Failed to initialize loan application service dependencies", e);
            return false;
        }
    }
    
    private double calculateMaximumLoanAmount(UUID playerId, LoanType loanType, int termMonths) {
        int creditScore = creditService.getCreditScore(playerId);
        double baseMultiplier = config.getDouble("loan_max_multiplier", 2.0);
        
        // Credit score multiplier
        double creditMultiplier = Math.max(1.0, creditScore / 700.0);
        
        // Term adjustment
        double termMultiplier = Math.min(1.5, 1.0 + (termMonths / 60.0)); // Up to 50% bonus for longer terms
        
        return baseMultiplier * creditMultiplier * termMultiplier * 100000; // Base 100k
    }
    
    private double calculateInterestRate(UUID playerId, LoanType loanType, double amount, int termMonths) {
        int creditScore = creditService.getCreditScore(playerId);
        double baseRate = getBaseInterestRate(loanType);
        
        // Credit score adjustment (-1% to +3%)
        double creditAdjustment = (650 - creditScore) / 50.0 * 0.5;
        
        // Amount adjustment (large loans get slight reduction)
        double amountAdjustment = amount > 50000 ? -0.2 : 0;
        
        // Term adjustment
        double termAdjustment = termMonths > 36 ? -0.1 : (termMonths < 6 ? 0.2 : 0);
        
        return baseRate + creditAdjustment + amountAdjustment + termAdjustment;
    }
    
    private AutoApprovalResult attemptAutoApproval(LoanApplication application) {
        // Auto-approval criteria
        int creditScore = creditService.getCreditScore(application.getPlayerId());
        LoanType loanType = application.getLoanType();
        double amount = application.getRequestedAmount();
        
        // Credit score requirements for auto-approval
        int minAutoApproveScore = getMinAutoApproveScore(loanType);
        
        if (creditScore >= minAutoApproveScore) {
            // Additional checks
            LoanStatusSummary loanStatus = getLoanStatus(application.getPlayerId());
            if (loanStatus.getActiveLoanCount() < 2 && !loanStatus.hasOverdueLoans()) {
                return AutoApprovalResult.approved("信用评分优秀，符合自动审批条件");
            }
        }
        
        return AutoApprovalResult.rejected("需要人工审核");
    }
    
    private String generateApplicationId(UUID playerId) {
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "LAP" + timestamp + "-" + playerId.toString().substring(0, 8);
    }
    
    private boolean saveApplication(LoanApplication application) {
        try (Connection conn = databaseService.getConnection()) {
            String sql = """
                INSERT INTO yae_loans (
                    loan_id, borrower_uuid, loan_type, loan_purpose,
                    principal_amount, interest_rate, term_months, status,
                    application_date, approved_by, approval_date, approval_reason,
                    collateral_type, collateral_value, collateral_discount_rate,
                    borrower_credit_score, borrower_credit_grade
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, application.getApplicationId());
                stmt.setString(2, application.getPlayerId().toString());
                stmt.setString(3, application.getLoanType().name());
                stmt.setString(4, application.getLoanPurpose());
                stmt.setDouble(5, application.getRequestedAmount());
                stmt.setDouble(6, application.getFinalTerms().getInterestRate());
                stmt.setInt(7, application.getTermMonths());
                stmt.setString(8, application.getStatus().name());
                stmt.setTimestamp(9, Timestamp.valueOf(application.getApplicationDate()));
                stmt.setString(10, application.getApprovedBy());
                stmt.setTimestamp(11, application.getApprovalDate() != null ? 
                    Timestamp.valueOf(application.getApprovalDate()) : null);
                stmt.setString(12, application.getApprovalReason());
                stmt.setString(13, application.getCollateralType());
                stmt.setDouble(14, application.getCollateralValue());
                stmt.setDouble(15, application.getCollateralDiscountRate());
                stmt.setInt(16, application.getCreditScore());
                stmt.setString(17, application.getCreditGrade().name());
                
                return stmt.executeUpdate() > 0;
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to save loan application " + application.getApplicationId(), e);
            return false;
        }
    }
    
    // === Configuration Access Methods ===
    
    private double getBaseInterestRate(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return config.getDouble("credit_loan_rate", 8.5);
            case MORTGAGE: return config.getDouble("mortgage_loan_rate", 5.5);
            case BUSINESS: return config.getDouble("business_loan_rate", 7.0);
            case EMERGENCY: return config.getDouble("emergency_loan_rate", 12.0);
            default: return config.getDouble("default_loan_rate", 10.0);
        }
    }
    
    private double getProcessingFeeRate(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return config.getDouble("credit_loan_fee_rate", 0.01);
            case MORTGAGE: return config.getDouble("mortgage_loan_fee_rate", 0.02);
            case BUSINESS: return config.getDouble("business_loan_fee_rate", 0.015);
            case EMERGENCY: return config.getDouble("emergency_loan_fee_rate", 0.005);
            default: return config.getDouble("default_loan_fee_rate", 0.01);
        }
    }
    
    private int getMinimumTerm(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return 3;
            case MORTGAGE: return 12;
            case BUSINESS: return 6;
            case EMERGENCY: return 1;
            default: return 3;
        }
    }
    
    private int getMaximumTerm(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return 36;
            case MORTGAGE: return 360;
            case BUSINESS: return 60;
            case EMERGENCY: return 3;
            default: return 60;
        }
    }
    
    private int getMinAutoApproveScore(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return 720;
            case MORTGAGE: return 750;
            case BUSINESS: return 780;
            case EMERGENCY: return 650;
            default: return 700;
        }
    }
    
    private double getMinimumAmount(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return 1000;
            case MORTGAGE: return 10000;
            case BUSINESS: return 5000;
            case EMERGENCY: return 500;
            default: return 1000;
        }
    }
    
    // === Additional Supporting Methods ===
    
    private void setRecommendations() {
        // To be implemented
    }
    
    private LoanStatusSummary getLoanStatus(UUID playerId) {
        // To be implemented
        return new LoanStatusSummary();
    }
    
    private boolean isAccountVerified(UUID playerId) {
        // To be implemented
        return true;
    }
    
    private List<String> generateEligibilityRecommendations(int creditScore, LoanStatusSummary loanStatus) {
        List<String> recommendations = new ArrayList<>();
        
        if (creditScore < 650) {
            recommendations.add("建议提高信用评分至650以上");
            recommendations.add("可通过定期存款和及时还款改善信用");
        }
        
        if (loanStatus.getActiveLoanCount() > 2) {
            recommendations.add("当前贷款数量较多，建议先还清部分贷款");
        }
        
        if (loanStatus.hasOverdueLoans()) {
            recommendations.add("存在逾期贷款，请优先处理逾期款项");
        }
        
        return recommendations;
    }
    
    private List<String> getLoanTypeRequirements(LoanType loanType, double amount) {
        List<String> requirements = new ArrayList<>();
        
        switch (loanType) {
            case CREDIT:
                requirements.add("信用评分600以上");
                requirements.add("有效身份证明");
                requirements.add("稳定收入证明");
                break;
            case MORTGAGE:
                requirements.add("信用评分650以上");
                requirements.add("抵押物评估");
                requirements.add("房产证明文件");
                requirements.add("收入证明");
                break;
            case BUSINESS:
                requirements.add("信用评分700以上");
                requirements.add("营业执照");
                requirements.add("财务报表");
                requirements.add("商业计划书");
                break;
            case EMERGENCY:
                requirements.add("信用评分500以上");
                requirements.add("紧急情况证明");
                break;
        }
        
        return requirements;
    }
    
    private List<String> getLoanTypeAdvantages(LoanType loanType) {
        List<String> advantages = new ArrayList<>();
        
        switch (loanType) {
            case CREDIT:
                advantages.add("审批快速，1-3个工作日");
                advantages.add("无需抵押，凭信用放款");
                advantages.add("用途灵活，满足多种需求");
                break;
            case MORTGAGE:
                advantages.add("利率较低，长期稳定");
                advantages.add("额度较高，可达抵押物价值80%");
                advantages.add("期限灵活，最长可达30年");
                break;
            case BUSINESS:
                advantages.add("支持企业发展，盘活资金");
                advantages.add("专业顾问服务");
                advantages.add("支持企业扩张和技术升级");
                break;
            case EMERGENCY:
                advantages.add("应急资金，快速审批");
                advantages.add("在线申请，即时放款");
                advantages.add("无需复杂材料");
                break;
        }
        
        return advantages;
    }
    
    private List<String> getLoanTypeRisks(LoanType loanType) {
        List<String> risks = new ArrayList<>();
        
        switch (loanType) {
            case CREDIT:
                risks.add("利率相对较高");
                risks.add("额度受信用评分影响");
                break;
            case MORTGAGE:
                risks.add("抵押物风险");
                risks.add("审批时间较长");
                break;
            case BUSINESS:
                risks.add("市场风险影响");
                risks.add("需要详细商业计划");
                break;
            case EMERGENCY:
                risks.add("期限较短，还款压力较大");
                risks.add("利率相对较高");
                break;
        }
        
        return risks;
    }
    
    private String getLoanTypeDescription(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return "基于个人信用的无抵押贷款";
            case MORTGAGE: return "以房产等资产作为抵押的贷款";
            case BUSINESS: return "用于企业经营发展的专项贷款";
            case EMERGENCY: return "应急情况下的快速小额贷款";
            default: return "通用贷款产品";
        }
    }
    
    private LoanTerms calculateFinalTerms(LoanApplication application) {
        double interestRate = calculateInterestRate(application.getPlayerId(), 
            application.getLoanType(), application.getRequestedAmount(), application.getTermMonths());
        
        TermOption termOption = new TermOption(
            application.getTermMonths(), interestRate, application.getRequestedAmount()
        );
        
        return new LoanTerms(termOption);
    }
    
    private CollateralTypeInfo getCollateralTypeInfo(String collateralType) {
        // To be implemented - query from database
        return null;
    }
    
    // === Result Classes ===
    
    public static class EligibilityResult {
        private final UUID playerId;
        private final LoanType loanType;
        private final int creditScore;
        private final CreditGrade creditGrade;
        private final boolean creditScoreQualified;
        private final LoanStatusSummary loanStatus;
        private final boolean accountVerified;
        private boolean eligible = false;
        private List<String> recommendations = new ArrayList<>();
        
        public EligibilityResult(UUID playerId, LoanType loanType, int creditScore, 
                               CreditGrade creditGrade, boolean creditScoreQualified,
                               LoanStatusSummary loanStatus, boolean accountVerified) {
            this.playerId = playerId;
            this.loanType = loanType;
            this.creditScore = creditScore;
            this.creditGrade = creditGrade;
            this.creditScoreQualified = creditScoreQualified;
            this.loanStatus = loanStatus;
            this.accountVerified = accountVerified;
        }
        
        public static EligibilityResult error(UUID playerId, LoanType loanType, String message) {
            EligibilityResult result = new EligibilityResult(playerId, loanType, 0, 
                CreditGrade.F, false, new LoanStatusSummary(), false);
            result.setRecommendations(Arrays.asList(message));
            return result;
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public LoanType getLoanType() { return loanType; }
        public int getCreditScore() { return creditScore; }
        public CreditGrade getCreditGrade() { return creditGrade; }
        public boolean isCreditScoreQualified() { return creditScoreQualified; }
        public LoanStatusSummary getLoanStatus() { return loanStatus; }
        public boolean isAccountVerified() { return accountVerified; }
        public boolean isEligible() { return eligible; }
        public List<String> getRecommendations() { return recommendations; }
        
        public void setEligible(boolean eligible) { this.eligible = eligible; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }
    
    public static class LoanValidationResult {
        private final UUID playerId;
        private final LoanType loanType;
        private final double requestedAmount;
        private final int termMonths;
        private final boolean amountValid;
        private final boolean termValid;
        private final double maximumAmount;
        private final double interestRate;
        private final List<String> messages = new ArrayList<>();
        
        public LoanValidationResult(UUID playerId, LoanType loanType, double requestedAmount,
                                  int termMonths, boolean amountValid, boolean termValid,
                                  double maximumAmount, double interestRate) {
            this.playerId = playerId;
            this.loanType = loanType;
            this.requestedAmount = requestedAmount;
            this.termMonths = termMonths;
            this.amountValid = amountValid;
            this.termValid = termValid;
            this.maximumAmount = maximumAmount;
            this.interestRate = interestRate;
        }
        
        public static LoanValidationResult ineligible(UUID playerId, String message) {
            LoanValidationResult result = new LoanValidationResult(playerId, null, 0, 0, false, false, 0, 0);
            result.addMessage(message);
            return result;
        }
        
        public static LoanValidationResult error(UUID playerId, String message) {
            return ineligible(playerId, message);
        }
        
        public void addMessage(String message) { messages.add(message); }
        public boolean isValid() { return amountValid && termValid; }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public LoanType getLoanType() { return loanType; }
        public double getRequestedAmount() { return requestedAmount; }
        public int getTermMonths() { return termMonths; }
        public boolean isAmountValid() { return amountValid; }
        public boolean isTermValid() { return termValid; }
        public double getMaximumAmount() { return maximumAmount; }
        public double getInterestRate() { return interestRate; }
        public List<String> getMessages() { return messages; }
    }
    
    public static class LoanTypeResult {
        private final LoanType loanType;
        private final double amount;
        private final int termMonths;
        private final double interestRate;
        private final double processingFee;
        private final String description;
        private final List<String> requirements = new ArrayList<>();
        private final List<String> advantages = new ArrayList<>();
        private final List<String> risks = new ArrayList<>();
        
        public LoanTypeResult(LoanType loanType, double amount, int termMonths,
                            double interestRate, double processingFee, String description) {
            this.loanType = loanType;
            this.amount = amount;
            this.termMonths = termMonths;
            this.interestRate = interestRate;
            this.processingFee = processingFee;
            this.description = description;
        }
        
        public static LoanTypeResult error(LoanType loanType, String message) {
            LoanTypeResult result = new LoanTypeResult(loanType, 0, 0, 0, 0, message);
            return result;
        }
        
        public String getTotalCostDisplay() {
            return String.format("年利率: %.2f%% | 手续费: ¥%.2f", interestRate, processingFee);
        }
        
        // Getters and setters
        public LoanType getLoanType() { return loanType; }
        public double getAmount() { return amount; }
        public int getTermMonths() { return termMonths; }
        public double getInterestRate() { return interestRate; }
        public double getProcessingFee() { return processingFee; }
        public String getDescription() { return description; }
        public List<String> getRequirements() { return requirements; }
        public List<String> getAdvantages() { return advantages; }
        public List<String> getRisks() { return risks; }
        
        public void setRequirements(List<String> requirements) { this.requirements.addAll(requirements); }
        public void setAdvantages(List<String> advantages) { this.advantages.addAll(advantages); }
        public void setRisks(List<String> risks) { this.risks.addAll(risks); }
    }
    
    public static class CollateralAssessment {
        private final boolean required;
        private final double totalValue;
        private final Map<String, Double> assessedValues;
        private final Map<String, Double> discountRates;
        private final String assessmentMessage;
        private boolean error = false;
        private String errorMessage;
        
        public CollateralAssessment(boolean required, double totalValue,
                                  Map<String, Double> assessedValues,
                                  Map<String, Double> discountRates,
                                  String assessmentMessage) {
            this.required = required;
            this.totalValue = totalValue;
            this.assessedValues = assessedValues;
            this.discountRates = discountRates;
            this.assessmentMessage = assessmentMessage;
        }
        
        public static CollateralAssessment notRequired() {
            return new CollateralAssessment(false, 0, new HashMap<>(), new HashMap<>(), 
                "无需抵押物评估");
        }
        
        public static CollateralAssessment error(String message) {
            CollateralAssessment assessment = new CollateralAssessment(false, 0,
                new HashMap<>(), new HashMap<>(), "评估错误: " + message);
            assessment.error = true;
            assessment.errorMessage = message;
            return assessment;
        }
        
        // Getters
        public boolean isRequired() { return required; }
        public double getTotalValue() { return totalValue; }
        public Map<String, Double> getAssessedValues() { return assessedValues; }
        public Map<String, Double> getDiscountRates() { return discountRates; }
        public String getAssessmentMessage() { return assessmentMessage; }
        public boolean hasError() { return error; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class LoanApplicationResult {
        private final LoanApplication application;
        private final AutoApprovalResult autoApproval;
        private final boolean success;
        private final String errorMessage;
        
        public LoanApplicationResult(LoanApplication application, AutoApprovalResult autoApproval,
                                   boolean success, String errorMessage) {
            this.application = application;
            this.autoApproval = autoApproval;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static LoanApplicationResult success(LoanApplication application, AutoApprovalResult autoApproval) {
            return new LoanApplicationResult(application, autoApproval, true, null);
        }
        
        public static LoanApplicationResult error(UUID playerId, String message) {
            return new LoanApplicationResult(null, null, false, message);
        }
        
        // Getters
        public LoanApplication getApplication() { return application; }
        public AutoApprovalResult getAutoApproval() { return autoApproval; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getStatusMessage() {
            if (!success) return "申请失败: " + errorMessage;
            if (autoApproval.isApproved()) return "申请已成功提交并已自动批准!";
            return "申请已提交，等待人工审核";
        }
    }
    
    public static class AutoApprovalResult {
        private final boolean approved;
        private final String reason;
        
        public AutoApprovalResult(boolean approved, String reason) {
            this.approved = approved;
            this.reason = reason;
        }
        
        public static AutoApprovalResult approved(String reason) {
            return new AutoApprovalResult(true, reason);
        }
        
        public static AutoApprovalResult rejected(String reason) {
            return new AutoApprovalResult(false, reason);
        }
        
        // Getters
        public boolean isApproved() { return approved; }
        public String getReason() { return reason; }
    }
}
