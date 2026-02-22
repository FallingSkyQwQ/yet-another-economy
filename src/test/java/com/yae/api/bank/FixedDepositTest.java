package com.yae.api.bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * FixedDeposit实体类单元测试
 * 测试定期存款的基本功能和业务逻辑
 */
@DisplayName("FixedDeposit 单元测试")
public class FixedDepositTest {
    
    private FixedDeposit testDeposit;
    private UUID testAccountId;
    private BigDecimal testPrincipal;
    private FixedDeposit.DepositTerm testTerm;
    
    @BeforeEach
    void setUp() {
        testAccountId = UUID.randomUUID();
        testPrincipal = new BigDecimal("1000.00");
        testTerm = FixedDeposit.DepositTerm.ONE_YEAR;
        testDeposit = new FixedDeposit(testAccountId, testPrincipal, testTerm);
    }
    
    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTest {
        
        @Test
        @DisplayName("应该成功创建有效的定期存款")
        void shouldCreateValidFixedDeposit() {
            assertNotNull(testDeposit);
            assertEquals(testAccountId, testDeposit.getAccountId());
            assertEquals(testPrincipal, testDeposit.getPrincipal());
            assertEquals(testTerm, testDeposit.getTerm());
            assertEquals(FixedDeposit.DepositStatus.ACTIVE, testDeposit.getStatus());
            assertEquals(testPrincipal, testDeposit.getCurrentAmount());
            assertEquals(testTerm.getAnnualInterestRate(), testDeposit.getInterestRate());
            assertNotNull(testDeposit.getDepositNumber());
            assertNotNull(testDeposit.getCreatedAt());
            assertNotNull(testDeposit.getMaturityDate());
            assertNotNull(testDeposit.getLastInterestCalculation());
        }
        
        @Test
        @DisplayName("应该正确设置到期日期")
        void shouldSetCorrectMaturityDate() {
            LocalDateTime expectedMaturityDate = testDeposit.getCreatedAt().plusMonths(12);
            assertEquals(expectedMaturityDate.getYear(), testDeposit.getMaturityDate().getYear());
            assertEquals(expectedMaturityDate.getMonth(), testDeposit.getMaturityDate().getMonth());
            assertEquals(expectedMaturityDate.getDayOfMonth(), testDeposit.getMaturityDate().getDayOfMonth());
        }
        
        @Test
        @DisplayName("应该抛出NullPointerException当传入null参数")
        void shouldThrowNullPointerExceptionWhenNullParameters() {
            assertThrows(NullPointerException.class, () -> 
                new FixedDeposit(null, testPrincipal, testTerm));
            
            assertThrows(NullPointerException.class, () -> 
                new FixedDeposit(testAccountId, null, testTerm));
            
            assertThrows(NullPointerException.class, () -> 
                new FixedDeposit(testAccountId, testPrincipal, null));
        }
        
        @Test
        @DisplayName("应该拒绝负本金")
        void shouldRejectNegativePrincipal() {
            assertThrows(IllegalArgumentException.class, () -> 
                new FixedDeposit(testAccountId, new BigDecimal("-1000.00"), testTerm));
        }
    }
    
    @Nested
    @DisplayName("存期枚举测试")
    class DepositTermTest {
        
        @Test
        @DisplayName("应该返回正确的存期属性")
        void shouldReturnCorrectTermProperties() {
            assertEquals(3, FixedDeposit.DepositTerm.THREE_MONTHS.getMonths());
            assertEquals("3个月", FixedDeposit.DepositTerm.THREE_MONTHS.getDisplayName());
            assertEquals(0.0125, FixedDeposit.DepositTerm.THREE_MONTHS.getAnnualInterestRate());
            
            assertEquals(12, FixedDeposit.DepositTerm.ONE_YEAR.getMonths());
            assertEquals("1年", FixedDeposit.DepositTerm.ONE_YEAR.getDisplayName());
            assertEquals(0.02, FixedDeposit.DepositTerm.ONE_YEAR.getAnnualInterestRate());
            
            assertEquals(60, FixedDeposit.DepositTerm.FIVE_YEARS.getMonths());
            assertEquals("5年", FixedDeposit.DepositTerm.FIVE_YEARS.getDisplayName());
            assertEquals(0.035, FixedDeposit.DepositTerm.FIVE_YEARS.getAnnualInterestRate());
        }
        
        @Test
        @DisplayName("应该根据月数找到对应的存期")
        void shouldFindTermByMonths() {
            assertEquals(FixedDeposit.DepositTerm.THREE_MONTHS, 
                        FixedDeposit.DepositTerm.fromMonths(3));
            assertEquals(FixedDeposit.DepositTerm.ONE_YEAR, 
                        FixedDeposit.DepositTerm.fromMonths(12));
            assertEquals(FixedDeposit.DepositTerm.FIVE_YEARS, 
                        FixedDeposit.DepositTerm.fromMonths(60));
            
            // 不存在的月数应该返回默认值
            assertEquals(FixedDeposit.DepositTerm.ONE_YEAR, 
                        FixedDeposit.DepositTerm.fromMonths(999));
        }
        
