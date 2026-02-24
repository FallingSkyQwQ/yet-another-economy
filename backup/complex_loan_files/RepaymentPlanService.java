package com.yae.api.loan;

import com.yae.api.core.ServiceConfig;
import com.yae.utils.Logging;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating and managing loan repayment plans
 * Supports amortization schedules and repayment tracking
 */
public class RepaymentPlanService {
    
    private final ServiceConfig config;
    
    public RepaymentPlanService(ServiceConfig config) {
        this.config = config;
    }
    
    /**
     * Generate amortization schedule for a loan
     */
    public AmortizationSchedule generateRepaymentPlan(LoanTerms loanTerms) {
        try {
            return loanTerms.getAmortizationSchedule();
        } catch (Exception e) {
            Logging.error("Failed to generate repayment plan", e);
            throw new RuntimeException("Failed to generate repayment plan", e);
        }
    }
    
    /**
     * Generate multiple repayment options with different terms
     */
    public List<RepaymentOption> generateRepaymentOptions(double principalAmount, int[] termOptions, double baseInterestRate) {
        List<RepaymentOption> options = new ArrayList<>();
        
        for (int term : termOptions) {
            try {
                double interestRate = adjustInterestRateForTerm(baseInterestRate, term);
                LoanTerms.TermsOption termsOption = new LoanTerms.TermsOption(term, interestRate, principalAmount);
                LoanTerms loanTerms = new LoanTerms(termsOption);
                
                RepaymentOption option = new RepaymentOption(
                    term, interestRate, loanTerms.getMonthlyPayment(),
                    loanTerms.getTotalInterest(), loanTerms.getTotalPayment()
                );
                
                // Add benefits/detriments based on term length
                addTermRecommendations(option, term, loanTerms);
                
                options.add(option);
                
            } catch (Exception e) {
                Logging.error("Failed to generate repayment option for term " + term, e);
            }
        }
        
        return options;
    }
    
    /**
     * Generate early payoff scenarios
     */
    public List<EarlyPayoffOption> generateEarlyPayoffOptions(LoanTerms originalTerms, int currentMonth) {
        List<EarlyPayoffOption> options = new ArrayList<>();
        
        // Get current remaining balance
        double currentBalance = originalTerms.getRemainingBalanceAfterMonths(currentMonth);
        
        // Various payoff scenarios
        int[] payoffScenarios = {3, 6, 12, 24}; // Payoff in 3, 6, 12, 24 months
        
        for (int payoffPeriod : payoffScenarios) {
            try {
                double newMonthlyPayment = calculateAcceleratedPayment(currentBalance, payoffPeriod, currentMonth);
                double totalInterestSaved = calculateInterestSaved(originalTerms, currentMonth, payoffPeriod);
                
                EarlyPayoffOption option = new EarlyPayoffOption(
                    payoffPeriod, currentBalance, newMonthlyPayment, totalInterestSaved
                );
                
                options.add(option);
                
            } catch (Exception e) {
                Logging.error("Failed to calculate early payoff for period " + payoffPeriod, e);
            }
        }
        
        return options;
    }
    
    /**
     * Calculate bi-weekly payment plan (26 payments per year)
     */
    public BiWeeklyPlan generateBiWeeklyPlan(LoanTerms originalTerms) {
        double monthlyPayment = originalTerms.getMonthlyPayment();
        double biWeeklyPayment = monthlyPayment / 2.0; // Half monthly payment every 2 weeks
        
        // Bi-weekly payments result in 26 payments per year vs 12 monthly
        // This effectively adds one extra monthly payment per year
        double effectiveMonthlyPayment = (biWeeklyPayment * 26) / 12;
        
        // Calculate payoff time reduction
        double originalPayoffMonths = originalTerms.getTermMonths();
        double biWeeklyPayoffMonths = calculateBiWeeklyPayoffTime(originalTerms);
        int monthsSaved = (int) (originalPayoffMonths - biWeeklyPayoffMonths);
        
        double totalInterestSaved = originalTerms.getTotalInterest() - 
                                   (effectiveMonthlyPayment * biWeeklyPayoffMonths - originalTerms.getPrincipalAmount());
        
        return new BiWeeklyPlan(
            biWeeklyPayment, effectiveMonthlyPayment, monthsSaved,
            totalInterestSaved, biWeeklyPayoffMonths
        );
    }
    
