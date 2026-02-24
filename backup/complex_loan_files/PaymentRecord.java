package com.yae.api.loan;

import com.yae.api.loan.RepaymentService.PaymentMethod;
import com.yae.api.loan.RepaymentService.PaymentStatus;

import java.time.LocalDateTime;

/**
 * Payment record storing transaction details
 */
public class PaymentRecord {
    
    private final String transactionId;
    private final String loanId;
    private final LocalDateTime paymentDate;
    private final double paymentAmount;
    private final double principalPayment;
    private final double interestPayment;
    private final double penaltyPayment;
    private final PaymentMethod method;
    private final PaymentStatus status;
    
    public PaymentRecord(String transactionId, String loanId, LocalDateTime paymentDate,
                       double paymentAmount, double principalPayment, double interestPayment,
                       double penaltyPayment, PaymentMethod method, PaymentStatus status) {
        this.transactionId = transactionId;
        this.loanId = loanId;
        this.paymentDate = paymentDate;
        this.paymentAmount = paymentAmount;
        this.principalPayment = principalPayment;
        this.interestPayment = interestPayment;
        this.penaltyPayment = penaltyPayment;
        this.method = method;
        this.status = status;
    }
    
    /**
     * Calculate total transaction amount
     */
    public double getTotalAmount() {
        return paymentAmount; // Already includes all components
    }
    
    /**
     * Check if this was a successful payment
     */
    public boolean isSuccessful() {
        return status == PaymentStatus.COMPLETED;
    }
    
    /**
     * Get payment breakdown
     */
    public String getPaymentBreakdown() {
        return String.format("""
            交易ID：%s
            日期：%s
            本金：¥%.2f
            利息：¥%.2f
            罚息：¥%.2f
            总金额：¥%.2f
            方法：%s
            状态：%s
            """, 
            transactionId, paymentDate, principalPayment, interestPayment, 
            penaltyPayment, paymentAmount, method.getChineseName(), status.getChineseName()
        );
    }
    
    public String getTransactionId() { return transactionId; }
    public String getLoanId() { return loanId; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public double getPaymentAmount() { return paymentAmount; }
    public double getPrincipalPayment() { return principalPayment; }
    public double getInterestPayment() { return interestPayment; }
    public double getPenaltyPayment() { return penaltyPayment; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
}
