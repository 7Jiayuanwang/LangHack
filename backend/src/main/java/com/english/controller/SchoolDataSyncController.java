package com.english.controller;

import com.english.dto.Result;
import com.english.entity.FieldMapping;
import com.english.entity.SyncLog;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import com.english.service.SchoolDataSyncService;
import com.english.service.SyncMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学校数据同步管理控制器
 * <p>
 * 所有接口仅限管理员和运维角色访问
 */
@RestController
@RequestMapping("/api/admin/sync")
public class SchoolDataSyncController {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataSyncController.class);

    @Autowired
    private SchoolDataSyncService syncService;

    @Autowired
    private SchoolDataApiClient apiClient;

    @Autowired
    private FieldMappingService fieldMappingService;

    @Autowired
    private SyncMonitorService monitorService;

    // =========================================================================
    // 同步操作
    // =========================================================================

    /**
     * 全量同步指定数据源
     * POST /api/admin/sync/full/{dataSource}
     */
    @PostMapping("/full/{dataSource}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<SyncLog> fullSync(@PathVariable String dataSource,
                                     @RequestParam(defaultValue = "MANUAL") String triggerType) {
        logger.info("手动触发全量同步: dataSource={}, triggerType={}", dataSource, triggerType);
        try {
            SyncLog syncLog = syncService.fullSync(dataSource, triggerType, getCurrentOperator());
            return Result.success("全量同步完成", syncLog);
        } catch (Exception e) {
            logger.error("全量同步失败: dataSource={}", dataSource, e);
            return Result.error("全量同步失败，请查看同步日志了解详情");
        }
    }

    /**
     * 增量同步指定数据源
     * POST /api/admin/sync/incremental/{dataSource}
     */
    @PostMapping("/incremental/{dataSource}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<SyncLog> incrementalSync(@PathVariable String dataSource,
                                            @RequestParam(defaultValue = "MANUAL") String triggerType) {
        logger.info("手动触发增量同步: dataSource={}, triggerType={}", dataSource, triggerType);
        try {
            SyncLog syncLog = syncService.incrementalSync(dataSource, triggerType, getCurrentOperator());
            return Result.success("增量同步完成", syncLog);
        } catch (Exception e) {
            logger.error("增量同步失败: dataSource={}", dataSource, e);
            return Result.error("增量同步失败，请查看同步日志了解详情");
        }
    }

    /**
     * 全量同步所有数据源
     * POST /api/admin/sync/full-all
     */
    @PostMapping("/full-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<List<SyncLog>> fullSyncAll(@RequestParam(defaultValue = "MANUAL") String triggerType) {
        logger.info("手动触发全量同步所有数据源: triggerType={}", triggerType);
        try {
            List<SyncLog> logs = syncService.fullSyncAll(triggerType, getCurrentOperator());
            return Result.success("全量同步所有数据源完成", logs);
        } catch (Exception e) {
            logger.error("全量同步所有数据源失败", e);
            return Result.error("全量同步所有数据源失败，请查看同步日志了解详情");
        }
    }

    /**
     * 增量同步所有数据源
     * POST /api/admin/sync/incremental-all
     */
    @PostMapping("/incremental-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<List<SyncLog>> incrementalSyncAll(@RequestParam(defaultValue = "MANUAL") String triggerType) {
        logger.info("手动触发增量同步所有数据源: triggerType={}", triggerType);
        try {
            List<SyncLog> logs = syncService.incrementalSyncAll(triggerType, getCurrentOperator());
            return Result.success("增量同步所有数据源完成", logs);
        } catch (Exception e) {
            logger.error("增量同步所有数据源失败", e);
            return Result.error("增量同步所有数据源失败，请查看同步日志了解详情");
        }
    }

    // =========================================================================
    // 状态查询
    // =========================================================================

    /**
     * 获取同步状态概览
     * GET /api/admin/sync/status
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getSyncStatus() {
        Map<String, Object> overview = syncService.getSyncStatusOverview();
        return Result.success(overview);
    }

    /**
     * 获取同步健康检查结果
     * GET /api/admin/sync/health
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getSyncHealth() {
        Map<String, Object> healthReport = monitorService.checkSyncHealth();
        return Result.success(healthReport);
    }

    /**
     * 数据校验
     * GET /api/admin/sync/validate
     */
    @GetMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> validateData() {
        Map<String, Object> validationResult = syncService.validateData();
        return Result.success(validationResult);
    }

    /**
     * 测试API连通性
     * GET /api/admin/sync/test-connection
     */
    @GetMapping("/test-connection")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Boolean> testConnection() {
        boolean connected = apiClient.testConnection();
        return Result.success(connected ? "API连接正常" : "API连接失败", connected);
    }

    // =========================================================================
    // 同步日志
    // =========================================================================

    /**
     * 获取指定数据源的同步历史
     * GET /api/admin/sync/logs/{dataSource}
     */
    @GetMapping("/logs/{dataSource}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<List<SyncLog>> getSyncLogs(@PathVariable String dataSource,
                                              @RequestParam(defaultValue = "20") int limit) {
        List<SyncLog> logs = monitorService.getSyncHistory(dataSource, limit);
        return Result.success(logs);
    }

    /**
     * 获取最近告警列表
     * GET /api/admin/sync/alerts
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<List<Map<String, Object>>> getAlerts(@RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> alerts = monitorService.getRecentAlerts(limit);
        return Result.success(alerts);
    }

    // =========================================================================
    // 字段映射管理
    // =========================================================================

    /**
     * 获取指定数据源的字段映射配置
     * GET /api/admin/sync/field-mappings/{dataSource}
     */
    @GetMapping("/field-mappings/{dataSource}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<List<FieldMapping>> getFieldMappings(@PathVariable String dataSource) {
        List<FieldMapping> mappings = fieldMappingService.getMappingsByDataSource(dataSource);
        return Result.success(mappings);
    }

    /**
     * 更新字段映射配置
     * PUT /api/admin/sync/field-mappings
     */
    @PutMapping("/field-mappings")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<FieldMapping> updateFieldMapping(@RequestBody FieldMapping fieldMapping) {
        try {
            FieldMapping updated = fieldMappingService.updateFieldMapping(fieldMapping);
            return Result.success("字段映射更新成功", updated);
        } catch (Exception e) {
            logger.error("更新字段映射失败", e);
            return Result.error("更新字段映射失败，请检查输入参数");
        }
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    /**
     * 获取当前操作人（简化实现，从Security上下文获取）
     */
    private String getCurrentOperator() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return "SYSTEM";
    }
}
