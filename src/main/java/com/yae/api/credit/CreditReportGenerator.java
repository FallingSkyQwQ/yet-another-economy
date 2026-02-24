package com.yae.api.credit;

import com.yae.utils.MessageUtils;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates detailed Chinese credit reports for players
 * Provides comprehensive credit analysis with localized formatting
 */
public class CreditReportGenerator {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
    
    /**
     * Generate comprehensive credit report
     */
    public static String generateCreditReport(@NotNull CreditService.CreditReport report) {
        StringBuilder sb = new StringBuilder();
        
        // Header
        sb.append("&6━━━━━━━━━━━ 信用报告 ━━━━━━━━━━━\n");
        sb.append("&7生成时间: &f").append(LocalDateTime.now().format(DATE_FORMATTER)).append("\n");
        sb.append("&7报告编号: &f").append(generateReportId(report.getPlayerId())).append("\n\n");
        
        // Basic Information
        sb.append("&6【基本信息】\n");
        sb.append("&7玩家ID: &f").append(report.getPlayerId()).append("\n");
        sb.append("&7信用评分: &f").append(report.getScore()).append(" 分\n");
        sb.append("&7信用等级: ").append(report.getGrade().getDisplayName()).append("\n");
        sb.append("&7等级描述: &f").append(report.getGrade().getChineseName()).append("\n");
        sb.append("&7评分时间: &f").append(report.getScoreData().getCalculatedAt().format(DATE_FORMATTER)).append("\n\n");
        
        // Score Analysis
        sb.append("&6【评分分析】\n");
        sb.append(getScoreAnalysis(report.getScore())).append("\n\n");
        
        // Credit Grade Information
        sb.append("&6【信用等级信息】\n");
        sb.append("&7当前等级: ").append(report.getGrade().getDisplayName())
          .append(" (&f").append(report.getGrade().getMinScore()).append("-").append(report.getGrade().getMaxScore()).append(" 分&7)\n");
        sb.append("&7基础年利率: &6").append(String.format("%.2f%%", report.getGrade().getBaseInterestRate() * 100)).append("\n");
        sb.append("&7最高信用额度: &6💰 ").append(formatCurrency(report.getGrade().getMaxCreditLimit())).append("\n\n");
        
        // Loan Qualification
        sb.append("&6【贷款资格】\n");
        sb.append(getLoanQualificationSection(report.getGrade())).append("\n");
        
        // Credit Factors
        sb.append("&6【信用因子分析】\n");
        sb.append(getCreditFactorsSection(report)).append("\n");
        
        // Recommendations
        sb.append("&6【信用建议】\n");
        sb.append(getCreditRecommendations(report.getScore())).append("\n");
        
        // Footer
        sb.append("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("&7本报告由 &6YAE经济系统信用评估中心&7 提供\n");
        sb.append("&7客服QQ: &f123456789 | &7官网: &fwww.yae-credit.com\n");
        sb.append("&7报告仅供参考，最终审批结果以系统评估为准\n");
        
        return sb.toString();
    }
    
    /**
     * Generate simplified credit summary
     */
    public static String generateCreditSummary(@NotNull CreditService.CreditReport report) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("&6━━━ 信用概览 ━━━\n");
        sb.append("&7评分: &f").append(report.getScore()).append(" 分\n");
        sb.append("&7等级: ").append(report.getGrade().getDisplayName()).append("\n");
        sb.append("&7状态: ").append(getCreditStatus(report.getScore())).append("\n");
        sb.append("&7额度: &6💰 ").append(formatCurrency(report.getGrade().getMaxCreditLimit())).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Generate credit change notification
     */
    public static String generateChangeNotification(int oldScore, int newScore, CreditGrade oldGrade, CreditGrade newGrade) {
        StringBuilder sb = new StringBuilder();
        
        int scoreChange = newScore - oldScore;
        boolean gradeChanged = !oldGrade.equals(newGrade);
        
        sb.append("&6━━━━━━ 信用评分更新 ━━━━━━\n");
        sb.append("&7您的信用评分已更新！\n\n");
        
        // Score change
        if (scoreChange > 0) {
            sb.append("&a✓ 评分上升: &f").append(oldScore).append(" → ").append(newScore).append(" 分 (+").append(scoreChange).append(")\n");
        } else if (scoreChange < 0) {
            sb.append("&c✗ 评分下降: &f").append(oldScore).append(" → ").append(newScore).append(" 分 (").append(scoreChange).append(")\n");
        } else {
            sb.append("&7→ 评分无变化: ").append(newScore).append(" 分\n");
        }
        
        // Grade change
        if (gradeChanged) {
            sb.append("\n&7等级变化: ")
              .append(oldGrade.getDisplayName()).append(" &f→ ")
              .append(newGrade.getDisplayName()).append("\n");
            
            if (newGrade.ordinal() < oldGrade.ordinal()) {
                sb.append("&a恭喜！您的信用等级已提升！\n");
            } else {
                sb.append("&c请注意：您的信用等级已下降\n");
            }
        }
        
        // Impact summary
        sb.append("\n&6【等级影响】\n");
        sb.append("&7基础年利率: ")
          .append(String.format("%.2f%%", oldGrade.getBaseInterestRate() * 100))
          .append(" → ")
          .append(String.format("%.2f%%", newGrade.getBaseInterestRate() * 100))
          .append("\n");
        
        sb.append("&7最高信用额度: &6💰 ")
          .append(formatCurrency(oldGrade.getMaxCreditLimit()))
          .append(" → 💰 ")
          .append(formatCurrency(newGrade.getMaxCreditLimit()))
          .append("\n");
        
        sb.append("\n&7感谢您的持续关注信用健康！&f\n");
        sb.append("&6━━━━━━━━━━━━━━━━━━━━\n");
        
        return sb.toString();
    }
    
    /**
     * Get detailed score analysis
     */
    private static String getScoreAnalysis(int score) {
        if (score >= 750) {
            return "您的信用评分非常优秀，达到了最高等级A级。这表明您具有极强的信用管理能力和还款能力。您可以享受最低的年利率和最高的信用额度。继续保持良好的信用习惯！";
        } else if (score >= 650) {
            return "您的信用评分良好，属于B级信用等级。您的信用记录相对稳定，具备较好的还款能力和信用管理能力。您可以申请大部分贷款产品，享受较为优惠的利率。";
        } else if (score >= 550) {
            return "您的信用评分处于一般水平，属于C级信用等级。虽然您的信用状况基本合格，但仍有改进空间。建议您加强财务管理，按时还款，合理使用信用额度，以提升信用评分。";
        } else if (score >= 450) {
            return "您的信用评分较低，属于D级信用等级。这可能意味着您有一些信用问题需要解决。建议您重点关注信用修复，改善还款记录，减少债务负担，逐步提升信用状况。";
        } else {
            return "您的信用评分很低，属于F级信用等级。这表明您存在严重的信用问题，可能有多笔逾期还款或违约记录。强烈建议您立即采取行动修复信用，包括清偿债务、与债权人协商等。";
        }
    }
    
    /**
     * Get loan qualification section
     */
    private static String getLoanQualificationSection(CreditGrade grade) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("&7信用贷款: ")
          .append(grade.qualifiesForLoan(com.yae.api.credit.LoanType.CREDIT) ? "&a✓ 符合" : "&c✗ 不符合")
          .append("      ");
        sb.append("&7抵押贷款: ")
          .append(grade.qualifiesForLoan(com.yae.api.credit.LoanType.MORTGAGE) ? "&a✓ 符合" : "&c✗ 不符合")
          .append("\n");
        
        sb.append("&7商业贷款: ")
          .append(grade.qualifiesForLoan(com.yae.api.credit.LoanType.BUSINESS) ? "&a✓ 符合" : "&c✗ 不符合")
          .append("      ");
        sb.append("&7应急贷款: ")
          .append("&a✓ 符合") // Emergency loans have lower requirements
          .append("\n");
        
        return sb.toString();
    }
    
