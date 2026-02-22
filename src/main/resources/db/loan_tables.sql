-- YAE Loan System Database Schema
-- This file contains the complete database structure for the loan management system

-- =====================================================
-- CORE LOAN TABLES
-- =====================================================

-- Loans table - Main loan records
CREATE TABLE IF NOT EXISTS yae_loans (
    loan_id VARCHAR(50) PRIMARY KEY,
    borrower_uuid VARCHAR(36) NOT NULL,
    lender_uuid VARCHAR(36) NOT NULL,
    loan_type VARCHAR(20) NOT NULL,
    loan_purpose TEXT,
    principal_amount DECIMAL(15,2) NOT NULL,
    current_balance DECIMAL(15,2) NOT NULL,
    interest_rate DECIMAL(5,4) NOT NULL,
    original_interest_rate DECIMAL(5,4) NOT NULL,
    term_months INTEGER NOT NULL,
    start_date TIMESTAMP,
    maturity_date TIMESTAMP NOT NULL,
    next_payment_date TIMESTAMP NOT NULL,
    monthly_payment DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'VAULT',
    repayment_method VARCHAR(20) NOT NULL DEFAULT 'EQUAL_INSTALLMENT',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payments_made INTEGER DEFAULT 0,
    total_payments INTEGER NOT NULL,
    total_interest_paid DECIMAL(15,2) DEFAULT 0,
    total_principal_paid DECIMAL(15,2) DEFAULT 0,
    overdue_payments INTEGER DEFAULT 0,
    overdue_amount DECIMAL(12,2) DEFAULT 0,
    last_overdue_date TIMESTAMP,
    is_in_default BOOLEAN DEFAULT FALSE,
    default_date TIMESTAMP,
    collateral_type VARCHAR(50),
    collateral_value DECIMAL(15,2),
    collateral_discount_rate DECIMAL(5,4),
    collateral_description TEXT,
    borrower_credit_score INTEGER DEFAULT 650,
    borrower_credit_grade VARCHAR(10) DEFAULT 'C',
    application_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_date TIMESTAMP,
    disbursement_date TIMESTAMP,
    approved_by VARCHAR(50),
    disbursed_by VARCHAR(50),
    rejection_reason TEXT,
    notes TEXT,
    auto_pay_enabled BOOLEAN DEFAULT TRUE,
    penalty_waived BOOLEAN DEFAULT FALSE,
    penalty_rate DECIMAL(5,4) DEFAULT 0.05,
    is_refinanced BOOLEAN DEFAULT FALSE,
    original_loan_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Loan payments - Individual payment records
CREATE TABLE IF NOT EXISTS yae_loan_payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id VARCHAR(50) NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_amount DECIMAL(12,2) NOT NULL,
    principal_payment DECIMAL(12,2) NOT NULL,
    interest_payment DECIMAL(12,2) NOT NULL,
    penalty_payment DECIMAL(12,2) DEFAULT 0,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'VAULT',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    transaction_id VARCHAR(50),
    notes TEXT,
    processed_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE RESTRICT,
    INDEX idx_loan_id (loan_id),
    INDEX idx_payment_date (payment_date)
);

-- Repayment schedule - Pre-calculated payment schedule
CREATE TABLE IF NOT EXISTS yae_loan_schedule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id VARCHAR(50) NOT NULL,
    payment_number INTEGER NOT NULL,
    payment_date DATE NOT NULL,
    scheduled_payment DECIMAL(12,2) NOT NULL,
    principal_payment DECIMAL(12,2) NOT NULL,
    interest_payment DECIMAL(12,2) NOT NULL,
    remaining_balance DECIMAL(15,2) NOT NULL,
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    actual_payment_date TIMESTAMP,
    actual_payment_amount DECIMAL(12,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    INDEX idx_loan_id (loan_id),
    INDEX idx_payment_date (payment_date),
    INDEX idx_payment_status (payment_status)
);

-- =====================================================
-- OVERDUE PROCESSING TABLES
-- =====================================================

-- Overdue records - Tracks overdue loan details
CREATE TABLE IF NOT EXISTS yae_overdue_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id VARCHAR(50) NOT NULL,
    borrower_uuid VARCHAR(36) NOT NULL,
    original_balance DECIMAL(15,2) NOT NULL,
    current_balance DECIMAL(15,2) NOT NULL,
    overdue_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_overdue_payments INTEGER DEFAULT 0,
    first_overdue_date TIMESTAMP,
    last_overdue_date TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    collection_attempt_count INTEGER DEFAULT 0,
    last_collection_attempt TIMESTAMP,
    penalty_amount DECIMAL(15,2) DEFAULT 0,
    resolved_date TIMESTAMP,
    resolved_by VARCHAR(50),
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    UNIQUE KEY uk_loan_overdue (loan_id, status),
    INDEX idx_borrower (borrower_uuid),
    INDEX idx_status (status)
);

