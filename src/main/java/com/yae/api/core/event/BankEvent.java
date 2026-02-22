package com.yae.api.core.event;

import com.yae.api.bank.BankAccount;
import com.yae.api.core.Service;
import com.yae.api.core.event.YAEEvent.EventSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 银行相关事件
 * 包括存款、取款、账户创建等
 */
public class BankEvent extends YAEEvent {
    
    private final UUID accountId;
    private final String accountNumber;
    private final UUID playerId;
    private final String playerName;
    private final BigDecimal amount;
    private final String transactionType;
    private final String description;
    private final String accountType;
    private final BigDecimal oldBalance;
    private final BigDecimal newBalance;
    
    /**
     * 基础银行事件构造方法
     */
    public BankEvent(@NotNull String eventType, @NotNull Service source, 
                     @NotNull BankAccount account, @NotNull BigDecimal amount) {
        this(eventType, source, account, amount, null, null, null, null, null);
    }
    
    /**
     * 完整的银行事件构造方法
     */
    public BankEvent(@NotNull String eventType, @NotNull Service source,
                     @NotNull BankAccount account, @NotNull BigDecimal amount,
                     String transactionType, String description,
                     BigDecimal oldBalance, BigDecimal newBalance, String playerName) {
        super(eventType, source.getName(), "Bank transaction: " + eventType);
        
        Objects.requireNonNull(account, "Account cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        
        this.accountId = account.getAccountId();
        this.accountNumber = account.getAccountNumber();
        this.playerId = account.getOwnerId();
        this.playerName = playerName != null ? playerName : "Unknown";
        this.amount = amount;
        this.transactionType = transactionType != null ? transactionType : eventType;
        this.description = description != null ? description : generateDescription(eventType, account, amount);
        this.accountType = account.getAccountType().name();
        this.oldBalance = oldBalance != null ? oldBalance : account.getCurrentBalance().subtract(amount);
        this.newBalance = newBalance != null ? newBalance : account.getCurrentBalance();
    }
    
    /**
     * 生成事件描述
     */
    private String generateDescription(String eventType, BankAccount account, BigDecimal amount) {
        switch (eventType.toLowerCase()) {
            case "account-created":
                return String.format("Created new %s account %s", 
                        account.getAccountType().getDisplayName(), account.getAccountNumber());
            case "deposit":
            case "money-deposit":
                return String.format("Deposited %s to account %s", amount, account.getAccountNumber());
            case "withdraw":
            case "money-withdraw":
                return String.format("Withdrew %s from account %s", amount, account.getAccountNumber());
            case "interest-earned":
                return String.format("Earned %s interest on account %s", amount, account.getAccountNumber());
            case "fixed-deposit-created":
                return String.format("Created fixed deposit of %s on account %s", amount, account.getAccountNumber());
            case "deposit-matured":
                return String.format("Fixed deposit matured, transferred %s to account %s", amount, account.getAccountNumber());
            default:
                return String.format("Bank operation %s executed on account %s for amount %s", 
                        eventType, account.getAccountNumber(), amount);
        }
    }
    
    @Override
    public String createSummary() {
        return String.format("%s | Account: %s | Amount: %s | Type: %s | Time: %s",
                getEventType(), accountNumber, amount, accountType, getTimestamp());
    }
    
    // Getters
    public UUID getAccountId() {
        return accountId;
    }
    
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public String getTransactionType() {
        return transactionType;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getAccountType() {
        return accountType;
    }
    
    public BigDecimal getOldBalance() {
        return oldBalance;
    }
    
    public BigDecimal getNewBalance() {
        return newBalance;
    }
    
    public boolean isDeposit() {
        return transactionType.toLowerCase().contains("deposit") || 
               transactionType.toLowerCase().contains("存款");
    }
    
    public boolean isWithdraw() {
        return transactionType.toLowerCase().contains("withdraw") || 
               transactionType.toLowerCase().contains("取款");
    }
    
    public boolean isInterest() {
        return transactionType.toLowerCase().contains("interest") || 
               transactionType.toLowerCase().contains("利息");
    }
    
    public boolean isFixedDeposit() {
        return transactionType.toLowerCase().contains("fixed") || 
               transactionType.toLowerCase().contains("定期");
    }
    
    /**
     * 检查是否为重要的账户事件
     */
    public boolean isImportantEvent() {
        return amount.abs().compareTo(BigDecimal.valueOf(10000)) >= 0 ||
               transactionType.toLowerCase().contains("mature") ||
               transactionType.toLowerCase().contains("create") ||
               transactionType.toLowerCase().contains("failed");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BankEvent bankEvent = (BankEvent) o;
        return Objects.equals(accountId, bankEvent.accountId) &&
               Objects.equals(amount, bankEvent.amount) &&
               Objects.equals(transactionType, bankEvent.transactionType);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), accountId, amount, transactionType);
    }
    
    @Override
    public String toString() {
        return "BankEvent{" +
                "transactionType='" + transactionType + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", amount=" + amount +
                ", playerId=" + playerId +
                ", description='" + description + '\'' +
                ", balanceChange=" + amount +
                ", newBalance=" + newBalance +
                ", timestamp=" + getTimestamp() +
                '}';
    }
    
    @Override
    public EventSeverity getSeverity() {
        return EventSeverity.INFO;
    }
    
    /**
     * 存款事件类型常量
     */
    public static final class Types {
        public static final String ACCOUNT_CREATED = "account-created";
        public static final String DEPOSIT = "money-deposit";
        public static final String WITHDRAW = "money-withdraw";
        public static final String INTEREST_EARNED = "interest-earned";
        public static final String FIXED_DEPOSIT_CREATED = "fixed-deposit-created";
        public static final String DEPOSIT_MATURED = "deposit-matured";
        public static final String TRANSFER = "money-transfer";
        public static final String BALANCE_CHANGE = "balance-change";
        public static final String ACCOUNT_FROZEN = "account-frozen";
        public static final String ACCOUNT_UNFROZEN = "account-unfrozen";
        public static final String INSUFFICIENT_FUNDS = "insufficient-funds";
        
        private Types() {}
    }
}
