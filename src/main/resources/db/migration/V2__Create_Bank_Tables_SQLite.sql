-- 银行相关表结构 (SQLite版本)
-- 版本2：创建银行账户、定期存款、贷款等表

-- 银行账户表
CREATE TABLE IF NOT EXISTS yae_bank_accounts (
    account_id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    owner_type TEXT NOT NULL CHECK (owner_type IN ('PLAYER', 'ORGANIZATION')),
    account_number TEXT UNIQUE NOT NULL,
    account_type TEXT NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS', 'FIXED_DEPOSIT', 'LOAN')),
    account_name TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED', 'SUSPENDED')),
    current_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    available_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    frozen_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    credit_score INTEGER DEFAULT 650 CHECK (credit_score >= 300 AND credit_score <= 850),
    interest_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0100,
    last_interest_calculation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 定期存款表
CREATE TABLE IF NOT EXISTS yae_fixed_deposits (
    deposit_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    deposit_number TEXT UNIQUE NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MATURED', 'WITHDRAWN', 'CLOSED')),
    principal DECIMAL(15,2) NOT NULL,
    current_amount DECIMAL(15,2) NOT NULL,
    term TEXT NOT NULL,
    interest_rate DECIMAL(5,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    maturity_date TIMESTAMP NOT NULL,
    last_interest_calculation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE
);

-- 贷款表
CREATE TABLE IF NOT EXISTS yae_loans (
    loan_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    loan_number TEXT UNIQUE NOT NULL,
    loan_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAID', 'DEFAULTED', 'WRITTEN_OFF')),
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
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE
);

-- 贷款还款记录表
CREATE TABLE IF NOT EXISTS yae_loan_payments (
    payment_id TEXT PRIMARY KEY,
    loan_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    payment_type TEXT NOT NULL CHECK (payment_type IN ('REGULAR', 'EXTRA', 'LATE_FEE')),
    amount DECIMAL(12,2) NOT NULL,
    principal_amount DECIMAL(12,2) NOT NULL,
    interest_amount DECIMAL(12,2) NOT NULL,
    late_fee_amount DECIMAL(12,2) DEFAULT 0.00,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date DATE NOT NULL,
    
    -- 外键约束
    FOREIGN KEY (loan_id) REFERENCES yae_loans(loan_id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE
);

-- 银行账户交易记录表
CREATE TABLE IF NOT EXISTS yae_bank_transactions (
    transaction_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    transaction_type TEXT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    description TEXT,
    reference_id TEXT,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_by TEXT,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE
);

-- 银行账户冻结记录表
CREATE TABLE IF NOT EXISTS yae_account_freezes (
    freeze_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    freeze_type TEXT NOT NULL CHECK (freeze_type IN ('MANUAL', 'SYSTEM', 'COURT_ORDER')),
    reason TEXT NOT NULL,
    amount DECIMAL(15,2),
    frozen_by TEXT,
    frozen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unfreeze_reason TEXT,
    unfrozen_by TEXT,
    unfrozen_at TIMESTAMP NULL,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE
);

-- 银行账户利率历史表
CREATE TABLE IF NOT EXISTS yae_account_interest_history (
    history_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    old_interest_rate DECIMAL(5,4) NOT NULL,
    new_interest_rate DECIMAL(5,4) NOT NULL,
    change_reason TEXT,
    changed_by TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE
);

-- 银行账户余额历史表（用于审计和统计）
CREATE TABLE IF NOT EXISTS yae_account_balance_history (
    history_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    balance_type TEXT NOT NULL CHECK (balance_type IN ('CURRENT', 'AVAILABLE', 'FROZEN')),
    balance_amount DECIMAL(15,2) NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE
);

-- 银行账户授权用户表（用于组织账户的权限管理）
CREATE TABLE IF NOT EXISTS yae_account_authorized_users (
    authorization_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    permission_level TEXT NOT NULL CHECK (permission_level IN ('VIEW', 'DEPOSIT', 'WITHDRAW', 'ADMIN')),
    granted_by TEXT NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 外键约束
    FOREIGN KEY (account_id) REFERENCES yae_bank_accounts(account_id) ON DELETE CASCADE,
    
    -- 唯一约束
    UNIQUE (account_id, user_id)
);

-- 触发器：更新账户更新时间戳
CREATE TRIGGER IF NOT EXISTS trg_update_account_timestamp
    AFTER UPDATE ON yae_bank_accounts
    FOR EACH ROW
    WHEN OLD.updated_at != CURRENT_TIMESTAMP
BEGIN
    UPDATE yae_bank_accounts 
    SET updated_at = CURRENT_TIMESTAMP 
    WHERE account_id = NEW.account_id;
END;

-- 触发器：记录账户余额历史 (SQLite简化版)
CREATE TRIGGER IF NOT EXISTS trg_record_balance_history
    AFTER UPDATE ON yae_bank_accounts
    FOR EACH ROW
BEGIN
    -- 记录当前余额变化
    INSERT INTO yae_account_balance_history (history_id, account_id, balance_type, balance_amount)
    SELECT 
        lower(hex(randomblob(16))),
        NEW.account_id,
        'CURRENT',
        NEW.current_balance
    WHERE OLD.current_balance != NEW.current_balance;
    
    -- 记录可用余额变化
    INSERT INTO yae_account_balance_history (history_id, account_id, balance_type, balance_amount)
    SELECT 
        lower(hex(randomblob(16))),
        NEW.account_id,
        'AVAILABLE',
        NEW.available_balance
    WHERE OLD.available_balance != NEW.available_balance;
    
    -- 记录冻结余额变化
    INSERT INTO yae_account_balance_history (history_id, account_id, balance_type, balance_amount)
    SELECT 
        lower(hex(randomblob(16))),
        NEW.account_id,
        'FROZEN',
        NEW.frozen_amount
    WHERE OLD.frozen_amount != NEW.frozen_amount;
END;

-- 视图：账户概览（包含活期余额和定期存款总额）
CREATE VIEW IF NOT EXISTS yae_account_overview AS
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
GROUP BY a.account_id;

-- 视图：到期定期存款提醒
CREATE VIEW IF NOT EXISTS yae_matured_deposits_view AS
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
        WHEN date(fd.maturity_date) <= date('now') THEN 'MATURED'
        WHEN date(fd.maturity_date) <= date('now', '+7 days') THEN 'DUE_SOON'
        ELSE 'ACTIVE'
    END AS status_warning
FROM yae_fixed_deposits fd
JOIN yae_bank_accounts a ON fd.account_id = a.account_id
WHERE fd.status = 'ACTIVE';

-- 视图：贷款风险分析
CREATE VIEW IF NOT EXISTS yae_loan_risk_analysis AS
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
        WHEN l.last_payment_date IS NULL AND l.first_payment_date < date('now') THEN 'HIGH_RISK'
        WHEN date(l.last_payment_date) < date('now', '-30 days') THEN 'HIGH_RISK'
        WHEN date(l.last_payment_date) < date('now', '-15 days') THEN 'MEDIUM_RISK'
        ELSE 'LOW_RISK'
    END AS risk_level
FROM yae_loans l
JOIN yae_bank_accounts a ON l.account_id = a.account_id;

-- 索引优化统计查询
CREATE INDEX IF NOT EXISTS idx_owner_accounts ON yae_bank_accounts(owner_id, owner_type);
CREATE INDEX IF NOT EXISTS idx_account_number ON yae_bank_accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_account_status ON yae_bank_accounts(status);
CREATE INDEX IF NOT EXISTS idx_account_type ON yae_bank_accounts(account_type);
CREATE INDEX IF NOT EXISTS idx_account_deposits ON yae_fixed_deposits(account_id);
CREATE INDEX IF NOT EXISTS idx_deposit_status ON yae_fixed_deposits(status);
CREATE INDEX IF NOT EXISTS idx_deposit_number ON yae_fixed_deposits(deposit_number);
CREATE INDEX IF NOT EXISTS idx_maturity_date ON yae_fixed_deposits(maturity_date);
CREATE INDEX IF NOT EXISTS idx_loan_status ON yae_loans(status);
CREATE INDEX IF NOT EXISTS idx_loan_number ON yae_loans(loan_number);
CREATE INDEX IF NOT EXISTS idx_loan_status_date ON yae_loans(account_id, status, maturity_date);
CREATE INDEX IF NOT EXISTS idx_account_transactions ON yae_bank_transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transaction_type ON yae_bank_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_transaction_date ON yae_bank_transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_reference_id ON yae_bank_transactions(reference_id);
CREATE INDEX IF NOT EXISTS idx_account_freezes ON yae_account_freezes(account_id);
CREATE INDEX IF NOT EXISTS idx_freeze_type ON yae_account_freezes(freeze_type);
CREATE INDEX IF NOT EXISTS idx_frozen_at ON yae_account_freezes(frozen_at);
CREATE INDEX IF NOT EXISTS idx_account_interest_history ON yae_account_interest_history(account_id);
CREATE INDEX IF NOT EXISTS idx_changed_at ON yae_account_interest_history(changed_at);
CREATE INDEX IF NOT EXISTS idx_account_balance_history ON yae_account_balance_history(account_id);
CREATE INDEX IF NOT EXISTS idx_balance_type ON yae_account_balance_history(balance_type);
CREATE INDEX IF NOT EXISTS idx_recorded_at ON yae_account_balance_history(recorded_at);
CREATE INDEX IF NOT EXISTS idx_account_users ON yae_account_authorized_users(account_id);
CREATE INDEX IF NOT EXISTS idx_user_accounts ON yae_account_authorized_users(user_id);
CREATE INDEX IF NOT EXISTS idx_permission_level ON yae_account_authorized_users(permission_level);
CREATE INDEX IF NOT EXISTS idx_loan_payments ON yae_loan_payments(loan_id);
CREATE INDEX IF NOT EXISTS idx_payment_date ON yae_loan_payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_due_date ON yae_loan_payments(due_date);

-- 数据完整性检查
-- 确保账户号码唯一性
CREATE UNIQUE INDEX IF NOT EXISTS uk_account_number ON yae_bank_accounts(account_number);

-- 确保存款号码唯一性  
CREATE UNIQUE INDEX IF NOT EXISTS uk_deposit_number ON yae_fixed_deposits(deposit_number);

-- 确保贷款号码唯一性
CREATE UNIQUE INDEX IF NOT EXISTS uk_loan_number ON yae_loans(loan_number);

-- 确保授权记录唯一性（一个用户不能多次授权同一账户）
CREATE UNIQUE INDEX IF NOT EXISTS uk_account_user ON yae_account_authorized_users(account_id, user_id);
