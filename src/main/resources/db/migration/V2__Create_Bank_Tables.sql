-- 银行相关表结构
-- 版本2：创建银行账户、定期存款、贷款等表

-- 银行账户表
CREATE TABLE IF NOT EXISTS yae_bank_accounts (
    account_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL CHECK (owner_type IN ('PLAYER', 'ORGANIZATION')),
    account_number VARCHAR(50) UNIQUE NOT NULL,
    account_type VARCHAR(30) NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS', 'FIXED_DEPOSIT', 'LOAN')),
    account_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED', 'SUSPENDED')),
    current_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    available_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    frozen_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    credit_score INTEGER DEFAULT 650 CHECK (credit_score >= 300 AND credit_score <= 850),
    interest_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0100,
    last_interest_calculation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_owner_accounts (owner_id, owner_type),
    INDEX idx_account_number (account_number),
    INDEX idx_account_status (status),
    INDEX idx_account_type (account_type)
);

-- 定期存款表
CREATE TABLE IF NOT EXISTS yae_fixed_deposits (
    deposit_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    deposit_number VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MATURED', 'WITHDRAWN', 'CLOSED')),
    principal DECIMAL(15,2) NOT NULL,
    current_amount DECIMAL(15,2) NOT NULL,
    term VARCHAR(30) NOT NULL,
    interest_rate DECIMAL(5,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    maturity_date TIMESTAMP NOT NULL,
    last_interest_calculation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_account_deposits (account_id),
    INDEX idx_deposit_status (status),
    INDEX idx_deposit_number (deposit_number),
    INDEX idx_maturity_date (maturity_date)
);

-- 贷款表
CREATE TABLE IF NOT EXISTS yae_loans (
    loan_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    loan_number VARCHAR(50) UNIQUE NOT NULL,
    loan_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAID', 'DEFAULTED', 'WRITTEN_OFF')),
    principal DECIMAL(15,2) NOT NULL,
    current_balance DECIMAL(15,2) NOT NULL,
    interest_rate DECIMAL(5,4) NOT NULL,
    term_months INTEGER NOT NULL,
    monthly_payment DECIMAL(12,2) NOT NULL,
    total_interest DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    late_fee_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0500,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    first_payment_date DATE NOT NULL,
    last_payment_date DATE NULL,
    maturity_date DATE NOT NULL,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_account_loans (account_id),
    INDEX idx_loan_status (status),
    INDEX idx_loan_number (loan_number),
    INDEX idx_maturity_date_loans (maturity_date)
);

-- 贷款还款记录表
CREATE TABLE IF NOT EXISTS yae_loan_payments (
    payment_id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    account_id UUID NOT NULL,
    payment_type VARCHAR(20) NOT NULL CHECK (payment_type IN ('REGULAR', 'EXTRA', 'LATE_FEE')),
    amount DECIMAL(12,2) NOT NULL,
    principal_amount DECIMAL(12,2) NOT NULL,
    interest_amount DECIMAL(12,2) NOT NULL,
    late_fee_amount DECIMAL(12,2) DEFAULT 0.00,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date DATE NOT NULL,
    
    -- 外键约束
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_loan_payments (loan_id),
    INDEX idx_payment_date (payment_date),
    INDEX idx_due_date (due_date)
);

-- 银行账户交易记录表
CREATE TABLE IF NOT EXISTS yae_bank_transactions (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    description TEXT,
    reference_id VARCHAR(100),
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_by UUID,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_account_transactions (account_id),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_transaction_date (transaction_date),
    INDEX idx_reference_id (reference_id)
);

-- 银行账户冻结记录表
CREATE TABLE IF NOT EXISTS yae_account_freezes (
    freeze_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    freeze_type VARCHAR(30) NOT NULL CHECK (freeze_type IN ('MANUAL', 'SYSTEM', 'COURT_ORDER')),
    reason TEXT NOT NULL,
    amount DECIMAL(15,2),
    frozen_by UUID,
    frozen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unfreeze_reason TEXT,
    unfrozen_by UUID,
    unfrozen_at TIMESTAMP NULL,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_account_freezes (account_id),
    INDEX idx_freeze_type (freeze_type),
    INDEX idx_frozen_at (frozen_at)
);

-- 银行账户利率历史表
CREATE TABLE IF NOT EXISTS yae_account_interest_history (
    history_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    old_interest_rate DECIMAL(5,4) NOT NULL,
    new_interest_rate DECIMAL(5,4) NOT NULL,
    change_reason VARCHAR(200),
    changed_by UUID,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_account_interest_history (account_id),
    INDEX idx_changed_at (changed_at)
);

-- 银行账户余额历史表（用于审计和统计）
CREATE TABLE IF NOT EXISTS yae_account_balance_history (
    history_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    balance_type VARCHAR(20) NOT NULL CHECK (balance_type IN ('CURRENT', 'AVAILABLE', 'FROZEN')),
    balance_amount DECIMAL(15,2) NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_account_balance_history (account_id),
    INDEX idx_balance_type (balance_type),
    INDEX idx_recorded_at (recorded_at)
);

-- 银行账户授权用户表（用于组织账户的权限管理）
CREATE TABLE IF NOT EXISTS yae_account_authorized_users (
    authorization_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    user_id UUID NOT NULL,
    permission_level VARCHAR(30) NOT NULL CHECK (permission_level IN ('VIEW', 'DEPOSIT', 'WITHDRAW', 'ADMIN')),
    granted_by UUID NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_account_users (account_id),
    INDEX idx_user_accounts (user_id),
    INDEX idx_permission_level (permission_level),
    UNIQUE KEY unique_account_user (account_id, user_id)
);

-- 视图：账户概览（包含活期余额和定期存款总额）
CREATE OR REPLACE VIEW yae_account_overview AS
SELECT 
    a.account_id,
    a.owner_id,
    a.owner_type,
    a.account_number,
    a.account_type,
    a.account_name,
    a.status,
    a.current_balance,
    a.available_balance,
    a.frozen_amount,
    COALESCE(SUM(fd.current_amount), 0) AS total_fixed_deposit_amount,
    (a.current_balance + COALESCE(SUM(fd.current_amount), 0)) AS total_balance,
    a.credit_score,
    a.interest_rate,
    a.created_at,
    a.updated_at
FROM yae_bank_accounts a
LEFT JOIN yae_fixed_deposits fd ON a.account_id = fd.account_id AND fd.status = 'ACTIVE'
GROUP BY a.account_id, a.owner_id, a.owner_type, a.account_number, a.account_type, 
         a.account_name, a.status, a.current_balance, a.available_balance, a.frozen_amount, 
         a.credit_score, a.interest_rate, a.created_at, a.updated_at;

-- 视图：到期定期存款提醒
CREATE OR REPLACE VIEW yae_matured_deposits_view AS
SELECT 
    fd.deposit_id,
    fd.account_id,
    fd.deposit_number,
    fd.principal,
    fd.current_amount,
    fd.term,
    fd.interest_rate,
    fd.maturity_date,
    a.owner_id,
    a.owner_type,
    a.account_number,
    CASE 
        WHEN fd.maturity_date <= CURRENT_DATE THEN 'MATURED'
        WHEN fd.maturity_date <= DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY) THEN 'DUE_SOON'
        ELSE 'ACTIVE'
    END AS status_warning
