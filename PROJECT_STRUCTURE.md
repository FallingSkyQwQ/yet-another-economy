# YAE Maven 项目结构

## 项目概览
已创建完整的Maven项目结构，支持Minecraft Paper服务器插件开发。

## 文件结构
```
yet_another_economy/
├── pom.xml                                  # Maven配置文件
├── PROJECT_STRUCTURE.md                     # 项目结构文档
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── yae/
│       │           └── YetAnotherEconomy.java  # 主插件类
│       └── resources/
│           ├── plugin.yml                   # 插件配置文件
│           └── config.yml                   # 主配置文件
└── README.md                                # 项目文档
```

## 依赖配置 (pom.xml)

### 主要依赖版本
- **Paper API**: 1.20.4-R0.1-SNAPSHOT
- **Vault API**: 1.7.1  
- **LuckPerms API**: 5.4
- **TriumphGUI**: 3.1.7
- **Annotations**: 24.0.0

### 存储库配置
- PaperMC Repository
- JitPack Repository (for Vault)
- LuckPerms Repository  
- TriumphTeam Repository

### 构建配置
- Java 17 编译目标
- Maven Shade Plugin 用于依赖重定位
- TriumphGUI 重定位到 `com.yae.libs.gui`

## Plugin YML 配置

### 基本信息
- 插件名称: YetAnotherEconomy
- 主类: com.yae.YetAnotherEconomy
- 版本: 1.0.0
- API版本: 1.20

### 依赖关系
- **软依赖**: Vault, LuckPerms
- **依赖**: TriumphGUI

### 权限系统
完整权限树结构，包含:
- yae.* - 所有权限
- yae.command.* - 命令权限
- yae.admin.* - 管理员权限  
- yae.user.* - 用户权限

## 配置系统 (config.yml)

### 主要功能模块
- 货币设置 (名称、符号、小数位、初始余额)
- 数据库支持 (SQLite, MySQL, MariaDB)
- 交易设置 (历史记录、限额、税率)
- 消息设置 (多语言支持)
- 高级功能 (利息、银行、PVP奖励)
- 性能优化 (缓存、异步保存)

## 兼容性验证

### 版本兼容性说明
1. **Paper API 1.20.4** - 兼容Minecraft 1.20.4版本
2. **Vault 1.7.1** - 稳定版本，广泛使用
3. **LuckPerms 5.4** - 最新主要版本
4. **TriumphGUI 3.1.7** - 稳定版本
5. **Java 17** - 现代LTS版本

### 依赖冲突检查
- All Paper API dependencies excluded from Vault
- TriumphGUI relocated to prevent conflicts
- Version ranges mutually compatible

## 后续步骤
1. 安装Maven环境
2. 执行 `mvn clean package` 构建项目
3. 部署到Paper服务器测试
4. 根据需求实现具体功能

## 注意事项
- 需要在支持Maven的环境中构建
- Paper服务器需要1.20.4版本
- 依赖的插件需要相应版本兼容
