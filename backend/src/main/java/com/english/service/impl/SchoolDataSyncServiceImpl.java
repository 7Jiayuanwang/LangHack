package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.config.SchoolDataProperties;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
            "teaching_plans"
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
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // =========================================================================
    // 全量同步
    // =========================================================================

    @Override
    public SyncLog fullSync(String dataSource, String triggerType, String operator) {
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
            SyncResult result = doFullSync(dataSource);
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
            SyncResult result = doIncrementalSync(dataSource);
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

    // =========================================================================
    // 全量同步核心逻辑
    // =========================================================================

    /**
     * 执行单个数据源的全量同步
     */
    private SyncResult doFullSync(String dataSource) {
        List<Map<String, Object>> fetchData = fetchFromApi(dataSource, null);
        if (fetchData.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }

        return persistData(dataSource, fetchData);
    }

    /**
     * 执行单个数据源的增量同步
     * 增量同步: 获取上次同步时间，仅同步更新的数据
     */
    private SyncResult doIncrementalSync(String dataSource) {
        // 获取上次同步时间
        SyncLog lastSync = syncLogMapper.selectLatestByDataSource(dataSource);
        Map<String, String> params = new HashMap<>();

        if (lastSync != null && lastSync.getEndTime() != null) {
            String since = lastSync.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            params.put("updatedSince", since);
        }

        List<Map<String, Object>> fetchData = fetchFromApi(dataSource, params);
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
            default:
                logger.warn("Unknown data source: {}", dataSource);
                return Collections.emptyList();
        }
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
                logger.warn("同步单条数据失败: 数据源={}, 错误={}", dataSource, e.getMessage());
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
                upsertTeachingClass(mappedData);
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
        entity.setMajorCode(getStringValue(data, "majorCode"));
        entity.setMajorName(getStringValue(data, "majorName"));
        entity.setGrade(getStringValue(data, "grade"));
        entity.setClassType(getStringValue(data, "classType"));
        entity.setHeadTeacherCode(getStringValue(data, "headTeacherCode"));
        entity.setHeadTeacherName(getStringValue(data, "headTeacherName"));
        entity.setStudentCount(getIntegerValue(data, "studentCount"));
        entity.setMaleCount(getIntegerValue(data, "maleCount"));
        entity.setFemaleCount(getIntegerValue(data, "femaleCount"));
        entity.setClassStatus(getStringValue(data, "classStatus"));
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
        entity.setCourseNature(getStringValue(data, "courseNature"));
        entity.setCourseCategory(getStringValue(data, "courseCategory"));
        entity.setCredits(getBigDecimalValue(data, "credits"));
        entity.setTotalHours(getIntegerValue(data, "totalHours"));
        entity.setTheoryHours(getIntegerValue(data, "theoryHours"));
        entity.setExperimentHours(getIntegerValue(data, "experimentHours"));
        entity.setPracticeHours(getIntegerValue(data, "practiceHours"));
        entity.setComputerHours(getIntegerValue(data, "computerHours"));
        entity.setCollegeCode(getStringValue(data, "collegeCode"));
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
        entity.setCourseStatus(getStringValue(data, "courseStatus"));
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
        entity.setSemester(getIntegerValue(data, "semester"));
        entity.setCapacity(getIntegerValue(data, "capacity"));
        entity.setEnrolledCount(getIntegerValue(data, "enrolledCount"));
        entity.setClassStatus(getStringValue(data, "classStatus"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));

        teachingClassMapper.upsertByClassId(entity);
    }

    private void upsertCourseSchedule(Map<String, Object> data) {
        // course_schedule 没有自然唯一键，使用 delete-then-insert 模式
        String classId = getStringValue(data, "classId");
        String academicYear = getStringValue(data, "academicYear");
        Integer semester = getIntegerValue(data, "semester");

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
        entity.setSelectionId(getStringValue(data, "selectionId"));
        entity.setStudentId(getStringValue(data, "studentId"));
        entity.setStudentName(getStringValue(data, "studentName"));
        entity.setCourseCode(getStringValue(data, "courseCode"));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setClassId(getStringValue(data, "classId"));
        entity.setTeacherId(getStringValue(data, "teacherId"));
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setCredits(getBigDecimalValue(data, "credits"));
        entity.setAcademicYear(getStringValue(data, "academicYear"));
        entity.setSemester(getIntegerValue(data, "semester"));
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
        entity.setStudentName(getStringValue(data, "studentName"));
        entity.setExamType(getStringValue(data, "examType"));
        entity.setExamName(getStringValue(data, "examName"));
        entity.setAdmissionTicketNo(getStringValue(data, "admissionTicketNo"));
        entity.setExamDate(getLocalDateValue(data, "examDate"));
        entity.setScore(getBigDecimalValue(data, "score"));
        entity.setGrade(getStringValue(data, "grade"));
        entity.setIsPassed(getBooleanValue(data, "isPassed"));
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
        entity.setPlanName(getStringValue(data, "planName"));
        entity.setMajorCode(getStringValue(data, "majorCode"));
        entity.setMajorName(getStringValue(data, "majorName"));
        entity.setGrade(getStringValue(data, "grade"));
        entity.setSchoolingLength(getIntegerValue(data, "schoolingLength"));
        entity.setDegreeType(getStringValue(data, "degreeType"));
        entity.setEducationLevel(getStringValue(data, "educationLevel"));
        entity.setCollegeCode(getStringValue(data, "collegeCode"));
        entity.setCollegeName(getStringValue(data, "collegeName"));
        entity.setTotalCredits(getBigDecimalValue(data, "totalCredits"));
        entity.setRequiredCredits(getBigDecimalValue(data, "requiredCredits"));
        entity.setElectiveCredits(getBigDecimalValue(data, "electiveCredits"));
        entity.setPracticeCredits(getBigDecimalValue(data, "practiceCredits"));
        entity.setGeneralEducationCredits(getBigDecimalValue(data, "generalEducationCredits"));
        entity.setMajorCoreCredits(getBigDecimalValue(data, "majorCoreCredits"));
        entity.setMajorElectiveCredits(getBigDecimalValue(data, "majorElectiveCredits"));
        entity.setTrainingObjective(getStringValue(data, "trainingObjective"));
        entity.setTrainingRequirements(getStringValue(data, "trainingRequirements"));
        entity.setMainDisciplines(getStringValue(data, "mainDisciplines"));
        entity.setCoreCourses(getStringValue(data, "coreCourses"));
        entity.setPlanStatus(getStringValue(data, "planStatus"));
        entity.setEffectiveDate(getLocalDateValue(data, "effectiveDate"));
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
                    logger.warn("同步教学计划课程明细失败: planId={}, 错误={}", planId, e.getMessage());
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
                return teachingClassMapper.selectCount(null);
            case "course_schedule":
                return courseScheduleMapper.selectCount(null);
            case "course_selection":
                return courseSelectionMapper.selectCount(null);
            case "level_exam_scores":
                return levelExamScoreMapper.selectCount(null);
            case "teaching_plans":
                return teachingPlanMapper.selectCount(null);
            default:
                return 0;
        }
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
