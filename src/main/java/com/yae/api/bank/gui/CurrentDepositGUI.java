package com.yae.api.bank.gui;

import com.yae.api.bank.BankAccount;
import com.yae.api.bank.DepositService;
import com.yae.api.core.YAECore;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.LanguageManager;
import com.yae.utils.MessageUtils;
import com.yae.utils.TimeUtils;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.Arrays;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 活期存款GUI界面
 * 提供快速存款选择（+1/+10/+100/+1000/Max/手动输入）
 */
@SuppressWarnings("deprecation")
public class CurrentDepositGUI {
    
    private final YAECore plugin;
    private final DepositService depositService;
    private final LanguageManager languageManager;
    private final Configuration configuration;
    private Economy vaultEconomy;
    
    private static final int ROWS = 6;
    private static final String TITLE = "💚 活期存款";
    
    // 快速存款金额选项
    private static final BigDecimal[] QUICK_AMOUNTS = {
            new BigDecimal(1),
            new BigDecimal(10),
            new BigDecimal(100),
            new BigDecimal(1000),
            new BigDecimal(10000)
    };
    
    public CurrentDepositGUI(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.depositService = new DepositService(plugin);
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        this.configuration = plugin.getMainConfiguration();
        
        // 初始化Vault经济
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                this.vaultEconomy = Bukkit.getServer().getServicesManager()
                        .getRegistration(Economy.class).getProvider();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to initialize Vault economy: " + e.getMessage());
            }
        }
    }
    
    /**
     * 打开发期存款界面
     */
    public void openCurrentDepositInterface(@NotNull Player player, @NotNull BankAccount account) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(account, "Account cannot be null");
        
        if (!depositService.isEnabled()) {
            player.sendMessage(MessageUtils.color("❌ 存款服务暂不可用"));
            return;
        }
        
        Gui gui = Gui.gui()
                .title(MessageUtils.miniMessage(TITLE))
                .rows(ROWS)
                .disableAllInteractions()
                .create();
        
        setupCurrentDepositInterface(gui, player, account);
        gui.open(player);
    }
    
    /**
     * 设置活期存款界面
     */
    private void setupCurrentDepositInterface(Gui gui, Player player, BankAccount account) {
        // Clear GUI by removing all existing items
        // Since the GUI library might not have a clear() method, we'll handle it differently
        for (int i = 0; i < 54; i++) { // Standard GUI size
            gui.setItem(i, new GuiItem(new ItemStack(Material.AIR)));
        }
        
        // 第1行：账户信息头
        setupAccountHeader(gui, account);
        
        // 第2行：快速存款按钮
        setupQuickDepositButtons(gui, player, account);
        
        // 第3行：手动输入和自定义金额
        setupManualDepositSection(gui, player, account);
        
        // 第4行：利率预览和收益展示
        setupInterestPreview(gui, player, account);
        
        // 第5行：导航和操作
        setupNavigationRow(gui, player, account);
        
        // 第6行：关闭按钮
        setupCloseButton(gui, player);
        
        // 装饰性边框
        setupDecorations(gui);
    }
    
    /**
     * 设置账户信息头部
     */
    private void setupAccountHeader(Gui gui, BankAccount account) {
        // 账户信息和当前余额
        ItemStack accountInfo = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = accountInfo.getItemMeta();
        infoMeta.setDisplayName(MessageUtils.color("📖 账户信息"));
        
        List<String> infoLore = new ArrayList<>();
        infoLore.add(MessageUtils.color("&7» 账户号码: &e" + account.getAccountNumber()));
        infoLore.add(MessageUtils.color("&7» 当前余额: &a" + formatCurrency(account.getCurrentBalance())));
        infoLore.add(MessageUtils.color("&7» 可用余额: &b" + formatCurrency(account.getAvailableBalance())));
        infoLore.add(MessageUtils.color("&7» 冻结金额: &c" + formatCurrency(account.getFrozenAmount())));
        
        infoMeta.setLore(infoLore);
        accountInfo.setItemMeta(infoMeta);
        gui.setItem(0, new GuiItem(accountInfo));
        
        // 利率信息
        ItemStack rateInfo = new ItemStack(Material.SUNFLOWER);
        ItemMeta rateMeta = rateInfo.getItemMeta();
        rateMeta.setDisplayName(MessageUtils.color("📊 利率信息"));
        
        List<String> rateLore = new ArrayList<>();
        double currentRate = depositService.getCurrentInterestRate();
        rateLore.add(MessageUtils.color("&7» 活期年利率: &b" + formatPercentage(currentRate) + "%"));
        rateLore.add(MessageUtils.color("&7» 计息方式: &f日复利"));
        rateLore.add(MessageUtils.color("&7» 付息周期: &f每日"));
        
        rateMeta.setLore(rateLore);
        rateInfo.setItemMeta(rateMeta);
        gui.setItem(8, new GuiItem(rateInfo));
    }
    
    /**
     * 设置快速存款按钮
     */
    private void setupQuickDepositButtons(Gui gui, Player player, BankAccount account) {
        int[] positions = {1, 2, 3, 5, 6}; // 按钮位置
        
        for (int i = 0; i < QUICK_AMOUNTS.length && i < positions.length; i++) {
            BigDecimal amount = QUICK_AMOUNTS[i];
            int position = positions[i];
            
            ItemStack depositButton = createDepositAmountButton(amount, player);
            
            gui.setItem(position, new GuiItem(depositButton, event -> {
                processCurrentDeposit(player, account, amount, gui);
            }));
        }
        
        // "全部存款"按钮
        ItemStack depositAll = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta allMeta = depositAll.getItemMeta();
        allMeta.setDisplayName(MessageUtils.color("💰 全部存款"));
        
        BigDecimal playerBalance = getPlayerBalance(player);
        List<String> allLore = new ArrayList<>();
        allLore.add(MessageUtils.color("&7» 当前可存: &e" + formatCurrency(playerBalance)));
        allLore.add(MessageUtils.color("&7» 存入所有可用资金"));
        allLore.add("");
        allLore.add(MessageUtils.color("&6⚡ 一键存款"));
        allMeta.setLore(allLore);
        depositAll.setItemMeta(allMeta);
        
        gui.setItem(4, new GuiItem(depositAll, event -> {
            if (playerBalance.compareTo(BigDecimal.ZERO) > 0) {
                processCurrentDeposit(player, account, playerBalance, gui);
            } else {
                player.sendMessage(MessageUtils.color("❌ 您没有可存款的资金"));
            }
        }));
    }
    
    /**
     * 创建存款金额按钮
     */
    private ItemStack createDepositAmountButton(BigDecimal amount, Player player) {
        ItemStack button;
        
        if (amount.compareTo(new BigDecimal(1)) == 0) {
            button = new ItemStack(Material.IRON_NUGGET);
        } else if (amount.compareTo(new BigDecimal(10)) == 0) {
            button = new ItemStack(Material.IRON_INGOT);
        } else if (amount.compareTo(new BigDecimal(100)) == 0) {
            button = new ItemStack(Material.IRON_BLOCK);
        } else if (amount.compareTo(new BigDecimal(1000)) == 0) {
            button = new ItemStack(Material.GOLD_INGOT);
        } else {
            button = new ItemStack(Material.GOLD_BLOCK);
        }
        
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(MessageUtils.color("💵 " + formatCurrency(amount)));
        
        List<String> lore = new ArrayList<>();
        
        // 检查玩家是否有足够的钱
        BigDecimal playerBalance = getPlayerBalance(player);
        lore.add(MessageUtils.color("&7» 您的余额: " + formatCurrency(playerBalance)));
        
        if (playerBalance.compareTo(amount) >= 0) {
            lore.add(MessageUtils.color("&a✔ 资金充足"));
            lore.add("");
            lore.add(MessageUtils.color("&e💡 点击快速存款"));
        } else {
            lore.add(MessageUtils.color("&c✖ 资金不足"));
            BigDecimal needed = amount.subtract(playerBalance);
            lore.add(MessageUtils.color("&c需要: " + formatCurrency(needed)));
        }
        
        meta.setLore(lore);
        button.setItemMeta(meta);
        
        return button;
    }
    
    /**
     * 设置手动存款部分
     */
    private void setupManualDepositSection(Gui gui, Player player, BankAccount account) {
        // 手动输入按钮
        ItemStack manualInput = new ItemStack(Material.ANVIL);
        ItemMeta manualMeta = manualInput.getItemMeta();
        manualMeta.setDisplayName(MessageUtils.color("✏️ 手动输入金额"));
        
        List<String> manualLore = new ArrayList<>();
        manualLore.add(MessageUtils.color("&7» 输入自定义存款金额"));
        manualLore.add(MessageUtils.color("&7» 支持小数和整数"));
        manualLore.add(MessageUtils.color("&7» 例如: 250.50"));
        manualLore.add("");
        manualLore.add(MessageUtils.color("&b💡 点击输入金额"));
        manualMeta.setLore(manualLore);
        manualInput.setItemMeta(manualMeta);
        
        gui.setItem(19, new GuiItem(manualInput, event -> {
            player.closeInventory();
            promptManualAmount(player, account);
        }));
        
        // 自定义金额预设
        BigDecimal[] customAmounts = {
                new BigDecimal(50),
                new BigDecimal(500),
                new BigDecimal(2500),
                new BigDecimal(5000),
                new BigDecimal(20000),
                new BigDecimal(50000)
        };
        
        int[] customPositions = {20, 21, 22, 23, 24, 25};
        
        for (int i = 0; i < customAmounts.length && i < customPositions.length; i++) {
            BigDecimal amount = customAmounts[i];
            int position = customPositions[i];
            
            ItemStack customButton = createCustomAmountButton(amount, player);
            
            gui.setItem(position, new GuiItem(customButton, event -> {
                processCurrentDeposit(player, account, amount, gui);
            }));
        }
    }
    
    /**
     * 创建自定义金额按钮
     */
    private ItemStack createCustomAmountButton(BigDecimal amount, Player player) {
        ItemStack button = new ItemStack(Material.PAPER);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(MessageUtils.color("📝 " + formatCurrency(amount)));
        
        List<String> lore = new ArrayList<>();
        BigDecimal playerBalance = getPlayerBalance(player);
        
        if (playerBalance.compareTo(amount) >= 0) {
            lore.add(MessageUtils.color("&a✔ 可存款"));
        } else {
            lore.add(MessageUtils.color("&c✖ 余额不足"));
        }
        
        lore.add("");
        lore.add(MessageUtils.color("&e点击存款"));
        meta.setLore(lore);
        button.setItemMeta(meta);
        
        return button;
    }
    
    /**
     * 设置利息预览
     */
    private void setupInterestPreview(Gui gui, Player player, BankAccount account) {
        // 利息计算器
        ItemStack calculator = new ItemStack(Material.PAPER); // Changed from CALCULATOR to PAPER for compatibility
        ItemMeta calcMeta = calculator.getItemMeta();
        calcMeta.setDisplayName(MessageUtils.color("🔢 利息计算器"));
        
        List<String> calcLore = new ArrayList<>();
        
        double currentRate = depositService.getCurrentInterestRate();
        
        // 不同金额的预期收益
        BigDecimal[] exampleAmounts = {
                new BigDecimal(1000),
                new BigDecimal(5000),
                new BigDecimal(10000)
        };
        
        calcLore.add(MessageUtils.color("&7» 当前年利率: &b" + formatPercentage(currentRate) + "%"));
        calcLore.add("");
        calcLore.add(MessageUtils.color("&7预期收益（按月）:"));
        
        for (BigDecimal amount : exampleAmounts) {
            BigDecimal monthlyInterest = calculateMonthlyInterest(amount, currentRate);
            calcLore.add(MessageUtils.color("  &7- " + formatCurrency(amount) + ": &e+" + formatCurrency(monthlyInterest)));
        }
        
        calcLore.add("");
        calcLore.add(MessageUtils.color("&b» 按日复利计息"));
        calcLore.add(MessageUtils.color("&e点击详细了解"));
        
        calcMeta.setLore(calcLore);
        calculator.setItemMeta(calcMeta);
        
        gui.setItem(31, new GuiItem(calculator, event -> {
            showDetailedInterestInfo(player, account);
        }));
    }
    
    /**
     * 设置导航行
     */
    private void setupNavigationRow(Gui gui, Player player, BankAccount account) {
        // 返回银行主界面
        ItemStack backToMain = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backToMain.getItemMeta();
        backMeta.setDisplayName(MessageUtils.color("⬅️ 返回银行"));
        
        List<String> backLore = new ArrayList<>();
        backLore.add(MessageUtils.color("&7» 返回银行主界面"));
        backLore.add(MessageUtils.color("&7» 查看其他服务"));
        backMeta.setLore(backLore);
        backToMain.setItemMeta(backMeta);
        
        gui.setItem(37, new GuiItem(backToMain, event -> {
            player.closeInventory();
            BankChestGUI mainGui = new BankChestGUI(plugin);
            mainGui.openBankInterface(player);
        }));
        
        // 定期存款
        ItemStack fixedDeposit = new ItemStack(Material.BOOK);
        ItemMeta fixedMeta = fixedDeposit.getItemMeta();
        fixedMeta.setDisplayName(MessageUtils.color("📘 定期存款"));
        
        List<String> fixedLore = new ArrayList<>();
        fixedLore.add(MessageUtils.color("&7» 更高利率，定期收益"));
        fixedLore.add(MessageUtils.color("&7» 多种期限可选"));
        fixedLore.add(MessageUtils.color("&7» 到期自动转入活期"));
        fixedLore.add("");
        fixedLore.add(MessageUtils.color("&6点击查看定期存款"));
        fixedMeta.setLore(fixedLore);
        fixedDeposit.setItemMeta(fixedMeta);
        
        gui.setItem(39, new GuiItem(fixedDeposit, event -> {
            player.closeInventory();
            FixedDepositGUI fixedGui = new FixedDepositGUI(plugin);
            fixedGui.openFixedDepositInterface(player, account);
        }));
        
        // 刷新余额
        ItemStack refresh = new ItemStack(Material.COMPASS);
        ItemMeta refreshMeta = refresh.getItemMeta();
        refreshMeta.setDisplayName(MessageUtils.color("🔄 刷新信息"));
        
        List<String> refreshLore = new ArrayList<>();
        refreshLore.add(MessageUtils.color("&7» 更新账户信息"));
        refreshLore.add(MessageUtils.color("&7» 获取最新数据"));
        refreshLore.add("");
        refreshLore.add(MessageUtils.color("&b点击刷新"));
        refreshMeta.setLore(refreshLore);
        refresh.setItemMeta(refreshMeta);
        
        gui.setItem(41, new GuiItem(refresh, event -> {
            player.closeInventory();
            // 重新打开界面刷新数据
            openCurrentDepositInterface(player, account);
        }));
        
        // 帮助信息
        ItemStack help = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta helpMeta = help.getItemMeta();
        helpMeta.setDisplayName(MessageUtils.color("❓ 帮助"));
        helpMeta.setLore(Arrays.asList(
                MessageUtils.color("&7» 活期存款帮助"),
                MessageUtils.color("&7» 利率和使用说明"),
                "",
                MessageUtils.color("&e点击查看")
        ));
        help.setItemMeta(helpMeta);
        
        gui.setItem(43, new GuiItem(help, event -> {
            player.closeInventory();
            showHelpInfo(player);
        }));
    }
    
    /**
     * 设置关闭按钮
     */
    private void setupCloseButton(Gui gui, Player player) {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(MessageUtils.color("❌ 关闭"));
        
        List<String> closeLore = new ArrayList<>();
        closeLore.add(MessageUtils.color("&7» 关闭存款界面"));
        closeLore.add(MessageUtils.color("&7» 返回游戏"));
        closeMeta.setLore(closeLore);
        close.setItemMeta(closeMeta);
        
        gui.setItem(49, new GuiItem(close, event -> {
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
        int[] borderSlots = {0, 8, 9, 17, 18, 26, 27, 35, 42, 44, 45, 46, 47, 48, 50, 51, 52, 53};
        for (int slot : borderSlots) {
            gui.setItem(slot, new GuiItem(border));
        }
    }
    
    /**
     * 处理活期存款
     */
    private void processCurrentDeposit(Player player, BankAccount account, BigDecimal amount, Gui gui) {
        player.sendMessage(MessageUtils.color("ℹ️ 正在处理活期存款..."));
        
        CompletableFuture<DepositService.DepositResult> future = 
                depositService.depositCurrent(player.getUniqueId(), amount);
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                player.sendMessage(MessageUtils.color("❌ 存款处理失败: " + throwable.getMessage()));
                return;
            }
            
            if (result.isSuccess()) {
                player.sendMessage(MessageUtils.color("✅ 活期存款成功！"));
                player.sendMessage(MessageUtils.color("   存款金额: " + formatCurrency(amount)));
                player.sendMessage(MessageUtils.color("   新余额: " + formatCurrency(result.getFinalBalance())));
                
                // 关闭当前界面并刷新主界面
                Bukkit.getScheduler().runTask((org.bukkit.plugin.Plugin) plugin, () -> {
                    player.closeInventory();
                    openCurrentDepositInterface(player, account);
                });
            } else {
                String errorMessage = result.getErrorMessage();
                player.sendMessage(MessageUtils.color("❌ 存款失败: " + errorMessage));
            }
        });
    }
    
    /**
     * 提示手动输入金额
     */
    private void promptManualAmount(Player player, BankAccount account) {
        player.sendMessage(MessageUtils.color("💡 请输入存款金额（例如：250.50）"));
        player.sendMessage(MessageUtils.color("💡 或者在聊天中输入 'cancel' 取消"));
        
        // 这里需要实现玩家输入监听
        // 由于GUI框架限制，这里使用命令方式处理
        player.sendMessage(MessageUtils.color("💡 请使用命令: /yae deposit current <amount>"));
    }
    
    /**
     * 显示详细利息信息
     */
    private void showDetailedInterestInfo(Player player, BankAccount account) {
        List<String> messages = new ArrayList<>();
        
        double currentRate = depositService.getCurrentInterestRate();
        BigDecimal currentBalance = account.getCurrentBalance();
        
        messages.addAll(Arrays.asList(
                "=== 活期存款利息详情 ===",
                "",
                "📌 基本利率: " + formatPercentage(currentRate) + "%/年",
                "📌 计息方式: 日复利",
                "📌 付息周期: 每日",
                "",
                "当前余额: " + formatCurrency(currentBalance),
                "",
                "预期收益:"
        ));
        
        // 计算不同时期的预期收益
        int[] days = {1, 7, 30, 90, 365};
        String[] labels = {"明天", "一周后", "一个月后", "三个月后", "一年后"};
        
        for (int i = 0; i < days.length; i++) {
            BigDecimal interest = calculateInterestForDays(currentBalance, currentRate, days[i]);
            messages.add("  " + labels[i] + ": +" + formatCurrency(interest));
        }
        
        messages.add("");
        messages.add("💡 提示：利息每日自动计算并计入账户");
        
        messages.forEach(message -> player.sendMessage(MessageUtils.color(message)));
    }
    
    /**
     * 显示帮助信息
     */
    private void showHelpInfo(Player player) {
        List<String> messages = Arrays.asList(
                "=== 活期存款帮助 ===",
                "",
                "📌 快速存款：",
                "   • 点击预设金额按钮快速存款",
                "   • 全部存款：存入所有可用资金",
                "",
                "📌 自定义存款：",
                "   • 手动输入任意金额",
                "   • 使用命令 /yae deposit current <amount>",
                "",
                "📌 利率说明：",
                "   • 活期存款按日复利计息",
                "   • 利率会根据经济环境动态调整",
                "   • 利息每日自动计入账户",
                "",
                "📌 注意事项：",
                "   • 活期存款随时可以支取",
                "   • 存款金额必须大于0",
                "   • 需要有足够的游戏货币",
                "",
                "« 返回使用 /yae bank"
        );
        
        messages.forEach(message -> player.sendMessage(MessageUtils.color(message)));
    }
    
    /**
     * 获取玩家余额
     */
    private BigDecimal getPlayerBalance(Player player) {
        if (vaultEconomy != null) {
            return BigDecimal.valueOf(vaultEconomy.getBalance(player));
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * 计算月利息
     */
    private BigDecimal calculateMonthlyInterest(BigDecimal principal, double rate) {
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // 简单的月利息计算
        return principal.multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算指定天数的利息
     */
    private BigDecimal calculateInterestForDays(BigDecimal principal, double rate, int days) {
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        return principal.multiply(BigDecimal.valueOf(rate))
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 格式化货币
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "¥0.00";
        String symbol = configuration.getCurrency().getSymbol();
        return symbol + amount.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 格式化百分比
     */
    private String formatPercentage(double rate) {
        return String.format("%.2f", rate * 100);
    }
}
