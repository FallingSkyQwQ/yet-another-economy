package com.yae.api.loan;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified Loan Service for basic mortgage loans
 * Provides minimal functionality to ensure compilation and basic operations
 */
public class LoanService implements Service {
    
    private ServiceConfig config;
    private boolean enabled = false;
    private final Map<String, SimpleLoan> loans = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerLoans = new ConcurrentHashMap<>();
    
    public LoanService() {
        this(null);
    }
    
    public LoanService(com.yae.YetAnotherEconomy plugin) {
        // Plugin parameter is kept for future extension but not currently used
    }

    @Override
    public @NotNull String getName() {
        return "Simple Loan Service";
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
        return true;
    }

    @Override
    public boolean reload() {
        return true;
    }

    @Override
    public void shutdown() {
        this.enabled = false;
        loans.clear();
        playerLoans.clear();
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
    
    // Simple loan operations
    public SimpleLoan createLoan(UUID playerId, double amount, int termMonths, double interestRate) {
        String loanId = generateLoanId();
        SimpleLoan loan = new SimpleLoan(loanId, playerId, amount, termMonths, interestRate);
        loans.put(loanId, loan);
        playerLoans.computeIfAbsent(playerId, k -> new ArrayList<>()).add(loanId);
        return loan;
    }
    
    public SimpleLoan getLoan(String loanId) {
        return loans.get(loanId);
    }
    
    public List<SimpleLoan> getPlayerLoans(UUID playerId) {
        List<String> loanIds = playerLoans.get(playerId);
        if (loanIds == null) return Collections.emptyList();
        
        List<SimpleLoan> result = new ArrayList<>();
        for (String loanId : loanIds) {
            SimpleLoan loan = loans.get(loanId);
            if (loan != null) result.add(loan);
        }
        return result;
    }
    
    public boolean makePayment(String loanId, double amount) {
        SimpleLoan loan = loans.get(loanId);
        if (loan == null) return false;
        
        return loan.makePayment(amount);
    }
    
    public boolean isOverdue(String loanId) {
        SimpleLoan loan = loans.get(loanId);
        return loan != null && loan.isOverdue();
    }
    
    private String generateLoanId() {
        return "LOAN-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }
}
