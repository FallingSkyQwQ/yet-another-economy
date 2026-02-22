package com.yae.api.shop.gui;

import com.yae.api.shop.ShopManager;
import com.yae.api.shop.ShopCategory;
import com.yae.api.services.EconomyService;
import com.yae.utils.MessageUtils;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Shop Categories GUI - Shows available shop categories for browsing.
 */
public class ShopCategoriesGUI {
    
    private final ShopManager shopManager;
    private final EconomyService economyService;
    private final Player player;
    private final UUID playerId;
    
    private static final int[] CATEGORY_SLOTS = {11, 12, 13, 14, 15, 21, 22, 23, 24, 25};
    
    public ShopCategoriesGUI(ShopManager shopManager, EconomyService economyService, Player player) {
        this.shopManager = shopManager;
        this.economyService = economyService;
        this.player = player;
        this.playerId = player.getUniqueId();
    }
    
    /**
     * Open the shop categories GUI.
     */
    public void open() {
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtils.colorize("&6&l商店 &f- &e选择分类")))
            .rows(5)
            .create();
        
        // Add frame items
        addFrameItems(gui);
        
        // Load and display categories
        List<ShopCategory> categories = shopManager.getCategories();
        for (int i = 0; i < Math.min(categories.size(), CATEGORY_SLOTS.length); i++) {
            ShopCategory category = categories.get(i);
            GuiItem categoryItem = createCategoryItem(category);
            gui.setItem(CATEGORY_SLOTS[i], categoryItem);
        }
        
        // Add search button
        addSearchButton(gui);
        
        // Add balance display
        addBalanceDisplay(gui);
        
        // Add close button
        addCloseButton(gui);
        
        gui.open(player);
    }
    
    /**
     * Create a GUI item for a shop category.
     */
    private GuiItem createCategoryItem(ShopCategory category) {
        Material material;
        try {
            material = Material.valueOf(category.getIcon());
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }
        
        ItemBuilder builder = ItemBuilder.from(material)
            .name(Component.text(MessageUtils.colorize("&6&l" + category.getDisplayName())));
        
        // Add description
        List<String> descriptions = category.getDescription();
        if (descriptions != null && !descriptions.isEmpty()) {
            for (String line : descriptions) {
                builder.lore(Component.text(MessageUtils.colorize("&7" + line)));
            }
        }
        
        // Add item count
        int enabledCount = category.getEnabledItemCount();
        builder.lore(Component.text(""));
        builder.lore(Component.text(MessageUtils.colorize("&f商品数量: &e" + enabledCount)));
        
        // Add color border
        String color = category.getColor();
        builder.glow(true);
        
        return builder.asGuiItem(event -> {
            event.setCancelled(true);
            
            // Open category items GUI
            new ShopItemsGUI(shopManager, economyService, player, category).open();
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
     * Add search button to the GUI.
     */
    private void addSearchButton(Gui gui) {
        GuiItem searchItem = ItemBuilder.from(Material.COMPASS)
            .name(Component.text(MessageUtils.colorize("&b&l搜索商品")))
            .lore(Component.text(MessageUtils.colorize("&7点击搜索所有商品")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                // Open search GUI would be implemented here
                player.sendMessage(MessageUtils.colorize("&6[YAE] &f搜索功能开发中..."));
            });
        
        gui.setItem(31, searchItem);
    }
    
    /**
     * Add balance display to the GUI.
     */
    private void addBalanceDisplay(Gui gui) {
        double balance = economyService.getBalance(playerId);
        String formattedBalance = economyService.formatCurrency(balance);
        
        GuiItem balanceItem = ItemBuilder.from(Material.GOLD_NUGGET)
            .name(Component.text(MessageUtils.colorize("&6&l你的余额")))
            .lore(Component.text(MessageUtils.colorize("&e" + formattedBalance)))
            .lore(Component.text(MessageUtils.colorize("&7点击刷新余额")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                double currentBalance = economyService.getBalance(playerId);
                String newFormattedBalance = economyService.formatCurrency(currentBalance);
                player.sendMessage(MessageUtils.colorize("&6[YAE] &f当前余额: &e" + newFormattedBalance));
            });
        
        gui.setItem(40, balanceItem);
    }
    
    /**
     * Add close button to the GUI.
     */
    private void addCloseButton(Gui gui) {
        GuiItem closeItem = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtils.colorize("&c&l关闭商店")))
            .asGuiItem(event -> {
                event.setCancelled(true);
                gui.close(player);
            });
        
        gui.setItem(44, closeItem);
    }
}
