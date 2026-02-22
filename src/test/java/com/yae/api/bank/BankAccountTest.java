package com.yae.api.bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BankAccount实体类单元测试
 * 测试银行账户的基本功能和业务逻辑
 */
@DisplayName("BankAccount 单元测试")
public class BankAccountTest {
    
    private BankAccount testAccount;
    private UUID testOwnerId;
    private String testAccountNumber;
    
    @BeforeEach
    void setUp() {
        testOwnerId = UUID.randomUUID();
        testAccountNumber = "TEST123456789";
        testAccount = new BankAccount(
            testOwnerId, 
            "PLAYER", 
            BankAccount.AccountType.CHECKING, 
            testAccountNumber
        );
    }
    
    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTest {
        
        @Test
        @DisplayName("应该成功创建有效的银行账户")
        void shouldCreateValidBankAccount() {
            assertNotNull(testAccount);
            assertEquals(testOwnerId, testAccount.getOwnerId());
            assertEquals("PLAYER", testAccount.getOwnerType());
            assertEquals(BankAccount.AccountType.CHECKING, testAccount.getAccountType());
            assertEquals(testAccountNumber, testAccount.getAccountNumber());
            assertEquals(BankAccount.AccountStatus.ACTIVE, testAccount.getStatus());
            assertEquals(BigDecimal.ZERO, testAccount.getCurrentBalance());
            assertEquals(BigDecimal.ZERO, testAccount.getAvailableBalance());
            assertEquals(BigDecimal.ZERO, testAccount.getFrozenAmount());
            assertEquals(650, testAccount.getCreditScore());
            assertEquals(BigDecimal.valueOf(0.01), testAccount.getInterestRate());
        }
        
        @Test
        @DisplayName("应该抛出NullPointerException当传入null参数")
        void shouldThrowNullPointerExceptionWhenNullParameters() {
            assertThrows(NullPointerException.class, () -> 
                new BankAccount(null, "PLAYER", BankAccount.AccountType.CHECKING, "12345"));
            
            assertThrows(NullPointerException.class, () -> 
                new BankAccount(testOwnerId, null, BankAccount.AccountType.CHECKING, "12345"));
            
            assertThrows(NullPointerException.class, () -> 
                new BankAccount(testOwnerId, "PLAYER", null, "12345"));
            
            assertThrows(NullPointerException.class, () -> 
                new BankAccount(testOwnerId, "PLAYER", BankAccount.AccountType.CHECKING, null));
        }
    }
    
    @Nested
    @DisplayName("账户状态测试")
    class AccountStatusTest {
        
        @Test
        @DisplayName("应该成功冻结账户")
        void shouldFreezeAccount() {
            testAccount.freeze();
            assertEquals(BankAccount.AccountStatus.FROZEN, testAccount.getStatus());
            assertFalse(testAccount.canTransact());
        }
        
        @Test
        @DisplayName("应该成功解冻账户")
        void shouldUnfreezeAccount() {
            testAccount.freeze();
            testAccount.unfreeze();
            assertEquals(BankAccount.AccountStatus.ACTIVE, testAccount.getStatus());
            assertTrue(testAccount.canTransact());
        }
        
        @Test
        @DisplayName("应该成功关闭账户")
        void shouldCloseAccount() {
            testAccount.close();
            assertEquals(BankAccount.AccountStatus.CLOSED, testAccount.getStatus());
            assertFalse(testAccount.isActive());
            assertFalse(testAccount.canTransact());
        }
        
        @Test
        @DisplayName("关闭账户后不能再进行其他状态变更")
        void shouldNotAllowStatusChangeAfterClosed() {
            testAccount.close();
            
            // 尝试冻结已关闭的账户
            testAccount.freeze();
            assertEquals(BankAccount.AccountStatus.CLOSED, testAccount.getStatus());
            
            // 尝试解冻已关闭的账户
            testAccount.unfreeze();
            assertEquals(BankAccount.AccountStatus.CLOSED, testAccount.getStatus());
        }
    }
    
    @Nested
    @DisplayName("存款业务测试")
    class DepositTest {
        
        @Test
        @DisplayName("应该成功存入正数金额")
        void shouldDepositPositiveAmount() {
            BigDecimal depositAmount = new BigDecimal("100.00");
            boolean result = testAccount.deposit(depositAmount);
            
            assertTrue(result);
            assertEquals(depositAmount, testAccount.getCurrentBalance());
            assertEquals(depositAmount, testAccount.getAvailableBalance());
        }
        
