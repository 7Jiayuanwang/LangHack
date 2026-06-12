package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.AccessLog;
import com.english.mapper.AccessLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 访问日志查询服务
 * 为前端日志管理页面提供访问日志查询功能
 */
@Service
public class AccessLogQueryService {

    @Autowired
    private AccessLogMapper accessLogMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询访问日志
     */
    public Map<String, Object> queryAccessLogs(
            int page,
            int size,
            String username,
            String uri,
            Integer statusCode,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        Page<AccessLog> pageParam = new Page<>(page, size);
        QueryWrapper<AccessLog> wrapper = new QueryWrapper<>();

        // 构建查询条件
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like("username", username.trim());
        }
        if (uri != null && !uri.trim().isEmpty()) {
            wrapper.like("uri", uri.trim());
        }
        if (statusCode != null) {
            wrapper.eq("status_code", statusCode);
        }
        if (startTime != null) {
            wrapper.ge("created_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("created_time", endTime);
        }

        // 按时间倒序
        wrapper.orderByDesc("created_time");

        IPage<AccessLog> result = accessLogMapper.selectPage(pageParam, wrapper);

        // 转换为前端需要的格式
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", records);
        response.put("total", result.getTotal());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        response.put("pages", result.getPages());

        return response;
    }

    /**
     * 获取访问日志统计信息
     */
    public Map<String, Object> getAccessLogStats(LocalDateTime startTime, LocalDateTime endTime) {
        QueryWrapper<AccessLog> wrapper = new QueryWrapper<>();

        if (startTime != null) {
            wrapper.ge("created_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("created_time", endTime);
        }

        // 总请求数
        Long totalRequests = accessLogMapper.selectCount(wrapper);

        // 错误数 (4xx + 5xx)
        QueryWrapper<AccessLog> errorWrapper = new QueryWrapper<>();
        errorWrapper.ge("status_code", 400);
        if (startTime != null) errorWrapper.ge("created_time", startTime);
        if (endTime != null) errorWrapper.le("created_time", endTime);
        Long errorCount = accessLogMapper.selectCount(errorWrapper);

        // 独立用户数 - 不使用select，避免其他字段为null
        QueryWrapper<AccessLog> userWrapper = new QueryWrapper<>();
        if (startTime != null) userWrapper.ge("created_time", startTime);
        if (endTime != null) userWrapper.le("created_time", endTime);
        Long uniqueUsers = (long) accessLogMapper.selectList(userWrapper).stream()
                .map(AccessLog::getUsername)
                .filter(u -> u != null && !u.isEmpty())
                .distinct()
                .count();

        // 平均响应时间 - 不使用select，避免字段为null
        QueryWrapper<AccessLog> timeWrapper = new QueryWrapper<>();
        if (startTime != null) timeWrapper.ge("created_time", startTime);
        if (endTime != null) timeWrapper.le("created_time", endTime);
        List<AccessLog> logs = accessLogMapper.selectList(timeWrapper);

        long avgResponseTime = logs.isEmpty() ? 0 :
                (long) logs.stream()
                        .mapToLong(log -> log.getResponseTimeMs() != null ? log.getResponseTimeMs() : 0)
                        .average()
                        .orElse(0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRequests", totalRequests);
        stats.put("errorCount", errorCount);
        stats.put("uniqueUsers", uniqueUsers);
        stats.put("avgResponseTimeMs", avgResponseTime);
        stats.put("errorRate", totalRequests > 0 ?
                String.format("%.2f", (errorCount * 100.0 / totalRequests)) : "0.00");

        return stats;
    }

    /**
     * 获取访问趋势数据（按小时聚合）
     */
    public List<Map<String, Object>> getAccessTrend(LocalDateTime startTime, LocalDateTime endTime) {
        return accessLogMapper.aggregateHourly(startTime, endTime);
    }

    /**
     * 获取热门页面排行
     */
    public List<Map<String, Object>> getTopPages(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return accessLogMapper.topPagesByDateRange(startTime, endTime, limit);
    }

    /**
     * 获取响应时间分布（6档 + P50/P90/P95/P99）
     */
    public Map<String, Object> getResponseTimeDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        QueryWrapper<AccessLog> wrapper = new QueryWrapper<>();
        if (startTime != null) wrapper.ge("created_time", startTime);
        if (endTime != null) wrapper.le("created_time", endTime);
        List<AccessLog> logs = accessLogMapper.selectList(wrapper);

        int t0 = 0, t50 = 0, t100 = 0, t200 = 0, t500 = 0, t2000 = 0;
        long[] sorted = logs.stream()
                .map(AccessLog::getResponseTimeMs)
                .filter(t -> t != null)
                .mapToLong(Long::longValue)
                .sorted()
                .toArray();

        for (long t : sorted) {
            if (t < 50) t0++;
            else if (t < 100) t50++;
            else if (t < 200) t100++;
            else if (t < 500) t200++;
            else if (t < 2000) t500++;
            else t2000++;
        }

        int n = sorted.length;
        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("under50", t0);
        distribution.put("s50to100", t50);
        distribution.put("s100to200", t100);
        distribution.put("s200to500", t200);
        distribution.put("s500to2000", t500);
        distribution.put("over2000", t2000);
        distribution.put("p50", n > 0 ? sorted[(int) (n * 0.50)] : 0);
        distribution.put("p90", n > 0 ? sorted[(int) (n * 0.90)] : 0);
        distribution.put("p95", n > 0 ? sorted[Math.min((int) (n * 0.95), n - 1)] : 0);
        distribution.put("p99", n > 0 ? sorted[Math.min((int) (n * 0.99), n - 1)] : 0);

        return distribution;
    }

    /**
     * 获取登录/退出统计
     */
    public Map<String, Object> getLoginStats(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        QueryWrapper<AccessLog> wrapper = new QueryWrapper<>();
        wrapper.in("uri", "/api/auth/login", "/api/auth/logout");
        if (startTime != null) wrapper.ge("created_time", startTime);
        if (endTime != null) wrapper.le("created_time", endTime);
        wrapper.orderByDesc("created_time");

        List<AccessLog> logs = accessLogMapper.selectList(wrapper);

        long loginSuccess = logs.stream()
                .filter(l -> "/api/auth/login".equals(l.getUri())
                        && l.getStatusCode() != null && l.getStatusCode() < 400)
                .count();
        long loginFail = logs.stream()
                .filter(l -> "/api/auth/login".equals(l.getUri())
                        && l.getStatusCode() != null && l.getStatusCode() >= 400)
                .count();
        long logoutCount = logs.stream()
                .filter(l -> "/api/auth/logout".equals(l.getUri()))
                .count();

        List<Map<String, Object>> records = logs.stream()
                .limit(limit)
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", l.getUsername() != null ? l.getUsername() : "未知");
                    m.put("action", "/api/auth/login".equals(l.getUri()) ? "登录" : "退出");
                    m.put("success", l.getStatusCode() != null && l.getStatusCode() < 400);
                    m.put("statusCode", l.getStatusCode());
                    m.put("ipAddress", l.getIpAddress());
                    m.put("createdTime", l.getCreatedTime() != null ? l.getCreatedTime().format(FORMATTER) : null);
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loginSuccess", loginSuccess);
        result.put("loginFail", loginFail);
        result.put("logoutCount", logoutCount);
        result.put("records", records);
        return result;
    }

    /**
     * 转换实体为Map
     */
    private Map<String, Object> convertToMap(AccessLog log) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", log.getId());
        map.put("userId", log.getUserId());
        map.put("username", log.getUsername() != null ? log.getUsername() : "匿名");
        map.put("method", log.getMethod());
        map.put("uri", log.getUri());
        map.put("queryString", log.getQueryString());
        map.put("statusCode", log.getStatusCode());
        map.put("responseTimeMs", log.getResponseTimeMs());
        map.put("ipAddress", log.getIpAddress());
        map.put("userAgent", log.getUserAgent());
        map.put("createdTime", log.getCreatedTime() != null ?
                log.getCreatedTime().format(FORMATTER) : null);
        return map;
    }
}
