package com.english.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用户请求量监控和分析
 * 记录单用户固定时间窗口内的请求量，识别异常访问模式
 */
@Component
public class UserRequestMonitor {

    private static final Logger log = LoggerFactory.getLogger(UserRequestMonitor.class);
    private static final int WINDOW_SIZE_MINUTES = 5; // 5分钟时间窗口

    // 用户 -> 时间窗口 -> 请求计数
    private final Map<String, Map<String, AtomicInteger>> userRequestWindows = new ConcurrentHashMap<>();

    // 用户峰值请求量记录
    private final Map<String, Integer> userPeakRequests = new ConcurrentHashMap<>();

    /**
     * 记录用户请求
     */
    public void recordRequest(String username, String path) {
        if (username == null || "anonymousUser".equals(username)) {
            return;
        }

        String windowKey = getCurrentWindowKey();
        userRequestWindows
                .computeIfAbsent(username, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(windowKey, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * 获取用户在当前时间窗口的请求数
     */
    public int getUserRequestCount(String username) {
        if (username == null) {
            return 0;
        }

        Map<String, AtomicInteger> windows = userRequestWindows.get(username);
        if (windows == null) {
            return 0;
        }

        String windowKey = getCurrentWindowKey();
        AtomicInteger count = windows.get(windowKey);
        return count == null ? 0 : count.get();
    }

    /**
     * 每5分钟分析一次用户请求模式
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void analyzeUserRequests() {
        try {
            String currentWindow = getCurrentWindowKey();
            Map<String, Integer> currentCounts = new HashMap<>();

            // 收集当前窗口的请求计数
            for (Map.Entry<String, Map<String, AtomicInteger>> entry : userRequestWindows.entrySet()) {
                String username = entry.getKey();
                AtomicInteger count = entry.getValue().get(currentWindow);
                if (count != null) {
                    int requestCount = count.get();
                    currentCounts.put(username, requestCount);

                    // 更新峰值
                    Integer previousPeak = userPeakRequests.get(username);
                    if (previousPeak == null || requestCount > previousPeak) {
                        userPeakRequests.put(username, requestCount);
                        log.info("[用户请求分析] 用户 {} 创建新峰值: {} requests/{}min (旧峰值: {})",
                                username, requestCount, WINDOW_SIZE_MINUTES, previousPeak != null ? previousPeak : 0);
                    }
                }
            }

            // 识别异常访问模式
            detectAnomalies(currentCounts);

            // 清理旧窗口数据（保留最近30分钟）
            cleanupOldWindows();

        } catch (Exception e) {
            log.error("用户请求分析失败", e);
        }
    }

    /**
     * 检测异常访问模式
     */
    private void detectAnomalies(Map<String, Integer> currentCounts) {
        // 计算平均值和标准差
        if (currentCounts.isEmpty()) {
            return;
        }

        double mean = currentCounts.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(currentCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average()
                .orElse(0.0));

        // 超过平均值 + 3倍标准差的视为异常
        double anomalyThreshold = mean + 3 * stdDev;

        for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
            String username = entry.getKey();
            int count = entry.getValue();

            if (count > anomalyThreshold && count > 100) { // 至少100次请求才报告
                log.warn("[异常访问检测] 用户 {} 在过去{}分钟发起了 {} 次请求（平均值: {:.1f}, 阈值: {:.1f}）",
                        username, WINDOW_SIZE_MINUTES, count, mean, anomalyThreshold);
            }
        }
    }

    /**
     * 清理30分钟前的数据
     */
    private void cleanupOldWindows() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        String cutoffKey = cutoff.format(formatter).substring(0, 11) + "0";

        for (Map<String, AtomicInteger> windows : userRequestWindows.values()) {
            windows.keySet().removeIf(key -> key.compareTo(cutoffKey) < 0);
        }
    }

    /**
     * 生成当前时间窗口的key（精确到5分钟）
     */
    private String getCurrentWindowKey() {
        LocalDateTime now = LocalDateTime.now();
        int minute = (now.getMinute() / WINDOW_SIZE_MINUTES) * WINDOW_SIZE_MINUTES;
        return now.format(DateTimeFormatter.ofPattern("yyyyMMddHH")) + String.format("%02d", minute);
    }

    /**
     * 获取用户请求统计报告
     */
    public Map<String, Object> getUserRequestReport(String username) {
        Map<String, Object> report = new HashMap<>();
        report.put("username", username);
        report.put("currentWindow", getUserRequestCount(username));
        report.put("peakRequests", userPeakRequests.getOrDefault(username, 0));

        // 最近6个时间窗口（30分钟）
        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, AtomicInteger> windows = userRequestWindows.get(username);
        if (windows != null) {
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < 6; i++) {
                LocalDateTime windowTime = now.minusMinutes(i * WINDOW_SIZE_MINUTES);
                int minute = (windowTime.getMinute() / WINDOW_SIZE_MINUTES) * WINDOW_SIZE_MINUTES;
                String key = windowTime.format(DateTimeFormatter.ofPattern("yyyyMMddHH")) + String.format("%02d", minute);

                AtomicInteger count = windows.get(key);
                Map<String, Object> windowData = new HashMap<>();
                windowData.put("time", key);
                windowData.put("requests", count != null ? count.get() : 0);
                history.add(windowData);
            }
        }
        report.put("history", history);

        return report;
    }

    /**
     * 获取所有用户的TOP请求量
     */
    public List<Map<String, Object>> getTopUsers(int limit) {
        String currentWindow = getCurrentWindowKey();
        List<Map<String, Object>> topUsers = new ArrayList<>();

        for (Map.Entry<String, Map<String, AtomicInteger>> entry : userRequestWindows.entrySet()) {
            String username = entry.getKey();
            AtomicInteger count = entry.getValue().get(currentWindow);
            if (count != null && count.get() > 0) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("username", username);
                userData.put("requests", count.get());
                userData.put("peak", userPeakRequests.getOrDefault(username, 0));
                topUsers.add(userData);
            }
        }

        topUsers.sort((a, b) -> Integer.compare((Integer) b.get("requests"), (Integer) a.get("requests")));
        return topUsers.subList(0, Math.min(limit, topUsers.size()));
    }
}
