package com.yae.api.loan;

import java.util.*;

/**
 * Simplified loan model for basic mortgage loans
 */
public class SimpleLoan {
    private final String loanId;
    private final UUID playerId;
    private final double principal;
    private final double interestRate;
    private final int termMonths;
    private final Date createdDate;
    private final Date dueDate;
    
    private double remainingBalance;
    private double monthlyPayment;
    private int paymentsMade;
    private boolean isActive;
    private boolean isOverdue;
    
    public SimpleLoan(String loanId, UUID playerId, double principal, int termMonths, double interestRate) {
        this.loanId = loanId;
        this.playerId = playerId;
        this.principal = principal;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.createdDate = new Date();
        this.dueDate = calculateDueDate();
        this.remainingBalance = calculateTotalAmount();
        this.monthlyPayment = calculateMonthlyPayment();
        this.paymentsMade = 0;
        this.isActive = true;
        this.isOverdue = false;
    }
    
    private Date calculateDueDate() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(createdDate);
        cal.add(Calendar.MONTH, termMonths);
        return cal.getTime();
    }
    
    private double calculateTotalAmount() {
        return principal + (principal * interestRate / 100 * termMonths / 12);
    }
    
    private double calculateMonthlyPayment() {
        return calculateTotalAmount() / termMonths;
    }
    
    public boolean makePayment(double amount) {
        if (!isActive || amount <= 0) return false;
        
        remainingBalance -= amount;
        if (remainingBalance <= 0) {
            remainingBalance = 0;
            isActive = false;
        }
        
        paymentsMade++;
        updateOverdueStatus();
        return true;
    }
    
    private void updateOverdueStatus() {
        Date now = new Date();
        if (now.after(dueDate) && remainingBalance > 0) {
            isOverdue = true;
        }
    }
    
    // Getters
    public String getLoanId() { return loanId; }
    public UUID getPlayerId() { return playerId; }
    public double getPrincipal() { return principal; }
    public double getInterestRate() { return interestRate; }
    public int getTermMonths() { return termMonths; }
    public Date getCreatedDate() { return createdDate; }
    public Date getDueDate() { return dueDate; }
    public double getRemainingBalance() { return remainingBalance; }
    public double getMonthlyPayment() { return monthlyPayment; }
    public int getPaymentsMade() { return paymentsMade; }
    public boolean isActive() { return isActive; }
    public boolean isOverdue() { return isOverdue; }
}
