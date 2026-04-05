package com.english.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.english.dto.Result;
import com.english.service.SystemConfigService;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 公开端点，用于负载均衡器和运维监控。
 * 详细模式需要 OPS_HEALTH_KEY 参数验证。
 */
@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Value("${ops.health.key:}")
    private String opsHealthKey;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SystemConfigService systemConfigService;

    /**
     * 功能开关公开接口 — 前端根据此接口控制导航菜单的可见性
     * 无需鉴权，仅返回 feature_toggle 分组的 key-value 映射
     */
    @GetMapping("/api/public/feature-toggles")
    public Result<java.util.Map<String, String>> getFeatureToggles() {
        try {
            java.util.Map<String, String> toggles = systemConfigService.getByGroup("feature_toggle");
            return Result.success(toggles != null ? toggles : new java.util.HashMap<>());
        } catch (Exception e) {
            logger.warn("获取功能开关失败: {}", e.getMessage());
            return Result.success(new java.util.HashMap<>());
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health(
            @RequestParam(required = false) String key) {

        Map<String, Object> result = new LinkedHashMap<>();
        boolean allHealthy = true;
        boolean isDetailedMode = opsHealthKey != null && !opsHealthKey.isEmpty()
                && key != null && MessageDigest.isEqual(
                    key.getBytes(StandardCharsets.UTF_8),
                    opsHealthKey.getBytes(StandardCharsets.UTF_8));

        // 基础数据库检查（始终执行）
        String dbStatus = checkDatabase();
        if (!dbStatus.startsWith("OK")) {
            allHealthy = false;
        }

        result.put("status", allHealthy ? "UP" : "DOWN");
        result.put("timestamp", System.currentTimeMillis());

        // 详细模式（运维 API Key 验证通过）
        if (isDetailedMode) {
            Map<String, String> checks = new LinkedHashMap<>();
            checks.put("database", dbStatus);
            checks.put("redis", checkRedis());
            checks.put("memory", checkMemory());
            checks.put("disk", checkDisk());
            result.put("checks", checks);
        }

        HttpStatus status = allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(result);
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(3)) {
                return "OK";
            }
            return "FAIL: Connection invalid";
        } catch (Exception e) {
            logger.warn("Health check: database failed - {}", e.getMessage());
            return "FAIL: " + e.getClass().getSimpleName();
        }
    }

    private String checkRedis() {
        try {
            String result = stringRedisTemplate.execute((RedisCallback<String>) connection -> {
                connection.ping();
                return "OK";
            });
            return result != null ? result : "FAIL: null response";
        } catch (Exception e) {
            logger.warn("Health check: redis failed - {}", e.getMessage());
            return "FAIL: " + e.getClass().getSimpleName();
        }
    }

    private String checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory();
        long usedMem = runtime.totalMemory() - runtime.freeMemory();
        int usagePercent = (int) (usedMem * 100 / maxMem);
        if (usagePercent > 90) {
            return "WARN: heap " + usagePercent + "%";
        }
        return "OK: heap " + usagePercent + "%";
    }

    private String checkDisk() {
        try {
            java.io.File root = new java.io.File(".");
            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            if (totalSpace == 0) {
                return "UNKNOWN";
            }
            int usagePercent = (int) ((totalSpace - usableSpace) * 100 / totalSpace);
            if (usagePercent > 90) {
                return "WARN: " + usagePercent + "% used";
            }
            return "OK: " + usagePercent + "% used";
        } catch (Exception e) {
            return "FAIL: " + e.getClass().getSimpleName();
        }
    }
}
