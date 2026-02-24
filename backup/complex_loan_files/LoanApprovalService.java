package com.yae.api.loan;

import com.yae.api.core.ServiceConfig;

/**
 * Loan approval service for processing credit-based and collateral-based approvals
 */
public class LoanApprovalService {
    
    private final ServiceConfig config;
    
    public LoanApprovalService(ServiceConfig config) {
        this.config = config;
    }
    
    /**
     * Calculate final loan terms based on application
     */
    public LoanTerms calculateFinalTerms(LoanApplication application) {
        double interestRate = applicationService.calculateInterestRate(
            application.getPlayerId(), application.getLoanType(), 
            application.getRequestedAmount(), application.getTermMonths()
        );
        
        return new LoanTerms(new LoanTerms.TermsOption(
            application.getTermMonths(), interestRate, application.getRequestedAmount()
        ));
    }
    
    /**
     * Attempt automatic approval based on criteria
     */
    public AutoApprovalResult attemptAutoApproval(LoanApplication application) {
        int creditScore = application.getCreditScore();
        LoanType loanType = application.getLoanType();
        double amount = application.getRequestedAmount();
        
        int minAutoApproveScore = getMinAutoApproveScore(loanType);
        
        if (creditScore >= minAutoApproveScore) {
            return AutoApprovalResult.approved("信用评分优秀，符合自动审批条件");
        }
        
        return AutoApprovalResult.rejected("需要人工审核");
    }
    
    // Additional methods would be implemented here
    
    private int getMinAutoApproveScore(LoanType loanType) {
        switch (loanType) {
            case CREDIT: return 720;
            case MORTGAGE: return 750;
            case BUSINESS: return 780;
            case EMERGENCY: return 650;
            default: return 700;
        }
    }
}
