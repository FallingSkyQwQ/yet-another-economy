package com.yae.api.credit;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceConfig;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.utils.Logging;
import com.yae.api.database.DatabaseService;
import com.yae.api.bank.BankAccountManager;
import com.yae.api.shop.ShopManager;
import com.yae.api.loan.LoanService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced Credit service with dynamic credit scoring algorithm
 * Supports multi-dimensional credit scoring with automatic updates
 */
public class CreditService implements Service {
    
    private final YAECore plugin;
    private ServiceConfig config;
    private boolean enabled = false;
    private boolean initialized = false;
    
    // Core components
    private CreditScoreCalculator scoreCalculator;
    private CreditUpdateService updateService;
    private CreditDataService dataService;
    
    // Cache for credit scores
    private final Map<UUID, CreditScoreData> creditCache = new ConcurrentHashMap<>();
    
    // Service dependencies
    private DatabaseService databaseService;
    private BankAccountManager bankAccountManager;
    private ShopManager shopManager;
    private LoanService loanService;
    
    // Defaults
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_CACHE_SIZE = 1000;
    private static final int DEFAULT_CACHE_EXPIRE_MINUTES = 30;
    private static final int DEFAULT_UPDATE_INTERVAL_HOURS = 24;
    private static final int DEFAULT_SCORE_VALIDITY_DAYS = 7;
    
    public CreditService(YAECore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public ServiceType getType() {
        return ServiceType.CREDIT;
    }
    
    @Override
    public String getName() {
        return "Credit Service";
    }
    
    @Override
    public String getDescription() {
        return "Manages credit scores and credit-related operations";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public boolean initialize() {
        this.config = getConfig();
        if (config == null) {
            return false;
        }
        this.enabled = config.getBoolean("enabled", DEFAULT_ENABLED);
        
        if (!enabled) {
            Logging.info("Credit service is disabled");
            return true;
        }
        
        // Initialize service dependencies
        if (!initializeDependencies()) {
            Logging.error("Failed to initialize credit service dependencies");
            return false;
        }
        
        // Initialize credit scoring components
        this.scoreCalculator = new CreditScoreCalculator(config);
        this.dataService = new CreditDataService(plugin, databaseService);
        this.updateService = new CreditUpdateService(plugin, this, dataService, config);
        
        // Initialize update service
        if (!updateService.initialize()) {
            Logging.error("Failed to initialize credit update service");
            return false;
        }
        
        this.initialized = true;
        Logging.info("Credit service initialized successfully with dynamic scoring");
        return true;
    }
    
    @Override
    public boolean reload() {
        return initialize();
    }
    
    @Override
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        Logging.info("Shutting down credit service...");
        
        // Shutdown update service
        if (updateService != null) {
            updateService.shutdown();
        }
        
        // Clear cache
        creditCache.clear();
        
        this.initialized = false;
        Logging.info("Credit service shut down successfully");
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public boolean dependsOn(ServiceType serviceType) {
        return false; // No strict dependencies for basic implementation
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
    public int getPriority() {
        return 50;
    }
    
    @Override
    public String getStatus() {
        if (!initialized) {
            return "Not initialized";
        }
        return enabled ? "Enabled" : "Disabled";
    }
    
    /**
     * Get credit score for a player with dynamic calculation
     */
    public int getCreditScore(UUID playerId) {
        if (!initialized || !enabled) {
            return CreditScoreCalculator.BASE_SCORE;
        }
        
        try {
            // Check cache first
            CreditScoreData cachedData = creditCache.get(playerId);
            if (cachedData != null && !cachedData.isExpired()) {
                return cachedData.getScore();
            }
            
            // Calculate new score
            CreditScoreData newScore = calculateCreditScore(playerId);
            creditCache.put(playerId, newScore);
            return newScore.getScore();
            
        } catch (Exception e) {
            Logging.error("Failed to get credit score for player " + playerId, e);
            return CreditScoreCalculator.BASE_SCORE; // Return base score on error
        }
    }
    
    /**
     * Check if player qualifies for specific loan type
     */
    public boolean qualifiesForLoan(UUID playerId, LoanType loanType) {
        int score = getCreditScore(playerId);
        return CreditScoreCalculator.qualifiesForLoan(score, loanType);
    }
    
    /**
     * Get credit grade for a player based on their credit score
     */
    public CreditGrade getCreditGrade(UUID playerId) {
        int score = getCreditScore(playerId);
        return CreditScoreCalculator.getCreditGrade(score);
    }
    
    /**
     * Force refresh credit score for a player
     */
    public CompletableFuture<Boolean> refreshCreditScore(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CreditScoreData newScore = calculateCreditScore(playerId);
                creditCache.put(playerId, newScore);
                Logging.info("Refreshed credit score for player " + playerId + ": " + newScore.getScore());
                return true;
            } catch (Exception e) {
                Logging.error("Failed to refresh credit score for player " + playerId, e);
                return false;
            }
        });
    }
    
