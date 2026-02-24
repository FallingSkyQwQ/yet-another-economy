package com.yae.api.credit;

import com.yae.api.core.YAECore;
import com.yae.api.database.DatabaseService;
import com.yae.utils.Logging;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for collecting historical credit data from various sources
 * Integrates with database, bank, shop, and loan services to gather comprehensive player data
 */
public class CreditDataService {
    
    private final YAECore plugin;
    private final DatabaseService databaseService;
    
    public CreditDataService(YAECore plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
    }
    
    /**
     * Get database connection from the service
     */
    private Connection getConnection() throws SQLException {
        // Try to use reflection to access the databaseManager
        try {
            java.lang.reflect.Field databaseManagerField = DatabaseService.class.getDeclaredField("databaseManager");
            databaseManagerField.setAccessible(true);
            com.yae.api.database.DatabaseManager manager = (com.yae.api.database.DatabaseManager) databaseManagerField.get(databaseService);
            return manager.getConnection();
        } catch (Exception e) {
            Logging.error("Failed to get database connection via reflection", e);
            throw new SQLException("Database connection not available", e);
        }
    }
    
    /**
     * Get transaction history for a player within the specified number of days
     */
    public List<CreditScoreCalculator.TransactionData> getTransactionHistory(@NotNull UUID playerId, int days) {
        List<CreditScoreCalculator.TransactionData> transactions = new ArrayList<>();
        
        String transactionQuery = 
            "SELECT amount, timestamp, transaction_type " +
            "FROM yae_transactions " +
            "WHERE player_id = ? AND timestamp >= ? " +
            "ORDER BY timestamp DESC";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(transactionQuery)) {
            
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
            stmt.setString(1, playerId.toString());
            stmt.setTimestamp(2, Timestamp.valueOf(cutoffDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double amount = rs.getDouble("amount");
                    LocalDateTime timestamp = rs.getTimestamp("timestamp").toLocalDateTime();
                    String type = rs.getString("transaction_type");
                    
                    transactions.add(new CreditScoreCalculator.TransactionData(amount, timestamp, type));
                }
            }
            
            Logging.debug("Retrieved " + transactions.size() + " transactions for player " + playerId);
            
        } catch (SQLException e) {
            Logging.error("Failed to get transaction history for player " + playerId, e);
        }
        
