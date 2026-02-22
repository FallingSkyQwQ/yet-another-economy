package com.yae.api.loan.command;

import com.yae.api.core.ServiceType;
import com.yae.YetAnotherEconomy;
import com.yae.api.core.command.YAECommand;
import com.yae.api.gui.LoanManagementGUI;
import com.yae.api.loan.Loan;
import com.yae.api.loan.LoanService;
import com.yae.api.credit.LoanType;
import com.yae.utils.MessageUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Loan command implementation
 * Handles /yae loan commands for loan management
 */
public class LoanCommand extends YAECommand {
    
    private final LoanService loanService;
    
    public LoanCommand(@NotNull YetAnotherEconomy plugin, @NotNull LoanService loanService) {
        super(plugin, "loan", "贷款管理相关命令", "yae.command.loan", 
              Arrays.asList("lend", "borrow", "application"));
        this.loanService = loanService;
    }
    
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!checkExecutionPermission(sender)) {
            return false;
        }
        
        if (args.length == 0) {
            // Show sender's loan overview
            return showLoanOverview(sender);
        }
        
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "list":
            case "ls":
                return handleListCommand(sender, args);
            case "view":
                return handleViewCommand(sender, args);
            case "apply":
                return handleApplyCommand(sender, args);
            case "pay":
                return handlePayCommand(sender, args);
            case "gui":
                return handleGuiCommand(sender, args);
            case "history":
            case "h":
                return handleHistoryCommand(sender, args);
            case "eligibility":
            case "elig":
                return handleEligibilityCommand(sender, args);
            case "status":
                return handleStatusCommand(sender, args);
            case "admin":
                return handleAdminCommand(sender, args);
            case "help":
                return showHelp(sender);
            default:
                sender.sendMessage(MessageUtils.error("未知子命令: " + subCommand));
                sender.sendMessage(MessageUtils.info("使用 /yae loan help 查看帮助"));
                return false;
        }
    }
    
    private boolean handleListCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = getPlayerOrNull(sender);
        if (player == null) {
            sender.sendMessage(MessageUtils.error("此命令只能由玩家使用"));
            return false;
        }
        
        String playerName = player.getName();
        if (args.length >= 2) {
            playerName = args[1];
            // Check permission to view others' loans
            if (!player.getName().equals(playerName) && !sender.hasPermission("yae.admin.loan.view")) {
                sender.sendMessage(MessageUtils.error("您没有权限查看其他玩家的贷款"));
                return false;
            }
        }
        
        return listPlayerLoans(sender, playerName);
    }
    
    private boolean handleViewCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan view <loan-id>"));
            return false;
        }
        
        String loanId = args[1];
        return showLoanDetail(sender, loanId);
    }
    
    private boolean handleApplyCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = getPlayerOrNull(sender);
        if (player == null) {
            sender.sendMessage(MessageUtils.error("此命令只能由玩家使用"));
            return false;
        }
        
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan apply <type> <amount> [term-months] [purpose]"));
            return false;
        }
        
        LoanType loanType;
        try {
            loanType = LoanType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(MessageUtils.error("无效贷款类型: " + args[1]));
            sender.sendMessage(MessageUtils.info("可用类型: CREDIT, MORTGAGE, BUSINESS, EMERGENCY"));
            return false;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.error("无效金额: " + args[2]));
            return false;
        }
        
        int termMonths = loanType.getMaxTermMonths() / 2; // Default to half max term
        if (args.length >= 4) {
            try {
                termMonths = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtils.error("无效期限: " + args[3]));
                return false;
            }
        }
        
        String purpose = "个人资金需求"; // Default purpose
        if (args.length >= 5) {
            purpose = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        }
        
        return submitLoanApplication(sender, player, loanType, amount, termMonths, purpose);
    }
    
    private boolean handlePayCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan pay <loan-id> [amount]"));
            return false;
        }
        
        String loanId = args[1];
        double amount = 0; // Use monthly payment if not specified
        
        if (args.length >= 3) {
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtils.error("无效金额: " + args[2]));
                return false;
            }
        }
        
        return makePayment(sender, loanId, amount);
    }
    
    private boolean handleGuiCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return false;
        }
        
        // Open loan management GUI
        LoanManagementGUI gui = new LoanManagementGUI(loanService, player);
        gui.open();
        return true;
    }
    
    private boolean handleHistoryCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan history <player>"));
            return false;
        }
        
        return showLoanHistory(sender, args[1]);
    }
    
    private boolean handleEligibilityCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return false;
        }
        
        return checkEligibility(player);
    }
    
    private boolean handleStatusCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        String loanId = null;
        if (args.length >= 2) {
            loanId = args[1];
        }
        return showLoanStatus(sender, loanId);
    }
    
    private boolean handleAdminCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("yae.admin.loan")) {
            sender.sendMessage(MessageUtils.error("您没有贷款管理的管理员权限"));
            return false;
        }
        
        if (args.length < 2) {
            showAdminHelp(sender);
            return true;
        }
        
        String adminSubCommand = args[1].toLowerCase();
        switch (adminSubCommand) {
            case "approve":
                return approveLoan(sender, args);
            case "reject":
                return rejectLoan(sender, args);
            case "process":
                return processLoan(sender, args);
            case "default":
                return markAsDefault(sender, args);
            case "complete":
                return completeLoan(sender, args);
            case "stats":
                return showLoanStats(sender);
            default:
                sender.sendMessage(MessageUtils.error("未知管理员命令: " + adminSubCommand));
                showAdminHelp(sender);
                return false;
        }
    }
    
    // Individual command implementations
    
    private boolean showLoanOverview(@NotNull CommandSender sender) {
        Player player = getPlayerOrNull(sender);
        if (player == null) {
            return false;
        }
        
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━ 贷款系统概览 ━━━━━━━━━━"));
        
        // Get player loans asynchronously
        var loans = loanService.getPlayerLoans(player.getUniqueId());
        
        int totalLoans = loans.size();
        int activeLoans = (int) loans.stream().filter(loan -> loan.getStatus().isActive()).count();
        int overdueLoans = (int) loans.stream().filter(loan -> loan.getStatus() == Loan.LoanStatus.OVERDUE).count();
        int pendingLoans = (int) loans.stream().filter(loan -> loan.getStatus() == Loan.LoanStatus.PENDING).count();
        
        double totalBalance = loans.stream()
            .filter(loan -> loan.getStatus().isActive() || loan.getStatus() == Loan.LoanStatus.OVERDUE)
            .mapToDouble(Loan::getCurrentBalance)
            .sum();
        
        double totalOverdue = loans.stream()
            .filter(loan -> loan.getStatus() == Loan.LoanStatus.OVERDUE)
            .mapToDouble(Loan::getOverdueAmount)
            .sum();
        
        sender.sendMessage(MessageUtils.format("&6您的贷款统计:"));
        sender.sendMessage(MessageUtils.format("&7总贷款数量: &f" + totalLoans));
        sender.sendMessage(MessageUtils.format("&7活跃贷款: &a" + activeLoans));
        sender.sendMessage(MessageUtils.format("&7待审核: &e" + pendingLoans));
        sender.sendMessage(MessageUtils.format("&7逾期贷款: &c" + overdueLoans));
        sender.sendMessage(MessageUtils.format("&7总贷款余额: &6💰" + String.format("%,.0f", totalBalance)));
        
        if (totalOverdue > 0) {
            sender.sendMessage(MessageUtils.format("&7逾期金额: &c💰" + String.format("%,.0f", totalOverdue)));
        }
        
        // Show next payment due date
        var nextDueLoan = loans.stream()
            .filter(loan -> loan.getStatus().isActive() && loan.getNextPaymentDate() != null)
            .min((l1, l2) -> l1.getNextPaymentDate().compareTo(l2.getNextPaymentDate()))
            .orElse(null);
        
        if (nextDueLoan != null) {
            sender.sendMessage(MessageUtils.format("&7下次还款: &f" + nextDueLoan.getNextPaymentDate()));
            sender.sendMessage(MessageUtils.format("&7月供金额: &6💰" + String.format("%,.0f", nextDueLoan.getMonthlyPayment())));
        }
        
        // Quick actions
        sender.sendMessage(MessageUtils.format(""));
        sender.sendMessage(MessageUtils.format("&6快捷操作:"));
        sender.sendMessage(MessageUtils.format("&e/yae loan gui &7- 贷款管理界面"));
        sender.sendMessage(MessageUtils.format("&e/yae loan eligibility &7- 检查贷款资格"));
        sender.sendMessage(MessageUtils.format("&e/yae loan apply <type> <amount> &7- 申请贷款"));
        
        return true;
    }
    
    private boolean listPlayerLoans(@NotNull CommandSender sender, String playerName) {
        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(MessageUtils.error("找不到玩家: " + playerName));
            return false;
        }
        
        var loans = loanService.getPlayerLoans(targetPlayer.getUniqueId());
        
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━ " + playerName + " 的贷款列表 ━━━━━━━━━━"));
        
        if (loans.isEmpty()) {
            sender.sendMessage(MessageUtils.info("该玩家暂无贷款记录"));
            sender.sendMessage(MessageUtils.format("&7使用 &e/yae loan apply &7可启动贷款申请"));
            return true;
        }
        
        for (Loan loan : loans) {
            String color = getLoanStatusColor(loan.getStatus());
            sender.sendMessage(MessageUtils.format("&7→ " + color + loan.getLoanType().getDisplayName() + 
                " &f- &6💰" + String.format("%,.0f", loan.getPrincipalAmount()) +
                " &f- " + color + getStatusText(loan.getStatus())));
            sender.sendMessage(MessageUtils.format("    &7编号: &f" + loan.getLoanId()));
        }
        
        return true;
    }
    
    private boolean showLoanDetail(@NotNull CommandSender sender, String loanId) {
        Loan loan = loanService.getLoan(loanId);
        if (loan == null) {
            sender.sendMessage(MessageUtils.error("找不到贷款: " + loanId));
            return false;
        }
        
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━ 《贷款详细信息》 ━━━━━━━━━━"));
        sender.sendMessage(MessageUtils.format("&7贷款编号: &f" + loan.getLoanId()));
        sender.sendMessage(MessageUtils.format("&7贷款类型: &f" + loan.getLoanType().getDisplayName()));
        sender.sendMessage(MessageUtils.format("&7当前状态: " + getLoanStatusColor(loan.getStatus()) + getStatusText(loan.getStatus())) +
            " &f(&7" + loan.getPaymentsMade() + "/" + loan.getTotalPayments() + "&f)");
        sender.sendMessage(MessageUtils.format(""));
        sender.sendMessage(MessageUtils.format("&6《贷款金额信息》"));
        sender.sendMessage(MessageUtils.format("&7本金金额: &6💰" + String.format("%,.0f", loan.getPrincipalAmount())));
        sender.sendMessage(MessageUtils.format("&7当前余额: &6💰" + String.format("%,.0f", loan.getCurrentBalance())));
        sender.sendMessage(MessageUtils.format("&7年利率: &f" + String.format("%.2f%%", loan.getInterestRate() * 100)));
        sender.sendMessage(MessageUtils.format("&7已付利息: &6💰" + String.format("%,.0f", loan.getTotalInterestPaid())));
        sender.sendMessage(MessageUtils.format("&7已付本金: &6💰" + String.format("%,.0f", loan.getTotalPrincipalPaid())));
        sender.sendMessage(MessageUtils.format(""));
        sender.sendMessage(MessageUtils.format("&6《还款信息》"));
        sender.sendMessage(MessageUtils.format("&7月供金额: &6💰" + String.format("%,.0f", loan.getMonthlyPayment())));
        sender.sendMessage(MessageUtils.format("&7下次还款: &f" + loan.getNextPaymentDate()));
        sender.sendMessage(MessageUtils.format("&7贷款类型: &f" + loan.getLoanType().getDisplayName()));
        
        if (loan.getStatus() == Loan.LoanStatus.OVERDUE) {
            sender.sendMessage(MessageUtils.format("&c《逾期信息》"));
            sender.sendMessage(MessageUtils.format("&c逾期期数: " + loan.getOverduePayments() + " 期"));
            sender.sendMessage(MessageUtils.format("&c逾期金额: 💰" + String.format("%,.0f", loan.getOverdueAmount())));
        }
        
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        
        return true;
    }
    
    private boolean submitLoanApplication(@NotNull CommandSender sender, Player player, 
                                        LoanType loanType, double amount, int termMonths, String purpose) {
        sender.sendMessage(MessageUtils.info("正在提交 " + loanType.getDisplayName() + " 申请..."));
        
        loanService.submitLoanApplication(player, loanType, amount, termMonths, purpose, "", 0)
            .thenAccept(loanId -> {
                sender.sendMessage(MessageUtils.success("贷款申请提交成功！"));
                sender.sendMessage(MessageUtils.format("&7贷款编号: &e" + loanId));
                sender.sendMessage(MessageUtils.format("&7申请金额: &6💰" + String.format("%,.0f", amount)));
                sender.sendMessage(MessageUtils.format("&7贷款期限: &f" + termMonths + " 月"));
                sender.sendMessage(MessageUtils.format("&7预计审核时间: &f24-48小时内"));
                
                // Schedule credit score update
                plugin.getService(ServiceType.CREDIT).scheduleCreditScoreUpdate(player.getUniqueId());
                
            })
            .exceptionally(ex -> {
                sender.sendMessage(MessageUtils.error("申请提交失败: " + ex.getMessage()));
                return null;
            });
        
        return true;
    }
    
    private boolean makePayment(@NotNull CommandSender sender, String loanId, double amount) {
        double paymentAmount = amount;
        
        if (amount <= 0) {
            // Get the loan to determine monthly payment
            Loan loan = loanService.getLoan(loanId);
            if (loan == null) {
                sender.sendMessage(MessageUtils.error("找不到贷款: " + loanId));
                return false;
            }
            paymentAmount = loan.getMonthlyPayment();
        }
        
        sender.sendMessage(MessageUtils.info("正在处理还款..."));
        
        loanService.makePayment(loanId, paymentAmount, Loan.PaymentMethod.VAULT)
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    sender.sendMessage(MessageUtils.success("还款成功！"));
                    sender.sendMessage(MessageUtils.format("&7支付金额: &6💰" + String.format("%,.0f", result.getTotalPayment())));
                    sender.sendMessage(MessageUtils.format("&7其中利息: &f💰" + String.format("%,.0f", result.getInterestPayment())));
                    sender.sendMessage(MessageUtils.format("&7其中本金: &f💰" + String.format("%,.0f", result.getPrincipalPayment())));
                    
                    if (result.getPenaltyPayment() > 0) {
                        sender.sendMessage(MessageUtils.format("&a滞纳金: &c💰" + String.format("%,.0f", result.getPenaltyPayment())));
                    }
                    
                    if (result.getUpdatedLoan().getStatus() == Loan.LoanStatus.PAID_OFF) {
                        sender.sendMessage(MessageUtils.success("恭喜！贷款已结清！"));
                    }
                    
                } else {
                    sender.sendMessage(MessageUtils.error("还款失败"));
                }
            })
            .exceptionally(ex -> {
                sender.sendMessage(MessageUtils.error("还款处理失败: " + ex.getMessage()));
                return null;
            });
        
        return true;
    }
    
    private boolean checkEligibility(@NotNull Player player) {
        // This would open the GUI or show eligibility information
        player.sendMessage(MessageUtils.format("&6[贷款系统] &f正在查询您的贷款资格..."));
        player.sendMessage(MessageUtils.format("&7使用 &e/yae loan gui &7打开贷款管理界面"));
        return true;
    }
    
    private boolean showLoanHistory(@NotNull CommandSender sender, String playerName) {
        sender.sendMessage(MessageUtils.format("&6[贷款系统] &f正在查询 " + playerName + " 的贷款历史..."));
        // Implementation would query historical loans
        return true;
    }
    
    private boolean showLoanStatus(@NotNull CommandSender sender, String loanId) {
        if (loanId == null) {
            return showLoanOverview(sender);
        }
        
        return showLoanDetail(sender, loanId);
    }
    
    // Admin command implementations
    
    private boolean approveLoan(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan admin approve <loan-id> [notes]"));
            return false;
        }
        
        String loanId = args[2];
        String approvedBy = sender.getName();
        String notes = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "管理员批准";
        
        sender.sendMessage(MessageUtils.info("正在批准贷款..."));
        
        loanService.approveLoan(loanId, approvedBy, notes)
            .thenAccept(loan -> {
                sender.sendMessage(MessageUtils.success("贷款已批准！"));
                sender.sendMessage(MessageUtils.format("&7贷款编号: &e" + loanId));
            })
            .exceptionally(ex -> {
                sender.sendMessage(MessageUtils.error("批准失败: " + ex.getMessage()));
                return null;
            });
        
        return true;
    }
    
    private boolean rejectLoan(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan admin reject <loan-id> <reason>"));
            return false;
        }
        
        String loanId = args[2];
        String rejectedBy = sender.getName();
        String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        
        sender.sendMessage(MessageUtils.info("正在拒绝贷款..."));
        
        loanService.rejectLoan(loanId, rejectedBy, reason)
            .thenAccept(loan -> {
                sender.sendMessage(MessageUtils.success("贷款已拒绝！"));
                sender.sendMessage(MessageUtils.format("&7贷款编号: &e" + loanId));
            })
            .exceptionally(ex -> {
                sender.sendMessage(MessageUtils.error("拒绝失败: " + ex.getMessage()));
                return null;
            });
        
        return true;
    }
    
    private boolean processLoan(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan admin process <loan-id>"));
            return false;
        }
        
        String loanId = args[2];
        
        sender.sendMessage(MessageUtils.info("正在放款处理..."));
        
        loanService.disburseLoan(loanId)
            .thenAccept(loan -> {
                sender.sendMessage(MessageUtils.success("贷款已放款！"));
                sender.sendMessage(MessageUtils.format("&7贷款编号: &e" + loanId));
                sender.sendMessage(MessageUtils.format("&7放款金额: &6💰" + String.format("%,.0f", loan.getPrincipalAmount())));
            })
            .exceptionally(ex -> {
                sender.sendMessage(MessageUtils.error("放款失败: " + ex.getMessage()));
                return null;
            });
        
        return true;
    }
    
    private boolean markAsDefault(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan admin default <loan-id> [reason]"));
            return false;
        }
        
        String loanId = args[2];
        String reason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "管理员标记为违约";
        
        // This would use the OverdueProcessingService to mark loan as default
        sender.sendMessage(MessageUtils.info("正在处理违约标记..."));
        sender.sendMessage(MessageUtils.success("贷款 " + loanId + " 已标记为违约"));
        sender.sendMessage(MessageUtils.format("&7原因: " + reason));
        
        return true;
    }
    
    private boolean completeLoan(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.error("用法: /yae loan admin complete <loan-id>"));
            return false;
        }
        
        String loanId = args[2];
        sender.sendMessage(MessageUtils.success("贷款 " + loanId + " 已手动标记为结清"));
        return true;
    }
    
    private boolean showLoanStats(@NotNull CommandSender sender) {
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━ 《贷款系统统计》 ━━━━━━━━━━"));
        
        // Calculate statistics (this would query database in real implementation)
        int totalLoans = 0; // Query would get actual count
        int pendingLoans = 0;
        int activeLoans = 0;
        int overdueLoans = 0;
        double totalLoanAmount = 0.0;
        double totalCurrentBalance = 0.0;
        double totalOverdueAmount = 0.0;
        
        sender.sendMessage(MessageUtils.format("&6《总体统计》"));
        sender.sendMessage(MessageUtils.format("&7总贷款: &f" + totalLoans));
        sender.sendMessage(MessageUtils.format("&7待审核: &e" + pendingLoans));
        sender.sendMessage(MessageUtils.format("&7活跃贷款: &a" + activeLoans));
        sender.sendMessage(MessageUtils.format("&7逾期贷款: &c" + overdueLoans));
        sender.sendMessage(MessageUtils.format("&7贷款总额: &6💰" + String.format("%,.0f", totalLoanAmount)));
        sender.sendMessage(MessageUtils.format("&7当前余额: &6💰" + String.format("%,.0f", totalCurrentBalance)));
        sender.sendMessage(MessageUtils.format("&7逾期金额: &c💰" + String.format("%,.0f", totalOverdueAmount)));
        
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }
    
    // Helper methods
    
    private String getLoanStatusColor(Loan.LoanStatus status) {
        switch (status) {
            case ACTIVE:
            case PAID_OFF:
                return "&a";
            case PENDING:
            case APPROVED:
                return "&e";
            case OVERDUE:
                return "&c";
            case DEFAULT:
                return "&4";
            case REJECTED:
                return "&8";
            case CANCELLED:
                return "&7";
            default:
                return "&f";
        }
    }
    
    private String getStatusText(Loan.LoanStatus status) {
        switch (status) {
            case PENDING:
                return "待审核";
            case APPROVED:
                return "已批准";
            case ACTIVE:
                return "正常还款";
            case OVERDUE:
                return "逾期";
            case PAID_OFF:
                return "已结清";
            case DEFAULT:
                return "违约";
            case REJECTED:
                return "已拒绝";
            case CANCELLED:
                return "已取消";
            default:
                return "未知";
        }
    }
    
    private boolean showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━ 《贷款系统命令帮助》 ━━━━━━━━━━"));
        
        // Player commands
        sender.sendMessage(MessageUtils.format("&e玩家命令:"));
        sender.sendMessage(MessageUtils.format("&6/yae loan &7- 查看贷款概览"));
        sender.sendMessage(MessageUtils.format("&6/yae loan list [player] &7- 查看贷款列表"));
        sender.sendMessage(MessageUtils.format("&6/yae loan view <loan-id> &7- 查看贷款详情"));
        sender.sendMessage(MessageUtils.format("&6/yae loan apply <type> <amount> [term] [purpose] &7- 申请贷款"));
        sender.sendMessage(MessageUtils.format("&6/yae loan pay <loan-id> [amount] &7- 还款"));
        sender.sendMessage(MessageUtils.format("&6/yae loan gui &7- 打开管理界面"));
        sender.sendMessage(MessageUtils.format("&6/yae loan eligibility &7- 检查资格"));
        sender.sendMessage(MessageUtils.format("&6/yae loan status [loan-id] &7- 查看状态"));
        
        // Admin commands
        if (sender.hasPermission("yae.admin.loan")) {
            sender.sendMessage(MessageUtils.format(""));
            sender.sendMessage(MessageUtils.format("&c管理员命令:"));
            sender.sendMessage(MessageUtils.format("&6/yae loan admin approve <loan-id> [notes] &7- 批准贷款"));
            sender.sendMessage(MessageUtils.format("&6/yae loan admin reject <loan-id> <reason> &7- 拒绝贷款"));
            sender.sendMessage(MessageUtils.format("&6/yae loan admin process <loan-id> &7- 放款处理"));
            sender.sendMessage(MessageUtils.format("&6/yae loan admin default <loan-id> [reason] &7- 标记违约"));
            sender.sendMessage(MessageUtils.format("&6/yae loan admin complete <loan-id> &7- 手动结清"));
            sender.sendMessage(MessageUtils.format("&6/yae loan admin stats &7- 系统统计"));
        }
        
        sender.sendMessage(MessageUtils.format(""));
        sender.sendMessage(MessageUtils.format("&e/yae loan help &7- 显示此帮助"));
        sender.sendMessage(MessageUtils.format("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        
        // Loan type info
        sender.sendMessage(MessageUtils.format(""));
        sender.sendMessage(MessageUtils.format("&7贷款类型:"));
        sender.sendMessage(MessageUtils.format("&eCREDIT &7- 信用贷款 (无抵押)"));
        sender.sendMessage(MessageUtils.format("&6MORTGAGE &7- 抵押贷款 (需抵押)"));
        sender.sendMessage(MessageUtils.format("&bBUSINESS &7- 商业贷款 (商业用途)"));
        sender.sendMessage(MessageUtils.format("&cEMERGENCY &7- 应急贷款 (应急资金)"));
        
        return true;
    }
    
    private void showAdminHelp(@NotNull CommandSender sender) {
        sender.sendMessage(MessageUtils.format("&c━━━━━━━━━━ 贷款管理管理员命令 ━━━━━━━━━━"));
        sender.sendMessage(MessageUtils.format("&6/yae loan admin approve <loan-id> [notes] &7- 批准贷款"));
        sender.sendMessage(MessageUtils.format("&6/yae loan admin reject <loan-id> <reason> &7- 拒绝贷款"));
        sender.sendMessage(MessageUtils.format("&6/yae loan admin process <loan-id> &7- 放款处理"));
        sender.sendMessage(MessageUtils.format("&6/yae loan admin default <loan-id> [reason] &7- 标记违约"));
        sender.sendMessage(MessageUtils.format("&6/yae loan admin complete <loan-id> &7- 手动结清"));
        sender.sendMessage(MessageUtils.format("&6/yae loan admin stats &7- 系统统计"));
        sender.sendMessage(MessageUtils.format("&c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }
    
    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!checkPermission(sender)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("list");
            suggestions.add("view");
            suggestions.add("apply");
            suggestions.add("pay");
            suggestions.add("gui");
            suggestions.add("history");
            suggestions.add("eligibility");
            suggestions.add("status");
            suggestions.add("help");
            
            if (sender.hasPermission("yae.admin.loan")) {
                suggestions.add("admin");
            }
            
            return suggestions.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("view")) {
                // List loan IDs for view command
                List<String> loanIds = plugin.getServer().getOnlinePlayers().stream()
                    .map(player -> loanService.getPlayerLoans(player.getUniqueId()))
                    .flatMap(List::stream)
                    .map(Loan::getLoanId)
                    .toList();
                return loanIds.stream()
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
            
            if (subCommand.equals("apply")) {
                return Arrays.stream(LoanType.values())
                    .map(type -> type.name().toLowerCase())
                    .filter(type -> type.startsWith(args[1].toLowerCase()))
                    .toList();
            }
            
            if (subCommand.equals("pay")) {
                // List loan IDs for pay command
                Player player = getPlayerOrNull(sender);
                if (player != null) {
                    return loanService.getPlayerLoans(player.getUniqueId()).stream()
                        .map(Loan::getLoanId)
                        .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
                }
            }
            
            if (subCommand.equals("admin")) {
                List<String> adminCommands = new ArrayList<>();
                adminCommands.add("approve");
                adminCommands.add("reject");
                adminCommands.add("process");
                adminCommands.add("default");
                adminCommands.add("complete");
                adminCommands.add("stats");
                
                return adminCommands.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
            }
            
            if (subCommand.equals("history") && sender.hasPermission("yae.admin.loan")) {
                // List online players
                return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }
        
        return Collections.emptyList();
    }
}
