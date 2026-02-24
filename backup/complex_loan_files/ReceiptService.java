package com.yae.api.shop;

// import com.yae.YetAnotherEconomy;
import com.yae.api.database.DatabaseService;
import com.yae.api.core.config.Configuration;
import com.yae.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 回执服务类 - 管理交易回执
 */
public class ReceiptService {
    
    private final com.yae.YetAnotherEconomy plugin;
    private final DatabaseService databaseService;
    private final Configuration config;
    
    public ReceiptService(com.yae.YetAnotherEconomy plugin) {
        this.plugin = plugin;
        this.databaseService = (DatabaseService) plugin.getService(com.yae.api.core.ServiceType.DATABASE);
        this.config = plugin.getMainConfiguration();
    }
    
    /**
     * 创建交易回执
     */
    public CompletableFuture<TransactionReceipt> createReceipt(UUID playerId, String action, double amount, String details, String pluginSource, String target) {
        return CompletableFuture.supplyAsync(() -> {
            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setReceiptId(UUID.randomUUID());
            receipt.setPlayerId(playerId);
            receipt.setAction(action);
            receipt.setAmount(amount);
            receipt.setDetails(details);
            receipt.setPluginSource(pluginSource);
            receipt.setTarget(target);
            receipt.setTimestamp(LocalDateTime.now());
            receipt.setCreatedAt(LocalDateTime.now());
            receipt.setUpdatedAt(LocalDateTime.now());
            return receipt;
            // TODO: 保存到数据库 - databaseService.saveReceipt(receipt);
        });
    }
    
    /**
     * 创建商店购买回执
     */
    public CompletableFuture<TransactionReceipt> createShopReceipt(UUID playerId, String itemName, double price, int quantity, String seller) {
        String details = String.format("购买 %dx %s, 卖家: %s", quantity, itemName, seller);
        return createReceipt(playerId, "SHOP_BUY", price, details, "YAEconomy", seller);
    }
    
    /**
     * 创建商店销售回执
     */
    public CompletableFuture<TransactionReceipt> createShopSellReceipt(UUID playerId, String itemName, double price, int quantity, String buyer) {
        String details = String.format("销售 %dx %s, 买家: %s", quantity, itemName, buyer);
        return createReceipt(playerId, "SHOP_SELL", price, details, "YAEconomy", buyer);
    }
    
    /**
     * 获取玩家回执
     */
    public CompletableFuture<List<TransactionReceipt>> getPlayerReceipts(UUID playerId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: 实现数据库查询 - return databaseService.getPlayerReceipts(playerId, limit);
            return new ArrayList<TransactionReceipt>();
        });
    }
    
    /**
     * 获取玩家最近的交易回执
     */
    public CompletableFuture<List<TransactionReceipt>> getRecentPlayerReceipts(UUID playerId, int days) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: 实现数据库查询 - return databaseService.getPlayerReceiptsSince(playerId, LocalDateTime.now().minusDays(days));
            return new ArrayList<TransactionReceipt>();
        });
    }
    
    /**
     * 按回执ID获取回执
     */
    public CompletableFuture<Optional<TransactionReceipt>> getReceiptById(UUID receiptId) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: 实现数据库查询 - return Optional.ofNullable(databaseService.getReceiptById(receiptId));
            return Optional.empty();
        });
    }
    
    /**
     * 删除过期回执
     */
    public CompletableFuture<Integer> deleteOldReceipts(int daysToKeep) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: 实现数据库清理 - LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
            // return databaseService.deleteReceiptsOlderThan(cutoffDate);
            return 0;
        });
    }
    
    /**
     * 生成回执显示组件
     */
    public Component formatReceiptForDisplay(TransactionReceipt receipt, OfflinePlayer player) {
        MiniMessage mm = MiniMessage.miniMessage();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        StringBuilder sb = new StringBuilder();
        sb.append("<dark_gray>═══════════════</dark_gray>\n");
        sb.append("<gold><bold>交易回执</bold></gold>\n");
        sb.append("<dark_gray>═══════════════</dark_gray>\n\n");
        
        // 基本信息
        sb.append("<gray>回执ID: <white>").append(receipt.getReceiptId().toString().substring(0, 8)).append("</white></gray>\n");
        sb.append("<gray>时间: <white>").append(receipt.getTimestamp().format(formatter)).append("</white></gray>\n");
        sb.append("<gray>操作: <yellow>").append(getActionDisplayName(receipt.getAction())).append("</yellow></gray>\n");
        
        // 金额信息
        String amountColor = receipt.getAmount() >= 0 ? "<green>+" : "<red>";
        sb.append("<gray>金额: ").append(amountColor).append(String.format("%.2f", receipt.getAmount())).append("</amount></gray>\n");
        
        // 详情
        sb.append("<gray>详情: <white>").append(receipt.getDetails()).append("</white></gray>\n");
        
        if (receipt.getTarget() != null) {
            sb.append("<gray>对象: <white>").append(receipt.getTarget()).append("</white></gray>\n");
        }
        
        sb.append("\n<dark_gray>═══════════════</dark_gray>");
        
        return mm.deserialize(sb.toString());
    }
    
    /**
     * 获取操作显示名称
     */
    private String getActionDisplayName(String action) {
        switch (action.toUpperCase()) {
            case "SHOP_BUY":
                return "商店购买";
            case "SHOP_SELL":
                return "商店销售";
            case "ADMIN_GIVE":
                return "管理员给予";
            case "ADMIN_TAKE":
                return "管理员扣除";
            case "PAY":
                return "转账";
            case "RECEIVE":
                return "收款";
            default:
                return action;
        }
    }
    
    /**
     * 发送回执给玩家
     */
    public void sendReceiptToPlayer(OfflinePlayer player, TransactionReceipt receipt) {
        Component receiptComponent = formatReceiptForDisplay(receipt, player);
        if (player.getPlayer() != null) {
            player.getPlayer().sendMessage(receiptComponent);
        }
    }
    
    /**
     * 回执服务是否启用
     */
    public boolean isReceiptServiceEnabled() {
        // 默认启用
        return true;
    }
    
    /**
     * 获取回执保存天数
     */
    public int getReceiptRetentionDays() {
        // 默认30天
        return 30;
    }
}
