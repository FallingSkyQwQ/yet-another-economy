package com.yae.api.bank;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.LanguageManager;
import com.yae.api.core.event.BankEvent;
import com.yae.api.core.event.YAEEvent;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * BankAccountManager类
 * 管理个人和组织账户的创建、查询、更新
 * 继承Service接口，集成YAE核心架构
 */
public class BankAccountManager implements Service {
    
    private final YAECore plugin;
    private final Logger logger;
    private final ServiceType serviceType;
    private final AtomicBoolean enabled;
    private final AtomicBoolean healthy;
    private final int priority;
    
    // 账户存储
    private final Map<UUID, BankAccount> accounts; // accountId -> BankAccount
    private final Map<UUID, Set<UUID>> ownerAccounts; // ownerId -> Set<accountId>
    private final Map<String, UUID> accountNumberIndex; // accountNumber -> accountId
    
    // Vault集成
    private Economy vaultEconomy;
    
    // 定时任务
    private ScheduledExecutorService scheduler;
    private static final java.util.concurrent.atomic.AtomicLong threadCounter = new java.util.concurrent.atomic.AtomicLong(0);
    
    // 配置
    private Configuration configuration;
    private LanguageManager languageManager;
    
    // 银行服务配置
    private boolean bankingEnabled;
    private int maxAccountsPerOwner;
    private BigDecimal defaultInterestRate;
    private BigDecimal earlyWithdrawalPenaltyRate;
    private long interestCalculationInterval; // 秒
    
    // 线程安全控制
    private final Object accountLock = new Object();
    
    public BankAccountManager(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.logger = plugin.getLogger();
        this.serviceType = ServiceType.BANK;
        this.enabled = new AtomicBoolean(false);
        this.healthy = new AtomicBoolean(true);
        this.priority = 200; // 较高的优先级
        
        this.accounts = new ConcurrentHashMap<>();
        this.ownerAccounts = new ConcurrentHashMap<>();
        this.accountNumberIndex = new ConcurrentHashMap<>();
    }
    
    @Override
    @NotNull
    public String getName() {
        return "BankAccountManager";
    }
    
