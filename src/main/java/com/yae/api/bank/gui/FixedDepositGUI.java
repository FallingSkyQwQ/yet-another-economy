package com.yae.api.bank.gui;

import com.yae.api.bank.*;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 定期存款GUI界面
 * 提供定期存款期限选择（7/14/30/60/90天）和收益预览
 */
@SuppressWarnings("deprecation")
public class FixedDepositGUI {
    
    private final YAECore plugin;
    private final DepositService depositService;
    private final LanguageManager languageManager;
    private final Configuration configuration;
    private Economy vaultEconomy;
    
    private static final int ROWS = 6;
    private static final String TITLE = "🔵 定期存款";
    
    // 定期存款期限选项（月数）
    private static final int[] TERM_MONTHS = {3, 6, 12, 24, 36, 60};
    private static final String[] TERM_NAMES = {"3个月", "6个月", "1年", "2年", "3年", "5年"};
    
    // 默认存款金额选项
    private static final BigDecimal[] DEFAULT_AMOUNTS = {
            new BigDecimal(1000),
            new BigDecimal(5000),
            new BigDecimal(10000),
            new BigDecimal(50000),
            new BigDecimal(100000),
            new BigDecimal(500000)
    };
    
    // 当前选中的存款配置
    private UUID selectedDepositId;
    private BigDecimal selectedAmount;
    private FixedDeposit.DepositTerm selectedTerm;
    
    public FixedDepositGUI(@NotNull YAECore plugin) {
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
        
        this.selectedDepositId = null;
        this.selectedAmount = BigDecimal.ZERO;
        this.selectedTerm = null;
    }
    
    /**
     * 打开定期存款界面
     */
    public void openFixedDepositInterface(@NotNull Player player, @NotNull BankAccount account) {
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
        
        setupFixedDepositInterface(gui, player, account);
        gui.open(player);
    }
    
    /**
     * 设置定期存款界面
     */
    private void setupFixedDepositInterface(Gui gui, Player player, BankAccount account) {
        // Clear GUI by removing all existing items
        // Since the GUI library might not have a clear() method, we'll handle it differently
        for (int i = 0; i < 54; i++) { // Standard GUI size
            gui.setItem(i, new GuiItem(new ItemStack(Material.AIR)));
        }
        
        // 第1行：账户信息和余额
        setupAccountHeader(gui, account);
        
        // 第2行：期限选择
        setupTermSelection(gui, player, account);
        
        // 第3行：金额选择
        setupAmountSelection(gui, player, account);
        
        // 第4行：收益预览和确认
        setupInterestPreview(gui, player, account);
        
        // 第5行：操作按钮
        setupActionButtons(gui, player, account);
        
        // 第6行：导航和关闭
        setupNavigationRow(gui, player, account);
        
        // 装饰性边框
        setupDecorations(gui);
    }
    
    /**
     * 设置账户信息头部
     */
    private void setupAccountHeader(Gui gui, BankAccount account) {
        ItemStack accountInfo = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = accountInfo.getItemMeta();
        infoMeta.setDisplayName(MessageUtils.color("📖 账户信息"));
        
        List<String> infoLore = new ArrayList<>();
        infoLore.add(MessageUtils.color("&7» 账户号码: &e" + account.getAccountNumber()));
        infoLore.add(MessageUtils.color("&7» 活期余额: &a" + formatCurrency(account.getCurrentBalance())));
        infoLore.add(MessageUtils.color("&7» 定期总额: &e" + formatCurrency(account.getTotalFixedDepositAmount())));
        infoLore.add(MessageUtils.color("&7» 可用余额: &b" + formatCurrency(account.getAvailableBalance())));
        
        infoMeta.setLore(infoLore);
        accountInfo.setItemMeta(infoMeta);
        gui.setItem(0, new GuiItem(accountInfo));
    }
    
