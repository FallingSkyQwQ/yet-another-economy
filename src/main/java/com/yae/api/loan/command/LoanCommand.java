package com.yae.api.loan.command;

import com.yae.api.core.YAECore;
import com.yae.api.loan.LoanService;
import com.yae.api.core.ServiceType;
import com.yae.api.loan.SimpleLoan;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Simplified loan command handler
 * Supports basic loan operations without complex GUI dependencies
 */
public class LoanCommand implements CommandExecutor {
    
    private final YAECore plugin;
    private final LoanService loanService;
    
    public LoanCommand(YAECore plugin) {
        this.plugin = plugin;
        this.loanService = (LoanService) plugin.getService(ServiceType.LOAN);
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        try {
            switch (subCommand) {
                case "apply":
                    return handleApplyCommand(sender, args);
                case "pay":
                    return handlePayCommand(sender, args);
                case "status":
                    return handleStatusCommand(sender, args);
                case "list":
                    return handleListCommand(sender, args);
                case "help":
                    showHelp(sender);
                    return true;
                default:
                    sender.sendMessage("§c无效的子命令: " + subCommand);
                    return false;
            }
            
        } catch (Exception e) {
            sender.sendMessage("§c[YAE] 命令执行失败: " + e.getMessage());
            return false;
        }
    }
    
