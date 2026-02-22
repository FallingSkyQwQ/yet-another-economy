package com.yae.api.bank;

import com.yae.api.core.Service;
import com.yae.api.core.ServiceType;
import com.yae.api.core.YAECore;
import com.yae.api.core.config.Configuration;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 利息计算引擎
 * 提供复利和单利计算，支持活期、定期、罚息等各类利息计算
 */
public class InterestCalculator implements Service {
    
    private final YAECore plugin;
    private final Logger logger;
    private final AtomicBoolean enabled;
    private final AtomicBoolean healthy;
    private final int priority;
    private final ServiceType serviceType;
    
    // 配置
    private Configuration configuration;
    
    // 利率配置
    private double currentAccountRate;      // 活期存款利率
    private double savingsAccountRate;      // 储蓄存款利率
    private Map<Integer, Double> termRates; // 定期存款利率(月数->年利率)
    private double penaltyRate;             // 提前支取罚息率
    private double compoundFrequency;       // 复利计算频次(月)
    
    // 计算精度
    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final MathContext MATH_CONTEXT = new MathContext(15, ROUNDING_MODE);
    
    public InterestCalculator(@NotNull YAECore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.logger = plugin.getLogger();
        this.enabled = new AtomicBoolean(false);
        this.healthy = new AtomicBoolean(true);
        this.priority = 220; // 比DepositService稍高
        this.serviceType = ServiceType.RISK; // 风险控制服务
        this.termRates = new ConcurrentHashMap<>();
    }
    
    @Override
    @NotNull
    public String getName() {
        return "InterestCalculator";
    }
    
    @Override
    @NotNull
    public ServiceType getType() {
        return serviceType;
    }
    
    @Override
    public boolean dependsOn(@NotNull ServiceType serviceType) {
        return false; // 无依赖
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled.get();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }
    
    @Override
    public boolean isHealthy() {
        return healthy.get();
    }
    
