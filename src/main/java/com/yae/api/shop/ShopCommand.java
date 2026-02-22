package com.yae.api.shop;

import com.yae.api.core.command.YAECommand;
import com.yae.api.core.YAECore;
import com.yae.api.core.ServiceType;
import com.yae.api.services.EconomyService;
import com.yae.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Minimal shop command implementation for YAE basic functionality.
 * Provides basic shop listing without GUI components.
 */
public class ShopCommand extends YAECommand {
    
    private final ShopManager shopManager;
    private final EconomyService economyService;
    
    public ShopCommand(@NotNull YAECore plugin) {
        super(plugin, "shop", "商店系统命令", "yae.shop",
             Arrays.asList("shop", "商店"));
        
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
            player.sendMessage(MessageUtil.color("&6[YAE] 商店系统"));
            player.sendMessage(MessageUtil.color("&7使用 &e/yae shop list &7来查看商品"));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "list":
                return handleListCommand(player);
            default:
                player.sendMessage(MessageUtil.color("&c[YAE] 未知命令: ") + subCommand);
                return showUsage(player);
        }
    }
    
    private boolean handleListCommand(Player player) {
        player.sendMessage(MessageUtil.color("&6[YAE] 商品列表:"));
        
        for (var category : shopManager.getCategories()) {
            player.sendMessage(MessageUtil.color("&e" + category.getDisplayName() + ":"));
            for (var item : category.getEnabledItems()) {
                player.sendMessage(MessageUtil.color("&7- " + item.getDisplayName() + 
                    " &a" + economyService.formatCurrency(item.getPrice()) + "&f | &7" + item.getStock() + "库存"));
            }
        }
        return true;
    }
    
    private boolean showUsage(Player player) {
        player.sendMessage(MessageUtil.color("&6[YAE] 商店命令用法:"));
        player.sendMessage(MessageUtil.color("&e/yae shop list &7- 查看所有商品"));
        return true;
    }
}