    /**
     * 设置期限选择
     */
    private void setupTermSelection(Gui gui, Player player, BankAccount account) {
        ItemStack termTitle = new ItemStack(Material.CLOCK);
        ItemMeta termTitleMeta = termTitle.getItemMeta();
        termTitleMeta.setDisplayName(MessageUtils.color("⏰ 选择存期"));
        
        List<String> termTitleLore = new ArrayList<>();
        termTitleLore.add(MessageUtils.color("&7» 不同期限对应不同利率"));
        termTitleLore.add(MessageUtils.color("&7» 期限越长，利率越高"));
        termTitleLore.add("");
        
        if (selectedTerm != null) {
            termTitleLore.add(MessageUtils.color("&a已选择: " + getTermDisplayName(selectedTerm)));
        } else {
            termTitleLore.add(MessageUtils.color("&e⚡ 请先选择存期"));
        }
        
        termTitleMeta.setLore(termTitleLore);
        termTitle.setItemMeta(termTitleMeta);
        gui.setItem(4, new GuiItem(termTitle));
        
        // 存期选项
        int[] termPositions = {10, 11, 12, 14, 15, 16}; // 存期按钮位置
        
        for (int i = 0; i < TERM_MONTHS.length && i < termPositions.length; i++) {
            int months = TERM_MONTHS[i];
            String name = TERM_NAMES[i];
            int position = termPositions[i];
            
            FixedDeposit.DepositTerm term = getDepositTermByMonths(months);
            double termRate = depositService.getTermInterestRate(term);
            
            ItemStack termButton = createTermButton(name, months, termRate, 
                    selectedTerm != null && selectedTerm.getMonths() == months);
            
            gui.setItem(position, new GuiItem(termButton, event -> {
                selectedTerm = term;
                selectedAmount = BigDecimal.ZERO; // 重置选择金额
                setupFixedDepositInterface(gui, player, account); // 刷新界面
                
                player.sendMessage(MessageUtils.color("✅ 已选择 " + name + " 存期"));
            }));
        }
    }
    
    /**
     * 设置金额选择
     */
    private void setupAmountSelection(Gui gui, Player player, BankAccount account) {
        ItemStack amountTitle = new ItemStack(Material.GOLD_INGOT);
        ItemMeta amountTitleMeta = amountTitle.getItemMeta();
        amountTitleMeta.setDisplayName(MessageUtils.color("💰 选择存款金额"));
        
        List<String> amountTitleLore = new ArrayList<>();
        amountTitleLore.add(MessageUtils.color("&7» 请先选择存款期限"));
        amountTitleLore.add(MessageUtils.color("&7» 然后选择适合的金额"));
        
        if (selectedTerm != null) {
            amountTitleLore.add("");
            double rate = depositService.getTermInterestRate(selectedTerm);
            amountTitleLore.add(MessageUtils.color("&a已选择存期: " + getTermDisplayName(selectedTerm)));
            amountTitleLore.add(MessageUtils.color("&a存期利率: " + formatPercentage(rate) + "%/年"));
        }
        
        amountTitleMeta.setLore(amountTitleLore);
        amountTitle.setItemMeta(amountTitleMeta);
        gui.setItem(4, new GuiItem(amountTitle));
        
        // 快速金额按钮
        int[] amountPositions = {28, 29, 30, 32, 33, 34}; // 金额按钮位置
        
        for (int i = 0; i < DEFAULT_AMOUNTS.length && i < amountPositions.length; i++) {
            BigDecimal amount = DEFAULT_AMOUNTS[i];
            int position = amountPositions[i];
            
            String buttonTitle;
            if (i == 0) buttonTitle = "💸 " + formatCurrency(amount) + " (入门)";
            else if (i == 1) buttonTitle = "💵 " + formatCurrency(amount) + " (标准)";
            else if (i == 2) buttonTitle = "💰 " + formatCurrency(amount) + " (递增)";
            else if (i == 3) buttonTitle = "💎 " + formatCurrency(amount) + " (大额)";
            else if (i == 4) buttonTitle = "🏆 " + formatCurrency(amount) + " (巨资)";
            else buttonTitle = "👑 " + formatCurrency(amount) + " (巨富)";
            
            ItemStack amountButton = createAmountButton(buttonTitle, amount, account.getAvailableBalance(),
                    selectedAmount != null && selectedAmount.compareTo(amount) == 0);
            
            gui.setItem(position, new GuiItem(amountButton, event -> {
                if (selectedTerm == null) {
                    player.sendMessage(MessageUtils.color("❌ 请先选择存款期限"));
                    return;
                }
                
                if (account.getAvailableBalance().compareTo(amount) < 0) {
                    player.sendMessage(MessageUtils.color("❌ 账户余额不足"));
                    return;
                }
                
                selectedAmount = amount;
                setupFixedDepositInterface(gui, player, account); // 刷新界面显示选择
                
                player.sendMessage(MessageUtils.color("✅ 已选择存款金额: " + formatCurrency(amount)));
            }));
        }
        
        // 手动输入金额按钮
        ItemStack manualInput = new ItemStack(Material.ANVIL);
        ItemMeta manualMeta = manualInput.getItemMeta();
        manualMeta.setDisplayName(MessageUtils.color("✏️ 输入自定义金额"));
        
        List<String> manualLore = new ArrayList<>();
        manualLore.add(MessageUtils.color("&7» 输入任意存款金额"));
        manualLore.add(MessageUtils.color("&7» 支持带小数的数字"));
        manualLore.add(MessageUtils.color("&7» 例如: 1234.56"));
        manualLore.add("");
        manualLore.add(MessageUtils.color("&b💡 点击输入金额"));
        manualMeta.setLore(manualLore);
        manualInput.setItemMeta(manualMeta);
        
        gui.setItem(38, new GuiItem(manualInput, event -> {
            if (selectedTerm == null) {
                player.sendMessage(MessageUtils.color("❌ 请先选择存款期限"));
                return;
            }
            
            player.closeInventory();
            promptCustomAmount(player, account);
        }));
        
        // "全部可存" 按钮
        ItemStack depositAll = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta allMeta = depositAll.getItemMeta();
        allMeta.setDisplayName(MessageUtils.color("💎 存全部可用余额"));
        
        List<String> allLore = new ArrayList<>();
        allLore.add(MessageUtils.color("&7» 可用余额: &a" + formatCurrency(account.getAvailableBalance())));
        allLore.add(MessageUtils.color("&7» 将全部可用资金转为定期"));
        allLore.add("");
        allLore.add(MessageUtils.color("&6⚡ 一键转存"));
        allMeta.setLore(allLore);
        depositAll.setItemMeta(allMeta);
        
        gui.setItem(40, new GuiItem(depositAll, event -> {
            if (selectedTerm == null) {
                player.sendMessage(MessageUtils.color("❌ 请先选择存款期限"));
                return;
            }
            
            if (account.getAvailableBalance().compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(MessageUtils.color("❌ 没有可用余额"));
                return;
            }
            
            selectedAmount = account.getAvailableBalance();
            setupFixedDepositInterface(gui, player, account);
            player.sendMessage(MessageUtils.color("✅ 已选择存款全部可用余额: " + formatCurrency(selectedAmount)));
        }));
    }
    
