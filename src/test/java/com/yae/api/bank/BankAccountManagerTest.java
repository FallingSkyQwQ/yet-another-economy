package com.yae.api.bank;

import com.yae.api.core.YAECore;
import com.yae.api.core.config.Configuration;
import com.yae.api.core.config.LanguageManager;
import com.yae.api.core.event.YAEEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * BankAccountManager服务类单元测试
 * 测试银行账户管理器的完整功能
 */
@DisplayName("BankAccountManager 单元测试")
public class BankAccountManagerTest {
    
    private BankAccountManager bankManager;
    private YAECore mockPlugin;
    private Configuration mockConfiguration;
    private LanguageManager mockLanguageManager;
    private Economy mockVaultEconomy;
    private Server mockServer;
    private PluginManager mockPluginManager;
    private ServicesManager mockServicesManager;
    
    @BeforeEach
    void setUp() {
        // 创建mock对象
        mockPlugin = mock(YAECore.class);
        mockConfiguration = mock(Configuration.class);
        mockLanguageManager = mock(LanguageManager.class);
        mockVaultEconomy = mock(Economy.class);
        mockServer = mock(Server.class);
        mockPluginManager = mock(PluginManager.class);
        mockServicesManager = mock(ServicesManager.class);
        
        // 配置mock行为
        when(mockPlugin.getMainConfiguration()).thenReturn(mockConfiguration);
        when(mockPlugin.getLanguageManager()).thenReturn(mockLanguageManager);
        when(mockPlugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        when(mockPlugin.getServer()).thenReturn(mockServer);
        when(mockServer.getPluginManager()).thenReturn(mockPluginManager);
        when(mockServer.getServicesManager()).thenReturn(mockServicesManager);
        
        // 配置配置mock
        Configuration.FeaturesConfig.BankingConfig bankingConfig = new Configuration.FeaturesConfig.BankingConfig();
        bankingConfig.setEnabled(true);
        bankingConfig.setMaxAccounts(3);
        
        Configuration.FeaturesConfig featuresConfig = new Configuration.FeaturesConfig();
        featuresConfig.setBanking(bankingConfig);
        
        when(mockConfiguration.getFeatures()).thenReturn(featuresConfig);
        
        bankManager = new BankAccountManager(mockPlugin);
    }
    
    @Nested
    @DisplayName("服务初始化和生命周期测试")
    class ServiceLifecycleTest {
        
        @Test
        @DisplayName("应该成功初始化银行服务")
        void shouldInitializeBankServiceSuccessfully() {
            // 跳过Vault集成，直接测试初始化
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            
            boolean result = bankManager.initialize();
            
            assertTrue(result);
            assertTrue(bankManager.isEnabled());
            assertTrue(bankManager.isHealthy());
        }
        
        @Test
        @DisplayName("应该成功重载银行服务")
        void shouldReloadBankServiceSuccessfully() {
            // 先初始化
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            bankManager.initialize();
            
            // 然后重载
            boolean result = bankManager.reload();
            
            assertTrue(result);
            assertTrue(bankManager.isEnabled());
            assertTrue(bankManager.isHealthy());
        }
        
        @Test
        @DisplayName("应该成功关闭银行服务")
        void shouldShutdownBankServiceSuccessfully() {
            // 先初始化
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            bankManager.initialize();
            
            // 然后关闭
            bankManager.shutdown();
            
            assertFalse(bankManager.isEnabled());
        }
        
        @Test
        @DisplayName("当银行功能禁用时初始化应该成功但不启用")
        void shouldInitializeButNotEnableWhenBankingDisabled() {
            // 修改配置禁用银行功能
            Configuration.FeaturesConfig.BankingConfig disabledConfig = new Configuration.FeaturesConfig.BankingConfig();
            disabledConfig.setEnabled(false);
            
            Configuration.FeaturesConfig disabledFeaturesConfig = new Configuration.FeaturesConfig();
            disabledFeaturesConfig.setBanking(disabledConfig);
            
            when(mockConfiguration.getFeatures()).thenReturn(disabledFeaturesConfig);
            
            boolean result = bankManager.initialize();
            
            assertTrue(result); // 初始化成功
            // 注意：在禁用状态下，服务仍然初始化但不执行核心功能
        }
        
        @Test
        @DisplayName("应该正确处理Vault集成失败")
        void shouldHandleVaultIntegrationFailure() {
            // 模拟Vault不可用
            when(mockPluginManager.getPlugin("Vault")).thenReturn(null);
            
            boolean result = bankManager.initialize();
            
            assertTrue(result); // 即使没有Vault也应该成功初始化
            assertTrue(bankManager.isEnabled());
        }
    }
    
    @Nested
    @DisplayName("账户创建测试")
    class AccountCreationTest {
        
        @BeforeEach
        void initializeBankManager() {
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            bankManager.initialize();
        }
        
        @Test
        @DisplayName("应该成功创建银行账户")
        void shouldCreateBankAccountSuccessfully() {
            UUID ownerId = UUID.randomUUID();
            
            BankAccount account = bankManager.createAccount(
                ownerId, "PLAYER", BankAccount.AccountType.CHECKING
            );
            
            assertNotNull(account);
            assertEquals(ownerId, account.getOwnerId());
            assertEquals("PLAYER", account.getOwnerType());
            assertEquals(BankAccount.AccountType.CHECKING, account.getAccountType());
            assertNotNull(account.getAccountNumber());
            assertTrue(account.getAccountNumber().startsWith("P1")); // PLAYER + CHECKING
            assertEquals(BankAccount.AccountStatus.ACTIVE, account.getStatus());
            assertEquals(BigDecimal.ZERO, account.getCurrentBalance());
        }
        
        @Test
        @DisplayName("应该为不同所有者创建多个账户")
        void shouldCreateMultipleAccountsForDifferentOwners() {
            UUID player1Id = UUID.randomUUID();
            UUID player2Id = UUID.randomUUID();
            
            BankAccount account1 = bankManager.createAccount(
                player1Id, "PLAYER", BankAccount.AccountType.CHECKING
            );
            BankAccount account2 = bankManager.createAccount(
                player2Id, "PLAYER", BankAccount.AccountType.SAVINGS
            );
            
            assertNotNull(account1);
            assertNotNull(account2);
            assertNotEquals(account1.getAccountId(), account2.getAccountId());
            assertNotEquals(account1.getAccountNumber(), account2.getAccountNumber());
        }
        
        @Test
        @DisplayName("应该拒绝超过账户数量限制的创建")
        void shouldRejectAccountCreationWhenLimitReached() {
            UUID ownerId = UUID.randomUUID();
            
            // 创建最大数量的账户（根据配置是3个）
            bankManager.createAccount(ownerId, "PLAYER", BankAccount.AccountType.CHECKING);
            bankManager.createAccount(ownerId, "PLAYER", BankAccount.AccountType.SAVINGS);
            bankManager.createAccount(ownerId, "PLAYER", BankAccount.AccountType.FIXED_DEPOSIT);
            
            // 尝试创建第4个账户应该失败
            assertThrows(IllegalStateException.class, () ->
                bankManager.createAccount(ownerId, "PLAYER", BankAccount.AccountType.LOAN)
            );
        }
        
        @Test
        @DisplayName("应该创建不同账户类型的账户")
        void shouldCreateAccountsOfDifferentTypes() {
            UUID ownerId = UUID.randomUUID();
            
            BankAccount checkingAccount = bankManager.createAccount(
                ownerId, "PLAYER", BankAccount.AccountType.CHECKING
            );
            BankAccount savingsAccount = bankManager.createAccount(
                ownerId, "PLAYER", BankAccount.AccountType.SAVINGS
            );
            
            assertEquals(BankAccount.AccountType.CHECKING, checkingAccount.getAccountType());
            assertEquals(BankAccount.AccountType.SAVINGS, savingsAccount.getAccountType());
            assertTrue(checkingAccount.getAccountNumber().startsWith("P1"));
            assertTrue(savingsAccount.getAccountNumber().startsWith("P2"));
        }
        
        @Test
        @DisplayName("应该拒绝null参数")
        void shouldRejectNullParameters() {
            assertThrows(NullPointerException.class, () ->
                bankManager.createAccount(null, "PLAYER", BankAccount.AccountType.CHECKING)
            );
            
            assertThrows(NullPointerException.class, () ->
                bankManager.createAccount(UUID.randomUUID(), null, BankAccount.AccountType.CHECKING)
            );
            
            assertThrows(NullPointerException.class, () ->
                bankManager.createAccount(UUID.randomUUID(), "PLAYER", null)
            );
        }
    }
    
    @Nested
    @DisplayName("账户查询测试")
    class AccountQueryTest {
        
        private UUID testOwnerId;
        private BankAccount testAccount;
        
        @BeforeEach
        void setupTestAccount() {
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            bankManager.initialize();
            
            testOwnerId = UUID.randomUUID();
            testAccount = bankManager.createAccount(
                testOwnerId, "PLAYER", BankAccount.AccountType.CHECKING
            );
        }
        
        @Test
        @DisplayName("应该通过账户ID查询账户")
        void shouldQueryAccountById() {
            BankAccount foundAccount = bankManager.getAccount(testAccount.getAccountId());
            
            assertNotNull(foundAccount);
            assertEquals(testAccount, foundAccount);
        }
        
        @Test
        @DisplayName("应该通过账户号码查询账户")
        void shouldQueryAccountByNumber() {
            BankAccount foundAccount = bankManager.getAccountByNumber(testAccount.getAccountNumber());
            
            assertNotNull(foundAccount);
            assertEquals(testAccount, foundAccount);
        }
        
        @Test
        @DisplayName("应该查询所有者的所有账户")
        void shouldQueryAllOwnerAccounts() {
            // 为同一所有者创建多个账户
            BankAccount savingsAccount = bankManager.createAccount(
                testOwnerId, "PLAYER", BankAccount.AccountType.SAVINGS
            );
            
            List<BankAccount> ownerAccounts = bankManager.getOwnerAccounts(testOwnerId);
            
            assertEquals(2, ownerAccounts.size());
            assertTrue(ownerAccounts.contains(testAccount));
            assertTrue(ownerAccounts.contains(savingsAccount));
        }
        
        @Test
        @DisplayName("应该返回空列表当所有者没有账户")
        void shouldReturnEmptyListWhenOwnerHasNoAccounts() {
            UUID newOwnerId = UUID.randomUUID();
            List<BankAccount> ownerAccounts = bankManager.getOwnerAccounts(newOwnerId);
            
            assertTrue(ownerAccounts.isEmpty());
        }
        
        @Test
        @DisplayName("应该返回null当查询不存在的账户")
        void shouldReturnNullForNonExistentAccounts() {
            UUID nonExistentAccountId = UUID.randomUUID();
            String nonExistentAccountNumber = "NONEXISTENT123";
            
            assertNull(bankManager.getAccount(nonExistentAccountId));
            assertNull(bankManager.getAccountByNumber(nonExistentAccountNumber));
        }
        
        @Test
        @DisplayName("应该拒绝null参数查询")
        void shouldRejectNullParametersInQueries() {
            assertThrows(NullPointerException.class, () ->
                bankManager.getAccount(null)
            );
            
            assertThrows(NullPointerException.class, () ->
                bankManager.getAccountByNumber(null)
            );
            
            assertThrows(NullPointerException.class, () ->
                bankManager.getOwnerAccounts(null)
            );
        }
    }
    
    @Nested
    @DisplayName("定期存款测试")
    class FixedDepositTest {
        
        private BankAccount testAccount;
        
        @BeforeEach
        void setupTestAccount() {
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            bankManager.initialize();
            
            UUID ownerId = UUID.randomUUID();
            testAccount = bankManager.createAccount(
                ownerId, "PLAYER", BankAccount.AccountType.CHECKING
            );
            
            // 给账户充值以进行定期存款测试
            testAccount.deposit(new BigDecimal("1000.00"));
        }
        
        @Test
        @DisplayName("应该成功创建定期存款")
        void shouldCreateFixedDepositSuccessfully() {
            BigDecimal depositAmount = new BigDecimal("500.00");
            FixedDeposit.DepositTerm term = FixedDeposit.DepositTerm.ONE_YEAR;
            
            FixedDeposit deposit = bankManager.createFixedDeposit(
                testAccount.getAccountId(), depositAmount, term
            );
            
            assertNotNull(deposit);
            assertEquals(testAccount.getAccountId(), deposit.getAccountId());
            assertEquals(depositAmount, deposit.getPrincipal());
            assertEquals(term, deposit.getTerm());
            assertEquals(term.getAnnualInterestRate(), deposit.getInterestRate());
            assertEquals(FixedDeposit.DepositStatus.ACTIVE, deposit.getStatus());
            
            // 验证活期账户余额被扣除
            assertEquals(new BigDecimal("500.00"), testAccount.getCurrentBalance());
            assertEquals(new BigDecimal("500.00"), testAccount.getAvailableBalance());
        }
        
        @Test
        @DisplayName("应该拒绝余额不足的定期存款创建")
        void shouldRejectFixedDepositWhenInsufficientBalance() {
            // 账户中只剩下400，不足以创建500的定期存款
            testAccount.withdraw(new BigDecimal("600.00"));
            
            assertThrows(IllegalStateException.class, () ->
                bankManager.createFixedDeposit(
                    testAccount.getAccountId(), 
                    new BigDecimal("500.00"), 
                    FixedDeposit.DepositTerm.ONE_YEAR
                )
            );
        }
        
        @Test
        @DisplayName("应该拒绝为非活跃账户创建定期存款")
        void shouldRejectFixedDepositForInactiveAccount() {
            testAccount.freeze();
            
            assertThrows(IllegalStateException.class, () ->
                bankManager.createFixedDeposit(
                    testAccount.getAccountId(), 
                    new BigDecimal("500.00"), 
                    FixedDeposit.DepositTerm.ONE_YEAR
                )
            );
        }
        
        @Test
        @DisplayName("应该拒绝null参数")
        void shouldRejectNullParameters() {
            assertThrows(NullPointerException.class, () ->
                bankManager.createFixedDeposit(
                    null, 
                    new BigDecimal("500.00"), 
                    FixedDeposit.DepositTerm.ONE_YEAR
                )
            );
            
            assertThrows(NullPointerException.class, () ->
                bankManager.createFixedDeposit(
                    testAccount.getAccountId(), 
                    null, 
                    FixedDeposit.DepositTerm.ONE_YEAR
                )
            );
            
            assertThrows(NullPointerException.class, () ->
                bankManager.createFixedDeposit(
                    testAccount.getAccountId(), 
                    new BigDecimal("500.00"), 
                    null
                )
            );
        }
        
        @Test
        @DisplayName("应该拒绝为不存在的账户创建定期存款")
        void shouldRejectFixedDepositForNonExistentAccount() {
            UUID nonExistentAccountId = UUID.randomUUID();
            
            assertThrows(IllegalArgumentException.class, () ->
                bankManager.createFixedDeposit(
                    nonExistentAccountId, 
                    new BigDecimal("500.00"), 
                    FixedDeposit.DepositTerm.ONE_YEAR
                )
            );
        }
        
        @Test
        @DisplayName("应该创建不同存期的定期存款")
        void shouldCreateFixedDepositsWithDifferentTerms() {
            FixedDeposit deposit3Months = bankManager.createFixedDeposit(
                testAccount.getAccountId(), 
                new BigDecimal("100.00"), 
                FixedDeposit.DepositTerm.THREE_MONTHS
            );
            
            FixedDeposit deposit5Years = bankManager.createFixedDeposit(
                testAccount.getAccountId(), 
                new BigDecimal("200.00"), 
                FixedDeposit.DepositTerm.FIVE_YEARS
            );
            
            assertNotNull(deposit3Months);
            assertNotNull(deposit5Years);
            assertEquals(0.0125, deposit3Months.getInterestRate());
            assertEquals(0.035, deposit5Years.getInterestRate());
        }
    }
    
    @Nested
    @DisplayName("Vault集成测试")
    class VaultIntegrationTest {
        
        @BeforeEach
        void setupVaultMock() {
            // 配置Vault模拟
            when(mockPluginManager.getPlugin("Vault")).thenReturn(mock(org.bukkit.plugin.Plugin.class));
            
            var vaultRegistration = mock(org.bukkit.plugin.RegisteredServiceProvider.class);
            when(vaultRegistration.getProvider()).thenReturn(mockVaultEconomy);
            when(mockServicesManager.getRegistration(Economy.class)).thenReturn(vaultRegistration);
        }
        
        @Test
        @DisplayName("应该成功集成Vault经济系统")
        void shouldIntegrateWithVaultEconomy() {
            bankManager.initialize();
            
            assertTrue(bankManager.isEnabled());
            assertTrue(bankManager.isHealthy());
        }
        
        @Test
        @DisplayName("应该通过Vault成功存款")
        void shouldDepositThroughVault() {
            bankManager.initialize();
            OfflinePlayer player = mock(OfflinePlayer.class);
            
            when(mockVaultEconomy.depositPlayer(player, 100.0))
                .thenReturn(new net.milkbowl.vault.economy.EconomyResponse(
                    100.0, 200.0, 
                    net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, 
                    "Success"
                ));
            
            var response = bankManager.depositToVault(player, 100.0);
            
            assertEquals(net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, 
                        response.type);
            assertEquals(100.0, response.amount);
            assertEquals(200.0, response.balance);
        }
        
        @Test
        @DisplayName("应该通过Vault成功取款")
        void shouldWithdrawThroughVault() {
            bankManager.initialize();
            OfflinePlayer player = mock(OfflinePlayer.class);
            
            when(mockVaultEconomy.withdrawPlayer(player, 50.0))
                .thenReturn(new net.milkbowl.vault.economy.EconomyResponse(
                    50.0, 150.0, 
                    net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, 
                    "Success"
                ));
            
            var response = bankManager.withdrawFromVault(player, 50.0);
            
            assertEquals(net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, 
                        response.type);
            assertEquals(50.0, response.amount);
            assertEquals(150.0, response.balance);
        }
        
        @Test
        @DisplayName("应该正确查询Vault余额")
        void shouldQueryVaultBalance() {
            bankManager.initialize();
            OfflinePlayer player = mock(OfflinePlayer.class);
            
            when(mockVaultEconomy.getBalance(player)).thenReturn(500.0);
            
            double balance = bankManager.getVaultBalance(player);
            
            assertEquals(500.0, balance);
        }
        
        @Test
        @DisplayName("应该处理Vault不可用的failure情况")
        void shouldHandleVaultUnavailableFailure() {
            bankManager.initialize();
            OfflinePlayer player = mock(OfflinePlayer.class);
            
            // Vault未初始化时应该返回失败响应
            var depositResponse = bankManager.depositToVault(player, 100.0);
            
            assertEquals(net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE, 
                        depositResponse.type);
            assertEquals("Vault economy not available", depositResponse.errorMessage);
            
            var balance = bankManager.getVaultBalance(player);
            assertEquals(0.0, balance);
        }
    }
    
    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTest {
        
        @BeforeEach
        void setupBankManager() {
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            bankManager.initialize();
        }
        
        @Test
        @DisplayName("应该线程安全地创建账户")
        void shouldCreateAccountsThreadSafely() throws InterruptedException {
            final int threadCount = 10;
            final UUID sharedOwnerId = UUID.randomUUID();
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<BankAccount> createdAccounts = Collections.synchronizedList(new ArrayList<>());
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
            
            // 并行创建账户
            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        BankAccount account = bankManager.createAccount(
                            sharedOwnerId, "PLAYER", 
                            i % 2 == 0 ? BankAccount.AccountType.CHECKING : BankAccount.AccountType.SAVINGS
                        );
                        createdAccounts.add(account);
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }
            
            // 等待所有线程完成
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            
            // 验证结果
            assertTrue(exceptions.isEmpty() || 
                      (exceptions.size() == 7 && exceptions.stream()
                       .allMatch(e -> e instanceof IllegalStateException))); // 超出限制的操作
            
            // 验证创建的账户数量
            List<BankAccount> ownerAccounts = bankManager.getOwnerAccounts(sharedOwnerId);
            assertEquals(3, ownerAccounts.size()); // 限制为3个账户
        }
        
        @Test
        @DisplayName("应该线程安全地处理存款和取款操作")
        void shouldHandleDepositsAndWithdrawalsThreadSafely() throws InterruptedException {
            final int operationCount = 100;
            final BigDecimal amountPerOperation = new BigDecimal("10.00");
            
            UUID ownerId = UUID.randomUUID();
            BankAccount account = bankManager.createAccount(
                ownerId, "PLAYER", BankAccount.AccountType.CHECKING
            );
            
            CountDownLatch depositLatch = new CountDownLatch(operationCount);
            CountDownLatch withdrawalLatch = new CountDownLatch(operationCount);
            
            List<Boolean> depositResults = Collections.synchronizedList(new ArrayList<>());
            List<Boolean> withdrawalResults = Collections.synchronizedList(new ArrayList<>());
            
            // 先存入足够的初始资金
            account.deposit(amountPerOperation.multiply(BigDecimal.valueOf(operationCount * 2)));
            
            // 并行执行存款操作
            for (int i = 0; i < operationCount; i++) {
                new Thread(() -> {
                    boolean result = account.deposit(amountPerOperation);
                    depositResults.add(result);
                    depositLatch.countDown();
                }).start();
            }
            
            // 并行执行取款操作
            for (int i = 0; i < operationCount; i++) {
                new Thread(() -> {
                    boolean result = account.withdraw(amountPerOperation);
                    withdrawalResults.add(result);
                    withdrawalLatch.countDown();
                }).start();
            }
            
            // 等待所有操作完成
            assertTrue(depositLatch.await(10, TimeUnit.SECONDS));
            assertTrue(withdrawalLatch.await(10, TimeUnit.SECONDS));
            
            // 验证所有操作都成功
            assertTrue(depositResults.stream().allMatch(Boolean::booleanValue));
            assertTrue(withdrawalResults.stream().filter(Boolean::booleanValue).count() >= 50); // 应该大部分成功
        }
    }
    
    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTest {
        
        @Test
        @DisplayName("应该优雅地处理初始化异常")
        void shouldHandleInitializationException() {
            // 模拟配置加载失败
            when(mockConfiguration.getFeatures()).thenThrow(new RuntimeException("配置加载失败"));
            
            boolean result = bankManager.initialize();
            
            assertFalse(result);
            assertFalse(bankManager.isHealthy());
        }
        
        @Test
        @DisplayName("应该优雅地处理重载异常")
        void shouldHandleReloadException() {
            // 先正确初始化
            doReturn(null).when(mockPluginManager).getPlugin("Vault");
            bankManager.initialize();
            
            // 然后模拟重载时配置异常
            when(mockConfiguration.getFeatures()).thenThrow(new RuntimeException("重载配置失败"));
            
            boolean result = bankManager.reload();
            
            assertFalse(result);
            assertFalse(bankManager.isHealthy());
        }
    }
}
