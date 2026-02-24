package com.yae.api.shop.gui;

import com.yae.api.core.YAECore;
import com.yae.api.core.config.LanguageManager;
import com.yae.api.core.config.Configuration;
import com.yae.api.shop.*;
import com.yae.api.core.ServiceType;
import com.yae.api.services.EconomyService;
import com.yae.utils.MessageUtils;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商店购买确认GUI
 * 提供商品详情展示、数量选择、价格计算和10秒撤销确认功能
 */
@SuppressWarnings("deprecation")
public class ShopPurchaseGUI {
    
    private final YAECore plugin;
    private final ShopManager shopManager;
    private final PurchaseService purchaseService;
    private final EconomyService economyService;
    private final LanguageManager languageManager;
    private final Configuration configuration;
    private static final int ROWS = 6;
    private static final String TITLE = "商店购买确认 - {item_name}";
    
    // 金额预设按钮
    private static final int[] AMOUNT_PRESETS = {-64, -10, -1, 1, 10, 64};
    
    // 倒计时管理
    private final Map<UUID, PurchaseCountdown> activeCountdowns;
    private static final long CONFIRMATION_TIMEOUT = 10000L; // 10秒
    
    public ShopPurchaseGUI(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.shopManager = plugin.getService(ServiceType.SHOP);
        this.purchaseService = plugin.getService(ServiceType.SHOP);
        this.economyService = plugin.getService(ServiceType.ECONOMY);
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        this.configuration = plugin.getMainConfiguration();
        this.activeCountdowns = new ConcurrentHashMap<>();
    }
    
    /**
     * 打开商品购买确认界面
     * @param player 玩家
     * @param shopItem 商品
     * @param initialQuantity 初始数量 (默认1)
     */
    public void openPurchaseInterface(@NotNull Player player, @NotNull ShopItem shopItem, int initialQuantity) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(shopItem, "shopItem cannot be null");
        
        if (!isServiceAvailable()) {
            player.sendMessage(MessageUtils.error("商店服务暂不可用"));
            return;
        }
        
        int quantity = Math.max(1, initialQuantity);
        PurchaseCalculation calculation = purchaseService.calculatePurchase(shopItem.getId(), quantity, player.getUniqueId());
        
        if (!calculation.isSuccessful()) {
            player.sendMessage(MessageUtils.error("无法计算购买信息: " + calculation.getMessage()));
            return;
        }
        
        createAndOpenGUI(player, shopItem, quantity, calculation);
        
