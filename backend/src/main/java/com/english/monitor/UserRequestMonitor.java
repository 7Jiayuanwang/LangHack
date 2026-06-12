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
import java.util.stream.Collectors;

@Component
public class UserRequestMonitor {

    private static final Logger log = LoggerFactory.getLogger(UserRequestMonitor.class);
    private final Map<String, Map<String, AtomicInteger>> userRequestWindows = new ConcurrentHashMap<>();

    public void recordRequest(String username) {
        if (username == null || "anonymousUser".equals(username)) return;

        String window = getCurrentWindowKey();
        userRequestWindows.computeIfAbsent(username, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(window, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public int getUserRequestCount(String username) {
        if (username == null) return 0;
        Map<String, AtomicInteger> windows = userRequestWindows.get(username);
        if (windows == null) return 0;

        AtomicInteger count = windows.get(getCurrentWindowKey());
        return count == null ? 0 : count.get();
    }

    @Scheduled(fixedRate = 300000)
    public void analyze() {
        try {
            String current = getCurrentWindowKey();
            List<Integer> counts = new ArrayList<>();

            for (Map<String, AtomicInteger> windows : userRequestWindows.values()) {
                AtomicInteger count = windows.get(current);
                if (count != null && count.get() > 100) {
                    counts.add(count.get());
                }
            }

            if (!counts.isEmpty()) {
                double avg = counts.stream().mapToInt(Integer::intValue).average().orElse(0);
                double threshold = avg + 3 * Math.sqrt(counts.stream()
                        .mapToDouble(c -> Math.pow(c - avg, 2)).average().orElse(0));

                for (Map.Entry<String, Map<String, AtomicInteger>> entry : userRequestWindows.entrySet()) {
                    AtomicInteger count = entry.getValue().get(current);
                    if (count != null && count.get() > threshold) {
                        log.warn("[异常访问] 用户 {} 过去5分钟: {} 次请求（阈值: {:.0f}）",
                                entry.getKey(), count.get(), threshold);
                    }
                }
            }

            cleanup();
        } catch (Exception e) {
            log.error("用户请求分析失败", e);
        }
    }

    private void cleanup() {
        String cutoff = LocalDateTime.now().minusMinutes(30)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")).substring(0, 11) + "0";
        for (Map<String, AtomicInteger> windows : userRequestWindows.values()) {
            windows.keySet().removeIf(key -> key.compareTo(cutoff) < 0);
        }
    }

    private String getCurrentWindowKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                .substring(0, 11) + (LocalDateTime.now().getMinute() / 5) * 5;
    }

    public List<Map<String, Object>> getTopUsers(int limit) {
        String window = getCurrentWindowKey();
        return userRequestWindows.entrySet().stream()
                .map(e -> {
                    AtomicInteger count = e.getValue().get(window);
                    if (count == null) return null;
                    Map<String, Object> m = new HashMap<>();
                    m.put("username", e.getKey());
                    m.put("requests", count.get());
                    return m;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare((Integer)b.get("requests"), (Integer)a.get("requests")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getUserRequestReport(String username) {
        Map<String, Object> report = new HashMap<>();
        Map<String, AtomicInteger> windows = userRequestWindows.get(username);

        if (windows == null) {
            report.put("currentWindow", 0);
            report.put("totalWindows", 0);
            return report;
        }

        String current = getCurrentWindowKey();
        AtomicInteger currentCount = windows.get(current);
        report.put("currentWindow", currentCount != null ? currentCount.get() : 0);
        report.put("totalWindows", windows.size());

        return report;
    }
}