    /**
     * 设置收益预览
     */
    private void setupInterestPreview(Gui gui, Player player, BankAccount account) {
        if (selectedTerm == null || selectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 显示默认的预览信息
            ItemStack previewInfo = new ItemStack(Material.ITEM_FRAME);
            ItemMeta infoMeta = previewInfo.getItemMeta();
            infoMeta.setDisplayName(MessageUtils.color("📊 收益预览"));
            
            List<String> infoLore = new ArrayList<>();
            infoLore.add(MessageUtils.color("&7» 选择期限和金额后"));
            infoLore.add(MessageUtils.color("&7» 将显示详细收益信息"));
            infoLore.add("");
            infoLore.add(MessageUtils.color("&e💡 请先完成选择"));
            
            infoMeta.setLore(infoLore);
            previewInfo.setItemMeta(infoMeta);
            gui.setItem(31, new GuiItem(previewInfo));
            return;
        }
        
        // 计算和显示预期收益
        ItemStack preview = new ItemStack(Material.SUNFLOWER);
        ItemMeta previewMeta = preview.getItemMeta();
        previewMeta.setDisplayName(MessageUtils.color("🎯 预期收益"));
        
        List<String> previewLore = calculateInterestPreview(selectedAmount, selectedTerm);
        previewMeta.setLore(previewLore);
        preview.setItemMeta(previewMeta);
        
        gui.setItem(31, new GuiItem(preview));
    }
    
