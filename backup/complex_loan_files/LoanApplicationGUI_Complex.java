package com.yae.api.loan.gui;

import com.yae.YetAnotherEconomy;
import com.yae.api.credit.LoanType;
import com.yae.api.gui.CustomGUI;
import com.yae.api.gui.GUIConfig;
import com.yae.api.gui.GUIManager;
import com.yae.api.gui.ItemBuilder;
import com.yae.api.loan.*;
import com.yae.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Professional 5-step loan application GUI
 * Guides users through complete application process with validation
 */
public class LoanApplicationGUI extends CustomGUI {
    
    private static final int GUI_SIZE = 54;
    private static final String TITLE_PREFIX = "&6贷款申请";
    
    private final LoanApplicationService applicationService;
    private final Map<UUID, LoanApplication> applicationCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentStep = new ConcurrentHashMap<>();
    
    // Step definitions
    public enum ApplicationStep {
        STEP_1_ELIGIBILITY(1, "资格条件检查", "验证信用分数和账户状态"),
        STEP_2_AMOUNT_TERM(2, "金额和期限选择", "选择贷款金额和还款期限"), 
        STEP_3_LOAN_TYPE(3, "贷款类型选择", "选择适合的贷款类型"),
        STEP_4_COLLATERAL(4, "抵押物评估", "评估属抵押物价值（如需要）"),
        STEP_5_CONFIRMATION(5, "最终确认", "审核所有信息和提交申请");
        
        private final int stepNumber;
        private final String title;
        private final String description;
        
        ApplicationStep(int stepNumber, String title, String description) {
            this.stepNumber = stepNumber;
            this.title = title;
            this.description = description;
        }
        
        public int getStepNumber() { return stepNumber; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        
        public String getFormattedTitle() {
            return String.format("第%d步: %s", stepNumber, title);
        }
    }
    
    public LoanApplicationGUI(YetAnotherEconomy plugin) {
        super(plugin, GUI_SIZE, TITLE_PREFIX);
        this.applicationService = plugin.getService(LoanApplicationService.class);
    }
    
    /**
     * Open the loan application GUI for a player
     */
    public void openApplicationGUI(Player player) {
        if (applicationService == null) {
            player.sendMessage(Messages.getMessage("loan.application.service_unavailable"));
            return;
        }
        
        // Reset or create new application
        applicationCache.remove(player.getUniqueId());
        currentStep.put(player.getUniqueId(), 1);
        
        // Create new application
        LoanApplication application = new LoanApplication(player.getUniqueId());
        applicationCache.put(player.getUniqueId(), application);
        
        openGUI(player);
        Messages.sendMessage(player, "loan.application.gui_opened");
    }
    
    @Override
    protected void onOpen(Player player) {
        super.onOpen(player);
        // Create step-specific interface
        int step = currentStep.getOrDefault(player.getUniqueId(), 1);
        createStepInterface(player, ApplicationStep.values()[step - 1]);
    }
    
    @Override
    protected void handleItemClick(Player player, int slot, ItemStack item, ClickType clickType) {
        UUID playerId = player.getUniqueId();
        int step = currentStep.getOrDefault(playerId, 1);
        ApplicationStep currentStepEnum = ApplicationStep.values()[step - 1];
        
        switch (currentStepEnum) {
            case STEP_1_ELIGIBILITY:
                handleStep1Click(player, slot, item, clickType);
                break;
            case STEP_2_AMOUNT_TERM:
                handleStep2Click(player, slot, item, clickType);
                break;
            case STEP_3_LOAN_TYPE:
                handleStep3Click(player, slot, item, clickType);
                break;
            case STEP_4_COLLATERAL:
                handleStep4Click(player, slot, item, clickType);
                break;
            case STEP_5_CONFIRMATION:
                handleStep5Click(player, slot, item, clickType);
                break;
        }
    }
    
