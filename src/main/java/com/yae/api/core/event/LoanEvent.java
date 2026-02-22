package com.yae.api.core.event;

import com.yae.api.loan.LoanService;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Events related to loan operations and management
 */
public abstract class LoanEvent extends YAEEvent {
    
    public LoanEvent(@NotNull String eventType, @NotNull String source, @Nullable String message) {
        super(eventType, source, message);
    }
    
    public LoanEvent(@NotNull String eventType, @NotNull String source, @Nullable String message, boolean async) {
        super(eventType, source, message, false, async);
    }
    
    /**
     * Loan service initialization event
     */
    public static class LoanServiceInitializedEvent extends LoanEvent {
        private final LoanService loanService;
        
        public LoanServiceInitializedEvent(@NotNull LoanService loanService) {
            super("loan-service-initialized", "LoanService", "Loan service has been initialized");
            this.loanService = loanService;
        }
        
        @NotNull
        public LoanService getLoanService() {
            return loanService;
        }
    }
    
    /**
     * Loan application submitted event
     */
    public static class LoanApplicationSubmittedEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final String loanType;
        private final double requestedAmount;
        private final int creditScore;
        private final String creditGrade;
        
        public LoanApplicationSubmittedEvent(@NotNull UUID playerId, @NotNull String loanId, 
                                           @NotNull String loanType, double requestedAmount, 
                                           int creditScore, @NotNull String creditGrade) {
            super("loan-application-submitted", "LoanService", 
                  String.format("Player %s submitted loan application %s for %s loan (amount: %.2f, credit: %d/%s)", 
                               playerId, loanId, loanType, requestedAmount, creditScore, creditGrade));
            this.playerId = playerId;
            this.loanId = loanId;
            this.loanType = loanType;
            this.requestedAmount = requestedAmount;
            this.creditScore = creditScore;
            this.creditGrade = creditGrade;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        @NotNull
        public String getLoanType() {
            return loanType;
        }
        
        public double getRequestedAmount() {
            return requestedAmount;
        }
        
        public int getCreditScore() {
            return creditScore;
        }
        
        @NotNull
        public String getCreditGrade() {
            return creditGrade;
        }
    }
    
    /**
     * Loan approved event
     */
    public static class LoanApprovedEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final String approvedBy;
        private final String notes;
        
        public LoanApprovedEvent(@NotNull UUID playerId, @NotNull String loanId, 
                               @NotNull String approvedBy, @Nullable String notes) {
            super("loan-approved", "LoanService", 
                  String.format("Loan %s approved by %s for player %s", loanId, approvedBy, playerId));
            this.playerId = playerId;
            this.loanId = loanId;
            this.approvedBy = approvedBy;
            this.notes = notes;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        @NotNull
        public String getApprovedBy() {
            return approvedBy;
        }
        
        @Nullable
        public String getNotes() {
            return notes;
        }
    }
    
    /**
     * Loan rejected event
     */
    public static class LoanRejectedEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final String rejectedBy;
        private final String reason;
        
        public LoanRejectedEvent(@NotNull UUID playerId, @NotNull String loanId, 
                               @NotNull String rejectedBy, @NotNull String reason) {
            super("loan-rejected", "LoanService", 
                  String.format("Loan %s rejected by %s for player %s: %s", loanId, rejectedBy, playerId, reason));
            this.playerId = playerId;
            this.loanId = loanId;
            this.rejectedBy = rejectedBy;
            this.reason = reason;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        @NotNull
        public String getRejectedBy() {
            return rejectedBy;
        }
        
        @NotNull
        public String getReason() {
            return reason;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return EventSeverity.WARNING;
        }
    }
    
    /**
     * Loan disbursed event
     */
    public static class LoanDisbursedEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final double disbursedAmount;
        
        public LoanDisbursedEvent(@NotNull UUID playerId, @NotNull String loanId, double disbursedAmount) {
            super("loan-disbursed", "LoanService", 
                  String.format("Loan %s disbursed to player %s (amount: %.2f)", 
                               loanId, playerId, disbursedAmount));
            this.playerId = playerId;
            this.loanId = loanId;
            this.disbursedAmount = disbursedAmount;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        public double getDisbursedAmount() {
            return disbursedAmount;
        }
    }
    
    /**
     * Loan payment made event
     */
    public static class LoanPaymentMadeEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final double totalPayment;
        private final double interestPayment;
        private final double principalPayment;
        private final double penaltyPayment;
        
        public LoanPaymentMadeEvent(@NotNull UUID playerId, @NotNull String loanId, double totalPayment,
                                  double interestPayment, double principalPayment, double penaltyPayment) {
            super("loan-payment-made", "LoanService", 
                  String.format("Payment made for loan %s by player %s (total: %.2f, interest: %.2f, principal: %.2f, penalty: %.2f)", 
                               loanId, playerId, totalPayment, interestPayment, principalPayment, penaltyPayment), true);
            this.playerId = playerId;
            this.loanId = loanId;
            this.totalPayment = totalPayment;
            this.interestPayment = interestPayment;
            this.principalPayment = principalPayment;
            this.penaltyPayment = penaltyPayment;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        public double getTotalPayment() {
            return totalPayment;
        }
        
        public double getInterestPayment() {
            return interestPayment;
        }
        
        public double getPrincipalPayment() {
            return principalPayment;
        }
        
        public double getPenaltyPayment() {
            return penaltyPayment;
        }
    }
    