    /**
     * Get detailed credit report for a player
     */
    public CreditReport getCreditReport(UUID playerId) {
        int score = getCreditScore(playerId);
        CreditGrade grade = CreditScoreCalculator.getCreditGrade(score);
        CreditScoreData scoreData = creditCache.get(playerId);
        
        return new CreditReport(playerId, score, grade, scoreData);
    }
    
    /**
     * Initialize service dependencies
     */
    private boolean initializeDependencies() {
        try {
            databaseService = (DatabaseService) plugin.getService(ServiceType.DATABASE);
            bankAccountManager = (BankAccountManager) plugin.getService(ServiceType.BANK);
            shopManager = (ShopManager) plugin.getService(ServiceType.SHOP);
            loanService = (LoanService) plugin.getService(ServiceType.LOAN);
            
            if (databaseService == null) {
                Logging.error("Database service not found");
                return false;
            }
            
            Logging.info("Credit service dependencies initialized successfully");
            return true;
            
        } catch (Exception e) {
            Logging.error("Failed to initialize credit service dependencies", e);
            return false;
        }
    }
    
    /**
     * Calculate credit score for a player using multi-dimensional analysis
     */
    private CreditScoreData calculateCreditScore(UUID playerId) {
        try {
            // Collect historical data
            List<CreditScoreCalculator.TransactionData> transactionHistory = dataService.getTransactionHistory(playerId, 30);
            List<CreditScoreCalculator.LoanData> loanHistory = dataService.getLoanHistory(playerId);
            CreditScoreCalculator.AccountData accountData = dataService.getAccountData(playerId);
            
            // Calculate score
            int score = scoreCalculator.calculateCreditScore(playerId, transactionHistory, loanHistory, accountData);
            
            return new CreditScoreData(score, LocalDateTime.now());
            
        } catch (Exception e) {
            Logging.error("Failed to calculate credit score for player " + playerId, e);
            return new CreditScoreData(CreditScoreCalculator.BASE_SCORE, LocalDateTime.now());
        }
    }
    
    public enum PenaltyType {
        LATE_PAYMENT,
        DEFAULT,
        FRAUD,
        ACCOUNT_SUSPENSION
    }
    
    /**
     * Credit score data with timestamp for caching
     */
    public static class CreditScoreData {
        private final int score;
        private final LocalDateTime calculatedAt;
        
        public CreditScoreData(int score, LocalDateTime calculatedAt) {
            this.score = score;
            this.calculatedAt = calculatedAt;
        }
        
        public int getScore() {
            return score;
        }
        
        public LocalDateTime getCalculatedAt() {
            return calculatedAt;
        }
        
        public boolean isExpired() {
            return calculatedAt.plusDays(DEFAULT_SCORE_VALIDITY_DAYS).isBefore(LocalDateTime.now());
        }
    }
    
    /**
     * Detailed credit report containing score, grade, and historical data
     */
    public static class CreditReport {
        private final UUID playerId;
        private final int score;
        private final CreditGrade grade;
        private final CreditScoreData scoreData;
        
        public CreditReport(UUID playerId, int score, CreditGrade grade, CreditScoreData scoreData) {
            this.playerId = playerId;
            this.score = score;
            this.grade = grade;
            this.scoreData = scoreData;
        }
        
        public UUID getPlayerId() {
            return playerId;
        }
        
        public int getScore() {
            return score;
        }
        
        public CreditGrade getGrade() {
            return grade;
        }
        
        public CreditScoreData getScoreData() {
            return scoreData;
        }
        
        public String getFormattedReport() {
            return String.format("信用报告\n" +
                "玩家ID: %s\n" +
                "信用评分: %d\n" +
                "信用等级: %s\n" +
                "等级描述: %s\n" +
                "评分时间: %s",
                playerId, score, grade.getDisplayName(), grade.getChineseName(),
                scoreData.getCalculatedAt().toString());
        }
    }
}