    /**
     * Get credit factors analysis
     */
    private static String getCreditFactorsSection(CreditService.CreditReport report) {
        StringBuilder sb = new StringBuilder();
        
        int score = report.getScore();
        
        // Analyze different factors
        sb.append("&7交易频率: ").append(getFactorRating(getTransactionFrequencyRating(score))).append("\n");
        sb.append("&7交易金额: ").append(getFactorRating(getTransactionAmountRating(score))).append("\n");
        sb.append("&7账户活跃: ").append(getFactorRating(getAccountActivityRating(score))).append("\n");
        sb.append("&7还款历史: ").append(getFactorRating(getRepaymentHistoryRating(score))).append("\n");
        sb.append("&7存款记录: ").append(getFactorRating(getDepositRating(score))).append("\n");
        sb.append("&7信用利用: ").append(getFactorRating(getCreditUtilizationRating(score))).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Get credit recommendations
     */
    private static String getCreditRecommendations(int score) {
        if (score >= 750) {
            return "继续保持良好的信用习惯！\n&7• 按时还款，保持零逾期记录\n&7• 合理管理信用额度，保持低利用率\n&7• 定期检查信用报告，确保信息准确";
        } else if (score >= 650) {
            return "提升信用评分的建议：\n&7• 确保所有账单按时足额还款\n&7• 减少信用额度使用率至30%以下\n&7• 保持稳定的交易活动\n&7• 避免频繁申请新的信用产品";
        } else if (score >= 550) {
            return "改善信用状况的行动方案：\n&7• 立即清偿所有逾期债务\n&7• 建立自动还款机制避免逾期\n&7• 适度增加定期存款持有\n&7• 保持账户活跃度，增加登录频次";
        } else if (score >= 450) {
            return "紧急信用修复措施：\n&7• 与债权人协商制定还款计划\n&7• 优先偿还高利率债务\n&7• 停止新的信用申请\n&7• 寻求专业信用咨询服务";
        } else {
            return "严重信用危机处理：\n&7• 立即停止借款行为\n&7• 制定详细的债务清偿计划\n&7• 与所有债权人主动沟通\n&7• 考虑债务重组或个人破产保护\n&7• 寻求法律援助和信用修复服务";
        }
    }
    
    /**
     * Format currency amount
     */
    private static String formatCurrency(double amount) {
        if (amount >= 1000000) {
            return String.format("%.1f万", amount / 10000);
        } else if (amount >= 10000) {
            return String.format("%.0f万", amount / 10000);
        } else {
            return String.format("%.0f", amount);
        }
    }
    
    /**
     * Generate unique report ID
     */
    private static String generateReportId(UUID playerId) {
        return "CR" + System.currentTimeMillis() + "-" + playerId.toString().substring(0, 8);
    }
    
    /**
     * Get credit status based on score
     */
    private static String getCreditStatus(int score) {
        if (score >= 750) return "&a优秀";
        else if (score >= 650) return "&2良好";
        else if (score >= 550) return "&e一般";
        else if (score >= 450) return "&c较差";
        else return "&4很差";
    }
    
    /**
     * Get factor rating functions (simplified implementations)
     */
    private static int getTransactionFrequencyRating(int score) { return Math.min(5, score / 150); }
    private static int getTransactionAmountRating(int score) { return Math.min(5, score / 170); }
    private static int getAccountActivityRating(int score) { return Math.min(5, score / 150); }
    private static int getRepaymentHistoryRating(int score) { return Math.min(5, score / 140); }
    private static int getDepositRating(int score) { return Math.min(5, score / 170); }
    private static int getCreditUtilizationRating(int score) { return Math.min(5, score / 160); }
    
    /**
     * Convert rating number to stars
     */
    private static String getFactorRating(int rating) {
        String[] stars = {"★☆☆☆☆", "★★☆☆☆", "★★★☆☆", "★★★★☆", "★★★★★"};
        return rating > 0 && rating <= 5 ? ("§e" + stars[rating - 1]) : "§8★☆☆☆☆";
    }
}