-- Collection attempts - Tracks collection procedures
CREATE TABLE IF NOT EXISTS yae_collection_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id VARCHAR(50) NOT NULL,
    borrower_uuid VARCHAR(36) NOT NULL,
    attempt_method VARCHAR(50) NOT NULL,
    attempt_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_number INTEGER DEFAULT 1,
    is_success BOOLEAN DEFAULT FALSE,
    notes TEXT,
    response_received BOOLEAN DEFAULT FALSE,
    response_content TEXT,
    next_action_required BOOLEAN DEFAULT TRUE,
    next_action_date TIMESTAMP,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    INDEX idx_loan_id (loan_id),
    INDEX idx_borrower_attempt (borrower_uuid, attempt_date)
);

-- =====================================================
-- CREDIT AND RISK TABLES
-- =====================================================

-- Credit scores - Player credit score history
CREATE TABLE IF NOT EXISTS yae_credit_scores (
    player_uuid VARCHAR(36) PRIMARY KEY,
    credit_score INTEGER NOT NULL CHECK (credit_score BETWEEN 300 AND 850),
    credit_grade VARCHAR(10) NOT NULL,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    calculation_method VARCHAR(50) DEFAULT 'standard',
    factors_snapshot TEXT,
    notes TEXT,
    updated_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Credit penalties - Track automatic and manual credit penalties
CREATE TABLE IF NOT EXISTS yae_credit_penalties (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    penalty_type VARCHAR(50) NOT NULL,
    penalty_amount INTEGER NOT NULL,
    current_score INTEGER NOT NULL,
    resulting_score INTEGER NOT NULL,
    reason TEXT,
    applied_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_date TIMESTAMP,
    applied_by VARCHAR(50),
    loan_id VARCHAR(50),
    FOREIGN KEY (player_uuid) REFERENCES yae_credit_scores(player_uuid) ON DELETE CASCADE,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE SET NULL,
    INDEX idx_player_penalty (player_uuid, applied_date),
    INDEX idx_penalty_expires (expires_date)
);

-- Credit bonuses - Track positive credit score updates
CREATE TABLE IF NOT EXISTS yae_credit_bonuses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    bonus_type VARCHAR(50) NOT NULL,
    bonus_amount INTEGER NOT NULL,
    current_score INTEGER NOT NULL,
    resulting_score INTEGER NOT NULL,
    reason TEXT,
    applied_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_date TIMESTAMP,
    applied_by VARCHAR(50),
    loan_id VARCHAR(50),
    FOREIGN KEY (player_uuid) REFERENCES yae_credit_scores(player_uuid) ON DELETE CASCADE,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE SET NULL,
    INDEX idx_player_bonus (player_uuid, applied_date)
);

-- =====================================================
-- COLLATERAL AND ASSETS TABLES
-- =====================================================

-- Collateral types - Defines accepted collateral types and their valuation rules
CREATE TABLE IF NOT EXISTS yae_collateral_types (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    collateral_key VARCHAR(50) NOT NULL UNIQUE,
    collateral_name VARCHAR(100) NOT NULL,
    material_type VARCHAR(100),
    base_value DECIMAL(15,2) NOT NULL,
    discount_rate DECIMAL(5,4) NOT NULL DEFAULT 0.8,
    valuation_method VARCHAR(50) DEFAULT 'FIXED',
    market_volatility DECIMAL(5,4) DEFAULT 0.1,
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    min_required_quantity INTEGER DEFAULT 1,
    max_accepted_quantity INTEGER DEFAULT 1000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Player collateral tracking
CREATE TABLE IF NOT EXISTS yae_player_collateral (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    loan_id VARCHAR(50),
    collateral_type VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    assessed_value DECIMAL(15,2) NOT NULL,
    acquisition_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    locked_until TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_uuid) REFERENCES yae_credit_scores(player_uuid) ON DELETE CASCADE,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE SET NULL,
    INDEX idx_player_collateral (player_uuid, is_active),
    INDEX idx_loan_collateral (loan_id, is_active)
);

-- =====================================================---
-- TRANSACTION AND LEDGER TABLES
-- =====================================================

