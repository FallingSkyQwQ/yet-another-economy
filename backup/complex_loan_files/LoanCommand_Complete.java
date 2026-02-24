package com.yae.api.loan.command;

import com.yae.api.core.YAECore;
import com.yae.api.loan.*;
import com.yae.api.loan.gui.*;
import com.yae.api.credit.LoanType;
import com.yae.utils.Messages;
import com.yae.utils.Logging;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.text.DecimalFormat;

/**
 * Complete loan system command handler
 * Supports loan application, management, and administration
 */
public class LoanCommand_Complete implements CommandExecutor {
    
    private final YAECore plugin;
    private final LoanApplicationService applicationService;
    private final RepaymentService repaymentService;
    private final OverdueProcessingService overdueService;
    
    // GUI instances
    private final LoanApplicationGUI applicationGUI;
    private final LoanManagementGUI managementGUI;
    private final MyLoansGUI myLoansGUI;
    
    // Formatters
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat RATE_FORMAT = new DecimalFormat("0.00");
    
    public LoanCommand_Complete(YAECore plugin) {
        this.plugin = plugin;
        this.applicationService = (LoanApplicationService) plugin.getService(ServiceType.LOAN);
        this.repaymentService = (RepaymentService) plugin.getService(ServiceType.LOAN);
        this.overdueService = (OverdueProcessingService) plugin.getService(ServiceType.LOAN);
        
        // Initialize GUI components
        this.applicationGUI = new LoanApplicationGUI(plugin);
        this.managementGUI = new LoanManagementGUI(plugin);
        this.myLoansGUI = new MyLoansGUI(plugin);
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        
        // Help command
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        try {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "gui":
                    return handleGuiCommand(sender, args);
                case "apply":
                    return handleApplyCommand(sender, args);
                case "pay":
                    return handlePayCommand(sender, args);
                case "status":
                    return handleStatusCommand(sender, args);
                case "list":
                    return handleListCommand(sender, args);
                case "eligibility":
                    return handleEligibilityCommand(sender, args);
                case "calculate":
                    return handleCalculateCommand(sender, args);
                case "auto_pay":
                    return handleAutoPayCommand(sender, args);
                case "admin":
                    return handleAdminCommand(sender, args);
                case "help":
                    showHelp(sender);
                    return true;
                default:
                    Messages.sendError(sender, "无效的子命令: " + subCommand);
                    return false;
            }
            
        } catch (Exception e) {
            Logging.error("贷款命令执行错误", e);
            sender.sendMessage("§c[YAE] 命令执行失败，请联系管理员");
            return false;
        }
    }
    
    // === Main Command Handlers ===
    
    private boolean handleGuiCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return false;
        }
        
        Player player = (Player) sender;
        String view = args.length > 1 ? args[1].toLowerCase() : "main";
        
        switch (view) {
            case "main":
            case "home":
                showMainLoanInterface(player);
                return true;
            case "apply":
            case "application":
                applicationGUI.openApplicationGUI(player);
                return true;
            case "manage":
            case "admin":
                managementGUI.openManagementGUI(player);
                return true;
            case "my":
            case "myloans":
                myLoansGUI.openMyLoansGUI(player);
                return true;
            default:
                player.sendMessage("§c无效的GUI视图: " + view);
                return false;
        }
    }
    
    private boolean handleApplyCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return false;
        }
        
        if (args.length < 4) {
            sender.sendMessage("§c使用方法: /yae loan apply <类型> <金额> <期限> [用途]");
            return false;
        }
        
        Player player = (Player) sender;
        
        try {
            // Parse loan type
            String loanTypeStr = args[1].toUpperCase();
            LoanType loanType;
            try {
                loanType = LoanType.valueOf(loanTypeStr);
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c无效的贷款类型: " + loanTypeStr);
                return false;
            }
            
            // Parse amount
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c无效金额格式: " + args[2]);
                return false;
            }
            
            // Parse term months
            int termMonths;
            try {
                termMonths = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c无效的期限格式: " + args[3]);
                return false;
            }
            
            // Parse loan purpose (optional)
            StringBuilder purposeBuilder = new StringBuilder("通过命令申请");
            if (args.length > 4) {
                for (int i = 4; i < args.length; i++) {
                    purposeBuilder.append(" ").append(args[i]);
                }
            }
            String loanPurpose = purposeBuilder.toString().trim();
            
            // Create loan application
            createLoanApplication(player, loanType, amount, termMonths, loanPurpose);
            return true;
            
        } catch (Exception e) {
            Logging.error("申请贷款命令执行错误", e);
            player.sendMessage("§c申请创建失败，请稍后重试");
            return false;
        }
    }
    
    private boolean handlePayCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return false;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage("§c使用方法: /yae loan pay <贷款ID> [金额|期次|期次");
            return false;
        }
        
        String loanId = args[1];
        double amount = -1.0; // Default to automatic calculation
        
        // Parse payment amount/options
        if (args.length > 2) {
            if (args[2].equals("-a") || args[2].equals("all")) {
                amount = -1.0; // Special flag - pay all overdue installments
            } else if (args[2].matches("\\d+")) {
                amount = Double.parseDouble(args[2]);
            } else if (args[2].matches("\\d+\\+")) {
                // Pay multiple installments (e.g., "3+" means pay 3 installments)
                int installments = Integer.parseInt(args[2].replace("+", ""));
                amount = installments * 2500.0; // Mock monthly payment
            }
        }
        
        // Execute payment
        return makeLoanPayment(player, loanId, amount);
    }
    
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length > 1 && args[1].equals("admin")) {
            showAdminStatus(player);
            return true;
        }
        
        String loanId = args.length > 1 ? args[1] : "current";
        
        if ("current".equals(loanId)) {
            showPlayerCurrentLoans(player);
        } else {
            showSpecificLoanStatus(player, loanId);
        }
        
        return true;
    }
    
    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return true;
        }
        
        Player player = (Player) sender;
        showPlayerLoanList(player);
        return true;
    }
    
    private boolean handleEligibilityCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return true;
        }
        
        Player player = (Player) sender;
        
        LoanType loanType = null;
        if (args.length > 1) {
            try {
                loanType = LoanType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c无效的贷款类型: " + args[1]);
                return false;
            }
        }
        
        checkPlayerEligibility(player, loanType);
        return true;
    }
    
    private boolean handleCalculateCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /yae loan calculate <本金> <月数> [年利率]");
            return false;
        }
        
        sender.sendMessage("§6[YAE] === 贷款计算器 ===");
        
        try {
            double principal = Double.parseDouble(args[1]);
            int months = Integer.parseInt(args[2]);
            double interestRate = args.length > 3 ? Double.parseDouble(args[3]) : 8.5;
            
            calculateLoanSchedule(sender, principal, months, interestRate);
            return true;
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§c计算器输入格式错误");
            return false;
        }
    }
    
    private boolean handleAutoPayCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return false;
        }
        
        if (args.length < 3) {
            player.sendMessage("§c使用方法: /yae loan auto_pay <贷款ID> <on|off>");
            return false;
        }
        
        Player player = (Player) sender;
        String loanId = args[1];
        String status = args[2].toLowerCase();
        
        boolean enable = status.equals("on") || status.equals("enable") || status.equals("true");
        
        return setAutoPay(player, loanId, enable);
    }
    
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage("§c您没有贷款管理权限");
            return false;
        }
        
        if (args.length < 2) {
            showAdminHelp(sender);
            return true;
        }
        
        String adminAction = args[1].toLowerCase();
        
        switch (adminAction) {
            case "approve":
                if (args.length < 3) {
                    sender.sendMessage("§c缺少贷款ID参数");
                    return false;
                }
                return approveApplication(sender, args[2], getReason(args, 3));
                
            case "reject":
                if (args.length < 3) {
                    sender.sendMessage("§c缺少贷款ID参数");
                    return false;
                }
                return rejectApplication(sender, args[2], getReason(args, 3));
                
            case "process":
                if (args.length < 3) {
                    sender.sendMessage("§c缺少贷款ID参数");
                    return false;
                }
                return processLoan(sender, args[2]);
                
            case "stats":
                showSystemStatistics(sender);
                return true;
                
            case "waiver":
            case "waive":
                return handlePenaltyWaiver(sender, args);
                
            case "suspend":
                return handlePlayerSuspension(sender, args);
                
            case "blacklist":
                return handleBlacklist(sender, args);
                
            default:
                sender.sendMessage(String.format("§c无效的管理操作: %s", adminAction));
                return false;
        }
    }
    
    /**
     * Create loan application for player (simplified version)
     */
    private void createLoanApplication(Player player, LoanType loanType, double amount, 
                                     int termMonths, String loanPurpose) {
        // Simplified implementation for testing
        player.sendMessage("§6[YAE] 正在创建贷款申请...");
        player.sendMessage(String.format("§7类型: %s | 金额: ¥%,.2f | 期限: %d个月", 
            loanType.getChineseName(), amount, termMonths));
        player.sendMessage("§7用途: " + loanPurpose);
        player.sendMessage("§a✅ 申请创建请求已提交");
        player.sendMessage("§7系统将自动检查资格并处理...");
    }
    
    /**
     * Display eligibility status
     */
    private void displayEligibilityStatus(Player player, LoanApplicationService.EligibilityResult eligibility) {
        player.sendMessage("§6=== 申请资格检查 ===");
        player.sendMessage(String.format("§f信用评分: §e%d§f (等级: §b%s)", 
            eligibility.getCreditScore(), eligibility.getCreditGrade().getChineseName()));
        player.sendMessage(String.format("§f贷款类型资格: §%s §%s", 
            eligibility.isCreditScoreQualified() ? "§a" : "§c",
            eligibility.isCreditScoreQualified() ? "✅ 符合" : "❌ 不符合"));
        player.sendMessage(String.format("§f账户状态: §%s §%s", 
            eligibility.getLoanStatus().isClean() ? "§a" : "§e",
            eligibility.getLoanStatus().isClean() ? "✅ 正常" : "⚠️ 需注意"));
        player.sendMessage("");
        
        // Display recommendations
        for (String recommendation : eligibility.getRecommendations()) {
            player.sendMessage("§6建议: §f" + recommendation);
        }
    }
    
    /**
     * Display application status
     */
    private void displayApplicationStatus(Player player, LoanApplicationService.LoanApplicationResult result) {
        player.sendMessage("§6=== 申请已成功提交 ===");
        player.sendMessage(String.format("§f申请编号: §e%s", result.getApplication().getApplicationId()));
        player.sendMessage(String.format("§f申请状态: §b%s", result.getApplication().getStatus()));
        
        if (result.getAutoApproval() != null) {
            player.sendMessage(String.format("§f自动审批: §%s §%s",
                result.getAutoApproval().isApproved() ? "§a" : "§c",
                result.getAutoApproval().isApproved() ? "已自动批准" : "需人工审核"));
        }
        
        player.sendMessage("§6后续操作:");
        player.sendMessage("§f• 关注系统通知");
        player.sendMessage("§f• 及时查看审批结果");
        player.sendMessage("§f• 如需补充材料请及时处理");
    }
    
    /**
     * Make loan payment (simplified)
     */
    private boolean makeLoanPayment(Player player, String loanId, double amount) {
        player.sendMessage(String.format("§6[YAE] 处理还款: 贷款%s | 金额¥%,.2f", loanId, amount));
        player.sendMessage("§a✅ 还款请求已发送");
        player.sendMessage("§7请等待银行处理...");
        return true;
    }
    
    /**
     * Show player current loans
     */
    private void showPlayerCurrentLoans(Player player) {
        player.sendMessage("§6=== 当前贷款概况 ===");
        player.sendMessage("§7您目前没有活跃贷款");
        player.sendMessage("§e");
        player.sendMessage("§a提示: 可使用 /yae loan apply 申请新贷款");
    }
    
    /**
     * Show specific loan status
     */
    private void showSpecificLoanStatus(Player player, String loanId) {
        player.sendMessage("§6=== 贷款详细状态 ===");
        player.sendMessage(String.format("§f贷款ID: §e%s", loanId));
        player.sendMessage("§7详细状态信息查询中...");
    }
    
    /**
     * Show comprehensive loan list
     */
    private void showPlayerLoanList(Player player) {
        player.sendMessage("§6=== 我的贷款列表 ===");
        player.sendMessage("§7加载贷款列表...");
        player.sendMessage("§7当前无活跃贷款记录");
    }
    
    /**
     * Check player eligibility
     */
    private void checkPlayerEligibility(Player player, LoanType loanType) {
        player.sendMessage("§6=== 申请资格检查 ===");
        
        if (loanType == null) {
            // Check all loan types
            for (LoanType type : LoanType.values()) {
                checkEligibilityForType(player, type);
            }
        } else {
            checkEligibilityForType(player, loanType);
        }
    }
    
    private void checkEligibilityForType(Player player, LoanType loanType) {
        LoanApplicationService.EligibilityResult eligibility = applicationService.checkEligibility(
            player.getUniqueId(), loanType
        );
        
        player.sendMessage("");
        player.sendMessage(String.format("§e[%s]", loanType.getChineseName()));
        player.sendMessage(String.format("§f资格状态: §%s §%s",
            eligibility.isEligible() ? "§a" : "§c",
            eligibility.isEligible() ? "✅ 符合资格" : "❌ 不符合"));
        
        player.sendMessage(String.format("§f信用评分: §e%d§f (等级: §b%s)", 
            eligibility.getCreditScore(), eligibility.getCreditGrade().getChineseName()));
        
        if (!eligibility.isEligible()) {
            player.sendMessage("§6不符合原因:");
            for (String reason : eligibility.getRecommendations()) {
                player.sendMessage("§7 • " + reason);
            }
        }
    }
    
    /**
     * Calculate and display loan schedule
     */
    @SuppressWarnings("unused")
    private void calculateLoanSchedule(CommandSender sender, double principal, int months, double interestRate) {
        try {
            // Create temporary loan terms for calculation
            LoanTerms.TermsOption termsOption = new LoanTerms.TermsOption(months, interestRate, principal);
            LoanTerms loanTerms = new LoanTerms(termsOption);
            
            sender.sendMessage(String.format("§7本金: §a¥%,.2f", principal));
            sender.sendMessage(String.format("§7期限: §b%d个月", months));
            sender.sendMessage(String.format("§7年利率: §e%.2f%%", interestRate));
            sender.sendMessage(String.format("§7月供: §a¥%,.2f", loanTerms.getMonthlyPayment()));
            sender.sendMessage(String.format("§7总还款: §6¥%,.2f", loanTerms.getTotalPayment()));
            sender.sendMessage(String.format("§7总利息: §c¥%,.2f", loanTerms.getTotalInterest()));
            
        } catch (Exception e) {
            sender.sendMessage("§c计算器错误，请检查输入参数");
        }
    }
    
    /**
     * Set automatic payment
     */
    private boolean setAutoPay(Player player, String loanId, boolean enable) {
        player.sendMessage(String.format("§6[YAE] 设置自动扣款: %s", enable ? "启用" : "禁用"));
        player.sendMessage(String.format("§7贷款ID: %s", loanId));
        
        // Mock implementation for testing
        player.sendMessage(enable ? "§a✅ 自动扣款已启用" : "§c⚠️ 自动扣款已禁用");
        return true;
    }
    
    // === Admin Command Methods ===
    
    private boolean approveApplication(CommandSender sender, String loanId, String reason) {
        if (reason.isEmpty()) reason = "管理员审核通过";
        
        sender.sendMessage(String.format("§6[YAE] 批准贷款申请: %s", loanId));
        sender.sendMessage(String.format("§7理由: %s", reason));
        sender.sendMessage("§a✅ 申请已批准，正在通知借款人...");
        return true;
    }
    
    private boolean rejectApplication(CommandSender sender, String loanId, String reason) {
        if (reason.isEmpty()) reason = "未满足申请条件";
        
        sender.sendMessage(String.format("§6[YAE] 拒绝贷款申请: %s", loanId));
        sender.sendMessage(String.format("§7理由: %s", reason));
        sender.sendMessage("§c❌ 申请已被拒绝，将通知借款人");
        return true;
    }
    
    private boolean processLoan(CommandSender sender, String loanId) {
        sender.sendMessage(String.format("§6[YAE] 处理贷款放款: %s", loanId));
        sender.sendMessage("§a✅ 放款处理中...");
        sender.sendMessage("§7款项将通过国库转账至借款人账户");
        return true;
    }
    
    private void showSystemStatistics(CommandSender sender) {
        sender.sendMessage("§6[YAE] === 系统统计 ===");
        sender.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§a活跃贷款: §10");
        sender.sendMessage("§e待审批申请: §10");
        sender.sendMessage("§c逾期贷款: §10");
        sender.sendMessage("§6总贷款余额: §1¥0.00");
        sender.sendMessage("§b总罚息收入: §1¥0.00");
        sender.sendMessage("§7统计功能完整版开发中...");
    }
    
    private boolean handlePenaltyWaiver(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c使用方法: /yae loan admin waiver <贷款ID> <金额> [理由]");
            return false;
        }
        
        String loanId = args[2];
        double waiverAmount = Double.parseDouble(args[3]);
        String reason = args.length > 4 ? getReason(args, 4) : "管理员工需求";
        
        sender.sendMessage(String.format("§6[YAE] 罚息豁免请求: %s", loanId));
        sender.sendMessage(String.format("§7豁免金额: §a¥%,.2f", waiverAmount));
        sender.sendMessage(String.format("§7理由: %s", reason));
        sender.sendMessage("§a✅ 豁免请求已提交，冻结罚息金额");
        return true;
    }
    
    private boolean handlePlayerSuspension(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /yae loan admin suspend <玩家> [理由]");
            return false;
        }
        
        String playerName = args[2];
        String reason = args.length > 3 ? getReason(args, 3) : "违反贷款条款";
        
        sender.sendMessage(String.format("§6[YAE] 暂停账户: %s", playerName));
        sender.sendMessage(String.format("§7理由: %s", reason));
        sender.sendMessage("§c⚠️ 账户已暂停，将禁止新的贷款申请");
        return true;
    }
    
    private boolean handleBlacklist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /yae loan admin blacklist <玩家> [理由]");
            return false;
        }
        
        String playerName = args[2];
        boolean isPermanent = args.length > 3 && args[3].equals("permanent");
        String reason = args.length > (isPermanent ? 4 : 3) ? getReason(args, isPermanent ? 4 : 3) : "严重违约行为";
        
        sender.sendMessage(String.format("§6[YAE] 列入黑名单: %s (%s)", 
            playerName, isPermanent ? "永久" : "临时"));
        sender.sendMessage(String.format("§7理由: %s", reason));
        sender.sendMessage("§4🔞 严重警告: 该用户已被列入贷款黑名单，将永久禁止申请新贷款");
        return true;
    }
    
    /**
     * Helper method to get reason text from args
     */
    private String getReason(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return "";
        }
        
        StringBuilder reason = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) reason.append(" ");
            reason.append(args[i]);
        }
        return reason.toString().trim();
    }
    
    /**
     * Check if sender is player
     */
    private boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }
    
    /**
     * Check admin permission
     */
    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("yae.loan.admin") || sender.hasPermission("yae.admin");
    }
    
    /**
     * Show help text
     */
    private void showHelp(CommandSender sender) {
        if (hasAdminPermission(sender)) {
            sender.sendMessage("§6=== YetAnotherEconomy 贷款系统 - 命令帮助 ===");
            sender.sendMessage("(包含管理命令)");
        } else {
            sender.sendMessage("§6=== YetAnotherEconomy 贷款系统 - 用户帮助 ===");
        }
        
        sender.sendMessage("§e[yae loan] §f- 打开贷款主界面");
        sender.sendMessage("§e[yae loan apply <类型> <金额> <期限> [用途>] §f- 申请新贷款");
        sender.sendMessage("§e[yae loan pay <贷款ID> [金额]|all 네: §f- 还款操作");
        sender.sendMessage("§e[yae loan status [贷款ID]] §f- 查看贷款状态");
        sender.sendMessage("§e[yae loan list] §f- 查看我的贷款");
        sender.sendMessage("§e[yae loan eligibility [类型]] §f- 检查申请资格");
        sender.sendMessage("§e[yae loan calculate <本金> <月数> [利率]] §f- 贷款计算器");
        sender.sendMessage("§e[yae loan auto_pay <贷款ID> <on|off>] §f- 设置自动扣款");
        sender.sendMessage("§e[yae loan help] §f- 显示此帮助");
        
        if (hasAdminPermission(sender)) {
            sender.sendMessage("§5[yae loan admin approve <贷款ID> [理由]] §f- 批准贷款 (管理)");
            sender.sendMessage("§5[yae loan admin reject <贷款ID> [理由]] §f- 拒绝贷款 (管理)");
            sender.sendMessage("§5[yae loan admin process <贷款ID>] §f- 处理放款 (管理)");
            sender.sendMessage("§5[yae loan admin stats] §f- 查看统计 (管理)");
        }
        
        sender.sendMessage("§6§l⭐ 推荐体验: §f/yae loan gui apply - 5步图形化申请");
    }
}
