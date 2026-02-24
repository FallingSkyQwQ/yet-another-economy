package com.yae.api.loan.gui;

import com.yae.YetAnotherEconomy;
import com.yae.api.credit.LoanType;
import com.yae.api.gui.CustomGUI;
import com.yae.api.gui.GUIManager;
import com.yae.api.gui.ItemBuilder;
import com.yae.api.loan.*;
import com.yae.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Complete Loan Application GUI with all navigation and helper methods
 */
public class LoanApplicationGUI_Complete extends LoanApplicationGUI {
    
    public LoanApplicationGUI_Complete(YetAnotherEconomy plugin) {
        super(plugin);
    }
    
    @Override
    protected void addNavigationButtons(Player player, ApplicationStep currentStep) {
        int maxSteps = ApplicationStep.values().length;
        int currentStepNum = currentStep.getStepNumber();
        
        // Previous step (if not first)
        if (currentStepNum > 1) {
            ApplicationStep prevStep = ApplicationStep.values()[currentStepNum - 2];
            setItem(47, new ItemBuilder(Material.ARROW)
                .setName(String.format("&e← 返回第%d步: %s", prevStep.getStepNumber(), prevStep.getTitle()))
                .setLore(String.format("&7%s", prevStep.getDescription())).build());
        } else {
            setItem(47, new ItemBuilder(Material.BARRIER)
                .setName("&c取消申请")
                .setLore("&7结束申请流程").build());
        }
        
        // Next step (if not last)
        if (currentStepNum < maxSteps) {
            ApplicationStep nextStep = ApplicationStep.values()[currentStepNum];
            setItem(51, new ItemBuilder(Material.ARROW)
                .setName(String.format("&a进入第%d步: %s →", nextStep.getStepNumber(), nextStep.getTitle()))
                .setLore(String.format("&7%s", nextStep.getDescription())).build());
        } else {
            setItem(51, new ItemBuilder(Material.COMPASS)
                .setName("&6提交申请")
                .setLore("&7确认所有信息并提交").build());
        }
        
        // Progress indicator
        setItem(48, new ItemBuilder(Material.DAYLIGHT_DETECTOR)
            .setName(String.format("&b进度: %d/%d", currentStepNum, maxSteps))
            .setLore(
                "",
                String.format("&f当前步骤: &e%s", currentStep.getTitle()),
                String.format("&f下一步: %s", 
                    currentStepNum < maxSteps ? ApplicationStep.values()[currentStepNum].getTitle() : "完成"),
                "",
                "&6继续保持，很快就可以完成了！"
            ).build());
        
        setItem(50, new ItemBuilder(Material.REDSTONE_DUST)
            .setName("&7[返回主界面]")
            .setLore("&7关闭申请表返回主菜单").build());
    }
}

/**
 * Administrative GUI for loan management
 */
public class LoanManagementGUI extends CustomGUI {
    
    private static final int GUI_SIZE = 54;
    private static final String TITLE = "&6贷款管理";
    
    private final LoanApplicationService applicationService;
    private Map<String, Object> currentFilters = new HashMap<>();
    
    public LoanManagementGUI(YetAnotherEconomy plugin) {
        super(plugin, GUI_SIZE, TITLE);
        this.applicationService = plugin.getService(LoanApplicationService.class);
    }
    
    /**
     * 打开贷款管理界面
     */
    public void openManagementGUI(Player player) {
        if (!hasPermission(player, "yae.loan.manage")) {
            Messages.sendMessage(player, "loan.management.no_permission");
            return;
        }
        
        if (applicationService == null) {
            Messages.sendMessage(player, "loan.management.service_unavailable");
            return;
        }
        
        openGUI(player);
        Messages.sendMessage(player, "loan.management.gui_opened");
    }
    
    @Override
    protected void onOpen(Player player) {
        createMainInterface(player);
    }
    
    @Override
    protected void handleItemClick(Player player, int slot, ItemStack item, ClickType clickType) {
        // Handle administrative loan management operations
        // Implementation would include:
        // - View pending applications
        // - Approve/reject applications  
        // - Manage active loans
        // - Monitor overdue loans
        // - Review statistics and reports
    }
    
