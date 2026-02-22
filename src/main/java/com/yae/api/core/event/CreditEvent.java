package com.yae.api.core.event;

import com.yae.api.credit.CreditGrade;
import com.yae.api.credit.CreditService;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Events related to credit scoring and credit operations
 */
public abstract class CreditEvent extends YAEEvent {
    
    public CreditEvent(@NotNull String eventType, @NotNull String source, @Nullable String message) {
        super(eventType, source, message);
    }
    
    public CreditEvent(@NotNull String eventType, @NotNull String source, @Nullable String message, boolean async) {
        super(eventType, source, message, false, async);
    }
    
    /**
     * Credit service initialization event
     */
    public static class CreditServiceInitializedEvent extends CreditEvent {
        private final CreditService creditService;
        
        public CreditServiceInitializedEvent(@NotNull CreditService creditService) {
            super("credit-service-initialized", "CreditService", "Credit service has been initialized");
            this.creditService = creditService;
        }
        
        @NotNull
        public CreditService getCreditService() {
            return creditService;
        }
    }
    
    /**
     * Credit score calculated event
     */
    public static class CreditScoreCalculatedEvent extends CreditEvent {
        private final UUID playerId;
        private final int creditScore;
        private final CreditGrade creditGrade;
        
        public CreditScoreCalculatedEvent(@NotNull UUID playerId, int creditScore, @NotNull CreditGrade creditGrade) {
            super("credit-score-calculated", "CreditService", 
                  String.format("Credit score calculated for player %s: %d (%s)", 
                               playerId, creditScore, creditGrade.getDisplayName()));
            this.playerId = playerId;
            this.creditScore = creditScore;
            this.creditGrade = creditGrade;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        public int getCreditScore() {
            return creditScore;
        }
        
        @NotNull
        public CreditGrade getCreditGrade() {
            return creditGrade;
        }
    }
    
    /**
     * Credit score updated event
     */
    public static class CreditScoreUpdatedEvent extends CreditEvent {
        private final UUID playerId;
        private final int newScore;
        private final CreditGrade newGrade;
        
        public CreditScoreUpdatedEvent(@NotNull UUID playerId, int newScore) {
            super("credit-score-updated", "CreditService", 
                  String.format("Credit score updated for player %s: %d (%s)", 
                               playerId, newScore, CreditGrade.fromScore(newScore).getDisplayName()), true);
            this.playerId = playerId;
            this.newScore = newScore;
            this.newGrade = CreditGrade.fromScore(newScore);
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        public int getNewScore() {
            return newScore;
        }
        
        @NotNull
        public CreditGrade getNewGrade() {
            return newGrade;
        }
    }
    
    /**
     * Credit penalty applied event
     */
    public static class CreditPenaltyAppliedEvent extends CreditEvent {
        private final UUID playerId;
        private final String penaltyType;
        private final int penaltyAmount;
        private final int oldScore;
        private final int newScore;
        private final String reason;
        
        public CreditPenaltyAppliedEvent(@NotNull UUID playerId, @NotNull String penaltyType, 
                                       int penaltyAmount, int oldScore, int newScore, @Nullable String reason) {
            super("credit-penalty-applied", "CreditService", 
                  String.format("Credit penalty applied to player %s: %d points (%s)", 
                               playerId, penaltyAmount, penaltyType));
            this.playerId = playerId;
            this.penaltyType = penaltyType;
            this.penaltyAmount = penaltyAmount;
            this.oldScore = oldScore;
            this.newScore = newScore;
            this.reason = reason;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getPenaltyType() {
            return penaltyType;
        }
        
        public int getPenaltyAmount() {
            return penaltyAmount;
        }
        
        public int getOldScore() {
            return oldScore;
        }
        
        public int getNewScore() {
            return newScore;
        }
        
        @Nullable
        public String getReason() {
            return reason;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return EventSeverity.WARNING;
        }
    }
    
    /**
     * Credit bonus applied event
     */
    public static class CreditBonusAppliedEvent extends CreditEvent {
        private final UUID playerId;
        private final String bonusType;
        private final int bonusAmount;
        private final int oldScore;
        private final int newScore;
        private final String reason;
        
        public CreditBonusAppliedEvent(@NotNull UUID playerId, @NotNull String bonusType, 
                                      int bonusAmount, int oldScore, int newScore, @Nullable String reason) {
            super("credit-bonus-applied", "CreditService", 
                  String.format("Credit bonus applied to player %s: +%d points (%s)", 
                               playerId, bonusAmount, bonusType));
            this.playerId = playerId;
            this.bonusType = bonusType;
            this.bonusAmount = bonusAmount;
            this.oldScore = oldScore;
            this.newScore = newScore;
            this.reason = reason;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getBonusType() {
            return bonusType;
        }
        
        public int getBonusAmount() {
            return bonusAmount;
        }
        
        public int getOldScore() {
            return oldScore;
        }
        
        public int getNewScore() {
            return newScore;
        }
        
        @Nullable
        public String getReason() {
            return reason;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return EventSeverity.INFO;
        }
    }
    
    /**
     * Loan qualification checked event
     */
    public static class LoanQualificationCheckedEvent extends CreditEvent {
        private final UUID playerId;
        private final String loanType;
        private final boolean qualified;
        private final int creditScore;
        private final CreditGrade creditGrade;
        private final String reason;
        
        public LoanQualificationCheckedEvent(@NotNull UUID playerId, @NotNull String loanType, 
                                           boolean qualified, int creditScore, @NotNull CreditGrade creditGrade, 
                                           @Nullable String reason) {
            super("loan-qualification-checked", "CreditService", 
                  String.format("Player %s %s for %s loan (score: %d, grade: %s)", 
                               playerId, qualified ? "qualified" : "did not qualify", 
                               loanType, creditScore, creditGrade.getDisplayName()));
            this.playerId = playerId;
            this.loanType = loanType;
            this.qualified = qualified;
            this.creditScore = creditScore;
            this.creditGrade = creditGrade;
            this.reason = reason;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        @NotNull
        public String getLoanType() {
            return loanType;
        }
        
        public boolean isQualified() {
            return qualified;
        }
        
        public int getCreditScore() {
            return creditScore;
        }
        
        @NotNull
        public CreditGrade getCreditGrade() {
            return creditGrade;
        }
        
        @Nullable
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * Credit blacklist updated event
     */
    public static class CreditBlacklistUpdatedEvent extends CreditEvent {
        private final UUID playerId;
        private final boolean blacklisted;
        private final String reason;
        private final String updatedBy;
        
        public CreditBlacklistUpdatedEvent(@NotNull UUID playerId, boolean blacklisted, 
                                         @Nullable String reason, @NotNull String updatedBy) {
            super(blacklisted ? "player-blacklisted" : "player-unblacklisted", "CreditService", 
                  String.format("Player %s has been %s from credit system by %s", 
                               playerId, blacklisted ? "blacklisted" : "unblacklisted", updatedBy));
            this.playerId = playerId;
            this.blacklisted = blacklisted;
            this.reason = reason;
            this.updatedBy = updatedBy;
        }
        
        @NotNull
        public UUID getPlayerId() {
            return playerId;
        }
        
        public boolean isBlacklisted() {
            return blacklisted;
        }
        
        @Nullable
        public String getReason() {
            return reason;
        }
        
        @NotNull
        public String getUpdatedBy() {
            return updatedBy;
        }
        
        @Override
        public EventSeverity getSeverity() {
            return blacklisted ? EventSeverity.WARNING : EventSeverity.INFO;
        }
    }
}