-- Loan transaction ledger - All financial changes to loans
CREATE TABLE IF NOT EXISTS yae_loan_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    current_balance DECIMAL(15,2) NOT NULL,
    previous_balance DECIMAL(15,2) NOT NULL,
    description TEXT,
    reference_id VARCHAR(50),
    processed_by VARCHAR(50),
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    INDEX idx_loan_trans (loan_id, transaction_date),
    INDEX idx_trans_type (transaction_type, transaction_date)
);

-- =====================================================
-- RISK ASSESSMENT TABLES
-- =====================================================

-- Risk indicators - Track risk factors for loans
CREATE TABLE IF NOT EXISTS yae_loan_risk_indicators (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id VARCHAR(50) NOT NULL,
    borrower_uuid VARCHAR(36) NOT NULL,
    risk_type VARCHAR(50) NOT NULL,
    risk_score INTEGER DEFAULT 0,
    risk_level VARCHAR(20) DEFAULT 'LOW',
    indicator_reason TEXT,
    detection_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    auto_mitigation_applied BOOLEAN DEFAULT FALSE,
    mitigation_applied VARCHAR(200),
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    INDEX idx_loan_risk (loan_id, risk_type),
    INDEX idx_risk_level (risk_level, detection_date)
);

-- =====================================================
-- ADMINISTRATIVE TABLES
-- =====================================================

-- Loan settings - Admin-configured loan parameters
CREATE TABLE IF NOT EXISTS yae_loan_settings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    setting_type VARCHAR(50) DEFAULT 'STRING',
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Loan blacklist - Borrowers who are blacklisted
CREATE TABLE IF NOT EXISTS yae_loan_blacklist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL UNIQUE,
    blacklist_reason TEXT NOT NULL,
    blacklist_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    blacklisted_by VARCHAR(50),
    expiry_date TIMESTAMP,
    is_permanent BOOLEAN DEFAULT FALSE,
    appeal_allowed BOOLEAN DEFAULT TRUE,
    additional_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_uuid) REFERENCES yae_credit_scores(player_uuid) ON DELETE CASCADE,
    INDEX idx_blacklist_date (blacklist_date),
    INDEX idx_expiry_date (expiry_date)
);

-- Loan audit log - Administrative actions on loans
CREATE TABLE IF NOT EXISTS yae_loan_audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_description TEXT,
    previous_value TEXT,
    new_value TEXT,
    action_by VARCHAR(50) NOT NULL,
    action_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_system_action BOOLEAN DEFAULT FALSE,
    ip_address VARCHAR(45),
    additional_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    INDEX idx_loan_audit (loan_id, action_date),
    INDEX idx_action_type (action_type, action_date)
);

-- =====================================================
-- REFERENCE DATA
-- =====================================================

-- Insert default collateral types
INSERT OR IGNORE INTO yae_loan_settings (setting_key, setting_value, setting_type, description) VALUES
('MAX_ACTIVE_LOANS_PER_PLAYER', '3', 'INTEGER', 'Maximum number of active loans per player'),
('LOAN_PROCESSING_FEE_RATE', '0.01', 'DECIMAL', 'Processing fee rate for new loans'),
('OVERDUE_PENALTY_RATE', '0.05', 'DECIMAL', 'Daily penalty rate for overdue amounts'),
('COLLECTION_FEE', '50.00', 'DECIMAL', 'Fixed collection fee for overdue processing'),
('GRACE_PERIOD_DAYS', '7', 'INTEGER', 'Grace period before penalties apply'),
('SUSPENSION_THRESHOLD', '3', 'INTEGER', 'Number of overdue payments before account suspension'),
('BLACKLIST_THRESHOLD', '6', 'INTEGER', 'Number of overdue payments before blacklist'),
('MAX_COLLECTION_ATTEMPTS', '5', 'INTEGER', 'Maximum collection attempts before escalation'),
('COLLECTION_INTERVAL_HOURS', '24', 'INTEGER', 'Hours between collection attempts'),
('DEFAULT_CREDIT_SCORE', '650', 'INTEGER', 'Default credit score for new players'),
('MAX_CREDIT_LOAN_AMOUNT', '100000', 'INTEGER', 'Maximum amount for credit loans'),
('MORTGAGE_LOAN_MULTIPLIER', '2.0', 'DECIMAL', 'Multiplier for mortgage loans vs credit limit'),
('BUSINESS_LOAN_MULTIPLIER', '1.5', 'DECIMAL', 'Multiplier for business loans vs credit limit');