    /**
     * 设置操作按钮
     */
    private void setupActionButtons(Gui gui, Player player, BankAccount account) {
        boolean canProcess = selectedTerm != null && selectedAmount.compareTo(BigDecimal.ZERO) > 0 
                && account.getAvailableBalance().compareTo(selectedAmount) >= 0;
        
        // 确认存款按钮
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        
        if (canProcess) {
            confirmMeta.setDisplayName(MessageUtils.color("✅ 确认存款"));
            
            List<String> confirmLore = new ArrayList<>();
            confirmLore.add(MessageUtils.color("&7» 期限: &a" + getTermDisplayName(selectedTerm)));
            confirmLore.add(MessageUtils.color("&7» 金额: &e" + formatCurrency(selectedAmount)));
            
            BigDecimal estimatedReturn = getEstimatedReturn(selectedAmount, selectedTerm);
            confirmLore.add(MessageUtils.color("&7» 到期收益: &6" + formatCurrency(estimatedReturn)));
            confirmLore.add("");
            confirmLore.add(MessageUtils.color("&e⚡ 点击确认存款"));
            
            confirmMeta.setLore(confirmLore);
        } else {
            confirmMeta.setDisplayName(MessageUtils.color("❌ 无法存款"));
            
            List<String> errorLore = new ArrayList<>();
            
            if (selectedTerm == null) {
                errorLore.add(MessageUtils.color("&c» 请先选择存款期限"));
            }
            if (selectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                errorLore.add(MessageUtils.color("&c» 请选择存款金额"));
            }
            if (selectedAmount.compareTo(account.getAvailableBalance()) > 0) {
                errorLore.add(MessageUtils.color("&c» 账户余额不足"));
            }
            
            confirmMeta.setLore(errorLore);
        }
        
        confirm.setItemMeta(confirmMeta);
        
        gui.setItem(40, new GuiItem(confirm, event -> {
            if (!canProcess) {
                player.sendMessage(MessageUtils.color("❌ 请先完成存款信息的选择"));
                return;
            }
            
            processFixedDeposit(player, account, selectedAmount, selectedTerm, gui);
        }));
        
        // 取消/重置按钮
        ItemStack reset = new ItemStack(Material.RED_WOOL);
        ItemMeta resetMeta = reset.getItemMeta();
        resetMeta.setDisplayName(MessageUtils.color("🔄 重选"));
        
        List<String> resetLore = new ArrayList<>();
        resetLore.add(MessageUtils.color("&7» 清除当前选择"));
        resetLore.add(MessageUtils.color("&7» 重新选择期限和金额"));
        resetMeta.setLore(resetLore);
        reset.setItemMeta(resetMeta);
        
        gui.setItem(38, new GuiItem(reset, event -> {
            selectedTerm = null;
            selectedAmount = BigDecimal.ZERO;
            selectedDepositId = null;
            setupFixedDepositInterface(gui, player, account);
            player.sendMessage(MessageUtils.color("ℹ️ 已重置选择"));
        }));
        
        // 查看现有定期存款
        ItemStack viewExisting = new ItemStack(Material.BOOKSHELF);
        ItemMeta viewMeta = viewExisting.getItemMeta();
        viewMeta.setDisplayName(MessageUtils.color("📚 查看定期存款"));
        
        List<String> viewLore = new ArrayList<>();
        int fixedCount = account.getFixedDeposits().size();
        viewLore.add(MessageUtils.color("&7» 您有 &e" + fixedCount + " &7笔定期存款"));
        viewLore.add(MessageUtils.color("&7» 查看存款状态和到期时间"));
        viewLore.add("");
        viewLore.add(MessageUtils.color("&6点击查看详情"));
        viewMeta.setLore(viewLore);
        viewExisting.setItemMeta(viewMeta);
        
        gui.setItem(42, new GuiItem(viewExisting, event -> {
            player.closeInventory();
            showFixedDepositsList(player, account);
        }));
    }
    
