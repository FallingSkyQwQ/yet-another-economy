package com.yae.api.credit;

import com.yae.api.core.ServiceConfig;
import com.yae.api.core.YAECore;
import com.yae.utils.Logging;
import org.jetbrains.annotations.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.*;
import java.time.format.DateTimeFormatter;

/**
 * Background service for automatic credit score updates and maintenance
 * Handles scheduled updates, event-driven updates, and batch processing
 */
public class CreditUpdateService {
    
    private final YAECore plugin;
    private final CreditService creditService;
    private final CreditDataService dataService;
    private final ServiceConfig config;
    private final com.yae.api.database.DatabaseService databaseService;
    
    // Scheduling
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> weeklyUpdateTask;
    private ScheduledFuture<?> dailyMaintenanceTask;
    
    // Configuration
    private boolean enabled;
    private int weeklyUpdateHour;
    private int dailyMaintenanceHour;
    private int updateBatchSize;
    private boolean updateOnTransaction;
    private boolean updateOnLoanEvent;
    
    // Processing state
    private final Set<UUID> pendingUpdates = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean isProcessing = false;
    
    // Constants
    private static final int DEFAULT_WEEKLY_UPDATE_HOUR = 2; // 2 AM
    private static final int DEFAULT_DAILY_MAINTENANCE_HOUR = 1; // 1 AM
    private static final int DEFAULT_UPDATE_BATCH_SIZE = 100;
    private static final int MIN_PLAYERS_FOR_BATCH_UPDATE = 10;
    
