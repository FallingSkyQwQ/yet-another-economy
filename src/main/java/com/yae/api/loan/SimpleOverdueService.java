package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified overdue loan processing service
 * Basic implementation for handling overdue loans
 */
public class SimpleOverdueService implements Service {
    
    private ServiceConfig config;
    private boolean enabled = false;
    private final LoanService loanService;
    private final Map<String, OverdueLoanData> overdueLoans = new ConcurrentHashMap<>();
    private final Timer overdueCheckTimer = new Timer();
    
    public SimpleOverdueService(LoanService loanService) {
        this.loanService = loanService;
    }

    @Override
    public @NotNull String getName() {
        return "Simple Overdue Service";
    }

    @Override
    public @NotNull ServiceType getType() {
        return ServiceType.LOAN;
    }

    @Override
    public ServiceConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public boolean initialize() {
        this.enabled = true;
        startOverdueCheckTask();
        return true;
    }

    @Override
    public boolean reload() {
        return true;
    }

    @Override
    public void shutdown() {
        this.enabled = false;
        overdueCheckTimer.cancel();
        overdueLoans.clear();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return false;
    }
    
    /**
     * Start periodic check for overdue loans
     */
    private void startOverdueCheckTask() {
        overdueCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (enabled) {
                    checkForOverdueLoans();
                }
            }
        }, 60000, 60000); // Check every minute
    }
    
    /**
     * Check all active loans for overdue status
     */
    private void checkForOverdueLoans() {
        // This would normally iterate through all loans in the system
        // For now, it's a placeholder for the check logic
    }
    
    /**
     * Process a loan that has become overdue
     */
    public void processOverdueLoan(String loanId) {
        com.yae.api.loan.SimpleLoan loan = (com.yae.api.loan.SimpleLoan) loanService.getLoan(loanId);
        if (loan == null || !loan.isActive() || !loan.isOverdue()) {
            return;
        }
        
        // Create or update overdue record
        OverdueLoanData overdueData = overdueLoans.computeIfAbsent(loanId, 
            k -> new OverdueLoanData(loanId, loan.getPlayerId()));
        
        // Update overdue days
        overdueData.updateOverdueDays();
        
        // Apply penalty (simple 0.1% daily)
        double penalty = loan.getRemainingBalance() * 0.001;
        overdueData.addPenalty(penalty);
        
        // Log the overdue processing
        System.out.println(String.format("[YAE-Overdue] Loan %s is %d days overdue, penalty applied: ¥%.2f", 
            loanId, overdueData.getDaysOverdue(), penalty));
    }
    
    /**
     * Get overdue information for a loan
     */
    public OverdueLoanData getOverdueInfo(String loanId) {
        return overdueLoans.get(loanId);
    }
    
    /**
     * Check if a loan is overdue
     */
    public boolean isLoanOverdue(String loanId) {
        OverdueLoanData data = overdueLoans.get(loanId);
        return data != null && data.getDaysOverdue() > 0;
    }
    
    /**
     * Get total penalty amount for a loan
     */
    public double getTotalPenalty(String loanId) {
        OverdueLoanData data = overdueLoans.get(loanId);
        return data != null ? data.getTotalPenalty() : 0.0;
    }
    
    /**
     * Get all overdue loans for a player
     */
    public List<OverdueLoanData> getPlayerOverdueLoans(UUID playerId) {
        List<OverdueLoanData> result = new ArrayList<>();
        for (OverdueLoanData data : overdueLoans.values()) {
            if (data.getPlayerId().equals(playerId)) {
                result.add(data);
            }
        }
        return result;
    }
    
    /**
     * Clear overdue status for a loan (when paid off)
     */
    public void clearOverdueStatus(String loanId) {
        overdueLoans.remove(loanId);
    }
    
    /**
     * Simple data class for overdue loan information
     */
    public static class OverdueLoanData {
        private final String loanId;
        private final UUID playerId;
        private int daysOverdue;
        private double totalPenalty;
        private Date firstOverdueDate;
        private Date lastUpdateDate;
        
        public OverdueLoanData(String loanId, UUID playerId) {
            this.loanId = loanId;
            this.playerId = playerId;
            this.daysOverdue = 0;
            this.totalPenalty = 0.0;
            this.firstOverdueDate = new Date();
            this.lastUpdateDate = new Date();
        }
        
        public void updateOverdueDays() {
            // Simple implementation - increment days
            daysOverdue++;
            lastUpdateDate = new Date();
        }
        
        public void addPenalty(double penalty) {
            totalPenalty += penalty;
            lastUpdateDate = new Date();
        }
        
        // Getters
        public String getLoanId() { return loanId; }
        public UUID getPlayerId() { return playerId; }
        public int getDaysOverdue() { return daysOverdue; }
        public double getTotalPenalty() { return totalPenalty; }
        public Date getFirstOverdueDate() { return firstOverdueDate; }
        public Date getLastUpdateDate() { return lastUpdateDate; }
    }
}