        @Test
        @DisplayName("应该拒绝存入零或负数金额")
        void shouldRejectZeroOrNegativeAmount() {
            assertFalse(testAccount.deposit(BigDecimal.ZERO));
            assertFalse(testAccount.deposit(new BigDecimal("-50.00")));
            assertEquals(BigDecimal.ZERO, testAccount.getCurrentBalance());
            assertEquals(BigDecimal.ZERO, testAccount.getAvailableBalance());
        }
        
        @Test
        @DisplayName("应该拒绝向非活跃账户存款")
        void shouldRejectDepositToInactiveAccount() {
            testAccount.freeze();
            
            boolean result = testAccount.deposit(new BigDecimal("100.00"));
            
            assertFalse(result);
            assertEquals(BigDecimal.ZERO, testAccount.getCurrentBalance());
        }
        
        @Test
        @DisplayName("应该正确计算多次存款的累积金额")
        void shouldAccumulateMultipleDeposits() {
            testAccount.deposit(new BigDecimal("100.00"));
            testAccount.deposit(new BigDecimal("200.50"));
            testAccount.deposit(new BigDecimal("50.25"));
            
            assertEquals(new BigDecimal("350.75"), testAccount.getCurrentBalance());
            assertEquals(new BigDecimal("350.75"), testAccount.getAvailableBalance());
        }
    }
    
    @Nested
    @DisplayName("取款业务测试")
    class WithdrawalTest {
        
        @BeforeEach
        void prepareAccountWithBalance() {
            testAccount.deposit(new BigDecimal("500.00"));
        }
        
        @Test
        @DisplayName("应该成功取出不超过余额的金额")
        void shouldWithdrawValidAmount() {
            BigDecimal withdrawAmount = new BigDecimal("200.00");
            boolean result = testAccount.withdraw(withdrawAmount);
            
            assertTrue(result);
            assertEquals(new BigDecimal("300.00"), testAccount.getCurrentBalance());
            assertEquals(new BigDecimal("300.00"), testAccount.getAvailableBalance());
        }
        
        @Test
        @DisplayName("应该拒绝取出超过余额的金额")
        void shouldRejectExcessiveWithdrawal() {
            BigDecimal withdrawAmount = new BigDecimal("600.00");
            boolean result = testAccount.withdraw(withdrawAmount);
            
            assertFalse(result);
            assertEquals(new BigDecimal("500.00"), testAccount.getCurrentBalance());
            assertEquals(new BigDecimal("500.00"), testAccount.getAvailableBalance());
        }
        
        @Test
        @DisplayName("应该拒绝取出零或负数金额")
        void shouldRejectZeroOrNegativeWithdrawal() {
            assertFalse(testAccount.withdraw(BigDecimal.ZERO));
            assertFalse(testAccount.withdraw(new BigDecimal("-50.00")));
            assertEquals(new BigDecimal("500.00"), testAccount.getCurrentBalance());
        }
        
        @Test
        @DisplayName("应该拒绝从非活跃账户取款")
        void shouldRejectWithdrawalFromInactiveAccount() {
            testAccount.freeze();
            
            boolean result = testAccount.withdraw(new BigDecimal("100.00"));
            
            assertFalse(result);
            assertEquals(new BigDecimal("500.00"), testAccount.getCurrentBalance());
        }
    }
    
    @Nested
    @DisplayName("金额冻结测试")
    class FreezeAmountTest {
        
        @BeforeEach
        void prepareAccountWithBalance() {
            testAccount.deposit(new BigDecimal("500.00"));
        }
        
        @Test
        @DisplayName("应该成功冻结有效金额")
        void shouldFreezeValidAmount() {
            BigDecimal freezeAmount = new BigDecimal("200.00");
            boolean result = testAccount.freezeAmount(freezeAmount);
            
            assertTrue(result);
            assertEquals(new BigDecimal("500.00"), testAccount.getCurrentBalance());
            assertEquals(new BigDecimal("300.00"), testAccount.getAvailableBalance());
            assertEquals(freezeAmount, testAccount.getFrozenAmount());
        }
        
        @Test
        @DisplayName("应该拒绝冻结超过可用余额的金额")
        void shouldRejectExcessiveFreeze() {
            BigDecimal freezeAmount = new BigDecimal("600.00");
            boolean result = testAccount.freezeAmount(freezeAmount);
            
            assertFalse(result);
            assertEquals(new BigDecimal("500.00"), testAccount.getCurrentBalance());
            assertEquals(new BigDecimal("500.00"), testAccount.getAvailableBalance());
            assertEquals(BigDecimal.ZERO, testAccount.getFrozenAmount());
        }
        
