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
public class LoanCommand implements CommandExecutor {
    
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
    
    public LoanCommand(YAECore plugin) {
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
        
        // Check if services are available
        if (!areServicesAvailable(sender)) {
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        try {
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
                    Messages.sendError(sender, "loan.error.invalid_subcommand");
                    return false;
            }
            
        } catch (Exception e) {
            Logging.error("贷款命令执行错误", e);
            Messages.sendError(sender, "loan.error.command_execution_failed");
            return false;
        }
    }
    
    // === Main Command Handlers ===
    
    private boolean handleGuiCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            Messages.sendError(sender, "loan.error.player_only");
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
            case "miloans":
                myLoansGUI.openMyLoansGUI(player);
                return true;
            default:
                Messages.sendError(player, "loan.error.invalid_gui_view");
                return false;
        }
    }
    
    private boolean handleApplyCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            Messages.sendError(sender, "loan.error.player_only");
            return false;
        }
        
        if (args.length < 4) {
            Messages.sendError(sender, "loan.error.insufficient_args_apply");
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
                Messages.sendError(player, String.format("loan.error.invalid_loan_type", loanTypeStr));
                return false;
            }
            
            // Parse amount
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                Messages.sendError(player, "loan.error.invalid_amount_format");
                return false;
            }
            
            // Parse term months
            int termMonths;
            try {
                termMonths = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                Messages.sendError(player, "loan.error.invalid_term_format");
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
            Messages.sendError(player, "loan.error.application_creation_failed");
            return false;
        }
    }
    
    private boolean handlePayCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            Messages.sendError(sender, "loan.error.player_only");
            return false;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            Messages.sendError(player, "loan.error.insufficient_args_pay");
            return false;
        }
        
        String loanId = args[1];
        double amount = -1.0; // Default to current monthly payment
        
        // Parse amount if provided
        if (args.length > 2) {
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                if ("-a".equals(args[2]) || "all".equals(args[2].toLowerCase())) {
                    amount = -1.0; // Special flag for all months
                } else {
                    Messages.sendError(player, "loan.error.invalid_payment_amount");
                    return false;
                }
            }
        }
        
        // Execute payment
        return makeLoanPayment(player, loanId, amount);
    }
    
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            Messages.sendError(sender, "loan.error.player_only");
            return true;
        }
        
        Player player = (Player) sender;
        
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
            Messages.sendError(sender, "loan.error.player_only");
            return true;
        }
        
        Player player = (Player) sender;
        showPlayerLoanList(player);
        return true;
    }
    
    private boolean handleEligibilityCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            Messages.sendError(sender, "loan.error.player_only");
            return true;
        }
        
        Player player = (Player) sender;
        
        LoanType loanType = null;
        if (args.length > 1) {
            try {
                loanType = LoanType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                Messages.sendError(player, String.format("loan.error.invalid_loan_type", args[1]));
                return false;
            }
        }
        
        checkPlayerEligibility(player, loanType);
        return true;
    }
    
    private boolean handleCalculateCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.sendError(sender, "loan.error.insufficient_args_calculate");
            return false;
        }
        
        sender.sendMessage("&e[YAE]&f=== 贷款计算器 ===");
        
        try {
            double principal = Double.parseDouble(args[1]);
            int months = Integer.parseInt(args[2]);
            double interestRate = args.length > 3 ? Double.parseDouble(args[3]) : 8.5;
            
            calculateLoanSchedule(sender, principal, months, interestRate);
            return true;
            
        } catch (NumberFormatException e) {
            Messages.sendError(sender, "loan.error.invalid_format_calculator");
            return false;
        }
    }
    
    private boolean handleAutoPayCommand(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) {
            Messages.sendError(sender, "loan.error.player_only");
            return false;
        }
        
        if (args.length < 3) {
            Messages.sendError(sender, "loan.error.insufficient_args_autopay");
            return false;
        }
        
        Player player = (Player) sender;
        String loanId = args[1];
        String status = args[2].toLowerCase();
        
        boolean enable = status.equals("enable") || status.equals("on") || status.equals("true");
        
        return setAutoPay(player, loanId, enable);
    }
    
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            Messages.sendError(sender, "loan.error.no_admin_permission");
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
                    Messages.sendError(sender, "loan.error.missing_loan_id");
                    return false;
                }
                return approveApplication(sender, args[2], getReason(args, 3));
                
            case "reject":
                if (args.length < 3) {
                    Messages.sendError(sender, "loan.error.missing_loan_id");
                    return false;
                }
                return rejectApplication(sender, args[2], getReason(args, 3));
                
            case "process":
                if (args.length < 3) {
                    Messages.sendError(sender, "loan.error.missing_loan_id");
                    return false;
                }
                return processLoan(sender, args[2]);
                
            case "stats":
            case "statistics":
                showSystemStatistics(sender);
                return true;
                
            case "waiver":
                return handlePenaltyWaiver(sender, args);
                
            case "suspend":
                return handlePlayerSuspension(sender, args);
                
            case "blacklist":
                return handleBlacklist(sender, args);
                
            default:
                Messages.sendError(sender, String.format("loan.admin.error.invalid_action", adminAction));
                return false;
        }
    }
    
    /**
     * Create loan application for player
     */
    private void createLoanApplication(Player player, LoanType loanType, double amount, 
                                     int termMonths, String loanPurpose) {
        try {
            Messages.sendMessage(player, "loan.application.creating");
            
            // Validate eligibility first
            LoanApplicationService.EligibilityResult eligibility = applicationService.checkEligibility(
                player.getUniqueId(), loanType
            );
            
            if (!eligibility.isEligible()) {
                Messages.sendMessage(player, "loan.application.not_eligible");
                displayEligibilityStatus(player, eligibility);
                return;
            }
            
            // Validate loan amount
            LoanApplicationService.LoanValidationResult validation = applicationService.validateLoanAmount(
                player.getUniqueId(), loanType, amount, termMonths
            );
            
            if (!validation.isValid()) {
                Messages.sendMessage(player, "loan.application.invalid_terms");
                for (String message : validation.getMessages()) {
                    player.sendMessage("&c" + message);
                }
                return;
            }
            
            // Create application
            LoanApplication application = new LoanApplication(player.getUniqueId());
            application.setLoanType(loanType);
            application.setRequestedAmount(amount);
            application.setTermMonths(termMonths);
            application.setLoanPurpose(loanPurpose);
            application.setCreditScore(eligibility.getCreditScore());
            application.setCreditGrade(eligibility.getCreditGrade());
            
            // Submit application
            LoanApplicationService.LoanApplicationResult result = applicationService.submitApplication(application);
            
            if (result.isSuccess()) {
                Messages.sendMessage(player, "loan.application.submitted");
                displayApplicationStatus(player, result);
            } else {
                Messages.sendError(player, "loan.application.failure_reason", result.getErrorMessage());
            }
            
        } catch (Exception e) {
            Logging.error("贷款申请创建错误", e);
            Messages.sendError(player, "loan.error.application_creation_failed");
        }
    }
    
    /**
     * Display eligibility status for player
     */
    private void displayEligibilityStatus(@NotNull Player player, LoanApplicationService.EligibilityResult eligibility) {
        player.sendMessage("§6=== 申请资格检查 ===");
        player.sendMessage(String.format("§f信用评分: §e%d§f (等级: §b%s)", 
            eligibility.getCreditScore(), eligibility.getCreditGrade().getChineseName()));
        player.sendMessage(String.format("§f贷款类型资格: §%s", 
            eligibility.isCreditScoreQualified() ? "§a✅ 符合" : "§c❌ 不符合"));
        player.sendMessage(String.format("§f账户状态: §%s", 
            eligibility.getLoanStatus().isClean() ? "§a✅ 正常" : "§c⚠️ 需注意"));
        player.sendMessage("");
        
        // Display recommendations
        for (String recommendation : eligibility.getRecommendations()) {
            player.sendMessage("§6建议: §f" + recommendation);
        }
    }
    
    /**
     * Display application status
     */
    private void displayApplicationStatus(@NotNull Player player, LoanApplicationService.LoanApplicationResult result) {
        player.sendMessage("”); ");
        player.sendMessage("§6=== 申请已成功提交 ===");
        player.sendMessage(String.format("§f申请编号: §e%s", result.getApplication().getApplicationId()));
        player.sendMessage(String.format("§f申请状态: §b%s", 
            translateStatus(result.getApplication().getStatus().name())));
        
        if (result.getAutoApproval() != null) {
            player.sendMessage(String.format("§f自动审批: %s%s",
                result.getAutoApproval().isApproved() ? "§a" : "§c",
                result.getAutoApproval().isApproved() ? "已自动批准" : "需人工审核"));
        }
        
        player.sendMessage("§6后续操作: ");
        player.sendMessage("§f• 关注系统通知");
        player.sendMessage("§f• 及时查看审批结果");
        player.sendMessage("§f• 如需补充材料请及时处理");
    }
    
    /**
     * Make loan payment for player
     */
    private boolean makeLoanPayment(Player player, String loanId, double amount) {
        try {
            // Auto payment - pay current monthly amount
            if (amount < 0) {
                amount = getCurrentMonthlyPayment(player.getUniqueId(), loanId);
                if (amount <= 0) {
                    Messages.sendError(player, "loan.pay.no_payment_due");
                    return false;
                }
            }
            
            // Validate payment amount
            if (amount <= 0) {
                Messages.sendError(player, "loan.pay.invalid_amount");
                return false;
            }
            
            Messages.sendMessage(player, "loan.pay.processing");
            
            // Process payment
            CompletableFuture<RepaymentService.PaymentResult> resultFuture = repaymentService.makeManualPayment(
                player, loanId, amount, RepaymentService.PaymentMethod.BANK_TRANSFER
            );
            
            RepaymentService.PaymentResult result = resultFuture.join();
            
            if (result.isSuccess()) {
                displayPaymentSuccess(player, result);
                return true;
            } else {
                Messages.sendError(player, "loan.pay.fail_reason", result.getErrorMessage());
                return false;
            }
            
        } catch (Exception e) {
            Logging.error("支付处理错误", e);
            Messages.sendError(player, "loan.pay.processing_error");
            return false;
        }
    }
    
    /**
     * Show loan summary for player
     */
    private void showPlayerCurrentLoans(Player player) {
        // Implementation to get current loans for player
        // This would integrate with existing loan tracking
        
        player.sendMessage("§6=== 当前贷款概况 ===");
        // Display current loan summary
        Messages.sendInfo(player, "loan.status.no_active_loans");
    }
    
    /**
     * Show specific loan status
     */
    private void showSpecificLoanStatus(Player player, String loanId) {
        player.sendMessage("§6=== 贷款详细状态 ===");
        player.sendMessage(String.format("§f贷款ID: §e%s", loanId));
        Messages.sendInfo(player, "loan.status.details_processing");
    }
    
    /**
     * Show comprehensive loan list for player
     */
    private void showPlayerLoanList(Player player) {
        player.sendMessage("§6=== 我的贷款列表 ===");
        Messages.sendInfo(player, "loan.list.loading");
        
        // Implementation to fetch and display player's loans
        // This would include active, pending, and historical loans
    }
    
    /**
     * Check player eligibility for loan
     */
    private void checkPlayerEligibility(Player player, LoanType loanType) {
        try {
            if (loanType == null) {
                player.sendMessage("§6=== 申请资格检查 ===");
                player.sendMessage("§7检查您对所有贷款类型的资格...");
                
                for (LoanType type : LoanType.values()) {
                    checkEligibilityForType(player, type);
                }
            } else {
                checkEligibilityForType(player, loanType);
            }
        } catch (Exception e) {
            Logging.error("资格检查错误", e);
            Messages.sendError(player, "loan.eligibility.check_failed");
        }
    }
    
    private void checkEligibilityForType(Player player, LoanType loanType) {
        LoanApplicationService.EligibilityResult eligibility = applicationService.checkEligibility(
            player.getUniqueId(), loanType
        );
        
        player.sendMessage("");
        player.sendMessage(String.format("§e[%s]", loanType.getChineseName()));
        player.sendMessage(String.format("§f资格状态: %s%s",
            eligibility.isEligible() ? "§a" : "§c",
            eligibility.isEligible() ? "✅ 符合资格" : "❌ 不符合"));
        
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
    private void calculateLoanSchedule(CommandSender sender, double principal, int months, double interestRate) {
        try {
            // Create temporary loan terms for calculation
            LoanTerms.TermsOption termsOption = new LoanTerms.TermsOption(months, interestRate, principal);
            LoanTerms loanTerms = new LoanTerms(termsOption);
            
            sender.sendMessage(String.format("§7本金: §a¥%,.2f", principal));
            sender.sendMessage(String.format("§7期限(§m: §b%d个§m", months));
            sender.sendMessage(String.format("§7年利率: §e%.2f%%", interestRate));
            sender.sendMessage(String.format("§7月供: §a¥%,.2f", loanTerms.getMonthlyPayment()));
            sender.sendMessage(String.format("§7总还款: §6¥%,.2f", loanTerms.getTotalPayment()));
            sender.sendMessage(String.format("§7总利息: §c¥%,.2f", loanTerms.getTotalInterest()));
            
            // Show alternative payment options
            sender.sendMessage("§");
            loanTerms.getEarlyPayoffOptions().stream()
                .limit(3)
                .forEach(option -> sender.sendMessage(option.getBreakdown()));
            
        } catch (Exception e) {
            Logging.error("贷款计算器错误", e);
            Messages.sendError(sender, "loan.calculator.error");
        }
    }
    
    /**
     * Set automatic payment for loan
     */
    private boolean setAutoPay(Player player, String loanId, boolean enable) {
        try {
            CompletableFuture<Boolean> result = repaymentService.setAutoPay(loanId, enable, RepaymentService.PaymentMethod.BANK_TRANSFER);
            
            boolean success = result.join();
            
            if (success) {
                Messages.sendMessage(player, enable ? "loan.autopay.enabled" : "loan.autopay.disabled");
                Messages.sendInfo(player, String.format("loan.autopay.status_for_loan", loanId));
            } else {
                Messages.sendError(player, "loan.autopay.failed_to_set");
            }
            
            return success;
            
        } catch (Exception e) {
            Logging.error("自动扣款设置错误", e);
            Messages.sendError(player, "loan.autopay.setting_error");
            return false;
        }
    }
    
    // === Admin Commands ===
    
    private boolean approveApplication(CommandSender sender, String loanId, String reason) {
        try {
            Messages.sendMessage(sender, String.format("loan.admin.approving", loanId));
            
            boolean approved = approveLoanApplication(loanId, sender.getName(), reason);
            
            if (approved) {
                Messages.sendMessage(sender, "loan.admin.approval_success");
                sender.sendMessage("§a✅ 贷款申请已批准");
            } else {
                Messages.sendError(sender, "loan.admin.approval_failed");
            }
            
            return approved;
            
        } catch (Exception e) {
            Logging.error("审批申请错误", e);
            Messages.sendError(sender, "loan.admin.approval_error");
            return false;
        }
    }
    
    private boolean rejectApplication(CommandSender sender, String loanId, String reason) {
        try {
            Messages.sendMessage(sender, String.format("loan.admin.rejecting", loanId));
            
            boolean rejected = rejectLoanApplication(loanId, sender.getName(), reason);
            
            if (rejected) {
                Messages.sendMessage(sender, "loan.admin.rejection_success");
            } else {
                Messages.sendError(sender, "loan.admin.rejection_failed");
            }
            
            return rejected;
            
        } catch (Exception e) {
            Logging.error("拒批申请错误", e);
            Messages.sendError(sender, "loan.admin.rejection_error");
            return false;
        }
    }
    
    private boolean processLoan(CommandSender sender, String loanId) {
        try {
            Messages.sendMessage(sender, String.format("loan.admin.processing_disbursement", loanId));
            
            boolean processed = processLoanDisbursement(loanId, sender.getName());
            
            if (processed) {
                Messages.sendMessage(sender, "loan.admin.disbursement_success");
            } else {
                Messages.sendError(sender, "loan.admin.disbursement_failed");
            }
            
            return processed;
            
        } catch (Exception e) {
            Logging.error("处理放款错误", e);
            Messages.sendError(sender, "loan.admin.disbursement_error");
            return false;
        }
    }
    
    private void showSystemStatistics(CommandSender sender) {
        // Implementation to fetch and display system statistics
        sender.sendMessage("§6=== 贷款系统统计 ===");
        sender.sendMessage("§7统计功能开发中...");
        Messages.sendInfo(sender, "loan.admin.stats_loading");
    }
    
    private boolean handlePenaltyWaiver(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Messages.sendError(sender, "loan.admin.waiver.insufficient_args");
            return false;
        }
        
        try {
            String loanId = args[2];
            double waiverAmount = Double.parseDouble(args[3]);
            String reason = getReason(args, 4);
            
            if (reason.isEmpty()) {
                Messages.sendError(sender, "loan.admin.waiver.missing_reason");
                return false;
            }
            
            PenaltyWaiverResult result = overdueService.waivePenalties(loanId, waiverAmount, reason, sender.getName());
            
            if (result.isSuccess()) {
                Messages.sendMessage(sender, String.format("loan.admin.waiver.success",
                    waiverAmount, loanId, result.getRemainingAmount()));
                return true;
            } else {
                Messages.sendError(sender, String.format("loan.admin.waiver.failed", result.getErrorMessage()));
                return false;
            }
            
        } catch (NumberFormatException e) {
            Messages.sendError(sender, "loan.admin.waiver.invalid_amount");
            return false;
        } catch (Exception e) {
            Logging.error("罚息豁免错误", e);
            Messages.sendError(sender, "loan.admin.waiver.error");
            return false;
        }
    }
    
    private boolean handlePlayerSuspension(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.sendError(sender, "loan.admin.suspend.insufficient_args");
            return false;
        }
        
        String playerName = args[2];
        String reason = getReason(args, 3);
        
        if (reason.isEmpty()) {
            Messages.sendError(sender, "loan.admin.suspend.missing_reason");
            return false;
        }
        
        try {
            UUID targetPlayerId = null; // Retrieve from database
            
            CompletableFuture<Boolean> result = overdueService.suspendBorrower(targetPlayerId, reason, sender.getName());
            boolean suspended = result.join();
            
            if (suspended) {
                Messages.sendMessage(sender, String.format("loan.admin.suspend.success", playerName));
            } else {
                Messages.sendError(sender, "loan.admin.suspend.failed");
            }
            
            return suspended;
            
        } catch (Exception e) {
            Logging.error("暂停账户错误", e);
            Messages.sendError(sender, "loan.admin.suspend.error");
            return false;
        }
    }
    
    private boolean handleBlacklist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.sendError(sender, "loan.admin.blacklist.insufficient_args");
            return false;
        }
        
        String playerName = args[2];
        boolean isPermanent = args.length > 3 && (args[3].equals("permanent") || args[3].equals("true"));
        String reason = getReason(args, isPermanent ? 4 : 3);
        
        if (reason.isEmpty()) {
            Messages.sendError(sender, "loan.admin.blacklist.missing_reason");
            return false;
        }
        
        try {
            UUID targetPlayerId = null; // Retrieve from database
            
            CompletableFuture<Boolean> result = overdueService.blacklistBorrower(targetPlayerId, reason, isPermanent, sender.getName());
            boolean blacklisted = result.join();
            
            if (blacklisted) {
                Messages.sendMessage(sender, String.format("loan.admin.blacklist.success",
                    playerName, isPermanent ? "永久" : "临时"));
            } else {
                Messages.sendError(sender, "loan.admin.blacklist.failed");
            }
            
            return blacklisted;
            
        } catch (Exception e) {
            Logging.error("黑名单操作错误", e);
            Messages.sendError(sender, "loan.admin.blacklist.error");
            return false;
        }
    }
    
    // === Helper Methods ===
    
    /**
     * Check if services are available
     */
    private boolean areServicesAvailable(CommandSender sender) {
        if (applicationService == null || repaymentService == null || overdueService == null) {
            Messages.sendError(sender, "loan.error.services_unavailable");
            return false;
        }
        return true;
    }
    
    /**
     * Check if sender is a player
     */
    private boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }
    
    /**
     * Check if player has admin permission
     */
    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("yae.loan.admin") || sender.hasPermission("yae.admin");
    }
    
    /**
     * Show main loan interface
     */
    private void showMainLoanInterface(Player player) {
        player.sendMessage("§6=== 贷款服务中心 ===");
        player.sendMessage("");
        player.sendMessage("§7可用的贷款服务：");
        player.sendMessage("§e► §f/yae loan apply <类型> <金额> <月数> [用途] - 申请新贷款");
        player.sendMessage("§e► §f/yae loan gui apply - 图形化贷款申请");
        player.sendMessage("§e► §f/yae loan list - 查看我的贷款");
        player.sendMessage("§e► §f/yae loan pay <贷款ID> [金额] - 还款");
        player.sendMessage("§e► §f/yae loan calculator <本金> <月数> [利率] - 贷款计算器");
        player.sendMessage("§a§l推荐使用: §f/yae loan gui apply - 完整的5步图形化申请");
    }
    
    /**
     * Get reason string from args starting at index
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
     * Translate status to Chinese
     */
    private String translateStatus(String status) {
        switch (status.toUpperCase()) {
            case "NEW": return "新建";
            case "PENDING": return "待审核";
            case "PENDING_FORMER_REVIEW": return "人工审核中";
            case "AUTO_APPROVED": return "自动批准";
            case "APPROVED": return "已批准";
            case "REJECTED": return "已拒绝";
            case "CANCELLED": return "已取消";
            case "DISBURSED": return "已放款";
            default: return status;
        }
    }
    
    /**
     * Get current monthly payment for loan
     */
    private double getCurrentMonthlyPayment(UUID playerId, String loanId) {
        // Implementation to get current monthly payment amount
        // This would query loan details
        return 2000.0; // Mock implementation
    }
    
    // === Mock administrative methods (implement with actual database logic) ===
    
    private boolean approveLoanApplication(String loanId, String approvedBy, String reason) {
        // Implementation to approve loan application
        return true; // Mock
    }
    
    private boolean rejectLoanApplication(String loanId, String rejectedBy, String reason) {
        // Implementation to reject loan application
        return true; // Mock
    }
    
    private boolean processLoanDisbursement(String loanId, String processedBy) {
        // Implementation to process loan disbursement
        return true; // Mock
    }
    
    private void showHelp(CommandSender sender) {
        if (hasAdminPermission(sender)) {
            sender.sendMessage("§6=== YetAnotherEconomy 贷款系统 - 命令帮助 ===");
        } else {
            sender.sendMessage("§6=== YetAnotherEconomy 贷款系统 - 用户帮助 ===");
        }
        
        sender.sendMessage("§e[yae loan]");
        sender.sendMessage("§f打开贷款主界面");
        sender.sendMessage("§e[yae loan apply <类型> <金额> <期限> [用途]]");
        sender.sendMessage("§f申请新贷款");
        sender.sendMessage("§e[yae loan pay <贷款ID> [金额] | -all]");
        sender.sendMessage("§f还款操作");
        sender.sendMessage("§e[yae loan status [贷款ID]]");
        sender.sendMessage("§f查看贷款状态");
        sender.sendMessage("§e[yae loan list]");
        sender.sendMessage("§f列出我的贷款");
        sender.sendMessage("§e[yae loan eligibility [类型]]");
        sender.sendMessage("§f检查申请资格");
        sender.sendMessage("§e[yae loan calculate <本金> <月数> [利率]]");
        sender.sendMessage("§f贷款计算器");
        sender.sendMessage("§e[yae loan auto_pay <贷款ID> <on|off>]");
        sender.sendMessage("§f设置自动扣款");
        sender.sendMessage("§e[yae loan help]");
        sender.sendMessage("§f显示此帮助");
        
        if (hasAdminPermission(sender)) {
            sender.sendMessage("§c[yae loan admin approve <贷款ID> [理由]]");
            sender.sendMessage("§f批准贷款申请 (管理)");
            sender.sendMessage("§c[yae loan admin reject <贷款ID> [理由]]");
            sender.sendMessage("§f拒绝贷款申请 (管理)");
            sender.sendMessage("§c[yae loan admin process <贷款ID>]");
            sender.sendMessage("§f处理放款 (管理)");
            sender.sendMessage("§c[yae loan admin stats]");
            sender.sendMessage("§f查看系统统计 (管理)");
        }
        
        sender.sendMessage("§6§l⭐ 完整体验: /yae loan gui apply - 5步图形化申请");
    }
    
    /**
     * Show admin-only help
     */
    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage("§6=== 贷款管理命令帮助 ===");
        sender.sendMessage("§5[yae loan admin approve <贷款ID> [理由]]");
        sender.sendMessage("§7批准贷款申请");
        sender.sendMessage("§5[yae loan admin reject <贷款ID> [理由]]");
        sender.sendMessage("§7拒绝贷款申请");
        sender.sendMessage("§5[yae loan admin process <贷款ID>]");
        sender.sendMessage("§7处理放款");
        sender.sendMessage("§5[yae loan admin stats]");
        sender.sendMessage("§7查看系统统计");
        sender.sendMessage("§5[yae loan admin waiver <贷款ID> <金额> [理由]]");
        sender.sendMessage("§7豁免罚息");
        sender.sendMessage("§5[yae loan admin suspend <玩家> [理由]]");
        sender.sendMessage("§7暂停账户");
        sender.sendMessage("§5[yae loan admin blacklist <玩家> [理由]]");
        sender.sendMessage("§7列入黑名单");
    }
}
