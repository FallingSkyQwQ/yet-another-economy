# YetAnotherEconomy - 安装配置指南

## 📋 安装前准备

### 系统要求
- **Java**: 版本 17 或更高
- **Minecraft**: Paper/Bukkit 1.20.4+ 服务端
- **内存**: 建议 4GB+ RAM 用于中等规模服务器
- **存储**: 至少 100MB 可用空间用于插件和数据库

### 必备插件
- **Vault** (必需): 经济服务API标准支持  
- **LuckPerms** (推荐): 权限管理系统
- **WorldEdit** (可选): 区域保护和建筑管理

---

## 🚀 快速安装

### 步骤 1: 获取插件
```bash
# 方法 A: 从 GitHub 发布下载
wget https://github.com/FallingSkyQwQ/yet-another-economy/releases/download/v1.0.0/yet-another-economy-1.0.0.jar

# 方法 B: 自行构建
## 需要 Java 17+ 和 Maven 3.6+
git clone https://github.com/FallingSkyQwQ/yet-another-economy.git
cd yet-another-economy
mvn clean package
# 构建后文件位于 target/yet-another-economy-1.0.0.jar
```

### 步骤 2: 安装插件
1. **停止服务器** - 确保Minecraft服务器进程已关闭
2. **复制JAR文件** - 将 `yet-another-economy-1.0.0.jar` 复制到 `plugins/` 目录
3. **启动服务器** - 启动Minecraft服务器，自动生成配置文件
4. **验证安装** - 检查控制台输出确认插件加载成功

### 步骤 3: 基础配置
```yaml
# plugins/YetAnotherEconomy/config.yml - 基础配置
plugin:
  name: "YetAnotherEconomy"
  version: "1.0.0"
  debug-mode: false
  auto-save-interval: 300  # 5分钟自动保存

# 数据库配置
database:
  type: "sqlite"  # 适合小规模服务器, 大规模推荐 mysql/mariadb
  
  sqlite:
    file: "plugins/YetAnotherEconomy/database.db"
    
# 经济基本设置  
economy:
  currency:
    name: "元"
    symbol: "¥"
    decimals: 2
    max-balance: 999999999.99
    min-balance: -999999999.99
```

---

## 📊 数据库配置

### SQLite 配置（推荐新手）
```yaml
database:
  type: "sqlite"
  sqlite:
    file: "plugins/YetAnotherEconomy/database.db"
    auto-backup: true
    backup-interval: 86400  # 24小时备份一次
```

**优点**: 无需额外配置，零依赖，适合新手和小型服务器  
**适用场景**: 少于500用户的小型服务器

### MySQL 配置（推荐生产环境）
```yaml
database:
  type: "mysql"
  mysql:
    host: "localhost"        # 数据库服务器地址
    port: 3306               # 端口
    database: "minecraft"    # 数据库名
    username: "yae_user"     # 专用数据库用户
    password: "your-secure-password"
    
    # 连接池设置
    pool-size: 10           # 连接池大小
    max-wait: 5000          # 最大等待时间(ms)
    connection-timeout: 30000
    idle-timeout: 3600000   # 1小时
    
    # 高级设置
    use-ssl: false          # 如在同一服务器
    character-encoding: "utf8mb4"
    connection-properties:
      "useUnicode": "true"
      "characterEncoding": "utf8mb4"
```

**创建 MySQL 数据库用户**:
```sql
-- 创建专用数据库和用户
CREATE DATABASE minecraft CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'yae_user'@'localhost' IDENTIFIED BY 'your-secure-password';
GRANT ALL PRIVILEGES ON minecraft.* TO 'yae_user'@'localhost';
FLUSH PRIVILEGES;
```

### MariaDB 配置
```yaml
database:
  type: "mariadb"
  mariadb:
    host: "localhost"
    port: 3307
    database: "yae_economy"
    username: "yae_admin"
    password: "strong-password"
    
    # MariaDB 最优设置
    use-mariadb-specific-features: true
    pool-size: 15
    connection-timeout: 25000
```

---

## 🔧 高级配置

