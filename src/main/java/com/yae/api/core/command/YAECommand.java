package com.yae.api.core.command;

import com.yae.api.core.YAECore;
import com.yae.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.yae.api.core.YAECore;
import com.yae.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * YAE命令基类
 * 为所有YAE命令提供统一的接口和权限管理
 */
public abstract class YAECommand extends Command implements CommandExecutor, TabCompleter {
    
    protected final YAECore plugin;
    protected final String permission;
    protected final List<String> aliases;
    protected final String description;
    
    protected YAECommand(@NotNull YAECore plugin, @NotNull String name, 
                        @NotNull String description, @NotNull String permission, 
                        @NotNull List<String> aliases) {
        super(name, description, "/" + plugin.getPluginName().toLowerCase() + " " + name.toLowerCase(), aliases);
        
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        Objects.requireNonNull(name, "Command name cannot be null");
        this.description = Objects.requireNonNull(description, "Command description cannot be null");
        this.permission = Objects.requireNonNull(permission, "Command permission cannot be null");
        this.aliases = new ArrayList<>(Objects.requireNonNull(aliases, "Command aliases cannot be null"));
        
        // 注册命令
        registerCommand();
    }
    
    protected YAECommand(@NotNull YAECore plugin, @NotNull String name, 
                        @NotNull String description, @NotNull String permission) {
        this(plugin, name, description, permission, Collections.emptyList());
    }
    
    /**
     * 注册命令到插件
     */
    private void registerCommand() {
        // 获取命令映射
        org.bukkit.command.CommandMap commandMap;
        try {
            java.lang.reflect.Field field = org.bukkit.Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            commandMap = (org.bukkit.command.CommandMap) field.get(org.bukkit.Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register command " + getName() + ": " + e.getMessage());
            return;
        }
        
        // 注册命令
        commandMap.register(plugin.getPluginName().toLowerCase(), this);
        
        plugin.debug("Registered command: " + getName() + " with aliases: " + aliases);
    }
    
    /**
     * 检查权限
     */
    public boolean checkPermission(@NotNull CommandSender sender) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        
        // 检查管理员权限
        if (sender.hasPermission("yae.admin.*") || sender.hasPermission("yae.*")) {
            return true;
        }
        
        // 检查通用权限
        if (sender.hasPermission("yae.command.*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 发送权限不足消息
     */
    protected void sendPermissionMessage(@NotNull CommandSender sender) {
        sender.sendMessage(MessageUtils.error("您没有权限使用此命令"));
    }
    
    /**
     * 检查并显示权限信息
     */
    protected boolean checkExecutionPermission(@NotNull CommandSender sender) {
        if (!checkPermission(sender)) {
            sendPermissionMessage(sender);
            return false;
        }
        return true;
    }
    
    // Command implementation
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        return execute(sender, label, args);
    }
    
    // Abstract methods to be implemented by subclasses
    /**
     * 执行命令
     * @param sender 命令发送者
     * @param label 命令标签
     * @param args 命令参数
     * @return 是否成功执行
     */
    public abstract boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args);
    
    /**
     * Tab补全
     * @param sender 命令发送者
     * @param label 命令标签
     * @param args 当前参数
     * @return 补全建议列表，可以为null
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String label, @NotNull String[] args) {
        // 检查权限
        if (!checkPermission(sender)) {
            return Collections.emptyList();
        }
        
        return tabComplete(sender, label, args);
    }
    
    /**
     * Tab补全（由子类实现）
     */
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
    
    // 常用工具方法
    
    /**
     * 获取离线玩家
     */
    @Nullable
    protected org.bukkit.OfflinePlayer getOfflinePlayer(String playerName) {
        return Bukkit.getOfflinePlayer(playerName);
    }
    
    /**
     * 检查是否为玩家
     */
    protected boolean isPlayer(@NotNull CommandSender sender) {
        return sender instanceof org.bukkit.entity.Player;
    }
    
    /**
     * 获取玩家或返回null
     */
    @Nullable
    protected org.bukkit.entity.Player getPlayerOrNull(@NotNull CommandSender sender) {
        return sender instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player) sender : null;
    }
    
    /**
     * 获取玩家或发送错误消息
     */
    @Nullable
    protected org.bukkit.entity.Player requirePlayer(@NotNull CommandSender sender) {
        if (sender instanceof org.bukkit.entity.Player player) {
            return player;
        }
        sender.sendMessage(MessageUtils.error("此命令只能由玩家使用"));
        return null;
    }
    
    /**
     * 解析参数为整数，失败时返回默认值
     */
    protected int parseIntOrDefault(String arg, int defaultValue) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 解析参数为双精度数，失败时返回默认值
     */
    protected double parseDoubleOrDefault(String arg, double defaultValue) {
        try {
            return Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 检查参数数量是否在范围内
     */
    protected boolean checkArgCount(String[] args, int min, int max) {
        return args.length >= min && args.length <= max;
    }
    
    /**
     * 检查参数数量是否为指定值
     */
    protected boolean checkArgCount(String[] args, int count) {
        return args.length == count;
    }
}
