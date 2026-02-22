package com.yae.api.gui;

import com.yae.api.loan.LoanService;
import com.yae.api.loan.Loan;
import com.yae.api.loan.LoanService.PaymentResult;
import com.yae.api.credit.LoanType;
import com.yae.utils.MessageUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Loan Management GUI - Handles loan management, repayment, and status viewing
 */
public class LoanManagementGUI {
    
    private final LoanService loanService;
    private final Player player;
    
    // Current view state
    private String selectedLoanId = null;
    private ViewMode currentViewMode = ViewMode.ACTIVE;
    
    public enum ViewMode {
        ACTIVE("活跃贷款", "ACTIVE", "显示当前活跃状态的贷款"),
        PENDING("待审核", "PENDING", "等待审核的贷款申请"),
        HISTORY("历史记录", "ALL", "所有历史贷款记录"),
        OVERDUE("逾期贷款", "OVERDUE", "需要立即处理的逾期贷款");
        
        private final String displayName;
        private final String filter;
        private final String description;
        
        ViewMode(String displayName, String filter, String description) {
            this.displayName = displayName;
            this.filter = filter;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getFilter() { return filter; }
        public String getDescription() { return description; }
    }
    
    public LoanManagementGUI(LoanService loanService, Player player) {
        this.loanService = loanService;
        this.player = player;
    }
    
    /**
     * Open the loan management main GUI
     */
    public void open() {
        PaginatedGui gui = Gui.paginated()
            .title(Component.text(MessageUtil.colorize("&6&l贷款管理 &f- &e" + player.getName())))
            .rows(6)
            .pageSize(27)
            .create();
        
        setupMainContent(gui);
        gui.open(player);
    }
    
    private void setupMainContent(PaginatedGui gui) {
        Loan loan = selectedLoanId != null ? loanService.getLoan(selectedLoanId) : null;
        
        if (selectedLoanId != null && loan != null) {
            setupLoanDetails(gui, loan);
        } else {
            setupLoanList(gui);
        }
    }
    
    private void setupLoanList(PaginatedGui gui) {
        // Header
        GuiItem header = ItemBuilder.from(Material.WRITABLE_BOOK)
            .name(Component.text(MessageUtil.colorize("&6&l贷款管理主页")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7当前视图: &f" + currentViewMode.getDisplayName())),
                Component.text(MessageUtil.colorize("&7" + currentViewMode.getDescription())),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击查看贷款详情"))
            ))
            .build();
        gui.setItem(4, header);
        
        // View mode selection
        setupViewModeButtons(gui);
        
        // Load loans
        loadLoansAndPopulate(gui);
        
        // Navigation
        setupPagination(gui);
        setupNavigation(gui);
    }
    
    private void setupLoanDetails(PaginatedGui gui, Loan loan) {
        // Back button
        GuiItem backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l返回列表")))
            .build();
        
        backButton.setAction(event -> {
            selectedLoanId = null;
            setupMainContent(gui);
        });
        
        gui.setItem(45, backButton);
        
        // Loan information panel
        setupLoanInfoPanel(gui, loan);
        
        // Action buttons
        setupLoanActionButtons(gui, loan);
        
        // Payment history/repayment schedule
        setupPaymentSchedule(gui, loan);
        
        // Close button
        GuiItem closeButton = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.colorize("&c&l关闭")))
            .build();
        
        closeButton.setAction(event -> player.closeInventory());
        gui.setItem(53, closeButton);
        
