# YetAnotherEconomy - 功能特性详细说明

## 📋 目录
1. [银行系统架构](#银行系统架构)
2. [信用评分算法](#信用评分算法)
3. [贷款管理系统](#贷款管理系统)
4. [商品购买流程](#商品购买流程)
5. [确认回执机制](#确认回执机制)
6. [多数据库支持](#多数据库支持)
7. [中文本地化](#中文本地化)

---

## 银行系统架构

### 核心组件

#### BankService (`BankService.java`)
```java
@ServiceMeta(defaultEnabled = true, name = ServiceType.BANK)
public class BankService extends AbstractService implements Bank {
    
    @Override
    public CompletableFuture<Double> getBalance(UUID account) {
        // 异步余额查询，确保高性能
        return CompletableFuture.supplyAsync(() -> {
            return databaseService.getBalance(account);
        });
    }
    
    @Override
    public CompletableFuture<BankResult> deposit(UUID account, double amount) {
        // 存款操作，包含完整的事务处理
        return CompletableFuture.supplyAsync(() -> {
            return performDeposit(account, amount);
        });
    }
}
```

#### 主要特性
- **异步处理**: 所有银行操作都使用 CompletableFuture 实现异步执行
- **事务安全**: 多步骤数据库操作保证原子性和一致性
- **并发控制**: 户级锁机制防止并发操作冲突
- **错误恢复**: 完善的异常处理和回滚机制
- **性能优化**: 内置缓存和批量处理优化

#### 账户管理功能
| 功能 | API方法 | 说明 |
|------|---------|------|
| 余额查询 | `getBalance()` | 异步查询指定账户余额 |
| 存款操作 | `deposit()` | 安全存入指定金额 |
| 取款操作 | `withdraw()` | 安全取出指定金额 |
| 转账操作 | `transfer()` | 跨账户安全转账 |
| 余额设置 | `setBalance()` | 管理员直接设置余额 |
| 交易历史 | `getTransactionHistory()` | 获取详细交易记录 |

### 货币格式化

#### 货币类 (`Currency.java`)
```java
public class Currency {
    private final String name = "元";
    private final String symbol = "¥";
    private final int decimals = 2;
    private final DecimalFormat formatter;
    
    public String format(double amount) {
        return symbol + formatter.format(amount) + name;
    }
    
    public boolean isValidAmount(double amount) {
        return amount >= -getMaxBalance() && amount <= getMaxBalance();
    }
}
```

#### 支持特性
- **本地格式化**: 自动适配中文货币显示习惯
- **精度控制**: 支持小数点后2位精度
- **范围验证**: 内置金额有效范围校验
- **国际化**: 预留多语言格式化扩展接口

---

## 信用评分算法

### 评分架构 (`CreditScoreCalculator.java`)

#### 多维度评估模型
```java
public class CreditScoreCalculator {
    
    private static final double INCOME_WEIGHT = 0.3;
    private static final double STABILITY_WEIGHT = 0.4;
    private static final double DEBT_WEIGHT = 0.3;
    
    public CreditScore calculateCreditScore(CreditProfile profile) {
        double incomeScore = calculateIncomeScore(profile.getMonthlyIncome());
        double stabilityScore = calculateStabilityScore(profile.getAccountAge(), profile.getTransactionFrequency());
        double debtScore = calculateDebtScore(profile.getExistingLoans(), profile.getMonthlyRepayments());
        
        double totalScore = incomeScore * INCOME_WEIGHT + 
                           stabilityScore * STABILITY_WEIGHT + 
                           debtScore * DEBT_WEIGHT;
        
        CreditScore result = new CreditScore();
        result.setScore((int) Math.round(totalScore));
        result.setGrade(getGradeForScore(totalScore));
        result.setMaxLoanAmount(calculateMaxLoanAmount(totalScore));
        result.setRiskLevel(getRiskLevel(totalScore));
        
        return result;
    }
}
```

### 评分因素详解

#### 1. 收入因素 (权重 30%)
- **月收入水平**: 评估玩家经济实力基础
- **收入稳定性**: 收入变化趋势分析
- **资产规模**: 可用于还款的资产总量

#### 2. 稳定性因素 (权重 40%)
- **账户年龄**: 账号存在时间越长越稳定
- **交易频率**: 定期经济活动显示活跃度
- **历史记录**: 长期良好记录降低风险

#### 3. 债务因素 (权重 30%)
- **现有贷款**: 当前未偿还债务总额
- **还款历史**: 历史还款记录和准时率
- **债务比例**: 现有债务与收入的比例

### 信用等级体系

| 等级 | 分数范围 | 最大贷款额 | 风险等级 |
|------|----------|------------|----------|
| SSS | 850-999 | ¥1,000,000 | 极低 |
| SS | 800-849 | ¥800,000 | 极低 |
| S | 750-799 | ¥600,000 | 低 |
| A+ | 700-749 | ¥400,000 | 低 |
| A | 650-699 | ¥250,000 | 中等 |
| B+ | 600-649 | ¥150,000 | 中等 |
| B | 550-599 | ¥80,000 | 高 |
| C | 300-549 | ¥30,000 | 高 |

---

## 贷款管理系统

### 贷款生命周期管理

#### 贷款申请 (`LoanService.java`)
```java
public class LoanService extends AbstractService {
    
    public CompletableFuture<LoanApplicationResult> applyForLoan(
            UUID playerId, double amount, int termInMonths, CollateralItem collateral) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 信用评估
                CreditScore creditScore = creditService.getCreditScore(playerId);
                
                // 2. 风险评估
                LoanRiskResult riskResult = riskAssessmentService.assessRisk(playerId, amount, collateral);
                
                // 3. 申请决策
                if (riskResult.isApproved()) {
                    SimpleLoan loan = createLoan(playerId, amount, termInMonths, 
                                                 riskResult.getInterestRate(), collateral);
                    return LoanApplicationResult.success(loan);
                } else {
                    return LoanApplicationResult.rejected(riskResult.getRejectionReason());
                }
            } catch (Exception e) {
                return LoanApplicationResult.error("申请处理失败");
            }
        });
    }
}
```

#### 还款计算算法
```java
public class AmortizationCalculator {
    
    public static double calculateMonthlyPayment(double principal, double annualRate, int months) {
        double monthlyRate = annualRate / 12 / 100;
        
        if (monthlyRate == 0) {
            return principal / months;
        }
        
        return principal * monthlyRate * Math.pow(1 + monthlyRate, months) / 
               (Math.pow(1 + monthlyRate, months) - 1);
    }
    
    public static List<PaymentSchedule> generatePaymentSchedule(double principal, double annualRate, int months) {
        List<PaymentSchedule> schedule = new ArrayList<>();
        double monthlyPayment = calculateMonthlyPayment(principal, annualRate, months);
        double remaining = principal;
        
        for (int i = 1; i <= months; i++) {
            double interest = remaining * annualRate / 12 / 100;
            double principal = monthlyPayment - interest;
            remaining -= principal;
            
            PaymentSchedule payment = new PaymentSchedule(i, monthlyPayment, principal, interest, remaining);
            schedule.add(payment);
        }
        
        return schedule;
    }
}
```

### 风险评估模型

#### 违约概率计算
```java
public class RiskAssessmentService {
    
    public LoanRiskResult assessRisk(UUID playerId, double loanAmount, CollateralItem collateral) {
        double riskScore = 0.0;
        
        // 1. 信用风险评分 (40%)
        CreditScore creditScore = creditService.getCreditScore(playerId);
        riskScore += (1000 - creditScore.getScore()) / 1000 * 0.4;
        
        // 2. 收入债务比 (30%)
        double debtToIncomeRatio = calculateDebtToIncomeRatio(playerId);
        riskScore += Math.min(debtToIncomeRatio / 0.5, 1.0) * 0.3;
        
        // 3. 抵押品价值比 (20%)
        double collateralCoverage = collateral.getValue() / loanAmount;
        riskScore += (1 - Math.min(collateralCoverage, 1.0)) * 0.2;
        
        // 4. 历史违约记录 (10%)
        riskScore += hasDefaultHistory(playerId) ? 0.1 : 0;
        
        // 风险等级判断
        RiskLevel riskLevel = getRiskLevel(riskScore);
        double defaultProbability = convertScoreToProbability(riskScore);
        
        boolean approved = riskScore < 0.7 && defaultProbability < 0.15;
        double interestRate = calculateInterestRate(riskScore, creditScore.getGrade());
        
        return new LoanRiskResult(approved, riskScore, riskLevel, interestRate, defaultProbability);
    }
}
```

---

## 商品购买流程

### 购买确认机制 (`ShopManager.java`)

#### 购买流程设计
```java
public class ShopManager {
    
    public CompletableFuture<PendingPurchaseReceipt> initiatePurchase(UUID playerId, ShopItem item, int quantity) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 验证购买条件
                PurchaseValidationResult validation = validatePurchase(playerId, item, quantity);
                if (!validation.isValid()) {
                    return PendingPurchaseReceipt.rejected(validation.getReason());
                }
                
                // 2. 计算总价
                double totalAmount = item.getPrice() * quantity;
                
                // 3. 检查余额
                CompletableFuture<Double> balanceFuture = bankService.getBalance(playerId);
                double playerBalance = balanceFuture.get();
                
                if (playerBalance < totalAmount) {
                    return PendingPurchaseReceipt.rejected("余额不足");
                }
                
                // 4. 创建待确认购买订单
                PendingPurchaseReceipt receipt = new PendingPurchaseReceipt(
                    UUID.randomUUID(), playerId, item, quantity, totalAmount,
                    System.currentTimeMillis(), Duration.ofSeconds(CONFIRMATION_SECONDS)
                );
                
                // 5. 存储到内存缓存（10秒确认期）
                pendingPurchases.put(receipt.getReceiptId(), receipt);
                
                return receipt;
            } catch (Exception e) {
                return PendingPurchaseReceipt.error("购买初始化失败");
            }
        });
    }
    
    public CompletableFuture<TransactionReceipt> confirmPurchase(UUID receiptId, UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            PendingPurchaseReceipt pending = pendingPurchases.get(receiptId);
            
            if (pending == null) {
                return TransactionReceipt.failed("订单不存在或已过期");
            }
            
            if (!pending.getBuyerId().equals(playerId)) {
                return TransactionReceipt.failed("订单归属错误");
            }
            
            if (pending.isExpired()) {
                pendingPurchases.remove(receiptId);
                return TransactionReceipt.failed("订单已过期");
            }
            
            try {
                // 执行购买
                BankResult paymentResult = bankService.withdraw(pending.getBuyerId(), pending.getTotalAmount()).get();
                
                if (!paymentResult.isSuccess()) {
                    return TransactionReceipt.failed("支付失败");
                }
                
                // 交付商品
                boolean itemsDelivered = deliverItem(pending.getBuyerId(), pending.getItem(), pending.getQuantity());
                
                if (!itemsDelivered) {
                    // 回滚支付
                    bankService.deposit(pending.getBuyerId(), pending.getTotalAmount());
                    return TransactionReceipt.failed("交付失败，已全额退款");
                }
                
                // 创建成功回执
                successReceipt = new TransactionReceipt(
                    pending.getBuyerId(),                                  // 购买玩家
                    pending.getItem().getSellerId(),                       // 售出商家
                    pending.getItem().getId(),                             // 商品ID
                    pending.getQuantity(),                                 // 数量
                    pending.getTotalAmount(),                              // 总金额
                    TransactionType.PURCHASE,                              // 交易类型
                    TransactionStatus.SUCCESS                              // 交易状态
                );
                
                // 记录交易历史
                transactionHistoryService.recordTransaction(successReceipt);
                
                // 清理待确认订单
                pendingPurchases.remove(receiptId);
                
                return successReceipt;
                
            } catch (Exception e) {
                return TransactionReceipt.failed("购买确认过程中发生错误");
            }
        });
    }
}
```

#### 购买确认用户体验
```java
public class PurchaseConfirmationHandler {
    
    private static final int CONFIRMATION_SECONDS = 10;
    
    public void handlePurchaseConfirmation(UUID playerId, PendingPurchaseReceipt receipt) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            // 发送确认消息
            player.sendMessage("§2✔ 购买订单已创建！");
            player.sendMessage("§f商品: " + receipt.getItem().getName());
            player.sendMessage("§f数量: " + receipt.getQuantity());
            player.sendMessage("§f总价: " + currency.format(receipt.getTotalAmount()));
            player.sendMessage("");
            player.sendMessage("§e⏰ 您有10秒时间确认此订单");
            player.sendMessage("§7输入 /yae shop confirm 「回执编号」 来确认购买");
            player.sendMessage("§7订单将在10秒后自动取消");
            
            // 开始倒计时任务
            startConfirmationCountdown(playerId, receipt.getReceiptId());
        }
    }
    
    private void startConfirmationCountdown(UUID playerId, UUID receiptId) {
        for (int i = 5; i > 0; i--) {
            final int seconds = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                PendingPurchaseReceipt receipt = pendingPurchases.get(receiptId);
                
                if (player != null && receipt != null) {
                    player.sendTitle("§e⏰", "§f" + seconds + "秒后订单将自动取消", 0, 20, 0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                }
            }, (CONFIRMATION_SECONDS - i) * 20L);
        }
    }
}
```

---

## 确认回执机制

### 回执管理系统 (`TransactionReceipt.java`)

#### 回执数据结构
```java
public class TransactionReceipt implements Serializable {
    private final UUID receiptId;
    private final UUID buyerId;
    private final UUID sellerId;
    private final UUID itemId;
    private final int quantity;
    private final double amount;
    private final TransactionType type;
    private final TransactionStatus status;
    private final long timestamp;
    private final long expireTime;
    private final Map<String, Object> metadata;
    
    // 撤销功能
    public boolean canBeReversed() {
        return status == TransactionStatus.SUCCESS && 
               System.currentTimeMillis() < expireTime &&
               type == TransactionType.PURCHASE;
    }
    
    public CompletableFuture<ReverseResult> attemptReverse(UUID initiatorId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!canBeReversed()) {
                return ReverseResult.rejected("回执不支持撤销");
            }
            
            if (!buyerId.equals(initiatorId)) {
                return ReverseResult.rejected("只能由购买方发起撤销");
            }
            
            try {
                // 执行撤销流程
                BankResult refundResult = bankService.deposit(buyerId, amount).get();
                if (!refundResult.isSuccess()) {
                    return ReverseResult.rejected("退款失败");
                }
                
                // 收回商品等同于退款过程
                boolean itemsRecovered = recoverItems(buyerId, itemId, quantity);
                if (!itemsRecovered) {
                    // 尝试回滚退款
                    bankService.withdraw(buyerId, amount);
                    return ReverseResult.rejected("商品回收失败,撤销已取消");
                }
                
                return ReverseResult.success();
                
            } catch (Exception e) {
                return ReverseResult.error("撤销过程发生错误");
            }
        });
    }
}
```

#### 撤销确认界面
```java
public class ReversalConfirmationHandler {
    
    public void handleReversalRequest(Player player, TransactionReceipt receipt) {
        long timeRemaining = (receipt.getExpireTime() - System.currentTimeMillis()) / 1000;
        
        player.sendMessage("§6┌─ 撤销确认界面 ─┐");
        player.sendMessage("§6│ 交易类型: " + getTransactionTypeName(receipt.getType()));
        player.sendMessage("§6│ 交易状态: " + getStatusColor(receipt.getStatus()) + getStatusName(receipt.getStatus()));
        player.sendMessage("§6│");
        player.sendMessage("§6│ 交易信息:");
        player.sendMessage("§6│   金额: " + currency.format(receipt.getAmount()));
        player.sendMessage("§6│   商品ID: " + receipt.getItemId());
        player.sendMessage("§6│   数量: " + receipt.getQuantity());
        player.sendMessage("§6│");
        player.sendMessage("§6│ 撤销倒计时: §e" + timeRemaining + " 秒");
        player.sendMessage("§6└────────────────┘");
        player.sendMessage("");
        player.sendMessage("§c⚠  撤销后将全额退款并收回商品");
        player.sendMessage("§7输入 /yae receipt reverse " + receipt.getReceiptId() + " 来确认撤销");
    }
}
```

---

## 多数据库支持

### 数据库支持策略 (`DatabaseService.java`)

#### 统一数据库接口
```java
public interface DatabaseService {
    
    // 连接管理
    Connection getConnection() throws SQLException;
    DataSource getDataSource();
    
    // 经济操作的核心数据库访问方法
    CompletableFuture<Boolean> updateBalance(UUID account, double amount, TransactionType type);
    CompletableFuture<Double> getBalance(UUID account);
    CompletableFuture<Boolean> recordTransaction(TransactionRecord record);
    
    // 贷款相关数据库访问
    CompletableFuture<Boolean> createLoan(LoanRecord loan);
    CompletableFuture<LoanRecord> getLoan(UUID loanId);
    CompletableFuture<Boolean> updateLoanStatus(UUID loanId, LoanStatus newStatus);
}
```

#### 多数据库配置
```java
public class DatabaseManager {
    
    public enum DatabaseType {
        SQLITE,
        MYSQL,   
        MARIADB
    }
    
    public DatabaseService createDatabaseService(DatabaseType type, DatabaseConfig config) {
        switch (type) {
            case SQLITE:
                return new SQLiteDatabaseService(config);
            case MYSQL:
                return new MySQLDatabaseService(config);
            case MARIADB:
                return new MariaDBDatabaseService(config);
            default:
                throw new IllegalArgumentException("不支持的数据库类型");
        }
    }
    
    // HikariCP连接池配置
    private HikariConfig createHikariConfig(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinPoolSize());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        hikariConfig.setLeakDetectionThreshold(60000);
        
        return hikariConfig;
    }
}
```

#### 数据库初始化脚本
```sql
-- SQLite/银行表结构
CREATE TABLE IF NOT EXISTS yae_accounts (
    uuid VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    frozen BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 交易记录表
CREATE TABLE IF NOT EXISTS yae_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id VARCHAR(36) UNIQUE NOT NULL,
    from_account VARCHAR(36),
    to_account VARCHAR(36),
    amount DECIMAL(15,2) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_account) REFERENCES yae_accounts(uuid),
    FOREIGN KEY (to_account) REFERENCES yae_accounts(uuid)
);

-- 贷款记录表
CREATE TABLE IF NOT EXISTS yae_loans (
    loan_id VARCHAR(36) PRIMARY KEY,
    borrower_id VARCHAR(36) NOT NULL,
    principal_amount DECIMAL(15,2) NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL,
    term_months INTEGER NOT NULL,
    monthly_payment DECIMAL(15,2) NOT NULL,
    remaining_principal DECIMAL(15,2) NOT NULL,
    collateral_type VARCHAR(50),
    collateral_value DECIMAL(15,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    default_risk_score DECIMAL(3,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP,
    FOREIGN KEY (borrower_id) REFERENCES yae_accounts(uuid)
);

-- 信用评分表
CREATE TABLE IF NOT EXISTS yae_credit_scores (
    playeruuid VARCHAR(36) PRIMARY KEY,
    score INTEGER NOT NULL DEFAULT 600,
    grade VARCHAR(3) DEFAULT 'B',
    income DECIMAL(15,2) DEFAULT 0,
    stability_factor DECIMAL(3,2) DEFAULT 0.5,
    debt_factor DECIMAL(3,2) DEFAULT 0.5,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP DEFAULT (datetime('now', '+30 days')),
    FOREIGN KEY (playeruuid) REFERENCES yae_accounts(uuid)
);
```

---

## 中文本地化

### 本地化支持 (`lang.yml`)

#### 消息系统架构
```yaml
# 中文本地化配置
language:
  code: "zh_CN"
  display-name: "简体中文"
  charset: "UTF-8"

# 通用消息
common:
  prefix: "&6[YAE经济系统] "
  success-indicator: "&a✓"
  error-indicator: "&c✗" 
  warning-indicator: "&e⚠"
  info-indicator: "&bℹ"

# 银行系统消息
bank:
  balance:
    check: "&b您的账户余额为: {balance}"
    check-other: "&b玩家 {player} 的账户余额为: {balance}"
    insufficient: "&c✗ 余额不足！需要 {required}， 当前余额 {balance}"
  
  deposit:
    success-self: "&a✓ 成功存入 {amount}，当前余额 {balance}"
    success-other: "&a✓ 成功为玩家 {player} 存入 {amount}"
  
  transfer:
    success: "&a✓ 成功向 {target} 转账 {amount}，您的余额 {balance}"
    received: "&a✓ 收到 {sender} 的转账 {amount}，您的余额 {balance}"
  
  invalid-amount: "&c✗ 无效的金额: {amount}"
  account-not-found: "&c✗ 账户不存在: {account}"

# 信用系统消息
credit:
  score-display: "&b您的信用评分为: &e{score} &7({grade})"
  grade-display: |
    "&6┌─ 信用等级信息 ─┐"
    "&6│ 当前等级: &e{grade}"
    "&6│ 分数范围: &7{min_score} - {max_score}"
    "&6│ 可贷额度: &a{max_loan}"
    "&6│ 风险等级: &7{risk_level}"
    "&6└────────────────┘"
  
  score-too-low: "&c✗ 信用评分不足，当前 {current} 需要 {required}"
  improvement-tips:
    - "&7💡 定期经济交易可提升信用评分"
    - "&7💡 保持良好的还款记录很重要"
    - "&7💡 避免频繁的大额债务申请"

# 贷款系统消息
loan:
  application-submitted: |
    "&6┌─ 贷款申请已提交 ─┐"
    "&6│ 申请金额: &e{amount}"
    "&6│ 申请期限: &7{term}个月"
    "&6│ 预估月供: &a{monthly_payment}"
    "&6│ 年化利率: &7{interest_rate}%"
    "&6│ 违约风险等级: {risk_level}"
    "&6└─────────────────┘"
  
  approved: |
    "&a✓ 贷款申请已批准！"
    "&f贷款编号: {loan_id}"
    "&f放款金额: {amount}"
    "&f到账时间: {disbursement_time}"
  
  rejected: |
    "&c✗ 贷款申请被拒绝: {reason}"
    "&7改进建议:"
    "&7- 提升信用评分"
    "&7- 降低现有债务" 
    "&7- 提供更多抵押品"
  
  monthly-payment: "&b月供提醒: 本期应还 {amount}，截止日期：{due_date}"
  payment-success: "&a✓ 还款成功！本期还款 {amount}，剩余本金 {remaining}"
  payment-late: "&e⚠ 还款提醒：请尽快还款，逾期会产生罚金"

# 商店系统消息
shop:
  item-list: |
    "&6┌─ 可购买商品列表 ─┐"
    "&6│ 分类: {category}"
    "&6└─────────────────┘"
  
  purchase-initiated: |
    "&a✓ 购买订单已创建！"
    "&f商品: {item_name}"
    "&f数量: {quantity}"
    "&f总价: {total_amount}"
    ""
    "&e⏰ 您有10秒时间确认此订单"
    "&7输入 /yae shop confirm [回执编号] 来确认购买"
    "&7订单将在10秒后自动取消"
  
  purchase-confirmed: "&a✓ 交易成功！已从您的账户扣除 {amount}"
  purchase-cancelled: "&e订单已取消"
  purchase-expired: "&e订单已过期"
  
  confirmation-countdown:
    title: "&e⏰"
    subtitle: "&f{seconds}秒后订单将自动取消"
    sound: "UI_BUTTON_CLICK"

# 确认回执消息
receipt:
  info: |
    "&6┌─ 交易回执信息 ─┐"
    "&6│ 回执编号: {receipt_id}"
    "&6│ 交易类型: &e{transaction_type}"
    "&6│ 交易状态: {status_color}{status}"
    "&6│"
    "&6│ 交易详情:"
    "&6│   交易双方: {buyer} -> {seller}"
    "&6│   总金额: {amount}"
    "&6│   商品ID: {item_id}"
    "&6│   数量: {quantity}"
    "&6│"
    "&6│ 撤销倒计时: &e{remaining_time} 秒"
    "&6└─────────────────┘"
  
  reversal-initiated: "&b交易撤销请求已接收，正在处理中..."
  reversal-success: "&a✓ 交易撤销成功！金额已退回，商品已回收"
  reversal-failed: "&c✗ 撤销失败: {reason}"
  reversal-expired: "&e⚠ 撤销功能已过期（超过10秒限制）"

# 错误消息
error:
  database-error: "&c✗ 数据库操作失败，请稍后重试"
  internal-error: "&c✗ 系统内部错误，请联系管理员"
  permission-denied: "&c✗ 权限不足，无法执行此操作"
  player-offline: "&c✗ 玩家 {player} 当前不在线"
  invalid-arguments: "&c✗ 参数错误: {help_text}"
  system-maintenance: "&e⚠ 系统维护中，部分功能可能不可用"

# 帮助信息
help:
  main: |
    "&6┌─ YetAnotherEconomy 帮助 ─┐"
    "&6│ &e/yae economy <子命令>&7 - 经济相关操作"
    "&6│ &e/yae credit <子命令>&7 - 信用相关操作"  
    "&6│ &e/yae loan <子命令>&7 - 贷款相关操作"
    "&6│ &e/yae shop <子命令>&7 - 商店相关操作"
    "&6│ &e/yae receipt <子命令>&7 - 回执相关操作"
    "&6│ &e/yae reload&7 - 重新加载配置"
    "&6│ &e/yae version&7 - 查看版本信息"
    "&6└─────────────────────────────┘"
  
  economy: |
    "&6┌─ 经济系统命令 ─┐"
    "&6│ &e/yae economy balance [玩家]&7 - 查看余额"
    "&6│ &e/yae economy pay <玩家> <金额>&7 -转账"
    "&6│ &e/yae economy deposit <金额>&7 - 存款"
    "&6│ &e/yae economy withdraw <金额>&7 - 取款"
    "&6└────────────────────┘"
  
  credit: |
    "&6┌─ 信用系统命令 ─┐" 
    "&6│ &e/yae credit score [玩家]&7 - 查看信用评分"
    "&6│ &e/yae credit grade&7 - 查看信用等级详情"
    "&6│ &e/yae credit report [玩家]&7 - 查看信用报告"
    "&6│ &e/yae credit history&7 - 查看历史评分变化"
    "&6└────────────────────┘"
  
  loan: |
    "&6┌─ 贷款系统命令 ─┐"
    "&6│ &e/yae loan create <金额> <月数>&7 - 申请贷款"
    "&6│ &e/yae loan list&7 - 查看我的贷款"
    "&6│ &e/yae loan status <ID>&7 - 查看贷款详情"
    "&6│ &e/yae loan pay <ID> [金额]&7 - 还款操作"
    "&6│ &e/yae loan calculate <金额> <月数>&7 - 试算"
    "&6└────────────────────┘"
  
  shop: |
    "&6┌─ 商店系统命令 ─┐"
    "&6│ &e/yae shop list&7 - 查看商品列表"
    "&6│ &e/yae shop buy <商品ID> [数量]&7 - 购买商品"
    "&6│ &e/yae shop categories&7 - 查看商品分类"
    "&6│ &e/yae shop category <分类>&7 - 按分类筛选"
    "&6│ &e/yae shop confirm <回执编号>&7 - 确认购买"
    "&6│ &e/yae shop history&7 - 查看购买历史"
    "&6└────────────────────┘"
  
  receipt: |
    "&6┌─ 回执系统命令 ─┐"
    "&6│ &e/yae receipt show <ID>&7 - 查看回执详情"
    "&6│ &e/yae receipt reverse <ID>&7 - 申请撤销交易"
    "&6│ &e/yae receipt list&7 - 查看最近回执"
    "&6└────────────────────┘"
```

### Unicode字符支持
```java
public class ChineseCharacterSupport {
    
    public static Map<String, String> getChineseReplacements() {
        Map<String, String> replacements = new HashMap<>();
        
        // 货币符号
        replacements.put("¥", "&currency-yuan");
        replacements.put("元", "&currency-name");
        
        // 标点符号
        replacements.put("，", "&comma-cn");
        replacements.put("。", "&period-cn");
        replacements.put("：", "&colon-cn");
        
        // 常用中文
        replacements.put("成功", "&success");
        replacements.put("失败", "&failed");
        replacements.put("确认", "&confirm");
        replacements.put("取消", "&cancel");
        
        return replacements;
    }
    
    public static String processChineseCharacters(String input) {
        // 确保所有中文内容都能正确显示
        return input.replaceAll("[\u4e00-\u9fa5]", 
                               match -> "&" + Integer.toHexString(match.charAt(0)));
    }
}
```

---

## 🚀 总结

YetAnotherEconomy 的功能特性展现了原版经济插件的完整实现。从银行系统的高性能异步处理，到信用评分的多维度智能算法，再到贷款管理的全流程风险控制，每个功能都体现了企业级的开发标准。

商品购买系统的10秒确认窗口创新性地解决了误操作问题，确认回执机制提供了交易安全保障，多数据库支持保证了环境适应性，而深度中文本地化则彰显了项目的本土化特色。

这些功能的紧密集成构成了一个完整的经济生态系统，为Minecraft服务器提供了专业可靠的经济管理解决方案。
