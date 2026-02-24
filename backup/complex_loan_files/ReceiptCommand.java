package com.yae.api.shop;

// import com.yae.YetAnotherEconomy;
import com.yae.api.core.command.YAECommand;
import com.yae.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 回执管理命令
 * 处理交易回执的查看、管理等操作
 */
public class ReceiptCommand extends YAECommand {
    
    private final ReceiptService receiptService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    
    public ReceiptCommand(@NotNull com.yae.YetAnotherEconomy plugin) {
        super(plugin, "receipt", "回执管理命令", "yae.command.receipt", 
              Arrays.asList("receipts", "rcp"));
        this.receiptService = new ReceiptService(plugin);
    }
    
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        // 检查服务是否启用
        if (!receiptService.isReceiptServiceEnabled()) {
            sender.sendMessage(MessageUtils.error("回执服务未启用"));
            return true;
        }
        
        if (args.length == 0) {
            return showHelp(sender);
        }
        
        switch (args[0].toLowerCase()) {
            case "list":
                return handleList(sender, Arrays.copyOfRange(args, 1, args.length));
            case "view":
                return handleView(sender, Arrays.copyOfRange(args, 1, args.length));
            case "clear":
                return handleClear(sender, Arrays.copyOfRange(args, 1, args.length));
            case "search":
                return handleSearch(sender, Arrays.copyOfRange(args, 1, args.length));
            case "help":
            case "?":
            default:
                return showHelp(sender);
        }
    }
    
    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, 
                                            @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("list", "view", "clear", "search", "help");
        }
        
        switch (args[0].toLowerCase()) {
            case "list":
                if (args.length == 2) {
                    List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                    players.add("0");
                    return players;
                } else if (args.length == 3) {
                    return Arrays.asList("10", "20", "50");
                }
                break;
                
            case "view":
                if (args.length == 2) {
                    return Arrays.asList("<回执ID>");
                }
                break;
                
            case "search":
                if (args.length == 2) {
                    return Arrays.asList("<关键词>");
                }
                break;
                
            case "clear":
                if (args.length == 2) {
                    return Arrays.asList("confirm", "cancel");
                }
                break;
        }
        
        return Collections.emptyList();
    }
    
    private boolean showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(MessageUtils.info("&6===== &e回执管理 &6====="));
        sender.sendMessage(MessageUtils.info("&e/yae receipt list [玩家] [数量] &f- 查看最近的回执"));
        sender.sendMessage(MessageUtils.info("&e/yae receipt view <回执ID> &f- 查看指定回执详情"));
        sender.sendMessage(MessageUtils.info("&e/yae receipt search <关键词> &f- 搜索回执"));
        sender.sendMessage(MessageUtils.info("&e/yae receipt clear &f- 清空旧回执"));
        sender.sendMessage(MessageUtils.info("&6==============="));
        return true;
    }
    
    private boolean handleList(@NotNull CommandSender sender, @NotNull String[] args) {
        UUID targetPlayerId;
        int limit = 10;
        
        // 解析参数
        if (args.length > 0) {
            // 检查是否是数字（数量）
            try {
                limit = Integer.parseInt(args[0]);
                if (limit <= 0 || limit > 100) {
                    limit = 10;
                }
                
                // 如果只有一个参数且是数字，使用发送者自己
                if (args.length == 1) {
                    targetPlayerId = getTargetPlayerId(sender, null);
                } else {
                    targetPlayerId = getTargetPlayerId(sender, args[1]);
                }
            } catch (NumberFormatException e) {
                // 第一个参数不是数字，当作玩家名处理
                targetPlayerId = getTargetPlayerId(sender, args[0]);
                
                if (args.length > 1) {
                    limit = parseIntOrDefault(args[1], 10);
                }
            }
        } else {
            targetPlayerId = getTargetPlayerId(sender, null);
        }
        
        if (targetPlayerId == null) {
            return true;
        }
        
        // 获取玩家名称用于显示
        String playerName = getPlayerName(targetPlayerId);
        
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }
        
        final int finalLimit = limit;
        CompletableFuture<List<TransactionReceipt>> future = receiptService.getPlayerReceipts(targetPlayerId, finalLimit);
        
        future.thenAccept(receipts -> {
            if (receipts.isEmpty()) {
                sender.sendMessage(MessageUtils.info("&c没有找到回执记录"));
                return;
            }
            
            sender.sendMessage(MessageUtils.info("&6===== &e" + playerName + " 的回收记录 &6====="));
            
            int index = 1;
            for (TransactionReceipt receipt : receipts) {
                Component receiptLine = createReceiptListLine(receipt, index);
                sender.sendMessage(receiptLine);
                index++;
            }
            
            if (receipts.size() == finalLimit) {
                Component moreInfo = Component.text("点击查看更多")
                    .color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/yae receipt list " + playerName + " " + (finalLimit + 10)))
                    .hoverEvent(HoverEvent.showText(Component.text("点击查看更多回执")));
                // TODO: 更完善的交互信息
                sender.sendMessage(MessageUtils.color("&7点击查看更多回执"));
            }
            
        }).exceptionally(throwable -> {
            sender.sendMessage(MessageUtils.error("获取回执失败: " + throwable.getMessage()));
            return null;
        });
        
        return true;
    }
    
    private boolean handleView(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtils.error("请输入回执ID"));
            return true;
        }
        
        String receiptIdStr = args[0];
        UUID receiptId;
        
        try {
            receiptId = UUID.fromString(receiptIdStr);
        } catch (IllegalArgumentException e) {
            // 可能是简化的ID格式（前8位）
            if (receiptIdStr.length() == 8) {
                sender.sendMessage(MessageUtils.error("简化ID功能待实现"));
                return true;
            } else {
                sender.sendMessage(MessageUtils.error("无效的回执ID格式"));
                return true;
            }
        }
        
        receiptService.getReceiptById(receiptId).thenAccept(optionalReceipt -> {
            if (optionalReceipt.isEmpty()) {
                sender.sendMessage(MessageUtils.error("未找到指定的回执"));
                return;
            }
            
            TransactionReceipt receipt = optionalReceipt.get();
            
            // 权限检查
            if (!sender.hasPermission("yae.admin.*") && !sender.hasPermission("yae.command.receipt.admin")) {
                if (sender instanceof Player player) {
                    if (!receipt.getPlayerId().equals(player.getUniqueId())) {
                        sender.sendMessage(MessageUtils.error("您只能查看自己的回执"));
                        return;
                    }
                }
            }
            
            OfflinePlayer player = Bukkit.getOfflinePlayer(receipt.getPlayerId());
            Component receiptDisplay = receiptService.formatReceiptForDisplay(receipt, player);
            sender.sendMessage(receiptDisplay);
            
        }).exceptionally(throwable -> {
            sender.sendMessage(MessageUtils.error("查看回执失败: " + throwable.getMessage()));
            return null;
        });
        
        return true;
    }
    
    private boolean handleClear(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("yae.admin.*") && !sender.hasPermission("yae.command.receipt.admin")) {
            sender.sendMessage(MessageUtils.error("您没有权限清理回执"));
            return true;
        }
        
        if (args.length == 0 || !args[0].equals("confirm")) {
            sender.sendMessage(MessageUtils.warning("&e确认清理旧回执?"));
            sender.sendMessage(MessageUtils.info("&e执行 &6/yae receipt clear confirm &e来确认"));
            return true;
        }
        
        int retentionDays = receiptService.getReceiptRetentionDays();
        sender.sendMessage(MessageUtils.info("&e正在清理 " + retentionDays + " 天前的回执..."));
        
        receiptService.deleteOldReceipts(retentionDays).thenAccept(count -> {
            sender.sendMessage(MessageUtils.success("清除了 &7" + count + " &a个旧回执"));
        }).exceptionally(throwable -> {
            sender.sendMessage(MessageUtils.error("清理失败: " + throwable.getMessage()));
            return null;
        });
        
        return true;
    }
    
    private boolean handleSearch(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtils.error("请输入搜索关键词"));
            return true;
        }
        
        String keyword = String.join(" ", args);
        UUID targetPlayerId = getTargetPlayerId(sender, null);
        
        if (targetPlayerId == null) {
            return true;
        }
        
        // 搜索功能待实现数据库支持
        sender.sendMessage(MessageUtils.info("&e搜索功能开发中..."));
        sender.sendMessage(MessageUtils.info("&7关键词: &f" + keyword));
        
        return true;
    }
    
    @Nullable
    private UUID getTargetPlayerId(@NotNull CommandSender sender, @Nullable String playerName) {
        if (playerName == null) {
            // 使用发送者自己
            if (sender instanceof Player player) {
                return player.getUniqueId();
            } else {
                sender.sendMessage(MessageUtils.error("控制台用户必须指定玩家名称"));
                return null;
            }
        } else {
            // 检查权限
            if (!sender.hasPermission("yae.admin.*") && !sender.hasPermission("yae.command.receipt.others")) {
                sender.sendMessage(MessageUtils.error("您没有权限查看其他玩家的回执"));
                return null;
            }
            
            // 查找玩家
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
            if (targetPlayer == null || targetPlayer.getUniqueId() == null) {
                sender.sendMessage(MessageUtils.error("未找到玩家: " + playerName));
                return null;
            }
            
            return targetPlayer.getUniqueId();
        }
    }
    
    @NotNull
    private String getPlayerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() != null ? player.getName() : "未知玩家";
    }
    
    @NotNull
    private Component createReceiptListLine(TransactionReceipt receipt, int index) {
        String actionDisplay = getActionDisplayName(receipt.getAction());
        String amountStr = String.format("%.2f", Math.abs(receipt.getAmount()));
        String amountColor = receipt.getAmount() >= 0 ? "<green>+" : "<red>-";
        String timeStr = receipt.getTimestamp().format(dateFormatter);
        
        Component line = Component.text("[" + index + "] ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(timeStr + " ").color(NamedTextColor.YELLOW))
            .append(Component.text(actionDisplay).color(NamedTextColor.WHITE))
            .append(Component.text(" "))
            .append(Component.text(amountColor + amountStr).color(receipt.getAmount() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
            .clickEvent(ClickEvent.runCommand("/yae receipt view " + receipt.getReceiptId()))
            .hoverEvent(HoverEvent.showText(Component.text("点击查看详情\nID: " + receipt.getReceiptId())));
        
        return line;
    }
    
    @NotNull
    private String getActionDisplayName(String action) {
        switch (action.toUpperCase()) {
            case "SHOP_BUY":
                return "购买";
            case "SHOP_SELL":
                return "销售";
            case "ADMIN_GIVE":
                return "系统给予";
            case "ADMIN_TAKE":
                return "系统扣除";
            case "PAY":
                return "转账";
            case "RECEIVE":
                return "收款";
            default:
                return action;
        }
    }
}
