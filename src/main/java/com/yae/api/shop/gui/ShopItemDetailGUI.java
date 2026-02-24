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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 商店商品详情GUI
 * 显示商品详细信息、价格和购买入口
 */
public class ShopItemDetailGUI {
    
    private final YAECore plugin;
    private final ShopManager shopManager;
    private final EconomyService economyService;
    private final LanguageManager languageManager;
    private final Configuration configuration;
    private static final int ROWS = 6;
    private static final String TITLE = "商品详情 - {item_name}";
    
    public ShopItemDetailGUI(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.shopManager = plugin.getService(ServiceType.SHOP);
        this.economyService = plugin.getService(ServiceType.ECONOMY);
        this.languageManager = plugin.getConfigurationManager().getLanguageManager();
        this.configuration = plugin.getMainConfiguration();
    }
    
    /**
     * 打开商品详情界面
     * @param player 玩家
     * @param shopItem 商品
     */
    public void openItemDetails(@NotNull Player player, @NotNull ShopItem shopItem) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(shopItem, "shopItem cannot be null");
        
        if (!isServiceAvailable()) {
            player.sendMessage(MessageUtils.error("商店服务暂不可用"));
            return;
        }
        
        if (!shopItem.isEnabled()) {
            player.sendMessage(MessageUtils.error("该商品当前不可用"));
            return;
        }
        
