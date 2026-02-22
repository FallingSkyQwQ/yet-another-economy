package com.yae.api.bank;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.LanguageManager;
import com.yae.api.core.event.BankEvent;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 存款服务类
 * 提供存款业务逻辑，包括活期和定期存款
 */
public class DepositService implements Service {
    
    /**
     * 存款错误类型枚举
     */
    public enum DepositError {
        INVALID_AMOUNT,
        AMOUNT_TOO_SMALL,
        AMOUNT_TOO_LARGE,
        NO_ACTIVE_ACCOUNT,
        INSUFFICIENT_BALANCE,
        DEPOSIT_FAILED,
        INTERNAL_ERROR;
    }
    
    private final YAECore plugin;
    private final Logger logger;
    private final ServiceType serviceType;
    private final AtomicBoolean enabled;
    private final AtomicBoolean healthy;
    private final int priority;
    
    // 依赖服务
    private BankAccountManager bankAccountManager;
    private InterestCalculator interestCalculator;
    
    // 配置
    private Configuration configuration;
    private LanguageManager languageManager;
    
    // 存款配置
    private boolean depositsEnabled;
    private Map<FixedDeposit.DepositTerm, Double> termInterestRates;
    private double currentInterestRate;
    private BigDecimal minDepositAmount;
    private BigDecimal maxDepositAmount;
    private double earlyWithdrawalPenalty;
    
    // 存款记录
    private final Map<UUID, DepositRecord> pendingDeposits;
    private final Map<UUID, DepositRecord> processingDeposits;
    
    public DepositService(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.logger = plugin.getLogger();
        this.serviceType = ServiceType.BANK; // 与BankAccountManager同一服务类型
        this.enabled = new AtomicBoolean(false);
        this.healthy = new AtomicBoolean(true);
        this.priority = 210; // 比BankAccountManager稍高
        
        this.pendingDeposits = new ConcurrentHashMap<>();
        this.processingDeposits = new ConcurrentHashMap<>();
    }
    
    @Override
    @NotNull
    public String getName() {
        return "DepositService";
    }
    
    @Override
    @NotNull
    public ServiceType getType() {
        return serviceType;
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        // 依赖于BankAccountManager
        return serviceType == ServiceType.BANK;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled.get();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }
    
    @Override
    public boolean isHealthy() {
        return healthy.get();
    }
    