    // === Step 1: Eligibility Check ===
    private void createStepInterface(Player player, ApplicationStep step) {
        LoanApplication application = applicationCache.get(player.getUniqueId());
        if (application == null) return;
        
        clearInventory();
        setTitle(String.format("%s - %s", TITLE_PREFIX, step.getFormattedTitle()));
        
        switch (step) {
            case STEP_1_ELIGIBILITY:
                createEligibilityInterface(player, application);
                break;
            case STEP_2_AMOUNT_TERM:
                createAmountTermInterface(player, application);
                break;
            case STEP_3_LOAN_TYPE:
                createLoanTypeInterface(player, application);
                break;
            case STEP_4_COLLATERAL:
                createCollateralInterface(player, application);
                break;
            case STEP_5_CONFIRMATION:
                createConfirmationInterface(player, application);
                break;
        }
    }
    
    private void createEligibilityInterface(Player player, LoanApplication application) {
        setItem(13, new ItemBuilder(Material.BOOK)
            .setName("&6资格条件检查")
            .setLore(
                "",
                "&7我们将在这一步检查：",
                "&f•您的信用评分情况",
                "&f•账户状态是否良好",
                "&f•是否符合所选贷款类型要求",
                "",
                "&e点击开始检查，查看您的贷款资格",
                ""
            ).build());
        
        // Start eligibility check button
        setItem(31, new ItemBuilder(Material.EMERALD_BLOCK)
            .setName("&a开始资格检查")
            .setLore(
                "",
                "&7点击开始检查您的贷款申请资格",
                "",
                "&e检查内容包括：",
                "• &f信用评分要求",
                "• &f现有贷款状态",
                "• &f账户验证状态",
                ""
            ).build());
        
        // Cancel button
        setItem(45, new ItemBuilder(Material.BARRIER)
            .setName("&c取消申请")
            .setLore("&7取消当前的贷款申请").build());
        
        // Navigation
        addNavigationButtons(player, ApplicationStep.STEP_1_ELIGIBILITY);
    }
    
    private void handleStep1Click(Player player, int slot, ItemStack item, ClickType clickType) {
        if (item.getType() == Material.EMERALD_BLOCK && slot == 31) {
            // Start eligibility check
            startEligibilityCheck(player);
        } else if (item.getType() == Material.BARRIER && slot == 45) {
            cancelApplication(player);
        }
    }
    