        @Test
        @DisplayName("应该正确处理所有存期类型")
        void shouldHandleAllTermTypes() {
            FixedDeposit deposit3Months = new FixedDeposit(
                testAccountId, testPrincipal, FixedDeposit.DepositTerm.THREE_MONTHS
            );
            assertEquals(0.0125, deposit3Months.getInterestRate());
            
            FixedDeposit deposit5Years = new FixedDeposit(
                testAccountId, testPrincipal, FixedDeposit.DepositTerm.FIVE_YEARS
            );
            assertEquals(0.035, deposit5Years.getInterestRate());
        }
    }
    
    @Nested
    @DisplayName("到期检查测试")
    class MaturityTest {
        
        @Test
        @DisplayName("应该正确判断存款是否到期")
        void shouldDetermineIfDepositIsMatured() {
            // 新建存款应该未到期
            assertFalse(testDeposit.isMatured());
            
            // 超过到期日应该已到期
            FixedDeposit maturedDeposit = new FixedDeposit(
                testAccountId, testPrincipal, testTerm
            ) {
                @Override
                public LocalDateTime getMaturityDate() {
                    return LocalDateTime.now().minusDays(1); // 昨天到期
                }
            };
            
            assertTrue(maturedDeposit.isMatured());
        }
        
        @Test
        @DisplayName("应该正确计算剩余天数")
        void shouldCalculateRemainingDays() {
            // 新建存款应该还有约365天的剩余时间
            long remainingDays = testDeposit.getRemainingDays();
            assertTrue(remainingDays > 0);
            assertTrue(remainingDays <= 365);
            
            // 到期后剩余天数应该为0
            FixedDeposit maturedDeposit = new FixedDeposit(
                testAccountId, testPrincipal, testTerm
            ) {
                @Override
                public LocalDateTime getMaturityDate() {
                    return LocalDateTime.now().minusDays(1);
                }
            };
            
            assertEquals(0, maturedDeposit.getRemainingDays());
        }
    }
    
    @Nested
    @DisplayName("支取状态测试")
    class WithdrawalStatusTest {
        
        @Test
        @DisplayName("应该正确判断存款是否可以支取")
        void shouldDetermineIfDepositCanBeWithdrawn() {
            // 新建存款应该可以支取
            assertTrue(testDeposit.canWithdraw());
            assertTrue(FixedDeposit.DepositStatus.ACTIVE == testDeposit.getStatus());
            
            // 修改为已支取状态后不能再支取
            testDeposit.setStatus(FixedDeposit.DepositStatus.WITHDRAWN);
            assertFalse(testDeposit.canWithdraw());
            
            // 修改为已关闭状态后不能再支取
            testDeposit.setStatus(FixedDeposit.DepositStatus.CLOSED);
            assertFalse(testDeposit.canWithdraw());
        }
        
        @Test
        @DisplayName("到期存款也应该可以支取")
        void shouldAllowWithdrawalForMaturedDeposit() {
            FixedDeposit maturedDeposit = new FixedDeposit(
                testAccountId, testPrincipal, testTerm
            ) {
                @Override
                public LocalDateTime getMaturityDate() {
                    return LocalDateTime.now().minusDays(1); // 已到期
                }
            };
            
            assertTrue(maturedDeposit.isMatured());
            assertTrue(maturedDeposit.canWithdraw());
        }
    }
    
    @Nested
    @DisplayName("利息计算测试")
    class InterestCalculationTest {
        