        return transactions;
    }
    
    /**
     * Get loan history for a player
     */
    public List<CreditScoreCalculator.LoanData> getLoanHistory(@NotNull UUID playerId) {
        List<CreditScoreCalculator.LoanData> loanDataList = new ArrayList<>();
        
        String loanQuery = 
            "SELECT total_payments, on_time_payments, late_payments, " +
            "is_defaulted, is_recovered, last_overdue_date, default_date, recovery_date " +
            "FROM yae_loans " +
            "WHERE borrower_id = ? AND status != 'PENDING' " +
            "ORDER BY application_date DESC";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(loanQuery)) {
            
            stmt.setString(1, playerId.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int totalPayments = rs.getInt("total_payments");
                    int onTimePayments = rs.getInt("on_time_payments");
                    int latePayments = rs.getInt("late_payments");
                    boolean isDefaulted = rs.getBoolean("is_defaulted");
                    boolean isRecovered = rs.getBoolean("is_recovered");
                    
                    Timestamp lastOverdueTs = rs.getTimestamp("last_overdue_date");
                    Timestamp defaultTs = rs.getTimestamp("default_date");
                    Timestamp recoveryTs = rs.getTimestamp("recovery_date");
                    
                    LocalDateTime lastOverdueDate = lastOverdueTs != null ? lastOverdueTs.toLocalDateTime() : null;
                    LocalDateTime defaultDate = defaultTs != null ? defaultTs.toLocalDateTime() : null;
                    LocalDateTime recoveryDate = recoveryTs != null ? recoveryTs.toLocalDateTime() : null;
                    
                    loanDataList.add(new CreditScoreCalculator.LoanData(
                        totalPayments, onTimePayments, latePayments,
                        isDefaulted, isRecovered, lastOverdueDate, defaultDate, recoveryDate
                    ));
                }
            }
            
            Logging.debug("Retrieved " + loanDataList.size() + " loan records for player " + playerId);
            
        } catch (SQLException e) {
            Logging.error("Failed to get loan history for player " + playerId, e);
        }
        
        return loanDataList;
    }
    
    /**
     * Get account data for a player
     */
    public CreditScoreCalculator.AccountData getAccountData(@NotNull UUID playerId) {
        String accountQuery = 
            "SELECT current_balance, average_balance, created_at " +
            "FROM yae_bank_accounts " +
            "WHERE player_id = ? AND status = 'ACTIVE' " +
            "LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(accountQuery)) {
            
            stmt.setString(1, playerId.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double currentBalance = rs.getDouble("current_balance");
                    double averageBalance = rs.getDouble("average_balance");
                    LocalDateTime createdDate = rs.getTimestamp("created_at").toLocalDateTime();
                    
                    Logging.debug("Retrieved account data for player " + playerId + 
                                 ": balance=" + currentBalance + ", avg=" + averageBalance);
                    
                    return new CreditScoreCalculator.AccountData(currentBalance, averageBalance, createdDate);
                }
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to get account data for player " + playerId, e);
        }
        
        // Return default data if no account found
        return new CreditScoreCalculator.AccountData(0.0, 0.0, LocalDateTime.now());
    }
    
    /**
     * Get deposit history for a player
     */
    public int getDepositDays(@NotNull UUID playerId, int days) {
        String depositQuery = 
            "SELECT COUNT(DISTINCT DATE(created_at)) as deposit_days " +
            "FROM yae_deposits " +
            "WHERE player_id = ? AND created_at >= ? AND status = 'ACTIVE'";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(depositQuery)) {
            
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
            stmt.setString(1, playerId.toString());
            stmt.setTimestamp(2, Timestamp.valueOf(cutoffDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("deposit_days");
                }
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to get deposit days for player " + playerId, e);
        }
        
        return 0;
    }
    
    /**
     * Get login statistics for a player
     */
    public int getLoginDays(@NotNull UUID playerId, int days) {
        // This would integrate with user service if available
        // For now, return a default value based on account age
        
        String accountAgeQuery = 
            "SELECT created_at FROM yae_player_profiles WHERE player_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(accountAgeQuery)) {
            
            stmt.setString(1, playerId.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    long daysSinceCreation = java.time.Duration.between(createdAt, LocalDateTime.now()).toDays();
                    return (int) Math.min(days, daysSinceCreation);
                }
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to get login stats for player " + playerId, e);
        }
        
        return Math.min(30, days); // Assume daily login for new accounts
    }
    
    /**
     * Check if player has recent overdrafts
     */
    public boolean hasRecentOverdrafts(@NotNull UUID playerId, int days) {
        String overdraftQuery = 
            "SELECT COUNT(*) as overdraft_count " +
            "FROM yae_bank_transactions " +
            "WHERE player_id = ? AND transaction_type = 'OVERDRAFT' " +
            "AND timestamp >= ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(overdraftQuery)) {
            
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
            stmt.setString(1, playerId.toString());
            stmt.setTimestamp(2, Timestamp.valueOf(cutoffDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("overdraft_count") > 0;
                }
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to check overdrafts for player " + playerId, e);
        }
        
        return false;
    }
    
    /**
     * Get credit utilization ratio for a player
     */
    public double getCreditUtilization(@NotNull UUID playerId) {
        String utilizationQuery = 
            "SELECT COALESCE(SUM(current_balance), 0) as total_balance, " +
            "COALESCE(SUM(credit_limit), 100000) as total_credit " +
            "FROM yae_credit_accounts " +
            "WHERE player_id = ? AND status = 'ACTIVE'";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(utilizationQuery)) {
            
            stmt.setString(1, playerId.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double totalBalance = rs.getDouble("total_balance");
                    double totalCredit = rs.getDouble("total_credit");
                    
                    return totalCredit > 0 ? totalBalance / totalCredit : 0.0;
                }
            }
            
        } catch (SQLException e) {
            Logging.error("Failed to get credit utilization for player " + playerId, e);
        }
        
        return 0.0;
    }
}