    private void startEligibilityCheck(Player player) {
        LoanApplication application = applicationCache.get(player.getUniqueId());
        if (application == null || application.getLoanType() == null) {
            Messages.sendMessage(player, "loan.application.invalid_type");
            return;
        }
        
        player.sendMessage(Messages.getMessage("loan.application.checking_eligibility"));
        
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            LoanApplicationService.EligibilityResult result = applicationService.checkEligibility(
                player.getUniqueId(), application.getLoanType()
            );
            
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                displayEligibilityResult(player, result);
            });
        });
    }
    
    private void displayEligibilityResult(Player player, LoanApplicationService.EligibilityResult result) {
        clearInventory();
        
        if (result.isEligible()) {
            setItem(13, new ItemBuilder(Material.EMERALD_BLOCK)
                .setName("&a✅ 符合贷款条件")
                .setLore(
                    "",
                    "&7恭喜！您符合以下要求：",
                    String.format("&f• 信用评分: &a%d分", result.getCreditScore()),
                    String.format("&f• 信用等级: &b%s", result.getCreditGrade().getChineseName()),
                    "&f• 账户状态: &a正常",
                    "&f• 现有贷款状态: &a良好",
                    "",
                    "&2✅ 可以继续申请流程",
                    ""
                ).build());
            
            // Continue button
            setItem(31, new ItemBuilder(Material.GREEN_WOOL)
                .setName("&a继续下一步 &r→")
                .setLore("&7进入第二步：选择金额和期限").build());
            
        } else {
            setItem(13, new ItemBuilder(Material.RED_CONCRETE)
                .setName("&c❌ 不符合条件")
                .setLore(
                    "",
                    "&7您目前有以下条件未满足：",
                    String.format("&f• 信用评分: &c%d分 (需要%d分)", result.getCreditScore(), 
                        getMinRequiredScore(result.getLoanType())),
                    String.format("&f• %s", result.isCreditScoreQualified() ? "&a信用评分达标" : "&c未达到最低评分"),
                    result.getLoanStatus().hasOverdueLoans() ? "&f• &c存在逾期贷款" : "",
                    !result.isAccountVerified() ? "&f• &c账户未验证" : "",
                    "",
                    "&6建议改进措施：",
                    getImprovementSuggestions(result),
                    ""
                ).build());
            
            // Try different loan type
            setItem(20, new ItemBuilder(Material.GOLD_BLOCK)
                .setName("&e尝试其他贷款类型")
                .setLore("&7选择不同要求的贷款类型重新检查").build());
            
            setItem(24, new ItemBuilder(Material.BARRIER)
                .setName("&c取消申请")
                .setLore("&7回到主界面").build());
        }
        
        // Restart button
        setItem(45, new ItemBuilder(Material.RED_DYE)
            .setName("&c重新检查")
            .setLore("&7重新开始资格检查").build());
        
        addNavigationButtons(player, ApplicationStep.STEP_1_ELIGIBILITY);
    }
    
    // === Step 2: Amount and Term Selection ===
    private void createAmountTermInterface(Player player, LoanApplication application) {
        // Loan amount selection
        setItem(10, new ItemBuilder(Material.GOLD_INGOT)
            .setName("&6贷款金额选择")
            .setLore(
                "",
                "&7选择您想要借贷款的金额：",
                application.getRequestedAmount() > 0 ? 
                    String.format("&f当前选择: &e¥%.2f", application.getRequestedAmount()) :
                    "&f当前选择: &e未选择",
                "",
                "&e点击下方选项快速选择,",
                "&e或输入精确的自定义金额"
            ).build());
        
        // Quick amount options
        double[] quickAmounts = {1000, 5000, 10000, 20000, 50000, 100000};
        for (int i = 0; i < quickAmounts.length && i < 6; i++) {
            setItem(19 + i, new ItemBuilder(Material.GOLD_BLOCK)
                .setName(String.format("&6¥%.0f", quickAmounts[i]))
                .setLore(
                    String.format("&7选择贷款金额: &e¥%.2f", quickAmounts[i]),
                    "",
                    "&f点击查看更多信息",
                    "&f确认后进入下一步"
                ).build());
        }
        
        // Custom amount input
        setItem(46, new ItemBuilder(Material.PAPER)
            .setName("&e自定义金额")
            .setLore(
                "&7输入精确的贷款金额",
                String.format("&f当前贷款：¥%.2f", application.getRequestedAmount())
            ).build());
        
        // Term selection section
        setItem(12, new ItemBuilder(Material.CLOCK)
            .setName("&b贷款期限选择")
            .setLore(
                "",
                "&7选择贷款的期限：",
                application.getTermMonths() > 0 ?
                    String.format("&f当前选择: &b%d个月", application.getTermMonths()) :
                    "&f当前选择: &b未选择",
                "",
                "&e点击下方期限选项，",
                "&e查看每月还款额和总利息"
            ).build());
        
        // Quick term options
        int[] quickTerms = {3, 6, 12, 24, 36, 60};
        for (int i = 0; i < quickTerms.length && i < 6; i++) {
            setItem(21 + i, new ItemBuilder(Material.CLOCK)
                .setName(String.format("&b%d个月", quickTerms[i]))
                .setLore(
                    String.format("&7贷款期限: &b%d个月", quickTerms[i]),
                    "",
                    "&f点击查看预估月供",
                    "&f确认后进入下一步"
                ).build());
        }
        
        // Amount/term info display
        if (application.getRequestedAmount() > 0 && application.getTermMonths() > 0) {
            displayAmountTermPreview(player, application);
        }
        
        addNavigationButtons(player, ApplicationStep.STEP_2_AMOUNT_TERM);
    }
    
    private void displayAmountTermPreview(Player player, LoanApplication application) {
        // Validate current selection
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            LoanApplicationService.LoanValidationResult result = applicationService.validateLoanAmount(
                player.getUniqueId(), application.getLoanType(),
                application.getRequestedAmount(), application.getTermMonths()
            );
            
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                clearInventory(32, 52); // Clear preview area
                
                if (result.isValid()) {
                    setItem(34, new ItemBuilder(Material.GREEN_CONCRETE)
                        .setName("&a✅ 金额/期限有效")
                        .setLore(
                            String.format("&f最大可借: &e¥%.2f", result.getMaximumAmount()),
                            String.format("&f实际利率: &b%.2f%%", result.getInterestRate()),
                            "",
                            "&2✅ 选择有效，可以进入下一步"
                        ).build());
                    
                    // Continue button becomes active
                    setItem(52, new ItemBuilder(Material.LIME_DYE)
                        .setName("&a进入第三步 →")
                        .setLore("&7继续选择贷款类型").build());
                } else {
                    setItem(34, new ItemBuilder(Material.RED_CONCRETE)
                        .setName("&c❌ 选择无效")
                        .setLore(
                            result.getMessages().toArray(new String[0]),
                            "",
                            "&6请调整金额或期限"
                        ).build());
                    
                    setItem(52, new ItemBuilder(Material.GRAY_DYE)
                        .setName("&7需要先修正金额/期限")
                        .setLore("&7暂时无法进入下一步").build());
                }
            });
        });
    }
    
    private void handleStep2Click(Player player, int slot, ItemStack item, ClickType clickType) {
        LoanApplication application = applicationCache.get(player.getUniqueId());
        if (application == null) return;
        
        // Quick amount selection 19-24
        if (slot >= 19 && slot <= 24 && item.getType() == Material.GOLD_BLOCK) {
            double amount = Double.parseDouble(item.getItemMeta().getDisplayName().replaceAll("[^0-9.]", ""));
            application.setRequestedAmount(amount);
            createStepInterface(player, ApplicationStep.STEP_2_AMOUNT_TERM); // Refresh display
        }
        
        // Quick term selection 21-26
        if (slot >= 21 && slot <= 26 && item.getType() == Material.CLOCK) {
            int term = Integer.parseInt(item.getItemMeta().getDisplayName().replaceAll("[^0-9]", ""));
            application.setTermMonths(term);
            createStepInterface(player, ApplicationStep.STEP_2_AMOUNT_TERM); // Refresh display
        }
        
        // Custom amount input
        if (slot == 46 && item.getType() == Material.PAPER) {
            requestCustomAmount(player, application);
        }
        
        // Navigation
        if (slot == 52 && item.getType() == Material.LIME_DYE) {
            if (application.getRequestedAmount() > 0 && application.getTermMonths() > 0) {
                currentStep.put(player.getUniqueId(), 3);
                createStepInterface(player, ApplicationStep.STEP_3_LOAN_TYPE);
            }
        }
    }
    
    private void requestCustomAmount(Player player, LoanApplication application) {
        Messages.sendMessage(player, "loan.application.enter_custom_amount");
        player.closeInventory();
        
        // Request custom amount input (implemented in command system)
        GUIManager.getInstance().requestTextInput(player, "loan_amount", input -> {
            try {
                double amount = Double.parseDouble(input);
                if (amount >= 500 && amount <= 1000000) {
                    application.setRequestedAmount(amount);
                    openApplicationGUI(player);
                } else {
                    Messages.sendMessage(player, "loan.application.amount_out_of_range");
                }
            } catch (NumberFormatException e) {
                Messages.sendMessage(player, "loan.application.invalid_amount");
            }
        });
    }
    
    // === Step 3: Loan Type Selection ===
    private void createLoanTypeInterface(Player player, LoanApplication application) {
        // Title and guidance
        setItem(13, new ItemBuilder(Material.COMPASS)
            .setName("&5贷款类型选择")
            .setLore(
                "",
                "&7请据您的需求选择合适的贷款类型：",
                "",
                "&e不同不同类型的特点：",
                String.format("&f• 申请金额: &e¥%.2f", application.getRequestedAmount()),
                String.format("&f• 申请期限: &b%d个月", application.getTermMonths()),
                "",
                "&6点击下方选项了解详细信息"
            ).build());
        
        // Create loan type selection items
        LoanType[] types = LoanType.values();
        for (int i = 0; i < types.length; i++) {
            createLoanTypeItem(player, types[i], 19 + (i * 9));
        }
        
        // Current selection display
        if (application.getLoanType() != null) {
            displaySelectedLoanType(player, application);
        }
        
        addNavigationButtons(player, ApplicationStep.STEP_3_LOAN_TYPE);
    }
    
    private void createLoanTypeItem(Player player, LoanType loanType, int slot) {
        LoanApplicationService.LoanTypeResult details = applicationService.getLoanTypeDetails(
            loanType, 0, 0 // Initial call without specific amount/term to get general info
        );
        
        Material material = getLoanTypeMaterial(loanType);
        
        setItem(slot, new ItemBuilder(material)
            .setName(String.format("&e%s", loanType.getChineseName()))
            .setLore(
                String.format("%d. &f&, %s", details.getLoanType().ordinal() + 1, details.getDescription()),
                "",
                "&7要求：",
                getRequirementsDisplay(loanType),
                "",
                "&7优势：",
                getAdvantagesDisplay(loanType),
                "",
                "&6点击选择此类型"
            ).build());
    }
    
    private void displaySelectedLoanType(Player player, LoanApplication application) {
        LoanApplicationService.LoanTypeResult details = applicationService.getLoanTypeDetails(
            application.getLoanType(), application.getRequestedAmount(), application.getTermMonths()
        );
        
        setItem(34, new ItemBuilder(Material.EMERALD)
            .setName(String.format("&a已选择: %s", details.getLoanType().getChineseName()))
            .setLore(
                details.getTotalCostDisplay(),
                "",
                "&7要求：",
                details.getRequirements().stream().limit(3).toArray(String[]::new),
                "",
                "&7额外信息：",
                details.getAdvantages().stream().limit(2).toArray(String[]::new),
                "",
                "&a✅ 类型选择已完成"
            ).build());
    }
    
    private void handleStep3Click(Player player, int slot, ItemStack item, ClickType clickType) {
        LoanApplication application = applicationCache.get(player.getUniqueId());
        if (application == null) return;
        
        LoanType selectedType = null;
        
        // Check for loan type selection (19, 28, 37, 46)
        if (slot >= 19 && slot <= 46 && slot % 9 == 1) {
            for (LoanType type : LoanType.values()) {
                if (item.getItemMeta().getDisplayName().contains(type.getChineseName())) {
                    selectedType = type;
                    break;
                }
            }
        }
        
        if (selectedType != null) {
            application.setLoanType(selectedType);
            createStepInterface(player, ApplicationStep.STEP_3_LOAN_TYPE); // Refresh display
        }
        
        // Continue navigation
        if (application.getLoanType() != null) {
            currentStep.put(player.getUniqueId(), 4);
            createStepInterface(player, ApplicationStep.STEP_4_COLLATERAL);
        }
    }
    
    // === Step 4: Collateral Assessment ===
    private void createCollateralInterface(Player player, LoanApplication application) {
        // Check if collateral is required
        if (application.getLoanType() != LoanType.MORTGAGE) {
            // Skip collateral for non-mortgage loans
            setItem(13, new ItemBuilder(Material.GREEN_CONCRETE)
                .setName("&a✅ 无需抵押")
                .setLore(
                    "",
                    String.format("&7基于您选择的%s，", application.getLoanType().getChineseName()),
                    "&7该类型贷款无需抵押物",
                    "",
                    "&2✅ 可直接进入最后一步"
                ).build());
            
            setItem(31, new ItemBuilder(Material.LIME_DYE)
                .setName("&a进入最后一步 →")
                .setLore("&7跳过抵押步骤").build());
            
            currentStep.put(player.getUniqueId(), 5);
            application.setCollateralType("N/A");
            application.setCollateralValue(0);
            application.setCollateralDiscountRate(0);
            return;
        }
        
        // Mortgage collateral assessment
        setItem(13, new ItemBuilder(Material.CHEST)
            .setName("&6抵押物评估")
            .setLore(
                "",
                String.format("&7%s需要抵押物作为担保", application.getLoanType().getChineseName()),
                "",
                "&7请放置您的抵押物品",
                "&7系统将自动评估抵押价值：",
                "",
                "&e评估原则：",
                "&f• 价值按市场价打折计算",
                "&f• 贵重品通常折扣较低",
                "&f• 流动性差的折扣较高"
            ).build());
        
        // Collateral input slot (actual items dropped by player)
        setItem(22, new ItemBuilder(Material.HOPPER)
            .setName("&8[抵押物放置槽]")
            .setLore(
                "",  
                "&7请将要作抵押的物品",
                "&7直接投入此槽中：",
                "",
                "&6允许的物品类型：",
                "• &f钻石、翡翠等贵金属",
                "• &f房地契或地契",
                "• &f稀有物品和装备",
                ""
            ).build());
        
        // Assessment result display (if available)
        if (application.getCollateralAssessment() != null) {
            displayCollateralAssessment(player, application);
        }
        
        // Current collateral items
        if (application.getCollateralItems() != null && !application.getCollateralItems().isEmpty()) {
            int slotIndex = 29;
            for (ItemStack itemStack : application.getCollateralItems()) {
                if (slotIndex > 34) break; // Limit display to 6 items
                setItem(slotIndex, itemStack);
                slotIndex++;
            }
        }
        
        // Assessment buttons
        setItem(36, new ItemBuilder(Material.COMPASS)
            .setName("&b开始评估")
            .setLore("&7点击开始抵押物价值评估").build());
        
        setItem(37, new ItemBuilder(Material.PAPER)
            .setName("&e评估说明")
            .setLore("&7查看评估标准和折扣率").build());
        
        addNavigationButtons(player, ApplicationStep.STEP_4_COLLATERAL);
    }
    
    private void displayCollateralAssessment(Player player, LoanApplication application) {
        CollateralAssessment assessment = application.getCollateralAssessment();
        
        if (assessment.hasError()) {
            setItem(45, new ItemBuilder(Material.RED_CONCRETE)
                .setName("&c❌ 评估失败")
                .setLore(
                    "&7无法评估抵押物品：",
                    assessment.getErrorMessage(),
                    "",
                    "&6请检查：",
                    "• 物品类型是否支持",
                    "• 物品完整性",
                    "• 系统状态"
                ).build());
        } else {
            setItem(45, new ItemBuilder(Material.EMERALD_BLOCK)
                .setName("&a✅ 评估完成")
                .setLore(
                    String.format("&f总价值: &e¥%.2f", assessment.getTotalValue()),
                    String.format("&f折扣后价值: &a¥%.2f", 
                        assessment.getTotalValue() * 0.8), // Example discount
                    String.format("&f折扣率: &c%.0f%%", (1.0 - 0.8) * 100),
                    "",
                    "&7抵押物品详细评估：",
                    assessment.getAssessmentMessage(),
                    "",
                    "&2✅ 可以继续申请"
                ).build());
        }
    }
    
    private void handleStep4Click(Player player, int slot, ItemStack item, ClickType clickType) {
        if (slot == 36 && item.getType() == Material.COMPASS) {
            startCollateralAssessment(player);
        } else if (slot == 37 && item.getType() == Material.PAPER) {
            showCollateralInfo(player);
        }
    }
    
    private void startCollateralAssessment(Player player) {
        // This would trigger the assessment logic
        Messages.sendMessage(player, "loan.application.collateral_assessing");
        
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            LoanApplication application = applicationCache.get(player.getUniqueId());
            if (application == null) return;
            
            // Example assessment - in real implementation this would evaluate actual items
            List<CollateralItem> collateralItems = new ArrayList<>();
            
            // Simulate collateral assessment
            CollateralAssessment assessment = applicationService.assessCollateral(
                application.getLoanType(), collateralItems
            );
            
            application.setCollateralAssessment(assessment);
            application.setCollateralValue(assessment.getTotalValue());
            
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                createStepInterface(player, ApplicationStep.STEP_4_COLLATERAL); // Refresh display
            });
        });
    }
    
    private void showCollateralInfo(Player player) {
        player.sendMessage(String.join("\n", new String[]{
            "&6=== 抵押物评估标准 ===",
            "",
            "&e评估原则：",
            "• &f所有抵押物价值按市场价格打折计算",
            "• &f折扣率反映物品的流动性和贬值风险",
            "• &f最终贷款额度按折扣后价值计算",
            "",
            "&e常见抵押物品折扣率：",
            "• &f钻石/珠宝：80-90%（高流动性）",
            "• &f金银制品：75-85%（中等流动性）",
            "• &f房产地产：60-80%（低流动性）",
            "• &f稀有物品：50-70%（完全流动性未知）",
            "",
            "&e最终贷款额度通常为抵押物折扣后价值的80-90%"
        }));
    }
    
    // === Step 5: Final Confirmation ===
    private void createConfirmationInterface(Player player, LoanApplication application) {
        // Summary display
        setItem(13, new ItemBuilder(Material.BOOK)
            .setName("&6申请信息最终确认")
            .setLore(
                "",
                "&7请仔细检查您的申请信息：",
                "",
                String.format("&f贷款类型: &e%s", application.getLoanType().getChineseName()),
                String.format("&f申请金额: &a¥%.2f", application.getRequestedAmount()),
                String.format("&f贷款期限: &b%d个月", application.getTermMonths()),
                String.format("&f申请目的: &f%s", application.getLoanPurpose() != null ? application.getLoanPurpose() : "未填写"),
                String.format("&f预计年利率: &b%.2f%%", applicationService.getInterestRate(player.getUniqueId(), 
                    application.getLoanType(), application.getRequestedAmount(), application.getTermMonths())),
                "",
                "&c请确认所有信息正确无误！",
                "&c提交后将进入审批流程"
            ).build());
        
        // Terms and conditions
        setItem(22, new ItemBuilder(Material.WRITABLE_BOOK)
            .setName("&3贷款条款和条件")
            .setLore(
                "",
                "&7阅读并同意贷款条款：",
                "",
                "&f• 按时还款责任",
                "&f• 逾期还款后果",
                "&f• 提前还款政策",
                "&f• 服务费用说明",
                "",
                "&e点击查看详细条款"
            ).build());
        
        // Privacy policy  
        setItem(31, new ItemBuilder(Material.PAPER)
            .setName("&9隐私政策")
            .setLore(
                "",
                "&7您的信息将被用于：",
                "",
                "&f• 信用评估",
                "&f• 贷款审批",
                "&f• 还款管理",
                "&f• 合规报告",
                "",
                "&e点击查看隐私政策"
            ).build());
        
        // Final action buttons
        setItem(36, new ItemBuilder(Material.GREEN_CONCRETE)
            .setName("&a✅ 提交申请并同意条款")
            .setLore(
                "",
                "&7点击即表示您确认：",
                "&f• 已阅读并理解贷款条款",
                "&f• 同意隐私政策",
                "&f• 所提供信息真实准确",
                "&f• 确认申请并进入审批",
                "",
                "&2✅ 提交贷款申请"
            ).build());
        
        setItem(40, new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
            .setName("&b[三思后行]")
            .setLore("&7建议再三检查信息").build());
        
        setItem(44, new ItemBuilder(Material.RED_CONCRETE)
            .setName("&c❌ 取消/返回修改")
            .setLore(
                "",
                "&7返回检查或修改：",
                "&f• 贷款类型",
                "&f• 金额和期限",
                "&f• 申请信息",
                "",
                "&c取消本次申请"
            ).build());
        
        // Add the visual separators for different sections
        createConfirmationLayout();
        addNavigationButtons(player, ApplicationStep.STEP_5_CONFIRMATION);
    }
    
    private void createConfirmationLayout() {
        // Create visual separators
        for (int row = 0; row < 6; row++) {  
            setItem(row * 9, new ItemBuilder(Material.valueOf("BLACK_STAINED_GLASS_PANE"))
                .setName("&8‖").build());
            setItem(row * 9 + 8, new ItemBuilder(Material.valueOf("BLACK_STAINED_GLASS_PANE"))
                .setName("&8‖").build());
        }
    }
    
    private void handleStep5Click(Player player, int slot, ItemStack item, ClickType clickType) {
        if (item.getType() == Material.GREEN_CONCRETE && slot == 36) {
            // Submit application
            submitFinalApplication(player);
            
        } else if (item.getType() == Material.WRITABLE_BOOK && slot == 22) {
            showLoanTerms(player);
            
        } else if (item.getType() == Material.PAPER && slot == 31) {
            showPrivacyPolicy(player);
            
        } else if (item.getType() == Material.RED_CONCRETE && slot == 44) {
            // Return to type selection for modifications
            currentStep.put(player.getUniqueId(), 3);
            createStepInterface(player, ApplicationStep.STEP_3_LOAN_TYPE);
        }
    }
    
    private void submitFinalApplication(Player player) {
        LoanApplication application = applicationCache.get(player.getUniqueId());
        if (application == null || !application.isStep5Valid()) {
            Messages.sendMessage(player, "loan.application.incomplete");
            return;
        }
        
        // Prompt for loan purpose if not provided
        if (application.getLoanPurpose() == null || application.getLoanPurpose().trim().isEmpty()) {
            requestLoanPurpose(player);
            return;
        }
        
        Messages.sendMessage(player, "loan.application.submitting");
        
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            try {
                LoanApplicationService.LoanApplicationResult result = applicationService.submitApplication(application);
                
                Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    displayApplicationResult(player, result);
                });
                
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    Messages.sendMessage(player, "loan.application.submission_failed");
                });
                
                com.yae.utils.Logging.error("Failed to submit loan application", e);
            }
        });
    }
    
    private void displayApplicationResult(Player player, LoanApplicationService.LoanApplicationResult result) {
        clearInventory();
        
        if (result.isSuccess()) {
            setItem(13, new ItemBuilder(Material.EMERALD_BLOCK)
                .setName("&a✅ 申请提交成功")
                .setLore(
                    String.format("&f申请ID: &e%s", result.getApplication().getApplicationId()),
                    String.format("&f申请金额: &a¥%.2f", result.getApplication().getApplicationId()), // TODO: fix - get amount
                    String.format("&f申请状态: &b%s", result.getApplication().getStatus()),
                    "",
                    result.getStatusMessage(),
                    "",
                    "&6后续操作：",
                    "• &f关注系统通知和审批结果",
                    "• &f注意查收邮件和短信提醒",
                    "• &f及时处理补充材料要求",
                    result.getApplication().getStatus().name().contains("APPROVED") ? 
                        "&2✅ 您已获得贷款批准" : "&e⏳ 请等待审批结果"
                ).build());
            
            setItem(31, new ItemBuilder(Material.GREEN_DYE)
                .setName("&a完成并退出")
                .setLore("&7顺利完成申请流程").build());
            
        } else {
            setItem(13, new ItemBuilder(Material.RED_CONCRETE)
                .setName("&c❌ 提交失败")
                .setLore(
                    String.format("&7原因: &c%s", result.getErrorMessage()),
                    "",
                    "&6建议：",
                    "• &f检查网络连接重新提交",
                    "• &f联系客服寻求帮助",
                    "• &f稍后再试"
                ).build());
            
            setItem(31, new ItemBuilder(Material.ORANGE_DYE)
                .setName("&e重试提交")
                .setLore("&7重新尝试提交申请").build());
        }
        
        setItem(49, new ItemBuilder(Material.BARRIER)
            .setName("&c关闭界面")
            .setLore("&7结束申请流程").build());
    }
    
    private void requestLoanPurpose(Player player) {
        Messages.sendMessage(player, "loan.application.enter_purpose");
        player.closeInventory();
        
        GUIManager.getInstance().requestTextInput(player, "loan_purpose", input -> {
            LoanApplication application = applicationCache.get(player.getUniqueId());
            if (application != null) {
                application.setLoanPurpose(input);
                openApplicationGUI(player);
            }
        });
    }
    
    private void showLoanTerms(Player player) {
        player.sendMessage(String.join("\n", new String[]{
            "&6=== 贷款条款和条件 ===",
            "",
            "&e1. 借款责任和义务",
            "&f   a) 必须按合同约定时间足额还款",
            "&f   b) 不得违反法律法规或恶意逃避还款",
            "&f   c) 及时通知地址、收入等重大变更",
            "",
            "&e2. 还款基本要求",
            "&f   a) 每月固定还款额（含本金+利息）",
            "&f   b) 逾期将产生罚金和额外利息",
            "&f   c) 连续逾期将导致信用记录受损",
            "",
            "&e3. 手续费和成本",
            "&f   a) 贷款服务费：按本金%+时间计费",
            "&f   b) 逾期罚金：每日万分之五",
            "",
            "&64. 同意条款",
            "&f   点击确认表示您已阅读并同意以上所有条款",
            "",
            "&a&l继续申请即表示同意以上条款"
        }));
        
        // 返回主界面
        Bukkit.getScheduler().runTaskLater(YetAnotherEconomy.getInstance(), () -> {
            openApplicationGUI(player);
        }, 100L);
    }
}