        @Test
        @DisplayName("应该正确计算活期利息")
        void shouldCalculateLiveInterest() {
            // 等待一段时间以确保有收益
            try {
                Thread.sleep(10); // 10毫秒，确保时间差
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            BigDecimal interest = testDeposit.calculateInterest();
            
            // 活期利息应该大于等于0（可能为0如果时间差太小）
            assertTrue(interest.compareTo(BigDecimal.ZERO) >= 0);
            
            // 当前金额应该等于本金加上或减去利息
            assertEquals(testPrincipal.add(interest), testDeposit.getCurrentAmount());
        }
        
        @Test
        @DisplayName("应该正确处理已关闭存款的利息计算")
        void shouldHandleInterestCalculationForClosedDeposit() {
            testDeposit.setStatus(FixedDeposit.DepositStatus.CLOSED);
            
            BigDecimal interest = testDeposit.calculateInterest();
            
            // 已关闭存款不应产生利息
            assertEquals(BigDecimal.ZERO, interest);
        }
        
        @Test
        @DisplayName("应该正确计算已获利息")
        void shouldCalculateEarnedInterest() {
            // 先计算一些利息
            testDeposit.calculateInterest();
            
            BigDecimal earnedInterest = testDeposit.getInterestEarned();
            BigDecimal expectedInterest = testDeposit.getCurrentAmount().subtract(testPrincipal);
            
            assertEquals(expectedInterest, earnedInterest);
            assertTrue(earnedInterest.compareTo(BigDecimal.ZERO) >= 0);
        }
        
        @Test
        @DisplayName("应该正确更新上次利息计算时间")
        void shouldUpdateLastInterestCalculationTime() {
            LocalDateTime beforeCalculation = testDeposit.getLastInterestCalculation();
            
            try {
                Thread.sleep(10); // 确保时间戳变化
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            testDeposit.calculateInterest();
            
            LocalDateTime afterCalculation = testDeposit.getLastInterestCalculation();
            assertTrue(afterCalculation.isAfter(beforeCalculation) || 
                      afterCalculation.equals(beforeCalculation.plusNanos(1)));
        }
    }
    
    @Nested
    @DisplayName("提前支取测试")
    class EarlyWithdrawalTest {
        
        @Test
        @DisplayName("应该成功提前支取并扣除罚金")
        void shouldSuccessfullyEarlyWithdrawWithPenalty() {
            // 等待一段时间以积累一些利息
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            BigDecimal beforeAmount = testDeposit.getCurrentAmount();
            BigDecimal penaltyRate = new BigDecimal("0.05"); // 5%罚金
            
            boolean result = testDeposit.earlyWithdrawal(penaltyRate.doubleValue());
            
            assertTrue(result);
            assertEquals(FixedDeposit.DepositStatus.WITHDRAWN, testDeposit.getStatus());
            assertNotNull(testDeposit.getClosedAt());
            
            // 验证罚金计算：最终金额 = 当前金额 * (1 - 罚金率)
            BigDecimal expectedAmount = beforeAmount.multiply(BigDecimal.valueOf(0.95));
            assertEquals(expectedAmount, testDeposit.getCurrentAmount());
        }
        
        @Test
        @DisplayName("应该拒绝不可支取状态的提前支取")
        void shouldRejectEarlyWithdrawalForNonWithdrawableStatus() {
            testDeposit.setStatus(FixedDeposit.DepositStatus.WITHDRAWN);
            
            boolean result = testDeposit.earlyWithdrawal(0.05);
            
            assertFalse(result);
            
            // 设置已到期状态也应该成功（到期存款可以支取）
            FixedDeposit maturedDeposit = new FixedDeposit(testAccountId, testPrincipal, testTerm) {
                @Override
                public boolean isMatured() {
                    return true;
                }
            };
            
            boolean maturedResult = maturedDeposit.earlyWithdrawal(0.05);
            assertTrue(maturedResult);
        }
    }
    
    @Nested
    @DisplayName("到期支取测试")
    class MaturityWithdrawalTest {
        
        @Test
        @DisplayName("应该成功到期支取")
        void shouldSuccessfullyWithdrawAtMaturity() {
            FixedDeposit maturedDeposit = new FixedDeposit(testAccountId, testPrincipal, testTerm) {
                @Override
                public boolean isMatured() {
                    return true;
                }
            };
            
            boolean result = maturedDeposit.withdrawAtMaturity();
            
            assertTrue(result);
            assertEquals(FixedDeposit.DepositStatus.WITHDRAWN, maturedDeposit.getStatus());
            assertNotNull(maturedDeposit.getClosedAt());
        }
        
        @Test
        @DisplayName("应该拒绝未到期存款的到期支取")
        void shouldRejectMaturityWithdrawalForNonMaturedDeposit() {
            // Regular deposit is not matured yet
            boolean result = testDeposit.withdrawAtMaturity();
            
            assertFalse(result);
            assertEquals(FixedDeposit.DepositStatus.ACTIVE, testDeposit.getStatus());
        }
        
        @Test
        @DisplayName("应该拒绝已关闭存款的到期支取")
        void shouldRejectMaturityWithdrawalForClosedDeposit() {
            testDeposit.setStatus(FixedDeposit.DepositStatus.CLOSED);
            
            boolean result = testDeposit.withdrawAtMaturity();
            
            assertFalse(result);
        }
    }
    
    @Nested
    @DisplayName("属性访问测试")
    class PropertyAccessTest {
        
        @Test
        @DisplayName("应该正确访问所有基本属性")
        void shouldAccessAllBasicProperties() {
            assertEquals(testAccountId, testDeposit.getAccountId());
            assertNotNull(testDeposit.getDepositId());
            assertNotNull(testDeposit.getDepositNumber());
            assertEquals(testPrincipal, testDeposit.getPrincipal());
            assertEquals(testPrincipal, testDeposit.getCurrentAmount());
            assertEquals(testTerm, testDeposit.getTerm());
            assertEquals(testTerm.getAnnualInterestRate(), testDeposit.getInterestRate());
            assertEquals(FixedDeposit.DepositStatus.ACTIVE, testDeposit.getStatus());
            assertNotNull(testDeposit.getCreatedAt());
            assertNotNull(testDeposit.getMaturityDate());
            assertNotNull(testDeposit.getLastInterestCalculation());
            assertNull(testDeposit.getClosedAt());
        }
        
        @Test
        @DisplayName("应该正确设置状态")
        void shouldSetStatusCorrectly() {
            // Test WITHDRAWN status
            testDeposit.setStatus(FixedDeposit.DepositStatus.WITHDRAWN);
            assertEquals(FixedDeposit.DepositStatus.WITHDRAWN, testDeposit.getStatus());
            assertNotNull(testDeposit.getClosedAt());
            
            // Test CLOSED status
            FixedDeposit newDeposit = new FixedDeposit(testAccountId, testPrincipal, testTerm);
            newDeposit.setStatus(FixedDeposit.DepositStatus.CLOSED);
            assertEquals(FixedDeposit.DepositStatus.CLOSED, newDeposit.getStatus());
            assertNotNull(newDeposit.getClosedAt());
        }
    }
    
    @Nested
    @DisplayName("对象方法测试")
    class ObjectMethodsTest {
        
        @Test
        @DisplayName("应该生成有意义的toString信息")
        void shouldGenerateMeaningfulToString() {
            String toString = testDeposit.toString();
            
            assertTrue(toString.contains(testDeposit.getDepositId().toString()));
            assertTrue(toString.contains(testAccountId.toString()));
            assertTrue(toString.contains(testPrincipal.toString()));
            assertTrue(toString.contains(String.valueOf(testTerm.getAnnualInterestRate())));
            assertTrue(toString.contains("ACTIVE"));
        }
        
        @Test
        @DisplayName("应该实现正确的equals和hashCode")
        void shouldImplementEqualsAndHashCode() {
            FixedDeposit anotherDeposit = new FixedDeposit(testAccountId, testPrincipal, testTerm);
            
            // 不同存款ID，即使其他属性相同也不相等
            assertNotEquals(testDeposit, anotherDeposit);
            assertNotEquals(testDeposit.hashCode(), anotherDeposit.hashCode());
            
            // 同一存款实例相等
            assertEquals(testDeposit, testDeposit);
            assertEquals(testDeposit.hashCode(), testDeposit.hashCode());
            
            // null和不同类不相等
            assertNotEquals(testDeposit, null);
            assertNotEquals(testDeposit, "not a deposit");
        }
    }
    
    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTest {
        
        @Test
        @DisplayName("应该处理非常小本金的利息计算")
        void shouldHandleVerySmallPrincipalInterestCalculation() {
            BigDecimal smallPrincipal = new BigDecimal("0.01");
            FixedDeposit smallDeposit = new FixedDeposit(testAccountId, smallPrincipal, testTerm);
            
            try {
                Thread.sleep(10); // 确保有时间差
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            BigDecimal interest = smallDeposit.calculateInterest();
            
            assertTrue(interest.compareTo(BigDecimal.ZERO) >= 0);
            assertEquals(smallPrincipal.add(interest), smallDeposit.getCurrentAmount());
        }
        
        @Test
        @DisplayName("应该正确处理刚好到期的边界情况")
        void shouldHandleExactMaturityBoundary() {
            FixedDeposit exactMaturityDeposit = new FixedDeposit(testAccountId, testPrincipal, testTerm) {
                @Override
                public LocalDateTime getMaturityDate() {
                    return LocalDateTime.now(); // 刚好是现在到期
                }
            };
            
            assertTrue(exactMaturityDeposit.isMatured());
            assertEquals(0, exactMaturityDeposit.getRemainingDays());
        }
        
        @Test
        @DisplayName("应该正确处理接近零的利率")
        void shouldHandleNearZeroInterestRate() {
            BigDecimal largePrincipal = new BigDecimal("1000000.00");
            FixedDeposit largeDeposit = new FixedDeposit(testAccountId, largePrincipal, testTerm) {
                @Override
                public double getInterestRate() {
                    return 0.0001; // 非常小的利率
                }
            };
            
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            BigDecimal interest = largeDeposit.calculateInterest();
            
            // 即使是100万本金，在0.01%年利率下10毫秒的利息也应该非常小
            assertTrue(interest.compareTo(new BigDecimal("0.01")) < 0);
        }
    }
}
