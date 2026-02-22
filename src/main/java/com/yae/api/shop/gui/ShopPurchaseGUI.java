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

import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified Shop Purchase Confirmation GUI
 * Shows purchase details and provides confirmation with 10-second cancellation window
 */
public class ShopPurchaseGUI {
    
    private final ShopManager shopManager;
    private final EconomyService economyService;
    private final PurchaseService purchaseService;
    private final Player player;
    private final ShopItem item;
    private final int quantity;
    private final double totalPrice;
    private final UUID playerId;
    private final LocalDateTime requestTime;
    private final String orderNumber;
    
    private static final int CANCELLATION_WINDOW_SECONDS = 10;
    private final AtomicInteger countdown = new AtomicInteger(CANCELLATION_WINDOW_SECONDS);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    public ShopPurchaseGUI(ShopManager shopManager, EconomyService economyService, 
                          PurchaseService purchaseService, Player player, 
                          ShopItem item, int quantity, double totalPrice) {
        this.shopManager = shopManager;
        this.economyService = economyService;
        this.purchaseService = purchaseService;
        this.player = player;
        this.item = item;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.playerId = player.getUniqueId();
        this.requestTime = LocalDateTime.now();
        this.orderNumber = generateOrderNumber();
    }
    
    /**
     * Open the purchase confirmation GUI with 10-second cancellation window
     */
    public void open() {
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtil.color("&6&l购买确认 &f- &e10秒取消窗口")))
            .rows(6)
            .create();
        
        setupGUI(gui);
        gui.open(player);
        
        startCancellationCountdown(gui);
    }
    
    /**
     * Setup the GUI with purchase details and confirmation/cancel buttons
     */
    private void setupGUI(Gui gui) {
        // Title information
        gui.setItem(4, ItemBuilder.from(Material.PAPER)
            .name(Component.text(MessageUtil.color("&6&l购买详情")))
            .lore(Component.text(MessageUtil.color("&f商品: &e" + item.getDisplayName())))
            .lore(Component.text(MessageUtil.color("&f数量: &e" + quantity)))
            .lore(Component.text(MessageUtil.color("&f总价: &e" + String.format("%.2f", totalPrice))))
            .lore(Component.text(MessageUtil.color("&f订单号: &b" + orderNumber)))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.color("&c&l倒计时: &e" + countdown.get() + " 秒")))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.color("&f操作: 点击下方按钮")))
            .build());
        
        // Confirm purchase button
        gui.setItem(21, ItemBuilder.from(Material.GREEN_WOOL)
            .name(Component.text(MessageUtil.color("&a&l确认购买")))
            .lore(Component.text(MessageUtil.color("&f确认购买 " + quantity + " 个")))
            .lore(Component.text(MessageUtil.color("&f总价: &e" + String.format("%.2f", totalPrice))))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.color("&7点击确认购买")))
            .asGuiItem(event -> {
                confirmPurchase();
                gui.close(player);
                scheduler.shutdown();
            }));
        
        // Cancel purchase button  
        gui.setItem(23, ItemBuilder.from(Material.RED_WOOL)
            .name(Component.text(MessageUtil.color("&c&l取消购买")))
            .lore(Component.text(MessageUtil.color("&f取消当前购买")))
            .lore(Component.text(MessageUtil.color("&f剩余时间: &c" + countdown.get() + " 秒")))
            .lore(Component.text(""))
            .lore(Component.text(MessageUtil.color("&7点击取消购买")))
            .asGuiItem(event -> {
                cancelPurchase();
                gui.close(player);
                scheduler.shutdown();
            }));
        
        // Player balance display
        double playerBalance = economyService.getBalance(player);
        gui.setItem(31, ItemBuilder.from(Material.COMPASS)
            .name(Component.text(MessageUtil.color("&b&l账户余额")))
            .lore(Component.text(MessageUtil.color("&f当前余额: &e" + String.format("%.2f", playerBalance))))
            .lore(Component.text(MessageUtil.color("&f购买后余额: &e" + String.format("%.2f", playerBalance - totalPrice))))
            .build());
        
        // Fill empty slots
        fillEmptySlots(gui);
    }
    
    /**
     * Start the cancellation countdown timer
     */
    private void startCancellationCountdown(Gui gui) {
        // Update GUI every second
        scheduler.scheduleAtFixedRate(() -> {
            int remaining = countdown.decrementAndGet();
            
            if (remaining <= 0) {
                // Auto-confirm purchase after countdown
                player.sendMessage(MessageUtil.color("&a[购买系统] &f购买确认已通过，交易完成！"));
                confirmPurchase();
                gui.close(player);
                scheduler.shutdown();
                return;
            }
            
            // Update countdown display
            updateCountdownDisplay(gui);
            
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Update the countdown display in the GUI
     */
    private void updateCountdownDisplay(Gui gui) {
        try {
            gui.updateTitle(Component.text(MessageUtil.color("&6&l购买确认 &f- &e" + countdown.get() + " 秒取消窗口")));
            
            // Update the cancel button's lore with new countdown
            gui.updateItem(23, ItemBuilder.from(Material.RED_WOOL)
                .name(Component.text(MessageUtil.color("&c&l取消购买")))
                .lore(Component.text(MessageUtil.color("&f取消当前购买")))
                .lore(Component.text(MessageUtil.color("&f剩余时间: &c" + countdown.get() + " 秒")))
                .lore(Component.text(""))
                .lore(Component.text(MessageUtil.color("&7点击取消购买")))
                .asGuiItem(event -> {
                    cancelPurchase();
                    gui.close(player);
                    scheduler.shutdown();
                }));
        } catch (Exception e) {
            // Player might have closed the GUI
            scheduler.shutdown();
        }
    }
    
    /**
     * Confirm the purchase
     */
    private void confirmPurchase() {
        scheduler.shutdown(); // Stop countdown
        
        player.sendMessage(MessageUtil.color("&a[购买系统] &f✅ 购买成功！"));
        player.sendMessage(MessageUtil.color("&f商品: &e" + item.getDisplayName()));
        player.sendMessage(MessageUtil.color("&f数量: &e" + quantity));
        player.sendMessage(MessageUtil.color("&f总价: &e" + String.format("%.2f", totalPrice)));
        player.sendMessage(MessageUtil.color("&f订单号: &b" + orderNumber));
        
        // Show receipt GUI
        new ShopReceiptGUI(shopManager, economyService, purchaseService, player, item, quantity, totalPrice, "SUCCESS_" + orderNumber, true).open();
    }
    
    /**
     * Cancel the purchase
     */
    private void cancelPurchase() {
        scheduler.shutdown();
        
        player.sendMessage(MessageUtil.color("&6[购买系统] &f⚠️ 购买已取消"));
        player.sendMessage(MessageUtil.color("&f商品: &e" + item.getDisplayName()));
        player.sendMessage(MessageUtil.color("&f数量: &e" + quantity));
        player.sendMessage(MessageUtil.color("&f订单号: &b" + orderNumber));
        player.sendMessage(MessageUtil.color("&f原因: 用户主动取消"));
        player.sendMessage(MessageUtil.color("&f时间: &e" + requestTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        
        // Show receipt for cancellation
        new ShopReceiptGUI(shopManager, economyService, purchaseService, player, item, quantity, totalPrice, "CANCELLED_" + orderNumber, false).open();
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
    
    /**
     * Generate unique order number
     */
    private String generateOrderNumber() {
        return System.currentTimeMillis() + "-" + playerId.toString().substring(0, 8);
    }
    
    public void cleanup() {
        scheduler.shutdown();
    }
}
