package com.yae.api.bank.command;

import com.yae.api.bank.*;
import com.yae.api.bank.gui.*;
import com.yae.api.core.YAECore;
import com.yae.api.core.Service;
import com.yae.api.core.command.YAECommand;
import com.yae.api.core.config.LanguageManager;
import com.yae.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 银行命令处理器
 * 处理所有银行相关的命令
 */
public class BankCommand extends YAECommand {
    
    private final YAECore plugin;
    private final LanguageManager languageManager;
    
    // Service references - set to null initially
    private BankAccountManager bankAccountManager;
    private DepositService depositService;
    private InterestCalculator interestCalculator;
    
    public BankCommand(@NotNull YAECore plugin) {
        super(plugin, "bank", "管理银行账户和存款服务", "yae.command.bank", 
              Arrays.asList("bk", "bankaccount"));
        
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        this.bankAccountManager = null; // Will initialize properly later
    }
    
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!checkPermission(sender)) {
            sender.sendMessage(MessageUtils.error("您没有权限使用此命令"));
            return false;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.error("此命令只能由玩家使用"));
            return false;
        }
        
        try {
            if (args.length == 0) {
                openBankGUI(player);
                return true;
            }
            
            return handleBankCommand(player, args);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error executing bank command", e);
            player.sendMessage(MessageUtils.error("执行银行命令时发生错误: " + e.getMessage()));
            return false;
        }
    }
    
    /**
     * 刷行银行命令
     */
    private boolean handleBankCommand(Player player, String[] args) {
        // 获取银行服务
        if (this.bankAccountManager == null) {
            Service bankService = plugin.getService(com.yae.api.core.ServiceType.BANK);
            if (bankService instanceof BankAccountManager) {
                this.bankAccountManager = (BankAccountManager) bankService;
            }
        }
        
        // 获取存款服务
        if (this.depositService == null) {
            Service depositService = plugin.getService(com.yae.api.core.ServiceType.SELL);
            if (depositService instanceof DepositService) {
                this.depositService = (DepositService) depositService;
            }
        }
        
        // 获取利息计算器服务
        if (this.interestCalculator == null) {
            Service calcService = plugin.getService(com.yae.api.core.ServiceType.RISK);
            if (calcService instanceof InterestCalculator) {
                this.interestCalculator = (InterestCalculator) calcService;
            }
        }
        
        // 检查服务是否可用
        if (this.bankAccountManager == null || this.depositService == null || this.interestCalculator == null) {
            player.sendMessage(MessageUtils.error("银行服务暂不可用"));
            return false;
        }
        
        if (!this.bankAccountManager.isEnabled() || !this.depositService.isEnabled() || !this.interestCalculator.isEnabled()) {
            player.sendMessage(MessageUtils.error("银行服务已禁用"));
            return false;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
            case "?":
                showBankHelp(player);
                return true;
                
            case "info":
                showBankInfo(player);
                return true;
                
            case "deposit":
            case "d":
                return handleDepositCommand(player, Arrays.copyOfRange(args, 1, args.length));
                
            case "current":
            case "活弗":
                return handleCurrentDeposit(player, Arrays.copyOfRange(args, 1, args.length));
                
            case "fixed":
            case "term":
            case "定期":
                return handleFixedDeposit(player, Arrays.copyOfRange(args, 1, args.length));
                
            case "gui":
            case "open":
            case "界面":
                openBankGUI(player);
                return true;
                
            case "list":
            case "ls":
                showAccountList(player);
                return true;
                
            case "rate":
            case "利率":
                showCurrentRates(player);
                return true;
                
            case "calculate":
            case "calc":
                return handleCalculateInterest(player, Arrays.copyOfRange(args, 1, args.length));
                
            default:
                player.sendMessage(MessageUtils.error("未知子命令: " + subCommand));
                showBankHelp(player);
                return false;
        }
    }
    
    /**
     * 处理存款相关命令
     */
    private boolean handleDepositCommand(Player player, String[] args) {
        if (args.length == 0) {
            openCurrentDepositGUI(player);
            return true;
        }
        
        String depositType = args[0].toLowerCase();
        
        switch (depositType) {
            case "current":
            case "活期":
                return handleCurrentDeposit(player, Arrays.copyOfRange(args, 1, args.length));
                
            case "fixed":
            case "term":
            case "定期":
                return handleFixedDeposit(player, Arrays.copyOfRange(args, 1, args.length));
                
            default:
                // 尝试直接解析为金额（活期存款）
                try {
                    BigDecimal amount = parseAmount(args[0]);
                    return processCurrentDeposit(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage(MessageUtils.error("无效存款类型或金额: " + args[0]));
                    return false;
                }
        }
    }
    
    /**
     * 处理活期存款命令
     */
    private boolean handleCurrentDeposit(Player player, String[] args) {
        if (args.length == 0) {
            openCurrentDepositGUI(player);
            return true;
        }
        
        if (args.length > 1) {
            player.sendMessage(MessageUtils.error("活期存款用法: /yae bank current [amount]"));
            return false;
        }
        
        try {
            BigDecimal amount = parseAmount(args[0]);
            return processCurrentDeposit(player, amount);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtils.error("无效金额: " + args[0]));
            return false;
        }
    }
    
    /**
     * 处理定期存款命令
     */
    private boolean handleFixedDeposit(Player player, String[] args) {
        if (args.length == 0) {
            openFixedDepositGUI(player);
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage(MessageUtils.error("定期存款用法: /yae bank fixed <months> <amount>"));
            player.sendMessage(MessageUtils.info("可用期限: 3, 6, 12, 24, 36, 60 (月)"));
            return false;
        }
        
        try {
            int months = Integer.parseInt(args[0]);
            BigDecimal amount = parseAmount(args[1]);
            
            return processFixedDeposit(player, months, amount);
            
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtils.error("无效的期限或金额"));
            return false;
        }
    }
    
    /**
     * 处理利息计算命令
     */
    private boolean handleCalculateInterest(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.error("利息计算用法: /yae bank calculate <principal> <days> [type]"));
            player.sendMessage(MessageUtils.info("类型: current（活期）, fixed（定期）, compound（复利）"));
            return false;
        }
        
        try {
            BigDecimal principal = parseAmount(args[0]);
            int days = Integer.parseInt(args[1]);
            String type = args.length > 2 ? args[2].toLowerCase() : "current";
            
            return calculateInterestPreview(player, principal, days, type);
            
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtils.error("无效的本金或天数"));
            return false;
        }
    }
    
    /**
     * 打开发行存款GUI
     */
    private void openCurrentDepositGUI(Player player) {
        if (bankAccountManager == null || !bankAccountManager.isEnabled()) {
            player.sendMessage(MessageUtils.error("银行服务暂不可用"));
            return;
        }
        
        List<BankAccount> accounts = bankAccountManager.getOwnerAccounts(player.getUniqueId());
        if (accounts.isEmpty()) {
            player.sendMessage(MessageUtils.error("您没有可用的银行账户"));
            return;
        }
        
        BankAccount account = accounts.stream()
                .filter(BankAccount::isActive)
                .findFirst()
                .orElse(null);
                
        if (account == null) {
            player.sendMessage(MessageUtils.error("您没有活跃的银行账户"));
            return;
        }
        
        CurrentDepositGUI gui = new CurrentDepositGUI(plugin);
        gui.openCurrentDepositInterface(player, account);
    }
    
    /**
     * 打开定期存款GUI
     */
    private void openFixedDepositGUI(Player player) {
        if (bankAccountManager == null || !bankAccountManager.isEnabled()) {
            player.sendMessage(MessageUtils.error("银行服务暂不可用"));
            return;
        }
        
        List<BankAccount> accounts = bankAccountManager.getOwnerAccounts(player.getUniqueId());
        if (accounts.isEmpty()) {
            player.sendMessage(MessageUtils.error("您没有可用的银行账户"));
            return;
        }
        
        BankAccount account = accounts.stream()
                .filter(BankAccount::isActive)
                .findFirst()
                .orElse(null);
                
        if (account == null) {
            player.sendMessage(MessageUtils.error("您没有活跃的银行账户"));
            return;
        }
        
        FixedDepositGUI gui = new FixedDepositGUI(plugin);
        gui.openFixedDepositInterface(player, account);
    }
    
    /**
     * 处理活期存款
     */
    private boolean processCurrentDeposit(Player player, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(MessageUtils.error("存款金额必须大于0"));
            return false;
        }
        
        player.sendMessage(MessageUtils.progress("正在处理活期存款..."));
        
        CompletableFuture<DepositService.DepositResult> future = depositService.depositCurrent(player.getUniqueId(), amount);
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                player.sendMessage(MessageUtils.error("存款处理失败: " + throwable.getMessage()));
                return;
            }
            
            if (result.isSuccess()) {
                player.sendMessage(MessageUtils.success("活期存款成功！"));
                player.sendMessage(MessageUtils.info("存款金额: " + formatCurrency(result.getDepositAmount())));
                player.sendMessage(MessageUtils.info("新余额: " + formatCurrency(result.getFinalBalance())));
            } else {
                player.sendMessage(MessageUtils.error("存款失败: " + result.getErrorMessage()));
            }
        });
        
        return true;
    }
    
    /**
     * 处理定期存款
     */
    private boolean processFixedDeposit(Player player, int months, BigDecimal amount) {
        // 验证期限
        if (!isValidTerm(months)) {
            player.sendMessage(MessageUtils.error("无效的存款期限: " + months + " 个月"));
            player.sendMessage(MessageUtils.info("可用期限: 3, 6, 12, 24, 36, 60 (月)"));
            return false;
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(MessageUtils.error("存款金额必须大于0"));
            return false;
        }
        
        FixedDeposit.DepositTerm term = getDepositTerm(months);
        player.sendMessage(MessageUtils.progress("正在处理定期存款..."));
        
        CompletableFuture<DepositService.DepositResult> future = depositService.depositFixed(player.getUniqueId(), amount, term);
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                player.sendMessage(MessageUtils.error("定期存款处理失败: " + throwable.getMessage()));
                return;
            }
            
            if (result.isSuccess()) {
                player.sendMessage(MessageUtils.success("定期存款创建成功！"));
                player.sendMessage(MessageUtils.info("存款号码: " + result.getDepositNumber()));
                player.sendMessage(MessageUtils.info("存款金额: " + formatCurrency(result.getDepositAmount())));
                player.sendMessage(MessageUtils.info("存期: " + months + " 个月"));
                player.sendMessage(MessageUtils.info("到期收益: " + formatCurrency(result.getMaturityAmount())));
                player.sendMessage(MessageUtils.info("到期日: " + result.getMaturityDate()));
            } else {
                player.sendMessage(MessageUtils.error("定期存款失败: " + result.getErrorMessage()));
            }
        });
        
        return true;
    }
    
    /**
     * 计算利息预览
     */
    private boolean calculateInterestPreview(Player player, BigDecimal principal, int days, String type) {
        BigDecimal interest;
        
        switch (type) {
            case "fixed":
            case "term":
                // 假设使用1年期利率
                double termRate = depositService.getTermInterestRate(FixedDeposit.DepositTerm.ONE_YEAR);
                interest = depositService.calculateFixedDepositReturn(principal, BigDecimal.valueOf(termRate), days, 12);
                break;
                
            case "compound":
                // 复利计算
                double compoundRate = interestCalculator.getCurrentAccountRate();
                interest = interestCalculator.calculateCompoundInterest(
                        principal, BigDecimal.valueOf(compoundRate), days, 12);
                break;
                
            case "current":
            case "simple":
            default:
                // 单利计算（活期）
                double currentRate = depositService.getCurrentInterestRate();
                interest = interestCalculator.calculateSimpleInterest(principal, BigDecimal.valueOf(currentRate), days);
                break;
        }
        
        player.sendMessage(MessageUtils.info("=== 利息预览 ==="));
        player.sendMessage(MessageUtils.info("本金: " + formatCurrency(principal)));
        player.sendMessage(MessageUtils.info("天数: " + days));
        player.sendMessage(MessageUtils.info("类型: " + getInterestTypeName(type)));
        player.sendMessage(MessageUtils.info("预计利息: " + formatCurrency(interest)));
        player.sendMessage(MessageUtils.info("到期总额: " + formatCurrency(principal.add(interest))));
        
        return true;
    }
    
    /**
     * 显示银行帮助信息
     */
    private void showBankHelp(Player player) {
        player.sendMessage(MessageUtils.title("=== 银行帮助 ===", '═', 50));
        player.sendMessage("");
        player.sendMessage(MessageUtils.info("&a/yae bank &f- 打开银行主界面"));
        player.sendMessage(MessageUtils.info("&a/yae bank current [amount] &f- 活期存款（或利息计算）"));
        player.sendMessage(MessageUtils.info("&a/yae bank fixed <months> <amount> &f- 定期存款"));
        player.sendMessage(MessageUtils.info("&a/yae bank info &f- 显示账户信息"));
        player.sendMessage(MessageUtils.info("&a/yae bank list &f- 列出所有账户"));
        player.sendMessage(MessageUtils.info("&a/yae bank rate &f- 显示当前利率"));
        player.sendMessage(MessageUtils.info("&a/yae bank calculate <principal> <days> [type] &f- 计算利息"));
        player.sendMessage(MessageUtils.info("&a/yae bank gui &f- 打开银行界面"));
        player.sendMessage("");
        player.sendMessage(MessageUtils.info("可用定期期限: 3, 6, 12, 24, 36, 60 (月)"));
        player.sendMessage(MessageUtils.info("利息类型: current(活期), fixed(定期), compound(复利)"));
        player.sendMessage("");
        player.sendMessage(MessageUtils.title("=== 存款示例 ===", '─', 50));
        player.sendMessage(MessageUtils.info("&7/yae bank deposit current 1000 &f- 活期存款1000"));
        player.sendMessage(MessageUtils.info("&7/yae bank deposit fixed 12 5000 &f- 定期存款12个月5000"));
        player.sendMessage(MessageUtils.info("&7/yae bank calculate 10000 365 compound &f- 计算复利364天"));
    }
    
    /**
     * 显示银行信息
     */
    private void showBankInfo(Player player) {
        List<BankAccount> accounts = bankAccountManager.getOwnerAccounts(player.getUniqueId());
        
        if (accounts.isEmpty()) {
            player.sendMessage(MessageUtils.info("您还没有银行账户"));
            player.sendMessage(MessageUtils.info("使用 /yae bank 创建新账户"));
            return;
        }
        
        BankAccount mainAccount = accounts.stream()
                .filter(BankAccount::isActive)
                .findFirst()
                .orElse(null);
                
        if (mainAccount == null) {
            player.sendMessage(MessageUtils.warning("您没有活跃的银行账户"));
            return;
        }
        
        player.sendMessage(MessageUtils.title("=== 账户信息 ===", '─', 50));
        player.sendMessage(MessageUtils.info("账户号码: &e" + mainAccount.getAccountNumber()));
        player.sendMessage(MessageUtils.info("账户类型: &a" + getAccountTypeName(mainAccount.getAccountType())));
        player.sendMessage(MessageUtils.info("当前余额: &6" + formatCurrency(mainAccount.getCurrentBalance())));
        player.sendMessage(MessageUtils.info("可用余额: &b" + formatCurrency(mainAccount.getAvailableBalance())));
        player.sendMessage(MessageUtils.info("定期总额: &e" + formatCurrency(mainAccount.getTotalFixedDepositAmount())));
        player.sendMessage(MessageUtils.info("信用评分: " + (mainAccount.getCreditScore() > 0 ? "&d" + mainAccount.getCreditScore() : "&7未设置")));
        player.sendMessage(MessageUtils.info("活期利率: " + formatPercentage(depositService.getCurrentInterestRate()) + "%/年"));
        player.sendMessage(MessageUtils.info("创建时间: " + mainAccount.getCreatedAt()));
        player.sendMessage(MessageUtils.info("账户数: " + accounts.size()));
    }
    
    /**
     * 显示账户列表
     */
    private void showAccountList(Player player) {
        List<BankAccount> accounts = bankAccountManager.getOwnerAccounts(player.getUniqueId());
        
        if (accounts.isEmpty()) {
            player.sendMessage(MessageUtils.info("您还没有银行账户"));
            return;
        }
        
        player.sendMessage(MessageUtils.title("=== 银行账户 ===", '─', 50));
        
        for (int i = 0; i < accounts.size(); i++) {
            BankAccount account = accounts.get(i);
            String status = account.isActive() ? "&a活跃" : "&c非活跃";
            player.sendMessage(MessageUtils.info(String.format("&7#%d &f%s &7| &f%s &7| %s &7| \u00a5%s", 
                    i + 1, account.getAccountNumber(), getAccountTypeName(account.getAccountType()),
                    status, formatCurrency(account.getCurrentBalance()))));
        }
    }
    
    /**
     * 显示当前利率
     */
    private void showCurrentRates(Player player) {
        player.sendMessage(MessageUtils.title("=== 当前利率 ===", '─', 50));
        player.sendMessage(MessageUtils.info("活期存款: &b" + formatPercentage(depositService.getCurrentInterestRate()) + "%/年"));
        player.sendMessage(MessageUtils.info("储蓄存款: &b" + formatPercentage(interestCalculator.getSavingsAccountRate()) + "%/年"));
        player.sendMessage("");
        player.sendMessage(MessageUtils.info("定期存款利率："));
        
        int[] months = {3, 6, 12, 24, 36, 60};
        String[] names = {"3月", "6月", "1年", "2年", "3年", "5年"};
        
        for (int i = 0; i < months.length; i++) {
            FixedDeposit.DepositTerm term = getDepositTerm(months[i]);
            double rate = depositService.getTermInterestRate(term);
            player.sendMessage(MessageUtils.info(names[i] + ": &e" + formatPercentage(rate) + "%/年"));
        }
    }
    
    /**
     * 打开银行GUI
     */
    private void openBankGUI(Player player) {
        if (bankAccountManager == null || !bankAccountManager.isEnabled()) {
            player.sendMessage(MessageUtils.error("银行服务暂不可用"));
            return;
        }
        
        BankChestGUI gui = new BankChestGUI(plugin);
        gui.openBankInterface(player);
    }
    
    // 工具方法
    
    /**
     * 解析金额
     */
    private BigDecimal parseAmount(String amountStr) {
        // 支持简写输入（例如：1k = 1000, 1m = 1000000）
        amountStr = amountStr.toLowerCase();
        
        double multiplier = 1.0;
        if (amountStr.endsWith("k")) {
            multiplier = 1000.0;
            amountStr = amountStr.substring(0, amountStr.length() - 1);
        } else if (amountStr.endsWith("m")) {
            multiplier = 1000000.0;
            amountStr = amountStr.substring(0, amountStr.length() - 1);
        } else if (amountStr.endsWith("b")) {
            multiplier = 1000000000.0;
            amountStr = amountStr.substring(0, amountStr.length() - 1);
        }
        
        double value = Double.parseDouble(amountStr) * multiplier;
        return BigDecimal.valueOf(value);
    }
    
    /**
     * 验证期限是否有效
     */
    private boolean isValidTerm(int months) {
        return months == 3 || months == 6 || months == 12 || months == 24 || months == 36 || months == 60;
    }
    
    /**
     * 获取存款期限枚举
     */
    private FixedDeposit.DepositTerm getDepositTerm(int months) {
        switch (months) {
            case 3: return FixedDeposit.DepositTerm.THREE_MONTHS;
            case 6: return FixedDeposit.DepositTerm.SIX_MONTHS;
            case 12: return FixedDeposit.DepositTerm.ONE_YEAR;
            case 24: return FixedDeposit.DepositTerm.TWO_YEARS;
            case 36: return FixedDeposit.DepositTerm.THREE_YEARS;
            case 60: return FixedDeposit.DepositTerm.FIVE_YEARS;
            default: return FixedDeposit.DepositTerm.ONE_YEAR;
        }
    }
    
    /**
     * 格式化货币
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "¥0.00";
        String symbol = plugin.getMainConfiguration().getCurrency().getSymbol();
        return symbol + amount.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 格式化百分比
     */
    private String formatPercentage(double rate) {
        return String.format("%.2f", rate * 100);
    }
    
    /**
     * 获取利息类型名称
     */
    private String getInterestTypeName(String type) {
        switch (type) {
            case "fixed": return "定期存款";
            case "compound": return "复利计算";
            case "current":
            case "simple":
                       return "活期存款";
            default: return "未知类型";
        }
    }
    
    /**
     * 获取账户类型名称
     */
    private String getAccountTypeName(BankAccount.AccountType type) {
        switch (type) {
            case CHECKING: return "活期账户";
            case SAVINGS: return "储蓄账户";
            case FIXED_DEPOSIT: return "定期账户";
            case LOAN: return "贷款账户";
            default: return "未知类型";
        }
    }
    
    /**
     * Tab补全支持
     */
    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!checkPermission(sender)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            return Arrays.asList("help", "info", "deposit", "current", "fixed", "gui", "list", "rate", "calculate").stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "deposit":
                case "current":
                    return Arrays.asList("1000", "5000", "10000", "50000").stream()
                            .filter(amt -> amt.startsWith(args[1]))
                            .collect(Collectors.toList());
                            
                case "fixed":
                case "term":
                    return Arrays.asList("3", "6", "12", "24", "36", "60").stream()
                            .filter(term -> term.startsWith(args[1]))
                            .collect(Collectors.toList());
                            
                case "calculate":
                case "calc":
                    return Arrays.asList("current", "fixed", "compound").stream()
                            .filter(type -> type.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3 && (args[0].equalsIgnoreCase("fixed") || args[0].equalsIgnoreCase("term") || 
                args[0].equalsIgnoreCase("定期"))) {
            return Arrays.asList("1000", "5000", "10000", "50000", "100000").stream()
                    .filter(amt -> amt.startsWith(args[2]))
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}