    /**
     * 设置导航行
     */
    private void setupNavigationRow(Gui gui, Player player, BankAccount account) {
        // 返回活期存款
        ItemStack backToCurrent = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backToCurrent.getItemMeta();
        backMeta.setDisplayName(MessageUtils.color("⬅️ 返回活期存款"));
        
        List<String> backLore = new ArrayList<>();
        backLore.add(MessageUtils.color("&7» 返回活期存款界面"));
        backLore.add(MessageUtils.color("&7» 活期存款随时可取"));
        backMeta.setLore(backLore);
        backToCurrent.setItemMeta(backMeta);
        
        gui.setItem(37, new GuiItem(backToCurrent, event -> {
            player.closeInventory();
            CurrentDepositGUI currentGui = new CurrentDepositGUI(plugin);
            currentGui.openCurrentDepositInterface(player, account);
        }));
        
        // 返回银行主界面
        ItemStack backToMain = new ItemStack(Material.COMPASS);
        ItemMeta mainMeta = backToMain.getItemMeta();
        mainMeta.setDisplayName(MessageUtils.color("🏦 返回银行"));
        
        List<String> mainLore = new ArrayList<>();
        mainLore.add(MessageUtils.color("&7» 返回银行主界面"));
        mainLore.add(MessageUtils.color("&7» 查看其他银行服务"));
        mainMeta.setLore(mainLore);
        backToMain.setItemMeta(mainMeta);
        
        gui.setItem(39, new GuiItem(backToMain, event -> {
            player.closeInventory();
            BankChestGUI mainGui = new BankChestGUI(plugin);
            mainGui.openBankInterface(player);
        }));
        
        // 刷新信息
        ItemStack refresh = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta refreshMeta = refresh.getItemMeta();
        refreshMeta.setDisplayName(MessageUtils.color("🔄 刷新"));
        
        List<String> refreshLore = new ArrayList<>();
        refreshLore.add(MessageUtils.color("&7» 更新利率和账户信息"));
        refreshLore.add(MessageUtils.color("&7» 获取最新数据"));
        refreshMeta.setLore(refreshLore);
        refresh.setItemMeta(refreshMeta);
        
        gui.setItem(41, new GuiItem(refresh, event -> {
            player.closeInventory();
            openFixedDepositInterface(player, account);
        }));
        
        // 帮助信息
        ItemStack help = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta helpMeta = help.getItemMeta();
        helpMeta.setDisplayName(MessageUtils.color("❓ 帮助"));
        helpMeta.setLore(Arrays.asList(
                MessageUtils.color("&7» 定期存款帮助"),
                MessageUtils.color("&7» 了解规则和利率"),
                "",
                MessageUtils.color("&e点击查看")
        ));
        help.setItemMeta(helpMeta);
        
        gui.setItem(43, new GuiItem(help, event -> {
            player.closeInventory();
            showHelpInfo(player);
        }));
        
        // 关闭按钮
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(MessageUtils.color("❌ 关闭"));
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
        int[] borderSlots = {0, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53};
        for (int slot : borderSlots) {
            gui.setItem(slot, new GuiItem(border));
        }
    }
    
    /**
     * 创建期限按钮
     */
    private ItemStack createTermButton(String name, int months, double rate, boolean selected) {
        Material material;
        if (months <= 3) material = Material.LIGHT_BLUE_WOOL;
        else if (months <= 12) material = Material.BLUE_WOOL;
        else if (months <= 24) material = Material.CYAN_WOOL;
        else material = Material.PURPLE_WOOL;
        
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(MessageUtils.color("🕰️ " + name + " (" + formatPercentage(rate) + "%/年)"));
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color("&7» 存期: &a" + months + "个月"));
        lore.add(MessageUtils.color("&7» 年利率: &b" + formatPercentage(rate) + "%"));
        lore.add("");
        
        if (selected) {
            lore.add(MessageUtils.color("&a✔ 已选择"));
            lore.add(MessageUtils.color("&a点击其他期限切换"));
        } else {
            lore.add(MessageUtils.color("&e点击选择此期限"));
        }
        
        meta.setLore(lore);
        button.setItemMeta(meta);
        
