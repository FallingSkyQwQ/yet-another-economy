package com.yae.api.bank.gui;

import com.yae.api.bank.BankAccount;
import com.yae.api.bank.BankAccountManager;
import com.yae.api.bank.InterestCalculator;
import com.yae.api.core.YAECore;
import com.yae.api.core.config.LanguageManager;
import com.yae.api.core.config.Configuration;
import com.yae.utils.MessageUtils;
import com.yae.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.bukkit.command.CommandSender;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 银行主界面GUI
 * 显示账户余额、存款选项、转账功能等
 */
@SuppressWarnings("deprecation")
public class BankChestGUI {
    
    private final YAECore plugin;
    private final BankAccountManager bankAccountManager;
    private final InterestCalculator interestCalculator;
    private final LanguageManager languageManager;
    private final Configuration configuration;
    
    private static final int ROWS = 6;
    private static final String TITLE = "🏦 银行服务";
    
    public BankChestGUI(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.bankAccountManager = (BankAccountManager) plugin.getService(com.yae.api.core.ServiceType.BANK);
        this.interestCalculator = new InterestCalculator(plugin); // 或者从服务中获取
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        this.configuration = plugin.getMainConfiguration();
    }
    
    /**
     * 打开银行界面（兼容不同调用方式）
     */
    public void openBankInterface(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.error("此界面只能由玩家使用"));
            return;
        }
        openBankInterface(player);
    }
    
    /**
     * 打开银行主界面
     */
    public void openBankInterface(@NotNull Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        if (bankAccountManager == null || !bankAccountManager.isEnabled()) {
            player.sendMessage(MessageUtils.color("❌ 银行服务暂不可用"));
            return;
        }
        
        List<BankAccount> playerAccounts = bankAccountManager.getOwnerAccounts(player.getUniqueId());
        if (playerAccounts.isEmpty()) {
            // 创建新账户
            player.sendMessage(MessageUtils.color("ℹ️ 正在为您创建新的银行账户..."));
            try {
                BankAccount newAccount = bankAccountManager.createAccount(
                        player.getUniqueId(), "PLAYER", BankAccount.AccountType.CHECKING);
                if (newAccount == null) {
                    player.sendMessage(MessageUtils.color("❌ 无法创建银行账户，请联系管理员"));
                    return;
                }
                playerAccounts = Arrays.asList(newAccount);
            } catch (Exception e) {
                player.sendMessage(MessageUtils.color("❌ 创建账户失败: " + e.getMessage()));
                return;
            }
        }
        
        Gui gui = Gui.gui()
                .title(MessageUtils.miniMessage(TITLE))
                .rows(ROWS)
                .disableAllInteractions()
                .create();
        
        // 获取主账户（第一个活跃的账户）
        BankAccount mainAccount = playerAccounts.stream()
                .filter(BankAccount::isActive)
                .findFirst()
                .orElse(null);
                
        if (mainAccount == null) {
            player.sendMessage(MessageUtils.color("❌ 没有可用的活跃银行账户"));
            return;
        }
        
        setupBankInterface(gui, player, mainAccount, playerAccounts);
        gui.open(player);
    }
    
    /**
     * 设置银行界面内容
     */
    private void setupBankInterface(Gui gui, Player player, BankAccount mainAccount, 
                                   List<BankAccount> playerAccounts) {
        // 清空界面
        // Clear GUI by removing all existing items
        // Since the GUI library might not have a clear() method, we'll handle it differently
        for (int i = 0; i < 54; i++) { // Standard GUI size
            gui.setItem(i, new GuiItem(new ItemStack(Material.AIR)));
        }
        
        // 第1行：账户信息
        setupAccountInfoRow(gui, mainAccount);
        
        // 第2行：余额和利率信息
        setupBalanceRow(gui, mainAccount);
        
        // 第3行：存款按钮
        setupDepositButtons(gui, player, mainAccount);
        
        // 第4行：其他功能按钮
        setupFunctionButtons(gui, player, mainAccount, playerAccounts);
        
        // 第5行：导航按钮
        setupNavigationRow(gui, player);
        
        // 第6行：关闭按钮
        setupCloseButton(gui);
        
        // 装饰性边框
        setupDecorations(gui);
    }
    
    /**
     * 设置账户信息行（第1行）
     */
    private void setupAccountInfoRow(Gui gui, BankAccount account) {
        ItemStack accountInfo = new ItemStack(Material.BOOK);
        ItemMeta meta = accountInfo.getItemMeta();
        
        meta.setDisplayName(MessageUtils.color("📖 账户信息"));
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color("&7» 账户号码: &e" + account.getAccountNumber()));
        lore.add(MessageUtils.color("&7» 账户类型: " + getAccountTypeDisplayName(account.getAccountType())));
        lore.add(MessageUtils.color("&7» 账户状态: " + getAccountStatusDisplayName(account.getStatus())));
        lore.add(MessageUtils.color("&7» 创建时间: &e" + TimeUtils.formatDateTime(account.getCreatedAt())));
        
        meta.setLore(lore);
        accountInfo.setItemMeta(meta);
        
        gui.setItem(0, new GuiItem(accountInfo));
        
        // 信用评分（如果有的话）
        if (account.getCreditScore() > 0) {
            ItemStack creditScore = new ItemStack(Material.GOLDEN_CARROT);
            ItemMeta creditMeta = creditScore.getItemMeta();
            creditMeta.setDisplayName(MessageUtils.color("⭐ 信用评分"));
            
            List<String> creditLore = new ArrayList<>();
            creditLore.add(MessageUtils.color("&7» 当前评分: &e" + account.getCreditScore() + "/850"));
            creditLore.add(MessageUtils.color("&7» 评级: " + getCreditRating(account.getCreditScore())));
            creditMeta.setLore(creditLore);
            creditScore.setItemMeta(creditMeta);
            
            gui.setItem(8, new GuiItem(creditScore));
        }
    }
    
    /**
     * 设置余额信息行（第2行）
     */
    private void setupBalanceRow(Gui gui, BankAccount account) {
        // 当前余额
        ItemStack currentBalance = new ItemStack(Material.EMERALD);
        ItemMeta currentMeta = currentBalance.getItemMeta();
        currentMeta.setDisplayName(MessageUtils.color("💰 当前余额"));
        
        List<String> currentLore = new ArrayList<>();
        BigDecimal totalBalance = account.getTotalBalance();
        BigDecimal currentBal = account.getCurrentBalance();
        
        currentLore.add(MessageUtils.color("&7» 活期余额: &a" + formatCurrency(currentBal)));
        currentLore.add(MessageUtils.color("&7» 定期总额: &e" + formatCurrency(account.getTotalFixedDepositAmount())));
        currentLore.add(MessageUtils.color("&7» 总余额: &6" + formatCurrency(totalBalance)));
        currentLore.add("");
        
        double currentRate = getCurrentAccountRate();
        currentLore.add(MessageUtils.color("&7» 活期利率: &b" + formatPercentage(currentRate) + "%/年"));
        
        if (account.getInterestRate().compareTo(BigDecimal.ZERO) > 0) {
            currentLore.add(MessageUtils.color("&7» 当前适用利率: &b" + formatPercentage(account.getInterestRate().doubleValue()) + "%"));
        }
        
        currentMeta.setLore(currentLore);
        currentBalance.setItemMeta(currentMeta);
        gui.setItem(0, new GuiItem(currentBalance));
        
        // 利息计算器预览
        ItemStack interestPreview = new ItemStack(Material.SUNFLOWER);
        ItemMeta previewMeta = interestPreview.getItemMeta();
        previewMeta.setDisplayName(MessageUtils.color("📊 利息预览"));
        
        List<String> previewLore = new ArrayList<>();
        BigDecimal monthlyInterest = calculateMonthlyInterest(currentBal, currentRate);
        previewLore.add(MessageUtils.color("&7预计月利息: &e" + formatCurrency(monthlyInterest)));
        
        BigDecimal yearlyInterest = calculateYearlyInterest(currentBal, currentRate);
        previewLore.add(MessageUtils.color("&7预计年利息: &e" + formatCurrency(yearlyInterest)));
        
        previewMeta.setLore(previewLore);
        interestPreview.setItemMeta(previewMeta);
        gui.setItem(8, new GuiItem(interestPreview));
    }
    
    /**
     * 设置存款按钮（第3行）
     */
    private void setupDepositButtons(Gui gui, Player player, BankAccount account) {
        // 活期存款按钮
        ItemStack currentDeposit = new ItemStack(Material.GREEN_WOOL);
        ItemMeta currentMeta = currentDeposit.getItemMeta();
        currentMeta.setDisplayName(MessageUtils.color("💚 活期存款"));
        
        List<String> currentLore = new ArrayList<>();
        currentLore.add(MessageUtils.color("&7» 利率: &b" + formatPercentage(getCurrentAccountRate()) + "%/年"));
        currentLore.add(MessageUtils.color("&7» 随时存取，灵活方便"));
        currentLore.add("");
        currentLore.add(MessageUtils.color("&e点击进行活期存款"));
        currentMeta.setLore(currentLore);
        currentDeposit.setItemMeta(currentMeta);
        
        gui.setItem(1, new GuiItem(currentDeposit, event -> {
            player.closeInventory();
            CurrentDepositGUI currentGUI = new CurrentDepositGUI(plugin);
            currentGUI.openCurrentDepositInterface(player, account);
        }));
        
        // 定期存款按钮
        ItemStack fixedDeposit = createFixedDepositButton();
        gui.setItem(4, new GuiItem(fixedDeposit, event -> {
            player.closeInventory();
            FixedDepositGUI fixedGUI = new FixedDepositGUI(plugin);
            fixedGUI.openFixedDepositInterface(player, account);
        }));
        
        // 大额存款按钮（装饰性）
        ItemStack largeDeposit = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta largeMeta = largeDeposit.getItemMeta();
        largeMeta.setDisplayName(MessageUtils.color("🏆 大额存款"));
        
        List<String> largeLore = new ArrayList<>();
        largeLore.add(MessageUtils.color("&7» 专属大额存款服务"));
        largeLore.add(MessageUtils.color("&7» 更高利率，更大收益"));
        largeLore.add("");
        largeLore.add(MessageUtils.color("&c功能开发中..."));
        largeMeta.setLore(largeLore);
        largeDeposit.setItemMeta(largeMeta);
        gui.setItem(7, new GuiItem(largeDeposit));
    }
    
    /**
     * 创建定期存款按钮
     */
    private ItemStack createFixedDepositButton() {
        ItemStack fixedDeposit = new ItemStack(Material.BLUE_WOOL);
        ItemMeta fixedMeta = fixedDeposit.getItemMeta();
        fixedMeta.setDisplayName(MessageUtils.color("🔵 定期存款"));
        
        List<String> fixedLore = new ArrayList<>();
        
        // 获取各期限利率
        Map<Integer, Double> termRates = getAvailableTermRates();
        fixedLore.add(MessageUtils.color("&7» 多种期限选择："));
        
        for (Map.Entry<Integer, Double> entry : termRates.entrySet()) {
            int months = entry.getKey();
            double rate = entry.getValue();
            fixedLore.add(MessageUtils.color("  &7- &f" + months + "个月: &e" + formatPercentage(rate) + "%/年"));
        }
        
        fixedLore.add("");
        fixedLore.add(MessageUtils.color("&b点击选择定期存款"));
        fixedMeta.setLore(fixedLore);
        fixedDeposit.setItemMeta(fixedMeta);
        
        return fixedDeposit;
    }
    
    /**
     * 设置功能按钮（第4行）
     */
    private void setupFunctionButtons(Gui gui, Player player, BankAccount mainAccount, 
                                    List<BankAccount> playerAccounts) {
        // 账户管理
        if (playerAccounts.size() > 1) {
            ItemStack accountManagement = new ItemStack(Material.CHEST);
            ItemMeta accountMeta = accountManagement.getItemMeta();
            accountMeta.setDisplayName(MessageUtils.color("📁 账户管理"));
            
            List<String> accountLore = new ArrayList<>();
            accountLore.add(MessageUtils.color("&7» 管理您的多个银行账户"));
            accountLore.add(MessageUtils.color("&7» 当前账户数: &e" + playerAccounts.size()));
            accountLore.add("");
            accountLore.add(MessageUtils.color("&e点击查看所有账户"));
            accountMeta.setLore(accountLore);
            accountManagement.setItemMeta(accountMeta);
            
            gui.setItem(1, new GuiItem(accountManagement, event -> {
                player.closeInventory();
                showAccountSelection(player, mainAccount, playerAccounts);
            }));
        }
        
        // 交易历史
        ItemStack transactionHistory = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta historyMeta = transactionHistory.getItemMeta();
        historyMeta.setDisplayName(MessageUtils.color("📋 交易历史"));
        
        List<String> historyLore = new ArrayList<>();
        historyLore.add(MessageUtils.color("&7» 查看账户交易记录"));
        historyLore.add(MessageUtils.color("&7» 存取款、转账明细"));
        historyLore.add("");
        historyLore.add(MessageUtils.color("&6点击查看历史记录"));
        historyMeta.setLore(historyLore);
        transactionHistory.setItemMeta(historyMeta);
        
        gui.setItem(4, new GuiItem(transactionHistory, event -> {
            player.sendMessage(MessageUtils.color("ℹ️ 交易历史功能开发中..."));
            // TODO: 实现交易历史GUI
        }));
        
        // 帮助信息
        ItemStack helpInfo = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta helpMeta = helpInfo.getItemMeta();
        helpMeta.setDisplayName(MessageUtils.color("❓ 帮助信息"));
        
        List<String> helpLore = new ArrayList<>();
        helpLore.add(MessageUtils.color("&7» 活期存款：随时存取，灵活方便"));
        helpLore.add(MessageUtils.color("&7» 定期存款：期限越长，利率越高"));
        helpLore.add(MessageUtils.color("&7» 利息按日复利计算"));
        helpLore.add("");
        helpLore.add(MessageUtils.color("&e点击查看详细帮助"));
        helpMeta.setLore(helpLore);
        helpInfo.setItemMeta(helpMeta);
        
        gui.setItem(7, new GuiItem(helpInfo, event -> {
            showHelpInfo(player);
        }));
    }
    
    /**
     * 设置导航行（第5行）
     */
    private void setupNavigationRow(Gui gui, Player player) {
        // 返回按钮（左侧）
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(MessageUtils.color("⬅️ 返回"));
        back.setItemMeta(backMeta);
        gui.setItem(3, new GuiItem(back, event -> {
            player.closeInventory();
            // 这里可以返回到上一级界面
        }));
        
        // 刷新按钮（中心）
        ItemStack refresh = new ItemStack(Material.COMPASS);
        ItemMeta refreshMeta = refresh.getItemMeta();
        refreshMeta.setDisplayName(MessageUtils.color("🔄 刷新"));
        
        List<String> refreshLore = new ArrayList<>();
        refreshLore.add(MessageUtils.color("&7» 更新账户信息"));
        refreshLore.add(MessageUtils.color("&7» 获取最新余额数据"));
        refreshMeta.setLore(refreshLore);
        refresh.setItemMeta(refreshMeta);
        
        gui.setItem(4, new GuiItem(refresh, event -> {
            player.closeInventory();
            // 重新打开界面以刷新
            Bukkit.getScheduler().runTaskLater((org.bukkit.plugin.Plugin) plugin, () -> openBankInterface(player), 1L);
        }));
        
        // 设置按钮（右侧）
        ItemStack settings = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta settingsMeta = settings.getItemMeta();
        settingsMeta.setDisplayName(MessageUtils.color("⚙️ 设置"));
        settings.setItemMeta(settingsMeta);
        gui.setItem(5, new GuiItem(settings, event -> {
            player.sendMessage(MessageUtils.color("⚙️ 银行设置功能开发中..."));
        }));
    }
    
    /**
     * 设置关闭按钮（第6行）
     */
    private void setupCloseButton(Gui gui) {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(MessageUtils.color("❌ 关闭"));
        close.setItemMeta(closeMeta);
        
        gui.setItem(4, new GuiItem(close, event -> {
            event.getWhoClicked().closeInventory();
        }));
    }
    
    /**
     * 设置装饰性边框
     */
    private void setupDecorations(Gui gui) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        
        // 填充边框
        int[] borderSlots = {9, 17, 18, 26, 27, 35, 36, 44, 45, 53};
        for (int slot : borderSlots) {
            gui.setItem(slot, new GuiItem(border));
        }
    }
    
    /**
     * 显示账户选择界面
     */
    private void showAccountSelection(Player player, BankAccount mainAccount, List<BankAccount> accounts) {
        PaginatedGui selectionGui = Gui.paginated()
                .title(MessageUtils.miniMessage("选择银行账户"))
                .rows(6)
                .disableAllInteractions()
                .create();
        
        for (BankAccount account : accounts) {
            ItemStack accountItem = createAccountItem(account);
            GuiItem accountGuiItem = new GuiItem(accountItem, event -> {
                player.closeInventory();
                // 重新打开主界面，使用选择的账户
                bankAccountManager.updateActiveAccount(player.getUniqueId(), account.getAccountId());
                openBankInterface(player);
            });
            
            selectionGui.addItem(accountGuiItem);
        }
        
        // 添加分页控件
        setupPaginationControls(selectionGui, player);
        
        selectionGui.open(player);
    }
    
    /**
     * 创建账户展示项
     */
    private ItemStack createAccountItem(BankAccount account) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(MessageUtils.color("📄 " + account.getAccountNumber()));
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color("&7» 类型: " + getAccountTypeDisplayName(account.getAccountType())));
        lore.add(MessageUtils.color("&7» 余额: &e" + formatCurrency(account.getCurrentBalance())));
        lore.add(MessageUtils.color("&7» 定期: &e" + formatCurrency(account.getTotalFixedDepositAmount())));
        lore.add(MessageUtils.color("&7» 状态: " + getAccountStatusDisplayName(account.getStatus())));
        lore.add("");
        lore.add(MessageUtils.color("&e点击查看详情"));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * 设置分页控件
     */
    private void setupPaginationControls(PaginatedGui gui, Player player) {
        ItemStack previous = new ItemStack(Material.ARROW);
        ItemMeta previousMeta = previous.getItemMeta();
        previousMeta.setDisplayName(MessageUtils.color("⬅️ 上一页"));
        previous.setItemMeta(previousMeta);
        
        gui.setItem(45, new GuiItem(previous, event -> gui.previous()));
        
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.setDisplayName(MessageUtils.color("➡️ 下一页"));
        next.setItemMeta(nextMeta);
        
        gui.setItem(53, new GuiItem(next, event -> gui.next()));
    }
    
    /**
     * 显示帮助信息
     */
    private void showHelpInfo(Player player) {
        List<String> helpMessages = Arrays.asList(
                "=== 银行服务帮助 ===",
                "",
                "📌 活期存款：",
                "   • 随时存取，灵活方便",
                "   • 按日复利计息",
                "   • 适合日常使用",
                "",
                "📌 定期存款：",
                "   • 期限越长，利率越高",
                "   • 到期自动转入活期账户",
                "   • 提前支取有罚金",
                "",
                "📌 利息计算：",
                "   • 活期：日复利",
                "   • 定期：到期一次性计息",
                "   • 利率根据经济环境和信用评分动态调整",
                "",
                "« 返回银行界面使用 /yae bank"
        );
        
        helpMessages.forEach(message -> player.sendMessage(MessageUtils.color(message)));
    }
    
    /**
     * 工具方法：格式化货币
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "¥0.00";
        String symbol = configuration.getCurrency().getSymbol();
        return symbol + amount.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 工具方法：格式化百分比
     */
    private String formatPercentage(double rate) {
        return String.format("%.2f", rate * 100);
    }
    
    /**
     * 获取活期账户利率
     */
    private double getCurrentAccountRate() {
        if (interestCalculator != null) {
            return interestCalculator.getCurrentAccountRate();
        }
        return configuration.getFeatures().getBanking().getDefaultInterestRate();
    }
    
    /**
     * 获取可用定期期限利率
     */
    private Map<Integer, Double> getAvailableTermRates() {
        Map<Integer, Double> rates = new LinkedHashMap<>();
        rates.put(3, getTermInterestRate(3));
        rates.put(6, getTermInterestRate(6));
        rates.put(12, getTermInterestRate(12));
        rates.put(24, getTermInterestRate(24));
        return rates;
    }
    
    /**
     * 获取定期利率
     */
    private double getTermInterestRate(int months) {
        if (interestCalculator != null) {
            return interestCalculator.getTermInterestRate(months);
        }
        return configuration.getFeatures().getBanking().getTermInterestRate(months);
    }
    
    /**
     * 计算月利息
     */
    private BigDecimal calculateMonthlyInterest(BigDecimal principal, double rate) {
        if (interestCalculator != null) {
            return interestCalculator.calculateSimpleInterest(principal, BigDecimal.valueOf(rate), 30);
        }
        return principal.multiply(BigDecimal.valueOf(rate)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算年利息
     */
    private BigDecimal calculateYearlyInterest(BigDecimal principal, double rate) {
        if (interestCalculator != null) {
            return interestCalculator.calculateSimpleInterest(principal, BigDecimal.valueOf(rate), 365);
        }
        return principal.multiply(BigDecimal.valueOf(rate));
    }
    
    /**
     * 获取账户类型显示名称
     */
    private String getAccountTypeDisplayName(BankAccount.AccountType type) {
        switch (type) {
            case CHECKING: return MessageUtils.color("&a活期账户");
            case SAVINGS: return MessageUtils.color("&e储蓄账户");
            case FIXED_DEPOSIT: return MessageUtils.color("&9定期账户");
            case LOAN: return MessageUtils.color("&c贷款账户");
            default: return MessageUtils.color("&7未知类型");
        }
    }
    
    /**
     * 获取账户状态显示名称
     */
    private String getAccountStatusDisplayName(BankAccount.AccountStatus status) {
        switch (status) {
            case ACTIVE: return MessageUtils.color("&a正常");
            case FROZEN: return MessageUtils.color("&c冻结");
            case CLOSED: return MessageUtils.color("&7已关闭");
            case SUSPENDED: return MessageUtils.color("&6暂停");
            default: return MessageUtils.color("&7未知");
        }
    }
    
    /**
     * 获取信用评级
     */
    private String getCreditRating(int score) {
        if (score >= 800) return MessageUtils.color("&a优秀");
        if (score >= 740) return MessageUtils.color("&b良好");
        if (score >= 670) return MessageUtils.color("&e中等");
        if (score >= 580) return MessageUtils.color("&6较差");
        return MessageUtils.color("&c很差");
    }
}