    @Override
    @NotNull
    public ServiceType getType() {
        return serviceType;
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        // 银行服务依赖于数据库服务
        return serviceType == ServiceType.DATABASE;
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
        logger.info("Initializing BankAccountManager...");
        
        try {
            // 加载配置
            loadConfiguration();
            
            if (!bankingEnabled) {
                logger.info("Banking feature is disabled in configuration");
                return true; // 不启用但不认为是失败
            }
            
            // 初始化Vault集成
            if (!setupVaultIntegration()) {
                logger.warning("Vault integration failed, bank account balance operations will be limited");
            }
            
            // 从数据库加载账户数据
            loadAccountsFromDatabase();
            
            // 启动定时任务
            startScheduledTasks();
            
            enabled.set(true);
            healthy.set(true);
            
            logger.info("BankAccountManager initialized successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to initialize BankAccountManager: " + e.getMessage());
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public boolean reload() {
        logger.info("Reloading BankAccountManager...");
        
        try {
            // 重新加载配置
            loadConfiguration();
            
            // 如果银行功能被禁用，停止服务
            if (!bankingEnabled && enabled.get()) {
                shutdown();
                return true;
            }
            
            // 重新加载数据库数据
            accounts.clear();
            ownerAccounts.clear();
            accountNumberIndex.clear();
            loadAccountsFromDatabase();
            
            logger.info("BankAccountManager reloaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to reload BankAccountManager: " + e.getMessage());
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down BankAccountManager...");
        
        try {
            enabled.set(false);
            
            // 停止定时任务
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // 保存所有数据到数据库
            saveAllAccountsToDatabase();
            
            // 清理内存数据
            accounts.clear();
            ownerAccounts.clear();
            accountNumberIndex.clear();
            
            // 清理Vault引用
            vaultEconomy = null;
            
            logger.info("BankAccountManager shutdown completed");
            
        } catch (Exception e) {
            logger.severe("Error during BankAccountManager shutdown: " + e.getMessage());
        }
    }
    
    /**
     * 加载配置
     */
    private void loadConfiguration() {
        this.configuration = plugin.getMainConfiguration();
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        
        // 银行配置
        var bankingConfig = configuration.getFeatures().getBanking();
        this.bankingEnabled = bankingConfig.isEnabled();
        this.maxAccountsPerOwner = bankingConfig.getMaxAccountsPerOwner();
        
        // 默认利率和罚金率
        this.defaultInterestRate = BigDecimal.valueOf(bankingConfig.getDefaultInterestRate());
        this.earlyWithdrawalPenaltyRate = BigDecimal.valueOf(bankingConfig.getEarlyWithdrawalPenaltyRate());
        this.interestCalculationInterval = bankingConfig.getInterestCalculationInterval();
    }
    
    /**
     * 初始化Vault集成
     */
    private boolean setupVaultIntegration() {
        try {
            if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
                logger.warning("Vault plugin not found");
                return false;
            }
            
            net.milkbowl.vault.economy.Economy economy = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
            if (economy == null) {
                logger.warning("Vault economy provider not available");
                return false;
            }
            
            this.vaultEconomy = economy;
            logger.info("Vault integration established successfully");
            return true;
            
        } catch (Exception e) {
            logger.warning("Failed to setup Vault integration: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "BankAccountManager-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        
        // 利息计算任务 - 每小时执行一次
        scheduler.scheduleAtFixedRate(this::calculateAllInterests, 
            interestCalculationInterval, interestCalculationInterval, TimeUnit.SECONDS);
        
        // 定期存款到期检查任务 - 每天执行一次
        long oneDayInSeconds = 24 * 60 * 60;
        scheduler.scheduleAtFixedRate(this::checkMaturedDeposits, 
            oneDayInSeconds, oneDayInSeconds, TimeUnit.SECONDS);
        
        // 数据保存任务 - 每5分钟执行一次
        long fiveMinutesInSeconds = 5 * 60;
        scheduler.scheduleAtFixedRate(this::saveAllAccountsToDatabase, 
            fiveMinutesInSeconds, fiveMinutesInSeconds, TimeUnit.SECONDS);
        
        logger.info("Scheduled tasks started");
    }
    
    /**
     * 从数据库加载账户数据
     */
    private void loadAccountsFromDatabase() {
        // TODO: 实现数据库加载逻辑
        logger.info("Loading bank accounts from database...");
        // 这里应该调用数据库服务加载数据
        // 暂时创建一些测试账户
        createTestAccounts();
        logger.info("Loaded " + accounts.size() + " bank accounts");
    }
    
    /**
     * 保存所有账户到数据库
     */
    private void saveAllAccountsToDatabase() {
        if (!enabled.get()) {
            return;
        }
        
        try {
            logger.fine("Saving bank accounts to database...");
            synchronized (accountLock) {
                // TODO: 实现数据库保存逻辑
                // 这里应该调用数据库服务保存数据
            }
            logger.fine("Saved " + accounts.size() + " bank accounts");
        } catch (Exception e) {
            logger.severe("Failed to save bank accounts to database: " + e.getMessage());
            healthy.set(false);
        }
    }
    
    /**
     * 创建测试账户（用于开发测试）
     */
    private void createTestAccounts() {
        // 创建一些测试账户
        for (int i = 0; i < 5; i++) {
            UUID testOwnerId = UUID.randomUUID();
            try {
                BankAccount account = createAccount(testOwnerId, "PLAYER", BankAccount.AccountType.CHECKING);
                if (account != null) {
                    // 存入一些测试资金
                    account.deposit(BigDecimal.valueOf(1000 + Math.random() * 10000));
                }
            } catch (Exception e) {
                logger.warning("Failed to create test account: " + e.getMessage());
            }
        }
    }
    
    /**
     * 计算所有账户的利息
     */
    private void calculateAllInterests() {
        if (!enabled.get()) {
            return;
        }
        
        try {
            logger.fine("Calculating interests for all bank accounts...");
            
            synchronized (accountLock) {
                for (BankAccount account : accounts.values()) {
                    if (account.isActive()) {
                        calculateAccountInterest(account);
                    }
                }
            }
            
            logger.fine("Interest calculation completed");
        } catch (Exception e) {
            logger.severe("Error during interest calculation: " + e.getMessage());
            healthy.set(false);
        }
    }
    
    /**
     * 计算单个账户的利息
     */
    private void calculateAccountInterest(BankAccount account) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastCalc = account.getLastInterestCalculation();
            
            // 计算经过的天数
            long days = ChronoUnit.DAYS.between(lastCalc, now);
            if (days <= 0) {
                return;
            }
            
            // 计算日利率
            BigDecimal dailyRate = account.getInterestRate()
                .divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP);
            
            // 计算利息
            BigDecimal interest = account.getCurrentBalance()
                .multiply(dailyRate)
                .multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP);
            
            if (interest.compareTo(BigDecimal.ZERO) > 0) {
                // 记入利息收入
                boolean deposited = account.deposit(interest);
                if (deposited) {
                    account.setLastInterestCalculation(now);
                    
                    // 触发利息收入事件
                    emitBankEvent("interest-earned", account, interest);
                    
                    logger.fine(String.format("Interest calculated for account %s: %s days, %s interest", 
                        account.getAccountNumber(), days, interest));
                }
            }
            
        } catch (Exception e) {
            logger.warning("Failed to calculate interest for account " + account.getAccountNumber() + ": " + e.getMessage());
        }
    }
    
