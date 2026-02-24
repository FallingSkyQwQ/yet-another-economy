package com.yae.api.loan.gui;

import com.yae.api.loan.LoanService;
import com.yae.api.loan.SimpleLoan;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Simplified 3-step loan application GUI
 * Basic implementation without complex dependencies
 */
@SuppressWarnings("deprecation")
public class LoanApplicationGUI {
    
    private final com.yae.YetAnotherEconomy plugin;
    private final LoanService loanService;
    
    // Simple GUI state tracking
    private final Map<UUID, LoanApplicationData> applications = new HashMap<>();
    
    // GUI constants
    private static final String TITLE_PREFIX = "§6贷款申请";
    private static final int GUI_SIZE = 27;
    
    public LoanApplicationGUI(com.yae.YetAnotherEconomy plugin) {
        this.plugin = plugin;
        this.loanService = (LoanService) plugin.getService(com.yae.api.core.ServiceType.LOAN);
    }
    
    /**
     * Open the basic loan GUI for a player
     */
    public void openLoanGUI(Player player) {
        // Create new application data
        LoanApplicationData appData = new LoanApplicationData();
        applications.put(player.getUniqueId(), appData);
        
        // Open step 1
        openStep1(player);
    }
    
    /**
     * Step 1: Amount and Term Selection
     */
    private void openStep1(Player player) {
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, TITLE_PREFIX + " - 步骤1: 金额和期限");
        
        // Instructions
        ItemStack infoItem = createItem(Material.PAPER, "§e步骤1: 选择金额和期限", 
            "§7请选择您的贷款金额和期限", "§7目前仅支持抵押贷款", "§7年利率固定8%");
        inventory.setItem(4, infoItem);
        
        // Amount options
        double[] amounts = {10000, 50000, 100000, 200000};
        for (int i = 0; i < amounts.length; i++) {
            ItemStack amountItem = createItem(Material.GOLD_INGOT, 
                String.format("§6¥%.0f", amounts[i]),
                "§7点击选择此金额");
            inventory.setItem(9 + i, amountItem);
        }
        
        // Term options
        int[] terms = {12, 24, 36, 60};
        for (int i = 0; i < terms.length; i++) {
            ItemStack termItem = createItem(Material.CLOCK, 
                String.format("§b%d个月", terms[i]),
                "§7点击选择此期限");
            inventory.setItem(18 + i, termItem);
        }
        
