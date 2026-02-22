package com.yae.api.database;

import com.yae.api.core.YAECore;
import com.yae.utils.Logging;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Database initializer service that creates necessary tables and initializes data
 * Handles database schema creation and migration
 */
public class DatabaseInitializer {
    
    private final YAECore plugin;
    private final DatabaseService databaseService;
    
    public DatabaseInitializer(YAECore plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
    }
    
    /**
     * Initialize the database schema and default data
     */
    public boolean initializeDatabase() {
        try {
            Logging.info("Starting database initialization...");
            
            // Check if database is connected
            if (!databaseService.isHealthy()) {
                Logging.error("Database is not healthy, cannot initialize");
                return false;
            }
            
            // Execute SQL scripts to create tables
            boolean tablesCreated = createLoanTables();
            if (!tablesCreated) {
                Logging.error("Failed to create loan tables");
                return false;
            }
            
            // Create credit system tables
            boolean creditTablesCreated = createCreditTables();
            if (!creditTablesCreated) {
                Logging.error("Failed to create credit system tables");
                return false;
            }
            
            // Create overdue processing tables
            boolean overdueTablesCreated = createOverdueTables();
            if (!overdueTablesCreated) {
                Logging.error("Failed to create overdue processing tables");
                return false;
            }
            
            // Verify table creation
            boolean verificationPassed = verifyTableCreation();
            if (!verificationPassed) {
                Logging.error("Database table verification failed");
                return false;
            }
            
            // Insert default data
            boolean defaultsInserted = insertDefaultData();
            if (!defaultsInserted) {
                Logging.error("Failed to insert default data");
                return false;
            }
            
            Logging.info("Database initialization completed successfully");
            return true;
            
        } catch (Exception e) {
            Logging.log(Level.SEVERE, "Database initialization failed", e);
            return false;
        }
    }
    
    /**
     * Create loan system tables
     */
    private boolean createLoanTables() {
        try {
            // Define table creation queries
            String[] createQueries = {
                // Main loans table
                "CREATE TABLE IF NOT EXISTS yae_loans (" +
                "loan_id VARCHAR(50) PRIMARY KEY," +
                "borrower_uuid VARCHAR(36) NOT NULL," +
                "lender_uuid VARCHAR(36) NOT NULL," +
                "loan_type VARCHAR(20) NOT NULL," +
                "loan_purpose TEXT," +
                "principal_amount DECIMAL(15,2) NOT NULL," +
                "current_balance DECIMAL(15,2) NOT NULL," +
                "interest_rate DECIMAL(5,4) NOT NULL," +
                "term_months INTEGER NOT NULL," +
                "maturity_date TIMESTAMP NOT NULL," +
                "next_payment_date TIMESTAMP NOT NULL," +
                "monthly_payment DECIMAL(12,2) NOT NULL," +
                "payment_method VARCHAR(20) NOT NULL DEFAULT 'VAULT'," +
                "repayment_method VARCHAR(20) NOT NULL DEFAULT 'EQUAL_INSTALLMENT'," +
                "status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                "payments_made INTEGER DEFAULT 0," +
                "total_payments INTEGER NOT NULL," +
                "overdue_payments INTEGER DEFAULT 0," +
                "overdue_amount DECIMAL(12,2) DEFAULT 0," +
                "default_date TIMESTAMP," +
                "collateral_type VARCHAR(50)," +
                "collateral_value DECIMAL(15,2)," +
                "borrower_credit_score INTEGER DEFAULT 650," +
                "borrower_credit_grade VARCHAR(10) DEFAULT 'C'," +
                "application_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "approval_date TIMESTAMP," +
                "disbursement_date TIMESTAMP," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")",
                
                // Loan payments table
                "CREATE TABLE IF NOT EXISTS yae_loan_payments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "loan_id VARCHAR(50) NOT NULL," +
                "payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "payment_amount DECIMAL(12,2) NOT NULL," +
                "principal_payment DECIMAL(12,2) NOT NULL," +
                "interest_payment DECIMAL(12,2) NOT NULL," +
                "penalty_payment DECIMAL(12,2) DEFAULT 0," +
                "payment_method VARCHAR(20) NOT NULL DEFAULT 'VAULT'," +
                "payment_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'," +
                "transaction_id VARCHAR(50)," +
                "notes TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)" +
                ")",
                
                // Repayment schedule table
                "CREATE TABLE IF NOT EXISTS yae_loan_schedule (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "loan_id VARCHAR(50) NOT NULL," +
                "payment_number INTEGER NOT NULL," +
                "payment_date DATE NOT NULL," +
                "scheduled_payment DECIMAL(12,2) NOT NULL," +
                "principal_payment DECIMAL(12,2) NOT NULL," +
                "interest_payment DECIMAL(12,2) NOT NULL," +
                "remaining_balance DECIMAL(15,2) NOT NULL," +
                "payment_status VARCHAR(20) DEFAULT 'PENDING'," +
                "actual_payment_date TIMESTAMP," +
                "actual_payment_amount DECIMAL(12,2)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)" +
                ")",
                
                // Create indexes for performance
                "CREATE INDEX IF NOT EXISTS idx_loans_borrower ON yae_loans(borrower_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_loans_status ON yae_loans(status)",
                "CREATE INDEX IF NOT EXISTS idx_loans_type ON yae_loans(loan_type)",
                "CREATE INDEX IF NOT EXISTS idx_loans_application_date ON yae_loans(application_date)",
                "CREATE INDEX IF NOT EXISTS idx_payments_loan ON yae_loan_payments(loan_id)",
                "CREATE INDEX IF NOT EXISTS idx_schedule_loan ON yae_loan_schedule(loan_id)",
                "CREATE INDEX IF NOT EXISTS idx_schedule_date ON yae_loan_schedule(payment_date)"
            };
            
            return executeTableCreation(createQueries);
            
        } catch (Exception e) {
            Logging.log(Level.WARNING, "Failed to create loan tables", e);
            return false;
        }
    }
    
