package com.yae.api.shop.gui;

import com.yae.api.shop.ShopManager;
import com.yae.api.shop.PurchaseService;
import com.yae.api.shop.ShopItem;
import com.yae.api.services.EconomyService;
import com.yae.utils.MessageUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Shop Receipt GUI - Shows purchase receipt after transaction
 */
public class ShopReceiptGUI {
    
    private final ShopManager shopManager;
    private final EconomyService economyService;
    private final PurchaseService purchaseService;
    private final Player player;
    private final ShopItem item;
    private final int quantity;
    private final double totalPrice;
    private final String transactionId;
    private final boolean success;
    private final UUID playerId;
    private final LocalDateTime transactionTime;
    
    public ShopReceiptGUI(ShopManager shopManager, EconomyService economyService,
                         PurchaseService purchaseService, Player player, 
                         ShopItem item, int quantity, double totalPrice, 
                         String transactionId, boolean success) {
        this.shopManager = shopManager;
        this.economyService = economyService;
        this.purchaseService = purchaseService;
        this.player = player;
        this.item = item;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.transactionId = transactionId;
        this.success = success;
        this.playerId = player.getUniqueId();
        this.transactionTime = LocalDateTime.now();
    }
    
    /**
     * Open the purchase receipt GUI
     */
    public void open() {
        String statusTitle = success ? "&a&l购买成功" : "&c&l购买失败";
        
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtil.color(statusTitle + " &f- &b交易回执")))
            .rows(6)
            .create();
        
        setupGUI(gui);
        gui.open(player);
    }
    
    /**
     * Setup the receipt GUI with transaction details
     */
    private void setupGUI(Gui gui) {
        // Transaction header
        ItemStack headerMaterial = success ? Material.GREEN_TERRACOTTA : Material.RED_TERRACOTTA;
        String statusColor = success ? "&a" : "&c";
        String statusText = success ? "交易成功" : "交易失败";
        
        gui.setItem(4, ItemBuilder.from(headerMaterial)
            .name(Component.text(MessageUtil.color(statusColor + "&l交易回执")))
            .lore(Component.text(MessageUtil.color("&f交易编号: &b" + transactionId)))
            .lore(Component.text(MessageUtil.color("&f交易状态: &7" + statusColor + statusText)))
            .lore(Component.text(MessageUtil.color("&f交易时间: &e" + transactionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.color(success ? "&f感谢您在此商店购物！" : "&f感谢您的浏览，请再次尝试。")))
            .build());
        
        // Item details
        gui.setItem(19, ItemBuilder.from(Material.BOOK)
            .name(Component.text(MessageUtil.color("&f&l商品详情")))
            .lore(Component.text(MessageUtil.color("&f商品名称: &e" + item.getDisplayName())))
            .lore(Component.text(MessageUtil.color("&f商品编号: &7" + item.getId())))
            .lore(Component.text(MessageUtil.color("&f购买数量: &e" + quantity)))
            .lore(Component.text(MessageUtil.color("&f单价: &e" + String.format("%.2f", item.getPrice()))))
            .build());
        
        // Price breakdown
        double basePrice = quantity * item.getPrice();
        double tax = totalPrice - basePrice;
        
        gui.setItem(21, ItemBuilder.from(Material.EMERALD)
            .name(Component.text(MessageUtil.color("&f&l价格明细")))
            .lore(Component.text(MessageUtil.color("&f商品小计: &e" + String.format("%.2f", basePrice))))
            .lore(Component.text(MessageUtil.color("&f税费: &e" + String.format("%.2f", tax))))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.color("&6&l总计: &e" + String.format("%.2f", totalPrice))))
            .build());
        
        // Account balance before and after
        double currentBalance = economyService.getBalance(player);
        gui.setItem(23, ItemBuilder.from(Material.COMPASS)
            .name(Component.text(MessageUtil.color("&b&l账户信息")))
            .lore(Component.text(MessageUtil.color("&f交易前余额: &e" + String.format("%.2f", currentBalance + totalPrice))))
            .lore(Component.text(MessageUtil.color("&f当前余额: &e" + String.format("%.2f", currentBalance))))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.color(success ? "&a账户状态良好" : "&c余额不足或交易错误")))
            .build());
        
        // Action buttons
        if (success) {
            // Transaction successful - show receipt actions
            gui.setItem(40, ItemBuilder.from(Material.GREEN_WOOL)
                .name(Component.text(MessageUtil.color("&a&l获取收据")))
                .lore(Component.text(MessageUtil.color("&f点击保存此交易回执")))
                .lore(Component.text(MessageUtil.color("&f订单号: &b" + transactionId)))
                .asGuiItem(event -> {
                    giveReceiptToPlayer();
                    player.sendMessage(MessageUtil.color("&a[购买系统] &f交易回执已发送至您的邮箱！"));
                }));
        } else {
            // Transaction failed - show retry actions
            gui.setItem(40, ItemBuilder.from(Material.YELLOW_WOOL)
                .name(Component.text(MessageUtil.color("&e&l重新购买")))
                .lore(Component.text(MessageUtil.color("&f点击返回商品界面")))
                .lore(Component.text(MessageUtil.color("&f您可以重新尝试购买")))
                .asGuiItem(event -> player.performCommand("shop items")));
        }
        
        // Common buttons
        gui.setItem(48, ItemBuilder.from(Material.CHEST)
            .name(Component.text(MessageUtil.color("&6&l继续购物")))
            .lore(Component.text(MessageUtil.color("&f点击查看更多商品")))
            .asGuiItem(event -> player.performCommand("shop")));
        
        gui.setItem(50, ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.color("&c&l关闭界面")))
            .lore(Component.text(MessageUtil.color("&f点击关闭此界面")))
            .asGuiItem(event -> gui.close(player)));
        
        // Fill empty slots
        fillEmptySlots(gui);
    }
    
    /**
     * Give receipt item to player
     */
    private void giveReceiptToPlayer() {
        ItemStack receipt = ItemBuilder.from(Material.PAPER)
            .name(Component.text(MessageUtil.color("&6&l交易回执")))
            .lore(Component.text(MessageUtil.color("&f订单号: &b" + transactionId)))
            .lore(Component.text(MessageUtil.color("&f商品: &e" + item.getDisplayName())))
            .lore(Component.text(MessageUtil.color("&f数量: &e" + quantity)))
            .lore(Component.text(MessageUtil.color("&f金额: &e" + String.format("%.2f", totalPrice))))
            .lore(Component.text(MessageUtil.color("&f时间: &e" + transactionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))))
            .lore(Component.text(MessageUtil.color("&a交易状态: 成功")))
            .asItemStack();
        
        player.getInventory().addItem(receipt);
    }
    
    /**
     * Fill empty slots with glass panes
     */
    private void fillEmptySlots(Gui gui) {
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
            .name(Component.text(""))
            .asItemStack();
        
        for (int slot = 0; slot < 54; slot++) {
            if (gui.getInventory().getItem(slot) == null) {
                gui.setItem(slot, new GuiItem(filler, event -> event.setCancelled(true)));
            }
        }
    }
}
