package com.yae.api.shop.gui;

import com.yae.api.shop.ShopManager;
import com.yae.api.shop.ShopCategory;
import com.yae.api.shop.ShopItem;
import com.yae.api.services.EconomyService;
import com.yae.utils.MessageUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Shop Items GUI - Shows items within a specific category.
 */
public class ShopItemsGUI {
    
    private final ShopManager shopManager;
    private final EconomyService economyService;
    private final Player player;
    private final ShopCategory category;
    private final UUID playerId;
    
    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16,
                                            19, 20, 21, 22, 23, 24, 25,
                                            28, 29, 30, 31, 32, 33, 34};
    
    public ShopItemsGUI(ShopManager shopManager, EconomyService economyService, Player player, ShopCategory category) {
        this.shopManager = shopManager;
        this.economyService = economyService;
        this.player = player;
        this.category = category;
        this.playerId = player.getUniqueId();
    }
    
    /**
     * Open the shop items GUI for the category.
     */
    public void open() {
        List<ShopItem> items = category.getEnabledItems();
        
        // Create paginated GUI if there are many items
        if (items.size() > ITEM_SLOTS.length) {
            openPaginated(items);
        } else {
            openSinglePage(items);
        }
    }
    
    /**
     * Open a single-page GUI for items.
     */
    private void openSinglePage(List<ShopItem> items) {
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtil.colorize("&6&l" + category.getDisplayName() + " &f- &e商品列表")))
            .rows(5)
            .create();
        
        // Add frame items
        addFrameItems(gui);
        
        // Add items
        for (int i = 0; i < Math.min(items.size(), ITEM_SLOTS.length); i++) {
            ShopItem item = items.get(i);
            GuiItem itemGui = createItemGui(item);
            gui.setItem(ITEM_SLOTS[i], itemGui);
        }
        
        // Add navigation and controls
        addNavigationItems(gui, true);
        
        gui.open(player);
    }
    
    /**
     * Open a paginated GUI for items.
     */
    private void openPaginated(List<ShopItem> items) {
        PaginatedGui gui = Gui.paginated()
            .title(Component.text(MessageUtil.colorize("&6&l" + category.getDisplayName() + " &f- &e商品列表")))
            .rows(6)
            .pageSize(21)
            .create();
        
        // Add items to pages
        for (ShopItem item : items) {
            GuiItem itemGui = createItemGui(item);
            gui.addItem(itemGui);
        }
        
        // Add navigation and controls for paginated GUI
        addPaginatedNavigation(gui, items);
        
        gui.open(player);
    }
    
    /**
     * Create a GUI item for a shop item.
     */
    private GuiItem createItemGui(ShopItem item) {
        Material material;
        try {
            material = Material.valueOf(item.getId());
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }
        
        ItemBuilder builder = ItemBuilder.from(material)
            .name(Component.text(MessageUtil.colorize("&6&l" + item.getDisplayName())));
        
        // Add description
        List<String> descriptions = item.getDescription();
        if (descriptions != null && !descriptions.isEmpty()) {
            for (String line : descriptions) {
                builder.lore(Component.text(MessageUtil.colorize("&7" + line)));
            }
        }
        
        // Add pricing information
        builder.lore(Component.text(""));
        builder.lore(Component.text(MessageUtil.colorize("&f购买价格: &e" + String.format("%.2f", item.getPrice()))));
        if (item.getSellPrice() > 0) {
            builder.lore(Component.text(MessageUtil.colorize("&f出售价格: &a" + String.format("%.2f", item.getSellPrice()))));
        }
        
        // Add stock and limit information
        builder.lore(Component.text(""));
        if (item.hasStockLimit()) {
            builder.lore(Component.text(MessageUtil.colorize("&f库存: &e" + item.getStock())));
        }
        
        if (item.hasDailyLimit()) {
            int dailyPurchases = shopManager.isDailyLimitReached(item.getId(), playerId) ? 
                               item.getDailyLimit() : getPlayerDailyPurchases(item.getId());
            int remaining = Math.max(0, item.getDailyLimit() - dailyPurchases);
            builder.lore(Component.text(MessageUtil.colorize("&f每日限购: &c" + item.getDailyLimit() + " (剩余: " + remaining + ")")));
        }
        
        if (item.hasPlayerLimit()) {
            int totalPurchases = getPlayerTotalPurchases(item.getId());
            int remaining = Math.max(0, item.getPlayerLimit() - totalPurchases);
            builder.lore(Component.text(MessageUtil.colorize("&f永久限购: &c" + item.getPlayerLimit() + " (剩余: " + remaining + ")")));
        }
        
        // Add available quantity
        int available = shopManager.getAvailableQuantity(item.getId(), playerId);
        if (available > 0) {
            builder.lore(Component.text(""));
            builder.lore(Component.text(MessageUtil.colorize("&f可购买: &a" + available)));
        } else {
            builder.lore(Component.text(""));
            builder.lore(Component.text(MessageUtil.colorize("&c&l暂不可购买")));
        }
        
        builder.glow(item.isEnabled());
        
        return builder.asGuiItem(event -> {
            event.setCancelled(true);
            
            if (item.isEnabled() && available > 0) {
                // Open item detail/purchase GUI
                new ShopItemDetailGUI(shopManager, economyService, player, item).open();
            }
        });
    }
    
    /**
     * Add frame items to the GUI.
     */
    private void addFrameItems(Gui gui) {
        Material frameMaterial = Material.GRAY_STAINED_GLASS_PANE;
        GuiItem frameItem = ItemBuilder.from(frameMaterial)
            .name(Component.text(""))
            .asGuiItem(event -> event.setCancelled(true));
        
        // Fill edges
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, frameItem); // Top row
            gui.setItem(i + 36, frameItem); // Bottom row
        }
        for (int i = 0; i < 5; i++) {
            gui.setItem(i * 9, frameItem); // Left column
            gui.setItem(i * 9 + 8, frameItem); // Right column
        }
    }
    
    /**
     * Add navigation and control items to the GUI.
     */
    private void addNavigationItems(Gui gui, boolean isSinglePage) {
        // Back to categories
        GuiItem backItem = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l返回分类")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                new ShopCategoriesGUI(shopManager, economyService, player).open();
            });
        
        // Refresh
        GuiItem refreshItem = ItemBuilder.from(Material.COMPASS)
            .name(Component.text(MessageUtil.colorize("&b&l刷新")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                open(); // Refresh the GUI
            });
        
        // Close
        GuiItem closeItem = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.colorize("&c&l关闭")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                gui.close(player);
            });
        
        if (isSinglePage) {
            gui.setItem(37, backItem);
            gui.setItem(39, refreshItem);
            gui.setItem(41, closeItem);
        } else {
            gui.setItem(45, backItem);
            gui.setItem(48, refreshItem);
            gui.setItem(50, closeItem);
        }
    }
    
    /**
     * Add navigation controls for paginated GUI.
     */
    private void addPaginatedNavigation(PaginatedGui gui, List<ShopItem> items) {
        // Previous page
        GuiItem prevItem = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l上一页")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                gui.previous();
            });
        
        // Next page
        GuiItem nextItem = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l下一页")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                gui.next();
            });
        
        // Page info
        GuiItem pageInfo = ItemBuilder.from(Material.PAPER)
            .name(Component.text(MessageUtil.colorize("&6&l页面信息")))
            .lore(Component.text(MessageUtil.colorize("&f商品总数: &e" + items.size())))
            .asGuiItem(event -> event.setCancelled(true));
        
        addNavigationItems(gui, false);
        
        gui.setItem(46, prevItem);
        gui.setItem(49, pageInfo);
        gui.setItem(52, nextItem);
    }
    
    // Helper methods
    
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
