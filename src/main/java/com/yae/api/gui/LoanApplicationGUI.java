package com.yae.api.gui;

import com.yae.api.credit.CreditService;
import com.yae.api.credit.LoanType;
import com.yae.api.loan.LoanService;
import com.yae.utils.MessageUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Loan Application GUI - Handles loan application process
 */
public class LoanApplicationGUI {
    
    private final CreditService creditService;
    private final LoanService loanService;
    private final Player player;
    private final LoanType loanType;
    
    // Application state
    private double requestedAmount = 0;
    private int termMonths = 1;
    private String loanPurpose = "";
    private String collateralType = "";
    private double collateralValue = 0;
    
    // Navigation state
    private int currentStep = 1;
    private final int totalSteps = 5;
    
    private static final Map<LoanType, String> loanPurposeExamples = new HashMap<>();
    
    static {
        loanPurposeExamples.put(LoanType.CREDIT, "个人消费, 紧急资金, 生活费用");
        loanPurposeExamples.put(LoanType.MORTGAGE, "购买房产, 房屋装修, 土地购置");
        loanPurposeExamples.put(LoanType.BUSINESS, "商业投资, 设备采购, 库存资金");
        loanPurposeExamples.put(LoanType.EMERGENCY, "应急资金, 医疗费用, 紧急修现");
    }
    
    public LoanApplicationGUI(CreditService creditService, LoanService loanService, 
                             Player player, LoanType loanType) {
        this.creditService = creditService;
        this.loanService = loanService;
        this.player = player;
        this.loanType = loanType;
    }
    