-- Insert default collateral types
INSERT OR IGNORE INTO yae_collateral_types (collateral_key, collateral_name, material_type, base_value, discount_rate, description) VALUES
('diamond', '钻石', 'DIAMOND', 1000.00, 0.80, 'Minecraft钻石，高价值稀缺资源'),
('diamond_block', '钻块', 'DIAMOND_BLOCK', 9000.00, 0.85, '9个钻石合成的钻块，便于存储'),
('emerald', '绿宝石', 'EMERALD', 800.00, 0.75, 'Minecraft绿宝石，贸易货币'),
('emerald_block', '绿宝石块', 'EMERALD_BLOCK', 7200.00, 0.80, '9个绿宝石合成的方块'),
('gold_ingot', '金锭', 'GOLD_INGOT', 500.00, 0.70, '金锭，贵金属'),
('gold_block', '金块', 'GOLD_BLOCK', 4500.00, 0.75, '9个金锭合成的金块'),
('iron_ingot', '铁锭', 'IRON_INGOT', 200.00, 0.60, '铁锭，基础金属'),
('iron_block', '铁块', 'IRON_BLOCK', 1800.00, 0.65, '9个铁锭合成的铁块'),
('netherite_ingot', '下界合金锭', 'NETHERITE_INGOT', 2000.00, 0.90, '高级材料，高价值'),
('netherite_block', '下界合金块', 'NETHERITE_BLOCK', 18000.00, 0.92, '9个下界合金锭'),
('enchanted_diamond', '附魔钻石', 'ENCHANTED_BOOK', 1500.00, 0.85, '附魔过的钻石装备'),
('enchanted_gold', '附魔黄金', 'ENCHANTED_GOLDEN_APPLE', 2500.00, 0.90, '附魔金苹果'),
('property', '房产', 'HOUSE', 50000.00, 0.90, '玩家房产或地块'),
('land', '土地', 'GRASS_BLOCK', 20000.00, 0.85, '特定区域的土地'),
('rare_item', '稀有物品', 'DRAGON_EGG', 100000.00, 0.95, '服务器稀有物品');

-- Insert default credit grades
INSERT OR IGNORE INTO yae_loan_settings (setting_key, setting_value, setting_type, description) VALUES
('CREDIT_GRADE_A_MIN', '800', 'INTEGER', 'Minimum score for A grade (Excellent)'),
('CREDIT_GRADE_B_MIN', '740', 'INTEGER', 'Minimum score for B grade (Good)'),
('CREDIT_GRADE_C_MIN', '670', 'INTEGER', 'Minimum score for C grade (Average)'),
('CREDIT_GRADE_D_MIN', '580', 'INTEGER', 'Minimum score for D grade (Below Average)'),
('CREDIT_GRADE_F_MAX', '579', 'INTEGER', 'Maximum score for F grade (Poor)'),
('CREDIT_LOAN_MIN_SCORE', '600', 'INTEGER', 'Minimum credit score for credit loans'),
('MORTGAGE_LOAN_MIN_SCORE', '650', 'INTEGER', 'Minimum credit score for mortgage loans'),
('BUSINESS_LOAN_MIN_SCORE', '700', 'INTEGER', 'Minimum credit score for business loans'),
('EMERGENCY_LOAN_MIN_SCORE', '500', 'INTEGER', 'Minimum credit score for emergency loans');

-- Insert credit score calculation weights
INSERT OR IGNORE INTO yae_loan_settings (setting_key, setting_value, setting_type, description) VALUES
('CREDIT_WEIGHT_TRANSACTION_FREQUENCY', '0.25', 'DECIMAL', 'Weight for transaction frequency in credit scoring'),
('CREDIT_WEIGHT_TRANSACTION_AMOUNT', '0.20', 'DECIMAL', 'Weight for transaction amounts in credit scoring'),
('CREDIT_WEIGHT_REPAYMENT_HISTORY', '0.35', 'DECIMAL', 'Weight for repayment history in credit scoring'),
('CREDIT_WEIGHT_ACCOUNT_AGE', '0.10', 'DECIMAL', 'Weight for account age in credit scoring'),
('CREDIT_WEIGHT_CURRENT_BALANCE', '0.10', 'DECIMAL', 'Weight for current balance in credit scoring');

-- =====================================================
-- FINAL INDEXES AND OPTIMIZATIONS
-- =====================================================

-- Create composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_loans_borrower_status ON yae_loans (borrower_uuid, status);
CREATE INDEX IF NOT EXISTS idx_loans_type_status ON yae_loans (loan_type, status);
CREATE INDEX IF NOT EXISTS idx_loans_application_date ON yae_loans (application_date);
CREATE INDEX IF NOT EXISTS idx_loans_overdue_amount ON yae_loans (overdue_amount);
CREATE INDEX IF NOT EXISTS idx_loans_next_payment ON yae_loans (next_payment_date);