### 经济规则配置
```yaml
economy:
  currency:
    name: "元"                    # 货币名称
    symbol: "¥"                  # 货币符号
    decimals: 2                  # 小数位精度
    max-balance: 999999999.99    # 最大余额限制
    min-balance: -999999999.99   # 最小允许负余额
    negative-balance: true       # 是否允许透支
  
  # 自动清理
  auto-cleanup:
    enabled: true
    cleanup-interval: 172800     # 48小时
    min-balance-threshold: 0.01  # 小于此数值清理
    min-inactive-days: 14        # 最小不活跃天数
  
  # 税务设置
  taxation:
    enabled: false
    transfer-tax: 0.01          # 转账税率 (1%)
    min-tax-amount: 100          # 最小征税金额
    tax-recipient: "SERVER"      # 税收接收方
```

### 信用评分配置
```yaml
credit:
  algorithm:
    base-score: 600
    max-score: 999
    min-score: 300
    
    # 评分权重设置
    weights:
      income: 0.30      # 收入因素 (30%)
      stability: 0.40   # 稳定性因素 (40%)
      debt: 0.30        # 债务因素 (30%)
    
    # 评分有效期设置
    score-expiry-days: 30
    auto-recalculation-interval: 864000  # 10天重新计算一次
    
  # 信用等级和最大贷款额
  grades:
    SSS: { min: 850, max: 999, color: "&6", max-loan: 1000000 }
    SS:  { min: 800, max: 849, color: "&e", max-loan: 800000 }
    S:   { min: 750, max: 799, color: "&a", max-loan: 600000 }
    A+:  { min: 700, max: 749, color: "&b", max-loan: 400000 }
    A:   { min: 650, max: 699, color: "&9", max-loan: 250000 }
    B+:  { min: 600, max: 649, color: "&5", max-loan: 150000 }
    B:   { min: 550, max: 599, color: "&c", max-loan: 80000 }
    C:   { min: 300, max: 549, color: "&4", max-loan: 30000 }
```

### 贷款配置
```yaml
loan:
  # 基础利率设置
  base-rate: 5.0              # 基础年利率 (%)
  max-rate: 15.0              # 最高年利率
  min-rate: 2.0               # 最低年利率
  
  # 贷款期限
  min-term-months: 1          # 最短1个月
  max-term-months: 36         # 最长36个月
  
  # 放款规则
  disbursement:
    auto-disbursement: false   # 是否自动放款
    approval-timeout: 172800   # 48小时过期
    require-collateral: true   # 是否需要抵押
    collateral-coverage-ratio: 0.5  # 抵押覆盖比例
  
  # 还款规则
  repayment:
    grace-period-days: 3       # 宽限期天数
    late-payment-penalty: 0.05  # 逾期罚金比例
    early-repayment-penalty: 0.0  # 提前还款是否收取手续费
    
  # 风控设置
  risk-control:
    max-concurrent-loans: 5    # 同一用户最大并发贷款数
    max-risk-score: 0.7        # 最大风险评分
    enable-collection: true    # 是否启用催收系统
    collection-delay-days: 7   # 催收开始延迟天数
```

### 商店配置
```yaml
shop:
  # 商品分类
  categories:
    - name: "建筑材料"
      icon: "STONE"
      description: "各种建筑材料"
      
    - name: "武器装备"
      icon: "DIAMOND_SWORD"
      description: "战斗装备"
      
    - name: "食物补给"
      icon: "COOKED_BEEF"
      description: "食物和消耗品"
  
  # 购买确认设置
  confirmation:
    enabled: true              # 启用确认机制
    confirmation-time: 10      # 确认时间(秒)
    allow-reversal: true       # 是否允许撤销
    reversal-time-limit: 10    # 撤销时间限制(秒)
  
  # 定价和库存
  pricing:
    enable-dynamic-pricing: false   # 动态定价
    price-fluctuation-percent: 5    # 价格浮动比例
    restock-interval: 3600          # 补货间隔(秒)
    max-price-multiplier: 2.0       # 最高价格倍数
    
  # 限制设置
  limits:
    max-quantity-per-purchase: 64   # 每次最多购买数量
    daily-purchase-limit: 1000      # 每日购买限额
    enable-purchase-logging: true   # 记录购买历史
```

---

