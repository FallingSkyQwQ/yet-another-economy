package com.yae.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息工具类
 * 提供字符串和组件之间的转换以及颜色代码处理
 */
@SuppressWarnings("deprecation")
public class MessageUtils {
    
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    /**
     * 将带有颜色代码的字符串转换为彩色文本
     */
    @NotNull
    public static String color(@NotNull String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * 将字符串列表转换为彩色文本
     */
    @NotNull
    public static List<String> color(@NotNull List<String> texts) {
        if (texts == null) {
            return new ArrayList<>();
        }
        return texts.stream()
                .map(MessageUtils::color)
                .collect(Collectors.toList());
    }
    
    /**
     * 使用 MiniMessage 格式创建组件
     */
    @NotNull
    public static Component miniMessage(@NotNull String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(text);
    }
    
    /**
     * 使用 MiniMessage 格式创建组件列表
     */
    @NotNull
    public static List<Component> miniMessages(@NotNull List<String> texts) {
        if (texts == null) {
            return new ArrayList<>();
        }
        return texts.stream()
                .map(MessageUtils::miniMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * 将组件转换为带有颜色代码的字符串
     */
    @NotNull
    public static String colorFromComponent(@NotNull Component component) {
        if (component == null) {
            return "";
        }
        return MINI_MESSAGE.serialize(component);
    }
    
    /**
     * 发送带前缀的消息
     */
    @NotNull
    public static String formatMessage(@NotNull String prefix, @NotNull String message) {
        return color(prefix + " " + message);
    }
    
    /**
     * 创建成功消息
     */
    @NotNull
    public static String success(@NotNull String message) {
        return color("&a✅ " + message);
    }
    
    /**
     * 创建错误消息
     */
    @NotNull
    public static String error(@NotNull String message) {
        return color("&c❌ " + message);
    }
    
    /**
     * 创建警告消息
     */
    @NotNull
    public static String warning(@NotNull String message) {
        return color("&6⚠️ " + message);
    }
    
    /**
     * 创建信息消息
     */
    @NotNull
    public static String info(@NotNull String message) {
        return color("&9ℹ️ " + message);
    }
    
    /**
     * 创建进度消息
     */
    @NotNull
    public static String progress(@NotNull String message) {
        return color("&e⏳ " + message);
    }
    
    /**
     * 去除颜色代码
     */
    @NotNull
    public static String stripColors(@NotNull String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ChatColor.stripColor(color(text));
    }
    
    /**
     * 检查字符串是否包含颜色代码
     */
    public static boolean hasColors(@NotNull String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("&") || text.contains("§");
    }
    
    /**
     * 重复字符串
     */
    @NotNull
    public static String repeat(@NotNull String str, int count) {
        if (str == null || count <= 0) {
            return "";
        }
        return str.repeat(count);
    }
    
    /**
     * 创建分隔符
     */
    @NotNull
    public static String separator(char separator, int length) {
        if (length <= 0) {
            return "";
        }
        return String.valueOf(separator).repeat(length);
    }
    
    /**
     * 创建标题
     */
    @NotNull
    public static String title(@NotNull String title, char borderChar, int totalLength) {
        if (title == null || title.isEmpty() || totalLength <= 0) {
            return "";
        }
        
        String coloredTitle = color(title);
        String strippedTitle = stripColors(coloredTitle);
        int titleLength = strippedTitle.length();
        
        if (titleLength >= totalLength) {
            return coloredTitle;
        }
        
        int borderLength = (totalLength - titleLength) / 2;
        String border = String.valueOf(borderChar).repeat(Math.max(0, borderLength));
        
        return color(border + " &r" + coloredTitle + " &r" + border);
    }
    
    /**
     * 格式化数字
     */
    @NotNull
    public static String formatNumber(double number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2fB", number / 1_000_000_000);
        } else if (number >= 1_000_000) {
            return String.format("%.2fM", number / 1_000_000);
        } else if (number >= 1_000) {
            return String.format("%.2fK", number / 1_000);
        } else {
            return String.format("%.2f", number);
        }
    }
    
    /**
     * 格式化货币
     */
    @NotNull
    public static String formatCurrency(double amount, @NotNull String symbol) {
        return String.format("%s%.2f", symbol, amount);
    }
    
    /**
     * 格式化列表
     */
    @NotNull
    public static String formatList(@NotNull List<String> items, @NotNull String separator) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return String.join(separator, items.stream()
                .map(MessageUtils::color)
                .toArray(String[]::new));
    }
    
    /**
     * 截断字符串
     */
    @NotNull
    public static String truncate(@NotNull String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 填充字符串
     */
    @NotNull
    public static String padLeft(@NotNull String text, int length, char padChar) {
        if (text == null) {
            text = "";
        }
        int textLength = stripColors(text).length();
        if (textLength >= length) {
            return text;
        }
        return repeat(String.valueOf(padChar), length - textLength) + text;
    }
    
    @NotNull
    public static String padRight(@NotNull String text, int length, char padChar) {
        if (text == null) {
            text = "";
        }
        int textLength = stripColors(text).length();
        if (textLength >= length) {
            return text;
        }
        return text + repeat(String.valueOf(padChar), length - textLength);
    }
    
    /**
     * 创建表格
     */
    @NotNull
    public static List<String> createTable(@NotNull List<String> headers, @NotNull List<List<String>> rows,
                                          int[] columnWidths, char borderChar) {
        List<String> result = new ArrayList<>();
        
        if (headers == null || rows == null || columnWidths == null) {
            return result;
        }
        
        // 创建边框
        StringBuilder border = new StringBuilder();
        border.append(borderChar);
        for (int width : columnWidths) {
            border.append(repeat(String.valueOf(borderChar), width + 2));
            border.append(borderChar);
        }
        
        result.add(border.toString());
        
        // 添加表头
        result.add(createTableRow(headers, columnWidths, borderChar));
        result.add(border.toString());
        
        // 添加数据行
        for (List<String> row : rows) {
            result.add(createTableRow(row, columnWidths, borderChar));
        }
        
        result.add(border.toString());
        return result;
    }
    
    private static String createTableRow(List<String> cells, int[] widths, char borderChar) {
        StringBuilder row = new StringBuilder();
        row.append(borderChar);
        
        for (int i = 0; i < cells.size() && i < widths.length; i++) {
            row.append(" ");
            row.append(padRight(stripColors(color(cells.get(i))), widths[i], ' '));
            row.append(" ");
            row.append(borderChar);
        }
        
        return row.toString();
    }
}
