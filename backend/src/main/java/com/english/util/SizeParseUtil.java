package com.english.util;

public final class SizeParseUtil {

    private SizeParseUtil() {}

    /** 解析 "10MB" / "512MB" / "1GB" / "100KB" → 字节数，解析失败返回 defaultBytes */
    public static long parseSize(String sizeStr, long defaultBytes) {
        if (sizeStr == null || sizeStr.isBlank()) return defaultBytes;
        String s = sizeStr.trim().toUpperCase();
        long multiplier = 1;
        if (s.endsWith("GB")) { multiplier = 1024L * 1024 * 1024; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("MB")) { multiplier = 1024L * 1024;      s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("KB")) { multiplier = 1024L;              s = s.substring(0, s.length() - 2); }
        try {
            return Long.parseLong(s.trim()) * multiplier;
        } catch (NumberFormatException e) {
            return defaultBytes;
        }
    }

    public static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format("%.0fGB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024)        return String.format("%.0fMB", bytes / (1024.0 * 1024));
        if (bytes >= 1024L)               return String.format("%.0fKB", bytes / 1024.0);
        return bytes + "B";
    }
}
