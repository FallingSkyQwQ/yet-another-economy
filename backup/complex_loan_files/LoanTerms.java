package com.yae.api.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/**
 * Loan terms containing all calculated loan parameters
 * Includes interest rate, monthly payment, and repayment schedule
 */
public class LoanTerms {
    
    private final double principalAmount;
    private final double interestRate;
    private final int termMonths;
    private final double monthlyPayment;
    private final double totalInterest;
    private final double totalPayment;
    private final double processingFee;
    
    public LoanTerms(TermOption termOption) {
        this.principalAmount = termOption.getPrincipalAmount();
        this.interestRate = termOption.getInterestRate();
        this.termMonths = termOption.getTermMonths();
        this.processingFee = termOption.getProcessingFee();
        
        // Calculate monthly payment using standard amortization formula
        this.monthlyPayment = calculateMonthlyPayment();
        this.totalPayment = monthlyPayment * termMonths;
        this.totalInterest = totalPayment - principalAmount;
    }
    
    /**
     * Calculate monthly payment using standard amortization formula
     * M = P * [r(1+r)^n] / [(1+r)^n - 1]
     * Where: M = monthly payment, P = principal, r = monthly interest rate, n = number of months
     */
    private double calculateMonthlyPayment() {
        if (interestRate <= 0) {
            return principalAmount / termMonths;
        }
        
        double monthlyRate = interestRate / 100.0 / 12.0;
        double numerator = principalAmount * monthlyRate * Math.pow(1 + monthlyRate, termMonths);
        double denominator = Math.pow(1 + monthlyRate, termMonths) - 1;
        
        return numerator / denominator;
    }
    
    /**
     * Get amortization schedule for the loan
     * Returns a list of monthly payment breakdowns
     */
    public AmortizationSchedule getAmortizationSchedule() {
        return new AmortizationSchedule(this);
    }
    
    /**
     * Calculate remaining balance after N monthly payments
     */
    public double getRemainingBalanceAfterMonths(int monthsPaid) {
        if (monthsPaid >= termMonths) {
            return 0;
        }
        
        return calculateRemainingBalance(monthsPaid);
    }
    
    /**
     * Calculate interest paid up to a certain month
     */
    public double getInterestPaid(int months) {
        if (months >= termMonths) {
            return totalInterest;
        }
        
        return calculateTotalInterestPaid(months);
    }
    
    /**
     * Calculate principal paid up to a certain month
     */
    public double getPrincipalPaid(int months) {
        if (months >= termMonths) {
            return principalAmount;
        }
        
        return months * monthlyPayment - calculateTotalInterestPaid(months);
    }
    
    /**
     * Calculate total cost including all fees
     */
    public double getTotalCost() {
        return totalPayment + processingFee;
    }
    
    /**
     * Calculate APR (Annual Percentage Rate) including fees
     */
    public double calculateAPR() {
        // Simplified APR calculation for demonstration
        double annualInterest = interestRate;
        double annualFeeRate = (processingFee / principalAmount) * (12.0 / termMonths) * 100;
        return annualInterest + annualFeeRate;
    }
    
    /**
     * Calculate remaining balance using amortization formula
     */
    private double calculateRemainingBalance(int paymentsMade) {
        double monthlyRate = interestRate / 100.0 / 12.0;
        return principalAmount * (Math.pow(1 + monthlyRate, termMonths) - Math.pow(1 + monthlyRate, paymentsMade))
                / (Math.pow(1 + monthlyRate, termMonths) - 1);
    }
    
    /**
     * Calculate total interest paid up to a certain month
     */
    private double calculateTotalInterestPaid(int monthsPaid) {
        double totalInterestPaid = 0;
        double remainingPrincipal = principalAmount;
        double monthlyRate = interestRate / 100.0 / 12.0;
        
        for (int month = 1; month <= monthsPaid; month++) {
            double interestPayment = remainingPrincipal * monthlyRate;
            double principalPayment = monthlyPayment - interestPayment;
            remainingPrincipal -= principalPayment;
            totalInterestPaid += interestPayment;
        }
        
        return totalInterestPaid;
    }
    
    /**
     * Get monthly payment breakdown for a specific month
     */
    public MonthlyPayment getMonthlyPaymentBreakdown(int month) {
        return new MonthlyPayment(this, month);
    }
    
    /**
     * Validate loan terms
     */
    public boolean isValid() {
        return principalAmount > 0 && interestRate > 0 && termMonths > 0 && monthlyPayment >= 0;
    }
    
    /**
     * Generate terms summary for display
     */
    public String getTermsSummary() {
        return String.format("""
            贷款条款
            本金: ¥%.2f
            年利率: %.2f%%
            期限: %d个月
            月供: ¥%.2f
            总利息: ¥%.2f
            总还款: ¥%.2f
            手续费: ¥%.2f
            总成本: ¥%.2f
            年化利率: %.2f%%
            """, principalAmount, interestRate, termMonths, monthlyPayment,
               totalInterest, totalPayment, processingFee, getTotalCost(), calculateAPR());
    }
    
    // Getters
    public double getPrincipalAmount() { return principalAmount; }
    public double getInterestRate() { return interestRate; }
    public int getTermMonths() { return termMonths; }
    public double getMonthlyPayment() { return monthlyPayment; }
    public double getTotalInterest() { return totalInterest; }
    public double getTotalPayment() { return totalPayment; }
    public double getProcessingFee() { return processingFee; }
    
    /**
     * Monthly payment breakdown
     */
    public static class MonthlyPayment {
        private final int month;
        private final double monthlyPayment;
        private final double interestPayment;
        private final double principalPayment;
        private final double remainingPrincipal;
        
