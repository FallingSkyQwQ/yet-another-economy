package com.yae.utils;

/**
 * 消息工具类 - 兼容接口
 * 用于保持与现有代码的兼容性，所有方法委托给MessageUtils
 */
public class MessageUtil {
    
    /**
     * 将带有颜色代码的字符串转换为彩色文本
     */
    public static String color(String text) {
        return MessageUtils.color(text);
    }
    
    /**
     * 将字符串列表转换为彩色文本
     */
    public static java.util.List<String> color(java.util.List<String> texts) {
        return MessageUtils.color(texts);
    }
    
    /**
     * 发送带前缀的消息
     */
    public static String formatMessage(String prefix, String message) {
        return MessageUtils.formatMessage(prefix, message);
    }
    
    /**
     * 创建成功消息
     */
    public static String success(String message) {
        return MessageUtils.success(message);
    }
    
    /**
     * 创建错误消息
     */
    public static String error(String message) {
        return MessageUtils.error(message);
    }
    
    /**
     * 创建警告消息
     */
    public static String warning(String message) {
        return MessageUtils.warning(message);
    }
    
    /**
     * 创建信息消息
     */
    public static String info(String message) {
        return MessageUtils.info(message);
    }
    
    /**
     * 去除颜色代码
     */
    public static String stripColors(String text) {
        return MessageUtils.stripColors(text);
    }
    
    /**
     * 格式化数字
     */
    public static String formatNumber(double number) {
        return MessageUtils.formatNumber(number);
    }
    
    /**
     * 格式化货币
     */
    public static String formatCurrency(double amount, String symbol) {
        return MessageUtils.formatCurrency(amount, symbol);
    }
    
    /**
     * 格式化列表
     */
    public static String formatList(java.util.List<String> items, String separator) {
        return MessageUtils.formatList(items, separator);
    }
}