FROM yae_fixed_deposits fd
JOIN yae_bank_accounts a ON fd.account_id = a.account_id
WHERE fd.status = 'ACTIVE';

-- 视图：贷款风险分析
CREATE OR REPLACE VIEW yae_loan_risk_analysis AS
SELECT 
    l.loan_id,
    l.account_id,
    l.loan_number,
    l.loan_type,
    l.status,
    l.principal,
    l.current_balance,
    l.interest_rate,
    l.term_months,
    l.monthly_payment,
    l.last_payment_date,
    l.maturity_date,
    a.owner_id,
    a.owner_type,
    a.credit_score,
    CASE 
        WHEN l.status IN ('DEFAULTED', 'WRITTEN_OFF') THEN 'HIGH_RISK'
        WHEN l.last_payment_date IS NULL AND l.first_payment_date < CURRENT_DATE THEN 'HIGH_RISK'
        WHEN l.last_payment_date < DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY) THEN 'HIGH_RISK'
        WHEN l.last_payment_date < DATE_SUB(CURRENT_DATE, INTERVAL 15 DAY) THEN 'MEDIUM_RISK'
        ELSE 'LOW_RISK'
    END AS risk_level
FROM yae_loans l
JOIN yae_bank_accounts a ON l.account_id = a.account_id;

