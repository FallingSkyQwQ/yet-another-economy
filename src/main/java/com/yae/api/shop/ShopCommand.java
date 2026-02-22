package com.yae.api.shop;

import com.yae.api.core.command.YAECommand;
import com.yae.api.core.YAECore;
import com.yae.api.core.ServiceType;
import com.yae.api.services.EconomyService;
import com.yae.api.shop.gui.*;
import com.yae.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop command implementation for YAE shop system.
 * Handles shop-related commands and operations.
 */
public class ShopCommand extends YAECommand {
    
    private final ShopManager shopManager;
    private final EconomyService economyService;
    
    public ShopCommand(@NotNull YAECore plugin) {
        super(plugin, "shop", "商店系统命令", "yae.shop",
             List.of("shop", "商店"));
        
        this.shopManager = plugin.getService(ServiceType.SHOP);
        this.economyService = plugin.getService(ServiceType.ECONOMY);
    }
    
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color("&c[YAE] 该命令只能由玩家执行"));
            return true;
        }
        
        if (args.length == 0) {
            // Open main shop GUI
            openShopMain(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "open":
            case "browse":
                // Open main shop GUI
                openShopMain(player);
                break;
                
            case "buy":
                // Direct buy command
                handleDirectBuyCommand(player, args);
                break;
                
            case "search":
                // Search for items
                handleSearchCommand(player, args);
                break;
                
            case "limit":
                // Check purchase limits
                handleLimitCommand(player, args);
                break;
                
            case "list":
            case "items":
                // List available items
                handleListCommand(player, args);
                break;
                
            case "help":
            case "?":
                // Show help
                showHelp(player);
                break;
                
            default:
                player.sendMessage(MessageUtil.color("&c[YAE] 未知子命令: &7" + subCommand));
                showHelp(player);
                break;
        }
        
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String alias, 
                                               @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        
        List<String> suggestions = new ArrayList<>();
        
        if (args.length == 1) {
            suggestions.addAll(List.of("open", "buy", "search", "limit", "list", "help"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "buy":
                    // Suggest item names
                    if (shopManager != null) {
                        List<String> itemNames = new ArrayList<>();
                        for (var category : shopManager.getCategories()) {
                            for (var item : category.getEnabledItems()) {
                                itemNames.add(item.getId().toLowerCase());
                            }
                        }
                        suggestions.addAll(itemNames);
                    }
                    break;
                    
                case "search":
                    suggestions.add("<关键词>");
                    break;
                    
                case "limit":
                    suggestions.addAll(List.of("daily", "player", "all"));
                    break;
                    
                case "list":
                    suggestions.addAll(List.of("all", "<分类>"));
                    if (shopManager != null) {
                        for (var category : shopManager.getCategories()) {
                            suggestions.add(category.getId().toLowerCase());
                        }
                    }
                    break;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("buy")) {
            suggestions.add("<数量>");
        }
        
        return suggestions;
    }
    
    /**
     * Open the main shop GUI.
     */
    private void openShopMain(Player player) {
        if (shopManager == null || !shopManager.isEnabled()) {
            player.sendMessage(MessageUtil.color("&c[YAE] 商店系统暂时不可用"));
            return;
        }
        
        if (economyService == null || !economyService.isEnabled()) {
            player.sendMessage(MessageUtil.color("&c[YAE] 经济系统暂时不可用"));
            return;
        }
        
        new ShopCategoriesGUI(shopManager, economyService, player).open();
    }
    
    /**
     * Handle direct buy command.
     */
    private void handleDirectBuyCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.color("&c[YAE] 用法: /yae shop buy <商品ID> [数量]"));
            return;
        }
        
        String itemId = args[1].toUpperCase();
        int quantity = 1;
        
        if (args.length >= 3) {
            try {
                quantity = Integer.parseInt(args[2]);
                if (quantity <= 0) {
                    player.sendMessage(MessageUtil.color("&c[YAE] 数量必须大于0"));
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(MessageUtil.color("&c[YAE] 无效的数量: &7" + args[2]));
                return;
            }
        }
        
        var item = shopManager.getItem(itemId);
        if (item == null) {
            player.sendMessage(MessageUtil.color("&c[YAE] 商品未找到: &7" + itemId));
            return;
        }
        
        // Check if item is enabled and available
        if (!item.isEnabled()) {
            player.sendMessage(MessageUtil.color("&c[YAE] 该商品暂时不可用"));
            return;
        }
        
        int availableQuantity = shopManager.getAvailableQuantity(itemId, playerId);
        if (availableQuantity <= 0) {
            player.sendMessage(MessageUtil.color("&c[YAE] 该商品暂时无法购买"));
            return;
        }
        
        if (quantity > availableQuantity) {
            player.sendMessage(MessageUtil.color("&c[YAE] 最多可购买: &e" + availableQuantity));
            return;
        }
        
        // Calculate purchase and open confirmation
        var purchaseService = plugin.getService(ServiceType.SHOP);
        if (purchaseService instanceof PurchaseService) {
            var purchaseCalc = ((PurchaseService) purchaseService).calculatePurchase(itemId, quantity, playerId);
            
            if (purchaseCalc.isSuccessful()) {
                var pendingPurchase = ((PurchaseService) purchaseService).createPendingPurchase(itemId, quantity, playerId);
                if (pendingPurchase != null) {
                    new ShopPurchaseGUI(plugin, shopManager, economyService, (PurchaseService) purchaseService, player, pendingPurchase).open();
                } else {
                    player.sendMessage(MessageUtil.color("&c[YAE] 创建购买请求失败"));
                }
            } else {
                player.sendMessage(MessageUtil.color("&c[YAE] " + purchaseCalc.getMessage()));
            }
        } else {
            player.sendMessage(MessageUtil.color("&c[YAE] 购买系统暂时不可用"));
        }
    }
    
    /**
     * Handle search command.
     */
    private void handleSearchCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.color("&c[YAE] 用法: /yae shop search <关键词>"));
            return;
        }
        
        // Build search query from remaining arguments
        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                queryBuilder.append(" ");
            }
            queryBuilder.append(args[i]);
        }
        
        String query = queryBuilder.toString();
        var results = shopManager.searchItems(query);
        
        if (results.isEmpty()) {
            player.sendMessage(MessageUtil.color("&6[YAE] &f未找到包含 \"&e" + query + "&f\" 的商品"));
            return;
        }
        
        player.sendMessage(MessageUtil.color("&6[YAE] &f搜索结果 (\"&e" + query + "&f\"):"));
        for (var item : results) {
            String info = String.format("&7- &e%s &f(%s) &7价格: %.2f",
                                      item.getDisplayName(),
                                      item.getId(),
                                      item.getPrice());
            player.sendMessage(MessageUtil.color(info));
        }
        player.sendMessage(MessageUtil.color("&6[YAE] &f共找到 &e" + results.size() + " &f个商品"));
    }
    
    /**
     * Handle limit command.
     */
    private void handleLimitCommand(Player player, String[] args) {
        String limitType = args.length >= 2 ? args[1].toLowerCase() : "all";
        UUID playerId = player.getUniqueId();
        
        player.sendMessage(MessageUtil.color("&6[YAE] &f购买限制查询:"));
        
        switch (limitType) {
            case "daily":
                player.sendMessage(MessageUtil.color("&f每日限购情况:"));
                for (var category : shopManager.getCategories()) {
                    for (var item : category.getEnabledItems()) {
                        if (item.hasDailyLimit()) {
                            if (shopManager.isDailyLimitReached(item.getId(), playerId)) {
                                player.sendMessage(MessageUtil.color("&7- &c" + item.getDisplayName() + " &7(已达到每日限制)"));
                            } else {
                                int remaining = item.getDailyLimit() - 0; // This would need proper implementation
                                player.sendMessage(MessageUtil.color("&7- " + item.getDisplayName() + " &7(剩余: " + remaining + ")"));
                            }
                        }
                    }
                }
                break;
                
            case "player":
                player.sendMessage(MessageUtil.color("&f玩家限购情况:"));
                for (var category : shopManager.getCategories()) {
                    for (var item : category.getEnabledItems()) {
                        if (item.hasPlayerLimit()) {
                            if (shopManager.isPlayerLimitReached(item.getId(), playerId)) {
                                player.sendMessage(MessageUtil.color("&7- &c" + item.getDisplayName() + " &7(已达到永久限制)"));
                            } else {
                                int remaining = item.getPlayerLimit() - 0; // This would need proper implementation
                                player.sendMessage(MessageUtil.color("&7- " + item.getDisplayName() + " &7(剩余: " + remaining + ")"));
                            }
                        }
                    }
                }
                break;
                
            case "all":
            default:
                handleLimitCommand(player, new String[]{"limit", "daily"});
                player.sendMessage("");
                handleLimitCommand(player, new String[]{"limit", "player"});
                break;
        }
    }
    
    /**
     * Handle list command.
     */
    private void handleListCommand(Player player, String[] args) {
        String listType = args.length >= 2 ? args[1].toLowerCase() : "all";
        
        player.sendMessage(MessageUtil.color("&6[YAE] &f商品列表:"));
        
        if (listType.equals("all")) {
            for (var category : shopManager.getCategories()) {
                player.sendMessage("");
                player.sendMessage(MessageUtil.color("&6" + category.getDisplayName() + " &f(&e" + category.getEnabledItemCount() + " 件商品&f):"));
                
                for (var item : category.getEnabledItems()) {
                    String itemInfo = String.format("&7- &e%s &7(%s) &f价格: %.2f",
                                                  item.getDisplayName(),
                                                  item.getId(),
                                                  item.getPrice());
                    if (item.hasDailyLimit()) {
                        itemInfo += String.format(" &c[每日限购: %d]", item.getDailyLimit());
                    }
                    if (item.hasPlayerLimit()) {
                        itemInfo += String.format(" &c[永久限购: %d]", item.getPlayerLimit());
                    }
                    player.sendMessage(MessageUtil.color(itemInfo));
                }
            }
        } else {
            // List specific category
            var category = shopManager.getCategory(listType);
            if (category != null) {
                player.sendMessage(MessageUtil.color("&6" + category.getDisplayName() + " &f(&e" + category.getEnabledItemCount() + " 件商品&f):"));
                
                for (var item : category.getEnabledItems()) {
                    String itemInfo = String.format("&7- &e%s &7(%s) &f价格: %.2f",
                                                  item.getDisplayName(),
                                                  item.getId(),
                                                  item.getPrice());
                    if (item.hasDailyLimit()) {
                        itemInfo += String.format(" &c[每日限购: %d]", item.getDailyLimit());
                    }
                    if (item.hasPlayerLimit()) {
                        itemInfo += String.format(" &c[永久限购: %d]", item.getPlayerLimit());
                    }
                    player.sendMessage(MessageUtil.color(itemInfo));
                }
            } else {
                player.sendMessage(MessageUtil.color("&c[YAE] 分类未找到: &7" + listType));
            }
        }
        
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6[YAE] &f使用 &e/yae shop search <关键词> &f搜索特定商品"));
    }
    
    /**
     * Show help information.
     */
    private void showHelp(Player player) {
        player.sendMessage(MessageUtil.color("&6&l=== YAE商店系统帮助 ==="));
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6/yae shop open"));
        player.sendMessage(MessageUtil.color("&f   打开商店主界面"));
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6/yae shop buy <商品ID> [数量]"));
        player.sendMessage(MessageUtil.color("&f   直接购买指定商品"));
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6/yae shop search <关键词>"));
        player.sendMessage(MessageUtil.color("&f   搜索商品"));
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6/yae shop limit [daily|player|all]"));
        player.sendMessage(MessageUtil.color("&f   查看购买限制"));
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6/yae shop list [分类|all]"));
        player.sendMessage(MessageUtil.color("&f   列出商品列表"));
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6/yae shop help"));
        player.sendMessage(MessageUtil.color("&f   显示此帮助信息"));
        player.sendMessage("");
        player.sendMessage(MessageUtil.color("&6[YAE] &f购买时请确保背包有足够空间"));
        player.sendMessage(MessageUtil.color("&6[YAE] &f所有购买都有10秒确认窗口"));
    }
}
