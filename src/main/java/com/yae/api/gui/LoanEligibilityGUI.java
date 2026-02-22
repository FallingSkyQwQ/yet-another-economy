package com.yae.api.gui;

import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.CreditService;
import com.yae.api.credit.LoanType;
import com.yae.utils.MessageUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * Loan Eligibility GUI - Shows player's loan eligibility details
 */
public class LoanEligibilityGUI {
    
    private final CreditService creditService;
    private final Player player;
    
    public LoanEligibilityGUI(CreditService creditService, Player player) {
        this.creditService = creditService;
        this.player = player;
    }
    
    /**
     * Open the loan eligibility GUI
     */
    public void open() {
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtil.colorize("&6&l贷款资格 &f- &e" + player.getName())))
            .rows(6)
            .create();
        
        // Load credit data and populate GUI
        creditService.getCreditScore(player.getUniqueId()).thenAccept(creditScore -> {
            creditService.getCreditGrade(player.getUniqueId()).thenAccept(creditGrade -> {
                
                setupLoanTypeItems(gui, creditScore, creditGrade);
                setupNavigationItems(gui);
                
                gui.open(player);
            });
        });
    }
    
    private void setupLoanTypeItems(Gui gui, int creditScore, CreditGrade creditGrade) {
        // Credit Loan Information
        boolean qualifiesForCredit = creditGrade.qualifiesForLoan(LoanType.CREDIT);
        GuiItem creditLoanItem = ItemBuilder.from(createLoanTypeItem(LoanType.CREDIT, qualifiesForCredit))
            .name(Component.text(MessageUtil.colorize("&e&l" + LoanType.CREDIT.getDisplayName())))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7类型: &f" + LoanType.CREDIT.getChineseName())),
                Component.text(MessageUtil.colorize("&7状态: " + (qualifiesForCredit ? "&a✓ 符合条件" : "&c✗ 不符合条件"))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e基本信息:")),
                Component.text(MessageUtil.colorize("&7• 最高期限: &f" + LoanType.CREDIT.getMaxTermMonths() + " 月")),
                Component.text(MessageUtil.colorize("&7• 是否需要抵押: &f否")),
                Component.text(MessageUtil.colorize("&7• 风险等级: &f" + (LoanType.CREDIT.isHighRisk() ? "&c高" : "&a低"))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6您的信息:")),
                Component.text(MessageUtil.colorize("&7• 信用要求: &f最少600分")),
                Component.text(MessageUtil.colorize("&7• 您的评分: &f" + creditScore + " (等级" + creditGrade.getChineseName() + ")")),
                Component.text(MessageUtil.colorize("&7• 最高额度: &6💰" + String.format("%.0f", creditGrade.getMaxCreditLimit()))),
                Component.text(MessageUtil.colorize("&7• 基础利率: &f" + String.format("%.2f%%", creditGrade.getInterestRate(LoanType.CREDIT) * 100))),
                Component.text(""),
                Component.text(MessageUtil.colorize(qualifiesForCredit ? "&a点击查看申请详情" : "&c信用分不足，无法申请"))
            ))
            .build();
        
        if (qualifiesForCredit) {
            creditLoanItem.setAction(event -> openLoanApplicationGUI(LoanType.CREDIT));
        }
        
        gui.setItem(10, creditLoanItem);
        
        // Mortgage Loan Information
        boolean qualifiesForMortgage = creditGrade.qualifiesForLoan(LoanType.MORTGAGE);
        GuiItem mortgageLoanItem = ItemBuilder.from(createLoanTypeItem(LoanType.MORTGAGE, qualifiesForMortgage))
            .name(Component.text(MessageUtil.colorize("&6&l" + LoanType.MORTGAGE.getDisplayName())))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7类型: &f" + LoanType.MORTGAGE.getChineseName())),
                Component.text(MessageUtil.colorize("&7状态: " + (qualifiesForMortgage ? "&a✓ 符合条件" : "&c✗ 不符合条件"))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e基本信息:")),
                Component.text(MessageUtil.colorize("&7• 最高期限: &f" + LoanType.MORTGAGE.getMaxTermMonths() + " 月")),
                Component.text(MessageUtil.colorize("&7• 是否需要抵押: &f是")),
                Component.text(MessageUtil.colorize("&7• 风险等级: &f" + (LoanType.MORTGAGE.isHighRisk() ? "&c高" : "&a低"))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6您的信息:")),
                Component.text(MessageUtil.colorize("&7• 信用要求: &f最少650分")),
                Component.text(MessageUtil.colorize("&7• 您的评分: &f" + creditScore + " (等级" + creditGrade.getChineseName() + ")")),
                Component.text(MessageUtil.colorize("&7• 最高额度: &6💰" + String.format("%.0f", creditGrade.getMaxCreditLimit() * 2))),
                Component.text(MessageUtil.colorize("&7• 基础利率: &f" + String.format("%.2f%%", creditGrade.getInterestRate(LoanType.MORTGAGE) * 100))),
                Component.text(""),
                Component.text(MessageUtil.colorize(qualifiesForMortgage ? "&a点击查看申请详情" : "&c信用分不足，无法申请"))
            ))
            .build();
        
        if (qualifiesForMortgage) {
            mortgageLoanItem.setAction(event -> openLoanApplicationGUI(LoanType.MORTGAGE));
        }
        
        gui.setItem(12, mortgageLoanItem);
        
        // Business Loan Information
        boolean qualifiesForBusiness = creditGrade.qualifiesForLoan(LoanType.BUSINESS);
        GuiItem businessLoanItem = ItemBuilder.from(createLoanTypeItem(LoanType.BUSINESS, qualifiesForBusiness))
            .name(Component.text(MessageUtil.colorize("&b&l" + LoanType.BUSINESS.getDisplayName())))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7类型: &f" + LoanType.BUSINESS.getChineseName())),
                Component.text(MessageUtil.colorize("&7状态: " + (qualifiesForBusiness ? "&a✓ 符合条件" : "&c✗ 不符合条件"))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e基本信息:")),
                Component.text(MessageUtil.colorize("&7• 最高期限: &f" + LoanType.BUSINESS.getMaxTermMonths() + " 月")),
                Component.text(MessageUtil.colorize("&7• 是否需要抵押: &f可选择")),
                Component.text(MessageUtil.colorize("&7• 风险等级: &f" + (LoanType.BUSINESS.isHighRisk() ? "&c高" : "&a低"))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6您的信息:")),
                Component.text(MessageUtil.colorize("&7• 信用要求: &f最少700分")),
                Component.text(MessageUtil.colorize("&7• 您的评分: &f" + creditScore + " (等级" + creditGrade.getChineseName() + ")")),
                Component.text(MessageUtil.colorize("&7• 最高额度: &6💰" + String.format("%.0f", creditGrade.getMaxCreditLimit() * 1.5))),
                Component.text(MessageUtil.colorize("&7• 基础利率: &f" + String.format("%.2f%%", creditGrade.getInterestRate(LoanType.BUSINESS) * 100))),
                Component.text(""),
                Component.text(MessageUtil.colorize(qualifiesForBusiness ? "&a点击查看申请详情" : "&c信用分不足，无法申请"))
            ))
            .build();
        
        if (qualifiesForBusiness) {
            businessLoanItem.setAction(event -> openLoanApplicationGUI(LoanType.BUSINESS));
        }
        
        gui.setItem(14, businessLoanItem);
        
        // Emergency Loan Information
        GuiItem emergencyLoanItem = ItemBuilder.from(createLoanTypeItem(LoanType.EMERGENCY, true))
            .name(Component.text(MessageUtil.colorize("&c&l" + LoanType.EMERGENCY.getDisplayName())))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7类型: &f" + LoanType.EMERGENCY.getChineseName())),
                Component.text(MessageUtil.colorize("&7状态: &a✓ 符合条件")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e基本信息:")),
                Component.text(MessageUtil.colorize("&7• 最高期限: &f" + LoanType.EMERGENCY.getMaxTermMonths() + " 月")),
                Component.text(MessageUtil.colorize("&7• 是否需要抵押: &f无")),
                Component.text(MessageUtil.colorize("&7• 风险等级: &c高风险")),
                Component.text(MessageUtil.colorize("&7• 信用要求: &f最少500分")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6您的信息:")),
                Component.text(MessageUtil.colorize("&7• 您的评分: &f" + creditScore + " (等级" + creditGrade.getChineseName() + ")")),
                Component.text(MessageUtil.colorize("&7• 最高额度: &6💰" + String.format("%.0f", Math.min(creditGrade.getMaxCreditLimit() * 0.3, 50000)))),
                Component.text(MessageUtil.colorize("&7• 基础利率: &c" + String.format("%.2f%%", creditGrade.getInterestRate(LoanType.EMERGENCY) * 100))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&c⚠ &7应急贷款利率较高，请谨慎使用")),
                Component.text(MessageUtil.colorize("&a点击查看申请详情"))
            ))
            .build();
        
        if (creditScore >= 500) {
            emergencyLoanItem.setAction(event -> openLoanApplicationGUI(LoanType.EMERGENCY));
        }
        
        gui.setItem(16, emergencyLoanItem);
        
        // Credit Score Summary
        GuiItem summaryItem = ItemBuilder.from(createCreditScoreItem(creditScore, creditGrade))
            .name(Component.text(MessageUtil.colorize("&6&l信用摘要")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7您的信用评分: &f" + creditScore)),
                Component.text(MessageUtil.colorize("&7信用等级: " + creditGrade.getDisplayName())),
                Component.text(MessageUtil.colorize("&7等级范围: &f" + creditGrade.getMinScore() + " - " + creditGrade.getMaxScore())),
                Component.text(""),
                Component.text(MessageUtil.colorize("&7符合" + (getQualifiedLoanTypes(creditGrade).length) + "种贷款申请条件"))
            ))
            .build();
        
        gui.setItem(31, summaryItem);
    }
    
    private void setupNavigationItems(Gui gui) {
        // Back to credit rating button
        GuiItem backItem = ItemBuilder.from(Material.ARROW)
            .name(Component.text(MessageUtil.colorize("&e&l返回信用评分")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7返回信用评分主界面")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击返回"))
            ))
            .build();
        
        backItem.setAction(event -> {
            player.closeInventory();
            CreditRatingGUI creditRatingGUI = new CreditRatingGUI(creditService, player);
            creditRatingGUI.open();
        });
        
        gui.setItem(45, backItem);
        
        // Refresh button
        GuiItem refreshItem = ItemBuilder.from(Material.CLOCK)
            .name(Component.text(MessageUtil.colorize("&6&l刷新")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7重新计算贷款资格")),
                Component.text(MessageUtil.colorize("&7基于最新信用评分")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击刷新"))
            ))
            .build();
        
        refreshItem.setAction(event -> {
            player.closeInventory();
            open();
        });
        
        gui.setItem(49, refreshItem);
        
        // Close button
        GuiItem closeItem = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.colorize("&c&l关闭")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7关闭贷款资格界面")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击关闭"))
            ))
            .build();
        
        closeItem.setAction(event -> player.closeInventory());
        gui.setItem(53, closeItem);
        
        // Fill empty slots
        GuiItem filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
            .name(Component.text(""))
            .build();
        
        for (int i = 0; i < 54; i++) {
            if (gui.getInventory().getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }
    
    private ItemStack createLoanTypeItem(LoanType loanType, boolean qualifies) {
        Material material;
        switch (loanType) {
            case CREDIT:
                material = qualifies ? Material.EMERALD : Material.REDSTONE;
                break;
            case MORTGAGE:
                material = qualifies ? Material.DIAMOND : Material.REDSTONE;
                break;
            case BUSINESS:
                material = qualifies ? Material.GOLD_INGOT : Material.REDSTONE;
                break;
            case EMERGENCY:
                material = qualifies ? Material.FIRE_CHARGE : Material.REDSTONE;
                break;
            default:
                material = qualifies ? Material.PAPER : Material.REDSTONE;
        }
        
        return new ItemStack(material);
    }
    
    private ItemStack createCreditScoreItem(int creditScore, CreditGrade creditGrade) {
        Material material;
        
        switch (creditGrade) {
            case A:
                material = Material.GOLD_BLOCK;
                break;
            case B:
                material = Material.EMERALD_BLOCK;
                break;
            case C:
                material = Material.DIAMOND_BLOCK;
                break;
            case D:
                material = Material.IRON_BLOCK;
                break;
            case F:
                material = Material.REDSTONE_BLOCK;
                break;
            default:
                material = Material.COAL_BLOCK;
        }
        
        return new ItemStack(material);
    }
    
    private LoanType[] getQualifiedLoanTypes(CreditGrade creditGrade) {
        return Arrays.stream(LoanType.values())
            .filter(loanType -> {
                if (loanType == LoanType.EMERGENCY) {
                    return true; // Emergency loans always available
                }
                return creditGrade.qualifiesForLoan(loanType);
            })
            .toArray(LoanType[]::new);
    }
    
    private void openLoanApplicationGUI(LoanType loanType) {
        player.closeInventory();
        player.sendMessage(MessageUtil.colorize("&6[贷款系统] &f正在打开" + loanType.getDisplayName() + "申请界面..."));
        
        // This would open the loan application GUI
        // LoanApplicationGUI applicationGUI = new LoanApplicationGUI(creditService, player, loanType);
        // applicationGUI.open();
    }
}