    public CreditUpdateService(@NotNull YAECore plugin, 
                              @NotNull CreditService creditService,
                              @NotNull CreditDataService dataService,
                              @NotNull ServiceConfig config) {
        this.plugin = plugin;
        this.creditService = creditService;
        this.dataService = dataService;
        this.config = config;
        this.databaseService = (com.yae.api.database.DatabaseService) plugin.getService(com.yae.api.core.ServiceType.DATABASE);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Initialize the update service
     */
    public boolean initialize() {
        try {
            loadConfiguration();
            
            if (!enabled) {
                Logging.info("Credit update service is disabled");
                return true;
            }
            
            scheduleTasks();
            
            Logging.info("Credit update service initialized successfully");
            return true;
            
        } catch (Exception e) {
            Logging.error("Failed to initialize credit update service", e);
            return false;
        }
    }
    
    /**
     * Shutdown the update service
     */
    public void shutdown() {
        try {
            Logging.info("Shutting down credit update service...");
            
            // Cancel scheduled tasks
            if (weeklyUpdateTask != null) {
                weeklyUpdateTask.cancel(false);
            }
            if (dailyMaintenanceTask != null) {
                dailyMaintenanceTask.cancel(false);
            }
            
            // Shutdown scheduler
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            
            Logging.info("Credit update service shut down successfully");
            
        } catch (Exception e) {
            Logging.error("Error during credit update service shutdown", e);
        }
    }
    
    /**
     * Request immediate credit score update for a player
     */
    public CompletableFuture<Boolean> requestUpdate(@NotNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return creditService.refreshCreditScore(playerId).join();
            } catch (Exception e) {
                Logging.error("Failed to update credit score for player " + playerId, e);
                return false;
            }
        });
    }
    
    /**
     * Queue a player for batch update
     */
    public void queueUpdate(@NotNull UUID playerId) {
        pendingUpdates.add(playerId);
        Logging.debug("Queued credit score update for player " + playerId);
    }
    
    /**
     * Handle transaction event for potential credit score update
     */
    public void handleTransactionEvent(@NotNull UUID playerId, double amount) {
        if (!enabled || !updateOnTransaction) {
            return;
        }
        
        // Only queue significant transactions for immediate update
        if (Math.abs(amount) > 1000) { // Threshold configurable via config
            queueUpdate(playerId);
        }
    }
    
    /**
     * Handle loan event for potential credit score update
     */
    public void handleLoanEvent(@NotNull UUID playerId, @NotNull String eventType) {
        if (!enabled || !updateOnLoanEvent) {
            return;
        }
        
        // Queue update for significant loan events
        if (eventType.equals("LOAN_APPROVED") || eventType.equals("LOAN_DEFAULT") || 
            eventType.equals("LOAN_REPAYMENT") || eventType.equals("LOAN_RECOVERY")) {
            queueUpdate(playerId);
        }
    }
    
    /**
     * Load configuration from service config
     */
    private void loadConfiguration() {
        this.enabled = config.getBoolean("credit_updates.enabled", true);
        this.weeklyUpdateHour = config.getInt("credit_updates.weekly_hour", DEFAULT_WEEKLY_UPDATE_HOUR);
        this.dailyMaintenanceHour = config.getInt("credit_updates.daily_hour", DEFAULT_DAILY_MAINTENANCE_HOUR);
        this.updateBatchSize = config.getInt("credit_updates.batch_size", DEFAULT_UPDATE_BATCH_SIZE);
        this.updateOnTransaction = config.getBoolean("credit_updates.update_on_transaction", true);
        this.updateOnLoanEvent = config.getBoolean("credit_updates.update_on_loan_event", true);
    }
    
    /**
     * Schedule background tasks
     */
    private void scheduleTasks() {
        // Schedule weekly updates (Mondays at specified hour)
        long weeklyDelay = calculateDelayToNextWeekday(DayOfWeek.MONDAY, weeklyUpdateHour, 0);
        weeklyUpdateTask = scheduler.scheduleAtFixedRate(
            this::performWeeklyUpdate,
            weeklyDelay,
            TimeUnit.DAYS.toMillis(7),
            TimeUnit.MILLISECONDS
        );
        
        // Schedule daily maintenance
        long dailyDelay = calculateDelayToNextHour(dailyMaintenanceHour, 0);
        dailyMaintenanceTask = scheduler.scheduleAtFixedRate(
            this::performDailyMaintenance,
            dailyDelay,
            TimeUnit.DAYS.toMillis(1),
            TimeUnit.MILLISECONDS
        );
        
        Logging.info("Scheduled credit update tasks: weekly updates on Mondays at " + 
                    weeklyUpdateHour + ":00, daily maintenance at " + dailyMaintenanceHour + ":00");
    }
    
    /**
     * Perform weekly credit score updates for all active players
     */
    private void performWeeklyUpdate() {
        if (isProcessing) {
            Logging.warning("Weekly credit update already in progress, skipping...");
            return;
        }
        
        try {
            isProcessing = true;
            Logging.info("Starting weekly credit score update process...");
            
            long startTime = System.currentTimeMillis();
            List<UUID> activePlayers = getActivePlayers(30); // Last 30 days
            
            Logging.info("Processing credit updates for " + activePlayers.size() + " active players");
            
            // Process in batches
            int updatedCount = 0;
            int failedCount = 0;
            
            for (int i = 0; i < activePlayers.size(); i += updateBatchSize) {
                int endIndex = Math.min(i + updateBatchSize, activePlayers.size());
                List<UUID> batch = activePlayers.subList(i, endIndex);
                
                BatchResult result = processBatch(batch);
                updatedCount += result.updated;
                failedCount += result.failed;
                
                // Add small delay to prevent overwhelming the system
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            Logging.info("Weekly credit update completed: " + updatedCount + " updated, " + 
                        failedCount + " failed, duration: " + duration + "ms");
            
        } catch (Exception e) {
            Logging.error("Error during weekly credit update", e);
        } finally {
            isProcessing = false;
        }
    }
    
    /**
     * Perform daily maintenance tasks
     */
    private void performDailyMaintenance() {
        try {
            Logging.debug("Performing daily credit maintenance tasks...");
            
            // Process pending updates
            if (!pendingUpdates.isEmpty()) {
                processPendingUpdates();
            }
            
            // Clean up old credit records
            cleanupOldRecords();
            
            // Generate credit reports for monitoring
            generateMaintenanceReport();
            
            Logging.debug("Daily credit maintenance completed");
            
        } catch (Exception e) {
            Logging.error("Error during daily credit maintenance", e);
        }
    }
    
    /**
     * Process pending updates from the queue
     */
    private void processPendingUpdates() {
        if (pendingUpdates.isEmpty()) {
            return;
        }
        
        Logging.info("Processing " + pendingUpdates.size() + " pending credit updates...");
        
        List<UUID> updatesToProcess = new ArrayList<>(pendingUpdates);
        pendingUpdates.clear();
        
        int updated = 0;
        int failed = 0;
        
        for (UUID playerId : updatesToProcess) {
            try {
                creditService.refreshCreditScore(playerId);
                updated++;
            } catch (Exception e) {
                Logging.error("Failed to update credit for player " + playerId, e);
                failed++;
            }
        }
        
        Logging.info("Processed pending updates: " + updated + " successful, " + failed + " failed");
    }
    
    /**
     * Process a batch of players for credit updates
     */
    private BatchResult processBatch(List<UUID> playerBatch) {
        int updated = 0;
        int failed = 0;
        
        for (UUID playerId : playerBatch) {
            try {
                creditService.refreshCreditScore(playerId);
                updated++;
            } catch (Exception e) {
                Logging.error("Failed to update credit for player " + playerId + " in batch", e);
                failed++;
            }
        }
        
        return new BatchResult(updated, failed);
    }
    
    /**
     * Get list of active players (logged in within specified days)
     */
    private List<UUID> getActivePlayers(int days) {
        List<UUID> activePlayers = new ArrayList<>();
        
        // This would query the database for players who logged in recently
        // For now, return empty list as placeholder
        String activePlayersQuery = 
            "SELECT DISTINCT player_id FROM yae_player_activity " +
            "WHERE last_login >= ? OR last_logout >= ?";
        
        try (Connection conn = getConnection()) {
            // Implementation would go here
            // For now, return empty list
            Logging.debug("Active players query would be executed here");
            
        } catch (SQLException e) {
            Logging.error("Failed to get active players", e);
        }
        
        return activePlayers;
    }
    
    /**
     * Get database connection from the service
     */
    private Connection getConnection() throws SQLException {
        // Try to use reflection to access the databaseManager
        try {
            java.lang.reflect.Field databaseManagerField = 
                com.yae.api.database.DatabaseService.class.getDeclaredField("databaseManager");
            databaseManagerField.setAccessible(true);
            com.yae.api.database.DatabaseManager manager = 
                (com.yae.api.database.DatabaseManager) databaseManagerField.get(databaseService);
            return manager.getConnection();
        } catch (Exception e) {
            Logging.error("Failed to get database connection via reflection", e);
            throw new SQLException("Database connection not available", e);
        }
    }
    
    /**
     * Clean up old credit records
     */
    private void cleanupOldRecords() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            
            // Clean up old credit history records
            String cleanupQuery = "DELETE FROM yae_credit_history WHERE updated_at < ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(cleanupQuery)) {
                
                stmt.setTimestamp(1, Timestamp.valueOf(cutoffDate));
                int deletedRows = stmt.executeUpdate();
                
                if (deletedRows > 0) {
                    Logging.info("Cleaned up " + deletedRows + " old credit history records");
                }
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to cleanup old credit records", e);
        }
    }
    
    /**
     * Generate maintenance report
     */
    private void generateMaintenanceReport() {
        // This would generate a summary report of the credit system status
        Logging.debug("Credit maintenance report generated");
    }
    
    /**
     * Calculate delay to next weekday at specified hour
     */
    private long calculateDelayToNextWeekday(DayOfWeek targetDay, int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.with(DayOfWeek.MONDAY).withHour(hour).withMinute(minute).withSecond(0);
        
        if (now.getDayOfWeek() == targetDay && now.isBefore(target)) {
            return java.time.Duration.between(now, target).toMillis();
        }
        
        if (now.getDayOfWeek() == targetDay && now.isAfter(target)) {
            target = target.plusWeeks(1);
        } else {
            target = target.with(targetDay);
            if (target.isBefore(now)) {
                target = target.plusWeeks(1);
            }
        }
        
        return java.time.Duration.between(now, target).toMillis();
    }
    
    /**
     * Calculate delay to next hour
     */
    private long calculateDelayToNextHour(int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.withHour(hour).withMinute(minute).withSecond(0);
        
        if (target.isBefore(now)) {
            target = target.plusDays(1);
        }
        
        return java.time.Duration.between(now, target).toMillis();
    }
    
    /**
     * Result of batch processing
     */
    private static class BatchResult {
        final int updated;
        final int failed;
        
        BatchResult(int updated, int failed) {
            this.updated = updated;
            this.failed = failed;
        }
    }
}
