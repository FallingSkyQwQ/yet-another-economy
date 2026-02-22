package com.yae.api.gui;

import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.CreditService;
import com.yae.api.credit.CreditScoreCalculator;
import com.yae.utils.MessageUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Credit Rating GUI - Shows player's credit score and rating information
 */
public class CreditRatingGUI {
    
    private final CreditService creditService;
    private final Player player;
    
    public CreditRatingGUI(CreditService creditService, Player player) {
        this.creditService = creditService;
        this.player = player;
    }
    
    /**
     * Open the credit rating GUI
     */
    public void open() {
        Gui gui = Gui.gui()
            .title(Component.text(MessageUtil.colorize("&6&l信用评分 &f- &e" + player.getName())))
            .rows(6)
            .create();
        
        // Load credit data asynchronously
        creditService.getCreditScore(player.getUniqueId()).thenAccept(creditScore -> {
            creditService.getCreditGrade(player.getUniqueId()).thenAccept(creditGrade -> {
                // Update GUI with credit information
                updateGUI(gui, creditScore, creditGrade);
                
                // Set up GUI items
                setupGUIItems(gui, creditScore, creditGrade);
                
                // Open GUI
                gui.open(player);
            });
        });
    }
    
    private void updateGUI(Gui gui, int creditScore, CreditGrade creditGrade) {
        // Main credit score display
        GuiItem scoreItem = ItemBuilder.from(createCreditScoreItem(creditScore, creditGrade))
            .name(Component.text(MessageUtil.colorize("&6&l您的信用评分: " + creditScore)))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7等级: &f" + creditGrade.getDisplayName())),
                Component.text(MessageUtil.colorize("&7范围: &f" + creditGrade.getMinScore() + " - " + creditGrade.getMaxScore())),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e&l信用等级说明:")),
                Component.text(MessageUtil.colorize("&7" + getGradeDescription(creditGrade))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&6&l贷款资格:")),
                Component.text(MessageUtil.colorize(getLoanQualifications(creditGrade))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&a&l提升建议:")),
                Component.text(MessageUtil.colorize(getImprovementSuggestions(creditScore)))
            ))
            .build();
        
        gui.setItem(13, scoreItem);
    }
    