    /**
     * 创建主管理界面
     */
    private void createMainInterface(Player player) {
        clearInventory();
        
        // Statistics overview
        setItem(4, new ItemBuilder(Material.COMPASS)
            .setName("&6贷款业务概览")
            .setLore(
                "",
                "&7当前系统状态：",
                "• &f待审批申请: &e0",
                "• &f活跃贷款数: &b0", 
                "• &f逾期贷款数: &c0",
                "• &f异常处理数: &60",
                "",
                "&e点击刷新统计数据"
            ).build());
        
        // Application management
        setItem(19, new ItemBuilder(Material.GOODAPPLE)
            .setName("&e待审批申请")
            .setLore(
                "",
                "&7查看和处待审批的贷款申请：",
                "",
                "&f查看：新提交申请",
                "&f处理：批准/驳回",
                "&f批量：批量操作", 
                "",
                "&e点击查看待审批申请"
            ).build());
        
        // Loan monitoring
        setItem(21, new ItemBuilder(Material.MONITOR)
            .setName("&b贷款监控")
            .setLore(
                "",
                "&7监控和管理存贷款：",
                "",
                "&f查看：活跃贷款列表",
                "&f监控：还款状态",
                "&f提醒：逾期预警", 
                "",
                "&e点击查看存贷款"
            ).build());
        
        // Collections and overdue
        setItem(23, new ItemBuilder(Material.REDSTONE_BLOCK)
            .setName("&c逾期催收")
            .setLore(
                "",
                "&7理逾期贷款和催收：",
                "",
                "&f催收：自动催收流程",
                "&f管理：人工催收",
                "&f减免：罚息减免处理", 
                "",
                "&e点击查看逾期情况"
            ).build());
        
        setItem(25, new ItemBuilder(Material.CHEST)
            .setName("&6统计报告")
            .setLore(
                "",
                "&7生成和查看业务报告：",
                "",
                "&f统计：业务数据统计",
                "&f分析：风险分析报告",
                "&f趋势：业务量对比", 
                "",
                "&e点击查看报告"
            ).build());
        
        // Settings and configuration
        setItem(40, new ItemBuilder(Material.REDSTONE_COMPARATOR)
            .setName("&d系统设置")
            .setLore(
                "",
                "&7配置贷款系统参数：", 
                "",
                "&f设置：审批规则",
                "&f调整：业务花费率",
                "&f管理：员工权限", 
                "",
                "&e点击系统设置"
            ).build());
        
        setItem(48, new ItemBuilder(Material.EMERALD)
            .setName("&a刷新数据")
            .setLore("&7至少新一次所有统计").build());
        
        setItem(49, new ItemBuilder(Material.BARRIER)
            .setName("&c返回主菜单")
            .setLore("&7关闭管理界面").build());
        
        setItem(50, new ItemBuilder(Material.PAPER)
            .setName("&7帮助信息")
            .setLore("&7查看效期使用权明").build());
    }
    
    private boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }
}

/**
 * Personal loan management GUI for borrowers
 */
public class MyLoansGUI extends CustomGUI {
    
    private static final int GUI_SIZE = 54; 
    private static final String TITLE = "&b我的贷款";
    
    private final LoanApplicationService applicationService;
    private final RepaymentPlanService repaymentPlanService;
    
    public MyLoansGUI(YetAnotherEconomy plugin) {
        super(plugin, GUI_SIZE, TITLE);
        this.applicationService = plugin.getService(LoanApplicationService.class);
        this.repaymentPlanService = plugin.getService(RepaymentPlanService.class);
    }
    
    /**
     * 打开个人贷款管理界面
     */
    public void openMyLoansGUI(Player player) {
        if (applicationService == null || repaymentPlanService == null) {
            Messages.sendMessage(player, "loan.service_unavailable");
            return;
        }
        
        openGUI(player);
        Messages.sendMessage(player, "loan.myloans.gui_opened");
    }
    
    @Override
    protected void onOpen(Player player) {
        createPersonalLoanInterface(player);
    }
    
    @Override
    protected void handleItemClick(Player player, int slot, ItemStack item, ClickType clickType) {
        UUID playerId = player.getUniqueId();
        
        // Handle personal loan operations:
        // - View active loans
        // - Make payments  
        // - Check payment schedules
        // - View loan history
        // - Apply for new loans
    }
    