        return button;
    }
    
    /**
     * 创建金额按钮
     */
    private ItemStack createAmountButton(String name, BigDecimal amount, 
                                       BigDecimal availableBalance, boolean selected) {
        ItemStack button = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(MessageUtils.color(name));
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color("&7» 金额: &e" + formatCurrency(amount)));
        
        if (availableBalance.compareTo(amount) >= 0) {
            lore.add(MessageUtils.color("&a✔ 资金充足"));
            
            if (selectedTerm != null) {
                BigDecimal estimatedReturn = getEstimatedReturn(amount, selectedTerm);
                lore.add(MessageUtils.color("&7» 到期收益: &6" + formatCurrency(estimatedReturn)));
            }
        } else {
            BigDecimal needed = amount.subtract(availableBalance);
            lore.add(MessageUtils.color("&c✖ 需要: " + formatCurrency(needed) + " 更多可用资金"));
        }
        
        if (selected) {
            lore.add("");
            lore.add(MessageUtils.color("&a✔ 已选择"));
        } else if (availableBalance.compareTo(amount) >= 0) {
            lore.add("");
            lore.add(MessageUtils.color("&e点击选择"));
        }
        
        meta.setLore(lore);
        button.setItemMeta(meta);
        
        return button;
    }
    
    /**
     * 处理定期存款
     */
    private void processFixedDeposit(Player player, BankAccount account, BigDecimal amount, 
                                   FixedDeposit.DepositTerm term, Gui gui) {
        player.sendMessage(MessageUtils.color("ℹ️ 正在处理定期存款..."));
        
        CompletableFuture<DepositService.DepositResult> future = 
                depositService.depositFixed(player.getUniqueId(), amount, term);
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                player.sendMessage(MessageUtils.color("❌ 定期存款处理失败: " + throwable.getMessage()));
                return;
            }
            
            if (result.isSuccess()) {
                player.sendMessage(MessageUtils.color("✅ 定期存款创建成功！"));
                player.sendMessage(MessageUtils.color("   存款号码: " + result.getDepositNumber()));
                player.sendMessage(MessageUtils.color("   存款金额: " + formatCurrency(amount)));
                player.sendMessage(MessageUtils.color("   存期: " + getTermDisplayName(term)));
                player.sendMessage(MessageUtils.color("   到期收益: " + formatCurrency(result.getMaturityAmount())));
                player.sendMessage(MessageUtils.color("   到期日: " + TimeUtils.formatDateTime(result.getMaturityDate())));
                
                Bukkit.getScheduler().runTask((org.bukkit.plugin.Plugin) plugin, () -> {
                    player.closeInventory();
                    // 重置选择并重开界面
                    selectedTerm = null;
                    selectedAmount = BigDecimal.ZERO;
                    openFixedDepositInterface(player, account);
                });
            } else {
                String errorMessage = result.getErrorMessage();
                player.sendMessage(MessageUtils.color("❌ 定期存款失败: " + errorMessage));
            }
        });
    }
    
    /**
     * 提示自定义金额
     */
    private void promptCustomAmount(Player player, BankAccount account) {
        player.sendMessage(MessageUtils.color("💡 请输入定期存款金额（例如：5000.00）"));
        player.sendMessage(MessageUtils.color("💡 或者在聊天中输入 'cancel' 取消"));
        player.sendMessage(MessageUtils.color("💡 注意：您的可用余额为 " + formatCurrency(account.getAvailableBalance())));
        
        // 这里需要实现玩家输入监听
        // 由于GUI框架限制，这里使用命令方式处理
        player.sendMessage(MessageUtils.color("💡 请使用命令: /yae deposit fixed " + getTermMonths(selectedTerm) + " <amount>"));
    }
    
    /**
     * 显示定期存款列表
     */
    private void showFixedDepositsList(Player player, BankAccount account) {
        List<FixedDeposit> deposits = new ArrayList<>(account.getFixedDeposits().values());
        
        if (deposits.isEmpty()) {
            player.sendMessage(MessageUtils.color("ℹ️ 您当前没有定期存款"));
            return;
        }
        
        player.sendMessage(MessageUtils.color("=== 您的定期存款 ==="));
        player.sendMessage("");
        
        for (FixedDeposit deposit : deposits) {
            player.sendMessage(MessageUtils.color(getFixedDepositInfo(deposit)));
        }
        
        player.sendMessage("");
        player.sendMessage(MessageUtils.color("« 回到银行界面: /yae bank"));
    }
    
    /**
     * 显示帮助信息
     */
    private void showHelpInfo(Player player) {
        List<String> messages = Arrays.asList(
                "=== 定期存款帮助 ===",
                "",
                "📌 自动选择：",
                "   • 先选择存款期限",
                "   • 再选择存款金额",
                "   • 查看收益预览后确认",
                "",
                "📌 存款利率：",
                "   • 3月：优利率，随存随取",
                "   • 6月：标准利率，稳健选择",
                "   • 1年：高瑜利率，较长锁定",
                "   • 2年：更高利率，长期收益",
                "   • 3年：优厚利率，稳健增值",
                "   • 5年：最高利率，长期锁定",
                "",
                "📌 到期处理：",
                "   • 到期自动转入活期账户",
                "   • 可手动提前支取（有罚金）",
                "   • 支持查看到期日程",
                "",
                "📌 实时预览：",
                "   • 即时计算到期收益",
                "   • 显示到期日期",
                "   • 支取罚金说明",
                "",
                "« 返回使用 /yae bank"
        );
        
        messages.forEach(message -> player.sendMessage(MessageUtils.color(message)));
    }
    
    // 工具方法
    
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
     * 获取根据月数获取对应的DepositTerm
     */
    private FixedDeposit.DepositTerm getDepositTermByMonths(int months) {
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
     * 获取期限显示名称
     */
    private String getTermDisplayName(FixedDeposit.DepositTerm term) {
        switch (term) {
            case THREE_MONTHS: return "3个月";
            case SIX_MONTHS: return "6个月";
            case ONE_YEAR: return "1年";
            case TWO_YEARS: return "2年";
            case THREE_YEARS: return "3年";
            case FIVE_YEARS: return "5年";
            default: return "未知";
        }
    }
    
    /**
     * 获取期限的月数
     */
    private int getTermMonths(FixedDeposit.DepositTerm term) {
        if (term == null) return 12;
        return term.getMonths();
    }
    
    /**
     * 计算利息预览
     */
    private List<String> calculateInterestPreview(BigDecimal amount, FixedDeposit.DepositTerm term) {
        List<String> lore = new ArrayList<>();
        
        double rate = depositService.getTermInterestRate(term);
        BigDecimal totalReturn = depositService.calculateFixedDepositReturn(amount, BigDecimal.valueOf(rate), 
                getTermDays(term), 12);
        BigDecimal interest = totalReturn.subtract(amount);
        
        LocalDateTime maturityDate = LocalDateTime.now().plusMonths(term.getMonths());
        
        lore.add(MessageUtils.color("&7» 本金: &e" + formatCurrency(amount)));
        lore.add(MessageUtils.color("&7» 利率: &b" + formatPercentage(rate) + "%/年"));
        lore.add(MessageUtils.color("&7» 存期: &a" + getTermDisplayName(term)));
        lore.add(MessageUtils.color("&7» 利息: &6" + formatCurrency(interest)));
        lore.add(MessageUtils.color("&7» 到期总额: &6" + formatCurrency(totalReturn)));
        lore.add("");
        lore.add(MessageUtils.color("&7» 到期日: &e" + TimeUtils.formatDate(maturityDate)));
        lore.add(MessageUtils.color("&7» 剩余天数: &c" + getTermDays(term) + "天"));
        
        return lore;
    }
    
    /**
     * 估算到期收益（简化计算）
     */
    private BigDecimal getEstimatedReturn(BigDecimal principal, FixedDeposit.DepositTerm term) {
        double rate = depositService.getTermInterestRate(term);
        return depositService.calculateFixedDepositReturn(principal, BigDecimal.valueOf(rate), 
                getTermDays(term), 12);
    }
    
    /**
     * 获取期限天数
     */
    private int getTermDays(FixedDeposit.DepositTerm term) {
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
     * 获取定期存款信息
     */
    private String getFixedDepositInfo(FixedDeposit deposit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maturity = deposit.getMaturityDate();
        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(now, maturity);
        
        return String.format("&7定期 #%s: &e%s &7| &7利率: &b%.2f%% &7| &7剩余: &c%d天 &7| &7状态: &a%s",
                deposit.getDepositNumber().substring(deposit.getDepositNumber().length() - 6),
                formatCurrency(deposit.getPrincipal()),
                deposit.getInterestRate() * 100,
                Math.max(0, remainingDays),
                deposit.getStatus().getDisplayName());
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
    
    // 服务配置接口
    private com.yae.api.core.ServiceConfig config;
    
    public com.yae.api.core.ServiceConfig getConfig() {
        return config;
    }
    
    public void setConfig(com.yae.api.core.ServiceConfig config) {
        this.config = config;
    }
}