    private void setupGUIItems(Gui gui, int creditScore, CreditGrade creditGrade) {
        // Credit history button
        GuiItem historyItem = ItemBuilder.from(Material.BOOK)
            .name(Component.text(MessageUtil.colorize("&a&l信用历史")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7查看您的信用历史记录")),
                Component.text(MessageUtil.colorize("&7包括所有评分变化详情")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击打开"))
            ))
            .build();
        
        historyItem.setAction(event -> {
            player.closeInventory();
            openCreditHistoryGUI();
        });
        
        gui.setItem(20, historyItem);
        
        // Loan eligibility button
        GuiItem eligibilityItem = ItemBuilder.from(Material.EMERALD)
            .name(Component.text(MessageUtil.colorize("&e&l贷款资格")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7查看您可申请的贷款类型")),
                Component.text(MessageUtil.colorize("&7以及对应的利率和限额")),
                Component.text(""),
                Component.text(MessageUtil.colorize(getDetailedEligibilityInfo(creditGrade))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击打开"))
            ))
            .build();
        
        eligibilityItem.setAction(event -> {
            player.closeInventory();
            openLoanEligibilityGUI();
        });
        
        gui.setItem(22, eligibilityItem);
        
        // Credit improvement button
        GuiItem improvementItem = ItemBuilder.from(Material.COMPASS)
            .name(Component.text(MessageUtil.colorize("&b&l提升信用")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7了解如何提升信用评分")),
                Component.text(MessageUtil.colorize("&7改善您的信用等级")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&a建议行动:")),
                Component.text(MessageUtil.colorize(getDetailedSuggestions(creditScore))),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击打开"))
            ))
            .build();
        
        improvementItem.setAction(event -> {
            player.closeInventory();
            openCreditImprovementGUI();
        });
        
        gui.setItem(24, improvementItem);
        
        // Refresh button
        GuiItem refreshItem = ItemBuilder.from(Material.CLOCK)
            .name(Component.text(MessageUtil.colorize("&6&l刷新")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7重新计算您的信用评分")),
                Component.text(MessageUtil.colorize("&7更新最新数据")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&7评分每日自动更新")),
                Component.text(MessageUtil.colorize("&7您也可以手动刷新")),
                Component.text(""),
                Component.text(MessageUtil.colorize("&e点击刷新"))
            ))
            .build();
        
        refreshItem.setAction(event -> {
            player.closeInventory();
            
            // Show refresh message
            player.sendMessage(MessageUtil.colorize("&6[信用系统] &f正在刷新信用评分..."));
            
            // Recalculate credit score
            creditService.calculateCreditScore(player.getUniqueId()).thenAccept(newScore -> {
                player.sendMessage(MessageUtil.colorize("&6[信用系统] &a信用评分已更新！新评分: &e" + newScore));
                
                // Reopen GUI with new score
                open();
            });
        });
        
        gui.setItem(49, refreshItem);
        
        // Close button
        GuiItem closeItem = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(MessageUtil.colorize("&c&l关闭")))
            .lore(Arrays.asList(
                Component.text(MessageUtil.colorize("&7关闭信用评分界面")),
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
    
    private ItemStack createCreditScoreItem(int creditScore, CreditGrade creditGrade) {
        Material material;
        
        // Choose material based on credit grade
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
    
    private String getGradeDescription(CreditGrade creditGrade) {
        switch (creditGrade) {
            case A:
                return "信用极佳，享受最低利率和最高额度";
            case B:
                return "信用良好，利率优惠，额度较高";
            case C:
                return "信用一般，标准利率和额度";
            case D:
                return "信用较差，较高利率和较低额度";
            case F:
                return "信用很差，很难获得贷款";
            default:
                return "未知信用等级";
        }
    }
    
    private String getLoanQualifications(CreditGrade creditGrade) {
        StringBuilder sb = new StringBuilder();
        
        if (creditGrade.qualifiesForLoan(LoanType.CREDIT)) {
            sb.append("&a✓ 信用贷款\n");
        } else {
            sb.append("&c✗ 信用贷款\n");
        }
        
        if (creditGrade.qualifiesForLoan(LoanType.MORTGAGE)) {
            sb.append("&a✓ 抵押贷款\n");
        } else {
            sb.append("&c✗ 抵押贷款\n");
        }
        
        if (creditGrade.qualifiesForLoan(LoanType.BUSINESS)) {
            sb.append("&a✓ 商业贷款");
        } else {
            sb.append("&c✗ 商业贷款");
        }
        
        return sb.toString();
    }
    
    private String getImprovementSuggestions(int creditScore) {
        if (creditScore >= 800) {
            return "&a保持良好的信用记录！";
        } else if (creditScore >= 740) {
            return "&a继续保持，还有提升空间";
        } else if (creditScore >= 670) {
            return "&e按时还款，减少逾期记录";
        } else if (creditScore >= 580) {
            return "&c改善还款习惯，积累良好记录";
        } else {
            return "&c重建信用，避免违约和逾期";
        }
    }
    
    private String getDetailedEligibilityInfo(CreditGrade creditGrade) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("&7信用贷款: ").append(creditGrade.qualifiesForLoan(LoanType.CREDIT) ? "&a可申请" : "&c不符合").append("\n");
        sb.append("&7抵押贷款: ").append(creditGrade.qualifiesForLoan(LoanType.MORTGAGE) ? "&a可申请" : "&c不符合").append("\n");
        sb.append("&7商业贷款: ").append(creditGrade.qualifiesForLoan(LoanType.BUSINESS) ? "&a可申请" : "&c不符合").append("\n");
        sb.append("&7应急贷款: &a可申请");
        
        return sb.toString();
    }
    
    private String getDetailedSuggestions(int creditScore) {
        StringBuilder sb = new StringBuilder();
        
        if (creditScore < 650) {
            sb.append("&7• 按时偿还现有贷款\n");
            sb.append("&7• 减少信用卡使用\n");
            sb.append("&7• 避免新的信用查询\n");
            sb.append("&7• 保持账户稳定");
        } else if (creditScore < 740) {
            sb.append("&7• 多样化信用类型\n");
            sb.append("&7• 增加信用历史长度\n");
            sb.append("&7• 保持低信用利用率\n");
            sb.append("&7• 避免频繁申请新信用");
        } else {
            sb.append("&7• 继续保持优秀记录\n");
            sb.append("&7• 监控信用报告\n");
            sb.append("&7• 维护多样化信用组合\n");
            sb.append("&7• 享受优质信用待遇");
        }
        
        return sb.toString();
    }
    
    private void openCreditHistoryGUI() {
        // This would open a detailed credit history GUI
        player.sendMessage(MessageUtil.colorize("&6[信用系统] &f信用历史功能开发中..."));
    }
    
    private void openLoanEligibilityGUI() {
        // This would open a detailed loan eligibility GUI
        LoanEligibilityGUI eligibilityGUI = new LoanEligibilityGUI(creditService, player);
        eligibilityGUI.open();
    }
    
    private void openCreditImprovementGUI() {
        // This would open a credit improvement suggestions GUI
        player.sendMessage(MessageUtil.colorize("&6[信用系统] &f信用提升建议功能开发中..."));
    }
}
