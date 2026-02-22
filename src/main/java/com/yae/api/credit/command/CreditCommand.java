package com.yae.api.credit.command;

import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.core.command.YAECommand;
import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.CreditService;
import com.yae.utils.MessageUtils;
import com.yae.utils.Logging;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Credit score command implementation
 * Handles /yae credit commands for credit score management
 */
public class CreditCommand extends YAECommand {
    
    private final CreditService creditService;
    
    public CreditCommand(@NotNull YAECore plugin, @NotNull CreditService creditService) {
        super(plugin, "credit", "信用评分相关命令", "yae.command.credit", 
              Arrays.asList("score", "creditscore"));
        this.creditService = creditService;
    }
    
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!checkExecutionPermission(sender)) {
            return false;
        }
        
        if (args.length == 0) {
            // Show player's own credit score
            return showOwnCredit(sender);
        }
        
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "score":
            case "view":
                return handleScoreCommand(sender, args);
            case "grade":
                return handleGradeCommand(sender, args);
            case "check":
                return handleCheckCommand(sender, args);
            case "refresh":
                return handleRefreshCommand(sender, args);
            case "history":
                return handleHistoryCommand(sender, args);
            case "help":
                return showHelp(sender);
            default:
                sender.sendMessage(MessageUtils.error("未知子命令: " + subCommand));
                sender.sendMessage(MessageUtils.info("使用 /yae credit help 查看帮助"));
                return false;
        }
    }
    
    private boolean handleScoreCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            // Show sender's own score
            Player player = getPlayerOrNull(sender);
            if (player == null) {
                sender.sendMessage(MessageUtils.error("请指定玩家名称或使用 /yae credit <score|view> <player>"));
                return false;
            }
            return showCreditScore(sender, player.getName());
        } else if (args.length == 2) {
            // Show specified player's score
            return showCreditScore(sender, args[1]);
        } else {
            sender.sendMessage(MessageUtils.error("用法: /yae credit score [player]"));
            return false;
        }
    }
    
    private boolean handleGradeCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = getPlayerOrNull(sender);
        if (player == null) {
            sender.sendMessage(MessageUtils.error("此命令只能由玩家使用"));
            return false;
        }
        
        try {
            CreditGrade grade = creditService.getCreditGrade(player.getUniqueId());
            sender.sendMessage(MessageUtils.info("您的信用等级信息:"));
            sender.sendMessage(MessageUtils.color("&7等级: " + grade.getDisplayName()));
            sender.sendMessage(MessageUtils.color("&7分数范围: &f" + grade.getMinScore() + " - " + grade.getMaxScore()));
            sender.sendMessage(MessageUtils.color("&7描述: &f" + grade.getChineseName()));
            sender.sendMessage(MessageUtils.color("&7基础年利率: &6" + String.format("%.2f%%", grade.getBaseInterestRate() * 100)));
            sender.sendMessage(MessageUtils.color("&7最高信用额度: &6💰" + String.format("%,.0f", grade.getMaxCreditLimit())));
            
            if (sender.hasPermission("yae.admin.credit")) {
                sender.sendMessage(MessageUtils.color("&e管理员信息:"));
                sender.sendMessage(MessageUtils.color("&7• 是否符合信用贷款: " + (grade.qualifiesForLoan(com.yae.api.credit.LoanType.CREDIT) ? "&a是" : "&c否")));
                sender.sendMessage(MessageUtils.color("&7• 是否符合抵押贷款: " + (grade.qualifiesForLoan(com.yae.api.credit.LoanType.MORTGAGE) ? "&a是" : "&c否")));
                sender.sendMessage(MessageUtils.color("&7• 是否符合商业贷款: " + (grade.qualifiesForLoan(com.yae.api.credit.LoanType.BUSINESS) ? "&a是" : "&c否")));
            }
        } catch (Exception ex) {
            sender.sendMessage(MessageUtils.error("获取信用等级失败: " + ex.getMessage()));
        }
        
        return true;
    }
    
    private boolean handleCheckCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.error("用法: /yae credit check <player>"));
            return false;
        }
        
        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(MessageUtils.error("找不到玩家: " + playerName));
            return false;
        }
        
        showCreditScore(sender, target.getName());
        return true;
    }
    
    private boolean handleRefreshCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("yae.admin.credit.refresh")) {
            sender.sendMessage(MessageUtils.error("您没有刷新信用评分的权限"));
            return false;
        }
        
        if (args.length == 1) {
            // Refresh sender's own credit score
            Player player = getPlayerOrNull(sender);
            if (player == null) {
                sender.sendMessage(MessageUtils.error("请指定玩家或使用 /yae credit refresh <player>"));
                return false;
            }
            return refreshCreditScore(sender, player.getUniqueId(), player.getName());
        } else if (args.length == 2) {
            // Refresh specified player's credit score
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtils.error("找不到玩家: " + args[1]));
                return false;
            }
            return refreshCreditScore(sender, target.getUniqueId(), target.getName());
        } else {
            sender.sendMessage(MessageUtils.error("用法: /yae credit refresh [player]"));
            return false;
        }
    }
    
    private boolean handleHistoryCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("yae.admin.credit.history")) {
            sender.sendMessage(MessageUtils.error("您没有查看信用历史的权限"));
            return false;
        }
        
        if (args.length == 1) {
            // Show sender's own history
            Player player = getPlayerOrNull(sender);
            if (player == null) {
                sender.sendMessage(MessageUtils.error("请指定玩家或使用 /yae credit history <player>"));
                return false;
            }
            return showCreditHistory(sender, player.getUniqueId(), player.getName());
        } else if (args.length == 2) {
            // Show specified player's history
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtils.error("找不到玩家: " + args[1]));
                return false;
            }
            return showCreditHistory(sender, target.getUniqueId(), target.getName());
        } else {
            sender.sendMessage(MessageUtils.error("用法: /yae credit history [player]"));
            return false;
        }
    }
    
    private boolean showOwnCredit(@NotNull CommandSender sender) {
        Player player = getPlayerOrNull(sender);
        if (player == null) {
            sender.sendMessage(MessageUtils.error("此命令只能由玩家使用"));
            return false;
        }
        
        return showCreditScore(sender, player.getName());
    }
    
    private boolean showCreditScore(@NotNull CommandSender sender, @NotNull String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null && !sender.hasPermission("yae.admin.credit.view")) {
            sender.sendMessage(MessageUtils.error("只能查看在线玩家的信用评分"));
            return false;
        }
        
        if (target == null) {
            sender.sendMessage(MessageUtils.error("找不到玩家: " + playerName));
            return false;
        }
        
        // Send loading message
        sender.sendMessage(MessageUtils.info("正在查询 " + playerName + " 的信用评分..."));
        
        try {
            int score = creditService.getCreditScore(target.getUniqueId());
            CreditGrade grade = creditService.getCreditGrade(target.getUniqueId());
            double rank = calculateCreditRank(target.getUniqueId());
            
            if (sender.equals(target)) {
                // Show to the player themselves
                sender.sendMessage(MessageUtils.success("信用评分查询结果:"));
                sender.sendMessage(MessageUtils.color(""));
                sender.sendMessage(MessageUtils.color("&6━━━━━━━━━━ 您的信用信息 ━━━━━━━━━━"));
                sender.sendMessage(MessageUtils.color("&7信用评分: &f" + score));
                sender.sendMessage(MessageUtils.color("&7信用等级: " + grade.getDisplayName()));
                sender.sendMessage(MessageUtils.color("&7等级描述: &f" + grade.getChineseName()));
                sender.sendMessage(MessageUtils.color("&7分数范围: &f" + grade.getMinScore() + " - " + grade.getMaxScore()));
                sender.sendMessage(MessageUtils.color("&7最高信用额度: &6💰" + String.format("%,.0f", grade.getMaxCreditLimit())));
                sender.sendMessage(MessageUtils.color("&7基础年利率: &f" + String.format("%.2f%%", grade.getBaseInterestRate() * 100)));
                
                if (rank > 0) {
                    sender.sendMessage(MessageUtils.color("&7信用排名: &f" + String.format("%.1f%% (前%s)", rank, (100.0 - rank)) + "%"));
                }
                
                // Qualification summary
                sender.sendMessage(MessageUtils.color(""));
                sender.sendMessage(MessageUtils.color("&6━━━━━━━━ 贷款申请资格 ━━━━━━━━"));
                showQualificationSummary(sender, score, grade);
                sender.sendMessage(MessageUtils.color("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                
            } else {
                // Show to another user (admin view)
                sender.sendMessage(MessageUtils.success("信用评分查询结果 - " + playerName + ":"));
                sender.sendMessage(MessageUtils.color("&7玩家: &f" + playerName));
                sender.sendMessage(MessageUtils.color("&7信用评分: &f" + score));
                sender.sendMessage(MessageUtils.color("&7信用等级: " + grade.getDisplayName()));
                sender.sendMessage(MessageUtils.color("&7等级描述: &f" + grade.getChineseName()));
                sender.sendMessage(MessageUtils.color("&7分数范围: &f" + grade.getMinScore() + " - " + grade.getMaxScore()));
                sender.sendMessage(MessageUtils.color("&7最高信用额度: &6💰" + String.format("%,.0f", grade.getMaxCreditLimit())));
                sender.sendMessage(MessageUtils.color("&7基础年利率: &f" + String.format("%.2f%%", grade.getBaseInterestRate() * 100)));
                sender.sendMessage(MessageUtils.color("&7最高可获贷款: &6💰" + String.format("%,.0f", grade.getMaxCreditLimit())));
                
                if (rank > 0) {
                    sender.sendMessage(MessageUtils.color("&7信用排名: " + String.format("%.1f%% (前%s)", rank, (100.0 - rank)) + "%"));
                }
                
                if (sender.hasPermission("yae.admin.credit.detailed")) {
                    sender.sendMessage(MessageUtils.color(""));
                    sender.sendMessage(MessageUtils.color("&e管理员信息:"));
                    sender.sendMessage(MessageUtils.color("&7• UUID: &f" + target.getUniqueId()));
                    sender.sendMessage(MessageUtils.color("&7• 最新更新时间: &f" + "待实现")); // TODO: Add last update time
                }
                
                sender.sendMessage(MessageUtils.color(""));
                sender.sendMessage(MessageUtils.color("&7贷款资格摘要:"));
                showQualificationSummary(sender, score, grade);
            }
            
        } catch (Exception ex) {
            sender.sendMessage(MessageUtils.error("获取信用评分失败，可能未完成初始计算"));
        }
        
        return true;
    }
    
    private double calculateCreditRank(UUID playerId) {
        try {
            int score = creditService.getCreditScore(playerId);
            // This is a best-effort rank calculation based on the player's score
            // In a real implementation, this would query all players' scores from the database
            
            // Simulated rank calculation
            double rankPercentile = 0;
            
            if (score >= 800) rankPercentile = 2.5;   // Top 2.5% (A grade)
            else if (score >= 740) rankPercentile = 15;   // Top 15% (B grade)
            else if (score >= 670) rankPercentile = 50;   // Top 50% (C grade)
            else if (score >= 580) rankPercentile = 85;   // Top 85% (D grade)
            else rankPercentile = 97.5;                // Bottom 2.5% (F grade)
            
            return rankPercentile;
            
        } catch (Exception e) {
            Logging.warning("Failed to calculate credit rank for " + playerId + ": " + e.getMessage());
            return -1.0; // Return error value
        }
    }
    
    private void showQualificationSummary(CommandSender sender, int score, CreditGrade grade) {
        sender.sendMessage(MessageUtils.color("&7信用贷款: " + (grade.qualifiesForLoan(com.yae.api.credit.LoanType.CREDIT) ? "&a✓ 符合" : "&c✗ 不符合")));
        sender.sendMessage(MessageUtils.color("&7抵押贷款: " + (grade.qualifiesForLoan(com.yae.api.credit.LoanType.MORTGAGE) ? "&a✓ 符合" : "&c✗ 不符合")));
        sender.sendMessage(MessageUtils.color("&7商业贷款: " + (grade.qualifiesForLoan(com.yae.api.credit.LoanType.BUSINESS) ? "&a✓ 符合" : "&c✗ 不符合")));
        sender.sendMessage(MessageUtils.color("&7应急贷款: " + (score >= 500 ? "&a✓ 符合" : "&c✗ 不符合")));
        
        if (sender.hasPermission("yae.admin.credit.detailed")) {
            sender.sendMessage(MessageUtils.color("&7预估最高额度:"));
            sender.sendMessage(MessageUtils.color("  &7• 信用贷款: &6💰" + String.format("%,.0f", grade.getMaxCreditLimit())));
            sender.sendMessage(MessageUtils.color("  &7• 抵押贷款: &6💰" + String.format("%,.0f", grade.getMaxCreditLimit() * 2)));
            sender.sendMessage(MessageUtils.color("  &7• 商业贷款: &6💰" + String.format("%,.0f", grade.getMaxCreditLimit() * 1.5)));
            sender.sendMessage(MessageUtils.color("  &7• 应急贷款: &6💰" + String.format("%,.0f", Math.min(grade.getMaxCreditLimit() * 0.3, 50000))));
        }
    }
    
    private boolean refreshCreditScore(@NotNull CommandSender sender, @NotNull UUID playerId, @NotNull String playerName) {
        sender.sendMessage(MessageUtils.info("正在重新计算 " + playerName + " 的信用评分..."));
        
        try {
            // Simulate score recalculation - current implementation returns fixed scores
            int currentScore = creditService.getCreditScore(playerId);
            sender.sendMessage(MessageUtils.success("信用评分重算完成！当前评分: " + currentScore));
            showCreditScore(sender, playerName);
            
        } catch (Exception ex) {
            sender.sendMessage(MessageUtils.error("重新计算信用评分失败: " + ex.getMessage()));
        }
        
        return true;
    }
    
    private boolean showCreditHistory(@NotNull CommandSender sender, @NotNull UUID playerId, @NotNull String playerName) {
        if (sender instanceof Player && !sender.getName().equals(playerName) && 
            !sender.hasPermission("yae.admin.credit.view")) {
            sender.sendMessage(MessageUtils.error("无权查看其他玩家的信用历史"));
            return false;
        }
        
        sender.sendMessage(MessageUtils.info("正在查询 " + playerName + " 的信用历史..."));
        
        // This would be implemented when credit history functionality is available
        // For now, show current information and a placeholder message
        showCreditScore(sender, playerName);
        sender.sendMessage(MessageUtils.color("&7信用历史: &f功能开发中..."));
        
        return true;
    }
    
    private boolean showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(MessageUtils.color("&6━━━━━━━━━━ 信用系统命令帮助 ━━━━━━━━━━"));
        sender.sendMessage(MessageUtils.color("&e/yae credit score [player] &7- 查看信用评分"));
        
        if (sender.hasPermission("yae.command.credit.grade") || sender.hasPermission("yae.admin.credit")) {
            sender.sendMessage(MessageUtils.color("&e/yae credit grade &7- 查看信用等级详情"));
        }
        
        if (sender.hasPermission("yae.admin.credit.check")) {
            sender.sendMessage(MessageUtils.color("&e/yae credit check <player> &7- 检查指定玩家信用"));
        }
        
        if (sender.hasPermission("yae.admin.credit.refresh")) {
            sender.sendMessage(MessageUtils.color("&e/yae credit refresh [player] &7- 重新计算信用评分"));
        }
        
        if (sender.hasPermission("yae.admin.credit.history")) {
            sender.sendMessage(MessageUtils.color("&e/yae credit history [player] &7- 查看信用历史"));
        }
        
        sender.sendMessage(MessageUtils.color("&e/yae credit help &7- 显示此帮助信息"));
        sender.sendMessage(MessageUtils.color("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        
        // Admin summary
        if (sender.hasPermission("yae.admin.credit")) {
            sender.sendMessage(MessageUtils.color(""));
            sender.sendMessage(MessageUtils.color("&c管理员权限提醒: &7您拥有信用系统的管理员权限"));
            sender.sendMessage(MessageUtils.color("&7可以查看所有玩家的信用信息并执行管理操作"));
        }
        
        return true;
    }
    
    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!checkPermission(sender)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("score");
            suggestions.add("view");
            suggestions.add("help");
            
            if (sender.hasPermission("yae.command.credit.grade")) {
                suggestions.add("grade");
            }
            
            if (sender.hasPermission("yae.admin.credit.check")) {
                suggestions.add("check");
            }
            
            if (sender.hasPermission("yae.admin.credit.refresh")) {
                suggestions.add("refresh");
            }
            
            if (sender.hasPermission("yae.admin.credit.history")) {
                suggestions.add("history");
            }
            
            return suggestions.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("score") || subCommand.equals("check") || 
                (subCommand.equals("refresh") && sender.hasPermission("yae.admin.credit.refresh")) ||
                (subCommand.equals("history") && sender.hasPermission("yae.admin.credit.history"))) {
                // Return list of online players
                return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> sender.hasPermission("yae.admin.credit.view") || p.getName().equals(sender.getName()))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
            
            // Default case for other sub-commands
            if (subCommand.equals("view") || (subCommand.equals("refresh") && !sender.hasPermission("yae.admin.credit.refresh"))) {
                return Collections.emptyList();
            }
        }
        
        return Collections.emptyList();
    }
}