    private boolean handleApplyCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return false;
        }
        
        if (args.length < 4) {
            sender.sendMessage("§c使用方法: /yae loan apply <类型> <金额> <期限>");
            return false;
        }
        
        Player player = (Player) sender;
        
        try {
            // Parse loan type (simplified - only support mortgage)
            String loanType = args[1].toUpperCase();
            if (!loanType.equals("MORTGAGE")) {
                player.sendMessage("§c目前只支持抵押贷款 (MORTGAGE)");
                return false;
            }
            
            // Parse amount
            double amount = Double.parseDouble(args[2]);
            if (amount < 1000 || amount > 1000000) {
                player.sendMessage("§c贷款金额必须在1000到1000000之间");
                return false;
            }
            
            // Parse term months
            int termMonths = Integer.parseInt(args[3]);
            if (termMonths < 3 || termMonths > 360) {
                player.sendMessage("§c贷款期限必须在3到360个月之间");
                return false;
            }
            
            // Create loan (fixed 8% interest rate)
            double interestRate = 8.0;
            SimpleLoan loan = loanService.createLoan(player.getUniqueId(), amount, termMonths, interestRate);
            
            player.sendMessage("§6[YAE] === 贷款申请成功 ===");
            player.sendMessage(String.format("§f贷款ID: §e%s", loan.getLoanId()));
            player.sendMessage(String.format("§f贷款金额: §a¥%.2f", loan.getPrincipal()));
            player.sendMessage(String.format("§f年利率: §b%.2f%%", loan.getInterestRate()));
            player.sendMessage(String.format("§f贷款期限: §b%d个月", loan.getTermMonths()));
            player.sendMessage(String.format("§f月供: §a¥%.2f", loan.getMonthlyPayment()));
            player.sendMessage(String.format("§f总还款: §6¥%.2f", loan.getPrincipal() + loan.getPrincipal() * interestRate / 100 * termMonths / 12));
            player.sendMessage("§7提示: 使用 /yae loan list 查看您的贷款");
            
            return true;
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c输入格式错误，请检查金额和期限格式");
            return false;
        }
    }
    
    private boolean handlePayCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return false;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /yae loan pay <贷款ID> <金额>");
            return false;
        }
        
        Player player = (Player) sender;
        
        try {
            String loanId = args[1];
            double amount = Double.parseDouble(args[2]);
            
            if (amount <= 0) {
                player.sendMessage("§c还款金额必须大于0");
                return false;
            }
            
            SimpleLoan loan = loanService.getLoan(loanId);
            if (loan == null) {
                player.sendMessage("§c未找到指定的贷款");
                return false;
            }
            
            if (!loan.getPlayerId().equals(player.getUniqueId())) {
                player.sendMessage("§c您无权操作此贷款");
                return false;
            }
            
            boolean success = loanService.makePayment(loanId, amount);
            
            if (success) {
                player.sendMessage("§6[YAE] 还款成功");
                player.sendMessage(String.format("§f贷款ID: §e%s", loanId));
                player.sendMessage(String.format("§f还款金额: §a¥%.2f", amount));
                player.sendMessage(String.format("§f剩余欠款: §c¥%.2f", loan.getRemainingBalance()));
                
                if (loan.getRemainingBalance() <= 0) {
                    player.sendMessage("§a✅ 恭喜！贷款已结清！");
                }
                
                return true;
            } else {
                player.sendMessage("§c还款失败，请稍后再试");
                return false;
            }
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c金额格式错误");
            return false;
        }
    }
    
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length > 1) {
            // Show specific loan status
            String loanId = args[1];
            SimpleLoan loan = loanService.getLoan(loanId);
            
            if (loan == null) {
                player.sendMessage("§c未找到指定的贷款");
                return true;
            }
            
            if (!loan.getPlayerId().equals(player.getUniqueId())) {
                player.sendMessage("§c您无权查看此贷款");
                return true;
            }
            
            displayLoanStatus(player, loan);
        } else {
            // Show all player loans
            List<SimpleLoan> playerLoans = loanService.getPlayerLoans(player.getUniqueId());
            
            if (playerLoans.isEmpty()) {
                player.sendMessage("§6[YAE] 您当前没有贷款");
                player.sendMessage("§7提示: 使用 /yae loan apply 申请新贷款");
            } else {
                player.sendMessage("§6[YAE] === 您的贷款列表 ===");
                for (SimpleLoan loan : playerLoans) {
                    player.sendMessage(String.format("§f贷款ID: §e%s §f状态: %s", 
                        loan.getLoanId(), loan.isActive() ? "§a活跃" : "§c已结清"));
                    player.sendMessage(String.format("§7  本金: ¥%.2f 剩余: ¥%.2f 月供: ¥%.2f", 
                        loan.getPrincipal(), loan.getRemainingBalance(), loan.getMonthlyPayment()));
                    if (loan.isOverdue()) {
                        player.sendMessage("§c  ⚠️ 此贷款已逾期！");
                    }
                }
            }
        }
        
        return true;
    }
    
    private boolean handleListCommand(CommandSender sender, String[] args) {
        return handleStatusCommand(sender, args);
    }
    
    private void displayLoanStatus(Player player, SimpleLoan loan) {
        player.sendMessage("§6[YAE] === 贷款详细状态 ===");
        player.sendMessage(String.format("§f贷款ID: §e%s", loan.getLoanId()));
        player.sendMessage(String.format("§f贷款金额: §a¥%.2f", loan.getPrincipal()));
        player.sendMessage(String.format("§f剩余欠款: §c¥%.2f", loan.getRemainingBalance()));
        player.sendMessage(String.format("§f年利率: §b%.2f%%", loan.getInterestRate()));
        player.sendMessage(String.format("§f贷款期限: §b%d个月", loan.getTermMonths()));
        player.sendMessage(String.format("§f月供: §a¥%.2f", loan.getMonthlyPayment()));
        player.sendMessage(String.format("§f已还款期数: §e%d期", loan.getPaymentsMade()));
        player.sendMessage(String.format("§f状态: %s", 
            loan.isActive() ? (loan.isOverdue() ? "§c逾期" : "§a正常") : "§7已结清"));
        player.sendMessage(String.format("§f到期日: §6%s", loan.getDueDate()));
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== YAE 简化贷款系统 ===");
        sender.sendMessage("§e/yae loan apply <MORTGAGE> <金额> <期限> §f- 申请抵押贷款");
        sender.sendMessage("§e/yae loan pay <贷款ID> <金额> §f- 还款");
        sender.sendMessage("§e/yae loan status [贷款ID] §f- 查看贷款状态");
        sender.sendMessage("§e/yae loan list §f- 列出您的所有贷款");
        sender.sendMessage("§e/yae loan help §f- 显示此帮助");
        sender.sendMessage("§7目前仅支持抵押贷款，年利率固定8%");
    }
    
    private boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }
}
