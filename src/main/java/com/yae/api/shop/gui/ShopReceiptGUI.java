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
import java.time.LocalDateTime;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商店购买回执GUI
 * 显示交易成功信息、交易详情、订单号和时间戳
 */
public class ShopReceiptGUI {
    
    private final YAECore plugin;
    private final ShopManager shopManager;
    private final PurchaseService purchaseService;
    private final EconomyService economyService;
    private final LanguageManager languageManager;
    private final Configuration configuration;
    private static final int ROWS = 6;
    private static final String TITLE = "购买回执 - {order_id}";
    
    // 回执存储管理
    private final Map<UUID, PurchaseReceipt> activeReceipts;
    private static final long RECEIPT_EXPIRY_TIME = 300000L; // 5分钟
    
    public ShopReceiptGUI(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.shopManager = plugin.getService(ServiceType.SHOP);
        this.purchaseService = plugin.getService(ServiceType.SHOP);
        this.economyService = plugin.getService(ServiceType.ECONOMY);
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        this.configuration = plugin.getMainConfiguration();
        this.activeReceipts = new ConcurrentHashMap<>();
    }
    
    /**
     * 显示购买成功回执
     * @param player 玩家
    * @param shopItem 购买的商品
   * @param calculation 购买计算结果
     * @param pendingPurchase 待购买记录
     */
    public void showPurchaseSuccess(@NotNull Player player, @NotNull ShopItem shopItem, 
                                  @NotNull PurchaseCalculation calculation, 
                                  @NotNull PurchaseService.PendingPurchase pendingPurchase) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(shopItem, "shopItem cannot be null");
        Objects.requireNonNull(calculation, "calculation cannot be null");
        Objects.requireNonNull(pendingPurchase, "pendingPurchase cannot be null");
        
        if (!isServiceAvailable()) {
            player.sendMessage(MessageUtils.error("商店服务暂不可用"));
            return;
        }
        
        try {
            // 执行购买
            PurchaseResult result = purchaseService.executePurchase(player.getUniqueId(), 
                pendingPurchase.getTimestamp() + "_" + player.getUniqueId());
            
            if (result != PurchaseResult.SUCCESS) {
                player.sendMessage(MessageUtils.error("购买执行失败: " + result.getDefaultMessage()));
                return;
            }
            
            // 创建新的计算（包含实际交易数据）
            PurchaseCalculation finalCalculation = purchaseService.calculatePurchase(
                shopItem.getId(), calculation.getQuantity(), player.getUniqueId()
            );
            
            if (finalCalculation.isSuccessful()) {
                // 给玩家发放物品
                giveItemsToPlayer(player, shopItem.getId(), calculation.getQuantity());
                
                // 显示购买成功回执
                createAndShowReceipt(player, shopItem, finalCalculation, pendingPurchase);
                
            } else {
                player.sendMessage(MessageUtils.error("购买计算失败: " + finalCalculation.getMessage()));
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("购买回执显示失败: " + e.getMessage());
            player.sendMessage(MessageUtils.error("购买回券显示失败，请联系管理员"));
        }
    }
    
    /**
     * 显示交易成功回执（简化版本）
     */
    public void showPurchaseSuccess(@NotNull Player player, @NotNull String orderId, 
                                  @NotNull ShopItem shopItem, int quantity, 
                                  double unitPrice, double totalPrice, double taxAmount, 
                                  double finalPrice, @NotNull LocalDateTime purchaseTime) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(shopItem, "shopItem cannot be null");
        Objects.requireNonNull(purchaseTime, "purchaseTime cannot be null");
        
