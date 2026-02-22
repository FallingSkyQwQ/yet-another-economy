package com.yae.api.shop;

import com.yae.api.core.YAECore;
import com.yae.api.core.ServiceType;
import com.yae.api.services.EconomyService;
import com.yae.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shop system test class to verify the complete purchase flow.
 */
public class ShopSystemTest {
    
    private final YAECore plugin;
    private final Logger logger;
    
    public ShopSystemTest(@NotNull YAECore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * Run comprehensive shop system tests.
     */
    public void runTests() {
        logger.log(Level.INFO, "=== 开始商店系统测试 ===");
        
        try {
            // Test 1: Service initialization
            testServiceInitialization();
            
            // Test 2: Shop configuration loading
            testConfigurationLoading();
            
            // Test 3: Database integration
            testDatabaseIntegration();
            
            // Test 4: Purchase calculation
            testPurchaseCalculation();
            
            // Test 5: Limit checking
            testPurchaseLimits();
            
            // Test 6: GUI creation (mock)
            testGUICreation();
            
            logger.log(Level.INFO, "=== 商店系统测试完成 ===");
            logger.log(Level.INFO, "所有测试通过！商店系统就绪。");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "商店系统测试失败", e);
        }
    }
    
    /**
     * Test service initialization.
     */
    private void testServiceInitialization() {
        logger.log(Level.INFO, "测试: 服务初始化");
        
        ShopManager shopManager = plugin.getService(ServiceType.SHOP);
        EconomyService economyService = plugin.getService(ServiceType.ECONOMY);
        
        if (shopManager == null) {
            throw new RuntimeException("ShopManager服务未找到");
        }
        
        if (economyService == null) {
            throw new RuntimeException("EconomyService服务未找到");
        }
        
        if (!shopManager.isEnabled()) {
            throw new RuntimeException("ShopManager服务未启用");
        }
        
        logger.log(Level.INFO, "✓ 服务初始化测试通过");
    }
    
    /**
     * Test configuration loading.
     */
    private void testConfigurationLoading() {
        logger.log(Level.INFO, "测试: 配置加载");
        
        ShopManager shopManager = plugin.getService(ServiceType.SHOP);
        
        // Test categories
        var categories = shopManager.getCategories();
        if (categories.isEmpty()) {
            throw new RuntimeException("未找到商店分类");
        }
        
        logger.log(Level.INFO, "✓ 找到 {} 个商店分类", categories.size());
        
        // Test items
        int totalItems = 0;
        for (var category : categories) {
            var items = category.getEnabledItems();
            totalItems += items.size();
        }
        
        if (totalItems == 0) {
            throw new RuntimeException("未找到商店商品");
        }
        
        logger.log(Level.INFO, "✓ 找到 {} 个商店商品", totalItems);
        logger.log(Level.INFO, "✓ 配置加载测试通过");
    }
    
    /**
     * Test database integration.
     */
    private void testDatabaseIntegration() {
        logger.log(Level.INFO, "测试: 数据库集成");
        
        // Test basic connectivity
        var dbManager = plugin.getService(ServiceType.DATABASE);
        if (dbManager == null || !dbManager.isEnabled()) {
            throw new RuntimeException("数据库服务不可用");
        }
        
        // Test shop manager database operations
        ShopManager shopManager = plugin.getService(ServiceType.SHOP);
        var testPlayerId = UUID.randomUUID();
        
        // Test limit checking (should not throw exceptions)
        try {
            var items = shopManager.searchItems("TEST");
            logger.log(Level.INFO, "✓ 数据库搜索功能正常");
            
            for (var item : items) {
                shopManager.isDailyLimitReached(item.getId(), testPlayerId);
                shopManager.isPlayerLimitReached(item.getId(), testPlayerId);
                shopManager.hasEnoughStock(item.getId(), 1);
                shopManager.getAvailableQuantity(item.getId(), testPlayerId);
            }
            logger.log(Level.INFO, "✓ 数据库限制检查功能正常");
            
        } catch (Exception e) {
            throw new RuntimeException("数据库查询失败: " + e.getMessage(), e);
        }
        
        logger.log(Level.INFO, "✓ 数据库集成测试通过");
    }
    