        player.openInventory(inventory);
    }
    
    /**
     * Step 2: Confirmation
     */
    private void openStep2(Player player) {
        LoanApplicationData appData = applications.get(player.getUniqueId());
        if (appData == null || !appData.isComplete()) {
            player.sendMessage("§c请先完成步骤1");
            return;
        }
        
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, TITLE_PREFIX + " - 步骤2: 确认");
        
        // Application summary
        double totalAmount = calculateTotalAmount(appData.amount, appData.term, 8.0);
        double monthlyPayment = totalAmount / appData.term;
        
        ItemStack summaryItem = createItem(Material.BOOK, "§6申请信息确认",
            String.format("§f贷款类型: §e抵押贷款"),
            String.format("§f贷款金额: §a¥%.2f", appData.amount),
            String.format("§f贷款期限: §b%d个月", appData.term),
            String.format("§f年利率: §88.0%%"),
            String.format("§f总还款额: §6¥%.2f", totalAmount),
            String.format("§f月供: §a¥%.2f", monthlyPayment),
            "",
            "§e请确认以上信息是否正确");
        inventory.setItem(4, summaryItem);
        
        // Confirm button
        ItemStack confirmItem = createItem(Material.GREEN_CONCRETE, "§a✅ 确认申请",
            "§7点击提交贷款申请");
        inventory.setItem(11, confirmItem);
        
        // Cancel button
        ItemStack cancelItem = createItem(Material.RED_CONCRETE, "§c❌ 取消申请",
            "§7点击取消并返回");
        inventory.setItem(15, cancelItem);
        
        player.openInventory(inventory);
    }
    
    /**
     * Step 3: Success/Result
     */
    private void openStep3(Player player, boolean success, String loanId) {
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, TITLE_PREFIX + " - 步骤3: 结果");
        
        if (success) {
            ItemStack successItem = createItem(Material.EMERALD_BLOCK, "§a✅ 申请成功！",
                "§f贷款ID: §e" + loanId,
                "§7您的贷款申请已成功提交",
                "§7系统将自动处理您的申请",
                "",
                "§a点击关闭此界面");
            inventory.setItem(13, successItem);
        } else {
            ItemStack failItem = createItem(Material.RED_CONCRETE, "§c❌ 申请失败",
                "§7申请处理过程中出现错误",
                "§7请稍后重试",
                "",
                "§c点击关闭此界面");
            inventory.setItem(13, failItem);
        }
        
        player.openInventory(inventory);
    }
    
    /**
     * Handle inventory click events
     */
    public void handleInventoryClick(Player player, ItemStack clickedItem, String title) {
        if (clickedItem == null || !title.startsWith(TITLE_PREFIX)) return;
        
        UUID playerId = player.getUniqueId();
        
        if (title.contains("步骤1")) {
            handleStep1Click(player, clickedItem);
        } else if (title.contains("步骤2")) {
            handleStep2Click(player, clickedItem);
        } else if (title.contains("步骤3")) {
            player.closeInventory();
        }
    }
    
    private void handleStep1Click(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.GOLD_INGOT) {
            // Amount selection
            String displayName = clickedItem.getItemMeta().getDisplayName();
            double amount = Double.parseDouble(displayName.replaceAll("[^0-9.]", ""));
            
            LoanApplicationData appData = applications.get(player.getUniqueId());
            if (appData != null) {
                appData.amount = amount;
                appData.amountSet = true;
                checkAndProceed(player);
            }
        } else if (clickedItem.getType() == Material.CLOCK) {
            // Term selection
            String displayName = clickedItem.getItemMeta().getDisplayName();
            int term = Integer.parseInt(displayName.replaceAll("[^0-9]", ""));
            
            LoanApplicationData appData = applications.get(player.getUniqueId());
            if (appData != null) {
                appData.term = term;
                appData.termSet = true;
                checkAndProceed(player);
            }
        }
    }
    
    private void handleStep2Click(Player player, ItemStack clickedItem) {
        String displayName = clickedItem.getItemMeta().getDisplayName();
        
        if (displayName.contains("确认申请")) {
            submitApplication(player);
        } else if (displayName.contains("取消申请")) {
            applications.remove(player.getUniqueId());
            player.sendMessage("§c贷款申请已取消");
            player.closeInventory();
        }
    }
    
    private void checkAndProceed(Player player) {
        LoanApplicationData appData = applications.get(player.getUniqueId());
        if (appData != null && appData.isComplete()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openStep2(player);
            }, 2L);
        }
    }
    
    private void submitApplication(Player player) {
        LoanApplicationData appData = applications.get(player.getUniqueId());
        if (appData == null || !appData.isComplete()) {
            player.sendMessage("§c申请数据不完整");
            return;
        }
        
        try {
            // Create loan using the service
            SimpleLoan loan = loanService.createLoan(player.getUniqueId(), appData.amount, appData.term, 8.0);
            
            applications.remove(player.getUniqueId());
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openStep3(player, true, loan.getLoanId());
            }, 2L);
            
        } catch (Exception e) {
            applications.remove(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openStep3(player, false, null);
            }, 2L);
        }
    }
    
    // Helper methods
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(line);
        }
        meta.setLore(loreList);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private double calculateTotalAmount(double principal, int termMonths, double annualRate) {
        return principal + (principal * annualRate / 100 * termMonths / 12);
    }
    
    /**
     * Simple data class for loan application
     */
    private static class LoanApplicationData {
        double amount;
        int term;
        boolean amountSet = false;
        boolean termSet = false;
        
        boolean isComplete() {
            return amountSet && termSet && amount > 0 && term > 0;
        }
    }
}
