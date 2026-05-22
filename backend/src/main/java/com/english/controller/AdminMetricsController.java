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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Cacheable(value = "metrics", key = "'user-activity-' + (#startDate == null ? 'null' : #startDate.toString()) + '-' + (#endDate == null ? 'null' : #endDate.toString())", unless = "#result == null")
    public Result<Map<String, Object>> getUserActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDate end = endDate != null ? endDate : now.toLocalDate();
            LocalDate start = startDate != null ? startDate : end.minusDays(29);
            LocalDateTime startDt = start.atStartOfDay();
            LocalDateTime endDt = end.atTime(LocalTime.MAX);

            LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
            LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

            long totalUsers = userMapper.selectCount(
                    new QueryWrapper<com.english.entity.User>().eq("deleted", 0));
            long totalClasses = classMapper.selectCount(null);
            long dau = countDistinctStudents(todayStart, now);
            long mau = countDistinctStudents(monthStart, now);

            List<Map<String, Object>> dauTrend = buildDauTrend(startDt, endDt);

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
    @Cacheable(value = "metrics", key = "'learning-behavior-' + (#startDate == null ? 'null' : #startDate.toString()) + '-' + (#endDate == null ? 'null' : #endDate.toString())", unless = "#result == null")
    public Result<Map<String, Object>> getLearningBehavior(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            LocalDate end = endDate != null ? endDate : LocalDate.now();
            LocalDate start = startDate != null ? startDate : end.minusDays(29);
            LocalDateTime startDt = start.atStartOfDay();
            LocalDateTime endDt = end.atTime(LocalTime.MAX);

            long totalRecords = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .ge("created_time", startDt).le("created_time", endDt));

            List<Map<String, Object>> typeDistribution = buildRecordTypeDistribution(startDt, endDt);

            long totalReads = typeDistribution.stream()
                    .filter(m -> "TEXT_VIEW".equals(m.get("type")))
                    .mapToLong(m -> toLong(m.get("count")))
                    .findFirst().orElse(0L);

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

            List<Map<String, Object>> dailyTrend = buildLearningDailyTrend(startDt, endDt);

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
    @Cacheable(value = "metrics", key = "'ai-usage-' + (#startDate == null ? 'null' : #startDate.toString()) + '-' + (#endDate == null ? 'null' : #endDate.toString())", unless = "#result == null")
    public Result<Map<String, Object>> getAiUsage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDate end = endDate != null ? endDate : now.toLocalDate();
            LocalDate start = startDate != null ? startDate : end.minusDays(29);
            LocalDateTime startDt = start.atStartOfDay();
            LocalDateTime endDt = end.atTime(LocalTime.MAX);
            LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

            // 今日统计
            List<Map<String, Object>> todayStats = aiCallLogMapper.getRealtimeStats(todayStart);
            long todayCalls = todayStats.stream()
                    .mapToLong(m -> toLong(m.get("total_calls"))).sum();

            // 指定范围统计
            List<Map<String, Object>> allStats = aiCallLogMapper.aggregateDaily(startDt, endDt);

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

            // 服务类型分布
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

            // 每日趋势（按日期范围填充）
            Map<String, Long> dailyMap = new LinkedHashMap<>();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                dailyMap.put(d.format(DATE_FMT), 0L);
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
    @Cacheable(value = "metrics", key = "'recommendation-effectiveness-' + (#startDate == null ? 'null' : #startDate.toString()) + '-' + (#endDate == null ? 'null' : #endDate.toString())", unless = "#result == null")
    public Result<Map<String, Object>> getRecommendationEffectiveness(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            LocalDate end = endDate != null ? endDate : LocalDate.now();
            LocalDate start = startDate != null ? startDate : end.minusDays(29);
            LocalDateTime startDt = start.atStartOfDay();
            LocalDateTime endDt = end.atTime(LocalTime.MAX);

            long totalViews = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_VIEW")
                            .ge("created_time", startDt).le("created_time", endDt));
            long totalClicks = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_CLICK")
                            .ge("created_time", startDt).le("created_time", endDt));
            long totalRefreshes = learningRecordMapper.selectCount(
                    new QueryWrapper<com.english.entity.LearningRecord>()
                            .eq("record_type", "RECOMMENDATION_REFRESH")
                            .ge("created_time", startDt).le("created_time", endDt));

            double clickRate = totalViews > 0
                    ? Math.round(totalClicks * 1000.0 / totalViews) / 1000.0 : 0.0;

            List<Map<String, Object>> dailyTrend = buildRecommendationDailyTrend(startDt, endDt);

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

    private List<Map<String, Object>> buildDauTrend(LocalDateTime startDt, LocalDateTime endDt) {
        QueryWrapper<com.english.entity.LearningRecord> qw = new QueryWrapper<>();
        qw.select("DATE(created_time) AS stat_date, COUNT(DISTINCT student_id) AS cnt")
                .ge("created_time", startDt).le("created_time", endDt)
                .groupBy("DATE(created_time)");
        List<Map<String, Object>> rows = learningRecordMapper.selectMaps(qw);
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(String.valueOf(row.get("stat_date")), toLong(row.get("cnt")));
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = startDt.toLocalDate(); !d.isAfter(endDt.toLocalDate()); d = d.plusDays(1)) {
            String date = d.format(DATE_FMT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("count", byDate.getOrDefault(date, 0L));
            trend.add(item);
        }
        return trend;
    }

    private List<Map<String, Object>> buildRecordTypeDistribution(LocalDateTime startDt, LocalDateTime endDt) {
        QueryWrapper<com.english.entity.LearningRecord> qw = new QueryWrapper<>();
        qw.select("record_type AS type, COUNT(*) AS cnt")
                .in("record_type", "TEXT_VIEW", "VIEW", "REVIEW", "TEST", "WORD_COMPLETE",
                        "COMPLETE", "RECOMMENDATION_VIEW", "RECOMMENDATION_CLICK", "RECOMMENDATION_REFRESH")
                .ge("created_time", startDt).le("created_time", endDt)
                .groupBy("record_type")
                .orderByDesc("cnt");
        List<Map<String, Object>> rows = learningRecordMapper.selectMaps(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", String.valueOf(row.get("type")));
            item.put("count", toLong(row.get("cnt")));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildLearningDailyTrend(LocalDateTime startDt, LocalDateTime endDt) {
        QueryWrapper<com.english.entity.LearningRecord> qw = new QueryWrapper<>();
        qw.select("DATE(created_time) AS stat_date, COUNT(*) AS cnt")
                .ge("created_time", startDt).le("created_time", endDt)
                .groupBy("DATE(created_time)");
        List<Map<String, Object>> rows = learningRecordMapper.selectMaps(qw);
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(String.valueOf(row.get("stat_date")), toLong(row.get("cnt")));
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = startDt.toLocalDate(); !d.isAfter(endDt.toLocalDate()); d = d.plusDays(1)) {
            String date = d.format(DATE_FMT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("count", byDate.getOrDefault(date, 0L));
            trend.add(item);
        }
        return trend;
    }

    private List<Map<String, Object>> buildRecommendationDailyTrend(LocalDateTime startDt, LocalDateTime endDt) {
        QueryWrapper<com.english.entity.LearningRecord> qw = new QueryWrapper<>();
        qw.select("DATE(created_time) AS stat_date, record_type, COUNT(*) AS cnt")
                .in("record_type", "RECOMMENDATION_VIEW", "RECOMMENDATION_CLICK")
                .ge("created_time", startDt).le("created_time", endDt)
                .groupBy("DATE(created_time)", "record_type");
        List<Map<String, Object>> rows = learningRecordMapper.selectMaps(qw);
        Map<String, long[]> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String date = String.valueOf(row.get("stat_date"));
            long[] vc = byDate.computeIfAbsent(date, k -> new long[2]);
            String type = String.valueOf(row.get("record_type"));
            long cnt = toLong(row.get("cnt"));
            if ("RECOMMENDATION_VIEW".equals(type)) vc[0] += cnt;
            else if ("RECOMMENDATION_CLICK".equals(type)) vc[1] += cnt;
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = startDt.toLocalDate(); !d.isAfter(endDt.toLocalDate()); d = d.plusDays(1)) {
            String date = d.format(DATE_FMT);
            long[] vc = byDate.getOrDefault(date, new long[2]);
            double clickRate = vc[0] > 0 ? Math.round(vc[1] * 1000.0 / vc[0]) / 1000.0 : 0.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("views", vc[0]);
            item.put("clicks", vc[1]);
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