    /**
     * Generate refinance options
     */
    public List<RefinanceOption> generateRefinanceOptions(LoanTerms currentTerms, double currentBalance, 
                                                        int remainingMonths, double currentMarketRate) {
        List<RefinanceOption> options = new ArrayList<>();
        
        // Refinance term options
        int[] refinanceTerms = {12, 24, 36, 60}; // 1, 2, 3, 5 years
        
        for (int newTerm : refinanceTerms) {
            if (newTerm >= remainingMonths) continue; // Skip if longer than current
            
            try {
                double newRate = Math.max(currentMarketRate - 0.5, currentTerms.getInterestRate() - 1.0);
                LoanTerms.TermsOption newTermsOption = new LoanTerms.TermsOption(newTerm, newRate, currentBalance);
                LoanTerms newTerms = new LoanTerms(newTermsOption);
                
                double monthlySavings = currentTerms.getMonthlyPayment() - newTerms.getMonthlyPayment();
                double totalSavings = monthlySavings * newTerm;
                
                // Check if refinance is beneficial (consider closing costs)
                double closingCosts = currentBalance * 0.01; // 1% closing costs
                
                RefinanceOption option = new RefinanceOption(
                    newTerm, newRate, newTerms.getMonthlyPayment(), monthlySavings,
                    totalSavings, closingCosts
                );
                
                options.add(option);
                
            } catch (Exception e) {
                Logging.error("Failed to calculate refinance option for term " + newTerm, e);
            }
        }
        
        return options;
    }
    
    /**
     * Analyze payment history and suggest optimizations
     */
    public PaymentAnalysis analyzePaymentHistory(PaymentHistory payments) {
        double avgPayment = payments.getAveragePayment();
        double medianPayment = payments.getMedianPayment();
        double maxPayment = payments.getMaxPayment();
        double minPayment = payments.getMinPayment();
        
        // Calculate payment consistency
        double consistencyScore = calculatePaymentConsistency(payments);
        
        // Identify late payments and their impact
        int latePayments = payments.countLatePayments();
        double latePenaltyImpact = LatePayments * config.getDouble("Late_penalty_rate", 0.05) * avgPayment;
        
        // Recommendations based on analysis
        List<String> recommendations = new ArrayList<>();
        
        if (consistencyScore < 0.8) {
            recommendations.add("建议设置自动还款以提高还款一致性");
        }
        
        if (latePayments > 2) {
            recommendations.add("存在多次逾期，建议改善还款习惯");
            recommendations.add("可考虑提前还款以减少损失");
        }
        
        if (avgPayment > medianPayment) {
            recommendations.add("支付金额波动较大，建议保持稳定的还款金额");
        }
        
        return new PaymentAnalysis(
            avgPayment, medianPayment, maxPayment, minPayment, consistencyScore,
            latePayments, latePenaltyImpact, recommendations
        );
    }
    
    // === Private Helper Methods ===
    
    private double adjustInterestRateForTerm(double baseRate, int term) {
        // Longer terms get slightly lower rates, shorter terms get higher rates
        if (term <= 6) return baseRate + 0.5;
        if (term <= 12) return baseRate + 0.2;
        if (term >= 60) return baseRate - 0.3;
        return baseRate;
    }
    
    private void addTermRecommendations(RepaymentOption option, int term, LoanTerms loanTerms) {
        List<String> recs = new ArrayList<>();
        
        if (term <= 6) {
            recs.add("最短期限，总利息最少");
            recs.add("月供压力相对较大");
        } else if (term <= 12) {
            recs.add("适中期限，平衡月供与总利息");
            recs.add("推荐选项");
        } else if (term >= 60) {
            recs.add("最长期限，月供压力最小");
            recs.add("总利息相对较高");
            recs.add("适合长期资金需求");
        }
        
        option.addRecommendations(recs);
    }
    
    private double calculateAcceleratedPayment(double remainingBalance, int payoffPeriod, int currentMonth) {
        double monthlyRate = config.getDouble("loan_interest_rate", 8.0) / 100.0 / 12.0;
        
        // Calculate required monthly payment for accelerated payoff
        double numerator = remainingBalance * monthlyRate * Math.pow(1 + monthlyRate, payoffPeriod);
        double denominator = Math.pow(1 + monthlyRate, payoffPeriod) - 1;
        
        return numerator / denominator;
    }
    
