package com.yae.utils;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * 时间工具类
 * 提供时间格式化和计算相关功能
 */
public class TimeUtils {
    
    // 常用时间格式
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter SHORT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    
    /**
     * 格式化日期时间
     */
    @NotNull
    public static String formatDateTime(@NotNull LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }
    
    /**
     * 格式化日期
     */
    @NotNull
    public static String formatDate(@NotNull LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }
    
    /**
     * 格式化时间
     */
    @NotNull
    public static String formatTime(@NotNull LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(TIME_FORMATTER);
    }
    
    /**
     * 格式化短日期时间
     */
    @NotNull
    public static String formatShortDateTime(@NotNull LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(SHORT_DATE_TIME_FORMATTER);
    }
    
    /**
     * 格式化自定义模式
     */
    @NotNull
    public static String formatCustom(@NotNull LocalDateTime dateTime, @NotNull String pattern) {
        if (dateTime == null || pattern == null) {
            return "";
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return dateTime.format(formatter);
        } catch (Exception e) {
            return dateTime.format(DATE_TIME_FORMATTER);
        }
    }
    
    /**
     * 计算剩余时间（用于格式化倒计时）
     */
    @NotNull
    public static String formatRemainingTime(@NotNull LocalDateTime futureTime) {
        if (futureTime == null) {
            return "";
        }
        return formatRemainingTime(futureTime, LocalDateTime.now());
    }
    
    /**
     * 计算两个时间之间的剩余时间
     */
    @NotNull
    public static String formatRemainingTime(@NotNull LocalDateTime futureTime, @NotNull LocalDateTime currentTime) {
        if (futureTime == null || currentTime == null) {
            return "";
        }
        
        if (futureTime.isBefore(currentTime) || futureTime.isEqual(currentTime)) {
            return "已到期";
        }
        
        long days = ChronoUnit.DAYS.between(currentTime, futureTime);
        LocalDateTime daysRemoved = currentTime.plusDays(days);
        long hours = ChronoUnit.HOURS.between(daysRemoved, futureTime);
        LocalDateTime hoursRemoved = daysRemoved.plusHours(hours);
        long minutes = ChronoUnit.MINUTES.between(hoursRemoved, futureTime);
        
        StringBuilder result = new StringBuilder();
        
        if (days > 0) {
            result.append(days).append("天");
        }
        if (hours > 0) {
            if (result.length() > 0) result.append(" ");
            result.append(hours).append("小时");
        }
        if (minutes > 0 && days == 0) { // 只有天数小于1天才显示分钟
            if (result.length() > 0) result.append(" ");
            result.append(minutes).append("分钟");
        }
        
        return result.length() > 0 ? result.toString() : "不到1分钟";
    }
    
    /**
     * 计算相对时间（例如：2分钟前，3天后）
     */
    @NotNull
    public static String formatRelativeTime(@NotNull LocalDateTime targetTime) {
        return formatRelativeTime(targetTime, LocalDateTime.now());
    }
    
    /**
     * 计算相对时间
     */
    @NotNull
    public static String formatRelativeTime(@NotNull LocalDateTime targetTime, @NotNull LocalDateTime currentTime) {
        if (targetTime == null || currentTime == null) {
            return "";
        }
        
        long seconds = ChronoUnit.SECONDS.between(currentTime, targetTime);
        
        if (seconds == 0) {
            return "现在";
        } else if (seconds > 0) {
            return formatDuration(seconds) + "后";
        } else {
            return formatDuration(-seconds) + "前";
        }
    }
    
    /**
     * 将秒数格式化为易读的持续时间
     */
    @NotNull
    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            seconds = -seconds;
        }
        
        long days = seconds / (24 * 60 * 60);
        seconds %= 24 * 60 * 60;
        long hours = seconds / (60 * 60);
        seconds %= 60 * 60;
        long minutes = seconds / 60;
        seconds %= 60;
        
        StringBuilder result = new StringBuilder();
        
        if (days > 0) {
            result.append(days).append("天");
            if (hours > 0 && days < 7) {
                result.append(" ").append(hours).append("小时");
            }
        } else if (hours > 0) {
            result.append(hours).append("小时");
            if (minutes > 0 && hours < 24) {
                result.append(" ").append(minutes).append("分钟");
            }
        } else if (minutes > 0) {
            result.append(minutes).append("分钟");
            if (seconds > 0 && minutes < 60) {
                result.append(" ").append(seconds).append("秒");
            }
        } else {
            result.append(seconds).append("秒");
        }
        
        return result.toString();
    }
    
    /**
     * 将毫秒数转换为易读的持续时间
     */
    @NotNull
    public static String formatDurationMillis(long milliseconds) {
        return formatDuration(TimeUnit.MILLISECONDS.toSeconds(milliseconds));
    }
    
    /**
     * 计算两个时间之间的天数差
     */
    public static long daysBetween(@NotNull LocalDateTime start, @NotNull LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end);
    }
    
    /**
     * 检查是否已过期
     */
    public static boolean isExpired(@NotNull LocalDateTime expiryTime) {
        return isExpired(expiryTime, LocalDateTime.now());
    }
    
    /**
     * 检查指定时间是否已过期
     */
    public static boolean isExpired(@NotNull LocalDateTime expiryTime, @NotNull LocalDateTime currentTime) {
        if (expiryTime == null || currentTime == null) {
            return false;
        }
        return !currentTime.isBefore(expiryTime);
    }
    
    /**
     * 计算剩余时间百分比（用于进度条等）
     */
    public static double calculateTimeProgress(@NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime, 
                                               @NotNull LocalDateTime currentTime) {
        if (startTime == null || endTime == null || currentTime == null) {
            return 0.0;
        }
        
        long totalDuration = ChronoUnit.SECONDS.between(startTime, endTime);
        long elapsed = ChronoUnit.SECONDS.between(startTime, currentTime);
        
        if (totalDuration <= 0) {
            return currentTime.isEqual(endTime) ? 1.0 : 0.0;
        }
        
        if (currentTime.isBefore(startTime)) {
            return 0.0;
        }
        
        if (currentTime.isAfter(endTime)) {
            return 1.0;
        }
        
        return Math.min(1.0, (double) elapsed / totalDuration);
    }
    
    /**
     * 获取当前时间的毫秒时间戳
     */
    public static long currentMillis() {
        return System.currentTimeMillis();
    }
    
    /**
     * 将秒转换为更易读的格式
     */
    @NotNull
    public static String formatSeconds(int seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 60 * 60) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            int hours = seconds / 3600;
            int remaining = seconds % 3600;
            int minutes = remaining / 60;
            int secondsRemaining = remaining % 60;
            
            if (secondsRemaining > 0) {
                return hours + "时" + minutes + "分" + secondsRemaining + "秒";
            } else if (minutes > 0) {
                return hours + "时" + minutes + "分";
            } else {
                return hours + "时";
            }
        }
    }
    
    /**
     * 将 ticks 转换为秒
     */
    public static int ticksToSeconds(int ticks) {
        return ticks / 20; // Minecraft tick rate is 20 ticks per second
    }
    
    /**
     * 将秒转换为 ticks
     */
    public static int secondsToTicks(int seconds) {
        return seconds * 20; // Minecraft tick rate is 20 ticks per second
    }
    
    /**
     * 等待指定秒数
     */
    public static void sleep(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
