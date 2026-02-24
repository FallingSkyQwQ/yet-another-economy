package com.yae.api.shop;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 交易回执模型类
 * 用于存储交易历史的详细信息
 */
public class TransactionReceipt {
    
    private UUID receiptId;
    private UUID playerId;
    private String action;
    private double amount;
    private String details;
    private String pluginSource;
    private String target;
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public TransactionReceipt() {
    }
    
    public TransactionReceipt(UUID receiptId, UUID playerId, String action, double amount, 
                            String details, String pluginSource, String target, 
                            LocalDateTime timestamp) {
        this.receiptId = receiptId;
        this.playerId = playerId;
        this.action = action;
        this.amount = amount;
        this.details = details;
        this.pluginSource = pluginSource;
        this.target = target;
        this.timestamp = timestamp;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public UUID getReceiptId() {
        return receiptId;
    }
    
    public void setReceiptId(UUID receiptId) {
        this.receiptId = receiptId;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public String getPluginSource() {
        return pluginSource;
    }
    
    public void setPluginSource(String pluginSource) {
        this.pluginSource = pluginSource;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return "TransactionReceipt{" +
                "receiptId=" + receiptId +
                ", playerId='" + playerId + '\'' +
                ", action='" + action + '\'' +
                ", amount=" + amount +
                ", details='" + details + '\'' +
                ", pluginSource='" + pluginSource + '\'' +
                ", target='" + target + '\'' +
                ", timestamp=" + timestamp +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