    /**
     * Test purchase calculation.
     */
    private void testPurchaseCalculation() {
        logger.log(Level.INFO, "测试: 购买计算");
        
        var purchaseService = plugin.getService(ServiceType.SHOP);
        if (purchaseService == null) {
            throw new RuntimeException("购买服务不可用");
        }
        
        ShopManager shopManager = plugin.getService(ServiceType.SHOP);
        var testPlayerId = UUID.randomUUID();
        
        // Get a test item
        var items = shopManager.getCategories().get(0).getEnabledItems();
        if (items.isEmpty()) {
            throw new RuntimeException("未找到测试商品");
        }
        
        var testItem = items.get(0);
        var itemId = testItem.getId();
        
        // Test purchase calculation
        var calculation = ((PurchaseService) purchaseService).calculatePurchase(itemId, 1, testPlayerId);
        if (calculation == null) {
            throw new RuntimeException("购买计算返回null");
        }
        
        if (!calculation.isSuccessful()) {
            logger.log(Level.WARNING, "购买计算失败但返回有效结果: {}", calculation.getMessage());
        }
        
        // Verify calculation components
        if (calculation.getTotalPrice() <= 0) {
            throw new RuntimeException("购买价格计算异常");
        }
        
        if (calculation.getTaxAmount() < 0) {
            throw new RuntimeException("税费计算异常");
        }
        
        if (calculation.getFinalPrice() <= calculation.getTotalPrice()) {
            throw new RuntimeException("最终价格计算异常");
        }
        
        logger.log(Level.INFO, "✓ 购买价格计算: 商品 [{}, {}] 基础价格: {}, 税费: {}, 最终价格: {}", 
                  new Object[]{testItem.getDisplayName(), itemId, calculation.getTotalPrice(), 
                              calculation.getTaxAmount(), calculation.getFinalPrice()});
        
        // Test price information
        var priceInfo = ((PurchaseService) purchaseService).getItemPriceInfo(itemId);
        if (priceInfo == null) {
            throw new RuntimeException("价格信息获取失败");
        }
        
        if (priceInfo.getShopPrice() != testItem.getPrice()) {
            throw new RuntimeException("商店价格不匹配");
        }
        
        logger.log(Level.INFO, "✓ 价格信息获取正常");
        logger.log(Level.INFO, "✓ 购买计算测试通过");
    }
    
    /**
     * Test purchase limits.
     */
    private void testPurchaseLimits() {
        logger.log(Level.INFO, "测试: 购买限制");
        
        ShopManager shopManager = plugin.getService(ServiceType.SHOP);
        var testPlayerId = UUID.randomUUID();
        
        int limitedItems = 0;
        for (var category : shopManager.getCategories()) {
            for (var item : category.getEnabledItems()) {
                if (item.hasDailyLimit() || item.hasPlayerLimit() || item.hasStockLimit()) {
                    limitedItems++;
                }
                
                // Test all limit functions
                boolean dailyReached = shopManager.isDailyLimitReached(item.getId(), testPlayerId);
                boolean playerReached = shopManager.isPlayerLimitReached(item.getId(), testPlayerId);
                boolean hasStock = shopManager.hasEnoughStock(item.getId(), 1);
                int available = shopManager.getAvailableQuantity(item.getId(), testPlayerId);
                
                if (available < 0) {
                    throw new RuntimeException("可用数量计算异常: " + available);
                }
                
                if (available == 0 && item.isEnabled()) {
                    logger.log(Level.WARNING, "商品 {} 可购买数量为0但已启用", item.getDisplayName());
                }
            }
        }
        
        logger.log(Level.INFO, "✓ 测试了 {} 个有限制的商品", limitedItems);
        logger.log(Level.INFO, "✓ 购买限制测试通过");
    }
    
    /**
     * Test GUI creation (basic validation).
     */
    private void testGUICreation() {
        logger.log(Level.INFO, "测试: GUI创建");
        
        ShopManager shopManager = plugin.getService(ServiceType.SHOP);
        EconomyService economyService = plugin.getService(ServiceType.ECONOMY);
        
        // Test that we can create GUI instances without exceptions
        try {
            var categoriesGUI = new ShopCategoriesGUI(shopManager, economyService, null);
            logger.log(Level.INFO, "✓ 商店分类GUI创建正常");
            
            var itemsGUI = new ShopItemsGUI(shopManager, economyService, null, 
                                           shopManager.getCategories().get(0));
            logger.log(Level.INFO, "✓ 商品列表GUI创建正常");
            
        } catch (Exception e) {
            throw new RuntimeException("GUI创建失败: " + e.getMessage(), e);
        }
        
        logger.log(Level.INFO, "✓ GUI创建测试通过");
    }
    
    /**
     * Get a summary of the shop system status.
     */
    public String getSystemStatus() {
        try {
            ShopManager shopManager = plugin.getService(ServiceType.SHOP);
            EconomyService economyService = plugin.getService(ServiceType.ECONOMY);
            
            if (shopManager == null || economyService == null) {
                return MessageUtils.color("&c商店系统: 服务未初始化");
            }
            
            StringBuilder status = new StringBuilder();
            status.append(MessageUtils.color("&6=== YAE商店系统状态 ===\\n"));
            status.append(MessageUtils.color("&f商店管理器: ")).append(shopManager.isHealthy() ? "&a正常" : "&c异常").append("\\n");
            status.append(MessageUtils.color("&f经济服务: ")).append(economyService.isHealthy() ? "&a正常" : "&c异常").append("\\n");
            
            var categories = shopManager.getCategories();
            status.append(MessageUtils.color("&f商店分类: &e" + categories.size() + "&f个\\n"));
            
            int totalItems = 0;
            for (var category : categories) {
                totalItems += category.getEnabledItemCount();
            }
            status.append(MessageUtils.color("&f商品总数: &e" + totalItems + "&f件\\n"));
            
            status.append(MessageUtils.color("&f购买税费: &e5%\\n"));
            status.append(MessageUtils.color("&f确认窗口: &e10秒\\n"));
            
            return status.toString();
            
        } catch (Exception e) {
            return MessageUtils.color("&c商店系统: 状态检查失败 - " + e.getMessage());
        }
    }
}
