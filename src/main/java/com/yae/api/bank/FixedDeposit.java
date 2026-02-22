package com.yae.api.bank;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * 定期存款实体类
 * 管理定期存款的创建、到期、利息计算等
 */
public class FixedDeposit {
    
    /**
     * 定期存款状态枚举
     */
    public enum DepositStatus {
        ACTIVE("正常"),
        MATURED("已到期"),
        WITHDRAWN("已支取"),
        CLOSED("已关闭");
        
        private final String displayName;
        
        DepositStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 定期存期枚举
     */
    public enum DepositTerm {
        THREE_MONTHS(3, "3个月", 0.0125),     // 1.25%年利率
        SIX_MONTHS(6, "6个月", 0.015),       // 1.5%年利率
        ONE_YEAR(12, "1年", 0.02),           // 2%年利率
        TWO_YEARS(24, "2年", 0.025),         // 2.5%年利率
        THREE_YEARS(36, "3年", 0.03),        // 3%年利率
        FIVE_YEARS(60, "5年", 0.035);        // 3.5%年利率
        
        private final int months;
        private final String displayName;
        private final double annualInterestRate;
        
        DepositTerm(int months, String displayName, double annualInterestRate) {
            this.months = months;
            this.displayName = displayName;
            this.annualInterestRate = annualInterestRate;
        }
        
        public int getMonths() {
            return months;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public double getAnnualInterestRate() {
            return annualInterestRate;
        }
        
        public static DepositTerm fromMonths(int months) {
            for (DepositTerm term : values()) {
                if (term.months == months) {
                    return term;
                }
            }
            return ONE_YEAR; // 默认1年
        }
    }
    
    // 基本存款信息
    private final UUID depositId;
    private final UUID accountId; // 关联的银行账户
    private final String depositNumber; // 定期存款编号
    private DepositStatus status;
    
    // 存款详情
    private final BigDecimal principal; // 本金
    private BigDecimal currentAmount; // 当前金额（本金+利息）
    private final DepositTerm term; // 存期
    private final double interestRate; // 年利率
    
    // 时间信息
    private final LocalDateTime createdAt; // 创建时间
    private final LocalDateTime maturityDate; // 到期时间
    private LocalDateTime lastInterestCalculation; // 上次利息计算时间
    private LocalDateTime closedAt; // 关闭时间
    
    // 构造函数
    public FixedDeposit(@NotNull UUID accountId, @NotNull BigDecimal principal, 
                       @NotNull DepositTerm term) {
        this.depositId = UUID.randomUUID();
        this.accountId = Objects.requireNonNull(accountId, "Account ID cannot be null");
        this.depositNumber = generateDepositNumber();
        this.status = DepositStatus.ACTIVE;
        this.principal = Objects.requireNonNull(principal, "Principal cannot be null");
        this.currentAmount = principal;
        this.term = Objects.requireNonNull(term, "Term cannot be null");
        this.interestRate = term.getAnnualInterestRate();
        this.createdAt = LocalDateTime.now();
        this.maturityDate = createdAt.plusMonths(term.getMonths());
        this.lastInterestCalculation = createdAt;
        this.closedAt = null;
    }
    
    // 完整的构造方法（用于数据库重建）
    public FixedDeposit(@NotNull UUID depositId, @NotNull UUID accountId,
                       @NotNull String depositNumber, @NotNull DepositStatus status,
                       @NotNull BigDecimal principal, @NotNull BigDecimal currentAmount,
                       @NotNull DepositTerm term, double interestRate,
                       @NotNull LocalDateTime createdAt, @NotNull LocalDateTime maturityDate,
                       @NotNull LocalDateTime lastInterestCalculation,
                       @Nullable LocalDateTime closedAt) {
        this.depositId = Objects.requireNonNull(depositId, "Deposit ID cannot be null");
        this.accountId = Objects.requireNonNull(accountId, "Account ID cannot be null");
        this.depositNumber = Objects.requireNonNull(depositNumber, "Deposit number cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.principal = Objects.requireNonNull(principal, "Principal cannot be null");
        this.currentAmount = Objects.requireNonNull(currentAmount, "Current amount cannot be null");
        this.term = Objects.requireNonNull(term, "Term cannot be null");
        this.interestRate = interestRate;
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.maturityDate = Objects.requireNonNull(maturityDate, "Maturity date cannot be null");
        this.lastInterestCalculation = Objects.requireNonNull(lastInterestCalculation, "Last interest calculation cannot be null");
        this.closedAt = closedAt;
    }
    
    // Getter方法
    public UUID getDepositId() {
        return depositId;
    }
    
    public UUID getAccountId() {
        return accountId;
    }
    
    public String getDepositNumber() {
        return depositNumber;
    }
    
    public DepositStatus getStatus() {
        return status;
    }
    
    public BigDecimal getPrincipal() {
        return principal;
    }
    
    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }
    
