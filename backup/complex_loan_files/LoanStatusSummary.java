
import com.yae.api.credit.LoanType;
import com.yae.api.loan.risk.LoanRiskLevel;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Summary of a borrower's loan status
 * Used for eligibility checking and risk assessment
 */
public class LoanStatusSummary {
    
    private int activeLoanCount;
    private int overdueLoanCount;
    private double totalCurrentBalance;
    private double totalOverdueAmount;
    private double totalMonthlyPayment;
    private int totalOverduePayments;
    private boolean hasOverdueLoans;
    private LocalDateTime lastOverdueDate;
    private LoanRiskLevel riskLevel;
    
    public LoanStatusSummary() {
        this.activeLoanCount = 0;
        this.overdueLoanCount = 0;
        this.totalCurrentBalance = 0;
        this.totalOverdueAmount = 0;
        this.totalMonthlyPayment = 0;
        this.totalOverduePayments = 0;
        this.hasOverdueLoans = false;
        this.riskLevel = LoanRiskLevel.LOW;
    }
    
    /**
     * Check if borrower has clean loan history
     */
    public boolean isClean() {
        return overdueLoanCount == 0 && totalOverdueAmount <= 0 && !hasOverdueLoans;
    }
    
    /**
     * Calculate total repayment burden
     */
    public double getTotalRepaymentBurden() {
        return totalCurrentBalance + totalOverdueAmount;
    }
    
    /**
     * Check if borrower qualifies for new loan
     */
    public boolean qualifiesForNewLoan() {
        // Maximum 3 active loans
        if (activeLoanCount >= 3) return false;
        
        // No overdue loans
        if (hasOverdueLoans) return false;
        
        // Total burden within limits
        double maxBurden = 200000; // Configurable maximum
        if (getTotalRepaymentBurden() > maxBurden) return false;
        
        // Monthly payment within limits
        double maxMonthlyPayment = 0.4 * 10000; // 40% of estimated monthly income
        if (totalMonthlyPayment > maxMonthlyPayment) return false;
        
        return true;
    }
    
    /**
     * Check if borrower qualifies for specific loan type
     */
    public boolean qualifiesForLoanType(LoanType loanType) {
        switch (loanType) {
            case CREDIT:
                return isClean() && activeLoanCount <= 1;
            case MORTGAGE:
                return isClean() && activeLoanCount <= 2;
            case BUSINESS:
                return isClean() && activeLoanCount <= 1;
            case EMERGENCY:
                return overdueLoanCount <= 1 && totalOverduePayments <= 1;
            default:
                return qualifiesForNewLoan();
        }
    }
    
    /**
     * Get risk assessment for borrower
     */
    public String getRiskAssessment() {
        StringBuilder assessment = new StringBuilder();
        
        assessment.append("贷款状态风险评估:\n");
        assessment.append(String.format("活跃贷款数: %d (风险等级: %s)\n", activeLoanCount, riskLevel.getChineseName()));
        
        if (hasOverdueLoans) {
            assessment.append(String.format("⚠️ 存在逾期贷款 (%d笔，总金额: ¥%.2f)\n", overdueLoanCount, totalOverdueAmount));
            assessment.append("建议: 建议先处理所有逾期贷款\n");
        } else {
            assessment.append("✅ 无逾期记录\n");
        }
        
        assessment.append(String.format("总还款负担: ¥%.2f (月还款: ¥%.2f)",
                             getTotalRepaymentBurden(), totalMonthlyPayment));
        
        assessment.append("当前状态: ").append(isClean() ? "优秀" : "需改进");
        
        return assessment.toString();
    }
    
    /**
     * Generate recommendations based on loan status
     */
    public List<String> generateRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        if (hasOverdueLoans) {
            recommendations.add("请优先处理所有逾期贷款");
            recommendations.add("建议制定稳定还款计划，避免信用进一步受损");
            recommendations.add("可考虑寻求财务咨询或债务重组");
        }
        
        if (activeLoanCount >= 3) {
            recommendations.add("当前贷款数量已达上限，不建议申请新贷款");
            recommendations.add("可考虑提前还清部分现有贷款");
        }
        
        if (getTotalRepaymentBurden() > 150000) {
            recommendations.add("还款负担较重，建议谨慎申请新贷款");
            recommendations.add("建议适当调整消费和储蓄计划");
        }
        
        if (totalMonthlyPayment > 4000) {
            recommendations.add("月度还款额较高，建议优化债务结构");
            recommendations.add("可考虑延长贷款期限降低月供");
        }
        
        if (isClean() && activeLoanCount < 2) {
            recommendations.add("信用记录良好，可考虑申请适度贷款");
            recommendations.add("建议继续保持良好的还款记录");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("状态正常，可按计划还款");
        }
        
        return recommendations;
    }
    
    /**
     * Calculate risk level based on loan status
     */
    public void calculateRiskLevel() {
        int riskScore = 0;
        
        // Overdue Loans Risk
        if (hasOverdueLoans) {
            riskScore += 50;
            if (overdueLoanCount > 1) riskScore += 20;
            if (totalOverdueAmount > 10000) riskScore += 15;
        }
        
        // Active Loans Risk
        riskScore += (activeLoanCount * 10);
        
        // Burden Risk
        if (getTotalRepaymentBurden() > 100000) riskScore += 15;
        if (getTotalRepaymentBurden() > 150000) riskScore += 15;
        
        // Monthly Payment Risk
        if (totalMonthlyPayment > 3000) riskScore += 10;
        if (totalMonthlyPayment > 6000) riskScore += 10;
        
        // Determine Risk Level
        if (riskScore >= 70) {
            this.riskLevel = LoanRiskLevel.HIGH;
        } else if (riskScore >= 40) {
            this.riskLevel = LoanRiskLevel.MEDIUM;
        } else {
            this.riskLevel = LoanRiskLevel.LOW;
        }
    }
    
    /**
     * Add overdue loan information
     */
    public void addOverdueLoan(double amount, LocalDateTime overdueDate) {
        this.overdueLoanCount++;
        this.hasOverdueLoans = true;
        this.totalOverdueAmount += amount;
        
        if (lastOverdueDate == null || overdueDate.isAfter(lastOverdueDate)) {
            this.lastOverdueDate = overdueDate;
        }
        
        calculateRiskLevel();
    }
    
    /**
     * Add active loan information
     */
    public void addActiveLoan(double balance, double monthlyPayment) {
        this.activeLoanCount++;
        this.totalCurrentBalance += balance;
        this.totalMonthlyPayment += monthlyPayment;
        
        calculateRiskLevel();
    }
    
    /**
     * Add overdue payment information
     */
    public void addOverduePayments(int count) {
        this.totalOverduePayments += count;
        
        calculateRiskLevel();
    }
    
    // Getters
    public int getActiveLoanCount() { return activeLoanCount; }
    public int getOverdueLoanCount() { return overdueLoanCount; }
    public double getTotalCurrentBalance() { return totalCurrentBalance; }
    public double getTotalOverdueAmount() { return totalOverdueAmount; }
    public double getTotalMonthlyPayment() { return totalMonthlyPayment; }
    public int getTotalOverduePayments() { return totalOverduePayments; }
    public boolean hasOverdueLoans() { return hasOverdueLoans; }
    public LocalDateTime getLastOverdueDate() { return lastOverdueDate; }
    public LoanRiskLevel getRiskLevel() { return riskLevel; }
}