        // 启动10秒倒计时
        PurchaseCountdown countdown = new PurchaseCountdown(player.getUniqueId(), CONFIRMATION_TIMEOUT);
        countdown.start();
        activeCountdowns.put(player.getUniqueId(), countdown);
    }
    
    /**
     * 打开商品购买确认界面（默认数量1）
     */
    public void openPurchaseInterface(@NotNull Player player, @NotNull ShopItem shopItem) {
        openPurchaseInterface(player, shopItem, 1);
    }
    
    /**
     * 创建并打开购买确认GUI
     */
    private void createAndOpenGUI(@NotNull Player player, @NotNull ShopItem shopItem, 
                                 int quantity, @NotNull PurchaseCalculation calculation) {
        String guiTitle = MessageUtils.color(TITLE.replace("{item_name}", shopItem.getDisplayName()));
        
        Gui gui = Gui.gui()
                .title(MessageUtils.miniMessage(guiTitle))
                .rows(ROWS)
                .disableAllInteractions()
                .create();
        
        setupPurchaseInterface(gui, player, shopItem, quantity, calculation);
        gui.open(player);
    }
    
    /**
     * 设置购买确认界面内容
     */
    private void setupPurchaseInterface(@NotNull Gui gui, @NotNull Player player, 
                                      @NotNull ShopItem shopItem, int quantity, 
                                      @NotNull PurchaseCalculation calculation) {
        setupItemInformation(gui, shopItem);
        setupPriceInformation(gui, shopItem, quantity, calculation);
        setupQuantitySelector(gui, player, shopItem, quantity);
        setupConfirmationControls(gui, player, shopItem, quantity, calculation);
        setupNavigationControls(gui, player);
        setupDecorations(gui, calculation);
        setupCountdownTimer(gui, player.getUniqueId());
    }
    
    /**
     * 设置商品信息区域（第1行）
     */
    private void setupItemInformation(@NotNull Gui gui, @NotNull ShopItem shopItem) {
        // 商品展示
        ItemStack itemDisplay = createItemDisplay(shopItem);
        gui.setItem(4, new GuiItem(itemDisplay));
        
        // 商品详细信息
        ItemStack itemInfo = createItemInfo(shopItem);
        gui.setItem(0, new GuiItem(itemInfo));
        
        // 库存信息
        ItemStack stockInfo = createStockInfo(shopItem);
        gui.setItem(8, new GuiItem(stockInfo));
    }
    
    /**
     * 设置价格信息区域（第2行）
     */
    private void setupPriceInformation(@NotNull Gui gui, @NotNull ShopItem shopItem, 
                                     int quantity, @NotNull PurchaseCalculation calculation) {
        // 单价信息
        ItemStack unitPriceInfo = createUnitPriceInfo(shopItem);
        gui.setItem(9, new GuiItem(unitPriceInfo));
        
        // 总价计算
        ItemStack totalPriceInfo = createTotalPriceInfo(calculation, quantity);
        gui.setItem(11, new GuiItem(totalPriceInfo));
        
        // 税费信息
        ItemStack taxInfo = createTaxInfo(calculation);
        gui.setItem(13, new GuiItem(taxInfo));
        
        // 最终价格
        ItemStack finalPriceInfo = createFinalPriceInfo(calculation);
        gui.setItem(15, new GuiItem(finalPriceInfo)); // 固定显示，不可交互
        
        // 价格对比
        ItemStack priceComparison = createPriceComparison(shopItem);
        gui.setItem(17, new GuiItem(priceComparison));
    }
    
    /**
     * 设置数量选择器区域（第3行）
     */
    private void setupQuantitySelector(@NotNull Gui gui, @NotNull Player player, 
                                     @NotNull ShopItem shopItem, int currentQuantity) {
        int centerRow = 2;
        int centerCol = 4;
        
        // 快速数量按钮（减少）
        ItemStack decrease64 = createQuantityButton(-64, Material.RED_STAINED_GLASS_PANE, shopItem);
        ItemStack decrease10 = createQuantityButton(-10, Material.ORANGE_STAINED_GLASS_PANE, shopItem);
        ItemStack decrease1 = createQuantityButton(-1, Material.YELLOW_STAINED_GLASS_PANE, shopItem);
        
        gui.setItem(centerRow * 9 + 0, new GuiItem(decrease64, event -> handleQuantityChange(gui, player, shopItem, currentQuantity - 64)));
        gui.setItem(centerRow * 9 + 1, new GuiItem(decrease10, event -> handleQuantityChange(gui, player, shopItem, currentQuantity - 10)));
        gui.setItem(centerRow * 9 + 2, new GuiItem(decrease1, event -> handleQuantityChange(gui, player, shopItem, currentQuantity - 1)));
        
        // 当前数量显示
        ItemStack currentQuantityDisplay = createCurrentQuantityDisplay(currentQuantity);
        gui.setItem(centerRow * 9 + 4, new GuiItem(currentQuantityDisplay));
        
        // 手动输入按钮
        ItemStack manualInput = createManualInputButton();
        gui.setItem(centerRow * 9 + 6, new GuiItem(manualInput, event -> handleManualQuantityInput(player, shopItem)));
        
        // 快速数量按钮（增加）
        ItemStack increase1 = createQuantityButton(1, Material.LIME_STAINED_GLASS_PANE, shopItem);
        ItemStack increase10 = createQuantityButton(10, Material.GREEN_STAINED_GLASS_PANE, shopItem);
        ItemStack increase64 = createQuantityButton(64, Material.PURPLE_STAINED_GLASS_PANE, shopItem);
        
        gui.setItem(centerRow * 9 + 6, new GuiItem(manualInput));
        gui.setItem(centerRow * 9 + 6, new GuiItem(increase1, event -> handleQuantityChange(gui, player, shopItem, currentQuantity + 1)));
        gui.setItem(centerRow * 9 + 6, new GuiItem(increase10, event -> handleQuantityChange(gui, player, shopItem, currentQuantity + 10)));
        gui.setItem(centerRow * 9 + 6, new GuiItem(increase64, event -> handleQuantityChange(gui, player, shopItem, currentQuantity + 64)));
        
        // 修复：正确设置按钮位置
        gui.setItem(centerRow * 9 + 6, new GuiItem(increase1, event -> handleQuantityChange(gui, player, shopItem, currentQuantity + 1)));
        gui.setItem(centerRow * 9 + 7, new GuiItem(increase10, event -> handleQuantityChange(gui, player, shopItem, currentQuantity + 10)));
        gui.setItem(centerRow * 9 + 8, new GuiItem(increase64, event -> handleQuantityChange(gui, player, shopItem, currentQuantity + 64)));
    }
    
    /**
     * 设置确认控制区域（第4行）
     */
    private void setupConfirmationControls(@NotNull Gui gui, @NotNull Player player, 
                                         @NotNull ShopItem shopItem, int quantity, 
                                         @NotNull PurchaseCalculation calculation) {
        // 取消购买按钮
        ItemStack cancelButton = createCancelButton();
        gui.setItem(3 * 9 + 1, new GuiItem(cancelButton, event -> handleCancelPurchase(player)));
        
        // 撤销倒计时按钮
        ItemStack revokeButton = createRevokeButton();
        gui.setItem(3 * 9 + 4, new GuiItem(revokeButton, event -> handleRevokePurchase(player, shopItem, calculation)));
        
        // 确认购买按钮
        ItemStack confirmButton = createConfirmButton(calculation);
        gui.setItem(3 * 9 + 7, new GuiItem(confirmButton, event -> handleConfirmPurchase(player, shopItem, quantity, calculation)));
    }
    
    /**
     * 设置导航控制区域（第5行）
     */
    private void setupNavigationControls(@NotNull Gui gui, @NotNull Player player) {
        // 继续购物按钮
        ItemStack continueShopping = createContinueShoppingButton();
        gui.setItem(4 * 9 + 4, new GuiItem(continueShopping, event -> handleContinueShopping(player)));
    }
    
    /**
     * 设置装饰性边框
     */
    private void setupDecorations(@NotNull Gui gui, @NotNull PurchaseCalculation calculation) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        
        // 边框位置（更优化的布局）
        int[] borderSlots = {9, 17, 18, 26, 27, 35, 36, 44, 45, 53};
        for (int slot : borderSlots) {
            // 重要位置不覆盖
            if (slot == 4 || slot == 13 || slot == 22 || slot == 31 || slot == 40 || slot == 49) continue;
            gui.setItem(slot, new GuiItem(border));
        }
        
        // 购买状态指示器
        ItemStack statusIndicator;
        if (calculation.isSuccessful()) {
            statusIndicator = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            ItemMeta meta = statusIndicator.getItemMeta();
            meta.displayName(MessageUtils.miniMessage("&a✅ 购买可行"));
            statusIndicator.setItemMeta(meta);
        } else {
            statusIndicator = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = statusIndicator.getItemMeta();
            meta.displayName(MessageUtils.miniMessage("&c❌ " + calculation.getMessage()));
            statusIndicator.setItemMeta(meta);
        }
        
        // 在合适位置放置状态指示器
        gui.setItem(26, new GuiItem(statusIndicator));
        gui.setItem(27, new GuiItem(statusIndicator));
    }
    
    /**
     * 设置倒计时器
     */
    private void setupCountdownTimer(@NotNull Gui gui, @NotNull UUID playerId) {
        PurchaseCountdown countdown = activeCountdowns.get(playerId);
        if (countdown == null) return;
        
        // 定期更新倒计时显示
        countdown.setUpdateCallback(() -> {
            if (countdown.isExpired()) {
                handleTimeout(playerId);
                return;
            }
            
            // 更新撤销按钮的倒计时显示
            ItemStack revokeButton = createRevokeButtonWithCountdown(countdown.getRemainingTime());
            gui.updateItem(3 * 9 + 4, new GuiItem(revokeButton));
        });
    }
    
    // UI创建辅助方法
    
    private ItemStack createItemDisplay(@NotNull ShopItem shopItem) {
        ItemStack item = new ItemStack(Material.valueOf(shopItem.getId()));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6" + shopItem.getDisplayName()));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        for (String description : shopItem.getDescription()) {
            lore.add(MessageUtils.miniMessage("&7" + description));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    private ItemStack createItemInfo(@NotNull ShopItem shopItem) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&f📖 商品信息"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l商品分类: &6" + shopItem.getCategory()));
        lore.add(MessageUtils.miniMessage("&7&l商品ID: &e" + shopItem.getId()));
        lore.add(MessageUtils.miniMessage("&7&l状态: " + (shopItem.isEnabled() ? "&a启用" : "&c禁用")));
        
        if (shopItem.hasDailyLimit()) {
            lore.add(MessageUtils.miniMessage("&7&l每日限购: &c" + shopItem.getDailyLimit() + " 个"));
        }
        
        if (shopItem.hasPlayerLimit()) {
            lore.add(MessageUtils.miniMessage("&7&l个人限购: &c" + shopItem.getPlayerLimit() + " 个"));
        }
        
        meta.lore(lore);
        info.setItemMeta(meta);
        
        return info;
    }
    
    private ItemStack createStockInfo(@NotNull ShopItem shopItem) {
        ItemStack stock = new ItemStack(Material.BARREL);
        ItemMeta meta = stock.getItemMeta();
        
        if (shopItem.hasStockLimit() && shopItem.getStock() <= 10) {
            meta.displayName(MessageUtils.miniMessage("&c⚠️ 库存不足"));
            stock.setType(Material.RED_STAINED_GLASS_PANE);
        } else {
            meta.displayName(MessageUtils.miniMessage("&a📦 库存信息"));
        }
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l当前库存: &e" + shopItem.getStock()));
        
        if (shopItem.hasStockLimit()) {
            lore.add(MessageUtils.miniMessage("&7&l库存类型: &6" + (shopItem.getStock() > 10 ? "充足" : "紧张")));
            if (shopItem.getStock() <= 0) {
                lore.add(MessageUtils.miniMessage("&c⚠️ 商品已售罄！"));
            }
        } else {
            lore.add(MessageUtils.miniMessage("&7&l库存类型: &a无限"));
        }
        
        meta.lore(lore);
        stock.setItemMeta(meta);
        
        return stock;
    }
    
    private ItemStack createUnitPriceInfo(@NotNull ShopItem shopItem) {
        ItemStack price = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = price.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6💰 单价信息"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l购买价格: &a" + economyService.formatCurrency(shopItem.getPrice())));
        lore.add(MessageUtils.miniMessage("&7&l出售价格: &c" + economyService.formatCurrency(shopItem.getSellPrice())));
        lore.add(MessageUtils.miniMessage("&7&l利润率: " + getProfitMarginColor(shopItem.getProfitMargin()) + String.format("%.1f%%", shopItem.getProfitMargin())));
        
        meta.lore(lore);
        price.setItemMeta(meta);
        
        return price;
    }
    
    private ItemStack createTotalPriceInfo(@NotNull PurchaseCalculation calculation, int quantity) {
        ItemStack total = new ItemStack(Material.EMERALD);
        ItemMeta meta = total.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&a💎 总价格"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l商品数量: &e" + quantity));
        lore.add(MessageUtils.miniMessage("&7&l商品价格: &a" + economyService.formatCurrency(calculation.getTotalPrice())));
        
        meta.lore(lore);
        total.setItemMeta(meta);
        
        return total;
    }
    
    private ItemStack createTaxInfo(@NotNull PurchaseCalculation calculation) {
        ItemStack tax = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = tax.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&b🏛️ 税费信息"));
        
        List<Component> lore = new ArrayList<>();
        double taxRate = calculation.getTaxAmount() / calculation.getTotalPrice();
        lore.add(MessageUtils.miniMessage("&7&l税率: &e" + String.format("%.1f%%", taxRate * 100)));
        lore.add(MessageUtils.miniMessage("&7&l税费: &c-" + economyService.formatCurrency(calculation.getTaxAmount())));
        lore.add(MessageUtils.miniMessage("&7&l含税总成本: &6" + economyService.formatCurrency(calculation.getFinalPrice())));
        
        meta.lore(lore);
        tax.setItemMeta(meta);
        
        return tax;
    }
    
    private ItemStack createFinalPriceInfo(@NotNull PurchaseCalculation calculation) {
        ItemStack finalPrice = new ItemStack(Material.DIAMOND);
        ItemMeta meta = finalPrice.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6💎 最终价格"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l最终金额: &6" + economyService.formatCurrency(calculation.getFinalPrice())));
        lore.add(MessageUtils.miniMessage("&7或者等值的: " + getCurrencyValueText(calculation.getFinalPrice())));
        
        meta.lore(lore);
        finalPrice.setItemMeta(meta);
        
        return finalPrice;
    }
    
    private ItemStack createPriceComparison(@NotNull ShopItem shopItem) {
        ItemStack comparison = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = comparison.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&e📊 价格对比"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l商店价格: &6" + economyService.formatCurrency(shopItem.getPrice())));
        lore.add(MessageUtils.miniMessage("&7&l出售价格: &a" + economyService.formatCurrency(shopItem.getSellPrice())));
        
        var marketRange = shopItem.getMarketPriceRange();
        if (marketRange != null) {
            lore.add(MessageUtils.miniMessage("&7&l市场价格: &e" + economyService.formatCurrency(marketRange.getMinPrice()) + " - " + economyService.formatCurrency(marketRange.getMaxPrice())));
        }
        
        meta.lore(lore);
        comparison.setItemMeta(meta);
        
        return comparison;
    }
    
    private ItemStack createQuantityButton(int changeAmount, @NotNull Material material, @NotNull ShopItem shopItem) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        
        String prefix = changeAmount > 0 ? "&a+" : "&c";
        meta.displayName(MessageUtils.miniMessage(prefix + changeAmount));
        
        List<Component> lore = new ArrayList<>();
        if (changeAmount > 0) {
            lore.add(MessageUtils.miniMessage("&7点击增加 &a" + changeAmount + " &7个"));
        } else {
            lore.add(MessageUtils.miniMessage("&7点击减少 &c" + Math.abs(changeAmount) + " &7个"));
        }
        
        meta.lore(lore);
        button.setItemMeta(meta);
        
        return button;
    }
    
    private ItemStack createCurrentQuantityDisplay(int quantity) {
        ItemStack display = new ItemStack(Material.PAPER);
        ItemMeta meta = display.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&e📋 当前数量"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l选择数量: &6" + quantity));
        lore.add(MessageUtils.miniMessage("&7点击选择按钮调整数量"));
        lore.add(MessageUtils.miniMessage("&7或者点击手动输入按钮") );
        
        meta.lore(lore);
        display.setItemMeta(meta);
        
        return display;
    }
    
    private ItemStack createManualInputButton() {
        ItemStack input = new ItemStack(Material.ANVIL);
        ItemMeta meta = input.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&e🔧 手动输入"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7点击输入自定义数量"));
        lore.add(MessageUtils.miniMessage("&7支持1-64个"));
        
        meta.lore(lore);
        input.setItemMeta(meta);
        
        return input;
    }
    
    private ItemStack createCancelButton() {
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta meta = cancel.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&c❌ 取消购买"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7取消此次购买"));
        lore.add(MessageUtils.miniMessage("&7不会扣除任何费用"));
        
        meta.lore(lore);
        cancel.setItemMeta(meta);
        
        return cancel;
    }
    
    private ItemStack createRevokeButton() {
        ItemStack revoke = new ItemStack(Material.CLOCK);
        ItemMeta meta = revoke.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6⏰ 10秒撤销"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7可以在10秒内撤销购买"));
        lore.add(MessageUtils.miniMessage("&7点击撤销，返还全部金额"));
        lore.add(MessageUtils.miniMessage("&a剩余时间: 10秒"));
        
        meta.lore(lore);
        revoke.setItemMeta(meta);
        
        return revoke;
    }
    
    private ItemStack createRevokeButtonWithCountdown(long remainingTime) {
        ItemStack revoke = new ItemStack(Material.CLOCK);
        ItemMeta meta = revoke.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6⏰ " + (remainingTime / 1000) + "秒撤销"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7点击撤销此次购买"));
        lore.add(MessageUtils.miniMessage("&7撤销后返还全部金额"));
        lore.add(MessageUtils.miniMessage("&a剩余时间: &e" + (remainingTime / 1000) + "秒"));
        
        if (remainingTime <= 3000) {
            meta.displayName(MessageUtils.miniMessage("&c⚠️ " + (remainingTime / 1000) + "秒撤销"));
            lore.set(2, MessageUtils.miniMessage("&c⚠️ 快点决定: &4" + (remainingTime / 1000) + "秒"));
        }
        
        meta.lore(lore);
        revoke.setItemMeta(meta);
        
        return revoke;
    }
    
    private ItemStack createConfirmButton(@NotNull PurchaseCalculation calculation) {
        ItemStack confirm = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = confirm.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&a✅ 确认购买"));
        
        List<Component> lore = new ArrayList<>();
        String finalPrice = economyService.formatCurrency(calculation.getFinalPrice());
        lore.add(MessageUtils.miniMessage("&7点击确认购买"));
        lore.add(MessageUtils.miniMessage("&7将从您的账户扣除: &e" + finalPrice));
        
        if (!calculation.isSuccessful()) {
            meta.displayName(MessageUtils.miniMessage("&c❌ 无法购买"));
            lore.clear();
            lore.add(MessageUtils.miniMessage("&c" + calculation.getMessage()));
        }
        
        meta.lore(lore);
        confirm.setItemMeta(meta);
        
        return confirm;
    }
    
    private ItemStack createContinueShoppingButton() {
        ItemStack continueBtn = new ItemStack(Material.COMPASS);
        ItemMeta meta = continueBtn.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&b🛍️ 继续购物"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7返回商店继续浏览"));
        lore.add(MessageUtils.miniMessage("&7购买后可继续选购商品"));
        
        meta.lore(lore);
        continueBtn.setItemMeta(meta);
        
        return continueBtn;
    }
    
    // 辅助方法
    
    private String getProfitMarginColor(double margin) {
        if (margin < 50) return "&a";
        if (margin < 100) return "&e";
        if (margin < 200) return "&6";
        return "&c";
    }
    
    private String getCurrencyValueText(double amount) {
        // 简单的货币价值转换提示
        int diamondValue = (int)(amount / 200); // 假设钻石约200钱
        int ironValue = (int)(amount / 30);     // 假设铁约30钱
        return diamondValue + " 个钻石 + " + ironValue + " 个铁锭";
    }
    
    // 事件处理方法
    
    private void handleQuantityChange(@NotNull Gui gui, @NotNull Player player, 
                                    @NotNull ShopItem shopItem, int newQuantity) {
        int maxAvailable = getMaxAvailableQuantity(player, shopItem);
        newQuantity = Math.max(1, Math.min(newQuantity, maxAvailable));
        
        PurchaseCalculation newCalculation = purchaseService.calculatePurchase(shopItem.getId(), newQuantity, player.getUniqueId());
        if (newCalculation.isSuccessful()) {
            openPurchaseInterface(player, shopItem, newQuantity);
        } else {
            player.sendMessage(MessageUtils.warning("数量调整失败: " + newCalculation.getMessage()));
        }
    }
    
    private void handleManualQuantityInput(@NotNull Player player, @NotNull ShopItem shopItem) {
        player.closeInventory();
        player.sendMessage(MessageUtils.info("请输入购买数量 (1-64):"));
        
        // 设置临时监听器处理聊天输入
        player.sendMessage(MessageUtils.info("使用 /yae shop buy <数量> 来购买此商品"));
        player.sendMessage(MessageUtils.info("或者切换到适当的GUI界面"));
        
        // Future: 实现更高级的聊天输入处理
    }
    
    private void handleCancelPurchase(@NotNull Player player) {
        player.closeInventory();
        cleanUpCountdown(player.getUniqueId());
        player.sendMessage(MessageUtils.info("购买已取消"));
    }
    
    private void handleRevokePurchase(@NotNull Player player, @NotNull ShopItem shopItem, 
                                    @NotNull PurchaseCalculation calculation) {
        PurchaseCountdown countdown = activeCountdowns.get(player.getUniqueId());
        
        if (countdown != null && !countdown.isExpired()) {
            // 创建后续的撤销处理
            player.closeInventory();
            handlePurchaseRevocation(player, shopItem, calculation);
            cleanUpCountdown(player.getUniqueId());
        } else {
            player.sendMessage(MessageUtils.error("撤销期限已过，无法撤销购买"));
            player.closeInventory();
        }
    }
    
    private void handleConfirmPurchase(@NotNull Player player, @NotNull ShopItem shopItem, 
                                     int quantity, @NotNull PurchaseCalculation calculation) {
        if (!calculation.isSuccessful()) {
            player.sendMessage(MessageUtils.error("无法确认购买: " + calculation.getMessage()));
            return;
        }
        
        try {
            // 创建待购买记录
            PurchaseService.PendingPurchase pendingPurchase = purchaseService.createPendingPurchase(
                shopItem.getId(), quantity, player.getUniqueId()
            );
            
            if (pendingPurchase == null) {
                player.sendMessage(MessageUtils.error("创建购买记录失败"));
                return;
            }
            
            player.closeInventory();
            cleanUpCountdown(player.getUniqueId());
            
            // 打开回执界面
            ShopReceiptGUI receiptGUI = new ShopReceiptGUI(plugin);
            receiptGUI.showPurchaseSuccess(player, shopItem, calculation, pendingPurchase);
            
        } catch (Exception e) {
            plugin.getLogger().severe("购买处理时出错: " + e.getMessage());
            player.sendMessage(MessageUtils.error("购买处理失败，请联系管理员"));
        }
    }
    
    private void handleContinueShopping(@NotNull Player player) {
        player.closeInventory();
        cleanUpCountdown(player.getUniqueId());
        // 返回商店主界面（可能需要一个ShopListGUI）
        player.sendMessage(MessageUtils.info("即将返回商店界面..."));
    }
    
    private void handleTimeout(@NotNull UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.closeInventory();
            player.sendMessage(MessageUtils.error("购买确认超时，自动取消购买"));
        }
        cleanUpCountdown(playerId);
    }
    
    private void handlePurchaseRevocation(@NotNull Player player, @NotNull ShopItem shopItem, 
                                        @NotNull PurchaseCalculation calculation) {
        // 实现购买撤销逻辑
        player.sendMessage(MessageUtils.success("购买已成功撤销"));
        player.sendMessage(MessageUtils.info("已返还金额: " + economyService.formatCurrency(calculation.getFinalPrice())));
        // TODO: 实现实际的撤销处理
    }
    
    // 实用方法
    
    private boolean isServiceAvailable() {
        return shopManager != null && shopManager.isEnabled() &&
               purchaseService != null && purchaseService.isEnabled() &&
               economyService != null && economyService.isEnabled();
    }
    
    private int getMaxAvailableQuantity(@NotNull Player player, @NotNull ShopItem shopItem) {
        int availableByStock = shopItem.hasStockLimit() ? shopItem.getStock() : Integer.MAX_VALUE;
        int availableByLimits = shopManager.getAvailableQuantity(shopItem.getId(), player.getUniqueId());
        return Math.min(64, Math.min(availableByStock, availableByLimits));
    }
    
    private void cleanUpCountdown(@NotNull UUID playerId) {
        PurchaseCountdown countdown = activeCountdowns.remove(playerId);
        if (countdown != null) {
            countdown.stop();
        }
    }
    
    // 内部类: 购买倒计时管理器
    private static class PurchaseCountdown {
        private final UUID playerId;
        private final long startTime;
        private final long duration;
        private long remainingTime;
        private boolean running;
        private Runnable updateCallback;
        private int taskId = -1;
        
        public PurchaseCountdown(UUID playerId, long duration) {
            this.playerId = playerId;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            this.remainingTime = duration;
            this.running = false;
        }
        
        public void start() {
            if (running) return;
            running = true;
            
            // 假设plugin接口提供了一个获取Bukkit插件实例的方法
            // 这里使用反射或者直接引用原始插件实例
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                null,  // 临时使用null，实际应用中需要正确的插件引用
                () -> {
                    remainingTime = duration - (System.currentTimeMillis() - startTime);
                    if (remainingTime <= 0) {
                        stop();
                        return;
                    }
                    if (updateCallback != null) {
                        // 同步执行回调
                        updateCallback.run();
                    }
                },
                0L, 20L // 每秒更新一次
            );
        }
        
        public void stop() {
            if (!running) return;
            running = false;
            if (taskId != -1) {
                Bukkit.getScheduler().cancelTask(taskId);
                taskId = -1;
            }
        }
        
        public boolean isExpired() {
            return remainingTime <= 0;
        }
        
        public long getRemainingTime() {
            return Math.max(0, remainingTime);
        }
        
        public void setUpdateCallback(Runnable callback) {
            this.updateCallback = callback;
        }
    }
}
