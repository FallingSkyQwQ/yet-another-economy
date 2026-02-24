# YetAnotherEconomy - API和命令参考

## 🎯 命令完整参考

### 命令前缀
所有命令都以 `/yae` 或 `/yea` 开头，支持别名：`/yeconomy`, `/yetanothereconomy`

### 🏦 经济命令组 `/yae economy`

| 命令 | 权限 | 描述 | 示例 |
|------|------|------|------|
| `/yae economy balance [玩家名]` | `yae.user.balance` | 查看余额 | `/yae economy balance` |
| `/yae economy pay <玩家> <金额>` | `yae.user.pay` | 转账给玩家 | `/yae economy pay PlayerA 100` |
| `/yae economy deposit <金额>` | `yae.admin.deposit` | 存入个人账户 | `/yae economy deposit 500` |
| `/yae economy withdraw <金额>` | `yae.admin.withdraw` | 取出个人账户 | `/yae economy withdraw 200` |
| `/yae economy set <玩家> <金额>` | `yae.admin.setbalance` | 设置玩家余额 | `/yae economy set PlayerB 1000` |

### 📊 信用命令组 `/yae credit`

| 命令 | 权限 | 描述 | 示例 |
|------|------|------|------|
| `/yae credit score [玩家名]` | `yae.user.credit.score` | 查看信用评分 | `/yae credit score` |
| `/yae credit grade` | `yae.user.credit.grade` | 查看信用等级 | `/yae credit grade` |
| `/yae credit report [玩家名]` | `yae.user.credit.report` | 查看信用报告 | `/yae credit report PlayerA` |
| `/yae credit history` | `yae.user.credit.history` | 查看信用历史 | `/yae credit history` |

### 💰 贷款命令组 `/yae loan`

| 命令 | 权限 | 描述 | 示例 |
|------|------|------|------|
| `/yae loan create <金额> <月数>` | `yae.user.loan.create` | 申请贷款 | `/yae loan create 10000 12` |
| `/yae loan list` | `yae.user.loan.list` | 查看我的贷款 | `/yae loan list` |
| `/yae loan status <ID>` | `yae.user.loan.status` | 查看贷款状态 | `/yae loan status loan123` |
| `/yae loan pay <ID> [金额]` | `yae.user.loan.pay` | 还款操作 | `/yae loan pay loan123 500` |
| `/yae loan calculate <金额> <月数>` | `yae.user.loan.calculate` | 贷款试算 | `/yae loan calculate 5000 6` |

### 🛒 商店命令组 `/yae shop`

| 命令 | 权限 | 描述 | 示例 |
|------|------|------|------|
| `/yae shop list` | `yae.user.shop.list` | 查看商品列表 | `/yae shop list` |
| `/yae shop buy <商品ID> [数量]` | `yae.user.shop.buy` | 购买商品 | `/yae shop buy diamond 10` |
| `/yae shop categories` | `yae.user.shop.browse` | 查看分类 | `/yae shop categories` |
| `/yae shop category <分类>` | `yae.user.shop.browse` | 按分类查看 | `/yae shop category weapons` |
| `/yae shop confirm <回执>` | `yae.user.shop.confirm` | 确认购买 | `/yae shop confirm receipt123` |
| `/yae shop history` | `yae.user.shop.viewhistory` | 查看购买历史 | `/yae shop history` |

### 🧾 回执命令组 `/yae receipt`

| 命令 | 权限 | 描述 | 示例 |
|------|------|------|------|
| `/yae receipt show <ID>` | `yae.user.receipt.view` | 查看回执详情 | `/yae receipt show receipt123` |
| `/yae receipt reverse <ID>` | `yae.user.receipt.reverse` | 申请撤销交易 | `/yae receipt reverse receipt123` |
| `/yae receipt list` | `yae.user.receipt.list` | 查看最近回执 | `/yae receipt list` |

### 🔧 管理命令组 `/yae`<br>

| 命令 | 权限 | 描述 | 示例 |
|------|------|------|------|
| `/yae reload` | `yae.admin.reload` | 重载配置 | `/yae reload` |
| `/yae version` | `yae.user.version` | 查看版本 | `/yae version` |
| `/yae status` | `yae.user.status` | 查看状态 | `/yae status` |

## 🚀 Java API参考

### 核心经济 API
```java
// 获取经济服务
EconomyService economy = YetAnotherEconomy.getEconomyService();

// 查询余额
CompletableFuture<Double> balanceFuture = economy.getBalance(player.getUniqueId());
balanceFuture.thenAccept(balance -> {
    player.sendMessage("您的余额: " + balance);
});

// 进行转账
economy.transfer(from, to, amount).thenAccept(result -> {
    if (result.isSuccess()) {
        Bukkit.getPlayer(from).sendMessage("转账成功！");
    }
});
```

### 信用评分 API
```java
// 获取信用服务
CreditService credit = YetAnotherEconomy.getCreditService();

// 查询信用评分
CreditScore score = credit.getCreditScore(player.getUniqueId());
player.sendMessage("您的信用评分: " + score.getScore());
player.sendMessage("信用等级: " + score.getGrade());

// 计算最大可贷款额
long maxLoan = score.getMaxLoanAmount();
```

### 贷款管理 API
```java
// 获取贷款服务  
LoanService loan = YetAnotherEconomy.getLoanService();

// 申请贷款
LoanApplication application = new LoanApplication(player, 10000, 12);
loan.applyForLoan(application).thenAccept(result -> {
    if (result.isApproved()) {
        player.sendMessage("贷款申请通过！");
    }
});

// 查看还款计划
Loan loanRecord = loan.getLoan(loanId);
List<PaymentSchedule> schedule = loan.getPaymentSchedule(loanRecord);
```

### 商店 API
```java
// 获取商店管理器
ShopManager shop = YetAnotherEconomy.getShopManager();

// 获取所有商品
List<ShopItem> items = shop.getAvailableItems();

// 购买商品
PendingPurchaseReceipt receipt = shop.initiatePurchase(player, item, 10);
// 10秒内调用确认
shop.confirmPurchase(receipt.getReceiptId(), player);
```

## 🛠️ 开发者集成

### Maven 依赖
```xml
<dependency>
    <groupId>com.yae</groupId>
    <artifactId>yet-another-economy</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### API 快速上手
```java
public class MyBankPlugin extends JavaPlugin implements Listener {
    
    private EconomyService economyService;
    
    @Override
    public void onEnable() {
        // 获取 YetAnotherEconomy 实例
        Plugin yaePlugin = getServer().getPluginManager().getPlugin("YetAnotherEconomy");
        if (yaePlugin != null && yaePlugin.isEnabled()) {
            this.economyService = YetAnotherEconomy.getEconomyService();
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 查询余额
        economyService.getBalance(player.getUniqueId()).thenAccept(balance -> {
            player.sendMessage("欢迎回来！您的账户余额: ¥" + balance);
        });
    }
}
```

### 自定义服务集成
```java
@ServiceMeta(defaultEnabled = false, name = ServiceType.CUSTOM)
public class MyCustomService extends AbstractService {
    
    @Override
    public boolean initialize() {
        // 自定义初始化代码
        return true;
    }
    
    @Override
    public void shutdown() {
        // 清理资源
    }
    
    @Override
    public boolean dependsOn(ServiceType serviceType) {
        // 依赖关系配置
        return serviceType == ServiceType.ECONOMY;
    }
}
```

---

查看完整功能特性请阅读 [FEATURE_DETAILS.md](FEATURE_DETAILS.md)，安装配置请查看 [INSTALLATION_GUIDE.md](INSTALLATION_GUIDE.md)。