    @Override
    public boolean initialize() {
        logger.info("Initializing InterestCalculator...");
        
        try {
            // 加载配置
            loadConfiguration();
            
            // 验证利率参数合理性
            validateInterestRates();
            
            enabled.set(true);
            healthy.set(true);
            
            logger.info("InterestCalculator initialized successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to initialize InterestCalculator: " + e.getMessage());
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public boolean reload() {
        logger.info("Reloading InterestCalculator...");
        
        try {
            // 重新加载配置
            loadConfiguration();
            
            // 重新验证利率参数
            validateInterestRates();
            
            logger.info("InterestCalculator reloaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to reload InterestCalculator: " + e.getMessage());
            healthy.set(false);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down InterestCalculator...");
        try {
            enabled.set(false);
            termRates.clear();
            logger.info("InterestCalculator shutdown completed");
        } catch (Exception e) {
            logger.severe("Error during InterestCalculator shutdown: " + e.getMessage());
        }
    }
    
    /**
     * 加载配置
     */
    private void loadConfiguration() {
        this.configuration = plugin.getMainConfiguration();
        
        var bankingConfig = configuration.getFeatures().getBanking();
        
        // 基础利率配置
        this.currentAccountRate = bankingConfig.getDefaultInterestRate();
        this.savingsAccountRate = bankingConfig.getSavingsInterestRate();
        this.penaltyRate = bankingConfig.getEarlyWithdrawalPenaltyRate();
        this.compoundFrequency = 12.0; // 按月复利
        
        // 定期存款利率
        this.termRates.clear();
        for (FixedDeposit.DepositTerm term : FixedDeposit.DepositTerm.values()) {
            this.termRates.put(term.getMonths(), bankingConfig.getTermInterestRate(term.getMonths()));
        }
    }
    
    /**
     * 验证利率参数合理性
     */
    private void validateInterestRates() {
        if (currentAccountRate < 0 || currentAccountRate > 1) {
            throw new IllegalArgumentException("Current account interest rate must be between 0 and 1");
        }
        
        if (savingsAccountRate < 0 || savingsAccountRate > 1) {
            throw new IllegalArgumentException("Savings account interest rate must be between 0 and 1");
        }
        
        if (penaltyRate < 0 || penaltyRate > 1) {
            throw new IllegalArgumentException("Penalty rate must be between 0 and 1");
        }
        
        for (Map.Entry<Integer, Double> entry : termRates.entrySet()) {
            double rate = entry.getValue();
            if (rate < 0 || rate > 1) {
                throw new IllegalArgumentException("Term interest rate for " + entry.getKey() + " months must be between 0 and 1");
            }
        }
        
        // 验证利率梯度合理性
        int[] months = {3, 6, 12, 24, 36, 60};
        for (int i = 1; i < months.length; i++) {
            double shortRate = termRates.getOrDefault(months[i-1], 0.0);
            double longRate = termRates.getOrDefault(months[i], 0.0);
            if (longRate < shortRate) {
                logger.warning(String.format("Long-term rate (%.2f%% for %d months) is lower than short-term rate (%.2f%% for %d months)",
                        longRate * 100, months[i], shortRate * 100, months[i-1]));
            }
        }
    }
    
    /**
     * 计算复利利息
     * 使用标准复利公式：A = P * (1 + r/n)^(nt)
     * 其中：P = 本金，r = 年利率，n = 每年复利次数，t = 时间（年）
     * 
     * @param principal 本金
     * @param annualRate 年利率（如0.05表示5%）
     * @param days 存款天数
     * @param compoundFrequency 每年复利次数（如12表示月复利）
     * @return 到期本息和
     */
    public BigDecimal calculateCompoundInterest(@NotNull BigDecimal principal, 
                                               @NotNull BigDecimal annualRate, 
                                               int days, 
                                               int compoundFrequency) {
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return principal;
        }
        
        if (annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            return principal;
        }
        
        if (days <= 0) {
            return principal;
        }
        
        try {
            // 计算年数
            double years = days / 365.25;
            
            // 将利率转换为小数
            BigDecimal rate = annualRate.divide(BigDecimal.valueOf(compoundFrequency), SCALE, ROUNDING_MODE);
            
            // 计算复利率
            double compoundRate = Math.pow(1 + rate.doubleValue(), compoundFrequency * years);
            
            // 计算最终本息和
            BigDecimal finalAmount = principal.multiply(BigDecimal.valueOf(compoundRate));
            
            // 减去本金得到利息
            return finalAmount.subtract(principal).setScale(2, ROUNDING_MODE);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating compound interest", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 计算单利利息
     * 使用单利公式：I = P * r * t
     * 其中：P = 本金，r = 年利率，t = 时间（年）
     */
    public BigDecimal calculateSimpleInterest(@NotNull BigDecimal principal,
                                            @NotNull BigDecimal annualRate,
                                            int days) {
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        if (annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        
        try {
            // 计算年数
            double years = days / 365.25;
            
            // 计算利息
            BigDecimal interest = principal
                    .multiply(annualRate)
                    .multiply(BigDecimal.valueOf(years));
            
            return interest.setScale(2, ROUNDING_MODE);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating simple interest", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 计算提前支取罚息
     * 使用惩罚利率计算
     */
    public BigDecimal calculateEarlyWithdrawalPenalty(@NotNull BigDecimal principal,
                                                    @NotNull BigDecimal earnedInterest,
                                                    double penaltyRate) {
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        if (earnedInterest.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        if (penaltyRate <= 0) {
            return BigDecimal.ZERO;
        }
        
        try {
            // 计算惩罚金额
            BigDecimal penalty = earnedInterest.multiply(BigDecimal.valueOf(penaltyRate));
            
            // 确保惩罚不超过已获利息
            return penalty.min(earnedInterest);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating early withdrawal penalty", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 获取活期账户利率
     */
    public double getCurrentAccountRate() {
        return currentAccountRate;
    }
    
    /**
     * 获取储蓄账户利率
     */
    public double getSavingsAccountRate() {
        return savingsAccountRate;
    }
    
    /**
     * 获取定期存款利率（根据月数）
     */
    public double getTermInterestRate(int months) {
        return termRates.getOrDefault(months, currentAccountRate);
    }
    
    /**
     * 获取定期存款利率（根据枚举值）
     */
    public double getTermInterestRate(FixedDeposit.DepositTerm term) {
        return termRates.getOrDefault(term.getMonths(), currentAccountRate);
    }
    
    /**
     * 获取提前支取罚息率
     */
    public double getPenaltyRate() {
        return penaltyRate;
    }
    
    /**
     * 计算分期付款的每朋利息（用于贷款场景）
     */
    public BigDecimal calculateMonthlyPayment(@NotNull BigDecimal principal,
                                            @NotNull BigDecimal annualRate,
                                            int totalMonths) {
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        if (annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            return principal.divide(BigDecimal.valueOf(totalMonths), 2, ROUNDING_MODE);
        }
        
        if (totalMonths <= 0) {
            return BigDecimal.ZERO;
        }
        
        try {
            // 月利率
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), SCALE, ROUNDING_MODE);
            
            // 月还款额公式：M = P * [r(1+r)^n] / [(1+r)^n - 1]
            double rate = monthlyRate.doubleValue();
            double numerator = rate * Math.pow(1 + rate, totalMonths);
            double denominator = Math.pow(1 + rate, totalMonths) - 1;
            double monthlyPayment = principal.doubleValue() * (numerator / denominator);
            
            return BigDecimal.valueOf(monthlyPayment).setScale(2, ROUNDING_MODE);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating monthly payment", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 验证利率参数
     */
    public boolean validateRate(double rate) {
        return rate >= 0 && rate <= 1;
    }
    
    /**
     * 计算实际年利率(APR)
     */
    public double calculateAPR(double nominalRate, int periodsPerYear) {
        if (nominalRate < 0 || periodsPerYear <= 0) {
            return 0.0;
        }
        
        try {
            // APR = (1 + r/n)^n - 1
            double apr = Math.pow(1 + nominalRate / periodsPerYear, periodsPerYear) - 1;
            return apr;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating APR", e);
            return 0.0;
        }
    }
    
    /**
     * 获取最优惠利率（基于信用评分）
     */
    public double getPrimeRate(int creditScore) {
        if (creditScore < 300 || creditScore > 850) {
            return currentAccountRate;
        }
        
        // 基于信用评分调整利率（评分越高，利率越低）
        double adjustment = 0.0;
        
        if (creditScore >= 800) {
            adjustment = -0.005; // -0.5%
        } else if (creditScore >= 740) {
            adjustment = -0.003; // -0.3%
        } else if (creditScore >= 670) {
            adjustment = 0.0; // 基准利率
        } else if (creditScore >= 580) {
            adjustment = 0.005; // +0.5%
        } else {
            adjustment = 0.01; // +1.0%
        }
        
        double primeRate = currentAccountRate + adjustment;
        return Math.max(0.001, Math.min(0.2, primeRate)); // 确保在合理范围内
    }
    
    // 服务配置接口
    private com.yae.api.core.ServiceConfig config;
    
    @Override
    public com.yae.api.core.ServiceConfig getConfig() {
        return config;
    }
    
    @Override
    public void setConfig(com.yae.api.core.ServiceConfig config) {
        this.config = config;
    }
}
