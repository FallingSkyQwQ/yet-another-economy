package com.yae.api.bank;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 银行账户实体类
 * 管理个人和组织账户的创建、查询、更新
 */
public class BankAccount {
    
    /**
     * 账户类型枚举
     */
    public enum AccountType {
        CHECKING("活期账户"),
        SAVINGS("储蓄账户"),
        FIXED_DEPOSIT("定期存款"),
        LOAN("贷款账户");
        
        private final String displayName;
        
        AccountType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 账户状态枚举
     */
    public enum AccountStatus {
        ACTIVE("正常"),
        FROZEN("冻结"),
        CLOSED("已关闭"),
        SUSPENDED("暂停");
        
        private final String displayName;
        
        AccountStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 基本账户信息
    private final UUID accountId;
    private final UUID ownerId; // 个人或组织的UUID
    private final String ownerType; // "PLAYER" 或 "ORGANIZATION"
    private final String accountNumber; // 银行账号
    private final AccountType accountType;
    private AccountStatus status;
    
    // 余额信息
    private BigDecimal currentBalance; // 活期余额
    private BigDecimal availableBalance; // 可用余额（考虑冻结金额）
    private BigDecimal frozenAmount; // 冻结金额
    
    // 定期存款列表
    private final Map<UUID, FixedDeposit> fixedDeposits;
    
    // 信用评分
    private int creditScore; // 300-850分
    
    // 利息相关
    private BigDecimal interestRate; // 利率
    private LocalDateTime lastInterestCalculation; // 上次利息计算时间
    
    // 时间戳
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 账户名称
    private String accountName;
    
    // 构造方法
    public BankAccount(@NotNull UUID ownerId, @NotNull String ownerType, 
                      @NotNull AccountType accountType, @NotNull String accountNumber) {
        this.accountId = UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        this.ownerType = Objects.requireNonNull(ownerType, "Owner type cannot be null");
        this.accountType = Objects.requireNonNull(accountType, "Account type cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        
        this.status = AccountStatus.ACTIVE;
        this.currentBalance = BigDecimal.ZERO;
        this.availableBalance = BigDecimal.ZERO;
        this.frozenAmount = BigDecimal.ZERO;
        this.fixedDeposits = new ConcurrentHashMap<>();
        this.creditScore = 650; // 默认信用评分
        this.interestRate = BigDecimal.valueOf(0.01); // 默认1%利率
        this.lastInterestCalculation = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.accountName = accountType.getDisplayName() + " - " + accountNumber.substring(accountNumber.length() - 4);
    }
    
    // 完整的构造方法（用于数据库重建）
    public BankAccount(@NotNull UUID accountId, @NotNull UUID ownerId, @NotNull String ownerType,
                      @NotNull String accountNumber, @NotNull AccountType accountType,
                      @NotNull AccountStatus status, @NotNull BigDecimal currentBalance,
                      @NotNull BigDecimal availableBalance, @NotNull BigDecimal frozenAmount,
                      int creditScore, @NotNull BigDecimal interestRate,
                      @NotNull LocalDateTime lastInterestCalculation,
                      @NotNull LocalDateTime createdAt, @NotNull LocalDateTime updatedAt,
                      @Nullable String accountName) {
        this.accountId = Objects.requireNonNull(accountId, "Account ID cannot be null");
        this.ownerId = Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        this.ownerType = Objects.requireNonNull(ownerType, "Owner type cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        this.accountType = Objects.requireNonNull(accountType, "Account type cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.currentBalance = Objects.requireNonNull(currentBalance, "Current balance cannot be null");
        this.availableBalance = Objects.requireNonNull(availableBalance, "Available balance cannot be null");
        this.frozenAmount = Objects.requireNonNull(frozenAmount, "Frozen amount cannot be null");
        this.creditScore = creditScore;
        this.interestRate = Objects.requireNonNull(interestRate, "Interest rate cannot be null");
        this.lastInterestCalculation = Objects.requireNonNull(lastInterestCalculation, "Last interest calculation cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
        this.accountName = accountName != null ? accountName : accountType.getDisplayName() + " - " + accountNumber.substring(accountNumber.length() - 4);
        this.fixedDeposits = new ConcurrentHashMap<>();
    }
    
    // Getter方法
    public UUID getAccountId() {
        return accountId;
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public String getOwnerType() {
        return ownerType;
    }
    
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public AccountType getAccountType() {
        return accountType;
    }
    
    public AccountStatus getStatus() {
        return status;
    }
    
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }
    
    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
    
    public BigDecimal getFrozenAmount() {
        return frozenAmount;
    }
    
    public Map<UUID, FixedDeposit> getFixedDeposits() {
        return Collections.unmodifiableMap(fixedDeposits);
    }
    
    public int getCreditScore() {
        return creditScore;
    }
    
    public BigDecimal getInterestRate() {
        return interestRate;
    }
    
    public LocalDateTime getLastInterestCalculation() {
        return lastInterestCalculation;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public String getAccountName() {
        return accountName;
    }
    
    // Setter方法（线程安全）
    public synchronized void setStatus(AccountStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    
    public synchronized void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
        this.updatedAt = LocalDateTime.now();
    }
    
    public synchronized void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
        this.updatedAt = LocalDateTime.now();
    }
    
    public synchronized void setFrozenAmount(BigDecimal frozenAmount) {
        this.frozenAmount = frozenAmount;
        this.updatedAt = LocalDateTime.now();
    }
    
    public synchronized void setCreditScore(int creditScore) {
        this.creditScore = Math.max(300, Math.min(850, creditScore));
        this.updatedAt = LocalDateTime.now();
    }
    
    public synchronized void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
        this.updatedAt = LocalDateTime.now();
    }
    
    public synchronized void setLastInterestCalculation(LocalDateTime lastInterestCalculation) {
        this.lastInterestCalculation = lastInterestCalculation;
        this.updatedAt = LocalDateTime.now();
    }
    
    public synchronized void setAccountName(String accountName) {
        this.accountName = accountName;
        this.updatedAt = LocalDateTime.now();
    }
    
    // 业务方法
    
    /**
     * 检查账户是否活跃
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
    
    /**
     * 检查账户是否可以进行交易
     */
    public boolean canTransact() {
        return isActive() && availableBalance.compareTo(BigDecimal.ZERO) >= 0;
    }
    
    /**
     * 存款
     */
    public synchronized boolean deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (!isActive()) {
            return false;
        }
        
        this.currentBalance = this.currentBalance.add(amount);
        this.availableBalance = this.availableBalance.add(amount);
        this.updatedAt = LocalDateTime.now();
        return true;
    }
    
    /**
     * 取款
     */
    public synchronized boolean withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (!canTransact()) {
            return false;
        }
        
        if (availableBalance.compareTo(amount) < 0) {
            return false;
        }
        
        this.currentBalance = this.currentBalance.subtract(amount);
        this.availableBalance = this.availableBalance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
        return true;
    }
    