CREATE INDEX IF NOT EXISTS idx_payments_loan_date ON yae_loan_payments (loan_id, payment_date DESC);
CREATE INDEX IF NOT EXISTS idx_payments_status ON yae_loan_payments (payment_status);
CREATE INDEX IF NOT EXISTS idx_payments_method ON yae_loan_payments (payment_method);

CREATE INDEX IF NOT EXISTS idx_schedule_loan_status ON yae_loan_schedule (loan_id, payment_status);
CREATE INDEX IF NOT EXISTS idx_schedule_date_asc ON yae_loan_schedule (payment_date ASC);
CREATE INDEX IF NOT EXISTS idx_schedule_date_desc ON yae_loan_schedule (payment_date DESC);

-- Overdue processing indexes
CREATE INDEX IF NOT EXISTS idx_overdue_borrower_active ON yae_overdue_records (borrower_uuid, status);
CREATE INDEX IF NOT EXISTS idx_overdue_collection_date ON yae_overdue_records (last_collection_attempt);
CREATE INDEX IF NOT EXISTS idx_overdue_status_date ON yae_overdue_records (status, created_at);

-- Credit and risk indexes
CREATE INDEX IF NOT EXISTS idx_credit_score_range ON yae_credit_scores (credit_score);
CREATE INDEX IF NOT EXISTS idx_credit_grade ON yae_credit_scores (credit_grade);
CREATE INDEX IF NOT EXISTS idx_credit_updated ON yae_credit_scores (last_updated);

-- ================= VIEWS FOR COMMON QUERIES =================

-- Active loans summary for players
CREATE OR REPLACE VIEW yae_active_loans_summary AS
SELECT 
    l.borrower_uuid,
    COUNT(*) as active_loan_count,
    SUM(current_balance) as total_current_balance,
    SUM(overdue_amount) as total_overdue_amount,
    AVG(interest_rate) as avg_interest_rate,
    MAX(next_payment_date) as next_payment_due,
    COUNT(CASE WHEN status = 'OVERDUE' THEN 1 END) as overdue_count
FROM yae_loans l
WHERE l.status IN ('PENDING', 'APPROVED', 'ACTIVE', 'OVERDUE')
GROUP BY l.borrower_uuid;

-- Loan statistics for risk analysis
CREATE OR REPLACE VIEW yae_loan_statistics AS
SELECT 
    loan_type,
    status,
    COUNT(*) as count,
    AVG(principal_amount) as avg_principal,
    AVG(interest_rate) as avg_interest_rate,
    AVG(term_months) as avg_term_months,
    SUM(principal_amount) as total_principal,
    SUM(current_balance) as total_current_balance,
    SUM(overdue_amount) as total_overdue
FROM yae_loans
GROUP BY loan_type, status;

-- Credit score distribution
CREATE OR REPLACE VIEW yae_credit_score_distribution AS
SELECT 
    credit_grade,
    COUNT(*) as borrower_count,
    MIN(credit_score) as min_score,
    MAX(credit_score) as max_score,
    AVG(credit_score) as avg_score
FROM yae_credit_scores 
GROUP BY credit_grade
ORDER BY avg_score DESC;

-- Monthly loan performance metrics
CREATE OR REPLACE VIEW yae_monthly_loan_metrics AS
SELECT 
    DATE_FORMAT(payment_date, '%Y-%m') as month,
    COUNT(*) as total_payments,
    SUM(payment_amount) as total_amount_paid,
    SUM(penalty_payment) as total_penalties,
    AVG(payment_amount) as avg_payment,
    SUM(CASE WHEN payment_status = 'FAILED' THEN 1 ELSE 0 END) as failed_payments
FROM yae_loan_payments 
GROUP BY DATE_FORMAT(payment_date, '%Y-%m')
ORDER BY month DESC;

-- Future payment obligations (next 30 days)
CREATE OR REPLACE VIEW yae_upcoming_payments AS
SELECT 
    s.loan_id,
    l.borrower_uuid,
    s.payment_number,
    s.payment_date,
    s.scheduled_payment,
    s.principal_payment,
    s.interest_payment,
    s.remaining_balance,
    l.loan_type,
    l.interest_rate
FROM yae_loan_schedule s
JOIN yae_loans l ON s.loan_id = l.loan_id
WHERE s.payment_status = 'PENDING'
  AND s.payment_date <= DATE_ADD(NOW(), INTERVAL 30 DAY)
ORDER BY s.payment_date ASC;

-- Print success message
SELECT 'YAE Loan System Tables Created Successfully!' as Message;