    /**
     * Loan overdue event
     */
    public static class LoanOverdueEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final int overduePayments;
        private final double overdueAmount;
        
        public LoanOverdueEvent(@NotNull UUID playerId, @NotNull String loanId, 
                              int overduePayments, double overdueAmount) {
            super("loan-overdue", "LoanService", 
                  String.format("Loan %s for player %s is overdue (payments: %d, amount: %.2f)", 
                               loanId, playerId, overduePayments, overdueAmount));
            this.playerId = playerId;
            this.loanId = loanId;
            this.overduePayments = overduePayments;
            this.overdueAmount = overdueAmount;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        public int getOverduePayments() {
            return overduePayments;
        }
        
        public double getOverdueAmount() {
            return overdueAmount;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return EventSeverity.WARNING;
        }
    }
    
    /**
     * Loan penalty calculated event
     */
    public static class LoanPenaltyCalculatedEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final double penaltyAmount;
        
        public LoanPenaltyCalculatedEvent(@NotNull UUID playerId, @NotNull String loanId, double penaltyAmount) {
            super("loan-penalty-calculated", "LoanService", 
                  String.format("Penalty interest calculated for loan %s of player %s (amount: %.2f)", 
                               loanId, playerId, penaltyAmount));
            this.playerId = playerId;
            this.loanId = loanId;
            this.penaltyAmount = penaltyAmount;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        public double getPenaltyAmount() {
            return penaltyAmount;
        }
    }
    
    /**
     * Loan defaulted event
     */
    public static class LoanDefaultedEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final String reason;
        
        public LoanDefaultedEvent(@NotNull UUID playerId, @NotNull String loanId, @NotNull String reason) {
            super("loan-defaulted", "LoanService", 
                  String.format("Loan %s for player %s has defaulted: %s", loanId, playerId, reason));
            this.playerId = playerId;
            this.loanId = loanId;
            this.reason = reason;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        @NotNull
        public String getReason() {
            return reason;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return EventSeverity.ERROR;
        }
    }
    
    /**
     * Loan paid off event
     */
    public static class LoanPaidOffEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final double finalPayment;
        
        public LoanPaidOffEvent(@NotNull UUID playerId, @NotNull String loanId, double finalPayment) {
            super("loan-paid-off", "LoanService", 
                  String.format("Loan %s for player %s has been paid off (final payment: %.2f)", 
                               loanId, playerId, finalPayment));
            this.playerId = playerId;
            this.loanId = loanId;
            this.finalPayment = finalPayment;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        public double getFinalPayment() {
            return finalPayment;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return EventSeverity.INFO;
        }
    }
    
    /**
     * Loan refinanced event
     */
    public static class LoanRefinancedEvent extends LoanEvent {
        private final UUID playerId;
        private final String oldLoanId;
        private final String newLoanId;
        private final double newAmount;
        private final double newRate;
        private final String reason;
        
        public LoanRefinancedEvent(@NotNull UUID playerId, @NotNull String oldLoanId, 
                                 @NotNull String newLoanId, double newAmount, double newRate, @Nullable String reason) {
            super("loan-refinanced", "LoanService", 
                  String.format("Loan %s refinanced to loan %s for player %s (amount: %.2f, rate: %.4f)", 
                               oldLoanId, newLoanId, playerId, newAmount, newRate));
            this.playerId = playerId;
            this.oldLoanId = oldLoanId;
            this.newLoanId = newLoanId;
            this.newAmount = newAmount;
            this.newRate = newRate;
            this.reason = reason;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getOldLoanId() {
            return oldLoanId;
        }
        
        @NotNull
        public String getNewLoanId() {
            return newLoanId;
        }
        
        public double getNewAmount() {
            return newAmount;
        }
        
        public double getNewRate() {
            return newRate;
        }
        
        @Nullable
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * Loan collateral liquidated event
     */
    public static class LoanCollateralLiquidatedEvent extends LoanEvent {
        private final UUID playerId;
        private final String loanId;
        private final String collateralType;
        private final double collateralValue;
        private final double recoveredAmount;
        
        public LoanCollateralLiquidatedEvent(@NotNull UUID playerId, @NotNull String loanId, 
                                           @NotNull String collateralType, double collateralValue, double recoveredAmount) {
            super("loan-collateral-liquidated", "LoanService", 
                  String.format("Collateral liquidated for loan %s of player %s (type: %s, value: %.2f, recovered: %.2f)", 
                               loanId, playerId, collateralType, collateralValue, recoveredAmount));
            this.playerId = playerId;
            this.loanId = loanId;
            this.collateralType = collateralType;
            this.collateralValue = collateralValue;
            this.recoveredAmount = recoveredAmount;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanId() {
            return loanId;
        }
        
        @NotNull
        public String getCollateralType() {
            return collateralType;
        }
        
        public double getCollateralValue() {
            return collateralValue;
        }
        
        public double getRecoveredAmount() {
            return recoveredAmount;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return EventSeverity.WARNING;
        }
    }
}