    @Override
    public boolean initialize() {
        logger.info("Initializing DepositService...");
        
        try {
            // 加载配置
            loadConfiguration();
            
            if (!depositsEnabled) {
                logger.info("Deposit service is disabled in configuration");
                return true;
            }
            
            // 获取依赖服务
            bankAccountManager = (BankAccountManager) plugin.getService(ServiceType.BANK);
            if (bankAccountManager == null || !bankAccountManager.isEnabled()) {
                logger.severe("BankAccountManager is not available");
                return false;
            }
            
            // 初始化利息计算器
            interestCalculator = new InterestCalculator(plugin);
            if (!interestCalculator.initialize()) {
                logger.severe("Failed to initialize InterestCalculator");
                return false;
            }
            
            enabled.set(true);
            healthy.set(true);
            
            logger.info("DepositService initialized successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to initialize DepositService: " + e.getMessage());
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public boolean reload() {
        logger.info("Reloading DepositService...");
        
        try {
            // 重新加载配置
            loadConfiguration();
            
            // 重新加载利息计算器
            if (interestCalculator != null) {
                interestCalculator.reload();
            }
            
            logger.info("DepositService reloaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to reload DepositService: " + e.getMessage());
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down DepositService...");
        try {
            enabled.set(false);
            
            // 关闭利息计算器
            if (interestCalculator != null) {
                interestCalculator.shutdown();
            }
            
            // 清除待处理存款
            pendingDeposits.clear();
            processingDeposits.clear();
            
            logger.info("DepositService shutdown completed");
            
        } catch (Exception e) {
            logger.severe("Error during DepositService shutdown: " + e.getMessage());
        }
    }
    
    /**
     * 加载配置
     */
    private void loadConfiguration() {
        this.configuration = plugin.getMainConfiguration();
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        
        // 存款配置
        var bankingConfig = configuration.getFeatures().getBanking();
        this.depositsEnabled = bankingConfig.isEnabled() && bankingConfig.getDeposits().isEnabled();
        this.currentInterestRate = bankingConfig.getDefaultInterestRate();
        this.minDepositAmount = BigDecimal.valueOf(bankingConfig.getDeposits().getMinDepositAmount());
        this.maxDepositAmount = BigDecimal.valueOf(bankingConfig.getDeposits().getMaxDepositAmount());
        this.earlyWithdrawalPenalty = bankingConfig.getEarlyWithdrawalPenaltyRate();
        
        // 定期存款利率
        this.termInterestRates = new EnumMap<>(FixedDeposit.DepositTerm.class);
        for (FixedDeposit.DepositTerm term : FixedDeposit.DepositTerm.values()) {
            this.termInterestRates.put(term, bankingConfig.getTermInterestRate(term.getMonths()));
        }
    }
    
    /**
     * 活期存款
     */
    public CompletableFuture<DepositResult> depositCurrent(
            @NotNull UUID playerId, @NotNull BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 金额验证
                DepositResult validationResult = validateDepositAmount(amount);
                if (!validationResult.isSuccess()) {
                    return validationResult;
                }
                
                // 检查是否有活跃账户
                List<BankAccount> playerAccounts = bankAccountManager.getOwnerAccounts(playerId);
                BankAccount targetAccount = playerAccounts.stream()
                        .filter(BankAccount::isActive)
                        .findFirst()
                        .orElse(null);
                
                if (targetAccount == null) {
                    // 创建新账户
                    targetAccount = bankAccountManager.createAccount(playerId, "PLAYER", BankAccount.AccountType.CHECKING);
                    if (targetAccount == null) {
                        return DepositResult.failed(DepositError.NO_ACTIVE_ACCOUNT,
                                "Failed to create new account");
                    }
                }
                
                // 执行存款
                boolean success = targetAccount.deposit(amount);
                if (!success) {
                    return DepositResult.failed(DepositError.DEPOSIT_FAILED,
                            "Failed to deposit to account");
                }
                
                // 更新银行管理器中的账户
                bankAccountManager.updateAccountInCache(targetAccount);
                
                // 记录存款
                DepositRecord record = new DepositRecord(
                        targetAccount.getAccountId(),
                        playerId,
                        DepositRecord.DepositType.CURRENT,
                        amount,
                        BigDecimal.ZERO, // 利息为活期利息
                        LocalDateTime.now(),
                        DepositRecord.DepositStatus.COMPLETED
                );
                
                // 触发事件
                BankEvent event = new BankEvent("current-deposit-completed", this, targetAccount, amount);
                plugin.emitEvent(event);
                
                logger.info(String.format("Current deposit completed: player=%s, account=%s, amount=%s", 
                        playerId, targetAccount.getAccountNumber(), amount));
                
                return DepositResult.success(targetAccount.getAccountId(), targetAccount.getAccountNumber(), 
                        amount, targetAccount.getCurrentBalance());
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing current deposit", e);
                return DepositResult.failed(DepositError.INTERNAL_ERROR,
                        "Failed to process deposit: " + e.getMessage());
            }
        });
    }
    
    /**
     * 定期存款创建
     * 支持自定义期限（以天为单位）
     */
    public CompletableFuture<DepositResult> depositFixed(
            @NotNull UUID playerId, @NotNull BigDecimal amount, @NotNull FixedDeposit.DepositTerm term) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 金额验证
                DepositResult validationResult = validateDepositAmount(amount);
                if (!validationResult.isSuccess()) {
                    return validationResult;
                }
                
                // 获取账户
                List<BankAccount> playerAccounts = bankAccountManager.getOwnerAccounts(playerId);
                BankAccount sourceAccount = playerAccounts.stream()
                        .filter(BankAccount::isActive)
                        .findFirst()
                        .orElse(null);
                
                if (sourceAccount == null) {
                    return DepositResult.failed(DepositError.NO_ACTIVE_ACCOUNT,
                            "No active account found");
                }
                
                // 检查余额
                if (sourceAccount.getAvailableBalance().compareTo(amount) < 0) {
                    return DepositResult.failed(DepositError.INSUFFICIENT_BALANCE,
                            "Insufficient balance");
                }
                
                // 创建定期存款
                FixedDeposit deposit = bankAccountManager.createFixedDeposit(
                        sourceAccount.getAccountId(), amount, term);
                
                // 记录存款
                DepositRecord record = new DepositRecord(
                        sourceAccount.getAccountId(),
                        playerId,
                        DepositRecord.DepositType.FIXED,
                        amount,
                        BigDecimal.valueOf(deposit.getInterestRate()),
                        LocalDateTime.now(),
                        DepositRecord.DepositStatus.PENDING
                );
                pendingDeposits.put(deposit.getDepositId(), record);
                
