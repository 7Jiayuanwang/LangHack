package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.AccessStatsDaily;
import com.english.entity.AccessStatsHourly;
import com.english.entity.AiCallLog;
import com.english.entity.AiCallStatsDaily;
import com.english.mapper.*;
import com.english.service.impl.AccessMetricsService;
import com.english.service.impl.AiCallMetricsService;
import com.english.service.impl.ElevationService;
import com.english.service.impl.SystemMetricsService;
import com.english.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/monitoring")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class MonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);

    @Autowired
    private AccessMetricsService accessMetricsService;

    @Autowired
    private SystemMetricsService systemMetricsService;

    @Autowired
    private AiCallMetricsService aiCallMetricsService;

    @Autowired
    private AccessStatsHourlyMapper hourlyMapper;

    @Autowired
    private AccessStatsDailyMapper dailyMapper;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @Autowired
    private AiCallStatsDailyMapper aiCallStatsDailyMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ElevationService elevationService;

    @GetMapping("/realtime")
    public Result<?> getRealTimeMetrics() {
        try {
            Map<String, Object> metrics = accessMetricsService.getRealTimeMetrics();
            return Result.success(metrics);
        } catch (Exception e) {
            logger.error("获取实时指标失败", e);
            return Result.error("获取实时指标失败");
        }
    }

    /**
     * SSE endpoint for real-time metrics push.
     * Uses token query parameter since EventSource doesn't support custom headers.
     */
    @GetMapping("/realtime/stream")
    public SseEmitter streamRealTimeMetrics(@RequestParam String token) {
        // Manual token validation for SSE endpoint (EventSource doesn't support custom headers)
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            if (username == null || !jwtUtil.validateToken(token, username)) {
                SseEmitter emitter = new SseEmitter(0L);
                emitter.completeWithError(new SecurityException("Invalid token"));
                return emitter;
            }
            // 验证角色：仅 OPS 和 ADMIN 可访问实时监控流（含提权状态检查）
            String role = jwtUtil.getRoleFromToken(token);
            boolean hasAccess = "ADMIN".equals(role) || "OPS".equals(role);
            if (!hasAccess) {
                // 检查 Redis 中的提权状态
                String elevatedRole = elevationService.getElevatedRole(username);
                hasAccess = "ADMIN".equals(elevatedRole) || "OPS".equals(elevatedRole);
            }
            if (!hasAccess) {
                SseEmitter emitter = new SseEmitter(0L);
                emitter.completeWithError(new SecurityException("Insufficient privileges"));
                return emitter;
            }
        } catch (Exception e) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new SecurityException("Token validation failed"));
            return emitter;
        }

        return accessMetricsService.registerEmitter();
    }

    @GetMapping("/system")
    public Result<?> getSystemMetrics() {
        try {
            Map<String, Object> metrics = systemMetricsService.getSystemMetrics();
            return Result.success(metrics);
        } catch (Exception e) {
            logger.error("获取系统指标失败", e);
            return Result.error("获取系统指标失败");
        }
    }

    @GetMapping("/hourly")
    public Result<?> getHourlyStats(@RequestParam(defaultValue = "24") int hours) {
        try {
            if (hours < 1) hours = 1;
            if (hours > 168) hours = 168; // max 7 days

            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            QueryWrapper<AccessStatsHourly> wrapper = new QueryWrapper<>();
            wrapper.ge("stat_hour", since).orderByAsc("stat_hour");
            List<AccessStatsHourly> stats = hourlyMapper.selectList(wrapper);
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取小时统计失败", e);
            return Result.error("获取小时统计失败");
        }
    }

    @GetMapping("/daily")
    public Result<?> getDailyStats(@RequestParam(defaultValue = "30") int days) {
        try {
            if (days < 1) days = 1;
            if (days > 365) days = 365;

            LocalDate since = LocalDate.now().minusDays(days);
            QueryWrapper<AccessStatsDaily> wrapper = new QueryWrapper<>();
            wrapper.ge("stat_date", since).orderByAsc("stat_date");
            List<AccessStatsDaily> stats = dailyMapper.selectList(wrapper);
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取日统计失败", e);
            return Result.error("获取日统计失败");
        }
    }

    @GetMapping("/top-pages")
    public Result<?> getTopPages(@RequestParam(required = false) String date) {
        try {
            LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now().minusDays(1);

            QueryWrapper<AccessStatsDaily> wrapper = new QueryWrapper<>();
            wrapper.eq("stat_date", targetDate);
            AccessStatsDaily daily = dailyMapper.selectOne(wrapper);

            if (daily != null && daily.getTopPages() != null) {
                return Result.success(daily.getTopPages());
            }
            return Result.success("[]");
        } catch (Exception e) {
            logger.error("获取热门页面失败", e);
            return Result.error("获取热门页面失败");
        }
    }

    // ========== AI Call Monitoring ==========

    @GetMapping("/ai-calls/realtime")
    public Result<?> getAiCallsRealtime() {
        try {
            Map<String, Object> stats = aiCallMetricsService.getTodayStats();
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取AI调用实时统计失败", e);
            return Result.error("获取AI调用实时统计失败");
        }
    }

    @GetMapping("/ai-calls/daily")
    public Result<?> getAiCallsDaily(@RequestParam(defaultValue = "30") int days) {
        try {
            if (days < 1) days = 1;
            if (days > 365) days = 365;

            LocalDate since = LocalDate.now().minusDays(days);
            QueryWrapper<AiCallStatsDaily> wrapper = new QueryWrapper<>();
            wrapper.ge("stat_date", since).orderByAsc("stat_date");
            List<AiCallStatsDaily> stats = aiCallStatsDailyMapper.selectList(wrapper);
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取AI调用日统计失败", e);
            return Result.error("获取AI调用日统计失败");
        }
    }

    @GetMapping("/ai-calls/recent")
    public Result<?> getAiCallsRecent(@RequestParam(defaultValue = "50") int limit) {
        try {
            if (limit < 1) limit = 1;
            if (limit > 200) limit = 200;

            QueryWrapper<AiCallLog> wrapper = new QueryWrapper<>();
            wrapper.orderByDesc("created_time").last("LIMIT " + limit);
            List<AiCallLog> logs = aiCallLogMapper.selectList(wrapper);
            return Result.success(logs);
        } catch (Exception e) {
            logger.error("获取AI调用记录失败", e);
            return Result.error("获取AI调用记录失败");
        }
    }
}