    /**
     * 创建个人贷款界面
     */
    private void createPersonalLoanInterface(Player player) {
        clearInventory();
        UUID playerId = player.getUniqueId();
        
        // Personal loan statistics
        setItem(4, new ItemBuilder(Material.BOOK)
            .setName("&b个人贷款概览")
            .setLore(
                "",
                String.format("&f玩家ID: &e%s", player.getName()),
                "&7当前贷款状态：",
                "• &f活跃贷款数: &60",
                "• &f月度还款额: &a¥0.00",
                "• &f信用评分: &650", 
                "• &f信用等级: &b一般",
                "",
                "&e点击刷新个人数据"
            ).build());
        
        // Active loans section
        setItem(19, new ItemBuilder(Material.GOODAPPLE)
            .setName("&e我的活跃贷款")
            .setLore(
                "",
                "&7查看和管理您的当前贷款：",
                "",
                "&f查看：进行中的贷款",
                "&f信息：还款计划和账单",
                "&f操作：在线还款和设置",
                "",
                "&f您目前没有活跃贷款",
                "",
                "&e点击申请新贷款"
            ).build());
        
        // Repayment scheduling
        setItem(30, new ItemBuilder(Material.CLOCK)
            .setName("&6还款计划")
            .setLore(
                "",
                "&7查看详细的还款计划：",
                "",
                "&f查看：未来还款安排",
                "&f提醒：系统到期提醒",
                "&f设置：自动扣账管理",
                "",
                "&e点击设置自动扣账"
            ).build());
        
        // Payment history - left space for history section
        setItem(21, new ItemBuilder(Material.PAPER)
            .setName("&f还款记录") 
            .setLore(
                "",
                "&7查看您的还款历史：",
                "",
                "&f历史：全部还款记录",
                "&f证明：银行还款接口",
                "&f分析：还款模式分析",
                "",
                "&e点击查看还款历史"
            ).build());
        
        // Apply for new loan
        setItem(23, new ItemBuilder(Material.EMERALD_BLOCK)
            .setName("&2申请新贷款")
            .setLore(
                "",
                "&7申请新的贷款：",
                "",
                "&f• 多类型贷款选择",
                "&f• 智能额度评估", 
                "&f• 快速审批流程",
                "&f• 专业客服支持",
                "",
                "&2✅ 开始新的贷款申请"
            ).build());
        
        // Financial tools
        setItem(32, new ItemBuilder(Material.CALCULATOR)
            .setName("&b贷款计算器")
            .setLore(
                "",
                "&7计算贷款成本：",
                "",
                "&f计算：月供和总额",
                "&f对比：不同方案对比",
                "&f规划：还款计划",
                "",
                "&e点击使用计算器"
            ).build());
        
        // Settings and preferences
        setItem(40, new ItemBuilder(Material.REDSTONE_COMPARATOR)
            .setName("&d偏好设置")
            .setLore(
                "",
                "&7设置个人偏好：",
                "",
                "&f提醒：还款提醒设置",
                "&f自动：扣账方式管理", 
                "&f隐私：数据偏好",
                "",
                "&e点击设置偏好"
            ).build());
        
        // Quick actions
        setItem(48, new ItemBuilder(Material.COMPASS)
            .setName("&a快捷操作")
            .setLore(
                "",
                "&7常用快捷操作：",
                "",
                "&a1次点击还款：", 
                "&b/yae loan pay -a",
                ""
            ).build());
        
        setItem(49, new ItemBuilder(Material.BARRIER)
            .setName("&c关闭界面")
            .setLore("&7关闭个人贷款界面").build());
        
        setItem(50, new ItemBuilder(Material.QUESTION_MARK)
            .setName("&7帮助中心")
            .setLore("&7获取贷款操作帮助").build());
        
        setItem(15, new ItemBuilder(Material.NOTE_BLOCK)
            .setName("&6提醒设置")
            .setLore(
                "",
                "&7管理还款提醒：",
                "&f• 账单提前提醒",
                "&f• 逾期预警通知",
                "&f• 额度变化告知",
                "",
                "&e点击设置提醒偏好"
            ).build());
    }
    
    @Override
    protected void handleItemClick(Player player, int slot, ItemStack item, ClickType clickType) {
        UUID playerId = player.getUniqueId();
        
        // Apply for new loan
        if (slot == 23) {
            // Check if player can apply
            if (canApplyForLoan(playerId)) {
                // Open loan application GUI
                LoanApplicationGUI applicationGUI = new LoanApplicationGUI(getPlugin());
                applicationGUI.openApplicationGUI(player);
            } else {
                Messages.sendMessage(player, "loan.myloans.cannot_apply_now");
            }
        }
        
        // Loan calculator
        if (slot == 32) {
            // Open loan calculator GUI
            LoanCalculatorGUI calculatorGUI = new LoanCalculatorGUI(getPlugin()); // This would need to be created
            calculatorGUI.openCalculatorGUI(player);
        }
        
        // Quick pay (approximate - need proper implementation)
        if (slot == 48) {
            quickPayCommand(player);
        }
        
        // Close
        if (slot == 49) {
            player.closeInventory();
        }
    }
    
    private boolean canApplyForLoan(UUID playerId) {
        // Implement eligibility checks for new loans
        return true; // Placeholder
    }
    
    private void quickPayCommand(Player player) {
        // Execute quick pay command
        Messages.sendMessage(player, "loan.myloans.execute_quick_pay");
        // Would call the loan payment service
    }
}