    /**
     * 冻结金额
     */
    public synchronized boolean freezeAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (availableBalance.compareTo(amount) < 0) {
            return false;
        }
        
        this.frozenAmount = this.frozenAmount.add(amount);
        this.availableBalance = this.availableBalance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
        return true;
    }
    
    /**
     * 解冻金额
     */
    public synchronized boolean unfreezeAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (frozenAmount.compareTo(amount) < 0) {
            return false;
        }
        
        this.frozenAmount = this.frozenAmount.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
        this.updatedAt = LocalDateTime.now();
        return true;
    }
    
    /**
     * 添加定期存款
     */
    public synchronized void addFixedDeposit(FixedDeposit deposit) {
        Objects.requireNonNull(deposit, "Fixed deposit cannot be null");
        this.fixedDeposits.put(deposit.getDepositId(), deposit);
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 移除定期存款
     */
    public synchronized FixedDeposit removeFixedDeposit(UUID depositId) {
        FixedDeposit removed = this.fixedDeposits.remove(depositId);
        if (removed != null) {
            this.updatedAt = LocalDateTime.now();
        }
        return removed;
    }
    
    /**
     * 获取定期存款总额
     */
    public BigDecimal getTotalFixedDepositAmount() {
        return fixedDeposits.values().stream()
            .map(FixedDeposit::getPrincipal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 计算总余额（活期+定期）
     */
    public BigDecimal getTotalBalance() {
        return currentBalance.add(getTotalFixedDepositAmount());
    }
    
    /**
     * 冻结账户
     */
    public synchronized void freeze() {
        if (status == AccountStatus.ACTIVE) {
            this.status = AccountStatus.FROZEN;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 解冻账户
     */
    public synchronized void unfreeze() {
        if (status == AccountStatus.FROZEN) {
            this.status = AccountStatus.ACTIVE;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 关闭账户
     */
    public synchronized void close() {
        if (status != AccountStatus.CLOSED) {
            this.status = AccountStatus.CLOSED;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    @Override
    public String toString() {
        return String.format("BankAccount{id=%s, number=%s, type=%s, status=%s, balance=%s, owner=%s}",
            accountId, accountNumber, accountType, status, currentBalance, ownerId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankAccount that = (BankAccount) o;
        return Objects.equals(accountId, that.accountId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }
}