        @Test
        @DisplayName("应该成功解冻金额")
        void shouldUnfreezeAmount() {
            BigDecimal freezeAmount = new BigDecimal("200.00");
            testAccount.freezeAmount(freezeAmount);
            
            boolean result = testAccount.unfreezeAmount(new BigDecimal("100.00"));
            
            assertTrue(result);
            assertEquals(new BigDecimal("500.00"), testAccount.getCurrentBalance());
            assertEquals(new BigDecimal("400.00"), testAccount.getAvailableBalance());
            assertEquals(new BigDecimal("100.00"), testAccount.getFrozenAmount());
        }
        
        @Test
        @DisplayName("应该拒绝解冻超过冻结金额的amount")
        void shouldRejectExcessiveUnfreeze() {
            testAccount.freezeAmount(new BigDecimal("200.00"));
            
            boolean result = testAccount.unfreezeAmount(new BigDecimal("300.00"));
            
            assertFalse(result);
            assertEquals(new BigDecimal("300.00"), testAccount.getAvailableBalance());
            assertEquals(new BigDecimal("200.00"), testAccount.getFrozenAmount());
        }
    }
    
    @Nested
    @DisplayName("定期存款测试")
    class FixedDepositTest {
        
        @BeforeEach
        void prepareAccountWithBalance() {
            testAccount.deposit(new BigDecimal("1000.00"));
        }
        
        @Test
        @DisplayName("应该成功添加定期存款")
        void shouldAddFixedDeposit() {
            FixedDeposit deposit = new FixedDeposit(
                testAccount.getAccountId(), 
                new BigDecimal("500.00"), 
                FixedDeposit.DepositTerm.ONE_YEAR
            );
            
            testAccount.addFixedDeposit(deposit);
            
            assertEquals(1, testAccount.getFixedDeposits().size());
            assertTrue(testAccount.getFixedDeposits().containsKey(deposit.getDepositId()));
        }
        
        @Test
        @DisplayName("应该成功移除定期存款")
        void shouldRemoveFixedDeposit() {
            FixedDeposit deposit = new FixedDeposit(
                testAccount.getAccountId(), 
                new BigDecimal("500.00"), 
                FixedDeposit.DepositTerm.ONE_YEAR
            );
            
            testAccount.addFixedDeposit(deposit);
            FixedDeposit removed = testAccount.removeFixedDeposit(deposit.getDepositId());
            
            assertEquals(deposit, removed);
            assertEquals(0, testAccount.getFixedDeposits().size());
            assertNull(testAccount.removeFixedDeposit(deposit.getDepositId()));
        }
        
        @Test
        @DisplayName("应该正确计算定期存款总额")
        void shouldCalculateTotalFixedDepositAmount() {
            testAccount.addFixedDeposit(new FixedDeposit(
                testAccount.getAccountId(), 
                new BigDecimal("300.00"), 
                FixedDeposit.DepositTerm.SIX_MONTHS
            ));
            
            testAccount.addFixedDeposit(new FixedDeposit(
                testAccount.getAccountId(), 
                new BigDecimal("200.00"), 
                FixedDeposit.DepositTerm.ONE_YEAR
            ));
            
            assertEquals(new BigDecimal("500.00"), testAccount.getTotalFixedDepositAmount());
        }
        
        @Test
        @DisplayName("应该正确计算总余额")
        void shouldCalculateTotalBalance() {
            testAccount.addFixedDeposit(new FixedDeposit(
                testAccount.getAccountId(), 
                new BigDecimal("300.00"), 
                FixedDeposit.DepositTerm.SIX_MONTHS
            ));
            
            assertEquals(new BigDecimal("1300.00"), testAccount.getTotalBalance());
        }
    }
    
    @Nested
    @DisplayName("信用评分测试")
    class CreditScoreTest {
        
        @Test
        @DisplayName("应该成功设置有效信用评分")
        void shouldSetValidCreditScore() {
            testAccount.setCreditScore(750);
            assertEquals(750, testAccount.getCreditScore());
            
            testAccount.setCreditScore(300);
            assertEquals(300, testAccount.getCreditScore());
            
            testAccount.setCreditScore(850);
            assertEquals(850, testAccount.getCreditScore());
        }
        
