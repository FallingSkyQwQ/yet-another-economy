package com.yae.api.shop.gui;

import com.yae.api.shop.ShopManager;
import com.yae.api.shop.ShopItem;
import com.yae.api.shop.PurchaseService;
import com.yae.api.services.EconomyService;
import com.yae.utils.MessageUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Shop Item Detail GUI - Shows detailed item information and purchase controls.
 */
public class ShopItemDetailGUI {
    
    private final ShopManager shopManager;
    private final EconomyService economyService;
    private PurchaseService purchaseService;
    private final Player player;
    private final ShopItem item;
    private final UUID playerId;
    
    private static final int[] QUANTITY_SLOTS = {19, 20, 21, 22, 23, 24};
    private int currentQuantity = 1;
    private int maxQuantity = 1;
    
    public ShopItemDetailGUI(ShopManager shopManager, EconomyService economyService, Player player, ShopItem item) {
        this.shopManager = shopManager;
        this.economyService = economyService;
        this.purchaseService = null; // Will be set via setter method
        this.player = player;
        this.item = item;
        this.playerId = player.getUniqueId();
        
        // Set reasonable default max quantity
        this.maxQuantity = 64; // Stack size
    }
    
    /**
     * Set the purchase service.
     */
    public void setPurchaseService(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }
    