    /**
     * Create credit system tables
     */
    private boolean createCreditTables() {
        try {
            String[] createQueries = {
                // Credit scores table
                "CREATE TABLE IF NOT EXISTS yae_credit_scores (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "credit_score INTEGER NOT NULL CHECK (credit_score BETWEEN 300 AND 850)," +
                "credit_grade VARCHAR(10) NOT NULL," +
                "last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "calculation_method VARCHAR(50) DEFAULT 'standard'," +
                "factors_snapshot TEXT," +
                "notes TEXT," +
                "updated_by VARCHAR(50)" +
                ")",
                
                // Credit penalties table
                "CREATE TABLE IF NOT EXISTS yae_credit_penalties (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "penalty_type VARCHAR(50) NOT NULL," +
                "penalty_amount INTEGER NOT NULL," +
                "current_score INTEGER NOT NULL," +
                "resulting_score INTEGER NOT NULL," +
                "reason TEXT," +
                "applied_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "expires_date TIMESTAMP," +
                "applied_by VARCHAR(50)," +
                "loan_id VARCHAR(50)," +
                "FOREIGN KEY (player_uuid) REFERENCES yae_credit_scores(player_uuid)" +
                ")",
                
                // Credit bonuses table
                "CREATE TABLE IF NOT EXISTS yae_credit_bonuses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "bonus_type VARCHAR(50) NOT NULL," +
                "bonus_amount INTEGER NOT NULL," +
                "current_score INTEGER NOT NULL," +
                "resulting_score INTEGER NOT NULL," +
                "reason TEXT," +
                "applied_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "expires_date TIMESTAMP," +
                "applied_by VARCHAR(50)," +
                "loan_id VARCHAR(50)," +
                "FOREIGN KEY (player_uuid) REFERENCES yae_credit_scores(player_uuid)" +
                ")",
                
                // Default collateral types
                "CREATE TABLE IF NOT EXISTS yae_collateral_types (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "collateral_key VARCHAR(50) NOT NULL UNIQUE," +
                "collateral_name VARCHAR(100) NOT NULL," +
                "material_type VARCHAR(100)," +
                "base_value DECIMAL(15,2) NOT NULL," +
                "discount_rate DECIMAL(5,4) NOT NULL DEFAULT 0.8," +
                "valuation_method VARCHAR(50) DEFAULT 'FIXED'," +
                "is_active BOOLEAN DEFAULT TRUE," +
                "description TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")",
                
                // Collateral evaluation rules
                "CREATE INDEX IF NOT EXISTS idx_penalties_player ON yae_credit_penalties(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_bonuses_player ON yae_credit_bonuses(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_collateral_types ON yae_collateral_types(collateral_key)"
            };
            
            return executeTableCreation(createQueries);
            
        } catch (Exception e) {
            Logging.log(Level.WARNING, "Failed to create credit tables", e);
            return false;
        }
    }
    
    /**
     * Create overdue processing tables
     */
    private boolean createOverdueTables() {
        try {
            String[] createQueries = {
                // Overdue records table
                "CREATE TABLE IF NOT EXISTS yae_overdue_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "loan_id VARCHAR(50) NOT NULL," +
                "borrower_uuid VARCHAR(36) NOT NULL," +
                "original_balance DECIMAL(15,2) NOT NULL," +
                "current_balance DECIMAL(15,2) NOT NULL," +
                "overdue_amount DECIMAL(15,2) NOT NULL DEFAULT 0," +
                "total_overdue_payments INTEGER DEFAULT 0," +
                "first_overdue_date TIMESTAMP," +
                "last_overdue_date TIMESTAMP," +
                "status VARCHAR(20) DEFAULT 'ACTIVE'," +
                "collection_attempt_count INTEGER DEFAULT 0," +
                "last_collection_attempt TIMESTAMP," +
                "penalty_amount DECIMAL(15,2) DEFAULT 0," +
                "resolved_date TIMESTAMP," +
                "resolved_by VARCHAR(50)," +
                "resolution_notes TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)" +
                ")",
                
                // Collection attempts table
                "CREATE TABLE IF NOT EXISTS yae_collection_attempts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "loan_id VARCHAR(50) NOT NULL," +
                "borrower_uuid VARCHAR(36) NOT NULL," +
                "attempt_method VARCHAR(50) NOT NULL," +
                "attempt_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "attempt_number INTEGER DEFAULT 1," +
                "is_success BOOLEAN DEFAULT FALSE," +
                "notes TEXT," +
                "response_received BOOLEAN DEFAULT FALSE," +
                "response_content TEXT," +
                "created_by VARCHAR(50)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)" +
                ")",
                
                // Risk indicators table
                "CREATE TABLE IF NOT EXISTS yae_loan_risk_indicators (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "loan_id VARCHAR(50) NOT NULL," +
                "borrower_uuid VARCHAR(36) NOT NULL," +
                "risk_type VARCHAR(50) NOT NULL," +
                "risk_score INTEGER DEFAULT 0," +
                "risk_level VARCHAR(20) DEFAULT 'LOW'," +
                "indicator_reason TEXT," +
                "detection_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "auto_mitigation_applied BOOLEAN DEFAULT FALSE," +
                "mitigation_applied VARCHAR(200)," +
                "is_resolved BOOLEAN DEFAULT FALSE," +
                "resolved_date TIMESTAMP," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)" +
                ")",
                
                // Loan blacklist table
                "CREATE TABLE IF NOT EXISTS yae_loan_blacklist (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid VARCHAR(36) NOT NULL UNIQUE," +
                "blacklist_reason TEXT NOT NULL," +
                "blacklist_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "blacklisted_by VARCHAR(50)," +
                "expiry_date TIMESTAMP," +
                "is_permanent BOOLEAN DEFAULT FALSE," +
                "appeal_allowed BOOLEAN DEFAULT TRUE," +
                "additional_data TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (player_uuid) REFERENCES yae_credit_scores(player_uuid)" +
                ")",
                
                // Audit log table
                "CREATE TABLE IF NOT EXISTS yae_loan_audit_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "loan_id VARCHAR(50) NOT NULL," +
                "action_type VARCHAR(50) NOT NULL," +
                "action_description TEXT," +
                "previous_value TEXT," +
                "new_value TEXT," +
                "action_by VARCHAR(50) NOT NULL," +
                "action_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "is_system_action BOOLEAN DEFAULT FALSE," +
                "ip_address VARCHAR(45)," +
                "additional_data TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id)" +
                ")",
                
                // Create indexes
                "CREATE INDEX IF NOT EXISTS idx_overdue_borrower ON yae_overdue_records(borrower_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_overdue_status ON yae_overdue_records(status)",
                "CREATE INDEX IF NOT EXISTS idx_collection_attempts ON yae_collection_attempts(loan_id)",
                "CREATE INDEX IF NOT EXISTS idx_risk_indicators ON yae_loan_risk_indicators(loan_id)",
                "CREATE INDEX IF NOT EXISTS idx_blacklist_player ON yae_loan_blacklist(player_uuid)"
            };
            
            return executeTableCreation(createQueries);
            
        } catch (Exception e) {
            Logging.log(Level.WARNING, "Failed to create overdue tables", e);
            return false;
        }
    }
    
    /**
     * Execute table creation queries
     */
    private boolean executeTableCreation(String[] createQueries) {
        for (String query : createQueries) {
            try {
                databaseService.executeUpdate(query).get();
                Logging.debug("Executed table creation: " + query.substring(0, Math.min(50, query.length())) + "...");
            } catch (Exception e) {
                Logging.log(Level.WARNING, "Failed to execute table creation query: " + query.substring(0, Math.min(100, query.length())), e);
                return false;
            }
        }
        return true;
    }
    
    /**
     * Verify that all required tables were created successfully
     */
    private boolean verifyTableCreation() {
        try {
            String[] requiredTables = {
                "yae_loans", "yae_loan_payments", "yae_loan_schedule",
                "yae_credit_scores", "yae_credit_penalties", "yae_credit_bonuses",
                "yae_overdue_records", "yae_collection_attempts",
                "yae_loan_risk_indicators", "yae_loan_blacklist"
            };
            
            for (String table : requiredTables) {
                String checkQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
                ResultSet result = databaseService.executeQuery(checkQuery, table).get();
                
                if (!result.next()) {
                    Logging.error("Table " + table + " was not created successfully");
                    return false;
                }
            }
            
            Logging.info("All required tables verified successfully");
            return true;
            
        } catch (Exception e) {
            Logging.log(Level.WARNING, "Table verification failed", e);
            return false;
        }
    }
    
    /**
     * Insert default data into tables
     */
    private boolean insertDefaultData() {
        try {
            insertDefaultCollateralTypes();
            insertDefaultSettings();
            insertDefaultLoanSettings();
            
            Logging.info("Default data inserted successfully");
            return true;
            
        } catch (Exception e) {
            Logging.log(Level.WARNING, "Failed to insert default data", e);
            return false;
        }
    }
    
    /**
     * Insert default collateral types
     */
    private void insertDefaultCollateralTypes() {
        String[][] collateralData = {
            {"diamond", "钻石", "DIAMOND", "1000.00", "0.80", "Minecraft钻石，高价值稀缺资源"},
            {"diamond_block", "钻石块", "DIAMOND_BLOCK", "9000.00", "0.85", "9个钻石合成的方块，便于存储"},
            {"emerald", "绿宝石", "EMERALD", "800.00", "0.75", "贸易货币"},
            {"emerald_block", "绿宝石块", "EMERALD_BLOCK", "7200.00", "0.80", "9个绿宝石合成的方块"},
            {"gold_ingot", "金锭", "GOLD_INGOT", "500.00", "0.70", "贵金属"},
            {"gold_block", "金块", "GOLD_BLOCK", "4500.00", "0.75", "9个金锭合成的方块"},
            {"iron_ingot", "铁锭", "IRON_INGOT", "200.00", "0.60", "基础金属材料"},
            {"iron_block", "铁块", "IRON_BLOCK", "1800.00", "0.65", "9个铁锭合成的方块"},
            {"netherite_ingot", "下界合金锭", "NETHERITE_INGOT", "2000.00", "0.90", "高级稀有材料"},
            {"netherite_block", "下界合金块", "NETHERITE_BLOCK", "18000.00", "0.92", "9个下界合金锭"},
            {"property", "房产", "HOUSE", "50000.00", "0.90", "玩家房产或地块"},
            {"land", "土地", "GRASS_BLOCK", "20000.00", "0.85", "特定区域的土地"},
            {"rare_item", "稀有物品", "DRAGON_EGG", "100000.00", "0.95", "服务器稀有珍贵物品"}
        };
        
        for (String[] collateral : collateralData) {
            String insertQuery = "INSERT OR IGNORE INTO yae_collateral_types " +
                "(collateral_key, collateral_name, material_type, base_value, discount_rate, description) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
            
            databaseService.executeUpdate(insertQuery, collateral);
        }
        
        Logging.debug("Default collateral types inserted");
    }
    
    /**
     * Insert default system settings
     */
    private void insertDefaultSettings() {
        String[][] settingsData = {
            {"MAX_ACTIVE_LOANS_PER_PLAYER", "3", "INTEGER", "每位玩家最多活跃贷款数量"},
            {"LOAN_PROCESSING_FEE_RATE", "0.01", "DECIMAL", "新贷款处理费率"},
            {"OVERDUE_PENALTY_RATE", "0.05", "DECIMAL", "逾期金额罚金率"},
            {"COLLECTION_FEE", "50.00", "DECIMAL", "逾期催收费用"},
            {"GRACE_PERIOD_DAYS", "7", "INTEGER", "宽限期（天数）"},
            {"SUSPENSION_THRESHOLD", "3", "INTEGER", "账户暂停门槛（逾期次数）"},
            {"BLACKLIST_THRESHOLD", "6", "INTEGER", "黑名单门槛（逾期次数）"},
            {"MAX_COLLECTION_ATTEMPTS", "5", "INTEGER", "最大催收次数"},
            {"COLLECTION_INTERVAL_HOURS", "24", "INTEGER", "催收间隔时间（小时）"},
            {"DEFAULT_CREDIT_SCORE", "650", "INTEGER", "新玩家默认信用评分"},
            {"MAX_CREDIT_LOAN_AMOUNT", "100000", "INTEGER", "信用贷款最大金额"},
            {"MORTGAGE_LOAN_MULTIPLIER", "2.0", "DECIMAL", "抵押贷款倍数"}
        };
        
        for (String[] setting : settingsData) {
            String insertQuery = "INSERT OR IGNORE INTO yae_loan_settings " +
                "(setting_key, setting_value, setting_type, description) VALUES (?, ?, ?, ?)";
            
            databaseService.executeUpdate(insertQuery, setting);
        }
        
        Logging.debug("Default system settings inserted");
    }
    
    /**
     * Insert default loan-specific settings
     */
    private void insertDefaultLoanSettings() {
        String[][] loanSettings = {
            {"CREDIT_GRADE_A_MIN", "800", "INTEGER", "A级信用最小分数"},
            {"CREDIT_GRADE_B_MIN", "740", "INTEGER", "B级信用最小分数"},
            {"CREDIT_GRADE_C_MIN", "670", "INTEGER", "C级信用最小分数"},
            {"CREDIT_GRADE_D_MIN", "580", "INTEGER", "D级信用最小分数"},
            {"CREDIT_GRADE_F_MAX", "579", "INTEGER", "F级信用最大分数"},
            {"CREDIT_LOAN_MIN_SCORE", "600", "INTEGER", "信用贷款最小分数"},
            {"MORTGAGE_LOAN_MIN_SCORE", "650", "INTEGER", "抵押贷款最小分数"},
            {"BUSINESS_LOAN_MIN_SCORE", "700", "INTEGER", "商业贷款最小分数"},
            {"EMERGENCY_LOAN_MIN_SCORE", "500", "INTEGER", "应急贷款最小分数"}
        };
        
        for (String[] setting : loanSettings) {
            String insertQuery = "INSERT OR IGNORE INTO yae_loan_settings " +
                "(setting_key, setting_value, setting_type, description) VALUES (?, ?, ?, ?)";
            
            databaseService.executeUpdate(insertQuery, setting);
        }
        
        Logging.debug("Default loan settings inserted");
    }
    
    /**
     * Execute database script from resources
     */
    public boolean executeScript(String scriptPath) {
        try (InputStream is = getClass().getResourceAsStream(scriptPath)) {
            if (is == null) {
                Logging.error("Script not found: " + scriptPath);
                return false;
            }
            
            String content = readStreamContent(is);
            String[] queries = content.split(";");
            
            for (String query : queries) {
                query = query.trim();
                if (query.isEmpty() || query.startsWith("--")) {
                    continue;
                }
                
                try {
                    databaseService.executeUpdate(query);
                } catch (Exception e) {
                    Logging.log(Level.WARNING, "Failed to execute query from script: " + query.substring(0, Math.min(100, query.length())), e);
                }
            }
            
            Logging.info("Script executed successfully: " + scriptPath);
            return true;
            
        } catch (Exception e) {
            Logging.log(Level.SEVERE, "Failed to execute script: " + scriptPath, e);
            return false;
        }
    }
    
    /**
     * Read content from input stream
     */
    private String readStreamContent(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder content = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        
        return content.toString();
    }
}