-- 存储过程：创建新银行账户
CREATE OR REPLACE PROCEDURE sp_create_bank_account(
    IN p_owner_id UUID,
    IN p_owner_type VARCHAR(20),
    IN p_account_type VARCHAR(30),
    IN p_account_name VARCHAR(100),
    OUT p_account_id UUID,
    OUT p_account_number VARCHAR(50),
    OUT p_error_message VARCHAR(500)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_error_message = 'Failed to create bank account';
        SET p_account_id = NULL;
        SET p_account_number = NULL;
    END;
    
    START TRANSACTION;
    
    -- 检查账户数量限制
    IF (SELECT COUNT(*) FROM yae_bank_accounts 
        WHERE owner_id = p_owner_id AND owner_type = p_owner_type 
        AND status = 'ACTIVE') >= 3 THEN
        SET p_error_message = 'Maximum number of active accounts reached (3)';
        SET p_account_id = NULL;
        SET p_account_number = NULL;
        ROLLBACK;
    ELSE
        -- 生成账户号码
        SET p_account_id = UUID();
        SET p_account_number = CONCAT(
            CASE p_owner_type WHEN 'PLAYER' THEN 'P' ELSE 'O' END,
            CASE p_account_type 
                WHEN 'CHECKING' THEN '1'
                WHEN 'SAVINGS' THEN '2'
                WHEN 'FIXED_DEPOSIT' THEN '3'
                WHEN 'LOAN' THEN '4'
                ELSE '0'
            END,
            UNIX_TIMESTAMP(),
            FLOOR(RAND() * 1000)
        );
        
        -- 创建账户
        INSERT INTO yae_bank_accounts (
            account_id, owner_id, owner_type, account_number, account_type, 
            account_name, created_at, updated_at
        ) VALUES (
            p_account_id, p_owner_id, p_owner_type, p_account_number, 
            p_account_type, p_account_name, NOW(), NOW()
        );
        
        SET p_error_message = NULL;
        COMMIT;
    END IF;
END;

-- 触发器：更新账户更新时间戳
CREATE TRIGGER trg_update_account_timestamp
    BEFORE UPDATE ON yae_bank_accounts
    FOR EACH ROW
    SET NEW.updated_at = CURRENT_TIMESTAMP;

-- 触发器：记录账户余额历史
CREATE TRIGGER trg_record_balance_history
    AFTER UPDATE ON yae_bank_accounts
    FOR EACH ROW
    BEGIN
        IF OLD.current_balance != NEW.current_balance THEN
            INSERT INTO yae_account_balance_history (account_id, balance_type, balance_amount)
            VALUES (NEW.account_id, 'CURRENT', NEW.current_balance);
        END IF;
        
        IF OLD.available_balance != NEW.available_balance THEN
            INSERT INTO yae_account_balance_history (account_id, balance_type, balance_amount)
            VALUES (NEW.account_id, 'AVAILABLE', NEW.available_balance);
        END IF;
        
        IF OLD.frozen_amount != NEW.frozen_amount THEN
            INSERT INTO yae_account_balance_history (account_id, balance_type, balance_amount)
            VALUES (NEW.account_id, 'FROZEN', NEW.frozen_amount);
        END IF;
    END;

-- 索引优化统计查询
CREATE INDEX idx_account_balance_combined ON yae_bank_accounts(owner_id, owner_type, status);
CREATE INDEX idx_deposit_maturity_active ON yae_fixed_deposits(account_id, maturity_date, status);
CREATE INDEX idx_loan_status_date ON yae_loans(account_id, status, maturity_date);
CREATE INDEX idx_transaction_composite ON yae_bank_transactions(account_id, transaction_date, transaction_type);

-- 数据完整性检查
-- 确保账户号码唯一性
ALTER TABLE yae_bank_accounts ADD CONSTRAINT uk_account_number UNIQUE (account_number);

-- 确保存款号码唯一性  
ALTER TABLE yae_fixed_deposits ADD CONSTRAINT uk_deposit_number UNIQUE (deposit_number);

-- 确保贷款号码唯一性
ALTER TABLE yae_loans ADD CONSTRAINT uk_loan_number UNIQUE (loan_number);

-- 确保授权记录唯一性（一个用户不能多次授权同一账户）
ALTER TABLE yae_account_authorized_users ADD CONSTRAINT uk_account_user UNIQUE (account_id, user_id);