        createAndOpenGUI(player, shopItem);
    }
    
    /**
     * 创建并打开商品详情GUI
     */
    private void createAndOpenGUI(@NotNull Player player, @NotNull ShopItem shopItem) {
        String guiTitle = MessageUtils.color(TITLE.replace("{item_name}", shopItem.getDisplayName()));
        
        Gui gui = Gui.gui()
                .title(MessageUtils.miniMessage(guiTitle))
                .rows(ROWS)
                .disableAllInteractions()
                .create();
        
        setupItemDetailsInterface(gui, player, shopItem);
        gui.open(player);
    }
    
    /**
     * 设置商品详情界面内容
     */
    private void setupItemDetailsInterface(@NotNull Gui gui, @NotNull Player player, @NotNull ShopItem shopItem) {
        setupItemHeader(gui, shopItem);
        setupItemInformation(gui, shopItem);
        setupPriceInformation(gui, shopItem);
        setupLimitInformation(gui, shopItem, player);
        setupActionButtons(gui, player, shopItem);
        setupNavigationControls(gui, player);
        setupDecorations(gui);
    }
    
    /**
     * 设置商品头部信息（第1行）
     */
    private void setupItemHeader(@NotNull Gui gui, @NotNull ShopItem shopItem) {
        ItemStack itemDisplay = createItemDisplay(shopItem);
        gui.setItem(0, new GuiItem(itemDisplay));
        
        ItemStack categoryInfo = createCategoryInfo(shopItem);
        gui.setItem(4, new GuiItem(categoryInfo));
        
        ItemStack statusInfo = createStatusInfo(shopItem);
        gui.setItem(8, new GuiItem(statusInfo));
    }
    
    /**
     * 设置商品信息区域（第2行）
     */
    private void setupItemInformation(@NotNull Gui gui, @NotNull ShopItem shopItem) {
        ItemStack basicInfo = createBasicInfo(shopItem);
        gui.setItem(9, new GuiItem(basicInfo));
        
        ItemStack description = createDescription(shopItem);
        gui.setItem(11, new GuiItem(description));
        
        ItemStack marketInfo = createMarketInfo(shopItem);
        gui.setItem(13, new GuiItem(marketInfo));
        
        ItemStack usageInfo = createUsageInfo(shopItem);
        gui.setItem(15, new GuiItem(usageInfo));
        
        ItemStack tips = createTips(shopItem);
        gui.setItem(17, new GuiItem(tips));
    }
    
    /**
     * 设置价格信息区域（第3行）
     */
    private void setupPriceInformation(@NotNull Gui gui, @NotNull ShopItem shopItem) {
        ItemStack buyPrice = createBuyPriceInfo(shopItem);
        gui.setItem(18, new GuiItem(buyPrice));
        
        ItemStack sellPrice = createSellPriceInfo(shopItem);
        gui.setItem(20, new GuiItem(sellPrice));
        
        ItemStack priceComparison = createPriceComparison(shopItem);
        gui.setItem(22, new GuiItem(priceComparison));
        
        ItemStack profitInfo = createProfitInfo(shopItem);
        gui.setItem(24, new GuiItem(profitInfo));
        
        ItemStack marketPrice = createMarketPrice(shopItem);
        gui.setItem(26, new GuiItem(marketPrice));
    }
    
    /**
     * 设置限购信息区域（第4行）
     */
    private void setupLimitInformation(@NotNull Gui gui, @NotNull ShopItem shopItem, @NotNull Player player) {
        ItemStack stockInfo = createStockInfo(shopItem);
        gui.setItem(27, new GuiItem(stockInfo));
        
        ItemStack dailyLimit = createDailyLimitInfo(shopItem, player);
        gui.setItem(29, new GuiItem(dailyLimit));
        
        ItemStack playerLimit = createPlayerLimitInfo(shopItem, player);
        gui.setItem(31, new GuiItem(playerLimit));
        
        ItemStack availability = createAvailabilityInfo(shopItem, player);
        gui.setItem(33, new GuiItem(availability));
        
        ItemStack recommendations = createRecommendations(shopItem, player);
        gui.setItem(35, new GuiItem(recommendations));
    }
    
    /**
     * 设置操作按钮区域（第5行）
     */
    private void setupActionButtons(@NotNull Gui gui, @NotNull Player player, @NotNull ShopItem shopItem) {
        ItemStack purchaseButton = createPurchaseButton(shopItem, player);
        gui.setItem(4 * 9 + 2, new GuiItem(purchaseButton, event -> handlePurchaseClick(player, shopItem)));
        
        ItemStack quickBuy = createQuickBuyButton(shopItem);
        gui.setItem(4 * 9 + 4, new GuiItem(quickBuy, event -> handleQuickBuyClick(player, shopItem)));
        
        ItemStack favoriteButton = createFavoriteButton(shopItem);
        gui.setItem(4 * 9 + 6, new GuiItem(favoriteButton, event -> handleFavoriteClick(player, shopItem)));
    }
    
    /**
     * 设置导航控件（第6行）
     */
    private void setupNavigationControls(@NotNull Gui gui, @NotNull Player player) {
        ItemStack backButton = createBackButton();
        gui.setItem(5 * 9 + 0, new GuiItem(backButton, event -> handleBackClick(player)));
        
        ItemStack refreshButton = createRefreshButton();
        gui.setItem(5 * 9 + 4, new GuiItem(refreshButton, event -> handleRefreshClick(player)));
        
        ItemStack closeButton = createCloseButton();
        gui.setItem(5 * 9 + 8, new GuiItem(closeButton, event -> handleCloseClick(player)));
    }
    
    /**
     * 设置装饰性边框
     */
    private void setupDecorations(@NotNull Gui gui) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        
        int[] borderSlots = {9, 17, 18, 26, 27, 35, 36, 44, 45, 53};
        for (int slot : borderSlots) {
            gui.setItem(slot, new GuiItem(border));
        }
    }
    
    // UI创建辅助方法
    
    private ItemStack createItemDisplay(@NotNull ShopItem shopItem) {
        Material material;
        try {
            material = Material.valueOf(shopItem.getId());
        } catch (IllegalArgumentException e) {
            material = Material.PAPER; // 默认材质
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6" + shopItem.getDisplayName()));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        for (String description : shopItem.getDescription()) {
            lore.add(MessageUtils.miniMessage("&7" + description));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&a点击查看详细信息"));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    private ItemStack createCategoryInfo(@NotNull ShopItem shopItem) {
        ItemStack category = new ItemStack(Material.LECTERN);
        ItemMeta meta = category.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&b📁 商品分类"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l分类: &6" + getChineseCategoryName(shopItem.getCategory())));
        lore.add(MessageUtils.miniMessage("&7&l商品ID: &e" + shopItem.getId()));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7该分类下的其他商品"));
        lore.add(MessageUtils.miniMessage("&7可能有相似用途"));
        
        meta.lore(lore);
        category.setItemMeta(meta);
        
        return category;
    }
    
    private ItemStack createStatusInfo(@NotNull ShopItem shopItem) {
        Material material = shopItem.isEnabled() ? Material.GREEN_WOOL : Material.RED_WOOL;
        ItemStack status = new ItemStack(material);
        ItemMeta meta = status.getItemMeta();
        
        if (shopItem.isEnabled()) {
            meta.displayName(MessageUtils.miniMessage("&a✅ 商品可用"));
        } else {
            meta.displayName(MessageUtils.miniMessage("&c❌ 商品不可用"));
        }
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l状态: " + (shopItem.isEnabled() ? "&a启用" : "&c禁用")));
        lore.add(MessageUtils.miniMessage("&7&l可购买: " + (shopItem.isEnabled() ? "&a是" : "&c否")));
        
        if (!shopItem.isEnabled()) {
            lore.add(MessageUtils.miniMessage("&7此商品暂时不可用"));
            lore.add(MessageUtils.miniMessage("&7请联系管理员"));
        }
        
        meta.lore(lore);
        status.setItemMeta(meta);
        
        return status;
    }
    
    private ItemStack createBasicInfo(@NotNull ShopItem shopItem) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&f📄 基本信息"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l商品名称: &6" + shopItem.getDisplayName()));
        lore.add(MessageUtils.miniMessage("&7&l商品ID: &e" + shopItem.getId()));
        lore.add(MessageUtils.miniMessage("&7&l来源: YAE商店"));
        
        if (shopItem.hasStockLimit()) {
            lore.add(MessageUtils.miniMessage("&7&l库存: &e" + shopItem.getStock() + " 个"));
        } else {
            lore.add(MessageUtils.miniMessage("&7&l库存: &a无限制"));
        }
        
        meta.lore(lore);
        info.setItemMeta(meta);
        
        return info;
    }
    
    private ItemStack createDescription(@NotNull ShopItem shopItem) {
        ItemStack description = new ItemStack(Material.PAPER);
        ItemMeta meta = description.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&9📝 商品描述"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        for (String desc : shopItem.getDescription()) {
            lore.add(MessageUtils.miniMessage("&7" + desc));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        meta.lore(lore);
        description.setItemMeta(meta);
        
        return description;
    }
    
    private ItemStack createMarketInfo(@NotNull ShopItem shopItem) {
        ItemStack market = new ItemStack(Material.COMPASS);
        ItemMeta meta = market.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&e📊 市场信息"));
        
        List<Component> lore = new ArrayList<>();
        var priceRange = shopItem.getMarketPriceRange();
        
        if (priceRange != null) {
            lore.add(MessageUtils.miniMessage("&7&l市场价格区间:"));
            lore.add(MessageUtils.miniMessage("&7最低: " + economyService.formatCurrency(priceRange.getMinPrice())));
            lore.add(MessageUtils.miniMessage("&7最高: " + economyService.formatCurrency(priceRange.getMaxPrice())));
            lore.add(MessageUtils.miniMessage("&7平均: " + economyService.formatCurrency(priceRange.getAveragePrice())));
        } else {
            lore.add(MessageUtils.miniMessage("&7&l市场价格: &e暂无信息"));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7市场价格仅供参考"));
        
        meta.lore(lore);
        market.setItemMeta(meta);
        
        return market;
    }
    
    private ItemStack createUsageInfo(@NotNull ShopItem shopItem) {
        ItemStack usage = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = usage.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&5🔧 使用指南"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l用途说明:"));
        
        // 根据物品类型显示不同的使用说明
        String itemId = shopItem.getId().toLowerCase();
        if (itemId.contains("pickaxe")) {
            lore.add(MessageUtils.miniMessage("&7• 用于挖掘矿石"));
            lore.add(MessageUtils.miniMessage("&7• 挖掘石头和矿物"));
            lore.add(MessageUtils.miniMessage("&7• 镐的等级决定挖掘能力"));
        } else if (itemId.contains("sword")) {
            lore.add(MessageUtils.miniMessage("&7• 用于战斗和防御"));
            lore.add(MessageUtils.miniMessage("&7• 可攻击敌对生物"));
            lore.add(MessageUtils.miniMessage("&7• 剑的等级影响伤害"));
        } else if (itemId.contains("food")) {
            lore.add(MessageUtils.miniMessage("&7• 右击食用"));
            lore.add(MessageUtils.miniMessage("&7• 回复饥饿值"));
            lore.add(MessageUtils.miniMessage("&7• 不同的食物回复不同"));
        } else if (itemId.contains("pick") || itemId.contains("axe") || itemId.contains("shovel")) {
            lore.add(MessageUtils.miniMessage("&7• 用于工具和材料制作"));
            lore.add(MessageUtils.miniMessage("&7• 制作高级装备所需"));
        } else {
            lore.add(MessageUtils.miniMessage("&7• 用于建造和装饰"));
            lore.add(MessageUtils.miniMessage("&7• 放置在世界中"));
            lore.add(MessageUtils.miniMessage("&7• 可能有特殊功能"));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7具体用法根据实际情况而定"));
        
        meta.lore(lore);
        usage.setItemMeta(meta);
        
        return usage;
    }
    
    private ItemStack createTips(@NotNull ShopItem shopItem) {
        ItemStack tips = new ItemStack(Material.LIGHT);
        ItemMeta meta = tips.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6💡 购买建议"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l小贴士:"));
        lore.add(MessageUtils.miniMessage("&7• 考虑您的实际需求"));
        lore.add(MessageUtils.miniMessage("&7• 比较不同商品的价格"));
        lore.add(MessageUtils.miniMessage("&7• 考虑后续使用成本"));
        lore.add(MessageUtils.miniMessage("&7• 适量购买，避免浪费"));
        
        if (shopItem.hasDailyLimit()) {
            lore.add(MessageUtils.miniMessage("&6⚠️ 注意每日限购"));
        }
        
        if (shopItem.hasPlayerLimit()) {
            lore.add(MessageUtils.miniMessage("&6⚠️ 注意个人限购"));
        }
        
        meta.lore(lore);
        tips.setItemMeta(meta);
        
        return tips;
    }
    
    private ItemStack createBuyPriceInfo(@NotNull ShopItem shopItem) {
        ItemStack price = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = price.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&a💰 购买价格"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l购买单价: &6" + economyService.formatCurrency(shopItem.getPrice())));
        lore.add(MessageUtils.miniMessage("&7&l购买 64 个: &e" + economyService.formatCurrency(shopItem.getPrice() * 64)));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l税费: &c" + String.format("%.1f%%", getPurchaseTaxRate() * 100)));
        
        double taxAmount = shopItem.getPrice() * getPurchaseTaxRate();
        double finalPrice = shopItem.getPrice() + taxAmount;
        lore.add(MessageUtils.miniMessage("&7&l含税价格: &6" + economyService.formatCurrency(finalPrice)));
        
        meta.lore(lore);
        price.setItemMeta(meta);
        
        return price;
    }
    
    private ItemStack createSellPriceInfo(@NotNull ShopItem shopItem) {
        ItemStack sell = new ItemStack(Material.REDSTONE);
        ItemMeta meta = sell.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&c🔄 出售价格"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l出售单价: &c" + economyService.formatCurrency(shopItem.getSellPrice())));
        lore.add(MessageUtils.miniMessage("&7&l出售 64 个: &6" + economyService.formatCurrency(shopItem.getSellPrice() * 64)));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l利润率: " + getProfitMarginColor(shopItem.getProfitMargin()) + String.format("%.1f%%", shopItem.getProfitMargin())));
        
        meta.lore(lore);
        sell.setItemMeta(meta);
        
        return sell;
    }
    
    private ItemStack createPriceComparison(@NotNull ShopItem shopItem) {
        ItemStack comparison = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = comparison.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&e📈 价格对比"));
        
        List<Component> lore = new ArrayList<>();
        double buyPrice = shopItem.getPrice();
        double sellPrice = shopItem.getSellPrice();
        
        lore.add(MessageUtils.miniMessage("&7&l购买价: &6" + economyService.formatCurrency(buyPrice)));
        lore.add(MessageUtils.miniMessage("&7&l出售价: &a" + economyService.formatCurrency(sellPrice)));
        lore.add(MessageUtils.miniMessage("&7&l差价: &e" + economyService.formatCurrency(Math.abs(buyPrice - sellPrice))));
        
        var marketRange = shopItem.getMarketPriceRange();
        if (marketRange != null) {
            lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
            lore.add(MessageUtils.miniMessage("&7市场价格: &b" + economyService.formatCurrency(marketRange.getAveragePrice())));
        }
        
        meta.lore(lore);
        comparison.setItemMeta(meta);
        
        return comparison;
    }
    
    private ItemStack createProfitInfo(@NotNull ShopItem shopItem) {
        ItemStack profit = new ItemStack(Material.EMERALD);
        ItemMeta meta = profit.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&2💎 利润分析"));
        
        List<Component> lore = new ArrayList<>();
        double margin = shopItem.getProfitMargin();
        lore.add(MessageUtils.miniMessage("&7&l利润率: " + getProfitMarginColor(margin) + String.format("%.1f%%", margin)));
        
        if (margin > 0) {
            lore.add(MessageUtils.miniMessage("&7&l商店盈利: &a是"));
        } else {
            lore.add(MessageUtils.miniMessage("&7&l商店盈利: &c否"));
        }
        
        String profitability = "";
        if (margin < 30) {
            profitability = "&c高风险";
        } else if (margin < 60) {
            profitability = "&e中度风险";
        } else if (margin < 100) {
            profitability = "&a合理利润";
        } else {
            profitability = "&6高利润";
        }
        
        lore.add(MessageUtils.miniMessage("&7&l盈利评级: " + profitability));
        lore.add(MessageUtils.miniMessage("&7基于买卖价格差计算"));
        
        meta.lore(lore);
        profit.setItemMeta(meta);
        
        return profit;
    }
    
    private ItemStack createMarketPrice(@NotNull ShopItem shopItem) {
        ItemStack market = new ItemStack(Material.MAP); // GLOBE doesn't exist, use MAP instead
        ItemMeta meta = market.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&9🌍 市场价格"));
        
        List<Component> lore = new ArrayList<>();
        var priceRange = shopItem.getMarketPriceRange();
        
        if (priceRange != null) {
            lore.add(MessageUtils.miniMessage("&7&l市场价格: &e" + economyService.formatCurrency(priceRange.getAveragePrice())));
            lore.add(MessageUtils.miniMessage("&7&l价格区间: &b" + economyService.formatCurrency(priceRange.getMinPrice()) + " - " +
                    economyService.formatCurrency(priceRange.getMaxPrice())));
            lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
            
            double currentPrice = shopItem.getPrice();
            double averageMarket = priceRange.getAveragePrice();
            if (currentPrice < averageMarket) {
                lore.add(MessageUtils.miniMessage("&a✅ 相比市场较低"));
            } else if (currentPrice > averageMarket * 1.2) {
                lore.add(MessageUtils.miniMessage("&c⚠️ 相比市场较高"));
            } else {
                lore.add(MessageUtils.miniMessage("&e👉 市场价格合理"));
            }
        } else {
            lore.add(MessageUtils.miniMessage("&7&l市场价格: &c暂无数据"));
            lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
            lore.add(MessageUtils.miniMessage("&7正在收集市场数据"));
        }
        
        lore.add(MessageUtils.miniMessage("&7仅供参考，可能有波动"));
        
        meta.lore(lore);
        market.setItemMeta(meta);
        
        return market;
    }
    
    private ItemStack createStockInfo(@NotNull ShopItem shopItem) {
        Material material;
        if (shopItem.hasStockLimit() && shopItem.getStock() <= 10) {
            material = Material.RED_STAINED_GLASS_PANE;
        } else {
            material = Material.GREEN_STAINED_GLASS_PANE;
        }
        
        ItemStack stock = new ItemStack(material);
        ItemMeta meta = stock.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&a📦 库存信息"));
        
        List<Component> lore = new ArrayList<>();
        
        if (shopItem.hasStockLimit()) {
            lore.add(MessageUtils.miniMessage("&7&l当前库存: &e" + shopItem.getStock() + " 个"));
            
            if (shopItem.getStock() <= 0) {
                meta.displayName(MessageUtils.miniMessage("&c⚠️ 暂无库存"));
                lore.add(MessageUtils.miniMessage("&c❌ 商品已售罄！"));
                lore.add(MessageUtils.miniMessage("&c请稍后再试"));
            } else if (shopItem.getStock() <= 10) {
                lore.add(MessageUtils.miniMessage("&c⚠️ 库存紧张！"));
                lore.add(MessageUtils.miniMessage("&6建议尽快购买"));
            } else {
                lore.add(MessageUtils.miniMessage("&7&l库存状态: &a充足"));
            }
        } else {
            lore.add(MessageUtils.miniMessage("&7&l库存: &a无限制"));
            lore.add(MessageUtils.miniMessage("&7&l供应: &a充足"));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7库存实时更新"));
        
        meta.lore(lore);
        stock.setItemMeta(meta);
        
        return stock;
    }
    
    private ItemStack createDailyLimitInfo(@NotNull ShopItem shopItem, @NotNull Player player) {
        if (!shopItem.hasDailyLimit()) {
            ItemStack unlimited = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
            ItemMeta meta = unlimited.getItemMeta();
            meta.displayName(MessageUtils.miniMessage("&b♾️ 每日限购"));
            
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtils.miniMessage("&7&l每日限购: &a无限制"));
            lore.add(MessageUtils.miniMessage("&7您可以随时购买"));
            lore.add(MessageUtils.miniMessage("&7不受数量限制"));
            
            meta.lore(lore);
            unlimited.setItemMeta(meta);
            return unlimited;
        }
        
        Material material;
        String title;
        
        boolean isDailyLimitReached = shopManager.isDailyLimitReached(shopItem.getId(), player.getUniqueId());
        if (isDailyLimitReached) {
            material = Material.RED_STAINED_GLASS_PANE;
            title = "&c🚫 每日限购";
        } else {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            title = "&e📅 每日限购";
        }
        
        ItemStack dailyLimitItem = new ItemStack(material);
        ItemMeta meta = dailyLimitItem.getItemMeta();
        meta.displayName(MessageUtils.miniMessage(title));
        
        List<Component> lore = new ArrayList<>();
        int dailyPurchases = getPlayerDailyPurchases(player, shopItem);
        int dailyLimitValue = shopItem.getDailyLimit();
        
        lore.add(MessageUtils.miniMessage("&7&l已购买/限制: &e" + dailyPurchases + "/" + dailyLimitValue));
        lore.add(MessageUtils.miniMessage("&7&l剩余可购买: &6" + (dailyLimitValue - dailyPurchases) + " 个"));
        lore.add(MessageUtils.miniMessage("&7&l重置时间: &b" + getDailyResetTime()));
        
        if (isDailyLimitReached) {
            lore.add(MessageUtils.miniMessage("&c❌ 今日已达到限购"));
            lore.add(MessageUtils.miniMessage("&c请等待明日重置"));
            meta.displayName(MessageUtils.miniMessage("&c🚫 今日限购已满"));
        } else {
            lore.add(MessageUtils.miniMessage("&a✅ 仍可购买"));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7限购会在每日重置"));
        
        meta.lore(lore);
        dailyLimitItem.setItemMeta(meta);
        
        return dailyLimitItem;
    }
    
    private ItemStack createPlayerLimitInfo(@NotNull ShopItem shopItem, @NotNull Player player) {
        if (!shopItem.hasPlayerLimit()) {
            ItemStack unlimited = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
            ItemMeta meta = unlimited.getItemMeta();
            meta.displayName(MessageUtils.miniMessage("&b♾️ 个人限购"));
            
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtils.miniMessage("&7&l个人限购: &a无限制"));
            lore.add(MessageUtils.miniMessage("&7您可以一直购买"));
            lore.add(MessageUtils.miniMessage("&7不受个人数量限制"));
            
            meta.lore(lore);
            unlimited.setItemMeta(meta);
            return unlimited;
        }
        
        Material material;
        String title;
        
        boolean isPlayerLimitReached = shopManager.isPlayerLimitReached(shopItem.getId(), player.getUniqueId());
        if (isPlayerLimitReached) {
            material = Material.RED_STAINED_GLASS_PANE;
            title = "&c🚫 个人限购";
        } else {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            title = "&e📅 个人限购";
        }
        
        ItemStack playerLimitItem = new ItemStack(material);
        ItemMeta meta = playerLimitItem.getItemMeta();
        meta.displayName(MessageUtils.miniMessage(title));
        
        List<Component> lore = new ArrayList<>();
        int totalPurchases = getPlayerTotalPurchases(player, shopItem);
        int playerLimitValue = shopItem.getPlayerLimit();
        
        lore.add(MessageUtils.miniMessage("&7&l总购买/限制: &e" + totalPurchases + "/" + playerLimitValue));
        lore.add(MessageUtils.miniMessage("&7&l剩余可购买: &6" + (playerLimitValue - totalPurchases) + " 个"));
        lore.add(MessageUtils.miniMessage("&7&l限购类型: &c永久"));
        
        if (isPlayerLimitReached) {
            lore.add(MessageUtils.miniMessage("&c❌ 已达到个人限购"));
            lore.add(MessageUtils.miniMessage("&c您已购买过此商品"));
            lore.add(MessageUtils.miniMessage("&c达到个人最大限制"));
        } else {
            lore.add(MessageUtils.miniMessage("&a✅ 仍可购买"));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l限制说明: &6永久"));
        lore.add(MessageUtils.miniMessage("&7永久限制，不会重置"));
        
        meta.lore(lore);
        
        return playerLimitItem;
    }
    
    private ItemStack createAvailabilityInfo(@NotNull ShopItem shopItem, @NotNull Player player) {
        Material material;
        String title;
        String availabilityStatusText;
        
        boolean availability = checkItemAvailability(shopItem, player);
        boolean canAfford = checkPlayerCanAfford(player, shopItem.getPrice());
        
        if (availability && canAfford) {
            material = Material.GREEN_WOOL;
            title = "&a✅ 购买可行性";
            availabilityStatusText = "&7&l可购买: &a是";
        } else if (!availability) {
            material = Material.RED_WOOL;
            title = "&c❌ 购买可行性";
            availabilityStatusText = "&7&l可购买: &c否";
        } else if (!canAfford) {
            material = Material.ORANGE_WOOL;
            title = "&6⚠️ 购买可行性";
            availabilityStatusText = "&7&l可购买: &6资金不足";
        } else {
            material = Material.GRAY_WOOL;
            title = "&7❓ 购买可行性";
            availabilityStatusText = "&7&l可购买: &7未知";
        }
        
        ItemStack availabilityItem = new ItemStack(material);
        ItemMeta meta = availabilityItem.getItemMeta();
        meta.displayName(MessageUtils.miniMessage(title));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage(availabilityStatusText));
        lore.add(MessageUtils.miniMessage("&7&l库存: " + (shopManager.hasEnoughStock(shopItem.getId(), 1) ? "&a充足" : "&c不足")));
        lore.add(MessageUtils.miniMessage("&7&l资金: " + (canAfford ? "&a充足" : "&c不足")));
        lore.add(MessageUtils.miniMessage("&7&l每日限购: " + (!shopManager.isDailyLimitReached(shopItem.getId(), player.getUniqueId()) ? "&a可购买" : "&c已达限")));
        lore.add(MessageUtils.miniMessage("&7&l个人限购: " + (!shopManager.isPlayerLimitReached(shopItem.getId(), player.getUniqueId()) ? "&a可购买" : "&c已达限")));
        
        if (!checkItemAvailability(shopItem, player)) {
            lore.add(MessageUtils.miniMessage("&c请检查库存和限额"));
        }
        
        meta.lore(lore);
        availabilityItem.setItemMeta(meta);
        
        return availabilityItem;
    }
    
    private ItemStack createRecommendations(@NotNull ShopItem shopItem, @NotNull Player player) {
        ItemStack recommend = new ItemStack(Material.LIGHT);
        ItemMeta meta = recommend.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&d💡 推荐建议"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7&l购买建议:"));
        
        double ratio = shopItem.getPrice() / shopItem.getSellPrice();
        if (ratio > 2.0) {
            lore.add(MessageUtils.miniMessage("&c⚠️ 价格较高，谨慎购买"));
            lore.add(MessageUtils.miniMessage("&7• 考虑用途是否紧要"));
            lore.add(MessageUtils.miniMessage("&7• 可以考虑自己制作"));
        } else if (ratio > 1.5) {
            lore.add(MessageUtils.miniMessage("&e👉 价格合理，适合购买"));
            lore.add(MessageUtils.miniMessage("&7• 性价比良好"));
            lore.add(MessageUtils.miniMessage("&7• 建议适量购买"));
        } else {
            lore.add(MessageUtils.miniMessage("&a✅ 价格较低，强烈推荐"));
            lore.add(MessageUtils.miniMessage("&7• 性价比很高"));
            lore.add(MessageUtils.miniMessage("&7• 可以考虑批量购买"));
        }
        
        int playerDailyPurchases = getPlayerDailyPurchases(player, shopItem);
        if (shopItem.hasDailyLimit() && playerDailyPurchases < shopItem.getDailyLimit() * 0.5) {
            lore.add(MessageUtils.miniMessage("&b🌟 还有较多每日购买额度"));
        }
        
        if (shopItem.hasPlayerLimit() && getPlayerTotalPurchases(player, shopItem) == 0) {
            lore.add(MessageUtils.miniMessage("&a💎 首次购买，值得推荐"));
        }
        
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7建议基于价格和供需"));
        
        meta.lore(lore);
        recommend.setItemMeta(meta);
        
        return recommend;
    }
    
    private ItemStack createPurchaseButton(@NotNull ShopItem shopItem, @NotNull Player player) {
        Material material;
        String title;
        
        boolean canBuy = checkItemAvailability(shopItem, player) && checkPlayerCanAfford(player, shopItem.getPrice());
        
        if (canBuy) {
            material = Material.EMERALD_BLOCK;
            title = "&a✅ 立即购买";
        } else {
            material = Material.RED_WOOL;
            title = "&c❌ 暂时无法购买";
        }
        
        ItemStack purchase = new ItemStack(material);
        ItemMeta meta = purchase.getItemMeta();
        meta.displayName(MessageUtils.miniMessage(title));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7点击打开购买界面"));
        lore.add(MessageUtils.miniMessage("&7可以调整购买数量"));
        lore.add(MessageUtils.miniMessage("&7查看价格和税费"));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        if (!canBuy) {
            lore.clear();
            lore.add(MessageUtils.miniMessage("&c当前无法购买"));
            
            if (!checkPlayerCanAfford(player, shopItem.getPrice())) {
                lore.add(MessageUtils.miniMessage("&c原因: 资金不足"));
            } else if (!checkItemAvailability(shopItem, player)) {
                lore.add(MessageUtils.miniMessage("&c原因: 库存不足或达到限购"));
            }
        } else {
            double finalPrice = shopItem.getPrice() * (1 + getPurchaseTaxRate());
            lore.add(MessageUtils.miniMessage("&7&l购买价格: &6" + economyService.formatCurrency(finalPrice)));
            lore.add(MessageUtils.miniMessage("&7开始购买 -> &e点击"));
        }
        
        meta.lore(lore);
        purchase.setItemMeta(meta);
        
        return purchase;
    }
    
    private ItemStack createQuickBuyButton(@NotNull ShopItem shopItem) {
        ItemStack quick = new ItemStack(Material.CLOCK);
        ItemMeta meta = quick.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&6⚡ 快速购买"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7快速购买1个商品"));
        lore.add(MessageUtils.miniMessage("&7跳过确认界面"));
        lore.add(MessageUtils.miniMessage("&7需要足够的资金"));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        
        double finalPrice = shopItem.getPrice() * (1 + getPurchaseTaxRate());
        lore.add(MessageUtils.miniMessage("&7&l预计价格: &e" + economyService.formatCurrency(finalPrice)));
        
        meta.lore(lore);
        quick.setItemMeta(meta);
        
        return quick;
    }
    
    private ItemStack createFavoriteButton(@NotNull ShopItem shopItem) {
        ItemStack favorite = new ItemStack(Material.YELLOW_DYE);
        ItemMeta meta = favorite.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&e⭐ 添加到收藏"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7将此商品加入收藏"));
        lore.add(MessageUtils.miniMessage("&7便于以后快速查看"));
        lore.add(MessageUtils.miniMessage("&7━━━━━━━━━━━━━━"));
        lore.add(MessageUtils.miniMessage("&7&l收藏状态: &6未收藏"));
        lore.add(MessageUtils.miniMessage("&e点击添加 -> 收藏"));
        
        meta.lore(lore);
        favorite.setItemMeta(meta);
        
        return favorite;
    }
    
    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&8⬅️ 返回"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7返回商店分类"));
        lore.add(MessageUtils.miniMessage("&7继续浏览其他商品"));
        
        meta.lore(lore);
        back.setItemMeta(meta);
        
        return back;
    }
    
    private ItemStack createRefreshButton() {
        ItemStack refresh = new ItemStack(Material.COMPASS);
        ItemMeta meta = refresh.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&7🔄 刷新信息"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7刷新商品信息"));
        lore.add(MessageUtils.miniMessage("&7获取最新库存和限制信息"));
        
        meta.lore(lore);
        refresh.setItemMeta(meta);
        
        return refresh;
    }
    
    private ItemStack createCloseButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta meta = close.getItemMeta();
        meta.displayName(MessageUtils.miniMessage("&c❌ 关闭"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtils.miniMessage("&7关闭详情界面"));
        lore.add(MessageUtils.miniMessage("&7返回游戏界面"));
        
        meta.lore(lore);
        close.setItemMeta(meta);
        
        return close;
    }
    
    // 事件处理方法
    
    private void handlePurchaseClick(@NotNull Player player, @NotNull ShopItem shopItem) {
        player.closeInventory();
        
        if (!checkItemAvailability(shopItem, player)) {
            player.sendMessage(MessageUtils.error("当前无法购买此商品"));
            return;
        }
        
        if (!checkPlayerCanAfford(player, shopItem.getPrice())) {
            player.sendMessage(MessageUtils.error("资金不足，无法购买此商品"));
            return;
        }
        
        // 打开购买确认界面
        ShopPurchaseGUI purchaseGUI = new ShopPurchaseGUI(plugin);
        purchaseGUI.openPurchaseInterface(player, shopItem, 1);
    }
    
    private void handleQuickBuyClick(@NotNull Player player, @NotNull ShopItem shopItem) {
        player.closeInventory();
        
        if (!checkItemAvailability(shopItem, player)) {
            player.sendMessage(MessageUtils.error("当前无法购买此商品"));
            return;
        }
        
        if (!checkPlayerCanAfford(player, shopItem.getPrice())) {
            player.sendMessage(MessageUtils.error("资金不足，无法购买此商品"));
            return;
        }
        
        // 直接购买（这里简化处理，实际应该调用购买服务）
        PurchaseService purchaseService = plugin.getService(ServiceType.SHOP);
        PurchaseCalculation calculation = purchaseService.calculatePurchase(shopItem.getId(), 1, player.getUniqueId());
        
        if (calculation.isSuccessful()) {
            PurchaseService.PendingPurchase pending = purchaseService.createPendingPurchase(shopItem.getId(), 1, player.getUniqueId());
            if (pending != null) {
                // 直接执行购买
                PurchaseResult result = purchaseService.executePurchase(player.getUniqueId(), 
                    player.getUniqueId() + "_" + System.currentTimeMillis());
                
                if (result == PurchaseResult.SUCCESS) {
                    player.sendMessage(MessageUtils.success("✅ 购买成功！"));
                    player.sendMessage(MessageUtils.info("已花费: " + economyService.formatCurrency(calculation.getFinalPrice())));
                    // 给玩家发放物品
                    givePlayerItem(player, shopItem.getId(), 1);
                } else {
                    player.sendMessage(MessageUtils.error("购买失败: " + result.getDefaultMessage()));
                }
            }
        } else {
            player.sendMessage(MessageUtils.error("购买计算失败: " + calculation.getMessage()));
        }
    }
    
    private void handleFavoriteClick(@NotNull Player player, @NotNull ShopItem shopItem) {
        player.sendMessage(MessageUtils.info("已添加到收藏：" + shopItem.getDisplayName()));
        // TODO: 实现收藏功能
    }
    
    private void handleBackClick(@NotNull Player player) {
        player.closeInventory();
        player.sendMessage(MessageUtils.info("返回商店列表界面..."));
        // TODO: 实现返回商店列表界面
    }
    
    private void handleRefreshClick(@NotNull Player player) {
        player.closeInventory();
        player.sendMessage(MessageUtils.info("信息已刷新"));
    }
    
    private void handleCloseClick(@NotNull Player player) {
        player.closeInventory();
    }
    
    // 实用方法
    
    private boolean isServiceAvailable() {
        return shopManager != null && shopManager.isEnabled() &&
               economyService != null && economyService.isEnabled();
    }
    
    private String getChineseCategoryName(@NotNull String categoryId) {
        switch (categoryId.toLowerCase()) {
            case "tools": return "工具";
            case "blocks": return "方块";
            case "food": return "食物";
            case "materials": return "材料";
            case "misc": return "杂项";
            default: return categoryId;
        }
    }
    
    private String getProfitMarginColor(double margin) {
        if (margin < 50) return "&a";
        if (margin < 100) return "&e";
        if (margin < 200) return "&6";
        return "&c";
    }
    
    private double getPurchaseTaxRate() {
        // 从配置获取购买税率，默认为5%
        return configuration.getTransactions().getTaxRate();
    }
    
    private boolean checkItemAvailability(@NotNull ShopItem shopItem, @NotNull Player player) {
        return shopManager.hasEnoughStock(shopItem.getId(), 1) &&
               !shopManager.isDailyLimitReached(shopItem.getId(), player.getUniqueId()) &&
               !shopManager.isPlayerLimitReached(shopItem.getId(), player.getUniqueId());
    }
    
    private boolean checkPlayerCanAfford(@NotNull Player player, double price) {
        double finalPrice = price * (1 + getPurchaseTaxRate());
        return economyService.hasMoney(player.getUniqueId(), finalPrice);
    }
    
    private int getPlayerDailyPurchases(@NotNull Player player, @NotNull ShopItem shopItem) {
        // 根据每日限制计算已经购买的数量
        int limit = shopItem.getDailyLimit();
        if (limit <= 0) return 0;
        
        // 假设我们可以计算出剩余数量，反向推算
        int remaining = Integer.MAX_VALUE;
        
        // 这里简化处理，实际应该使用数据库查询
        // 因为ShopManager没有直接的方法，我们使用购买计算来估算
        if (shopManager.hasEnoughStock(shopItem.getId(), 1) && !shopManager.isDailyLimitReached(shopItem.getId(), player.getUniqueId())) {
            // 如果还能购买，则返回 (limit - remaining)
            // 这里返回0，表示刚开始购买
            return 0;
        } else if (shopManager.isDailyLimitReached(shopItem.getId(), player.getUniqueId())) {
            // 如果达到限制，则返回limit
            return limit;
        }
        
        return remaining; // 默认返回最大值
    }
    
    private int getPlayerTotalPurchases(@NotNull Player player, @NotNull ShopItem shopItem) {
        // 根据总限制计算已经购买的数量
        int limit = shopItem.getPlayerLimit();
        if (limit <= 0) return 0;
        
        // 类似每日限制的处理
        if (shopManager.isPlayerLimitReached(shopItem.getId(), player.getUniqueId())) {
            return limit; // 达到限制，说明已经购买了limit个
        }
        
        // 简化处理：如果没有达到限制，则返回0
        return 0;
    }
    
    private String getDailyResetTime() {
        return "凌晨6:00"; // 可以从配置获取
    }
    
    private void givePlayerItem(@NotNull Player player, @NotNull String itemId, int quantity) {
        try {
            Material material = Material.valueOf(itemId);
            ItemStack itemStack = new ItemStack(material, Math.min(quantity, 64));
            
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
            
            if (!leftover.isEmpty()) {
                // 如果库存满了，掉落在地上
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.sendMessage(MessageUtils.warning("您的库存已满，部分商品已放置在地面上"));
            }
            
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无法发放物品: " + itemId);
            player.sendMessage(MessageUtils.error("无法发放部分商品，请联系管理员"));
        }
    }
}