        public MonthlyPayment(LoanTerms terms, int month) {
            this.month = month;
            this.monthlyPayment = terms.getMonthlyPayment();
            
            // Calculate breakdown for this specific month
            LoanTerms.AmortizationSchedule schedule = terms.getAmortizationSchedule();
            this.interestPayment = schedule.getMonthlyInterestPayment(month);
            this.principalPayment = schedule.getMonthlyPrincipalPayment(month);
            this.remainingPrincipal = schedule.getRemainingPrincipal(month);
        }
        
        // Getters
        public int getMonth() { return month; }
        public double getMonthlyPayment() { return monthlyPayment; }
        public double getInterestPayment() { return interestPayment; }
        public double getPrincipalPayment() { return principalPayment; }
        public double getRemainingPrincipal() { return remainingPrincipal; }
        
        public double getPrincipalRatio() {
            return monthlyPayment > 0 ? principalPayment / monthlyPayment : 0;
        }
        
        public double getInterestRatio() {
            return monthlyPayment > 0 ? interestPayment / monthlyPayment : 0;
        }
    }
    
    /**
     * Full amortization schedule
     */
    public static class AmortizationSchedule {
        private final LoanTerms terms;
        private final List<PaymentDetail> schedule;
        private final double totalInterest;
        private final double totalPayment;
        
        public AmortizationSchedule(LoanTerms terms) {
            this.terms = terms;
            this.schedule = generateSchedule();
            this.totalInterest = schedule.stream().mapToDouble(PaymentDetail::getInterestPayment).sum();
            this.totalPayment = schedule.stream().mapToDouble(PaymentDetail::getMonthlyPayment).sum();
        }
        
        private List<PaymentDetail> generateSchedule() {
            List<PaymentDetail> schedule = new ArrayList<>();
            double remainingPrincipal = terms.getPrincipalAmount();
            double monthlyRate = terms.getInterestRate() / 100.0 / 12.0;
            double monthlyPayment = terms.getMonthlyPayment();
            
            for (int month = 1; month <= terms.getTermMonths(); month++) {
                double interestPayment = remainingPrincipal * monthlyRate;
                double principalPayment = monthlyPayment - interestPayment;
                remainingPrincipal -= principalPayment;
                
                if (month == terms.getTermMonths()) {
                    // Final payment adjustment for rounding
                    principalPayment += remainingPrincipal;
                    remainingPrincipal = 0;
                }
                
                schedule.add(new PaymentDetail(month, monthlyPayment, interestPayment, principalPayment, remainingPrincipal));
            }
            
            return schedule;
        }
        
        /**
         * Get payment detail for specific month
         */
        public PaymentDetail getPaymentDetail(int month) {
            if (month < 1 || month > terms.getTermMonths()) {
                throw new IllegalArgumentException("Month out of range: " + month);
            }
            return schedule.get(month - 1);
        }
        
        /**
         * Get monthly interest payment
         */
        public double getMonthlyInterestPayment(int month) {
            return getPaymentDetail(month).getInterestPayment();
        }
        
        /**
         * Get monthly principal payment
         */
        public double getMonthlyPrincipalPayment(int month) {
            return getPaymentDetail(month).getPrincipalPayment();
        }
        
        /**
         * Get remaining principal after N payments
         */
        public double getRemainingPrincipal(int month) {
            if (month >= terms.getTermMonths()) {
                return 0;
            }
            return getPaymentDetail(month).getRemainingPrincipal();
        }
        
        /**
         * Get cumulative interest paid up to month N
         */
        public double getCumulativeInterest(int month) {
            return schedule.stream()
                    .limit(month)
                    .mapToDouble(PaymentDetail::getInterestPayment)
                    .sum();
        }
        
        /**
         * Get cumulative principal paid up to month N
         */
        public double getCumulativePrincipal(int month) {
            return schedule.stream()
                    .limit(month)
                    .mapToDouble(PaymentDetail::getPrincipalPayment)
                    .sum();
        }
        
        public List<PaymentDetail> getSchedule() { return new ArrayList<>(schedule); }
        public double getTotalInterest() { return totalInterest; }
        public double getTotalPayment() { return totalPayment; }
    }
    
    /**
     * Individual payment detail
     */
    public static class PaymentDetail {
        private final int month;
        private final double monthlyPayment;
        private final double interestPayment;
        private final double principalPayment;
        private final double remainingPrincipal;
        
        public PaymentDetail(int month, double monthlyPayment, double interestPayment, 
                           double principalPayment, double remainingPrincipal) {
            this.month = month;
            this.monthlyPayment = monthlyPayment;
            this.interestPayment = interestPayment;
            this.principalPayment = principalPayment;
            this.remainingPrincipal = remainingPrincipal;
        }
        
        // Getters
        public int getMonth() { return month; }
        public double getMonthlyPayment() { return monthlyPayment; }
        public double getInterestPayment() { return interestPayment; }
        public double getPrincipalPayment() { return principalPayment; }
        public double getRemainingPrincipal() { return remainingPrincipal; }
    }
    
    public static class TermOption {
        private final int termMonths;
        private final double interestRate;
        private final double principalAmount;
        private final double processingFee;
        
        public TermOption(int termMonths, double interestRate, double principalAmount) {
            this(termMonths, interestRate, principalAmount, 0);
        }
        
        public TermOption(int termMonths, double interestRate, double principalAmount, double processingFee) {
            this.termMonths = termMonths;
            this.interestRate = interestRate;
            this.principalAmount = principalAmount;
            this.processingFee = processingFee;
        }
        
        // Getters
        public int getTermMonths() { return termMonths; }
        public double getInterestRate() { return interestRate; }
        public double getPrincipalAmount() { return principalAmount; }
        public double getProcessingFee() { return processingFee; }
    }
}