        try {
            PurchaseCalculation calculation = new PurchaseCalculation(
                PurchaseResult.SUCCESS, "购买成功", shopItem, quantity, totalPrice, taxAmount, finalPrice
            );
            
            PurchaseService.PendingPurchase pendingPurchase = new PurchaseService.PendingPurchase(
                System.currentTimeMillis(), player.getUniqueId(), shopItem.getId(), quantity,
                unitPrice, totalPrice, taxAmount, finalPrice, purchaseTime
            );
            
            createAndShowReceipt(player, shopItem, calculation, orderId);
            
        } catch (Exception e) {
            plugin.getLogger().severe("购买回凭显示失败: " + e.getMessage());
            player.sendMessage(MessageUtils.error("显示购买回凭失败"));
        }
    }
    
    /**
     * 显示交易回执（从订单ID）
     */
    public void showReceiptByOrderId(@NotNull Player player, @NotNull String orderId) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(orderId, "orderId cannot be null");
        
        // 从数据库或回凭存储中获取交易信息
        PurchaseReceipt receipt = activeReceipts.get(player.getUniqueId());
        
        if (receipt != null && receipt.getOrderId().equals(orderId) && !receipt.isExpired()) {
            createAndShowReceiptFromStoredData(player, receipt);
        } else {
            player.sendMessage(MessageUtils.error("未找到该订单或未找到有效的回凭"));
        }
    }
    
    /**
     * 创建并显示购买回执
     */
    private void createAndShowReceipt(@NotNull Player player, @NotNull ShopItem shopItem, 
                                    @NotNull PurchaseCalculation calculation, 
                                    @NotNull PurchaseService.PendingPurchase pendingPurchase) {
        String orderId = generateOrderId(player, pendingPurchase);
        
        // 创建回凭对象
        PurchaseReceipt receipt = new PurchaseReceipt(
            orderId, player.getUniqueId(), shopItem, calculation.getQuantity(),
            calculation.getUnitPrice(), calculation.getTotalPrice(), calculation.getTaxAmount(),
            calculation.getFinalPrice(), LocalDateTime.now(), RECEIPT_EXPIRY_TIME
        );
        
        activeReceipts.put(player.getUniqueId(), receipt);
        
        createAndShowReceiptFromStoredData(player, receipt);
    }
    
    /**
     * 创建并显示购买回据（从存储的回据数据）
     */
    private void createAndShowReceipt(@NotNull Player player, @NotNull ShopItem shopItem, 
                                    @NotNull PurchaseCalculation calculation, @NotNull String orderId) {
        PurchaseReceipt receipt = new PurchaseReceipt(
            orderId, player.getUniqueId(), shopItem, calculation.getQuantity(),
            calculation.getUnitPrice(), calculation.getTotalPrice(), calculation.getTaxAmount(),
            calculation.getFinalPrice(), LocalDateTime.now(), RECEIPT_EXPIRY_TIME
        );
        
        activeReceipts.put(player.getUniqueId(), receipt);
        createAndShowReceiptFromStoredData(player, receipt);
    }
    
    /**
     * 从存储的回据数据显示回据GUI
     */
    private void createAndShowReceiptFromStoredData(@NotNull Player player, @NotNull PurchaseReceipt receipt) {
        String guiTitle = MessageUtils.color(TITLE.replace("{order_id}", receipt.getOrderId().substring(0, 8)));
        
        Gui gui = Gui.gui()
                .title(MessageUtils.miniMessage(guiTitle))
                .rows(ROWS)
                .disableAllInteractions()
                .create();
        
        setupReceiptInterface(gui, player, receipt);
        
        gui.open(player);
        
        // 启动清理定时器
        startReceiptCleanup(receipt);
    }
    
    /**
     * 设置回执界面内容
     */
    private void setupReceiptInterface(@NotNull Gui gui, @NotNull Player player, @NotNull PurchaseReceipt receipt) {
        setupReceiptHeader(gui, receipt);
        setupTransactionDetails(gui, receipt);
        setupItemInformation(gui, receipt);
        setupFinancialSummary(gui, receipt);
        setupActionButtons(gui, player, receipt);
        setupDecorations(gui);
    }
    
    /**
     * 设置回执头部信息（第1行）
     */
    private void setupReceiptHeader(@NotNull Gui gui, @NotNull PurchaseReceipt receipt) {
        // 成功徽标
        ItemStack successIcon = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta successMeta = successIcon.getItemMeta();
        successMeta.displayName(MessageUtils.miniMessage("&a✅ 交易成功"));
        
        List<Component> successLore = new ArrayList<>();
        successLore.add(MessageUtils.miniMessage("&7恭喜您成功购买商品"));
        successLore.add(MessageUtils.miniMessage("&7购买时间: &b" + formatDateTime(receipt.getPurchaseTime())));
        successLore.add(MessageUtils.miniMessage("&7订单号: &e" + receipt.getOrderId()));
        
        successMeta.lore(successLore);
        successIcon.setItemMeta(successMeta);
        gui.setItem(0, new GuiItem(successIcon));
        
        // 订单状态
        ItemStack statusIcon = new ItemStack(Material.GREEN_WOOL);
        ItemMeta statusMeta = statusIcon.getItemMeta();
        statusMeta.displayName(MessageUtils.miniMessage("&6📋 订单状态"));
        
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(MessageUtils.miniMessage("&7&l交易时间: &b" + formatDateTime(receipt.getPurchaseTime())));
        statusLore.add(MessageUtils.miniMessage("&7&l订单号: &e" + receipt.getOrderId()));
        statusLore.add(MessageUtils.miniMessage("&7&l过期时间: &6" + formatTimeLeft(receipt.getTimeLeft())));
        statusLore.add(MessageUtils.miniMessage("&7&l状态: &a已完成"));
        
        statusMeta.lore(statusLore);
        statusIcon.setItemMeta(statusMeta);
        gui.setItem(4, new GuiItem(statusIcon));
        
        // 金额总计显示
        ItemStack totalAmount = new ItemStack(Material.GOLD_INGOT);
        ItemMeta totalMeta = totalAmount.getItemMeta();
        totalMeta.displayName(MessageUtils.miniMessage("&6💰 交易金额"));
        
        List<Component> totalLore = new ArrayList<>();
        totalLore.add(MessageUtils.miniMessage("&7&l商品总计: &e" + economyService.formatCurrency(receipt.getTotalPrice())));
        totalLore.add(MessageUtils.miniMessage("&7&l最终金额: &6" + economyService.formatCurrency(receipt.getFinalPrice())));
        
        totalMeta.lore(totalLore);
        totalAmount.setItemMeta(totalMeta);
        gui.setItem(8, new GuiItem(totalAmount));
    }
    
    /**
     * 设置交易详情区域（第2行）
     */
    private void setupTransactionDetails(@NotNull Gui gui, @NotNull PurchaseReceipt receipt) {
        // 购买类型
        ItemStack purchaseType = new ItemStack(Material.FURNACE);
        ItemMeta typeMeta = purchaseType.getItemMeta();
        typeMeta.displayName(MessageUtils.miniMessage("&3🔥 交易类型"));
        
        List<Component> typeLore = new ArrayList<>();
        typeLore.add(MessageUtils.miniMessage("&7&l交易类型: &a购买"));
        typeLore.add(MessageUtils.miniMessage("&7&l购买数量: &e" + receipt.getQuantity() + " 个"));
        typeLore.add(MessageUtils.miniMessage("&7&l单价: &6" + economyService.formatCurrency(receipt.getUnitPrice())));
        
        typeMeta.lore(typeLore);
        purchaseType.setItemMeta(typeMeta);
        gui.setItem(9, new GuiItem(purchaseType));
        
        // 税费信息
        ItemStack taxInfo = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta taxMeta = taxInfo.getItemMeta();
        taxMeta.displayName(MessageUtils.miniMessage("&b🏛️ 税费详情"));
        
        List<Component> taxLore = new ArrayList<>();
        double taxRate = receipt.getTaxAmount() / receipt.getTotalPrice();
        taxLore.add(MessageUtils.miniMessage("&7&l总价格: &e" + economyService.formatCurrency(receipt.getTotalPrice())));
        taxLore.add(MessageUtils.miniMessage("&7&l税率: &c" + String.format("%.1f%%", taxRate * 100)));
        taxLore.add(MessageUtils.miniMessage("&7&l税费: &c-" + economyService.formatCurrency(receipt.getTaxAmount())));
        taxLore.add(MessageUtils.miniMessage("&7&l最终价格: &6" + economyService.formatCurrency(receipt.getFinalPrice())));
        
        taxMeta.lore(taxLore);
        taxInfo.setItemMeta(taxMeta);
        gui.setItem(11, new GuiItem(taxInfo));
        
        // 客户服务
        ItemStack customerService = new ItemStack(Material.NAME_TAG);
        ItemMeta serviceMeta = customerService.getItemMeta();
        serviceMeta.displayName(MessageUtils.miniMessage("&9💬 客户服务"));
        
        List<Component> serviceLore = new ArrayList<>();
        serviceLore.add(MessageUtils.miniMessage("&7如有问题，请联系管理员"));
        serviceLore.add(MessageUtils.miniMessage("&7提供订单号: &e" + receipt.getOrderId()));
        serviceLore.add(MessageUtils.miniMessage("&7保存此回执以备查询"));
        
        serviceMeta.lore(serviceLore);
        customerService.setItemMeta(serviceMeta);
        gui.setItem(17, new GuiItem(customerService));
    }
    
    /**
     * 设置商品信息（第3行）
     */
    private void setupItemInformation(@NotNull Gui gui, @NotNull PurchaseReceipt receipt) {
        // 商品展示
        ItemStack itemDisplay = createItemDisplay(receipt.getItem());
        gui.setItem(13, new GuiItem(itemDisplay));
        
        // 商品详情
        ItemStack itemDetails = createItemDetails(receipt);
        gui.setItem(14, new GuiItem(itemDetails));
        
        // 使用指南
        ItemStack usageGuide = createUsageGuide(receipt);
        gui.setItem(15, new GuiItem(usageGuide));
    }
    
    /**
     * 设置财务摘要（第4行）
     */
    private void setupFinancialSummary(@NotNull Gui gui, @NotNull PurchaseReceipt receipt) {
        // 账户余额变化
        ItemStack balanceChange = createBalanceChangeInfo(receipt);
        gui.setItem(18, new GuiItem(balanceChange));
        
        // 交易摘要
        ItemStack summary = createTransactionSummary(receipt);
        gui.setItem(22, new GuiItem(summary));
        
        // 财务建议
        ItemStack advice = createFinancialAdvice(receipt);
        gui.setItem(26, new GuiItem(advice));
    }
    
    /**
     * 设置操作按钮（第5行）
     */
    private void setupActionButtons(@NotNull Gui gui, @NotNull Player player, @NotNull PurchaseReceipt receipt) {
        // 继续购物按钮
        ItemStack continueShopping = createContinueShoppingButton();
        gui.setItem(4 * 9 + 2, new GuiItem(continueShopping, event -> handleContinueShopping(player)));
        
        // 查看详细回凭
        ItemStack viewReceipt = createViewReceiptButton(receipt);
        gui.setItem(4 * 9 + 4, new GuiItem(viewReceipt, event -> handleViewReceipt(player, receipt.getOrderId())));
        
        // 关闭按钮
        ItemStack closeButton = createCloseButton();
        gui.setItem(4 * 9 + 6, new GuiItem(closeButton, event -> handleClose(player)));
    }
    
    /**
     * 设置装饰性边框
     */
    private void setupDecorations(@NotNull Gui gui) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        
        // 边框位置
        int[] borderSlots = {9, 17, 18, 26, 27, 35, 36, 44, 45, 53};
        for (int slot : borderSlots) {
            gui.setItem(slot, new GuiItem(border));
        }
    }
    
    // UI辅助方法
    
    private ItemStack createItemDisplay(@NotNull ShopItem shopItem) {
        ItemStack item;
        try {
            item = new ItemStack(Material.valueOf(shopItem.getId()));
        } catch (IllegalArgumentException e) {
            item = new ItemStack(Material.PAPER);
        }
        
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6" + shopItem.getDisplayName()));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l购买成功: &a✅"));
        lore.add(MessageUtils.miniMessage("&7商品已发放到您的库存中"));
        lore.add(MessageUtils.miniMessage("&7如果库存不足，将存放于地面"));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    private ItemStack createItemDetails(@NotNull PurchaseReceipt receipt) {
        ItemStack details = new ItemStack(Material.BOOK);
        ItemMeta meta = details.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&f📖 商品详情"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l购买数量: &e" + receipt.getQuantity()));
        lore.add(MessageUtils.miniMessage("&7&l商品分类: &6" + receipt.getItem().getCategory()));
        lore.add(MessageUtils.miniMessage("&7&l购买单价: &6" + economyService.formatCurrency(receipt.getUnitPrice())));
        lore.add(MessageUtils.miniMessage("&7&l购买时间: &b" + formatDateTime(receipt.getPurchaseTime())));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        meta.lore(lore);
        details.setItemMeta(meta);
        
        return details;
    }
    
    private ItemStack createUsageGuide(@NotNull PurchaseReceipt receipt) {
        ItemStack guide = new ItemStack(Material.COMPASS);
        ItemMeta meta = guide.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&9💡 使用指南"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l商品使用:"));
        lore.add(MessageUtils.miniMessage("&7• 商品已自动添加到您的库存中"));
        lore.add(MessageUtils.miniMessage("&7• 您可以在合适的地方使用商品"));
        lore.add(MessageUtils.miniMessage("&7• 部分商品可能有特殊用途"));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l注意事项:"));
        lore.add(MessageUtils.miniMessage("&7• 保存好您的订单回执"));
        lore.add(MessageUtils.miniMessage("&7• 如有问题联系管理员"));
        lore.add(MessageUtils.miniMessage("&7• 提供订单号以获得帮助"));
        
        meta.lore(lore);
        guide.setItemMeta(meta);
        
        return guide;
    }
    
    private ItemStack createBalanceChangeInfo(@NotNull PurchaseReceipt receipt) {
        ItemStack balance = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = balance.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6💰 余额变化"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l交易前余额: &a" + economyService.formatCurrency(getPlayerBalanceBeforeTransaction(receipt.getPlayerId(), receipt.getFinalPrice()))));
        lore.add(MessageUtils.miniMessage("&7&l消费金额: &c-" + economyService.formatCurrency(receipt.getFinalPrice())));
        lore.add(MessageUtils.miniMessage("&7&l交易后余额: &6" + getCurrentPlayerBalanceDisplay(receipt.getPlayerId())));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l账户状态: &a正常"));
        
        meta.lore(lore);
        balance.setItemMeta(meta);
        
        return balance;
    }
    
    private ItemStack createTransactionSummary(@NotNull PurchaseReceipt receipt) {
        ItemStack summary = new ItemStack(Material.EMERALD);
        ItemMeta meta = summary.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&a📊 交易摘要"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l购买商品: &6" + receipt.getItem().getDisplayName()));
        lore.add(MessageUtils.miniMessage("&7&l购买数量: &e" + receipt.getQuantity() + " 个"));
        lore.add(MessageUtils.miniMessage("&7&l商品总价: &e" + economyService.formatCurrency(receipt.getTotalPrice())));
        lore.add(MessageUtils.miniMessage("&7&l税费金额: &c" + economyService.formatCurrency(receipt.getTaxAmount())));
        lore.add(MessageUtils.miniMessage("&7&l最终支付: &6" + economyService.formatCurrency(receipt.getFinalPrice())));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l交易时间: &b" + formatDateTime(receipt.getPurchaseTime())));
        lore.add(MessageUtils.miniMessage("&7&l订单ID: &d" + receipt.getOrderId()));
        
        meta.lore(lore);
        summary.setItemMeta(meta);
        
        return summary;
    }
    
    private ItemStack createFinancialAdvice(@NotNull PurchaseReceipt receipt) {
        ItemStack advice = new ItemStack(Material.DIAMOND);
        ItemMeta meta = advice.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&d💎 财务建议"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l购买成功:"));
        lore.add(MessageUtils.miniMessage("&7• 明智的消费选择"));
        lore.add(MessageUtils.miniMessage("&7• 保持良好的消费习惯"));
        lore.add(MessageUtils.miniMessage("&7• 记得查看现金流"));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l下次购物:"));
        lore.add(MessageUtils.miniMessage("&7• 关注商品优惠"));
        lore.add(MessageUtils.miniMessage("&7• 比较价格和功能"));
        lore.add(MessageUtils.miniMessage("&7• 量力而行，理性消费"));
        
        if (receipt.getFinalPrice() > 1000) {
            lore.add(MessageUtils.miniMessage("&6💰 大量消费提示:"));
            lore.add(MessageUtils.miniMessage("&6• 考虑增加收入来源"));
        }
        
        meta.lore(lore);
        advice.setItemMeta(meta);
        
        return advice;
    }
    
    private ItemStack createContinueShoppingButton() {
        ItemStack shopping = new ItemStack(Material.SHULKER_BOX);
        ItemMeta meta = shopping.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&b🛍️ 继续购物"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7点击返回商店"));
        lore.add(MessageUtils.miniMessage("&7浏览更多商品"));
        lore.add(MessageUtils.miniMessage("&7发现更好的选择"));
        
        meta.lore(lore);
        shopping.setItemMeta(meta);
        
        return shopping;
    }
    
    private ItemStack createViewReceiptButton(@NotNull PurchaseReceipt receipt) {
        ItemStack receiptBtn = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = receiptBtn.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&f📖 查看详细回凭"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l订单号: &e" + receipt.getOrderId()));
        lore.add(MessageUtils.miniMessage("&7&l保存方式为: &a数字化存储"));
        lore.add(MessageUtils.miniMessage("&7&l有效期: &e" + formatTimeLeft(receipt.getTimeLeft())));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7点击保存回凭信息"));
        lore.add(MessageUtils.miniMessage("&7可以重新调出回予"));
        
        meta.lore(lore);
        receiptBtn.setItemMeta(meta);
        
        return receiptBtn;
    }
    
    private ItemStack createCloseButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta meta = close.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&c❌ 关闭回凭"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7关闭此次回凭"));
        lore.add(MessageUtils.miniMessage("&7回执将自动保存"));
        lore.add(MessageUtils.miniMessage("&7可以稍后再次调出"));
        
        meta.lore(lore);
        close.setItemMeta(meta);
        
        return close;
    }
    
    // 事件处理方法
    
    private void handleContinueShopping(@NotNull Player player) {
        player.closeInventory();
        // 返回商店列表界面
        // TODO: 实现 ShopListGUI 或类似功能
        player.sendMessage(MessageUtils.info("即将返回商店界面..."));
    }
    
    private void handleViewReceipt(@NotNull Player player, @NotNull String orderId) {
        player.sendMessage(MessageUtils.success("回凭订单号: " + orderId));
        player.sendMessage(MessageUtils.info("订单已保存，您可以使用 /yae receipt view " + orderId + " 命令重新显示此回凭"));
        player.closeInventory();
    }
    
    private void handleClose(@NotNull Player player) {
        player.closeInventory();
        // 清理活动回凭
        activeReceipts.remove(player.getUniqueId());
    }
    
    // 实用方法
    
    private boolean isServiceAvailable() {
        return shopManager != null && shopManager.isEnabled() &&
               purchaseService != null && purchaseService.isEnabled() &&
               economyService != null && economyService.isEnabled();
    }
    
    private String generateOrderId(@NotNull Player player, 
                                 @NotNull PurchaseService.PendingPurchase pendingPurchase) {
        return String.format("YAE-%s-%d-%s",
            player.getName().substring(0, Math.min(4, player.getName().length())),
            pendingPurchase.getTimestamp(),
            pendingPurchase.getItemId().substring(0, Math.min(6, pendingPurchase.getItemId().length()))
        );
    }
    
    private double getPlayerBalanceBeforeTransaction(@NotNull UUID playerId, double amount) {
        double currentBalance = economyService.getBalance(playerId);
        return currentBalance + amount; // 近似计算
    }
    
    private double getCurrentPlayerBalance(@NotNull UUID playerId) {
        return economyService.getBalance(playerId);
    }
    
    private String getCurrentPlayerBalanceDisplay(@NotNull UUID playerId) {
        double balance = getCurrentPlayerBalance(playerId);
        return economyService.formatCurrency(balance);
    }
    
    private String formatTimeLeft(long timeLeft) {
        if (timeLeft <= 0) {
            return "已过期";
        }
        
        long totalSeconds = timeLeft / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        return String.format("%d分%d秒", minutes, seconds);
    }
    
    private String formatDateTime(@NotNull LocalDateTime dateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(java.util.Date.from(dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }
    
    private void giveItemsToPlayer(@NotNull Player player, @NotNull String itemId, int quantity) {
        try {
            Material material = Material.valueOf(itemId);
            ItemStack itemStack = new ItemStack(material, Math.min(quantity, 64));
            
            // 给玩家的物品
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
            
            if (quantity > 64) {
                // 处理多余物品（分批）
                for (int i = 1; i * 64 < quantity && leftover.isEmpty(); i++) {
                    int currentStack = Math.min(64, quantity - (i * 64));
                    ItemStack overflowStack = new ItemStack(material, currentStack);
                    Map<Integer, ItemStack> newRemainder = player.getInventory().addItem(overflowStack);
                    
                    // 如果还有剩余，说明物品无法完全放入库存
                    if (!newRemainder.isEmpty()) {
                        leftover.putAll(newRemainder);
                        break;
                    }
                }
            }
            
            // 如果有剩余物品，扔在地上
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.sendMessage(MessageUtils.warning("您的库存已满，部分商品已放置在地面上"));
            }
            
            player.sendMessage(MessageUtils.success("✅ 商品已发放到您的库存中"));
            
        } catch (IllegalArgumentException e) {
            // 处理无效物品
            plugin.getLogger().warning("无法发放物品: " + itemId + ", 数量: " + quantity);
            player.sendMessage(MessageUtils.error("无法发放部分商品，请联系管理员"));
        }
    }
    
    private void startReceiptCleanup(@NotNull PurchaseReceipt receipt) {
        // 启动清理定时器，在回据过期后自动清理内存中的回据
        Bukkit.getScheduler().runTaskLater((org.bukkit.plugin.Plugin)plugin, () -> {
            activeReceipts.remove(receipt.getPlayerId());
        }, (int)(receipt.getExpiryTime()/50L)); // 转换为服务器刻
    }
    
    // 内部类: 存储交易回据信息
    private static class PurchaseReceipt {
        private final String orderId;
        private final UUID playerId;
        private final ShopItem item;
        private final int quantity;
        private final double unitPrice;
        private final double totalPrice;
        private final double taxAmount;
        private final double finalPrice;
        private final LocalDateTime purchaseTime;
        private final long expiryTime;
        
        public PurchaseReceipt(String orderId, UUID playerId, ShopItem item, int quantity,
                             double unitPrice, double totalPrice, double taxAmount, double finalPrice,
                             LocalDateTime purchaseTime, long expiryTime) {
            this.orderId = orderId;
            this.playerId = playerId;
            this.item = item;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
            this.taxAmount = taxAmount;
            this.finalPrice = finalPrice;
            this.purchaseTime = purchaseTime;
            this.expiryTime = System.currentTimeMillis() + expiryTime;
        }
        
        public String getOrderId() { return orderId; }
        public UUID getPlayerId() { return playerId; }
        public ShopItem getItem() { return item; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getTotalPrice() { return totalPrice; }
        public double getTaxAmount() { return taxAmount; }
        public double getFinalPrice() { return finalPrice; }
        public LocalDateTime getPurchaseTime() { return purchaseTime; }
        
        public boolean isExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }
        
        public long getTimeLeft() {
            return Math.max(0, expiryTime - System.currentTimeMillis());
        }
        
        public long getExpiryTime() {
            return expiryTime;
        }
    }
}