    public DepositTerm getTerm() {
        return term;
    }
    
    public double getInterestRate() {
        return interestRate;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getMaturityDate() {
        return maturityDate;
    }
    
    public LocalDateTime getLastInterestCalculation() {
        return lastInterestCalculation;
    }
    
    public LocalDateTime getClosedAt() {
        return closedAt;
    }
    
    // Setter方法
    public synchronized void setStatus(DepositStatus status) {
        this.status = status;
        if (status == DepositStatus.WITHDRAWN || status == DepositStatus.CLOSED) {
            this.closedAt = LocalDateTime.now();
        }
    }
    
    public synchronized void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = currentAmount;
    }
    
    public synchronized void setLastInterestCalculation(LocalDateTime lastInterestCalculation) {
        this.lastInterestCalculation = lastInterestCalculation;
    }
    
    // 业务方法
    
    /**
     * 检查是否已到期
     */
    public boolean isMatured() {
        return LocalDateTime.now().isAfter(maturityDate) || LocalDateTime.now().isEqual(maturityDate);
    }
    
    /**
     * 检查是否可以支取
     */
    public boolean canWithdraw() {
        return status == DepositStatus.ACTIVE || status == DepositStatus.MATURED;
    }
    
    /**
     * 计算利息
     * 使用复利计算方式
     */
    public synchronized BigDecimal calculateInterest() {
        if (status != DepositStatus.ACTIVE) {
            return BigDecimal.ZERO;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 如果已到期但未更新状态
        if (isMatured() && status == DepositStatus.ACTIVE) {
            this.status = DepositStatus.MATURED;
        }
        
        // 计算天数差
        long days = ChronoUnit.DAYS.between(lastInterestCalculation, now);
        
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        
        // 计算日利率
        double dailyRate = interestRate / 365.0;
        
        // 计算新的金额（复利）
        BigDecimal newAmount = currentAmount.multiply(
            BigDecimal.valueOf(Math.pow(1 + dailyRate, days))
        );
        
        // 更新利息金额
        BigDecimal interestEarned = newAmount.subtract(currentAmount);
        this.currentAmount = newAmount;
        this.lastInterestCalculation = now;
        
        return interestEarned;
    }
    
    /**
     * 提前支取（会产生罚金）
     */
    public synchronized boolean earlyWithdraw(BigDecimal penaltyRate) {
        if (!canWithdraw() || isMatured()) {
            return false;
        }
        
        // 计算利息
        calculateInterest();
        
        // 应用罚金
        BigDecimal penalty = currentAmount.multiply(penaltyRate);
        this.currentAmount = currentAmount.subtract(penalty);
        
        // 更新状态
        this.status = DepositStatus.WITHDRAWN;
        this.closedAt = LocalDateTime.now();
        
        return true;
    }
    
    /**
     * 正常到期支取
     */
    public synchronized boolean withdrawAtMaturity() {
        if (!isMatured()) {
            return false;
        }
        
        if (status != DepositStatus.ACTIVE && status != DepositStatus.MATURED) {
            return false;
        }
        
        // 计算最终利息
        calculateInterest();
        
        // 更新状态
        this.status = DepositStatus.WITHDRAWN;
        this.closedAt = LocalDateTime.now();
        
        return true;
    }
    
    /**
     * 获取已赚取的利息
     */
    public BigDecimal getInterestEarned() {
        return currentAmount.subtract(principal);
    }
    
    /**
     * 获取剩余天数
     */
    public long getRemainingDays() {
        if (isMatured()) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDateTime.now(), maturityDate);
    }
    
    /**
     * 生成存款编号
     */
    private String generateDepositNumber() {
        return "FD" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }
    
    @Override
    public String toString() {
        return String.format("FixedDeposit{id=%s, account=%s, principal=%s, current=%s, term=%s, rate=%s, status=%s}",
            depositId, accountId, principal, currentAmount, term, interestRate, status);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixedDeposit that = (FixedDeposit) o;
        return Objects.equals(depositId, that.depositId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(depositId);
    }
}