    /**
     * Open the loan application GUI
     */
    public void open() {
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtil.colorize("&6&l申请 " + loanType.getDisplayName() + " &f- &e步骤 " + currentStep + "/" + totalSteps)))
            .rows(6)
            .create();
        
        setupStepContent(gui);
        
        gui.open(player);
    }
    
    private void setupStepContent(Gui gui) {
        switch (currentStep) {
            case 1:
                setupAmountSelection(gui);
                break;
            case 2:
                setupTermSelection(gui);
                break;
            case 3:
                setupPurposeInput(gui);
                break;
            case 4:
                setupCollateralSelection(gui);
                break;
            case 5:
                setupReviewAndConfirm(gui);
                break;
        }
    }
    
    private void setupAmountSelection(Gui gui) {
        // Get player credit info for amount limits
        creditService.getCreditScore(player.getUniqueId()).thenAccept(creditScore -> {
            creditService.getCreditGrade(player.getUniqueId()).thenAccept(creditGrade -> {
                
                double maxAmount = calculateMaxLoanAmount(creditGrade);
                
                // Amount selection header
                GuiItem header = ItemBuilder.from(Material.WRITABLE_BOOK)
                    .name(Component.text(MessageUtil.colorize("&6&l第1步: 选择贷款金额")))
                    .lore(Arrays.asList(
                        Component.text(MessageUtil.colorize("&7贷款类型: " + loanType.getDisplayName())),
                        Component.text(MessageUtil.colorize("&7信用评分: &f" + creditScore)),
                        Component.text(MessageUtil.colorize("&7信用等级: " + creditGrade.getDisplayName())),
                        Component.text(MessageUtil.colorize("&7最高可借: &6💰" + String.format("%,.0f", maxAmount))),
                        Component.text(""),
                        Component.text(MessageUtil.colorize("&e当前金额: &6💰" + String.format("%,.0f", requestedAmount))),
                        Component.text(MessageUtil.colorize("&7选择具体金额或输入自定义金额"))
                    ))
                    .build();
                gui.setItem(4, header);
                
                // Preset amount buttons
                double[] presetAmounts = calculatePresetAmounts(maxAmount);
                int[] presetSlots = {19, 20, 21, 28, 29, 30};
                
                for (int i = 0; i < presetAmounts.length && i < presetSlots.length; i++) {
                    double amount = presetAmounts[i];
                    int slot = presetSlots[i];
                    
                    GuiItem amountItem = ItemBuilder.from(Material.EMERALD)
                        .name(Component.text(MessageUtil.colorize("&6💰 " + String.format("%,.0f", amount))))
                        .lore(Arrays.asList(
                            Component.text(MessageUtil.colorize("&7选择此金额" + (amount > maxAmount ? " &c(超限额)" : ""))),
                            Component.text(MessageUtil.colorize("&8约占最大额度 " + String.format("%.1f%%", amount / maxAmount * 100)))
                        ))
                        .build();
                    
                    if (amount <= maxAmount) {
                        amountItem.setAction(event -> {
                            requestedAmount = amount;
                            nextStep(gui);
                        });
                    }
                    
                    gui.setItem(slot, amountItem);
                }
                
                // Custom amount input
                GuiItem customAmount = ItemBuilder.from(Material.WOODEN_BUTTON)
                    .name(Component.text(MessageUtil.colorize("&b&l自定义金额")))
                    .lore(Arrays.asList(
                        Component.text(MessageUtil.colorize("&7输入特定金额")),
                        Component.text(MessageUtil.colorize("&7范围: 1 - " + String.format("%,.0f", maxAmount))),
                        Component.text(""),
                        Component.text(MessageUtil.colorize("&e点击后请在聊天栏输入"))
                    ))
                    .build();
                
                customAmount.setAction(event -> {
                    player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f请输入您想要的贷款金额 (1-" + String.format("%,.0f", maxAmount) + "):"));
                    player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f输入 q 取消申请"));
                    player.closeInventory();
                    // Handle input would be implemented via async chat listener
                });
                
                gui.setItem(33, customAmount);
                
                // Navigation
                setupNavigation(gui);
                
            }).exceptionally(ex -> {
                player.sendMessage(MessageUtil.colorize("&c获取信用信息失败，请稍后重试"));
                return null;
            });
        }).exceptionally(ex -> {
            player.sendMessage(MessageUtil.colorize("&c获取信用评分失败，请稍后重试"));
            return null;
        });
    }
    
    private void setupTermSelection(Gui gui) {
        int maxTerm = loanType.getMaxTermMonths();
        
        // Term selection header
        GuiItem header = ItemBuilder.from(Material.CLOCK)
            .name(Component.text(MessageUtil.colorize("&6&l第2步: 选择贷款期限")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7贷款类型: " + loanType.getDisplayName())),
                Component.text(MessageUtil.colorize("&7申请金额: &6💰" + String.format("%,.0f", requestedAmount))),
                Component.text(MessageUtil.colorize("&7最长期限: &f" + maxTerm + " 月")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e当前期限: &f" + termMonths + " 月")),
                Component.text(MessageUtil.colorize("&7选择具体期限"))
            ))
            .build();
        gui.setItem(4, header);
        
        // Preset term buttons
        int[] presetTerms = calculatePresetTerms(maxTerm);
        int[] presetSlots = {19, 20, 21, 28, 29, 30, 37, 38, 39};
        
        for (int i = 0; i < presetTerms.length && i < presetSlots.length; i++) {
            int months = presetTerms[i];
            int slot = presetSlots[i];
            
            GuiItem termItem = ItemBuilder.from(Material.FEATHER)
                .name(Component.text(MessageUtil.colorize("&e" + months + " 月")))
                .lore(Arrays.asList(
                    Component.text(MessageUtil.colorize("&7选择此期限")),
                    Component.text(MessageUtil.colorize("&7约 " + (months / 12.0) + " 年"))
                ))
                .build();
            
            termItem.setAction(event -> {
                termMonths = months;
                nextStep(gui);
            });
            
            gui.setItem(slot, termItem);
        }
        
        // Navigation
        setupNavigation(gui);
    }
    
    private void setupPurposeInput(Gui gui) {
        // Purpose input header
        GuiItem header = ItemBuilder.from(Material.PAPER)
            .name(Component.text(MessageUtil.colorize("&6&l第3步: 输入贷款用途")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7贷款类型: " + loanType.getDisplayName())),
                Component.text(MessageUtil.colorize("&7申请金额: &6💰" + String.format("%,.0f", requestedAmount))),
                Component.text(MessageUtil.colorize("&7申请期限: &f" + termMonths + " 月")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e当前用途: &f" + (loanPurpose.isEmpty() ? "未设置" : loanPurpose))),
                Component.text(MessageUtil.colorize("&7说明资金用途"))
            ))
            .build();
        gui.setItem(4, header);
        
        // Example purposes
        String[] examplePurposes = loanPurposeExamples.get(loanType).split(", ");
        int[] exampleSlots = {19, 20, 21, 28, 29, 30};
        
        for (int i = 0; i < examplePurposes.length && i < exampleSlots.length; i++) {
            String purpose = examplePurposes[i];
            int slot = exampleSlots[i];
            
            GuiItem purposeItem = ItemBuilder.from(Material.WRITABLE_BOOK)
                .name(Component.text(MessageUtil.colorize("&e" + purpose)))
                .build();
            
            purposeItem.setAction(event -> {
                loanPurpose = purpose;
                nextStep(gui);
            });
            
            gui.setItem(slot, purposeItem);
        }
        
        // Custom purpose input
        GuiItem customPurpose = ItemBuilder.from(Material.OAK_SIGN)
            .name(Component.text(MessageUtil.colorize("&b&l自定义用途")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7输入自定义用途")),
                Component.text(MessageUtil.colorize("&7请简明具体"))
            ))
            .build();
        
        customPurpose.setAction(event -> {
            player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f请输入贷款用途:"));
            player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f输入 q 取消申请"));
            player.closeInventory();
        });
        
        gui.setItem(34, customPurpose);
        
        // Navigation
        setupNavigation(gui);
    }
    
    private void setupCollateralSelection(Gui gui) {
        if (!loanType.requiresCollateral()) {
            // Skip collateral step for non-collateral loans
            currentStep++;
            setupStepContent(gui);
            return;
        }
        
        // Collateral header
        GuiItem header = ItemBuilder.from(Material.CHEST)
            .name(Component.text(MessageUtil.colorize("&6&l第4步: 选择抵押物")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7贷款类型: " + loanType.getDisplayName())),
                Component.text(MessageUtil.colorize("&7申请金额: &6💰" + String.format("%,.0f", requestedAmount))),
                Component.text(MessageUtil.colorize("&7申请期限: &f" + termMonths + " 月")),
                Component.text(MessageUtil.colorize("&7贷款用途: &f" + loanPurpose)),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e当前抵押: &f" + (collateralType.isEmpty() ? "未选择" : collateralType))),
                Component.text(MessageUtil.colorize("&7选择可用于抵押的物品"))
            ))
            .build();
        gui.setItem(4, header);
        
        // Available collateral types (from CreditService)
        creditService.getAvailableCollateralTypes(player.getUniqueId()).thenAccept(collateralTypes -> {
            if (collateralTypes.isEmpty()) {
                GuiItem noCollateral = ItemBuilder.from(Material.BARRIER)
                    .name(Component.text(MessageUtil.colorize("&c无可用的抵押物")))
                    .lore(Arrays.asList(
                        Component.text(MessageUtil.colorize("&7您没有可用的抵押物")),
                        Component.text(MessageUtil.colorize("&7请先准备抵押物再申请")),
                        Component.text(""),
                        Component.text(MessageUtil.colorize("&7常见抵押物: 钻石装, 稀有材料, 房产"))
                    ))
                    .build();
                gui.setItem(31, noCollateral);
            } else {
                int slot = 19;
                for (int i = 0; i < collateralTypes.size() && slot < 47; i++, slot++) {
                    var collateral = collateralTypes.get(i);
                    
                    double loanValue = collateral.getLoanValue();
                    
                    GuiItem collateralItem = ItemBuilder.from(Material.valueOf(collateral.getMaterial()))
                        .name(Component.text(MessageUtil.colorize("&6" + collateral.getName())))
                        .lore(Arrays.asList(
                            Component.text(MessageUtil.colorize("&7评估价值: &6💰" + String.format("%,.0f", collateral.getValue()))),
                            Component.text(MessageUtil.colorize("&7抵押折扣率: &f" + String.format("%.1f%%", collateral.getDiscountRate() * 100))),
                            Component.text(MessageUtil.colorize("&7可贷金额: &6💰" + String.format("%,.0f", loanValue))),
                            Component.text(""),
                            Component.text(MessageUtil.colorize("&7申请金额覆盖率: &f" + String.format("%.1f%%", loanValue / requestedAmount * 100))),
                            Component.text(MessageUtil.colorize("&a点击查看详情"))
                        ))
                        .build();
                    
                    final String materialName = collateral.getName();
                    final double value = collateral.getValue();
                    collateralItem.setAction(event -> {
                        collateralType = materialName;
                        collateralValue = value;
                        nextStep(gui);
                    });
                    
                    gui.setItem(slot, collateralItem);
                }
            }
        }).exceptionally(ex -> {
            player.sendMessage(MessageUtil.colorize("&c获取抵押物信息失败"));
            return null;
        });
        
        // Navigation
        setupNavigation(gui);
    }
    
    private void setupReviewAndConfirm(Gui gui) {
        // Calculate loan details
        double interestRate = calculateInterestRate();
        double monthlyPayment = calculateMonthlyPayment(interestRate);
        
        // Review header
        GuiItem header = ItemBuilder.from(Material.GREEN_TERRACOTTA)
            .name(Component.text(MessageUtil.colorize("&6&l第5步: 确认申请信息")))
            .build();
        gui.setItem(4, header);
        
        // Summary info
        GuiItem summary = ItemBuilder.from(Material.BOOK)
            .name(Component.text(MessageUtil.colorize("&e&l申请摘要")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7贷款类型: " + loanType.getDisplayName())),
                Component.text(MessageUtil.colorize("&7申请金额: &6💰" + String.format("%,.0f", requestedAmount))),
                Component.text(MessageUtil.colorize("&7申请期限: &f" + termMonths + " 月")),
                Component.text(MessageUtil.colorize("&7贷款用途: &f" + loanPurpose)),
                Component.text(MessageUtil.colorize("&7年利率: &f" + String.format("%.2f%%", interestRate * 100))),
                Component.text(MessageUtil.colorize("&7抵押物: &f" + (collateralType.isEmpty() ? "无" : collateralType + " (💰" + String.format("%,.0f", collateralValue) + ")"))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6预计月供: &f💰" + String.format("%,.0f", monthlyPayment))),
                Component.text(MessageUtil.colorize("&6应付利息: &f💰" + String.format("%,.0f", calculateTotalInterest(interestRate)))),
                Component.text(MessageUtil.colorize("&6总还款额: &f💰" + String.format("%,.0f", requestedAmount + calculateTotalInterest(interestRate)))),
                Component.text(""),
                Component.text(""),
                Component.text(MessageUtil.colorize("&c⚠ &7请仔细确认所有信息")),
                Component.text(MessageUtil.colorize("&7确认后无法直接修改"))
            ))
            .build();
        gui.setItem(22, summary);
        
        // Confirm button
        GuiItem confirmButton = ItemBuilder.from(Material.GREEN_CONCRETE)
            .name(Component.text(MessageUtil.colorize("&a&l提交申请")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7确认申请此贷款")),
                Component.text(MessageUtil.colorize("&7所有信息已填写完整")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&c⚠ &7申请提交后需要审核")),
                Component.text(MessageUtil.colorize("&7审核时间: 通常24小时内"))
            ))
            .build();
        
        confirmButton.setAction(event -> {
            submitApplication();
        });
        
        gui.setItem(28, confirmButton);
        
        // Cancel button
        GuiItem cancelButton = ItemBuilder.from(Material.RED_CONCRETE)
            .name(Component.text(MessageUtil.colorize("&c&l取消申请")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7放弃本次申请")),
                Component.text(MessageUtil.colorize("&7您保存的信息将会清除")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&7随时可以重新申请"))
            ))
            .build();
        
        cancelButton.setAction(event -> {
            cancelApplication();
        });
        
        gui.setItem(34, cancelButton);
        
        // Navigation
        setupNavigation(gui);
    }
    
    private void setupNavigation(Gui gui) {
        // Previous button
        if (currentStep > 1) {
            GuiItem prevButton = ItemBuilder.from(Material.ARROW)
                .name(Component.text(MessageUtil.colorize("&e&l上一步")))
                .build();
            
            prevButton.setAction(event -> {
                currentStep--;
                setupStepContent(gui);
            });
            
            gui.setItem(48, prevButton);
        }
        
        // Close button
        GuiItem closeButton = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.colorize("&c&l关闭")))
            .build();
        
        closeButton.setAction(event -> {
            player.closeInventory();
        });
        
        gui.setItem(53, closeButton);
        
        // Progress indicator
        GuiItem progress = ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text(MessageUtil.colorize("&a&l进度 " + currentStep + "/" + totalSteps)))
            .build();
        
        for (int i = 46; i < 53; i++) {
            if (gui.getInventory().getItem(i) == null) {
                gui.setItem(i, progress);
            }
        }
    }
    
    private void nextStep(Gui gui) {
        currentStep++;
        setupStepContent(gui);
    }
    
    private void submitApplication() {
        player.closeInventory();
        
        player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f正在提交申请..."));
        
        // Submit loan application
        loanService.submitLoanApplication(player, loanType, requestedAmount, termMonths, 
            loanPurpose, collateralType, collateralValue)
            .thenAccept(loanId -> {
                player.sendMessage(MessageUtil.colorize("&6[贷款申请] &a申请提交成功！"));
                player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f申请编号: &e" + loanId));
                player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f预计在24小时内完成审核"));
                player.sendMessage(MessageUtil.colorize("&6[贷款申请] &f请留意聊天栏或邮件通知"));
            })
            .exceptionally(ex -> {
                player.sendMessage(MessageUtil.colorize("&c[贷款申请] 申请提交失败: " + ex.getMessage()));
                return null;
            });
        
        // Clear saved data
        clearApplicationData();
    }
    
    private void cancelApplication() {
        player.closeInventory();
        player.sendMessage(MessageUtil.colorize("&c[贷款申请] 申请已取消"));
        clearApplicationData();
    }
    
    private void clearApplicationData() {
        requestedAmount = 0;
        termMonths = 1;
        loanPurpose = "";
        collateralType = "";
        collateralValue = 0;
        currentStep = 1;
    }
    
    // Calculation methods
    
    private double calculateMaxLoanAmount(com.yae.api.credit.CreditGrade creditGrade) {
        double baseLimit = creditGrade.getMaxCreditLimit();
        double multiplication = loanType.getMaxAmountMultiplier(creditGrade);
        return Math.min(baseLimit * multiplication, baseLimit * 2.0);
    }
    
    private double[] calculatePresetAmounts(double maxAmount) {
        return new double[] {
            Math.min(10000, maxAmount),
            Math.min(50000, maxAmount * 0.25),
            Math.min(100000, maxAmount * 0.5),
            Math.min(200000, maxAmount * 0.75),
            Math.min(500000, maxAmount * 0.9),
            maxAmount
        };
    }
    
    private int[] calculatePresetTerms(int maxTerm) {
        return new int[] {3, 6, 12, 24, 36, Math.min(60, maxTerm), Math.min(120, maxTerm)};
    }
    
    private double calculateInterestRate() {
        return creditService.getCreditGrade(player.getUniqueId())
            .thenApply(grade -> grade.getInterestRate(loanType) * 100)
            .joinOrDefault(0.1); // Default to 10% if calculation fails
    }
    
    private double calculateMonthlyPayment(double annualRate) {
        double monthlyRate = annualRate / 12;
        double principal = requestedAmount;
        int months = termMonths;
        
        if (monthlyRate == 0) {
            return principal / months;
        }
        
        double monthlyPayment = principal * monthlyRate * Math.pow(1 + monthlyRate, months) /
                               (Math.pow(1 + monthlyRate, months) - 1);
        return monthlyPayment;
    }
    
    private double calculateTotalInterest(double annualRate) {
        return termMonths * calculateMonthlyPayment(annualRate) - requestedAmount;
    }
}