        @Test
        @DisplayName("应该将信用评分限制在300-850范围内")
        void shouldLimitCreditScoreRange() {
            testAccount.setCreditScore(100); // 低于最小值
            assertEquals(300, testAccount.getCreditScore());
            
            testAccount.setCreditScore(900); // 超过最大值
            assertEquals(850, testAccount.getCreditScore());
        }
        
        @Test
        @DisplayName("应该更新账户更新时间戳")
        void shouldUpdateTimestamp() {
            LocalDateTime beforeUpdate = testAccount.getUpdatedAt();
            
            // 等待一小段时间确保时间戳变化
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            testAccount.setCreditScore(750);
            
            assertTrue(testAccount.getUpdatedAt().isAfter(beforeUpdate) || 
                      testAccount.getUpdatedAt().equals(beforeUpdate.plusNanos(1)));
        }
    }
    
    @Nested
    @DisplayName("账户属性测试")
    class AccountPropertiesTest {
        
        @Test
        @DisplayName("应该正确判断账户活跃度")
        void shouldDetermineAccountActivity() {
            assertTrue(testAccount.isActive());
            assertTrue(testAccount.canTransact());
            
            testAccount.freeze();
            assertFalse(testAccount.isActive());
            assertFalse(testAccount.canTransact());
            
            testAccount.unfreeze();
            assertTrue(testAccount.isActive());
            assertTrue(testAccount.canTransact());
        }
        
        @Test
        @DisplayName("应该正确生成toString信息")
        void shouldGenerateToString() {
            String toString = testAccount.toString();
            
            assertTrue(toString.contains(testAccount.getAccountId().toString()));
            assertTrue(toString.contains(testAccountNumber));
            assertTrue(toString.contains("CHECKING"));
            assertTrue(toString.contains("ACTIVE"));
            assertTrue(toString.contains(testOwnerId.toString()));
        }
        
        @Test
        @DisplayName("应该实现正确的equals和hashCode")
        void shouldImplementEqualsAndHashCode() {
            BankAccount anotherAccount = new BankAccount(
                testOwnerId, 
                "PLAYER", 
                BankAccount.AccountType.SAVINGS, 
                "ANOTHER123"
            );
            
            // 不同账户ID，即使其他属性相同也不相等
            assertNotEquals(testAccount, anotherAccount);
            assertNotEquals(testAccount.hashCode(), anotherAccount.hashCode());
            
            // 同一账户实例相等
            assertEquals(testAccount, testAccount);
            assertEquals(testAccount.hashCode(), testAccount.hashCode());
        }
    }
    
    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTest {
        
        @Test
        @DisplayName("应该处理非常大金额的精确度")
        void shouldHandleVeryLargeAmountsWithPrecision() {
            BigDecimal largeAmount = new BigDecimal("999999999.99");
            testAccount.deposit(largeAmount);
            
            assertEquals(largeAmount, testAccount.getCurrentBalance());
            
            BigDecimal anotherLargeAmount = new BigDecimal("1000000000.01");
            testAccount.deposit(anotherLargeAmount);
            
            assertEquals(new BigDecimal("2000000000.00"), testAccount.getCurrentBalance());
        }
        
        @Test
        @DisplayName("应该处理非常小金额的精确度")
        void shouldHandleVerySmallAmountsWithPrecision() {
            BigDecimal smallAmount = new BigDecimal("0.001");
            testAccount.deposit(smallAmount);
            
            assertEquals(smallAmount, testAccount.getCurrentBalance());
        }
        
        @Test
        @DisplayName("应该处理与其他实体类的集成")
        void shouldHandleIntegrationWithOtherEntities() {
            // 模拟与玩家UUID的集成
            UUID playerId = UUID.randomUUID();
            BankAccount playerAccount = new BankAccount(
                playerId, 
                "PLAYER", 
                BankAccount.AccountType.CHECKING, 
                "P001234567"
            );
            
            assertEquals(playerId, playerAccount.getOwnerId());
            assertEquals("PLAYER", playerAccount.getOwnerType());
            
            // 模拟与组织UUID的集成 
            UUID orgId = UUID.randomUUID();
            BankAccount orgAccount = new BankAccount(
                orgId, 
                "ORGANIZATION", 
                BankAccount.AccountType.CHECKING, 
                "O001234567"
            );
            
            assertEquals(orgId, orgAccount.getOwnerId());
            assertEquals("ORGANIZATION", orgAccount.getOwnerType());
        }
    }
}
