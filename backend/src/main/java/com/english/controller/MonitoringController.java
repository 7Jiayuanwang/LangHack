package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.AccessStatsDaily;
import com.english.entity.AccessStatsHourly;
import com.english.entity.AiCallLog;
import com.english.entity.AiCallStatsDaily;
import com.english.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.english.mapper.*;
import com.english.service.TokenBlacklistService;
import com.english.service.UserService;
import com.english.service.impl.AccessMetricsService;
import com.english.service.impl.AiCallMetricsService;
import com.english.service.impl.ElevationService;
import com.english.service.impl.InMemoryLogService;
import com.english.service.impl.SystemMetricsService;
import com.english.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/monitoring")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class MonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
    private AccessLogMapper accessLogMapper;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @Autowired
    private AiCallStatsDailyMapper aiCallStatsDailyMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ElevationService elevationService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private UserService userService;

    @Autowired
    private InMemoryLogService inMemoryLogService;

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
     *
     * 手工鉴权：SSE 跳过 Spring Security 过滤链，所以这里必须显式完成以下检查：
     * 1. Token 签名与未过期（validateToken）
     * 2. Token 是否在黑名单（已登出）
     * 3. 用户仍活跃（存在、未删除、ACTIVE 状态）
     * 4. Token 中的角色与 DB 当前角色一致（防止角色降级后旧 token 还能访问）
     * 5. 角色具备访问权限（ADMIN/OPS 或已提权至 ADMIN/OPS）
     */
    @GetMapping("/realtime/stream")
    public SseEmitter streamRealTimeMetrics(@RequestParam String token) {
        try {
            // 1. 基础 token 校验
            String username = jwtUtil.getUsernameFromToken(token);
            if (username == null || !jwtUtil.validateToken(token, username)) {
                return errorEmitter("Invalid token");
            }

            // 2. 黑名单检查（已登出的 token 不能再订阅 SSE）
            if (tokenBlacklistService.isBlacklisted(token)) {
                logger.warn("Blacklisted token attempted SSE subscription: user={}", username);
                return errorEmitter("Token revoked");
            }

            // 3. 用户状态检查 + 角色一致性检查
            User user = userService.getUserByUsername(username);
            if (user == null) {
                logger.warn("SSE rejected: user '{}' not found (deleted?)", username);
                return errorEmitter("User not found");
            }
            if (!"ACTIVE".equals(user.getStatus())) {
                logger.warn("SSE rejected: user '{}' status is '{}'", username, user.getStatus());
                return errorEmitter("User inactive");
            }
            String tokenRole = jwtUtil.getRoleFromToken(token);
            if (tokenRole == null || !tokenRole.equals(user.getRole())) {
                logger.warn("SSE rejected: user '{}' role changed from '{}' to '{}'",
                        username, tokenRole, user.getRole());
                return errorEmitter("Role changed");
            }

            // 4. 角色访问权限校验（含提权状态）
            boolean hasAccess = "ADMIN".equals(tokenRole) || "OPS".equals(tokenRole);
            if (!hasAccess) {
                String elevatedRole = elevationService.getElevatedRole(username);
                hasAccess = "ADMIN".equals(elevatedRole) || "OPS".equals(elevatedRole);
            }
            if (!hasAccess) {
                return errorEmitter("Insufficient privileges");
            }
        } catch (Exception e) {
            logger.warn("SSE authentication exception: {}", e.getMessage());
            return errorEmitter("Token validation failed");
        }

        return accessMetricsService.registerEmitter();
    }

    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.completeWithError(new SecurityException(message));
        return emitter;
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
            LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();

            if (targetDate.equals(LocalDate.now())) {
                LocalDateTime startTime = targetDate.atStartOfDay();
                LocalDateTime endTime = LocalDateTime.now().plusSeconds(1);
                List<Map<String, Object>> liveTopPages = accessLogMapper.topPagesByDateRange(startTime, endTime, 20);
                return Result.success(normalizeTopPages(liveTopPages));
            }

            QueryWrapper<AccessStatsDaily> wrapper = new QueryWrapper<>();
            wrapper.eq("stat_date", targetDate);
            AccessStatsDaily daily = dailyMapper.selectOne(wrapper);

            if (daily != null && daily.getTopPages() != null) {
                return Result.success(parseStoredTopPages(daily.getTopPages()));
            }
            return Result.success(Collections.emptyList());
        } catch (Exception e) {
            logger.error("获取热门页面失败", e);
            return Result.error("获取热门页面失败");
        }
    }

    private List<Map<String, Object>> parseStoredTopPages(String topPagesJson) {
        if (topPagesJson == null || topPagesJson.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> rawPages = objectMapper.readValue(
                    topPagesJson,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            return normalizeTopPages(rawPages);
        } catch (Exception e) {
            logger.warn("Failed to parse stored top pages JSON", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> normalizeTopPages(List<Map<String, Object>> topPages) {
        if (topPages == null || topPages.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> page : topPages) {
            if (page == null || page.isEmpty()) {
                continue;
            }

            String uri = firstNonBlank(page.get("uri"), page.get("path"));
            if (uri == null) {
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("uri", uri);
            item.put("count", toLong(page.get("count"), page.get("visitCount"), page.get("visit_count")));
            normalized.add(item);
        }

        return normalized;
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }

        for (Object value : values) {
            if (value == null) {
                continue;
            }

            String text = value.toString().trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                return text;
            }
        }

        return null;
    }

    private long toLong(Object... values) {
        if (values == null) {
            return 0L;
        }

        for (Object value : values) {
            if (value == null) {
                continue;
            }

            if (value instanceof Number) {
                return ((Number) value).longValue();
            }

            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // Try next candidate.
            }
        }

        return 0L;
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

    // ========== Backend Application Logs ==========

    @GetMapping("/app-logs")
    public Result<?> getApplicationLogs(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("entries", inMemoryLogService.getRecentLogs(limit, level, keyword));
            data.put("levelCounts", inMemoryLogService.getLevelCounts());
            return Result.success(data);
        } catch (Exception e) {
            logger.error("获取应用日志失败", e);
            return Result.error("获取应用日志失败");
        }
    }

    @GetMapping("/app-logs/download")
    public ResponseEntity<ByteArrayResource> downloadAppLogs(
            @RequestParam(defaultValue = "2000") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<Map<String, Object>> entries = inMemoryLogService.getRecentLogs(limit, level, null);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> entry : entries) {
                String ts = (String) entry.get("timestamp");
                // date-range filter: timestamp starts with "yyyy-MM-dd"
                if (startDate != null && ts != null && ts.compareTo(startDate) < 0) continue;
                if (endDate != null && ts != null && ts.compareTo(endDate + " 23:59:59") > 0) continue;
                sb.append(ts).append(" [").append(entry.get("level")).append("] ")
                  .append(entry.get("logger")).append(" - ").append(entry.get("message"));
                if (entry.get("exception") != null) {
                    sb.append("\n  ").append(entry.get("exception"));
                }
                sb.append("\n");
            }
            String filename = "langhack-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log";
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
        } catch (Exception e) {
            logger.error("下载日志失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