                // 触发事件
                BankEvent event = new BankEvent("fixed-deposit-created", this, sourceAccount, amount);
                plugin.emitEvent(event);
                
                logger.info(String.format("Fixed deposit created: player=%s, account=%s, amount=%s, term=%s", 
                        playerId, sourceAccount.getAccountNumber(), amount, term.getDisplayName()));
                
                return DepositResult.fixedSuccess(
                        sourceAccount.getAccountId(), 
                        sourceAccount.getAccountNumber(),
                        deposit.getDepositId(),
                        deposit.getDepositNumber(),
                        amount,
                        deposit.getInterestRate(),
                        depositTermToDays(term),
                        deposit.getCurrentAmount(),
                        deposit.getMaturityDate()
                );
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing fixed deposit", e);
                return DepositResult.failed(DepositError.INTERNAL_ERROR,
                        "Failed to process fixed deposit: " + e.getMessage());
            }
        });
    }
    
    /**
     * 根据存期获取利率
     */
    public double getTermInterestRate(FixedDeposit.DepositTerm term) {
        return termInterestRates.getOrDefault(term, currentInterestRate);
    }
    
    /**
     * 获取活期存款利率
     */
    public double getCurrentInterestRate() {
        return currentInterestRate;
    }
    
    /**
     * 计算定期存款的到期收益
     */
    public BigDecimal calculateFixedDepositReturn(BigDecimal principal, BigDecimal annualRate, int days, int compoundFrequencyPerYear) {
        // Convert annual rate to decimal
        double rate = annualRate.doubleValue() / 100.0;
        double time = days / 365.0;
        
        // Calculate compound interest
        double amount = principal.doubleValue() * Math.pow(1 + rate / compoundFrequencyPerYear, compoundFrequencyPerYear * time);
        
        return BigDecimal.valueOf(amount);
    }
    
    public BigDecimal calculateFixedDepositReturn(BigDecimal principal, FixedDeposit.DepositTerm term) {
        return interestCalculator.calculateCompoundInterest(
                principal, 
                BigDecimal.valueOf(getTermInterestRate(term)), 
                depositTermToDays(term),
                12 // 按月计息
        );
    }
    
    /**
     * 计算活期存款利息
     */
    public BigDecimal calculateCurrentInterest(BigDecimal principal, int days) {
        return interestCalculator.calculateSimpleInterest(
                principal,
                BigDecimal.valueOf(currentInterestRate),
                days
        );
    }
    
    /**
     * 主动存期转换为天数
     */
    private int depositTermToDays(FixedDeposit.DepositTerm term) {
        switch (term) {
            case THREE_MONTHS: return 90;
            case SIX_MONTHS: return 180;
            case ONE_YEAR: return 365;
            case TWO_YEARS: return 730;
            case THREE_YEARS: return 1095;
            case FIVE_YEARS: return 1825;
            default: return 365;
        }
    }
    
    /**
     * 验证存款金额
     */
    private DepositResult validateDepositAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return DepositResult.failed(DepositError.INVALID_AMOUNT,
                    "Deposit amount must be positive");
        }
        
        if (amount.compareTo(minDepositAmount) < 0) {
            return DepositResult.failed(DepositError.AMOUNT_TOO_SMALL,
                    String.format("Minimum deposit amount is %s", minDepositAmount));
        }
        
        if (amount.compareTo(maxDepositAmount) > 0) {
            return DepositResult.failed(DepositError.AMOUNT_TOO_LARGE,
                    String.format("Maximum deposit amount is %s", maxDepositAmount));
        }
        
        return DepositResult.success(null, null, amount, null);
    }
    
    /**
     * 存款结果类
     */
    public static class DepositResult {
        private final boolean success;
        private final DepositError error;
        private final String errorMessage;
        private final UUID accountId;
        private final String accountNumber;
        private final BigDecimal depositAmount;
        private final BigDecimal finalBalance;
        private final UUID depositId;
        private final String depositNumber;
        private final double interestRate;
        private final int daysToMaturity;
        private final BigDecimal maturityAmount;
        private final LocalDateTime maturityDate;
        private final DepositRecord.DepositType depositType;
        
        private DepositResult(boolean success, DepositError error, String errorMessage,
                             UUID accountId, String accountNumber, BigDecimal depositAmount, 
                             BigDecimal finalBalance, UUID depositId, String depositNumber,
                             double interestRate, int daysToMaturity, BigDecimal maturityAmount,
                             LocalDateTime maturityDate, DepositRecord.DepositType depositType) {
            this.success = success;
            this.error = error;
            this.errorMessage = errorMessage;
            this.accountId = accountId;
            this.accountNumber = accountNumber;
            this.depositAmount = depositAmount;
            this.finalBalance = finalBalance;
            this.depositId = depositId;
            this.depositNumber = depositNumber;
            this.interestRate = interestRate;
            this.daysToMaturity = daysToMaturity;
            this.maturityAmount = maturityAmount;
            this.maturityDate = maturityDate;
            this.depositType = depositType;
        }
        
        public static DepositResult success(UUID accountId, String accountNumber, 
                                           BigDecimal depositAmount, BigDecimal finalBalance) {
            return new DepositResult(true, null, null, accountId, accountNumber, 
                    depositAmount, finalBalance, null, null, 0, 0, null, null, 
                    DepositRecord.DepositType.CURRENT);
        }
        
        public static DepositResult fixedSuccess(UUID accountId, String accountNumber,
                                                 UUID depositId, String depositNumber,
                                                 BigDecimal depositAmount, double interestRate,
                                                 int daysToMaturity, BigDecimal maturityAmount,
                                                 LocalDateTime maturityDate) {
            return new DepositResult(true, null, null, accountId, accountNumber, 
                    depositAmount, null, depositId, depositNumber, interestRate, 
                    daysToMaturity, maturityAmount, maturityDate, DepositRecord.DepositType.FIXED);
        }
        
        public static DepositResult failed(DepositError error, String errorMessage) {
            return new DepositResult(false, error, errorMessage, null, null, null, 
                    null, null, null, 0, 0, null, null, null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public DepositError getError() { return error; }
        public String getErrorMessage() { return errorMessage; }
        public UUID getAccountId() { return accountId; }
        public String getAccountNumber() { return accountNumber; }
        public BigDecimal getDepositAmount() { return depositAmount; }
        public BigDecimal getFinalBalance() { return finalBalance; }
        public UUID getDepositId() { return depositId; }
        public String getDepositNumber() { return depositNumber; }
        public double getInterestRate() { return interestRate; }
        public int getDaysToMaturity() { return daysToMaturity; }
        public BigDecimal getMaturityAmount() { return maturityAmount; }
        public LocalDateTime getMaturityDate() { return maturityDate; }
    }
    
    /**
     * 存款记录类
     */
    public static class DepositRecord {
        private final UUID depositId;
        private final UUID accountId;
        private final UUID playerId;
        private final DepositType depositType;
        private final BigDecimal amount;
        private final BigDecimal interestRate;
        private final LocalDateTime timestamp;
        private DepositStatus status;
        
        public DepositRecord(UUID accountId, UUID playerId, DepositType depositType,
                            BigDecimal amount, BigDecimal interestRate,
                            LocalDateTime timestamp, DepositStatus status) {
            this.depositId = UUID.randomUUID();
            this.accountId = accountId;
            this.playerId = playerId;
            this.depositType = depositType;
            this.amount = amount;
            this.interestRate = interestRate;
            this.timestamp = timestamp;
            this.status = status;
        }
        
        public enum DepositType {
            CURRENT("活期"),
            FIXED("定期");
            
            private final String displayName;
            
            DepositType(String displayName) {
                this.displayName = displayName;
            }
            
            public String getDisplayName() {
                return displayName;
            }
        }
        
        public enum DepositStatus {
            PENDING("待处理"),
            PROCESSING("处理中"),
            COMPLETED("已完成"),
            FAILED("已失败"),
            CANCELLED("已取消");
            
            private final String displayName;
            
            DepositStatus(String displayName) {
                this.displayName = displayName;
            }
            
            public String getDisplayName() {
                return displayName;
            }
        }
        
        // Getters and setters
        public UUID getDepositId() { return depositId; }
        public UUID getAccountId() { return accountId; }
        public UUID getPlayerId() { return playerId; }
        public DepositType getDepositType() { return depositType; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getInterestRate() { return interestRate; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public DepositStatus getStatus() { return status; }
        public void setStatus(DepositStatus status) { this.status = status; }
    }
    
    // 服务配置接口
    private com.yae.api.core.ServiceConfig config;
    
    @Override
    public com.yae.api.core.ServiceConfig getConfig() {
        return config;
    }
    
    @Override
    public void setConfig(com.yae.api.core.ServiceConfig config) {
        this.config = config;
    }
}