    /**
     * 检查到期的定期存款
     */
    private void checkMaturedDeposits() {
        if (!enabled.get()) {
            return;
        }
        
        try {
            logger.fine("Checking matured fixed deposits...");
            
            synchronized (accountLock) {
                for (BankAccount account : accounts.values()) {
                    if (!account.isActive()) {
                        continue;
                    }
                    
                    for (FixedDeposit deposit : account.getFixedDeposits().values()) {
                        if (deposit.isMatured() && deposit.getStatus() == FixedDeposit.DepositStatus.ACTIVE) {
                            // 处理到期的定期存款
                            handleMaturedDeposit(account, deposit);
                        }
                    }
                }
            }
            
            logger.fine("Matured deposits check completed");
        } catch (Exception e) {
            logger.severe("Error during matured deposits check: " + e.getMessage());
            healthy.set(false);
        }
    }
    
    /**
     * 处理到期的定期存款
     */
    private void handleMaturedDeposit(BankAccount account, FixedDeposit deposit) {
        try {
            // 计算最终利息
            deposit.calculateInterest();
            
            // 将定期存款金额转入活期账户
            BigDecimal depositAmount = deposit.getCurrentAmount();
            boolean success = account.deposit(depositAmount);
            
            if (success) {
                // 更新定期存款状态
                deposit.setStatus(FixedDeposit.DepositStatus.MATURED);
                
                // 从账户中移除定期存款
                account.removeFixedDeposit(deposit.getDepositId());
                
                // 触发事件
                emitBankEvent("deposit-matured", account, depositAmount);
                
                logger.info(String.format("Fixed deposit %s matured for account %s, amount: %s", 
                    deposit.getDepositNumber(), account.getAccountNumber(), depositAmount));
            }
            
        } catch (Exception e) {
            logger.warning("Failed to handle matured deposit " + deposit.getDepositNumber() + ": " + e.getMessage());
        }
    }
    
    /**
     * 创建银行账户
     */
    public BankAccount createAccount(@NotNull UUID ownerId, @NotNull String ownerType, 
                                   @NotNull BankAccount.AccountType accountType) {
        Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        Objects.requireNonNull(ownerType, "Owner type cannot be null");
        Objects.requireNonNull(accountType, "Account type cannot be null");
        
        if (!enabled.get()) {
            throw new IllegalStateException("Bank service is not enabled");
        }
        
        synchronized (accountLock) {
            // 检查账户数量限制
            Set<UUID> ownerAccountSet = ownerAccounts.get(ownerId);
            if (ownerAccountSet != null && ownerAccountSet.size() >= maxAccountsPerOwner) {
                throw new IllegalStateException("Maximum number of accounts reached for owner: " + maxAccountsPerOwner);
            }
            
            // 生成银行账户号码
            String accountNumber = generateAccountNumber(ownerType, accountType);
            
            // 创建新账户
            BankAccount account = new BankAccount(ownerId, ownerType, accountType, accountNumber);
            
            // 保存到内存
            accounts.put(account.getAccountId(), account);
            accountNumberIndex.put(accountNumber, account.getAccountId());
            
            ownerAccounts.computeIfAbsent(ownerId, k -> ConcurrentHashMap.newKeySet())
                        .add(account.getAccountId());
            
            // 保存到数据库
            saveAccountToDatabase(account);
            
            // 触发事件
            emitBankEvent("account-created", account, BigDecimal.ZERO);
            
            logger.info("Created bank account " + accountNumber + " for owner " + ownerId);
            return account;
        }
    }
    
    /**
     * 获取账户
     */
    @Nullable
    public BankAccount getAccount(@NotNull UUID accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        return accounts.get(accountId);
    }
    
    /**
     * 通过账号获取账户
     */
    @Nullable
    public BankAccount getAccountByNumber(@NotNull String accountNumber) {
        Objects.requireNonNull(accountNumber, "Account number cannot be null");
        UUID accountId = accountNumberIndex.get(accountNumber);
        return accountId != null ? accounts.get(accountId) : null;
    }
    