    private double calculateInterestSaved(LoanTerms originalTerms, int currentMonth, int acceleratedPayoffPeriod) {
        double originalRemainingInterest = originalTerms.getTotalPayment() - 
                                         (originalTerms.getMonthlyPayment() * currentMonth) - 
                                         originalTerms.getRemainingBalanceAfterMonths(currentMonth);
        
        // Simplified calculation for new interest with accelerated payment
        double newMonthlyPayment = calculateAcceleratedPayment(
            originalTerms.getRemainingBalanceAfterMonths(currentMonth),
            acceleratedPayoffPeriod,
            currentMonth
        );
        
        double newTotalInterest = (newMonthlyPayment * acceleratedPayoffPeriod) - 
                                originalTerms.getRemainingBalanceAfterMonths(currentMonth);
        
        return originalRemainingInterest - newTotalInterest;
    }
    
    private double calculateBiWeeklyPayoffTime(LoanTerms originalTerms) {
        double biWeeklyPayment = originalTerms.getMonthlyPayment() / 2.0;
        double remainingBalance = originalTerms.getPrincipalAmount();
        double monthlyRate = originalTerms.getInterestRate() / 100.0 / 12.0;
        double biWeeklyRate = monthlyRate / 2.16; // Approximate bi-weekly rate
        
        int payments = 0;
        while (remainingBalance > 0.01) {
            double interestPayment = remainingBalance * biWeeklyRate;
            double principalPayment = biWeeklyPayment - interestPayment;
            remainingBalance -= principalPayment;
            payments++;
        }
        
        return payments / 2.16; // Convert bi-weekly payments to months
    }
    
    private double calculatePaymentConsistency(PaymentHistory payments) {
        double avgPayment = payments.getAveragePayment();
        double stdDev = payments.getStandardDeviation();
        
        // Higher consistency score for lower relative standard deviation
        if (avgPayment == 0) return 0;
        return Math.max(0, Math.min(1.0, 1.0 - (stdDev / avgPayment)));
    }
    
    // === Result Classes ===
    
    public static class RepaymentOption {
        private final int termMonths;
        private final double interestRate;
        private final double monthlyPayment;
        private final double totalInterest;
        private final double totalPayment;
        private final List<String> recommendations = new ArrayList<>();
        
        public RepaymentOption(int termMonths, double interestRate, double monthlyPayment,
                             double totalInterest, double totalPayment) {
            this.termMonths = termMonths;
            this.interestRate = interestRate;
            this.monthlyPayment = monthlyPayment;
            this.totalInterest = totalInterest;
            this.totalPayment = totalPayment;
        }
        
        public void addRecommendations(List<String> recs) {
            recommendations.addAll(recs);
        }
        
        public void addRecommendation(String rec) {
            recommendations.add(rec);
        }
        
        // Getters
        public int getTermMonths() { return termMonths; }
        public double getInterestRate() { return interestRate; }
        public double getMonthlyPayment() { return monthlyPayment; }
        public double getTotalInterest() { return totalInterest; }
        public double getTotalPayment() { return totalPayment; }
        public List<String> getRecommendations() { return recommendations; }
        
        public String getSummary() {
            return String.format("%d个月期限: 月供¥%.2f, 总利息¥%.2f, 总还款¥%.2f",
                termMonths, monthlyPayment, totalInterest, totalPayment);
        }
    }
    
    public static class EarlyPayoffOption {
        private final int payoffPeriodMonths;
        private final double currentBalance;
        private final double newMonthlyPayment;
        private final double totalInterestSaved;
        
        public EarlyPayoffOption(int payoffPeriodMonths, double currentBalance,
                               double newMonthlyPayment, double totalInterestSaved) {
            this.payoffPeriodMonths = payoffPeriodMonths;
            this.currentBalance = currentBalance;
            this.newMonthlyPayment = newMonthlyPayment;
            this.totalInterestSaved = totalInterestSaved;
        }
        
        public String getBreakdown() {
            return String.format("提前%d个月还清的方案: 月供增至¥%.2f, 节省利息¥%.2f",
                payoffPeriodMonths, newMonthlyPayment, totalInterestSaved);
        }
        
        // Getters
        public int getPayoffPeriodMonths() { return payoffPeriodMonths; }
        public double getCurrentBalance() { return currentBalance; }
        public double getNewMonthlyPayment() { return newMonthlyPayment; }
        public double getTotalInterestSaved() { return totalInterestSaved; }
    }
    
    public static class BiWeeklyPlan {
        private final double biWeeklyPayment;
        private final double effectiveMonthlyPayment;
        private final int monthsSaved;
        private final double totalInterestSaved;
        private final double totalPayoffTimeMonths;
        
