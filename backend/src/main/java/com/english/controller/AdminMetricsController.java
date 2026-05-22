package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.Text;
import com.english.mapper.AiCallLogMapper;
import com.english.mapper.ClassMapper;
import com.english.mapper.LearningRecordMapper;
import com.english.mapper.TextMapper;
import com.english.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/metrics")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
public class AdminMetricsController {

    private static final Logger log = LoggerFactory.getLogger(AdminMetricsController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LearningRecordMapper learningRecordMapper;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private ClassMapper classMapper;

    // ── 用户活跃度 ──────────────────────────────────────────────────────────────

    @GetMapping("/user-activity")
    @Cacheable(value = "metrics", key = "'user-activity'", unless = "#result == null")
    public Result<Map<String, Object>> getUserActivity() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
            LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

            long totalUsers = userMapper.selectCount(
                    new QueryWrapper<com.english.entity.User>().eq("deleted", 0));

            long totalClasses = classMapper.selectCount(null);

            // DAU: 今日有学习记录的不重复用户数
            long dau = countDistinctStudents(todayStart, now);

            // MAU: 本月有学习记录的不重复用户数
            long mau = countDistinctStudents(monthStart, now);

            // 最近30天 DAU 趋势
            List<Map<String, Object>> dauTrend = buildDauTrend(30);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalUsers", totalUsers);
            data.put("mau", mau);
            data.put("dau", dau);
            data.put("totalClasses", totalClasses);
            data.put("dauTrend", dauTrend);
            return Result.success(data);
        } catch (Exception e) {
            log.error("Failed to get user activity metrics", e);
            return Result.error("获取用户活跃度数据失败");
        }
    }

    // ── 学习行为 ──────────────────────────────────────────────────────────────

    @GetMapping("/learning-behavior")
    @Cacheable(value = "metrics", key = "'learning-behavior'", unless = "#result == null")
    public Result<Map<String, Object>> getLearningBehavior() {
        try {
            long totalRecords = learningRecordMapper.selectCount(null);

            long totalReads = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "TEXT_VIEW"));

            // 热门文章 Top 10（按 view_count 降序）
            List<Text> hotTexts = textMapper.selectList(
                    new QueryWrapper<Text>()
                            .eq("deleted", 0)
                            .orderByDesc("view_count")
                            .last("LIMIT 10"));
            List<Map<String, Object>> hotArticles = hotTexts.stream().map(t -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", t.getId());
                item.put("title", t.getTitle());
                item.put("viewCount", t.getViewCount() != null ? t.getViewCount() : 0);
                return item;
            }).collect(Collectors.toList());

            // 记录类型分布
            List<Map<String, Object>> typeDistribution = buildRecordTypeDistribution();

            // 最近30天每日学习记录趋势
            List<Map<String, Object>> dailyTrend = buildLearningDailyTrend(30);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalReads", totalReads);
            data.put("totalRecords", totalRecords);
            data.put("hotArticles", hotArticles);
            data.put("recordTypeDistribution", typeDistribution);
            data.put("dailyTrend", dailyTrend);
            return Result.success(data);
        } catch (Exception e) {
            log.error("Failed to get learning behavior metrics", e);
            return Result.error("获取学习行为数据失败");
        }
    }

    // ── AI 调用统计 ──────────────────────────────────────────────────────────

    @GetMapping("/ai-usage")
    @Cacheable(value = "metrics", key = "'ai-usage'", unless = "#result == null")
    public Result<Map<String, Object>> getAiUsage() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
            LocalDateTime thirtyDaysAgo = now.minusDays(30);

            // 今日统计
            List<Map<String, Object>> todayStats = aiCallLogMapper.getRealtimeStats(todayStart);
            long todayCalls = todayStats.stream()
                    .mapToLong(m -> toLong(m.get("total_calls"))).sum();

            // 全量统计（最近30天）
            List<Map<String, Object>> allStats = aiCallLogMapper.aggregateDaily(thirtyDaysAgo, now);

            long totalCalls = allStats.stream()
                    .mapToLong(m -> toLong(m.get("total_calls"))).sum();
            long successCalls = allStats.stream()
                    .mapToLong(m -> toLong(m.get("success_calls"))).sum();
            double successRate = totalCalls > 0
                    ? Math.round(successCalls * 1000.0 / totalCalls) / 1000.0 : 0.0;

            long totalResponseTime = allStats.stream()
                    .mapToLong(m -> toLong(m.get("avg_response_time_ms")) * toLong(m.get("total_calls")))
                    .sum();
            long avgResponseMs = totalCalls > 0 ? totalResponseTime / totalCalls : 0;

            // 服务类型分布（最近30天）
            Map<String, Long> serviceMap = new LinkedHashMap<>();
            for (Map<String, Object> row : allStats) {
                String service = String.valueOf(row.getOrDefault("service_name", "UNKNOWN"));
                serviceMap.merge(service, toLong(row.get("total_calls")), Long::sum);
            }
            List<Map<String, Object>> serviceDistribution = serviceMap.entrySet().stream()
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("service", e.getKey());
                        item.put("count", e.getValue());
                        return item;
                    }).collect(Collectors.toList());

            // 每日趋势（最近30天，按日期聚合）
            Map<String, Long> dailyMap = new LinkedHashMap<>();
            for (int i = 29; i >= 0; i--) {
                dailyMap.put(now.minusDays(i).toLocalDate().format(DATE_FMT), 0L);
            }
            for (Map<String, Object> row : allStats) {
                String date = String.valueOf(row.getOrDefault("stat_date", ""));
                if (dailyMap.containsKey(date)) {
                    dailyMap.merge(date, toLong(row.get("total_calls")), Long::sum);
                }
            }
            List<Map<String, Object>> dailyTrend = dailyMap.entrySet().stream()
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("date", e.getKey());
                        item.put("count", e.getValue());
                        return item;
                    }).collect(Collectors.toList());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalCalls", totalCalls);
            data.put("todayCalls", todayCalls);
            data.put("successRate", successRate);
            data.put("avgResponseMs", avgResponseMs);
            data.put("serviceDistribution", serviceDistribution);
            data.put("dailyTrend", dailyTrend);
            return Result.success(data);
        } catch (Exception e) {
            log.error("Failed to get AI usage metrics", e);
            return Result.error("获取AI调用统计失败");
        }
    }

    // ── 推荐效果 ──────────────────────────────────────────────────────────────

    @GetMapping("/recommendation-effectiveness")
    @Cacheable(value = "metrics", key = "'recommendation-effectiveness'", unless = "#result == null")
    public Result<Map<String, Object>> getRecommendationEffectiveness() {
        try {
            long totalViews = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_VIEW"));
            long totalClicks = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_CLICK"));
            long totalRefreshes = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_REFRESH"));

            double clickRate = totalViews > 0
                    ? Math.round(totalClicks * 1000.0 / totalViews) / 1000.0 : 0.0;

            // 最近30天每日点击率趋势
            List<Map<String, Object>> dailyTrend = buildRecommendationDailyTrend(30);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalViews", totalViews);
            data.put("totalClicks", totalClicks);
            data.put("totalRefreshes", totalRefreshes);
            data.put("clickRate", clickRate);
            data.put("dailyTrend", dailyTrend);
            return Result.success(data);
        } catch (Exception e) {
            log.error("Failed to get recommendation effectiveness metrics", e);
            return Result.error("获取推荐效果数据失败");
        }
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    private long countDistinctStudents(LocalDateTime start, LocalDateTime end) {
        // 使用 selectMaps 聚合查询
        QueryWrapper<com.english.entity.LearningRecord> qw = new QueryWrapper<>();
        qw.select("COUNT(DISTINCT student_id) AS cnt")
                .ge("created_time", start)
                .lt("created_time", end);
        List<Map<String, Object>> result = learningRecordMapper.selectMaps(qw);
        if (result != null && !result.isEmpty()) {
            return toLong(result.get(0).get("cnt"));
        }
        return 0L;
    }

    private List<Map<String, Object>> buildDauTrend(int days) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = now.toLocalDate().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            long count = countDistinctStudents(start, end);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.format(DATE_FMT));
            item.put("count", count);
            trend.add(item);
        }
        return trend;
    }

    private List<Map<String, Object>> buildRecordTypeDistribution() {
        String[] types = {"TEXT_VIEW", "VIEW", "REVIEW", "TEST", "WORD_COMPLETE",
                "RECOMMENDATION_VIEW", "RECOMMENDATION_CLICK", "RECOMMENDATION_REFRESH"};
        List<Map<String, Object>> result = new ArrayList<>();
        for (String type : types) {
            long count = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>().eq("record_type", type));
            if (count > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", type);
                item.put("count", count);
                result.add(item);
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildLearningDailyTrend(int days) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = now.toLocalDate().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            long count = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .ge("created_time", start)
                            .le("created_time", end));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.format(DATE_FMT));
            item.put("count", count);
            trend.add(item);
        }
        return trend;
    }

    private List<Map<String, Object>> buildRecommendationDailyTrend(int days) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = now.toLocalDate().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            long views = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_VIEW")
                            .ge("created_time", start).le("created_time", end));
            long clicks = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_CLICK")
                            .ge("created_time", start).le("created_time", end));
            double clickRate = views > 0 ? Math.round(clicks * 1000.0 / views) / 1000.0 : 0.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.format(DATE_FMT));
            item.put("views", views);
            item.put("clicks", clicks);
            item.put("clickRate", clickRate);
            trend.add(item);
        }
        return trend;
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }
}
