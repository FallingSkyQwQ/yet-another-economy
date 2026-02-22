package com.yae.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 日志工具类
 * 提供统一的日志记录功能
 */
public class Logging {
    
    private static final Logger logger = Logger.getLogger("YetAnotherEconomy");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 记录信息日志
     */
    public static void info(@NotNull String message) {
        logger.info(formatMessage("INFO", message));
    }
    
    /**
     * 记录信息日志（带格式化）
     */
    public static void info(@NotNull String format, Object... args) {
        info(String.format(format, args));
    }
    
    /**
     * 记录警告日志
     */
    public static void warning(@NotNull String message) {
        logger.warning(formatMessage("WARN", message));
    }
    
    /**
     * 记录警告日志（带格式化）
     */
    public static void warning(@NotNull String format, Object... args) {
        warning(String.format(format, args));
    }
    
    /**
     * 记录错误日志
     */
    public static void error(@NotNull String message) {
        logger.severe(formatMessage("ERROR", message));
    }
    
    /**
     * 记录错误日志（带格式化）
     */
    public static void error(@NotNull String format, Object... args) {
        error(String.format(format, args));
    }
    
    /**
     * 记录错误日志（带异常）
     */
    public static void error(@NotNull String message, @Nullable Throwable throwable) {
        logger.log(Level.SEVERE, formatMessage("ERROR", message), throwable);
    }
    
    /**
     * 记录调试日志
     */
    public static void debug(@NotNull String message) {
        logger.fine(formatMessage("DEBUG", message));
    }
    
    /**
     * 记录调试日志（带格式化）
     */
    public static void debug(@NotNull String format, Object... args) {
        debug(String.format(format, args));
    }
    
    /**
     * 记录追踪日志
     */
    public static void trace(@NotNull String message) {
        logger.finest(formatMessage("TRACE", message));
    }
    
    /**
     * 记录追踪日志（带格式化）
     */
    public static void trace(@NotNull String format, Object... args) {
        trace(String.format(format, args));
    }
    
    /**
     * 记录服务启动日志
     */
    public static void logServiceStartup(@NotNull String serviceName) {
        info("服务 " + serviceName + " 正在启动...");
    }
    
    /**
     * 记录服务启动完成日志
     */
    public static void logServiceStarted(@NotNull String serviceName) {
        info("服务 " + serviceName + " 已成功启动");
    }
    
    /**
     * 记录服务停止日志
     */
    public static void logServiceShutdown(@NotNull String serviceName) {
        info("服务 " + serviceName + " 正在停止...");
    }
    
    /**
     * 记录服务停止完成日志
     */
    public static void logServiceStopped(@NotNull String serviceName) {
        info("服务 " + serviceName + " 已成功停止");
    }
    
    /**
     * 记录服务配置加载日志
     */
    public static void logConfigLoading(@NotNull String configName) {
        debug("正在加载配置文件: " + configName);
    }
    
    /**
     * 记录服务配置加载完成日志
     */
    public static void logConfigLoaded(@NotNull String configName) {
        debug("配置文件已加载: " + configName);
    }
    
    /**
     * 记录数据库操作日志
     */
    public static void logDatabaseOperation(@NotNull String operation, @NotNull String table) {
        debug(String.format("执行数据库操作: %s - 表: %s", operation, table));
    }
    
    /**
     * 记录数据库错误日志
     */
    public static void logDatabaseError(@NotNull String operation, @NotNull String table, @Nullable Throwable error) {
        error(String.format("数据库操作失败: %s - 表: %s", operation, table), error);
    }
    
    /**
     * 记录用户操作日志
     */
    public static void logUserAction(@NotNull String userName, @NotNull String action) {
        info(String.format("用户操作: %s - %s", userName, action));
    }
    
    /**
     * 记录系统事件日志
     */
    public static void logSystemEvent(@NotNull String eventType, @NotNull String description) {
        info(String.format("系统事件: %s - %s", eventType, description));
    }
    
    /**
     * 记录性能信息
     */
    public static void logPerformance(@NotNull String operation, long durationMs) {
        debug(String.format("性能记录: %s - 耗时: %d ms", operation, durationMs));
    }
    
    /**
     * 记录警告事件
     */
    public static void logWarning(@NotNull String component, @NotNull String warning) {
        warning(String.format("警告 [%s]: %s", component, warning));
    }
    
    /**
     * 记录错误事件
     */
    public static void logError(@NotNull String component, @NotNull String error) {
        error(String.format("错误 [%s]: %s", component, error));
    }
    
    /**
     * 获取堆栈跟踪字符串
     */
    @NotNull
    public static String getStackTrace(@NotNull Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage()).append("\n");
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化日志消息
     */
    @NotNull
    private static String formatMessage(@NotNull String level, @NotNull String message) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        return String.format("[%s] [%s] %s", timestamp, level, message);
    }
    
    /**
     * 检查当前日志级别是否启用
     */
    public static boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }
    
    /**
     * 检查当前日志级别是否启用
     */
    public static boolean isTraceEnabled() {
        return logger.isLoggable(Level.FINEST);
    }
}