    /**
     * 获取所有者的所有账户
     */
    @NotNull
    public List<BankAccount> getOwnerAccounts(@NotNull UUID ownerId) {
        Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        
        Set<UUID> accountIds = ownerAccounts.get(ownerId);
        if (accountIds == null) {
            return Collections.emptyList();
        }
        
        return accountIds.stream()
            .map(accounts::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * 存款到Vault余额
     */
    public EconomyResponse depositToVault(@NotNull OfflinePlayer player, double amount) {
        if (vaultEconomy == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Vault economy not available");
        }
        
        return vaultEconomy.depositPlayer(player, amount);
    }
    
    /**
     * 从Vault余额取款
     */
    public EconomyResponse withdrawFromVault(@NotNull OfflinePlayer player, double amount) {
        if (vaultEconomy == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Vault economy not available");
        }
        
        return vaultEconomy.withdrawPlayer(player, amount);
    }
    
    /**
     * 获取Vault余额
     */
    public double getVaultBalance(@NotNull OfflinePlayer player) {
        if (vaultEconomy == null) {
            return 0.0;
        }
        
        return vaultEconomy.getBalance(player);
    }
    
    /**
     * 创建定期存款
     */
    public FixedDeposit createFixedDeposit(@NotNull UUID accountId, @NotNull BigDecimal amount,
                                         @NotNull FixedDeposit.DepositTerm term) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(term, "Term cannot be null");
        
        if (!enabled.get()) {
            throw new IllegalStateException("Bank service is not enabled");
        }
        
        BankAccount account = getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        
        if (!account.isActive()) {
            throw new IllegalStateException("Account is not active: " + accountId);
        }
        
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance in account");
        }
        
        synchronized (accountLock) {
            // 从活期账户扣除金额
            boolean withdrawn = account.withdraw(amount);
            if (!withdrawn) {
                throw new IllegalStateException("Failed to withdraw from account");
            }
            
            // 创建定期存款（使用配置中的利率）
            var bankingConfig = configuration.getFeatures().getBanking();
            double termRate = bankingConfig.getTermInterestRate(term.getMonths());
            
            FixedDeposit deposit = new FixedDeposit(accountId, amount, term) {
                @Override
                public double getInterestRate() {
                    return termRate;
                }
            };
            
            // 添加到账户
            account.addFixedDeposit(deposit);
            
            // 保存到数据库
            saveFixedDepositToDatabase(deposit);
            saveAccountToDatabase(account);
            
            // 触发事件
            emitBankEvent("fixed-deposit-created", account, amount);
            
            logger.info(String.format("Created fixed deposit %s for account %s, amount: %s, term: %s", 
                deposit.getDepositNumber(), account.getAccountNumber(), amount, term.getDisplayName()));
            
            return deposit;
        }
    }
    
    /**
     * 生成银行账户号码
     */
    private String generateAccountNumber(String ownerType, BankAccount.AccountType accountType) {
        String prefix = ownerType.equals("PLAYER") ? "P" : "O";
        String typeCode = switch (accountType) {
            case CHECKING -> "1";
            case SAVINGS -> "2";
            case FIXED_DEPOSIT -> "3";
            case LOAN -> "4";
            default -> "0";
        };
        
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(6);
        String random = String.valueOf((int)(Math.random() * 1000)).substring(0, 3);
        
        return prefix + typeCode + timestamp + random;
    }
    
    /**
     * 更新账户缓存中的账户信息
     */
    public void updateAccountInCache(BankAccount account) {
        if (account == null || !enabled.get()) {
            return;
        }
        
        synchronized (accountLock) {
            accounts.put(account.getAccountId(), account);
        }
    }
    
    /**
     * 主动设置活跃账户
     */
    public void updateActiveAccount(UUID ownerId, UUID accountId) {
        if (ownerId == null || accountId == null) {
            return;
        }
        
        synchronized (accountLock) {
            // 这里可以实现缓存逻辑，当前版本主要依赖数据库查询
            BankAccount account = accounts.get(accountId);
            if (account != null && account.getOwnerId().equals(ownerId)) {
                // 已经是正确的账户，无需处理
            }
        }
    }
    
    /**
     * 保存账户到数据库
     */
    private void saveAccountToDatabase(BankAccount account) {
        // TODO: 实现数据库保存逻辑
        // 这里应该调用数据库服务保存数据
    }
    
    /**
     * 保存定期存款到数据库
     */
    private void saveFixedDepositToDatabase(FixedDeposit deposit) {
        // TODO: 实现数据库保存逻辑
        // 这里应该调用数据库服务保存数据
    }
    
    /**
     * 触发银行事件
     */
    private void emitBankEvent(String eventType, BankAccount account, BigDecimal amount) {
        try {
            BankEvent event = new BankEvent(eventType, this, account, amount);
            plugin.emitEvent(event);
        } catch (Exception e) {
            logger.warning("Failed to emit bank event: " + e.getMessage());
        }
    }
    
    // 服务配置接口（实现Service接口的setter方法）
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
