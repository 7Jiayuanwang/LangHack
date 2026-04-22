package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.config.SchoolDataProperties;
import com.english.dto.SchoolDataSyncFilterRequest;
import com.english.entity.*;
import com.english.exception.BusinessException;
import com.english.mapper.*;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import com.english.service.SchoolDataSyncService;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 学校数据同步核心服务实现
 * <p>
 * 核心流程: fetch API data -> apply field mapping -> UPSERT per record -> log results
 * 事务策略: 每个数据源一个独立事务，失败不影响其他数据源
 */
@Service
public class SchoolDataSyncServiceImpl implements SchoolDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataSyncServiceImpl.class);

    /** 支持的数据源列表 */
    private static final List<String> DATA_SOURCES = List.of(
            "administrative_class", "school_course", "teaching_class",
            "course_schedule", "course_selection", "level_exam_scores",
            "teaching_plans", "teacher_courses", "student_schedule"
    );

    /** 防止重入标志 */
    private final AtomicBoolean fullSyncRunning = new AtomicBoolean(false);
    private final AtomicBoolean incrementalSyncRunning = new AtomicBoolean(false);

    private final SchoolDataApiClient apiClient;
    private final FieldMappingService fieldMappingService;
    private final SyncLogMapper syncLogMapper;
    private final AdministrativeClassMapper administrativeClassMapper;
    private final SchoolCourseMapper schoolCourseMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseScheduleMapper courseScheduleMapper;
    private final CourseSelectionMapper courseSelectionMapper;
    private final LevelExamScoreMapper levelExamScoreMapper;
    private final TeachingPlanMapper teachingPlanMapper;
    private final TeachingPlanCourseMapper teachingPlanCourseMapper;
    private final StudentScheduleMapper studentScheduleMapper;
    private final SchoolStudentMapper schoolStudentMapper;
    private final SchoolTeacherMapper schoolTeacherMapper;
    private final TeacherStudentBindingMapper teacherStudentBindingMapper;
    private final ObjectMapper objectMapper;
    private final SchoolDataProperties properties;

    public SchoolDataSyncServiceImpl(@Qualifier("schoolDataApiClientDelegate") SchoolDataApiClient apiClient,
                                     FieldMappingService fieldMappingService,
                                     SyncLogMapper syncLogMapper,
                                     AdministrativeClassMapper administrativeClassMapper,
                                     SchoolCourseMapper schoolCourseMapper,
                                     TeachingClassMapper teachingClassMapper,
                                     CourseScheduleMapper courseScheduleMapper,
                                     CourseSelectionMapper courseSelectionMapper,
                                     LevelExamScoreMapper levelExamScoreMapper,
                                     TeachingPlanMapper teachingPlanMapper,
                                     TeachingPlanCourseMapper teachingPlanCourseMapper,
                                     StudentScheduleMapper studentScheduleMapper,
                                     SchoolStudentMapper schoolStudentMapper,
                                     SchoolTeacherMapper schoolTeacherMapper,
                                     TeacherStudentBindingMapper teacherStudentBindingMapper,
                                     ObjectMapper objectMapper,
                                     SchoolDataProperties properties) {
        this.apiClient = apiClient;
        this.fieldMappingService = fieldMappingService;
        this.syncLogMapper = syncLogMapper;
        this.administrativeClassMapper = administrativeClassMapper;
        this.schoolCourseMapper = schoolCourseMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.courseScheduleMapper = courseScheduleMapper;
        this.courseSelectionMapper = courseSelectionMapper;
        this.levelExamScoreMapper = levelExamScoreMapper;
        this.teachingPlanMapper = teachingPlanMapper;
        this.teachingPlanCourseMapper = teachingPlanCourseMapper;
        this.studentScheduleMapper = studentScheduleMapper;
        this.schoolStudentMapper = schoolStudentMapper;
        this.schoolTeacherMapper = schoolTeacherMapper;
        this.teacherStudentBindingMapper = teacherStudentBindingMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // =========================================================================
    // 全量同步
    // =========================================================================

    @Override
    public SyncLog fullSync(String dataSource, String triggerType, String operator) {
        return fullSync(dataSource, triggerType, operator, null);
    }

    @Override
    public SyncLog fullSync(String dataSource, String triggerType, String operator, SchoolDataSyncFilterRequest request) {
        if (!DATA_SOURCES.contains(dataSource)) {
            throw new BusinessException("不支持的数据源: " + dataSource);
        }

        if (!fullSyncRunning.compareAndSet(false, true)) {
            throw new BusinessException("全量同步正在进行中，请稍后再试");
        }

        LocalDateTime startTime = LocalDateTime.now();
        SyncLog syncLog = createSyncLog("FULL", dataSource, "REPLACE_ALL", triggerType, operator, startTime);

        try {
            logger.info("开始全量同步数据源: {}, 触发方式: {}", dataSource, triggerType);
            SyncResult result = doFullSync(dataSource, request);
            completeSyncLog(syncLog, result, startTime);
            logger.info("全量同步完成: 数据源={}, 总数={}, 成功={}, 失败={}, 跳过={}",
                    dataSource, result.total, result.success, result.fail, result.skip);
        } catch (Exception e) {
            logger.error("全量同步失败: 数据源={}", dataSource, e);
            failSyncLog(syncLog, e, startTime);
        } finally {
            fullSyncRunning.set(false);
        }

        return syncLog;
    }

    @Override
    public List<SyncLog> fullSyncAll(String triggerType, String operator) {
        if (!fullSyncRunning.compareAndSet(false, true)) {
            throw new BusinessException("全量同步正在进行中，请稍后再试");
        }

        List<SyncLog> logs = new ArrayList<>();
        try {
            for (String dataSource : DATA_SOURCES) {
                try {
                    logger.info("开始全量同步数据源: {}", dataSource);
                    LocalDateTime startTime = LocalDateTime.now();
                    SyncLog syncLog = createSyncLog("FULL", dataSource, "REPLACE_ALL", triggerType, operator, startTime);
                    SyncResult result = doFullSync(dataSource);
                    completeSyncLog(syncLog, result, startTime);
                    logs.add(syncLog);
                } catch (Exception e) {
                    logger.error("全量同步数据源 {} 失败，继续下一个", dataSource, e);
                    SyncLog failLog = createSyncLog("FULL", dataSource, "REPLACE_ALL", triggerType, operator, LocalDateTime.now());
                    failSyncLog(failLog, e, LocalDateTime.now());
                    logs.add(failLog);
                }
            }
        } finally {
            fullSyncRunning.set(false);
        }

        return logs;
    }

    // =========================================================================
    // 增量同步
    // =========================================================================

    @Override
    public SyncLog incrementalSync(String dataSource, String triggerType, String operator) {
        return incrementalSync(dataSource, triggerType, operator, null);
    }

    @Override
    public SyncLog incrementalSync(String dataSource, String triggerType, String operator,
                                   SchoolDataSyncFilterRequest request) {
        if (!DATA_SOURCES.contains(dataSource)) {
            throw new BusinessException("不支持的数据源: " + dataSource);
        }

        if (!incrementalSyncRunning.compareAndSet(false, true)) {
            throw new BusinessException("增量同步正在进行中，请稍后再试");
        }

        LocalDateTime startTime = LocalDateTime.now();
        SyncLog syncLog = createSyncLog("INCREMENTAL", dataSource, "UPSERT", triggerType, operator, startTime);

        try {
            logger.info("开始增量同步数据源: {}, 触发方式: {}", dataSource, triggerType);
            SyncResult result = doIncrementalSync(dataSource, request);
            completeSyncLog(syncLog, result, startTime);
            logger.info("增量同步完成: 数据源={}, 总数={}, 成功={}, 失败={}, 跳过={}",
                    dataSource, result.total, result.success, result.fail, result.skip);
        } catch (Exception e) {
            logger.error("增量同步失败: 数据源={}", dataSource, e);
            failSyncLog(syncLog, e, startTime);
        } finally {
            incrementalSyncRunning.set(false);
        }

        return syncLog;
    }

    @Override
    public List<SyncLog> incrementalSyncAll(String triggerType, String operator) {
        if (!incrementalSyncRunning.compareAndSet(false, true)) {
            throw new BusinessException("增量同步正在进行中，请稍后再试");
        }

        List<SyncLog> logs = new ArrayList<>();
        try {
            for (String dataSource : DATA_SOURCES) {
                try {
                    logger.info("开始增量同步数据源: {}", dataSource);
                    LocalDateTime startTime = LocalDateTime.now();
                    SyncLog syncLog = createSyncLog("INCREMENTAL", dataSource, "UPSERT", triggerType, operator, startTime);
                    SyncResult result = doIncrementalSync(dataSource);
                    completeSyncLog(syncLog, result, startTime);
                    logs.add(syncLog);
                } catch (Exception e) {
                    logger.error("增量同步数据源 {} 失败，继续下一个", dataSource, e);
                    SyncLog failLog = createSyncLog("INCREMENTAL", dataSource, "UPSERT", triggerType, operator, LocalDateTime.now());
                    failSyncLog(failLog, e, LocalDateTime.now());
                    logs.add(failLog);
                }
            }
        } finally {
            incrementalSyncRunning.set(false);
        }

        return logs;
    }

    // =========================================================================
    // 状态查询 & 数据校验
    // =========================================================================

    @Override
    public Map<String, Object> getSyncStatusOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // 获取每个数据源最近一次同步日志
        List<SyncLog> latestLogs = syncLogMapper.selectLatestPerDataSource();
        Map<String, SyncLog> latestMap = new LinkedHashMap<>();
        for (SyncLog log : latestLogs) {
            latestMap.put(log.getDataSource(), log);
        }

        for (String dataSource : DATA_SOURCES) {
            Map<String, Object> sourceStatus = new LinkedHashMap<>();
            SyncLog latest = latestMap.get(dataSource);

            if (latest != null) {
                sourceStatus.put("lastSyncTime", latest.getCreatedAt());
                sourceStatus.put("lastSyncStatus", latest.getStatus());
                sourceStatus.put("lastSyncType", latest.getSyncType());
                sourceStatus.put("totalCount", latest.getTotalCount());
                sourceStatus.put("successCount", latest.getSuccessCount());
                sourceStatus.put("failCount", latest.getFailCount());
                sourceStatus.put("errorMessage", latest.getErrorMessage());
            } else {
                sourceStatus.put("lastSyncTime", null);
                sourceStatus.put("lastSyncStatus", "NEVER");
                sourceStatus.put("lastSyncType", null);
                sourceStatus.put("totalCount", 0);
                sourceStatus.put("successCount", 0);
                sourceStatus.put("failCount", 0);
                sourceStatus.put("errorMessage", null);
            }

            // 获取本地记录数
            sourceStatus.put("localCount", getLocalCount(dataSource));
            overview.put(dataSource, sourceStatus);
        }

        return overview;
    }

    @Override
    public Map<String, Object> validateData() {
        Map<String, Object> validationResult = new LinkedHashMap<>();
        int totalIssues = 0;

        for (String dataSource : DATA_SOURCES) {
            Map<String, Object> sourceResult = new LinkedHashMap<>();
            List<String> issues = new ArrayList<>();

            // 检查是否有字段映射配置
            List<FieldMapping> mappings = fieldMappingService.getMappingsByDataSource(dataSource);
            if (mappings.isEmpty()) {
                issues.add("无字段映射配置");
            } else {
                validateCriticalMappings(dataSource, mappings, issues);
            }

            // 检查最近一次同步是否失败
            SyncLog latestLog = syncLogMapper.selectLatestByDataSource(dataSource);
            if (latestLog != null && "FAILED".equals(latestLog.getStatus())) {
                issues.add("最近一次同步失败: " + (latestLog.getErrorMessage() != null
                        ? latestLog.getErrorMessage().substring(0, Math.min(100, latestLog.getErrorMessage().length()))
                        : "未知错误"));
            }

            // 检查本地数据量
            long localCount = getLocalCount(dataSource);
            sourceResult.put("localCount", localCount);
            sourceResult.put("issues", issues);
            sourceResult.put("healthy", issues.isEmpty());

            totalIssues += issues.size();
            validationResult.put(dataSource, sourceResult);
        }

        validationResult.put("totalIssues", totalIssues);
        validationResult.put("overallHealthy", totalIssues == 0);
        validationResult.put("validatedAt", LocalDateTime.now());

        return validationResult;
    }

    private void validateCriticalMappings(String dataSource, List<FieldMapping> mappings, List<String> issues) {
        Set<String> targetFields = mappings.stream()
                .map(FieldMapping::getTargetField)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        switch (dataSource) {
            case "teacher_courses":
                if (!targetFields.contains("classId")) {
                    issues.add("teacher_courses 缺少关键映射: classId");
                }
                if (!targetFields.contains("courseCode")) {
                    issues.add("teacher_courses 缺少关键映射: courseCode");
                }
                break;
            case "course_selection":
                if (!targetFields.contains("studentId")) {
                    issues.add("course_selection 缺少关键映射: studentId");
                }
                if (!targetFields.contains("classId")) {
                    issues.add("course_selection 缺少关键映射: classId（课程班号）");
                }
                if (targetFields.contains("courseCode") && !targetFields.contains("classId")) {
                    issues.add("course_selection 疑似仍将 KCBH 映射到 courseCode");
                }
                break;
            case "student_schedule":
                if (!targetFields.contains("classId")) {
                    issues.add("student_schedule 缺少关键映射: classId");
                }
                if (!targetFields.contains("courseCode")) {
                    issues.add("student_schedule 缺少关键映射: courseCode");
                }
                break;
            case "course_schedule":
                if (!targetFields.contains("classId")) {
                    issues.add("course_schedule 缺少关键映射: classId");
                }
                break;
            case "level_exam_scores":
                if (!targetFields.contains("studentId")) {
                    issues.add("level_exam_scores 缺少关键映射: studentId");
                }
                if (!targetFields.contains("examName")) {
                    issues.add("level_exam_scores 缺少关键映射: examName");
                }
                break;
            default:
                break;
        }
    }

    // =========================================================================
    // 全量同步核心逻辑
    // =========================================================================

    /**
     * 执行单个数据源的全量同步
     */
    private SyncResult doFullSync(String dataSource) {
        return doFullSync(dataSource, null);
    }

    private SyncResult doFullSync(String dataSource, SchoolDataSyncFilterRequest request) {
        if ("student_schedule".equals(dataSource) && hasFilterRequest(request)) {
            return syncFilteredStudentSchedule(request);
        }
        Map<String, String> upstreamParams = buildUpstreamParams(dataSource, request);
        List<Map<String, Object>> fetchData = fetchFromApi(dataSource, upstreamParams);
        fetchData = applyFilterGroups(dataSource, fetchData, request);
        if (fetchData.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }

        return persistData(dataSource, fetchData);
    }

    /**
     * 执行单个数据源的增量同步
     * <p>
     * CDSP API 实际仅支持基本分页参数（$top/$skip），不支持 $filter/$select/$beauty 等 OData 扩展参数
     * （会触发 IY1103 "NOT SUPPORTED: URI: customQuery"），
     * 因此增量同步实际退化为全量拉取 + 按业务主键 UPSERT（由 upsertEntity 内部处理去重/覆盖）。
     * 未来如需真正增量，需改用 $filter=<timestamp_field> gt <since_datetime> 的 OData 表达式（如果 API 支持）。
     */
    private SyncResult doIncrementalSync(String dataSource) {
        return doIncrementalSync(dataSource, null);
    }

    private SyncResult doIncrementalSync(String dataSource, SchoolDataSyncFilterRequest request) {
        if ("student_schedule".equals(dataSource) && hasFilterRequest(request)) {
            return syncFilteredStudentSchedule(request);
        }
        Map<String, String> upstreamParams = buildUpstreamParams(dataSource, request);
        List<Map<String, Object>> fetchData = fetchFromApi(dataSource, upstreamParams);
        fetchData = applyFilterGroups(dataSource, fetchData, request);
        if (fetchData.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }

        return persistData(dataSource, fetchData);
    }

    // =========================================================================
    // 数据获取
    // =========================================================================

    /**
     * 从学校API获取数据
     */
    private List<Map<String, Object>> fetchFromApi(String dataSource, Map<String, String> extraParams) {
        Map<String, String> params = extraParams != null ? new HashMap<>(extraParams) : new HashMap<>();

        switch (dataSource) {
            case "administrative_class":
                return apiClient.fetchAdministrativeClasses(params);
            case "school_course":
                return apiClient.fetchSchoolCourses(params);
            case "teaching_class":
                return apiClient.fetchTeachingClasses(params);
            case "course_schedule":
                return apiClient.fetchCourseSchedules(params);
            case "course_selection":
                return apiClient.fetchCourseSelections(params);
            case "level_exam_scores":
                return apiClient.fetchLevelExamScores(params);
            case "teaching_plans":
                return apiClient.fetchTeachingPlans(params);
            case "teacher_courses":
                return apiClient.fetchTeacherCourses(params);
            case "student_schedule":
                logger.info("student_schedule requires studentId parameter, skipping generic batch fetch");
                return Collections.emptyList();
            default:
                logger.warn("Unknown data source: {}", dataSource);
                return Collections.emptyList();
        }
    }

    private boolean hasFilterRequest(SchoolDataSyncFilterRequest request) {
        return request != null && request.getFilterGroups() != null && !request.getFilterGroups().isEmpty();
    }

    private Map<String, String> buildUpstreamParams(String dataSource, SchoolDataSyncFilterRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        if (!hasFilterRequest(request)) {
            return params;
        }
        for (SchoolDataSyncFilterRequest.FilterGroup group : request.getFilterGroups()) {
            if (group == null || group.getConditions() == null) {
                continue;
            }
            for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                if (condition == null || condition.getField() == null || condition.getValue() == null || condition.getValue().trim().isEmpty()) {
                    continue;
                }
                String field = condition.getField().trim();
                String value = condition.getValue().trim();
                if (!"eq".equalsIgnoreCase(defaultString(condition.getOperator(), "eq"))) {
                    continue;
                }
                switch (dataSource) {
                    case "administrative_class":
                        if ("collegeCode".equals(field)) params.put("XSH", value);
                        else if ("grade".equals(field)) params.put("NJ", value);
                        break;
                    case "school_course":
                        // KKYX=开课院系（学院名），CDSP 对该字段可能忽略导致全校返回，客户端兜底过滤
                        if ("collegeName".equals(field)) params.put("kkyx", value);
                        else if ("courseCode".equals(field)) params.put("kcdm", value);
                        break;
                    case "course_schedule":
                        if ("classId".equals(field)) params.put("xkkch", value);
                        else if ("academicYear".equals(field)) params.put("xn", value);
                        else if ("semester".equals(field)) params.put("xq", "1".equals(value) ? "秋" : "2".equals(value) ? "春" : value);
                        else if ("dayOfWeek".equals(field)) params.put("skxqj", value);
                        else if ("period".equals(field)) params.put("skjc", value);
                        break;
                    case "course_selection":
                        if ("studentId".equals(field)) params.put("XH", value);
                        else if ("academicYear".equals(field)) params.put("XN", value);
                        else if ("semester".equals(field)) params.put("XQ", "1".equals(value) ? "秋" : "2".equals(value) ? "春" : value);
                        else if ("courseClassCode".equals(field) || "classId".equals(field)) params.put("KCBH", value);
                        break;
                    case "level_exam_scores":
                        if ("studentId".equals(field)) params.put("xh", value);
                        break;
                    case "teaching_plans":
                        if ("grade".equals(field)) params.put("nj", value);
                        break;
                    case "teacher_courses":
                        if ("teacherId".equals(field)) params.put("rkjsh", value);
                        else if ("courseCode".equals(field)) params.put("kcdm", value);
                        else if ("academicYear".equals(field)) params.put("xn", value);
                        else if ("semester".equals(field)) params.put("xq", "1".equals(value) ? "秋" : "2".equals(value) ? "春" : value);
                        break;
                    default:
                        break;
                }
            }
        }
        return params;
    }

    private List<Map<String, Object>> applyFilterGroups(String dataSource, List<Map<String, Object>> fetchData,
                                                        SchoolDataSyncFilterRequest request) {
        if (!hasFilterRequest(request) || fetchData == null || fetchData.isEmpty()) {
            return fetchData == null ? Collections.emptyList() : fetchData;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : fetchData) {
            if (matchesAllGroups(dataSource, row, request.getFilterGroups())) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private boolean matchesAllGroups(String dataSource, Map<String, Object> row,
                                     List<SchoolDataSyncFilterRequest.FilterGroup> groups) {
        for (SchoolDataSyncFilterRequest.FilterGroup group : groups) {
            if (group == null || group.getConditions() == null || group.getConditions().isEmpty()) {
                continue;
            }
            boolean isOr = "OR".equalsIgnoreCase(defaultString(group.getRelation(), "AND"));
            boolean groupResult = isOr ? false : true;
            for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                boolean conditionMatched = matchesCondition(row, condition);
                if (isOr) {
                    groupResult = groupResult || conditionMatched;
                    if (groupResult) break;
                } else {
                    groupResult = groupResult && conditionMatched;
                    if (!groupResult) break;
                }
            }
            if (!groupResult) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCondition(Map<String, Object> row, SchoolDataSyncFilterRequest.FilterCondition condition) {
        if (condition == null || condition.getField() == null) {
            return true;
        }
        String operator = defaultString(condition.getOperator(), "eq").toLowerCase(Locale.ROOT);
        String actual = stringValue(row.get(condition.getField()));
        String expected = defaultString(condition.getValue(), "").trim();
        switch (operator) {
            case "contains":
                return actual.contains(expected);
            case "gte":
                return compareAsNumber(actual, expected) >= 0;
            case "lte":
                return compareAsNumber(actual, expected) <= 0;
            case "in":
                return Arrays.stream(expected.split(",")).map(String::trim).anyMatch(actual::equals);
            case "eq":
            default:
                return actual.equals(expected);
        }
    }

    private int compareAsNumber(String actual, String expected) {
        try {
            return new java.math.BigDecimal(actual).compareTo(new java.math.BigDecimal(expected));
        } catch (Exception e) {
            return actual.compareTo(expected);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private SyncResult syncFilteredStudentSchedule(SchoolDataSyncFilterRequest request) {
        String studentId = null;
        String academicYear = null;
        Integer semester = null;
        for (SchoolDataSyncFilterRequest.FilterGroup group : request.getFilterGroups()) {
            if (group == null || group.getConditions() == null) {
                continue;
            }
            for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                if (condition == null || condition.getField() == null) {
                    continue;
                }
                String field = condition.getField();
                String value = defaultString(condition.getValue(), "").trim();
                if ("studentId".equals(field)) studentId = value;
                if ("academicYear".equals(field)) academicYear = value;
                if ("semester".equals(field) && !value.isEmpty()) semester = Integer.parseInt(value);
            }
        }
        if (studentId == null || studentId.isEmpty()) {
            throw new BusinessException("student_schedule 手动同步必须提供学号");
        }
        List<StudentSchedule> schedules = syncStudentSchedule(studentId, academicYear, semester);
        SyncResult result = new SyncResult();
        result.total = schedules.size();
        result.success = schedules.size();
        result.fail = 0;
        result.skip = 0;
        return result;
    }

    // =========================================================================
    // 数据持久化
    // =========================================================================

    /**
     * 将映射后的数据持久化到数据库
     * 单条记录失败不影响整体（catch后继续处理下一条），不使用事务
     */
    public SyncResult persistData(String dataSource, List<Map<String, Object>> rawData) {
        SyncResult result = new SyncResult();
        result.total = rawData.size();

        LocalDateTime syncTime = LocalDateTime.now();
        int batchCount = 0;

        for (Map<String, Object> sourceRecord : rawData) {
            try {
                // 1. 应用字段映射
                Map<String, Object> mappedData = fieldMappingService.applyMapping(dataSource, sourceRecord);

                // 2. 设置同步元数据
                mappedData.put("syncTime", syncTime);
                mappedData.put("syncSource", "SCHOOL_API");

                // 3. 转换为实体并UPSERT
                upsertEntity(dataSource, mappedData);
                result.success++;
            } catch (Exception e) {
                result.fail++;
                // 保留堆栈以定位字段映射/类型转换/UPSERT 约束冲突等问题
                logger.warn("同步单条数据失败 dataSource={} recordPreview={} err={}",
                        dataSource, truncateRecordForLog(sourceRecord), e.getMessage(), e);
                // 单条失败不影响整体，继续处理
            }

            // 批量刷新（避免大事务）
            batchCount++;
            if (batchCount % properties.getSync().getBatchSize() == 0) {
                logger.debug("批量处理进度: {}/{}", batchCount, rawData.size());
            }
        }

        result.skip = result.total - result.success - result.fail;
        return result;
    }

    /**
     * 根据数据源类型执行UPSERT
     */
    private void upsertEntity(String dataSource, Map<String, Object> mappedData) {
        switch (dataSource) {
            case "administrative_class":
                upsertAdministrativeClass(mappedData);
                break;
            case "school_course":
                upsertSchoolCourse(mappedData);
                break;
            case "teaching_class":
                // bzkcxx (cdsp_1721804030860152850) 端点实际返回课程基本信息（2026-04-21 PDF 复核），
                // 字段与 school_course 重叠，路由到 upsertSchoolCourse 避免 TeachingClass NOT NULL 约束失败
                upsertSchoolCourse(mappedData);
                break;
            case "course_schedule":
                upsertCourseSchedule(mappedData);
                break;
            case "course_selection":
                upsertCourseSelection(mappedData);
                break;
            case "level_exam_scores":
                upsertLevelExamScore(mappedData);
                break;
            case "teaching_plans":
                upsertTeachingPlan(mappedData);
                break;
            case "teacher_courses":
                // 教师授课数据映射到teaching_class表，与bzkcxx接口共享
                upsertTeachingClass(mappedData);
                break;
            case "student_schedule":
                upsertStudentSchedule(mappedData);
                break;
            default:
                throw new BusinessException("不支持的数据源: " + dataSource);
        }
    }

    // =========================================================================
    // 各数据源 UPSERT 实现
    // =========================================================================

    private void upsertAdministrativeClass(Map<String, Object> data) {
        AdministrativeClass entity = new AdministrativeClass();
        entity.setClassCode(getStringValue(data, "classCode"));
        entity.setClassName(getStringValue(data, "className"));
        entity.setClassShortName(getStringValue(data, "classShortName"));
        entity.setCollegeCode(getStringValue(data, "collegeCode"));
        entity.setCollegeName(getStringValue(data, "collegeName"));

        // majorCode: 外部 API 可能不提供，使用默认值或从 majorName 提取
        String majorCode = getStringValue(data, "majorCode");
        if (majorCode == null || majorCode.trim().isEmpty()) {
            majorCode = "UNKNOWN";  // 默认值
        }
        entity.setMajorCode(majorCode);

        entity.setMajorName(getStringValue(data, "majorName"));
        entity.setGrade(getStringValue(data, "grade"));
        entity.setClassType(getStringValue(data, "classType"));
        entity.setHeadTeacherCode(getStringValue(data, "headTeacherCode"));
        entity.setHeadTeacherName(getStringValue(data, "headTeacherName"));
        entity.setStudentCount(getIntegerValue(data, "studentCount"));
        entity.setMaleCount(getIntegerValue(data, "maleCount"));
        entity.setFemaleCount(getIntegerValue(data, "femaleCount"));

        // classStatus: 外部 API 可能不提供，使用默认值
        String classStatus = getStringValue(data, "classStatus");
        if (classStatus == null || classStatus.trim().isEmpty()) {
            classStatus = "ACTIVE";  // 默认为活跃状态
        }
        entity.setClassStatus(classStatus);

        entity.setEstablishDate(getLocalDateValue(data, "establishDate"));
        entity.setGraduateDate(getLocalDateValue(data, "graduateDate"));
        entity.setRemark(getStringValue(data, "remark"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));

        administrativeClassMapper.upsertByClassCode(entity);
    }

    private void upsertSchoolCourse(Map<String, Object> data) {
        SchoolCourse entity = new SchoolCourse();
        entity.setCourseCode(getStringValue(data, "courseCode"));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setCourseNameEn(getStringValue(data, "courseNameEn"));
        entity.setCourseShortName(getStringValue(data, "courseShortName"));

        // courseNature: 外部 API 可能不提供，使用默认值
        String courseNature = getStringValue(data, "courseNature");
        if (courseNature == null || courseNature.trim().isEmpty()) {
            courseNature = "必修";  // 默认必修
        }
        entity.setCourseNature(courseNature);

        // courseCategory: 外部 API 可能不提供，使用默认值
        String courseCategory = getStringValue(data, "courseCategory");
        if (courseCategory == null || courseCategory.trim().isEmpty()) {
            courseCategory = "专业课";  // 默认专业课
        }
        entity.setCourseCategory(courseCategory);

        entity.setCredits(getBigDecimalValue(data, "credits"));
        entity.setTotalHours(getIntegerValue(data, "totalHours"));
        entity.setTheoryHours(getIntegerValue(data, "theoryHours"));
        entity.setExperimentHours(getIntegerValue(data, "experimentHours"));
        entity.setPracticeHours(getIntegerValue(data, "practiceHours"));
        entity.setComputerHours(getIntegerValue(data, "computerHours"));

        // collegeCode: 外部 API 可能不提供，从 collegeName 提取或使用默认值
        String collegeCode = getStringValue(data, "collegeCode");
        if (collegeCode == null || collegeCode.trim().isEmpty()) {
            collegeCode = "UNKNOWN";
        }
        entity.setCollegeCode(collegeCode);

        entity.setCollegeName(getStringValue(data, "collegeName"));
        entity.setDepartmentCode(getStringValue(data, "departmentCode"));
        entity.setDepartmentName(getStringValue(data, "departmentName"));
        entity.setLeaderCode(getStringValue(data, "leaderCode"));
        entity.setLeaderName(getStringValue(data, "leaderName"));
        entity.setAssessmentMethod(getStringValue(data, "assessmentMethod"));
        entity.setIsBilingual(getStringValue(data, "isBilingual"));
        entity.setIsOnline(getStringValue(data, "isOnline"));
        entity.setApplicableMajors(getStringValue(data, "applicableMajors"));
        entity.setPrerequisiteCodes(getStringValue(data, "prerequisiteCodes"));
        entity.setPrerequisiteNames(getStringValue(data, "prerequisiteNames"));
        entity.setCourseIntro(getStringValue(data, "courseIntro"));
        entity.setTeachingObjectives(getStringValue(data, "teachingObjectives"));

        // courseStatus: 外部 API 可能不提供，使用默认值
        String courseStatus = getStringValue(data, "courseStatus");
        if (courseStatus == null || courseStatus.trim().isEmpty()) {
            courseStatus = "ACTIVE";  // 默认活跃状态
        }
        entity.setCourseStatus(courseStatus);

        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));

        schoolCourseMapper.upsertByCourseCode(entity);
    }

    private void upsertTeachingClass(Map<String, Object> data) {
        TeachingClass entity = new TeachingClass();
        entity.setClassId(getStringValue(data, "classId"));
        entity.setClassName(getStringValue(data, "className"));
        entity.setCourseCode(getStringValue(data, "courseCode"));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setTeacherId(getStringValue(data, "teacherId"));
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setAcademicYear(getStringValue(data, "academicYear"));
        entity.setSemester(parseSchoolSemester(data, "semester"));

        // capacity: 外部 API 可能不提供，使用默认值
        Integer capacity = getIntegerValue(data, "capacity");
        if (capacity == null) {
            capacity = 100;  // 默认容量
        }
        entity.setCapacity(capacity);

        entity.setEnrolledCount(getIntegerValue(data, "enrolledCount"));

        // classStatus: 外部 API 可能不提供，使用默认值
        String classStatus = getStringValue(data, "classStatus");
        if (classStatus == null || classStatus.trim().isEmpty()) {
            classStatus = "ACTIVE";  // 默认为活跃状态
        }
        entity.setClassStatus(classStatus);

        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));

        teachingClassMapper.upsertByClassId(entity);
    }

    private void upsertCourseSchedule(Map<String, Object> data) {
        // course_schedule 没有自然唯一键，使用 delete-then-insert 模式
        String classId = getStringValue(data, "classId");
        String academicYear = getStringValue(data, "academicYear");
        Integer semester = parseSchoolSemester(data, "semester");

        if (classId != null && academicYear != null && semester != null) {
            // 先删除同班级同学期的旧数据
            courseScheduleMapper.deleteByClassIdAndSemester(classId, academicYear, semester);

            CourseSchedule entity = new CourseSchedule();
            entity.setClassId(classId);
            entity.setDayOfWeek(getIntegerValue(data, "dayOfWeek"));
            entity.setPeriod(getStringValue(data, "period"));
            entity.setWeekRange(getStringValue(data, "weekRange"));
            entity.setClassLocation(getStringValue(data, "classLocation"));
            entity.setAcademicYear(academicYear);
            entity.setSemester(semester);
            entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
            entity.setSyncSource(getStringValue(data, "syncSource"));

            courseScheduleMapper.insert(entity);
        }
    }

    private void upsertCourseSelection(Map<String, Object> data) {
        CourseSelection entity = new CourseSelection();

        // 生成复合唯一键：学号_课程班号_学年_学期
        // 外部 API 不提供选课记录ID，需要根据业务字段生成
        String studentId = getStringValue(data, "studentId");
        String classId = getClassIdValue(data);
        String academicYear = getStringValue(data, "academicYear");
        Integer semester = parseSchoolSemester(data, "semester");

        String selectionId = getStringValue(data, "selectionId");
        if (selectionId == null || selectionId.trim().isEmpty()) {
            // 生成复合键：学号_课程班号_学年_学期
            if (studentId != null && classId != null && academicYear != null && semester != null) {
                selectionId = studentId + "_" + classId + "_" + academicYear + "_" + semester;
            } else {
                throw new BusinessException("选课记录缺少必要字段: studentId=" + studentId
                    + ", classId=" + classId + ", academicYear=" + academicYear + ", semester=" + semester);
            }
        }

        entity.setSelectionId(selectionId);
        entity.setStudentId(studentId);
        entity.setStudentName(getStringValue(data, "studentName"));
        entity.setCourseCode(getCourseCodeValue(data));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setClassId(classId);
        entity.setTeacherId(getStringValue(data, "teacherId"));
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setCredits(getBigDecimalValue(data, "credits"));
        entity.setAcademicYear(academicYear);
        entity.setSemester(semester);
        entity.setClassTime(getStringValue(data, "classTime"));
        entity.setClassLocation(getStringValue(data, "classLocation"));
        entity.setSelectionTime(getLocalDateTimeValue(data, "selectionTime"));
        entity.setSelectionStatus(getStringValue(data, "selectionStatus"));
        entity.setRegularGrade(getBigDecimalValue(data, "regularGrade"));
        entity.setFinalGrade(getBigDecimalValue(data, "finalGrade"));
        entity.setTotalGrade(getBigDecimalValue(data, "totalGrade"));
        entity.setGradePoint(getBigDecimalValue(data, "gradePoint"));
        entity.setAttendanceCount(getIntegerValue(data, "attendanceCount"));
        entity.setAbsenceCount(getIntegerValue(data, "absenceCount"));
        entity.setGradeRemark(getStringValue(data, "gradeRemark"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));

        courseSelectionMapper.upsertBySelectionId(entity);
    }

    private void upsertLevelExamScore(Map<String, Object> data) {
        LevelExamScore entity = new LevelExamScore();
        entity.setStudentId(getStringValue(data, "studentId"));

        // studentName: CDSP 等级考试成绩不返回姓名，回退使用学号
        String studentName = getStringValue(data, "studentName");
        if (studentName == null || studentName.trim().isEmpty()) {
            studentName = entity.getStudentId();
        }
        entity.setStudentName(studentName);

        // examType: 外部 API 可能不提供，从 examName 推断或使用默认值
        String examType = getStringValue(data, "examType");
        if (examType == null || examType.trim().isEmpty()) {
            String examName = getStringValue(data, "examName");
            if (examName != null && examName.contains("CET")) {
                examType = "CET";
            } else if (examName != null && examName.contains("四级")) {
                examType = "CET4";
            } else if (examName != null && examName.contains("六级")) {
                examType = "CET6";
            } else {
                examType = "OTHER";
            }
        }
        entity.setExamType(examType);

        entity.setExamName(getStringValue(data, "examName"));
        entity.setAdmissionTicketNo(getStringValue(data, "admissionTicketNo"));
        entity.setExamDate(getLocalDateValue(data, "examDate"));

        BigDecimal score = getBigDecimalValue(data, "score");
        entity.setScore(score);
        entity.setGrade(getStringValue(data, "grade"));

        // isPassed: 外部 API 可能不提供，根据成绩判断
        Boolean isPassed = getBooleanValue(data, "isPassed");
        if (isPassed == null) {
            BigDecimal passingScore = getBigDecimalValue(data, "passingScore");
            if (score != null && passingScore != null) {
                isPassed = score.compareTo(passingScore) >= 0;
            } else if (score != null) {
                // 默认及格线60分
                isPassed = score.compareTo(new BigDecimal("60")) >= 0;
            } else {
                isPassed = false;
            }
        }
        entity.setIsPassed(isPassed);

        entity.setPassingScore(getBigDecimalValue(data, "passingScore"));
        entity.setListeningScore(getBigDecimalValue(data, "listeningScore"));
        entity.setReadingScore(getBigDecimalValue(data, "readingScore"));
        entity.setWritingScore(getBigDecimalValue(data, "writingScore"));
        entity.setTranslationScore(getBigDecimalValue(data, "translationScore"));
        entity.setCertificateNo(getStringValue(data, "certificateNo"));
        entity.setCertificateDate(getLocalDateValue(data, "certificateDate"));
        entity.setExamLocation(getStringValue(data, "examLocation"));
        entity.setExamRoomNo(getStringValue(data, "examRoomNo"));
        entity.setSeatNo(getStringValue(data, "seatNo"));
        entity.setRegistrationDate(getLocalDateValue(data, "registrationDate"));
        entity.setScoreReleaseDate(getLocalDateValue(data, "scoreReleaseDate"));
        entity.setDataSource(getStringValue(data, "dataSource"));
        entity.setRemark(getStringValue(data, "remark"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));

        levelExamScoreMapper.upsertByStudentExam(entity);
    }

    private void upsertTeachingPlan(Map<String, Object> data) {
        TeachingPlan entity = new TeachingPlan();
        entity.setPlanId(getStringValue(data, "planId"));

        String planName = firstNonBlank(
                getStringValue(data, "planName"),
                getStringValue(data, "courseName"),
                getStringValue(data, "collegeName"),
                getStringValue(data, "grade")
        );
        if (planName == null) {
            planName = "教学计划-" + firstNonBlank(entity.getPlanId(), "UNKNOWN");
        }
        entity.setPlanName(planName);

        // majorCode: 外部 API 可能不提供，使用默认值
        String majorCode = getStringValue(data, "majorCode");
        if (majorCode == null || majorCode.trim().isEmpty()) {
            majorCode = "UNKNOWN";
        }
        entity.setMajorCode(majorCode);

        // majorName: 外部 API 可能不提供，使用默认值
        String majorName = getStringValue(data, "majorName");
        if (majorName == null || majorName.trim().isEmpty()) {
            majorName = "未知专业";
        }
        entity.setMajorName(majorName);

        entity.setGrade(getStringValue(data, "grade"));

        // schoolingLength: 外部 API 可能不提供，使用默认值
        Integer schoolingLength = getIntegerValue(data, "schoolingLength");
        if (schoolingLength == null) {
            schoolingLength = 4;  // 默认4年制
        }
        entity.setSchoolingLength(schoolingLength);

        // degreeType: 外部 API 可能不提供，使用默认值
        String degreeType = getStringValue(data, "degreeType");
        if (degreeType == null || degreeType.trim().isEmpty()) {
            degreeType = "学士";
        }
        entity.setDegreeType(degreeType);

        // educationLevel: 外部 API 可能不提供，使用默认值
        String educationLevel = getStringValue(data, "educationLevel");
        if (educationLevel == null || educationLevel.trim().isEmpty()) {
            educationLevel = "本科";
        }
        entity.setEducationLevel(educationLevel);

        entity.setCollegeCode(getStringValue(data, "collegeCode"));
        entity.setCollegeName(getStringValue(data, "collegeName"));
        entity.setTotalCredits(getBigDecimalValue(data, "totalCredits"));

        // requiredCredits: 外部 API 可能不提供，使用默认值
        BigDecimal requiredCredits = getBigDecimalValue(data, "requiredCredits");
        if (requiredCredits == null) {
            requiredCredits = BigDecimal.ZERO;
        }
        entity.setRequiredCredits(requiredCredits);

        // electiveCredits: 外部 API 可能不提供，使用默认值
        BigDecimal electiveCredits = getBigDecimalValue(data, "electiveCredits");
        if (electiveCredits == null) {
            electiveCredits = BigDecimal.ZERO;
        }
        entity.setElectiveCredits(electiveCredits);

        entity.setPracticeCredits(getBigDecimalValue(data, "practiceCredits"));
        entity.setGeneralEducationCredits(getBigDecimalValue(data, "generalEducationCredits"));
        entity.setMajorCoreCredits(getBigDecimalValue(data, "majorCoreCredits"));
        entity.setMajorElectiveCredits(getBigDecimalValue(data, "majorElectiveCredits"));
        entity.setTrainingObjective(getStringValue(data, "trainingObjective"));
        entity.setTrainingRequirements(getStringValue(data, "trainingRequirements"));
        entity.setMainDisciplines(getStringValue(data, "mainDisciplines"));
        entity.setCoreCourses(getStringValue(data, "coreCourses"));
        entity.setPlanStatus(getStringValue(data, "planStatus"));

        // effectiveDate: 外部 API 可能不提供，使用默认值
        LocalDate effectiveDate = getLocalDateValue(data, "effectiveDate");
        if (effectiveDate == null) {
            effectiveDate = LocalDate.now();
        }
        entity.setEffectiveDate(effectiveDate);

        entity.setExpiryDate(getLocalDateValue(data, "expiryDate"));
        entity.setCreator(getStringValue(data, "creator"));
        entity.setReviewer(getStringValue(data, "reviewer"));
        entity.setReviewDate(getLocalDateValue(data, "reviewDate"));
        entity.setVersion(getStringValue(data, "version"));
        entity.setRevisionNotes(getStringValue(data, "revisionNotes"));
        entity.setAttachmentPath(getStringValue(data, "attachmentPath"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));

        teachingPlanMapper.upsertByPlanId(entity);

        // 同步教学计划课程明细
        String planId = entity.getPlanId();
        if (planId != null) {
            syncTeachingPlanCourses(planId);
        }
    }

    private void upsertStudentSchedule(Map<String, Object> data) {
        // student_schedule 使用 delete-then-insert 模式（按 student_id + academic_year + semester）
        String studentId = getStringValue(data, "studentId");
        String academicYear = getStringValue(data, "academicYear");
        Integer semester = parseSchoolSemester(data, "semester");

        if (studentId != null && academicYear != null && semester != null) {
            // 先删除该学生同学期的旧课表数据
            QueryWrapper<StudentSchedule> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("student_id", studentId)
                    .eq("academic_year", academicYear)
                    .eq("semester", semester);
            studentScheduleMapper.delete(deleteWrapper);
        }

        StudentSchedule entity = new StudentSchedule();
        entity.setStudentId(studentId);
        entity.setStudentName(getStringValue(data, "studentName"));
        entity.setClassId(getClassIdValue(data));
        entity.setCourseCode(getCourseCodeValue(data));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setTeacherId(getStringValue(data, "teacherId"));
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setDayOfWeek(getIntegerValue(data, "dayOfWeek"));
        entity.setPeriod(getStringValue(data, "period"));
        entity.setWeekRange(getStringValue(data, "weekRange"));
        entity.setClassLocation(getStringValue(data, "classLocation"));
        entity.setAcademicYear(academicYear);
        entity.setSemester(semester);
        entity.setSyncTime((LocalDateTime) data.getOrDefault("syncTime", LocalDateTime.now()));
        entity.setSyncSource((String) data.getOrDefault("syncSource", "SCHOOL_API"));

        studentScheduleMapper.insert(entity);
    }

    /**
     * 同步教学计划课程明细
     */
    private void syncTeachingPlanCourses(String planId) {
        try {
            List<Map<String, Object>> coursesData = apiClient.fetchTeachingPlanCourses(planId);
            if (coursesData == null || coursesData.isEmpty()) {
                return;
            }

            LocalDateTime syncTime = LocalDateTime.now();
            for (Map<String, Object> sourceRecord : coursesData) {
                try {
                    Map<String, Object> mappedData = fieldMappingService.applyMapping("teaching_plans", sourceRecord);
                    mappedData.put("planId", planId);
                    mappedData.put("syncTime", syncTime);
                    mappedData.put("syncSource", "SCHOOL_API");

                    TeachingPlanCourse courseEntity = new TeachingPlanCourse();
                    courseEntity.setDetailId(getStringValue(mappedData, "detailId"));
                    courseEntity.setPlanId(planId);
                    courseEntity.setCourseCode(getStringValue(mappedData, "courseCode"));
                    courseEntity.setCourseName(getStringValue(mappedData, "courseName"));
                    courseEntity.setCourseType(getStringValue(mappedData, "courseType"));
                    courseEntity.setCourseCategory(getStringValue(mappedData, "courseCategory"));
                    courseEntity.setCredits(getBigDecimalValue(mappedData, "credits"));
                    courseEntity.setHours(getIntegerValue(mappedData, "hours"));
                    courseEntity.setTheoryHours(getIntegerValue(mappedData, "theoryHours"));
                    courseEntity.setLabHours(getIntegerValue(mappedData, "labHours"));
                    courseEntity.setSemester(getIntegerValue(mappedData, "semester"));
                    courseEntity.setAssessmentMethod(getStringValue(mappedData, "assessmentMethod"));
                    courseEntity.setPrerequisiteCourses(getStringValue(mappedData, "prerequisiteCourses"));
                    courseEntity.setSortOrder(getIntegerValue(mappedData, "sortOrder"));
                    courseEntity.setSyncTime(syncTime);
                    courseEntity.setSyncSource("SCHOOL_API");

                    teachingPlanCourseMapper.upsertByDetailId(courseEntity);
                } catch (Exception e) {
                    // 保留堆栈以诊断课程明细字段类型转换 / detailId 重复等问题
                    logger.warn("同步教学计划课程明细失败 planId={} detailPreview={} err={}",
                            planId, truncateRecordForLog(sourceRecord), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.warn("获取教学计划课程明细失败: planId={}", planId, e);
        }
    }

    // =========================================================================
    // 同步日志管理
    // =========================================================================

    /**
     * 创建同步日志
     */
    private SyncLog createSyncLog(String syncType, String dataSource, String syncMethod,
                                  String triggerType, String operator, LocalDateTime startTime) {
        SyncLog syncLog = new SyncLog();
        syncLog.setSyncType(syncType);
        syncLog.setDataSource(dataSource);
        syncLog.setSyncMethod(syncMethod);
        syncLog.setStatus("RUNNING");
        syncLog.setStartTime(startTime);
        syncLog.setTriggerType(triggerType);
        syncLog.setOperator(operator);
        syncLogMapper.insert(syncLog);
        return syncLog;
    }

    /**
     * 完成同步日志（成功）
     */
    private void completeSyncLog(SyncLog syncLog, SyncResult result, LocalDateTime startTime) {
        syncLog.setStatus("SUCCESS");
        syncLog.setTotalCount(result.total);
        syncLog.setSuccessCount(result.success);
        syncLog.setFailCount(result.fail);
        syncLog.setSkipCount(result.skip);
        syncLog.setEndTime(LocalDateTime.now());
        syncLog.setDurationMs(java.time.Duration.between(startTime, syncLog.getEndTime()).toMillis());
        syncLogMapper.updateById(syncLog);
    }

    /**
     * 标记同步日志为失败
     */
    private void failSyncLog(SyncLog syncLog, Exception e, LocalDateTime startTime) {
        syncLog.setStatus("FAILED");
        syncLog.setEndTime(LocalDateTime.now());
        syncLog.setDurationMs(java.time.Duration.between(startTime, syncLog.getEndTime()).toMillis());
        // 错误信息截断，防止过长
        String errorMsg = e.getMessage();
        if (errorMsg != null && errorMsg.length() > 500) {
            errorMsg = errorMsg.substring(0, 500);
        }
        syncLog.setErrorMessage(errorMsg);
        syncLogMapper.updateById(syncLog);
    }

    // =========================================================================
    // 本地数据量查询
    // =========================================================================

    private long getLocalCount(String dataSource) {
        switch (dataSource) {
            case "administrative_class":
                return administrativeClassMapper.selectCount(null);
            case "school_course":
                return schoolCourseMapper.selectCount(null);
            case "teaching_class":
                // bzkcxx 端点实际落到 school_course 表（见 upsertEntity 注释）
                return schoolCourseMapper.selectCount(null);
            case "course_schedule":
                return courseScheduleMapper.selectCount(null);
            case "course_selection":
                return courseSelectionMapper.selectCount(null);
            case "level_exam_scores":
                return levelExamScoreMapper.selectCount(null);
            case "teaching_plans":
                return teachingPlanMapper.selectCount(null);
            case "student_schedule":
                return studentScheduleMapper.selectCount(null);
            case "teacher_courses":
                // teacher_courses 共享 teaching_class 表
                return teachingClassMapper.selectCount(null);
            default:
                return 0;
        }
    }

    // =========================================================================
    // 学生课表 按学生同步 & 查询
    // =========================================================================

    @Override
    public List<StudentSchedule> syncStudentSchedule(String studentId, String academicYear, Integer semester) {
        // 1. 从学校API拉取课表
        List<Map<String, Object>> fetchData = apiClient.fetchStudentSchedules(studentId, academicYear, semester);
        if (fetchData == null || fetchData.isEmpty()) {
            logger.info("学校API返回空课表数据: studentId={}, academicYear={}, semester={}", studentId, academicYear, semester);
            return getStudentSchedule(studentId, academicYear, semester);
        }

        // 2. 应用字段映射并持久化（会自动执行 delete-then-insert）
        LocalDateTime syncTime = LocalDateTime.now();
        for (Map<String, Object> sourceRecord : fetchData) {
            try {
                Map<String, Object> mappedData = fieldMappingService.applyMapping("student_schedule", sourceRecord);
                mappedData.put("syncTime", syncTime);
                mappedData.put("syncSource", "SCHOOL_API");
                upsertEntity("student_schedule", mappedData);
            } catch (Exception e) {
                // 保留堆栈以定位排课 delete-then-insert 过程中的约束冲突或类型转换问题
                logger.warn("同步学生课表单条数据失败 studentId={} recordPreview={} err={}",
                        studentId, truncateRecordForLog(sourceRecord), e.getMessage(), e);
            }
        }

        logger.info("学生课表同步完成: studentId={}, 拉取{}条", studentId, fetchData.size());

        // 3. 返回本地数据
        return getStudentSchedule(studentId, academicYear, semester);
    }

    @Override
    public List<StudentSchedule> getStudentSchedule(String studentId, String academicYear, Integer semester) {
        QueryWrapper<StudentSchedule> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        if (academicYear != null) {
            wrapper.eq("academic_year", academicYear);
        }
        if (semester != null) {
            wrapper.eq("semester", semester);
        }
        wrapper.orderByAsc("day_of_week", "period");
        return studentScheduleMapper.selectList(wrapper);
    }

    // =========================================================================
    // 按学院链式拉取：school_course → teacher_courses → course_selection
    // 衍生：school_student / school_teacher / teacher_student_binding
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> chainFetchStudentsByCollege(String collegeName, String matchMode, boolean persist) {
        if (collegeName == null || collegeName.trim().isEmpty()) {
            throw new BusinessException("学院名称不能为空");
        }
        String normalizedCollege = collegeName.trim();
        String mode = defaultString(matchMode, "eq").toLowerCase(Locale.ROOT);
        LocalDateTime syncTime = LocalDateTime.now();
        logger.info("[chainFetch] 开始: collegeName={}, matchMode={}, persist={}", normalizedCollege, mode, persist);

        // Step 1: 按 KKYX 拉 school_course + 客户端过滤
        Map<String, String> courseParams = new LinkedHashMap<>();
        courseParams.put("kkyx", normalizedCollege);
        List<Map<String, Object>> rawCourses;
        try {
            rawCourses = apiClient.fetchSchoolCourses(courseParams);
        } catch (Exception e) {
            logger.error("[chainFetch] 拉取课程失败 college={}", normalizedCollege, e);
            throw new BusinessException("拉取学校课程失败: " + e.getMessage());
        }
        if (rawCourses == null) rawCourses = Collections.emptyList();
        List<Map<String, Object>> matchedCourses = filterCoursesByCollege(rawCourses, normalizedCollege, mode);
        logger.info("[chainFetch] Step1 课程: upstream={}, matched={}", rawCourses.size(), matchedCourses.size());

        List<String> matchedCourseCodes = new ArrayList<>();
        for (Map<String, Object> course : matchedCourses) {
            Map<String, Object> mapped = fieldMappingService.applyMapping("school_course", course);
            String kcdm = stringValue(mapped.get("courseCode"));
            if (kcdm == null || kcdm.isEmpty()) continue;
            matchedCourseCodes.add(kcdm);
            if (persist) {
                mapped.put("syncTime", syncTime);
                mapped.put("syncSource", "CHAIN_SYNC");
                try {
                    upsertSchoolCourse(mapped);
                } catch (Exception e) {
                    logger.warn("[chainFetch] upsert school_course 失败 kcdm={} err={}", kcdm, e.getMessage());
                }
            }
        }

        // Step 2 & 2.5: 每门课拉 teacher_courses（按 kcdm）→ 得到 kcbh + 教师
        //               + course_selection（按 KCBH）→ 得到学号
        Set<String> studentIds = new LinkedHashSet<>();
        Map<String, Set<String>> studentToCourses = new LinkedHashMap<>();
        Map<String, SchoolTeacher> teacherMap = new LinkedHashMap<>();
        List<TeacherStudentBinding> bindings = new ArrayList<>();
        Boolean kcbhSupported = null;

        for (String kcdm : matchedCourseCodes) {
            // 2.5: teacher_courses 按 kcdm 拉 → 得到 (kcbh, rkjsh, rkjsxm, xn, xq)
            List<Map<String, Object>> rawTcs;
            try {
                rawTcs = apiClient.fetchTeacherCourses(Map.of("kcdm", kcdm));
            } catch (Exception e) {
                logger.warn("[chainFetch] 拉取 teacher_courses 失败 kcdm={} err={}", kcdm, e.getMessage());
                continue;
            }
            if (rawTcs == null) rawTcs = Collections.emptyList();

            // 客户端过滤：CDSP 可能忽略 kcdm 过滤，确保只保留当前课程记录
            Map<String, List<Map<String, Object>>> tcByClassCode = new LinkedHashMap<>();
            for (Map<String, Object> tc : rawTcs) {
                Map<String, Object> mapped = fieldMappingService.applyMapping("teacher_courses", tc);
                if (!kcdm.equals(getCourseCodeValue(mapped))) continue;
                String kcbh = getClassIdValue(mapped);
                if (kcbh == null || kcbh.isEmpty()) continue;
                tcByClassCode.computeIfAbsent(kcbh, k -> new ArrayList<>()).add(mapped);
            }

            // 2: 对每个 kcbh 拉 course_selection
            for (Map.Entry<String, List<Map<String, Object>>> entry : tcByClassCode.entrySet()) {
                String kcbh = entry.getKey();
                List<Map<String, Object>> tcs = entry.getValue();

                List<Map<String, Object>> rawSels;
                try {
                    rawSels = apiClient.fetchCourseSelections(Map.of("KCBH", kcbh));
                } catch (Exception e) {
                    logger.warn("[chainFetch] 拉取 course_selection 失败 kcbh={} err={}", kcbh, e.getMessage());
                    continue;
                }
                if (rawSels == null) rawSels = Collections.emptyList();

                // 首次探测 KCBH 支持：若第一批返回记录的 classId 不全匹配 kcbh → 降级客户端过滤
                if (kcbhSupported == null && !rawSels.isEmpty()) {
                    kcbhSupported = probeKcbhSupport(rawSels, kcbh);
                    if (Boolean.FALSE.equals(kcbhSupported)) {
                        logger.warn("[chainFetch] CDSP 忽略 KCBH 参数，切换客户端过滤");
                    }
                }

                List<String> courseStudentIds = new ArrayList<>();
                for (Map<String, Object> sel : rawSels) {
                    Map<String, Object> mapped = fieldMappingService.applyMapping("course_selection", sel);
                    if (Boolean.FALSE.equals(kcbhSupported)
                            && !kcbh.equals(getClassIdValue(mapped))) {
                        continue;
                    }
                    String xh = stringValue(mapped.get("studentId"));
                    if (xh == null || xh.isEmpty()) continue;
                    studentIds.add(xh);
                    studentToCourses.computeIfAbsent(xh, k -> new LinkedHashSet<>()).add(kcdm);
                    courseStudentIds.add(xh);
                    if (persist) {
                        mapped.put("syncTime", syncTime);
                        mapped.put("syncSource", "CHAIN_SYNC");
                        try {
                            upsertCourseSelection(mapped);
                        } catch (Exception e) {
                            logger.warn("[chainFetch] upsert course_selection 失败 xh={} err={}", xh, e.getMessage());
                        }
                    }
                }

                // 教师去重 + 绑定关系构建（teacher × student 笛卡尔积，限定于 kcbh）
                for (Map<String, Object> tc : tcs) {
                    String tid = stringValue(tc.get("teacherId"));
                    if (tid == null || tid.isEmpty()) continue;
                    teacherMap.computeIfAbsent(tid, k -> {
                        SchoolTeacher t = new SchoolTeacher();
                        t.setTeacherId(tid);
                        t.setTeacherName(stringValue(tc.get("teacherName")));
                        t.setCollegeName(normalizedCollege);
                        t.setSyncTime(syncTime);
                        t.setSyncSource("CHAIN_SYNC");
                        return t;
                    });
                    String xn = stringValue(tc.get("academicYear"));
                    String xq = stringValue(tc.get("semester"));
                    for (String sid : courseStudentIds) {
                        TeacherStudentBinding b = new TeacherStudentBinding();
                        b.setTeacherId(tid);
                        b.setStudentId(sid);
                        b.setCourseCode(kcdm);
                        b.setCourseClassCode(kcbh);
                        b.setAcademicYear(xn == null ? "" : xn);
                        b.setSemester(xq == null ? "" : xq);
                        b.setSourceType("CHAIN_SYNC");
                        b.setSyncTime(syncTime);
                        bindings.add(b);
                    }
                }
            }
        }

        // Step 3 & 3.5: 主数据落库
        int studentInserted = 0;
        int teacherInserted = 0;
        int bindingInserted = 0;
        if (persist) {
            for (String xh : studentIds) {
                try {
                    Set<String> courses = studentToCourses.getOrDefault(xh, Collections.emptySet());
                    upsertSchoolStudent(xh, normalizedCollege, courses, syncTime);
                    studentInserted++;
                } catch (Exception e) {
                    logger.warn("[chainFetch] upsert school_student 失败 xh={} err={}", xh, e.getMessage());
                }
            }
            for (SchoolTeacher t : teacherMap.values()) {
                try {
                    schoolTeacherMapper.upsertByTeacherId(t);
                    teacherInserted++;
                } catch (Exception e) {
                    logger.warn("[chainFetch] upsert school_teacher 失败 tid={} err={}", t.getTeacherId(), e.getMessage());
                }
            }
            for (TeacherStudentBinding b : bindings) {
                try {
                    teacherStudentBindingMapper.upsertBinding(b);
                    bindingInserted++;
                } catch (Exception e) {
                    logger.warn("[chainFetch] upsert binding 失败 tid={} sid={} kcdm={} err={}",
                            b.getTeacherId(), b.getStudentId(), b.getCourseCode(), e.getMessage());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collegeName", normalizedCollege);
        result.put("matchMode", mode);
        result.put("persist", persist);
        result.put("courseCount", matchedCourses.size());
        result.put("studentCount", studentIds.size());
        result.put("teacherCount", teacherMap.size());
        result.put("bindingCount", bindings.size());
        result.put("studentInserted", studentInserted);
        result.put("teacherInserted", teacherInserted);
        result.put("bindingInserted", bindingInserted);
        result.put("upstreamKcbhSupported", kcbhSupported);
        result.put("matchedCourseCodes", matchedCourseCodes);
        result.put("syncTime", syncTime);
        logger.info("[chainFetch] 完成: {}", result);
        return result;
    }

    @Override
    public List<LevelExamScore> getOrSyncLevelExamScores(String studentId, Duration staleness) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Duration effectiveStaleness = staleness == null ? Duration.ofDays(1) : staleness;
        LocalDateTime last = schoolStudentMapper.selectLastExamSyncTime(studentId);
        boolean stale = last == null || last.isBefore(LocalDateTime.now().minus(effectiveStaleness));
        if (stale) {
            try {
                List<Map<String, Object>> raw = apiClient.fetchLevelExamScores(Map.of("xh", studentId));
                if (raw != null) {
                    LocalDateTime now = LocalDateTime.now();
                    for (Map<String, Object> r : raw) {
                        try {
                            Map<String, Object> mapped = fieldMappingService.applyMapping("level_exam_scores", r);
                            mapped.put("syncTime", now);
                            mapped.put("syncSource", "LAZY_SYNC");
                            upsertLevelExamScore(mapped);
                        } catch (Exception e) {
                            logger.warn("Lazy 四六级 upsert 失败 xh={} err={}", studentId, e.getMessage());
                        }
                    }
                    schoolStudentMapper.updateLastExamSyncTime(studentId, now);
                }
            } catch (Exception e) {
                logger.warn("Lazy 四六级同步失败 xh={}，降级返回本地数据: {}", studentId, e.getMessage());
            }
        }
        return levelExamScoreMapper.selectList(
                new QueryWrapper<LevelExamScore>().eq("student_id", studentId).orderByDesc("exam_date"));
    }

    @Override
    public List<StudentSchedule> getOrSyncStudentSchedule(String studentId,
                                                          String academicYear,
                                                          Integer semester,
                                                          Duration staleness) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Duration effectiveStaleness = staleness == null ? Duration.ofHours(6) : staleness;
        LocalDateTime last = schoolStudentMapper.selectLastScheduleSyncTime(studentId);
        boolean stale = last == null || last.isBefore(LocalDateTime.now().minus(effectiveStaleness));
        if (stale) {
            try {
                syncStudentSchedule(studentId, academicYear, semester);
                schoolStudentMapper.updateLastScheduleSyncTime(studentId, LocalDateTime.now());
            } catch (Exception e) {
                logger.warn("Lazy 课表同步失败 xh={}，降级返回本地数据: {}", studentId, e.getMessage());
            }
        }
        return getStudentSchedule(studentId, academicYear, semester);
    }

    // ---- 链式拉取辅助方法 ----

    private List<Map<String, Object>> filterCoursesByCollege(List<Map<String, Object>> rawCourses,
                                                             String collegeName, String matchMode) {
        if (rawCourses == null || rawCourses.isEmpty() || collegeName == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> raw : rawCourses) {
            Map<String, Object> mapped = fieldMappingService.applyMapping("school_course", raw);
            String actual = stringValue(mapped.get("collegeName"));
            if (actual.isEmpty()) continue;
            boolean matches = "contains".equalsIgnoreCase(matchMode)
                    ? actual.contains(collegeName)
                    : actual.equals(collegeName);
            if (matches) filtered.add(raw);
        }
        return filtered;
    }

    /**
     * 探测 CDSP 是否支持 KCBH 过滤：若前 3 条记录的 classId 全等于目标 kcbh → true；否则 false。
     */
    private Boolean probeKcbhSupport(List<Map<String, Object>> rawSels, String kcbh) {
        if (rawSels == null || rawSels.isEmpty() || kcbh == null) return null;
        int probeCount = Math.min(3, rawSels.size());
        for (int i = 0; i < probeCount; i++) {
            Map<String, Object> mapped = fieldMappingService.applyMapping("course_selection", rawSels.get(i));
            String classId = getClassIdValue(mapped);
            if (!kcbh.equals(classId)) return false;
        }
        return true;
    }

    private void upsertSchoolStudent(String studentId, String collegeName, Set<String> courseCodes, LocalDateTime syncTime) {
        SchoolStudent entity = new SchoolStudent();
        entity.setStudentId(studentId);
        entity.setCollegeName(collegeName);
        // grade 可从学号前 4 位推断（如 2023122066164 → 2023）
        if (studentId != null && studentId.length() >= 4) {
            String gradePrefix = studentId.substring(0, 4);
            if (gradePrefix.matches("\\d{4}")) {
                entity.setGrade(gradePrefix);
            }
        }
        if (courseCodes != null && !courseCodes.isEmpty()) {
            try {
                entity.setSourceCourseCodes(objectMapper.writeValueAsString(courseCodes));
            } catch (Exception e) {
                entity.setSourceCourseCodes(String.join(",", courseCodes));
            }
        }
        entity.setSyncTime(syncTime);
        entity.setSyncSource("CHAIN_SYNC");
        schoolStudentMapper.upsertByStudentId(entity);
    }

    // =========================================================================
    // 类型安全取值方法
    // =========================================================================

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String getClassIdValue(Map<String, Object> data) {
        return firstNonBlank(
                getStringValue(data, "classId"),
                getStringValue(data, "courseCode")
        );
    }

    private String getCourseCodeValue(Map<String, Object> data) {
        return getStringValue(data, "courseCode");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer getIntegerValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 学期字段解析：兼容 CDSP API 返回的中文 varchar "秋"/"春" 与 Integer 1/2。
     * V12 field_mappings 对 student_schedule/course_schedule/teacher_courses 的 xq
     * 保留 STRING 原值（API 返回的 "秋"/"春" 无法 parse 为 NUMBER），
     * upsert 层在此处做中文→Integer 的语义转换（秋=1, 春=2）。
     */
    private Integer parseSchoolSemester(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }
        switch (str) {
            case "秋":
            case "1":
                return 1;
            case "春":
            case "2":
                return 2;
            default:
                try {
                    return Integer.parseInt(str);
                } catch (NumberFormatException e) {
                    logger.warn("无法解析学期字段值 key={} value={}", key, str);
                    return null;
                }
        }
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String strVal = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(strVal) || "1".equals(strVal) || "yes".equals(strVal) || "是".equals(strVal)) {
            return true;
        }
        if ("false".equals(strVal) || "0".equals(strVal) || "no".equals(strVal) || "否".equals(strVal)) {
            return false;
        }
        return null;
    }

    private LocalDate getLocalDateValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime getLocalDateTimeValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return null;
    }

    /**
     * 将单条源记录截断用于日志，避免：
     *   ① 单条数据过大导致日志膨胀；
     *   ② 敏感字段（身份证/手机号等）完整回显到 info/warn 级别日志。
     * 用于失败时锚定问题记录，便于从上游核对。
     */
    private String truncateRecordForLog(Map<String, Object> record) {
        if (record == null) return "null";
        String str = record.toString();
        return str.length() > 300 ? str.substring(0, 300) + "...(truncated)" : str;
    }

    // =========================================================================
    // 内部结果类
    // =========================================================================

    private static class SyncResult {
        int total;
        int success;
        int fail;
        int skip;

        SyncResult() {
        }

        SyncResult(int total, int success, int fail, int skip) {
            this.total = total;
            this.success = success;
            this.fail = fail;
            this.skip = skip;
        }
    }
}
