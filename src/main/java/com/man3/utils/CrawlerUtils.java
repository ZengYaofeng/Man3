package com.man3.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 爬虫通用工具
 */
public final class CrawlerUtils {

    private CrawlerUtils() {
    }

    /** 从字符串中提取第一个数字 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /** 从 style="background-image: url(xxx)" 中提取图片地址 */
    public static String extractUrlFromStyle(String style) {
        if (style == null || style.isEmpty()) {
            return null;
        }
        Matcher m = Pattern.compile("url\\s*\\(['\"]?(.*?)['\"]?\\)").matcher(style);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * 提取文本中的数字
     * 如 "点击：1024" -> 1024L, "更新时间：2026-08-14" -> 20260814(含义不同,请按需使用)
     */
    public static long extractNumber(String text) {
        if (text == null) {
            return 0L;
        }
        Matcher m = NUMBER_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Long.parseLong(m.group());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    /** 去掉字段前缀, 如 "别名：衣冠禽兽" -> "衣冠禽兽" */
    public static String stripPrefix(String text, String prefix) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        String p = prefix == null ? "" : prefix;
        if (t.startsWith(p)) {
            t = t.substring(p.length());
        }
        // 去掉开头的冒号(中英文)
        while (t.startsWith(":") || t.startsWith("：")) {
            t = t.substring(1).trim();
        }
        return t.trim();
    }

    /** 线程休眠, 不抛出受检异常 */
    public static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