## 🔐 权限配置

### 权限组推荐设置

#### 新玩家组 (NewPlayer)
```yaml
# LuckPerms 权限节点
yet_another_economy.user.balance:
  description: "查看自己余额"
yet_another_economy.user.pay:
  description: "转账给其他玩家"
yet_another_economy.user.credit.score:
  description: "查看自己信用评分"
```

#### 普通玩家组 (Player)
```yaml
# 继承 NewPlayer 权限, 并添加以下
yet_another_economy.user.shopping.*:
yet_another_economy.user.loan.apply:
yet_another_economy.user.loan.repay:
yet_another_economy.user.loan.list:
```

#### VIP组 (VIP)
```yaml
# 继承 Player 权限, 并添加以下
yet_another_economy.user.transfer.limit.50000:
yet_another_economy.user.loan.max.100000:
yet_another_economy.user.credit.premium:
```

#### 管理员组 (Admin)
```yaml
yet_another_economy.admin.*:
yet_another_economy.economy.balance.others:
yet_another_economy.economy.set:*:
yet_another_economy.credit.view.others:
yet_another_economy.loan.view.others:
yet_another_economy.loan.approve:*:
yet_another_economy.shop.manage:*:
yet_another_economy.reload:
```

### 权限配置示例
```bash
# LuckPerms 命令创建权限组
lp creategroup yae_newplayer default
lp creategroup yae_player
lp creategroup yae_vip
lp creategroup yae_admin

# 设置继承关系
lp group yae_player parent add yae_newplayer
lp group yae_vip parent add yae_player
lp group yae_admin parent add yae_vip

# 分配权限
lp group yae_newplayer permission set yet_another_economy.user.balance
lp group yae_newplayer permission set yet_another_economy.user.pay
lp group yae_newplayer permission set yet_another_economy.user.credit.score

lp group yae_player permission set yet_another_economy.user.shopping true
lp group yae_player permission set yet_another_economy.user.loan.apply true
lp group yae_player permission set yet_another_economy.user.loan.repay true
```

---

## 📊 性能优化

### 内存占用优化
```yaml
performance:
  # 缓存设置
  cache:
    enabled: true
    max-size: 1000          # 最大缓存数量
    expiry-time: 1800       # 30分钟过期
    
  # 数据库连接池优化
  database:
    connection-pool-size: 8
    connection-timeout: 3000
    
  # 查询优化
  query:
    batch-size: 100         # 批处理大小
    auto-commit: false      # 事务自动提交
    
  # 日志级别
  log-level: INFO
  debug-sql: false
  performance-metrics: false
```

### 服务器启动优化
```yaml
startup:
  # 初始化设置
  lazy-loading: true        # 懒加载模式
  pre-load-balance: false   # 不预加载所有余额
  
  # 异步初始化
  async-initialization:
    enabled: true
    thread-count: 2
    queue-size: 10
    
  # 数据库初始化
  database-initialization:
    create-tables-on-startup: true
    verify-connection-on-startup: true
    init-script-timeout: 30
```

### 高并发优化
```yaml
concurrency:
  # 线程池设置
  thread-pools:
    io-pool-size: 8         # IO操作线程池
    cpu-pool-size: 4        # CPU计算线程池
    scheduled-pool-size: 2  # 定时任务线程池
    
  # 锁设置
  locks:
    account-lock-timeout: 5000     # 账户锁超时(毫秒)
    global-lock-timeout: 10000     # 全局锁超时
    deadlock-detection: true       # 死锁检测
    
  # 限制
  limits:
    max-concurrent-transfers: 50   # 最大并发转账数
    max-concurrent-shopping: 20    # 最大并发购物数
    sql-connection-pool-size: 15   # SQL连接池大小
```

---

## 🔧 故障排除

### 常见问题解决

#### 1. 插件无法加载
**症状**: 启动时出现 `ClassNotFoundException`  
**原因**: Java版本不兼容或缺少依赖  
**解决**:
```bash
# 检查Java版本
java -version
# 应该显示 Java 17 或更高

# 检查依赖插件
ls plugins/ | grep -E "(Vault|LuckPerms)"
```