    /**
     * Open the item detail GUI.
     */
    public void open() {
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtil.color("&6&l" + item.getDisplayName() + " &f- &e详细信息")))
            .rows(6)
            .create();
        
        updateGUI(gui);
        gui.open(player);
    }
    
    /**
     * Update the GUI with current information.
     */
    private void updateGUI(Gui gui) {
        gui.clear();
        
        // Add item display
        addItemDisplay(gui);
        
        // Add quantity controls
        addQuantityControls(gui);
        
        // Add price information
        addPriceInformation(gui);
        
        // Add purchase buttons
        addPurchaseButtons(gui);
        
        // Add stock and limit information
        addLimitInformation(gui);
        
        // Add navigation
        addNavigationItems(gui);
    }
    
    /**
     * Add the main item display.
     */
    private void addItemDisplay(Gui gui) {
        Material material;
        try {
            material = Material.valueOf(item.getId());
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }
        
        ItemBuilder builder = ItemBuilder.from(material)
            .name(Component.text(MessageUtil.color("&6&l" + item.getDisplayName())));
        
        // Add detailed description
        List<String> descriptions = item.getDescription();
        if (descriptions != null && !descriptions.isEmpty()) {
            for (String line : descriptions) {
                builder.lore(Component.text(MessageUtil.color("&7" + line)));
            }
        }
        
        // Add item type and category
        builder.lore(Component.text(""));
        builder.lore(Component.text(MessageUtil.color("&f类型: &e" + getItemType(item.getId()))));
        builder.lore(Component.text(MessageUtil.color("&f分类: &e" + "商店分类")));
        
        GuiItem itemGui = builder.asGuiItem(event -> {
            event.setCancelled(true);
        });
        
        gui.setItem(4, itemGui);
    }
    
    /**
     * Add quantity selection controls.
     */
    private void addQuantityControls(Gui gui) {
        // Current quantity display
        GuiItem quantityDisplay = ItemBuilder.from(Material.PAPER)
            .name(Component.text(MessageUtil.colorize("&6&l数量选择")))
            .lore(Component.text(MessageUtil.colorize("&f当前数量: &e" + currentQuantity)))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.colorize("&7点击右侧按钮调整数量")))
            .asGuiItem(event -> event.setCancelled(true));
        
        gui.setItem(13, quantityDisplay);
        
        // Quantity adjustment buttons
        String[] buttonNames = {"-64", "-10", "-1", "+1", "+10", "+64"};
        Material[] materials = {Material.RED_CONCRETE, Material.RED_WOOL, Material.RED_STAINED_GLASS,
                               Material.GREEN_STAINED_GLASS, Material.GREEN_WOOL, Material.GREEN_CONCRETE};
        int[] amounts = {-64, -10, -1, 1, 10, 64};
        
        for (int i = 0; i < QUANTITY_SLOTS.length; i++) {
            final int amount = amounts[i];
            
            GuiItem button = ItemBuilder.from(materials[i])
                .name(Component.text(MessageUtil.colorize("&6&l" + buttonNames[i])))
                .lore(Component.text(MessageUtil.colorize("&7点击调整数量")))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    adjustQuantity(amount);
                    updateGUI(gui);
                });
            
            gui.setItem(QUANTITY_SLOTS[i], button);
        }
        
        // Max/Min buttons
        GuiItem minButton = ItemBuilder.from(Material.REDSTONE_BLOCK)
            .name(Component.text(MessageUtil.colorize("&c&l最小数量")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                currentQuantity = 1;
                updateGUI(gui);
            });
        
        GuiItem maxButton = ItemBuilder.from(Material.EMERALD_BLOCK)
            .name(Component.text(MessageUtil.colorize("&a&l最大数量")))
            .lore(Component.text(MessageUtil.colorize("&7最大可购买: &e" + maxQuantity)))
            .asGuiItem(event -> {
                event.setCancelled(true);
                currentQuantity = maxQuantity;
                updateGUI(gui);
            });
        
        gui.setItem(18, minButton);
        gui.setItem(26, maxButton);
    }
    
    /**
     * Add price information display.
     */
    private void addPriceInformation(Gui gui) {
        GuiItem priceInfo = ItemBuilder.from(Material.EMERALD)
            .name(Component.text(MessageUtil.colorize("&6&l价格信息")))
            .lore(Component.text(MessageUtil.colorize("&f购买单价: &e" + String.format("%.2f", item.getPrice()))))
            .lore(Component.text(MessageUtil.colorize("&f数量单价: &e" + String.format("%.2f", currentQuantity * item.getPrice()))))
            .lore(Component.text(MessageUtil.colorize("&f税率: &e5%")))
            .lore(Component.text(MessageUtil.colorize("&f税费: &e" + String.format("%.2f", currentQuantity * item.getPrice() * 0.05))))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.colorize("&6&l最终价格: &e" + String.format("%.2f", currentQuantity * item.getPrice() * 1.05))))
            .asGuiItem(event -> event.setCancelled(true));
        
        gui.setItem(37, priceInfo);
    }
    
    /**
     * Add purchase confirmation buttons.
     */
    private void addPurchaseButtons(Gui gui) {
        double totalPrice = currentQuantity * item.getPrice() * 1.05; // Including tax
        double playerBalance = economyService.getBalance(playerId);
        boolean canAfford = playerBalance >= totalPrice;
        
        // Buy button
        Material buyMaterial = canAfford ? Material.GREEN_WOOL : Material.RED_WOOL;
        String buyColor = canAfford ? "&a" : "&c";
        
        GuiItem buyButton = ItemBuilder.from(buyMaterial)
            .name(Component.text(MessageUtil.colorize(buyColor + "&l立即购买")))
            .lore(Component.text(MessageUtil.colorize("&f购买: &e" + currentQuantity + " x " + item.getDisplayName())))
            .lore(Component.text(MessageUtil.colorize("&f总价: &e" + String.format("%.2f", totalPrice))))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.colorize("&7你的余额: &e" + economyService.formatCurrency(playerBalance))))
            .asGuiItem(event -> {
                event.setCancelled(true);
                
                if (canAfford && currentQuantity > 0) {
                    // Create pending purchase and open confirmation GUI
                    var pendingPurchase = purchaseService.createPendingPurchase(
                        item.getId(), currentQuantity, playerId);
                    
                    if (pendingPurchase != null) {
                        gui.close(player);
                        new ShopPurchaseGUI(shopManager, economyService, purchaseService, player, pendingPurchase).open();
                    } else {
                        player.sendMessage(MessageUtil.colorize("&c[YAE] 购买失败，请重试"));
                    }
                } else if (!canAfford) {
                    player.sendMessage(MessageUtil.colorize("&c[YAE] 余额不足，无法购买"));
                }
            });
        
        gui.setItem(39, buyButton);
        
        // Sell button (if applicable)
        if (item.getSellPrice() > 0) {
            GuiItem sellButton = ItemBuilder.from(Material.YELLOW_WOOL)
                .name(Component.text(MessageUtil.colorize("&e&l出售")))
                .lore(Component.text(MessageUtil.colorize("&f出售单价: &a" + String.format("%.2f", item.getSellPrice()))))
                .lore(Component.text(""))
                .lore(Component.text(MessageUtil.colorize("&7点击出售你背包中的物品")))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.sendMessage(MessageUtil.colorize("&6[YAE] &f出售功能开发中..."));
                });
            
            gui.setItem(41, sellButton);
        }
    }
    
    /**
     * Add stock and limit information.
     */
    private void addLimitInformation(Gui gui) {
        StringBuilder limitInfo = new StringBuilder();
        
        // Stock information
        if (item.hasStockLimit()) {
            limitInfo.append("&f库存: &e").append(item.getStock()).append("\n");
        }
        
        // Daily limit
        if (item.hasDailyLimit()) {
            int dailyPurchases = getPlayerDailyPurchases(item.getId());
            int remaining = Math.max(0, item.getDailyLimit() - dailyPurchases);
            limitInfo.append("&f每日限购: &c").append(item.getDailyLimit())
                    .append(" &7(剩余: ").append(remaining).append(")\n");
        }
        
        // Player limit
        if (item.hasPlayerLimit()) {
            int totalPurchases = getPlayerTotalPurchases(item.getId());
            int remaining = Math.max(0, item.getPlayerLimit() - totalPurchases);
            limitInfo.append("&f永久限购: &c").append(item.getPlayerLimit())
                    .append(" &7(剩余: ").append(remaining).append(")\n");
        }
        
        GuiItem limitItem = ItemBuilder.from(Material.BOOK)
            .name(Component.text(MessageUtil.colorize("&6&l购买限制")))
            .lore(Component.text(MessageUtil.colorize(limitInfo.toString())))
            .asGuiItem(event -> event.setCancelled(true));
        
        gui.setItem(43, limitItem);
    }
    
    /**
     * Add navigation items.
     */
    private void addNavigationItems(Gui gui) {
        // Back to items list
        GuiItem backItem = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l返回商品列表")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                new ShopItemsGUI(shopManager, economyService, player, category).open();
            });
        
        // Main shop
        GuiItem mainShopItem = ItemBuilder.from(Material.HOME)
            .name(Component.text(MessageUtil.colorize("&b&l返回商店主页")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                new ShopCategoriesGUI(shopManager, economyService, player).open();
            });
        
        // Close
        GuiItem closeItem = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.colorize("&c&l关闭")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                gui.close(player);
            });
        
        gui.setItem(45, backItem);
        gui.setItem(46, mainShopItem);
        gui.setItem(53, closeItem);
        
        // Fill remaining slots with glass
        Material glass = Material.GRAY_STAINED_GLASS_PANE;
        for (int i = 0; i < 54; i++) {
            if (gui.getInventory().getItem(i) == null) {
                gui.setItem(i, ItemBuilder.from(glass).name(Component.text("")).asGuiItem());
            }
        }
    }
    
    /**
     * Adjust the current quantity.
     */
    private void adjustQuantity(int amount) {
        currentQuantity = Math.max(1, Math.min(maxQuantity, currentQuantity + amount));
    }
    
    // Helper methods
    
    private String getItemType(String itemId) {
        // Simple type mapping based on item ID
        if (itemId.contains("PICKAXE") || itemId.contains("SWORD") || itemId.contains("AXE") || itemId.contains("SHOVEL") || itemId.contains("HOE")) {
            return "工具";
        } else if (itemId.contains("PLANK") || itemId.contains("STONE") || itemId.contains("BRICK") || itemId.contains("GLASS")) {
            return "建筑方块";
        } else if (itemId.contains("FOOD") || itemId.contains("BREAD") || itemId.contains("MEAT") || itemId.contains("CARROT")) {
            return "食物";
        } else if (itemId.contains("INGOT") || itemId.contains("DIAMOND") || itemId.contains("COAL") || itemId.contains("STICK")) {
            return "材料";
        } else {
            return "杂项";
        }
    }
    
    private int getPlayerDailyPurchases(String itemId) {
        // This should be implemented in ShopManager or PurchaseService
        // For now, return 0 as placeholder
        return 0;
    }
    
    private int getPlayerTotalPurchases(String itemId) {
        // This should be implemented in ShopManager or PurchaseService
        // For now, return 0 as placeholder
        return 0;
    }
}