        public BiWeeklyPlan(double biWeeklyPayment, double effectiveMonthlyPayment,
                          int monthsSaved, double totalInterestSaved, double totalPayoffTimeMonths) {
            this.biWeeklyPayment = biWeeklyPayment;
            this.effectiveMonthlyPayment = effectiveMonthlyPayment;
            this.monthsSaved = monthsSaved;
            this.totalInterestSaved = totalInterestSaved;
            this.totalPayoffTimeMonths = totalPayoffTimeMonths;
        }
        
        public String getBenefitSummary() {
            return String.format("双周付款可达利益: 节省%.1f个月, 节约利息¥%.2f, 有效月供¥%.2f",
                totalPayoffTimeMonths - monthsSaved, totalInterestSaved, effectiveMonthlyPayment);
        }
        
        // Getters
        public double getBiWeeklyPayment() { return biWeeklyPayment; }
        public double getEffectiveMonthlyPayment() { return effectiveMonthlyPayment; }
        public int getMonthsSaved() { return monthsSaved; }
        public double getTotalInterestSaved() { return totalInterestSaved; }
        public double getTotalPayoffTimeMonths() { return totalPayoffTimeMonths; }
    }
    
    public static class RefinanceOption {
        private final int newTermMonths;
        private final double newInterestRate;
        private final double newMonthlyPayment;
        private final double monthlySavings;
        private final double totalSavings;
        private final double closingCosts;
        
        public RefinanceOption(int newTermMonths, double newInterestRate, double newMonthlyPayment,
                             double monthlySavings, double totalSavings, double closingCosts) {
            this.newTermMonths = newTermMonths;
            this.newInterestRate = newInterestRate;
            this.newMonthlyPayment = newMonthlyPayment;
            this.monthlySavings = monthlySavings;
            this.totalSavings = totalSavings;
            this.closingCosts = closingCosts;
        }
        
        public String getRefinanceAnalysis() {
            double netSavings = totalSavings - closingCosts;
            String benefit = netSavings > 0 ? "可节约" : "不划算（成本）";
            return String.format("再贷%d个月: 月供降至¥%.2f, 月省¥%.2f, 总省¥%.2f, 手续费¥%.2f - %s",
                newTermMonths, newMonthlyPayment, monthlySavings, totalSavings, closingCosts, benefit);
        }
        
        // Getters
        public int getNewTermMonths() { return newTermMonths; }
        public double getNewInterestRate() { return newInterestRate; }
        public double getNewMonthlyPayment() { return newMonthlyPayment; }
        public double getMonthlySavings() { return monthlySavings; }
        public double getTotalSavings() { return totalSavings; }
        public double getClosingCosts() { return closingCosts; }
    }
    
    public static class PaymentAnalysis {
        private final double avgPayment;
        private final double medianPayment;
        private final double maxPayment;
        private final double minPayment;
        private final double consistencyScore;
        private final int latePayments;
        private final double latePenaltyImpact;
        private final List<String> recommendations;
        
        public PaymentAnalysis(double avgPayment, double medianPayment, double maxPayment,
                             double minPayment, double consistencyScore, int latePayments,
                             double latePenaltyImpact, List<String> recommendations) {
            this.avgPayment = avgPayment;
            this.medianPayment = medianPayment;
            this.maxPayment = maxPayment;
            this.minPayment = minPayment;
            this.consistencyScore = consistencyScore;
            this.latePayments = latePayments;
            this.latePenaltyImpact = latePenaltyImpact;
            this.recommendations = recommendations;
        }
        
        public String getAnalysisSummary() {
            return String.format("还款分析: 平均¥%.2f, 一致性%.1f%%, 逾期%d次, 罚金影响¥%.2f. 建议: %d条",
                avgPayment, consistencyScore * 100, latePayments, latePenaltyImpact, recommendations.size());
        }
        
        // Getters
        public double getAvgPayment() { return avgPayment; }
        public double getMedianPayment() { return medianPayment; }
        public double getMaxPayment() { return maxPayment; }
        public double getMinPayment() { return minPayment; }
        public double getConsistencyScore() { return consistencyScore; }
        public int getLatePayments() { return latePayments; }
        public double getLatePenaltyImpact() { return latePenaltyImpact; }
        public List<String> getRecommendations() { return recommendations; }
    }
    
    // Supporting interfaces/classes for payment history (these would be provided by the calling service)
    public interface PaymentHistory {
        double getAveragePayment();
        double getMedianPayment();
        double getMaxPayment();
        double getMinPayment();
        double getStandardDeviation();
        int countLatePayments();
    }
}
