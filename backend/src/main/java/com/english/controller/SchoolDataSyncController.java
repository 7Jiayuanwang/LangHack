package com.english.controller;

import com.english.dto.Result;
import com.english.dto.SchoolDataSyncFilterRequest;
import com.english.entity.FieldMapping;
import com.english.entity.SyncLog;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import com.english.service.SchoolDataSyncService;
import com.english.service.SystemConfigService;
import com.english.service.SyncMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    @Qualifier("schoolDataApiClientDelegate")
    private SchoolDataApiClient apiClient;

    @Autowired
    private FieldMappingService fieldMappingService;

    @Autowired
    private SyncMonitorService monitorService;

    @Autowired(required = false)
    private SystemConfigService systemConfigService;

    @Autowired
    private com.english.service.SchoolDataArchiveService archiveService;

    @Autowired
    private com.english.service.TeacherAccountProvisionService teacherAccountProvisionService;

    @Autowired
    private com.english.service.EnglishTeacherOnboardService englishTeacherOnboardService;

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

    @PostMapping("/full/{dataSource}/filtered")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<SyncLog> fullSyncFiltered(@PathVariable String dataSource,
                                            @RequestBody(required = false) SchoolDataSyncFilterRequest request) {
        String triggerType = request != null && request.getTriggerType() != null && !request.getTriggerType().isEmpty()
                ? request.getTriggerType() : "MANUAL";
        logger.info("手动触发筛选全量同步: dataSource={}, triggerType={}", dataSource, triggerType);
        try {
            SyncLog syncLog = syncService.fullSync(dataSource, triggerType, getCurrentOperator(), request);
            return Result.success("筛选全量同步完成", syncLog);
        } catch (Exception e) {
            logger.error("筛选全量同步失败: dataSource={}", dataSource, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "筛选全量同步失败，请查看同步日志了解详情");
        }
    }

    @PostMapping("/incremental/{dataSource}/filtered")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<SyncLog> incrementalSyncFiltered(@PathVariable String dataSource,
                                                   @RequestBody(required = false) SchoolDataSyncFilterRequest request) {
        String triggerType = request != null && request.getTriggerType() != null && !request.getTriggerType().isEmpty()
                ? request.getTriggerType() : "MANUAL";
        logger.info("手动触发筛选增量同步: dataSource={}, triggerType={}", dataSource, triggerType);
        try {
            SyncLog syncLog = syncService.incrementalSync(dataSource, triggerType, getCurrentOperator(), request);
            return Result.success("筛选增量同步完成", syncLog);
        } catch (Exception e) {
            logger.error("筛选增量同步失败: dataSource={}", dataSource, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "筛选增量同步失败，请查看同步日志了解详情");
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

    /**
     * 按学院链式拉取学生/教师/绑定关系
     * POST /api/admin/sync/chain/students-by-college
     *
     * 链路: school_course(KKYX) → teacher_courses(KCDM) → course_selection(KCBH)
     *      → school_student / school_teacher / teacher_student_binding 全部落库。
     */
    @PostMapping("/chain/students-by-college")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> chainStudentsByCollege(
            @RequestParam String collegeName,
            @RequestParam(defaultValue = "eq") String matchMode,
            @RequestParam(defaultValue = "true") boolean persist,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester) {
        if (!isChainFetchEnabled()) {
            return Result.error("按学院链式拉取功能当前已关闭");
        }
        logger.info("手动触发链式拉取: collegeName={}, matchMode={}, persist={}, academicYear={}, semester={}",
                collegeName, matchMode, persist, academicYear, semester);
        try {
            Map<String, Object> result = syncService.chainFetchStudentsByCollege(
                    collegeName, matchMode, persist, academicYear, semester);
            return Result.success("链式拉取完成", result);
        } catch (Exception e) {
            logger.error("链式拉取失败: collegeName={}", collegeName, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "链式拉取失败");
        }
    }

    /**
     * 按学院批量开通教师账号（SSO-only，直接授予 TEACHER 角色）
     * POST /api/admin/sync/teachers/provision-accounts
     *
     * 依据 school_teacher 目录（需先执行链式同步）批量创建本地 TEACHER 账号，
     * 账号无本地密码，只能通过统一身份认证（SSO）登录；已存在同名账号则跳过。
     */
    @PostMapping("/teachers/provision-accounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> provisionTeacherAccounts(
            @RequestParam String collegeName,
            @RequestParam(defaultValue = "eq") String matchMode) {
        logger.info("手动触发批量开通教师账号: collegeName={}, matchMode={}", collegeName, matchMode);
        try {
            Map<String, Object> result =
                    teacherAccountProvisionService.provisionTeacherAccountsByCollege(collegeName, matchMode);
            return Result.success("批量开通教师账号完成", result);
        } catch (Exception e) {
            logger.error("批量开通教师账号失败: collegeName={}", collegeName, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "批量开通教师账号失败");
        }
    }

    /**
     * 一键接入英语教师：按学年学期从 teacher_courses 拉取课程名含关键词（默认英语）的授课记录，
     * 写 teaching_class → 建 TEACHER 账号 → 逐个教师触发班级+学生绑定。
     * POST /api/admin/sync/teachers/onboard-by-course
     */
    @PostMapping("/teachers/onboard-by-course")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> onboardEnglishTeachers(
            @RequestParam String academicYear,
            @RequestParam String semester,
            @RequestParam(defaultValue = "英语") String courseKeyword) {
        logger.info("手动触发英语教师一键接入: academicYear={}, semester={}, courseKeyword={}",
                academicYear, semester, courseKeyword);
        try {
            Map<String, Object> result =
                    englishTeacherOnboardService.onboardEnglishTeachers(academicYear, semester, courseKeyword);
            return Result.success("英语教师一键接入完成", result);
        } catch (Exception e) {
            logger.error("英语教师一键接入失败: academicYear={}, semester={}", academicYear, semester, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "英语教师一键接入失败");
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
    // 数据归档
    // =========================================================================

    /**
     * 归档指定数据源的历史数据
     * POST /api/admin/sync/archive/{dataSource}
     */
    @PostMapping("/archive/{dataSource}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> archiveDataSource(@PathVariable String dataSource) {
        // 白名单校验：防止路径参数被用于 SQL 标识符注入
        java.util.Set<String> allowed = java.util.Set.of(
            "school_student", "school_teacher", "school_course",
            "teaching_class", "course_schedule", "course_selection",
            "level_exam_scores", "teacher_student_binding",
            "administrative_class", "teaching_plan", "teaching_plan_courses"
        );
        if (!allowed.contains(dataSource)) {
            return Result.error("不支持的数据源: " + dataSource);
        }
        logger.info("手动触发数据归档: dataSource={}", dataSource);
        try {
            Map<String, Object> result = archiveService.archiveDataSource(dataSource);
            return Result.success("归档完成", result);
        } catch (Exception e) {
            logger.error("数据归档失败: dataSource={}", dataSource, e);
            return Result.error("归档失败，请稍后重试");
        }
    }

    /**
     * 归档所有数据源
     * POST /api/admin/sync/archive/all
     */
    @PostMapping("/archive/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Map<String, Object>>> archiveAll() {
        logger.info("手动触发全部数据归档");
        try {
            Map<String, Map<String, Object>> results = archiveService.archiveAll();
            return Result.success("归档完成", results);
        } catch (Exception e) {
            logger.error("全部数据归档失败", e);
            return Result.error("归档失败: " + e.getMessage());
        }
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
    private boolean isChainFetchEnabled() {
        if (systemConfigService == null) {
            return true;
        }
        try {
            String value = systemConfigService.get("feature_toggle", "ops.school_data_chain_fetch", "true");
            return "true".equalsIgnoreCase(value) || "1".equals(value);
        } catch (Exception e) {
            logger.warn("读取 ops.school_data_chain_fetch 失败，默认允许执行: {}", e.getMessage());
            return true;
        }
    }

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