#### 2. 数据库连接失败
**症状**: `Connection refused` 或找不到数据库  
**解决**: 
```yaml
# 检查 MySQL 配置
database:
  type: "mysql"
  mysql:
    host: "localhost"          # 确保地址正确
    port: 3306                 # 默认端口 3306
    database: "minecraft"      # 数据库必须存在
    # 确保用户有所有权限
    # GRANT ALL PRIVILEGES ON minecraft.* TO 'yae_user'@'localhost';
```

#### 3. 中文显示乱码
**症状**: 消息显示为问号或乱码  
**解决**: 
```yaml
# 确保 UTF-8 设置
database:
  type: "mysql"
  mysql:
    connection-properties:
      "useUnicode": "true"
      "characterEncoding": "utf8mb4"

# 或在Paper服务端
# 编辑 server.properties
# 设置 file-encoding=utf-8
```

#### 4. 性能低下
**症状**: 玩家操作时明显卡顿  
**解决**: 
```yaml
performance:
  # 启用性能监控
  performance-metrics: true
  
  # 调整连接池
  database:
    connection-pool-size: 15
    max-wait: 2000
    
  # 调整缓存
  cache:
    max-size: 2000
    expiry-time: 600  # 缩短过期时间
```

### 日志和调试

#### 启用详细日志
```yaml
logging:
  level: DEBUG
  categories:
    database: true
    transactions: true
    sql-queries: true
    performance: true
  
  # 日志文件
  log-file: "plugins/YetAnotherEconomy/logs/yae.log"
  max-size: "10MB"
  max-files: 5
```

#### 性能监控
```bash
# 监控数据库性能
mysql> SHOW STATUS LIKE 'Threads_connected';
mysql> SHOW STATUS LIKE 'Max_used_connections';
mysql> SHOW PROCESSLIST;

# SQLite 性能检查
sqlite3 database.db
sqlite> .stats on
sqlite> PRAGMA integrity_check;
```

### 数据备份和恢复

#### 自动备份配置
```yaml
backup:
  enabled: true
  interval: 86400          # 24小时备份一次  
  keep-backups: 7          # 保留7天的备份
  compress: true           # 压缩备份文件
  
  # 备份源
  sources:
    database: true
    config: true
    logs: false
    
  # 目标位置  
  destination: "backups/daily_%Y%m%d_%H%M%S.tar.gz"
  
  # 通知设置
  notify:
    enabled: true
    discord-webhook: ""
    admin-emails: []
```

#### 手动备份
```bash
# SQLite 备份
cp plugins/YetAnotherEconomy/database.db backup/database_backup.$(date +%Y%m%d).db

# MySQL 备份
mysqldump -u yae_user -p minecraft > backup/yae_backup_$(date +%Y%m%d).sql

# 配置文件备份
cp -r plugins/YetAnotherEconomy/ backup/yae_config_$(date +%Y%m%d)/
```

#### 数据恢复
```bash
# SQLite 恢复
cpl backup/database_backup.$(date).db plugins/YetAnotherEconomy/database.db

# MySQL 恢复  
mysql -u yae_user -p minecraft < backup/yae_backup_$(date).sql

# 然后重启服务器
```

---

## 📚 更多资源

### 官方文档
- [完整文档](README_FINAL.md) - 项目详细介绍
- [功能特性](FEATURE_DETAILS.md) - 各模块详细信息
- [API 参考](API_REFERENCE.md) - 开发接口文档

### 社区支持
- **GitHub Issues** - [提交问题/建议](https://github.com/FallingSkyQwQ/yet-another-economy/issues)
- **QQ 讨论群** - Minecraft服务器技术交流 (群号待创建)
- **Bukkit Forums** - [插件发布页面](https://bukkit.org)

### 相关资源
- **SpigotMC** - 官方插件资源站
- **PaperMC** - 高性能服务端
- **Vault 文档** - 经济API标准
- **LuckPerms 文档** - 权限管理系统

---

**✅ 安装完成！**  
现在您已经成功安装并配置了 YetAnotherEconomy 经济插件，可以开始享受专业的Minecraft经济系统了！

如果遇到任何问题，请参考故障排除部分或在GitHub提交Issue寻求帮助。