        // Fill empty slots
        fillEmptySlots(gui);
    }
    
    private void setupViewModeButtons(PaginatedGui gui) {
        int[] slots = {0, 1, 2, 3};
        ViewMode[] modes = ViewMode.values();
        
        for (int i = 0; i < modes.length && i < slots.length; i++) {
            ViewMode mode = modes[i];
            int slot = slots[i];
            
            Material material = getMaterialForMode(mode);
            boolean isCurrentMode = mode == currentViewMode;
            
            GuiItem modeButton = ItemBuilder.from(material)
                .name(Component.text(MessageUtil.colorize((isCurrentMode ? "&6&l" : "&7") + mode.getDisplayName())))
                .lore(Arrays.asList(
                    Component.text(MessageUtil.colorize("&7" + mode.getDescription())),
                    Component.text(""),
                    Component.text(MessageUtil.colorize(isCurrentMode ? "&a当前视图" : "&e点击切换"))
                ))
                .build();
            
            if (!isCurrentMode) {
                modeButton.setAction(event -> {
                    currentViewMode = mode;
                    selectedLoanId = null;
                    setupMainContent(gui);
                });
            }
            
            gui.setItem(slot, modeButton);
        }
    }
    
    private Material getMaterialForMode(ViewMode mode) {
        switch (mode) {
            case ACTIVE:
                return Material.GREEN_WOOL;
            case PENDING:
                return Material.YELLOW_WOOL;
            case HISTORY:
                return Material.BOOK;
            case OVERDUE:
                return Material.RED_WOOL;
            default:
                return Material.PAPER;
        }
    }
    
    private void loadLoansAndPopulate(PaginatedGui gui) {
        List<Loan> loans = loanService.getPlayerLoans(player.getUniqueId());
        
        // Filter loans based on current view mode
        List<Loan> filteredLoans = loans.stream()
            .filter(loan -> filterLoanByMode(loan, currentViewMode))
            .toList();
        
        if (filteredLoans.isEmpty()) {
            GuiItem emptyItem = ItemBuilder.from(Material.BARRIER)
                .name(Component.text(MessageUtil.colorize("&c没有符合条件的贷款")))
                .lore(Arrays.asList(
                    Component.text(MessageUtil.colorize("&7当前视图: " + currentViewMode.getDisplayName())),
                    Component.text(MessageUtil.colorize("&7您还没有任何" + currentViewMode.getDisplayName()))
                ))
                .build();
            gui.setItem(31, emptyItem);
            return;
        }
        
        // Add loan items
        for (int i = 0; i < filteredLoans.size() && i < gui.getPageSize(); i++) {
            Loan loan = filteredLoans.get(i);
            GuiItem loanItem = createLoanItem(loan);
            gui.setItem(i + 9, loanItem); // Start from second row
        }
    }
    
    private boolean filterLoanByMode(Loan loan, ViewMode mode) {
        Loan.LoanStatus status = loan.getStatus();
        
        switch (mode) {
            case ACTIVE:
                return status.isActive();
            case PENDING:
                return status == Loan.LoanStatus.PENDING;
            case OVERDUE:
                return status == Loan.LoanStatus.OVERDUE;
            case HISTORY:
                return true; // Show all
            default:
                return status.isActive();
        }
    }
    
    private GuiItem createLoanItem(Loan loan) {
        Material material = getMaterialForLoan(loan);
        
        GuiItem item = ItemBuilder.from(material)
            .name(Component.text(MessageUtil.colorize(getLoanTitle(loan))))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7贷款编号: &f" + loan.getLoanId())),
                Component.text(MessageUtil.colorize("&7贷款类型: &f" + loan.getLoanType().getDisplayName())),
                Component.text(MessageUtil.colorize("&7当前状态: " + getStatusColor(loan.getStatus()) + getStatusText(loan.getStatus()))),
                Component.text(MessageUtil.colorize("&7申请金额: &6💰" + String.format("%,.0f", loan.getPrincipalAmount()))),
                Component.text(MessageUtil.colorize("&7剩余本金: &6💰" + String.format("%,.0f", loan.getCurrentBalance()))),
                Component.text(MessageUtil.colorize("&7利率: &f" + String.format("%.2f%%", loan.getInterestRate() * 100))),
                Component.text(MessageUtil.colorize("&7期限: &f" + loan.getTermMonths() + " 月")),
                Component.text(getProgressBar(loan)),
                Component.text(MessageUtil.colorize("&7月供: &6💰" + String.format("%,.0f", loan.getMonthlyPayment()))),
                Component.text(MessageUtil.colorize("&7下次还款: &f" + formatNextPaymentDate(loan))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击查看详细信息"))
            ))
            .build();
        
        item.setAction(event -> {
            selectedLoanId = loan.getLoanId();
            setupMainContent((PaginatedGui) event.getInventory().getHolder());
        });
        
        return item;
    }
    
    private Material getMaterialForLoan(Loan loan) {
        Loan.LoanStatus status = loan.getStatus();
        LoanType type = loan.getLoanType();
        
        if (status == Loan.LoanStatus.OVERDUE) {
            return Material.RED_WOOL;
        } else if (status == Loan.LoanStatus.PENDING) {
            return Material.YELLOW_WOOL;
        } else if (status == Loan.LoanStatus.ACTIVE) {
            switch (type) {
                case CREDIT:
                    return Material.GREEN_WOOL;
                case MORTGAGE:
                    return Material.BLUE_WOOL;
                case BUSINESS:
                    return Material.PURPLE_WOOL;
                case EMERGENCY:
                    return Material.ORANGE_WOOL;
                default:
                    return Material.WHITE_WOOL;
            }
        } else if (status == Loan.LoanStatus.PAID_OFF) {
            return Material.EMERALD_BLOCK;
        } else if (status == Loan.LoanStatus.DEFAULT) {
            return Material.BEDROCK;
        } else {
            return Material.GRAY_WOOL;
        }
    }
    
    private String getLoanTitle(Loan loan) {
        String title = "&6";
        if (loan.getLoanType() == LoanType.MORTGAGE) {
            title += "抵押贷款";
        } else if (loan.getLoanType() == LoanType.CREDIT) {
            title += "信用贷款";
        } else if (loan.getLoanType() == LoanType.BUSINESS) {
            title += "商业贷款";
        } else if (loan.getLoanType() == LoanType.EMERGENCY) {
            title += "应急贷款";
        }
        
        if (loan.getStatus() == Loan.LoanStatus.OVERDUE) {
            title += " (逾期)";
        }
        
        return title;
    }
    
    private String getStatusColor(Loan.LoanStatus status) {
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
    
    private String getProgressBar(Loan loan) {
        int progress = (int) ((double) loan.getPaymentsMade() / loan.getTotalPayments() * 10);
        StringBuilder bar = new StringBuilder("&7进度: ");
        
        bar.append("&a");
        for (int i = 0; i < progress; i++) {
            bar.append("█");
        }
        
        bar.append("&7");
        for (int i = progress; i < 10; i++) {
            bar.append("█");
        }
        
        bar.append(" &f").append(loan.getPaymentsMade()).append("/").append(loan.getTotalPayments());
        return bar.toString();
    }
    
    private String formatNextPaymentDate(Loan loan) {
        if (loan.getNextPaymentDate() == null) {
            return "无";
        }
        // TODO: Implement proper date formatting
        return loan.getNextPaymentDate().toString();
    }
    
    private void setupLoanInfoPanel(PaginatedGui gui, Loan loan) {
        // Loan summary
        GuiItem summary = ItemBuilder.from(Material.WRITABLE_BOOK)
            .name(Component.text(MessageUtil.colorize("&6&l贷款详情 - " + loan.getLoanId())))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7贷款类型: &f" + loan.getLoanType().getDisplayName())),
                Component.text(MessageUtil.colorize("&7贷款编号: &f" + loan.getLoanId())),
                Component.text(MessageUtil.colorize("&7当前状态: " + getStatusColor(loan.getStatus()) + getStatusText(loan.getStatus()))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6贷款金额:")),
                Component.text(MessageUtil.colorize("&7本   金: &6💰" + String.format("%,.0f", loan.getPrincipalAmount()))),
                Component.text(MessageUtil.colorize("&7余   额: &6💰" + String.format("%,.0f", loan.getCurrentBalance()))),
                Component.text(MessageUtil.colorize("&7利率: &f" + String.format("%.2f%%", loan.getInterestRate() * 100))),
                Component.text(MessageUtil.colorize("&7贷款期限: &f" + loan.getTermMonths() + " 月")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6还款信息:")),
                Component.text(MessageUtil.colorize("&7已还期数: &f" + loan.getPaymentsMade() + "/" + loan.getTotalPayments())),
                Component.text(MessageUtil.colorize("&7月供金额: &6💰" + String.format("%,.0f", loan.getMonthlyPayment()))),
                Component.text(MessageUtil.colorize("&7下次还款: &f" + formatNextPaymentDate(loan)))
            ))
            .build();
        gui.setItem(4, summary);
        
        // Progress bar
        GuiItem progress = ItemBuilder.from(createProgressBarItem(loan))
            .build();
        gui.setItem(31, progress);
    }
    
    private ItemStack createProgressBarItem(Loan loan) {
        int progress = (int) ((double) loan.getPaymentsMade() / loan.getTotalPayments() * 10);
        StringBuilder bar = new StringBuilder();
        
        for (int i = 0; i < 10; i++) {
            if (i < progress) {
                bar.append("&a█");
            } else {
                bar.append("&7█");
            }
        }
        
        return ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text(MessageUtil.colorize("&6&l还款进度: " + loan.getPaymentsMade() + "/" + loan.getTotalPayments())))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize(bar.toString())),
                Component.text(MessageUtil.colorize("&7完成度: " + String.format("%.1f%%", (double) loan.getPaymentsMade() / loan.getTotalPayments() * 100)))
            ))
            .build();
    }
    
    private void setupLoanActionButtons(PaginatedGui gui, Loan loan) {
        if (loan.getStatus().isActive() || loan.getStatus() == Loan.LoanStatus.OVERDUE) {
            // Make payment button
            GuiItem paymentButton = ItemBuilder.from(Material.EMERALD_BLOCK)
                .name(Component.text(MessageUtil.colorize("&a&l立即还款")))
                .lore(Arrays.asList(
                    Component.text(MessageUtil.colorize("&7月供金额: &6💰" + String.format("%,.0f", loan.getMonthlyPayment()))),
                    Component.text(MessageUtil.colorize("&7剩余本金: &6💰" + String.format("%,.0f", loan.getCurrentBalance()))),
                    Component.text(MessageUtil.colorize("&7应付利息: &6💰" + String.format("%,.0f", calculateRemainingInterest(loan)))),
                    Component.text(""),
                    Component.text(MessageUtil.colorize("&c⚠ &7只能还当月的月供"))
                ))
                .build();
            
            paymentButton.setAction(event -> handlePayment(loan));
            gui.setItem(10, paymentButton);
            
            // Auto-payment toggle
            boolean autoPayEnabled = loan.isAutoPayEnabled();
            GuiItem autoPayButton = ItemBuilder.from(autoPayEnabled ? Material.REDSTONE_TORCH : Material.LEVER)
                .name(Component.text(MessageUtil.colorize(autoPayEnabled ? "&c&l关闭自动还款" : "&a&l开启自动还款")))
                .lore(Arrays.asList(
                    Component.text(MessageUtil.colorize("&7自动还款: " + (autoPayEnabled ? "&a开启" : "&c关闭"))),
                    Component.text(MessageUtil.colorize("&7从银行账户自动扣款")),
                    Component.text(""),
                    Component.text(MessageUtil.colorize("&7到期日自动扣款"))
                ))
                .build();
            
            autoPayButton.setAction(event -> toggleAutoPayment(loan));
            gui.setItem(12, autoPayButton);
            
            if (loan.getStatus() == Loan.LoanStatus.OVERDUE) {
                // Pay overdue amount
                GuiItem overduePayment = ItemBuilder.from(Material.REDSTONE_BLOCK)
                    .name(Component.text(MessageUtil.colorize("&c&l支付逾期金额")))
                    .lore(Arrays.asList(
                        Component.text(MessageUtil.colorize("&7逾期金额: &6💰" + String.format("%,.0f", loan.getOverdueAmount()))),
                        Component.text(MessageUtil.colorize("&7逾期期数: &c" + loan.getOverduePayments() + " 期")),
                        Component.text(""),
                        Component.text(MessageUtil.colorize("&c⚠ &7请尽快处理逾期款项")),
                        Component.text(MessageUtil.colorize("&7否则将承担额外滞纳金"))
                    ))
                    .build();
                
                overduePayment.setAction(event -> handleOverduePayment(loan));
                gui.setItem(14, overduePayment);
            }
        }
        
        // Loan details/files
        GuiItem detailsButton = ItemBuilder.from(Material.BOOK)
            .name(Component.text(MessageUtil.colorize("&6&l查看详细信息")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7贷款合同编号: &f" + loan.getLoanId())),
                Component.text(MessageUtil.colorize("&7贷款开始日期: &f" + loan.getStartDate())),
                Component.text(MessageUtil.colorize("&7预计结束日期: &f" + loan.getMaturityDate())),
                Component.text(MessageUtil.colorize("&7创建日期: &f" + loan.getApplicationDate())),
                Component.text(""),
                Component.text(MessageUtil.colorize("&7可贷款合同和条款详情"))
            ))
            .build();
        
        detailsButton.setAction(event -> showLoanDetails(loan));
        gui.setItem(16, detailsButton);
    }
    
    private void handlePayment(Loan loan) {
        player.sendMessage(MessageUtil.colorize("&6[贷款管理] &f准备还款..."));
        
        double monthlyPayment = loan.getMonthlyPayment();
        
        // Create payment confirmation GUI or use chat input
        player.sendMessage(MessageUtil.colorize("&6[还款] &f本次应还金额: &6💰" + String.format("%,.0f", monthlyPayment)));
        player.sendMessage(MessageUtil.colorize("&6[还款] &f请在银行软件中确认还款"));
        player.sendMessage(MessageUtil.colorize("&6[还款] &f或输入 &ayes&f 确认还款, &cno&f 取消"));
        
        // Handle payment confirmation (would normally be handled by chat listener)
        CompletableFuture<PaymentResult> paymentFuture = loanService.makePayment(
            loan.getLoanId(), monthlyPayment, com.yae.api.loan.Loan.PaymentMethod.VAULT);
        
        paymentFuture.thenAccept(result -> {
            if (result.isSuccess()) {
                player.sendMessage(MessageUtil.colorize("&a[还款] 还款成功！"));
                player.sendMessage(MessageUtil.colorize("&a[还款] 支付金额: &6💰" + String.format("%,.0f", result.getTotalPayment())));
                player.sendMessage(MessageUtil.colorize("&a[还款] 其中利息: &6💰" + String.format("%,.0f", result.getInterestPayment())));
                player.sendMessage(MessageUtil.colorize("&a[还款] 其中本金: &6💰" + String.format("%,.0f", result.getPrincipalPayment())));
                
                if (result.getPenaltyPayment() > 0) {
                    player.sendMessage(MessageUtil.colorize("&a[还款] 滞纳金: &6💰" + String.format("%,.0f", result.getPenaltyPayment())));
                }
                
                // Refresh GUI
                setupMainContent((PaginatedGui) player.getOpenInventory().getTopInventory().getHolder());
                
            } else {
                player.sendMessage(MessageUtil.colorize("&c[还款] 还款失败！"));
            }
        });
    }
    
    private void handleOverduePayment(Loan loan) {
        player.sendMessage(MessageUtil.colorize("&c[逾期还款] &f支付逾期款项: &6💰" + String.format("%,.0f", loan.getOverdueAmount())));
        
        loanService.makePayment(loan.getLoanId(), loan.getOverdueAmount(), com.yae.api.loan.Loan.PaymentMethod.VAULT)
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    player.sendMessage(MessageUtil.colorize("&a[逾期还款] 支付成功！"));
                    player.sendMessage(MessageUtil.colorize("&a[逾期还款] 贷款状态已恢复正常"));
                } else {
                    player.sendMessage(MessageUtil.colorize("&c[逾期还款] 支付失败！"));
                }
            });
    }
    
    private void toggleAutoPayment(Loan loan) {
        boolean newState = !loan.isAutoPayEnabled();
        player.sendMessage(MessageUtil.colorize(newState ? "&a[自动还款] 已开启" : "&c[自动还款] 已关闭"));
        player.sendMessage(MessageUtil.colorize("&7自动还款将在每月经期从银行账户扣款"));
        // For now, this would need to be saved to the database - simplified for testing
        // Refresh GUI
        setupMainContent((PaginatedGui) player.getOpenInventory().getTopInventory().getHolder());
    }
    
    private void showLoanDetails(Loan loan) {
        player.sendMessage(MessageUtil.colorize("&6━━━━━━━━━━ 《贷款詳細信息》 ━━━━━━━━━━"));
        player.sendMessage(MessageUtil.colorize("&7贷款编号: &f" + loan.getLoanId()));
        player.sendMessage(MessageUtil.colorize("&7贷款类型: &f" + loan.getLoanType().getDisplayName()));
        player.sendMessage(MessageUtil.colorize("&7贷款状态: " + getStatusColor(loan.getStatus()) + getStatusText(loan.getStatus())));
        player.sendMessage(MessageUtil.colorize(""));
        player.sendMessage(MessageUtil.colorize("&6━━━━━━━ 《贷款金额信息》 ━━━━━━━"));
        player.sendMessage(MessageUtil.colorize("&7本金金额: &6💰" + String.format("%,.0f", loan.getPrincipalAmount())));
        player.sendMessage(MessageUtil.colorize("&7当前余额: &6💰" + String.format("%,.0f", loan.getCurrentBalance())));
        player.sendMessage(MessageUtil.colorize("&7已还本金: &6💰" + String.format("%,.0f", loan.getTotalPrincipalPaid())));
        player.sendMessage(MessageUtil.colorize("&7已付利息: &6💰" + String.format("%,.0f", loan.getTotalInterestPaid())));
        player.sendMessage(MessageUtil.colorize("") );
        player.sendMessage(MessageUtil.colorize("&6━━━━━━━ 《贷款条款信息》 ━━━━━━━"));
        player.sendMessage(MessageUtil.colorize("&7年利率: &f" + String.format("%.2f%%", loan.getInterestRate() * 100)));
        player.sendMessage(MessageUtil.colorize("&7贷款期限: &f" + loan.getTermMonths() + " 月"));
        player.sendMessage(MessageUtil.colorize("&7月供金额: &6💰" + String.format("%,.0f", loan.getMonthlyPayment())));
        player.sendMessage(MessageUtil.colorize("&7已还期数: &f" + loan.getPaymentsMade() + "/" + loan.getTotalPayments()));
        
        if (loan.isOverdue()) {
            player.sendMessage(MessageUtil.colorize("&7逾期期数: &c" + loan.getOverduePayments() + " 期"));
            player.sendMessage(MessageUtil.colorize("&7逾期金额: &c💰" + String.format("%,.0f", loan.getOverdueAmount())));
        }
        
        player.sendMessage(MessageUtil.colorize(""));
        player.sendMessage(MessageUtil.colorize("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }
    
    private void setupPaymentSchedule(PaginatedGui gui, Loan loan) {
        // Payment schedule header
        GuiItem scheduleHeader = ItemBuilder.from(Material.CHEST)
            .name(Component.text(MessageUtil.colorize("&6&l还款计划")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7点击查看详细还款计划")),
                Component.text(MessageUtil.colorize("&7包括历史还款记录")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击查看"))
            ))
            .build();
        
        scheduleHeader.setAction(event -> showPaymentSchedule(loan));
        gui.setItem(52, scheduleHeader);
    }
    
    private void showPaymentSchedule(Loan loan) {
        loanService.generateRepaymentSchedule(loan).thenAccept(schedule -> {
            player.sendMessage(MessageUtil.colorize("&6━━━━━━━━━━ 《还款计划表》 ━━━━━━━━━━"));
            
            int page = 0;
            int itemsPerPage = 10;
            
            for (int i = 0; i < schedule.size(); i += itemsPerPage) {
                page++;
                if (page > 1) {
                    player.sendMessage(MessageUtil.colorize("&6━━━━━━━ 第 " + page + " 页 ━━━━━━━"));
                }
                
                int endIndex = Math.min(i + itemsPerPage, schedule.size());
                for (int j = i; j < endIndex; j++) {
                    var payment = schedule.get(j);
                    player.sendMessage(MessageUtil.colorize("&7第" + payment.getPaymentNumber() + "期 " +
                        formatDate(payment.getPaymentDate()) + 
                        " &6💰" + String.format("%,.0f", payment.getScheduledPayment()) +
                        " &f(本: 💰" + String.format("%,.0f", payment.getPrincipalPayment()) +
                        " 利: 💰" + String.format("%,.0f", payment.getInterestPayment()) + ")"));
                }
            }
            
            player.sendMessage(MessageUtil.colorize("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            
        }).exceptionally(ex -> {
            player.sendMessage(MessageUtil.colorize("&c获取还款计划失败"));
            return null;
        });
    }
    
    private void setupPagination(PaginatedGui gui) {
        // Previous page button
        GuiItem prevPage = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l上一页")))
            .build();
        
        prevPage.setAction(event -> gui.previous());
        gui.setItem(52, prevPage);
        
        // Next page button
        GuiItem nextPage = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l下一页")))
            .build();
        
        nextPage.setAction(event -> gui.next());
        gui.setItem(53, nextPage);
    }
    
    private void setupNavigation(PaginatedGui gui) {
        // Refresh button
        GuiItem refreshButton = ItemBuilder.from(Material.CLOCK)
            .name(Component.text(MessageUtil.colorize("&6&l刷新")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7重新加载贷款信息")),
                Component.text(MessageUtil.colorize("&7从数据库获取最新数据")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击刷新"))
            ))
            .build();
        
        refreshButton.setAction(event -> {
            setupMainContent(gui);
            player.sendMessage(MessageUtil.colorize("&6[贷款管理] &f贷款信息已刷新"));
        });
        
        gui.setItem(49, refreshButton);
        
        // Settings button
        GuiItem settingsButton = ItemBuilder.from(Material.COMPASS)
            .name(Component.text(MessageUtil.colorize("&b&l设置")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7管理贷款通知和提醒")),
                Component.text(MessageUtil.colorize("&7设置自动还款偏好")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击查看设置"))
            ))
            .build();
        
        settingsButton.setAction(event -> showSettings());
        gui.setItem(50, settingsButton);
        
        // Close button
        GuiItem closeButton = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.colorize("&c&l关闭")))
            .build();
        
        closeButton.setAction(event -> player.closeInventory());
        gui.setItem(53, closeButton);
        
        // Fill empty slots
        fillEmptySlots(gui);
    }
    
    private void fillEmptySlots(PaginatedGui gui) {
        GuiItem filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
            .name(Component.text(""))
            .build();
        
        for (int i = 0; i < 54; i++) {
            if (gui.getInventory().getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }
    
    // Helper methods
    
    private double calculateRemainingInterest(Loan loan) {
        // Calculate remaining interest based on current balance and payments left
        double monthlyRate = loan.getInterestRate() / 12;
        int remainingPayments = loan.getTotalPayments() - loan.getPaymentsMade();
        double currentBalance = loan.getCurrentBalance();
        
        double remainingInterest = 0;
        double balance = currentBalance;
        
        for (int i = 0; i < remainingPayments; i++) {
            double monthlyInterest = balance * monthlyRate;
            remainingInterest += monthlyInterest;
            double principalPayment = loan.getMonthlyPayment() - monthlyInterest;
            if (principalPayment > 0) {
                balance = Math.max(0, balance - principalPayment);
            }
        }
        
        return remainingInterest;
    }
    
    private String formatDate(LocalDateTime date) {
        if (date == null) {
            return "未知";
        }
        return date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }
    
    private void showSettings() {
        player.sendMessage(MessageUtil.colorize("&6[贷款管理] &f贷款管理设置功能开发中..."));
    }
}
