package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.dto.SchoolDataFilterFieldDto;
import com.english.dto.SchoolDataQueryRequest;
import com.english.dto.SchoolDataSyncFilterRequest;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.SchoolDataFieldMetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学校数据查询控制器
 * <p>
 * 管理员/运维查看已同步的学校数据，支持分页和筛选
 */
@RestController
@RequestMapping("/api/admin/school-data")
public class SchoolDataQueryController {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataQueryController.class);

    @Autowired private AdministrativeClassMapper administrativeClassMapper;
    @Autowired private SchoolCourseMapper schoolCourseMapper;
    @Autowired private TeachingClassMapper teachingClassMapper;
    @Autowired private CourseScheduleMapper courseScheduleMapper;
    @Autowired private CourseSelectionMapper courseSelectionMapper;
    @Autowired private LevelExamScoreMapper levelExamScoreMapper;
    @Autowired private TeachingPlanMapper teachingPlanMapper;
    @Autowired private TeachingPlanCourseMapper teachingPlanCourseMapper;
    @Autowired private StudentScheduleMapper studentScheduleMapper;
    @Autowired private SchoolStudentMapper schoolStudentMapper;
    @Autowired private SchoolTeacherMapper schoolTeacherMapper;
    @Autowired private TeacherStudentBindingMapper teacherStudentBindingMapper;
    @Autowired private SchoolDataFieldMetadataService fieldMetadataService;
    @Autowired private JdbcTemplate jdbcTemplate;

    // =========================================================================
    // 行政班
    // =========================================================================

    /**
     * GET /api/admin/school-data/administrative-classes?page=1&size=20&collegeCode=&grade=
     */
    @GetMapping("/administrative-classes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<AdministrativeClass>> listAdministrativeClasses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String collegeCode,
            @RequestParam(required = false) String grade) {
        Page<AdministrativeClass> pageReq = new Page<>(page, size);
        QueryWrapper<AdministrativeClass> wrapper = new QueryWrapper<>();
        if (collegeCode != null && !collegeCode.isEmpty()) wrapper.eq("college_code", collegeCode);
        if (grade != null && !grade.isEmpty()) wrapper.eq("grade", grade);
        wrapper.orderByDesc("updated_at");
        return Result.success(administrativeClassMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 课程
    // =========================================================================

    /**
     * GET /api/admin/school-data/courses?page=1&size=20&courseNature=&courseCategory=
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<SchoolCourse>> listSchoolCourses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String courseNature,
            @RequestParam(required = false) String courseCategory) {
        Page<SchoolCourse> pageReq = new Page<>(page, size);
        QueryWrapper<SchoolCourse> wrapper = new QueryWrapper<>();
        if (courseNature != null && !courseNature.isEmpty()) wrapper.eq("course_nature", courseNature);
        if (courseCategory != null && !courseCategory.isEmpty()) wrapper.eq("course_category", courseCategory);
        wrapper.orderByDesc("updated_at");
        return Result.success(schoolCourseMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 教学班
    // =========================================================================

    /**
     * GET /api/admin/school-data/teaching-classes?page=1&size=20&academicYear=&semester=
     */
    @GetMapping("/teaching-classes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<TeachingClass>> listTeachingClasses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester) {
        Page<TeachingClass> pageReq = new Page<>(page, size);
        QueryWrapper<TeachingClass> wrapper = new QueryWrapper<>();
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        applySemesterFilter(wrapper, semester);
        wrapper.orderByDesc("updated_at");
        return Result.success(teachingClassMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 课程安排
    // =========================================================================

    /**
     * GET /api/admin/school-data/course-schedules?page=1&size=20&classId=&academicYear=&semester=
     */
    @GetMapping("/course-schedules")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<CourseSchedule>> listCourseSchedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester) {
        Page<CourseSchedule> pageReq = new Page<>(page, size);
        QueryWrapper<CourseSchedule> wrapper = new QueryWrapper<>();
        if (classId != null && !classId.isEmpty()) wrapper.eq("class_id", classId);
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        applySemesterFilter(wrapper, semester);
        wrapper.orderByAsc("day_of_week", "period");
        return Result.success(courseScheduleMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 选课记录
    // =========================================================================

    /**
     * GET /api/admin/school-data/course-selections?page=1&size=20&studentId=&courseCode=&academicYear=&semester=
     */
    @GetMapping("/course-selections")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<CourseSelection>> listCourseSelections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String courseCode,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester) {
        Page<CourseSelection> pageReq = new Page<>(page, size);
        QueryWrapper<CourseSelection> wrapper = new QueryWrapper<>();
        if (studentId != null && !studentId.isEmpty()) wrapper.eq("student_id", studentId);
        if (courseCode != null && !courseCode.isEmpty()) wrapper.eq("course_code", courseCode);
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        applySemesterFilter(wrapper, semester);
        wrapper.orderByDesc("updated_at");
        return Result.success(courseSelectionMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 等级考试成绩
    // =========================================================================

    /**
     * GET /api/admin/school-data/level-exam-scores?page=1&size=20&studentId=&examType=
     */
    @GetMapping("/level-exam-scores")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<LevelExamScore>> listLevelExamScores(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String examType) {
        Page<LevelExamScore> pageReq = new Page<>(page, size);
        QueryWrapper<LevelExamScore> wrapper = new QueryWrapper<>();
        if (studentId != null && !studentId.isEmpty()) wrapper.eq("student_id", studentId);
        if (examType != null && !examType.isEmpty()) wrapper.eq("exam_type", examType);
        wrapper.orderByDesc("exam_date");
        return Result.success(levelExamScoreMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 教学计划
    // =========================================================================

    /**
     * GET /api/admin/school-data/teaching-plans?page=1&size=20&majorCode=&grade=
     */
    @GetMapping("/teaching-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<TeachingPlan>> listTeachingPlans(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String majorCode,
            @RequestParam(required = false) String grade) {
        Page<TeachingPlan> pageReq = new Page<>(page, size);
        QueryWrapper<TeachingPlan> wrapper = new QueryWrapper<>();
        if (majorCode != null && !majorCode.isEmpty()) wrapper.eq("major_code", majorCode);
        if (grade != null && !grade.isEmpty()) wrapper.eq("grade", grade);
        wrapper.orderByDesc("updated_at");
        return Result.success(teachingPlanMapper.selectPage(pageReq, wrapper));
    }

    /**
     * GET /api/admin/school-data/teaching-plans/{planId}/courses
     */
    @GetMapping("/teaching-plans/{planId}/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<java.util.List<TeachingPlanCourse>> listTeachingPlanCourses(
            @PathVariable("planId") String planId) {
        QueryWrapper<TeachingPlanCourse> wrapper = new QueryWrapper<>();
        wrapper.eq("plan_id", planId).orderByAsc("semester", "sort_order");
        return Result.success(teachingPlanCourseMapper.selectList(wrapper));
    }

    // =========================================================================
    // 学生课表
    // =========================================================================

    /**
     * GET /api/admin/school-data/student-schedules?page=1&size=20&studentId=&academicYear=&semester=
     */
    @GetMapping("/student-schedules")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<StudentSchedule>> listStudentSchedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester) {
        Page<StudentSchedule> pageReq = new Page<>(page, size);
        QueryWrapper<StudentSchedule> wrapper = new QueryWrapper<>();
        if (studentId != null && !studentId.isEmpty()) wrapper.eq("student_id", studentId);
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        applySemesterFilter(wrapper, semester);
        wrapper.orderByAsc("day_of_week", "period");
        return Result.success(studentScheduleMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 数据概览（各表记录数）
    // =========================================================================

    /**
     * GET /api/admin/school-data/overview
     * 使用 information_schema 近似行数，毫秒级返回，避免全表 COUNT 阻塞页面。
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Long>> getOverview() {
        // 目标表名 → 前端 key 映射
        Map<String, String> tableToKey = new LinkedHashMap<>();
        tableToKey.put("administrative_class",    "administrative_class");
        tableToKey.put("school_course",           "school_course");
        tableToKey.put("teaching_class",          "teacher_courses");
        tableToKey.put("course_schedule",         "course_schedule");
        tableToKey.put("course_selection",        "course_selection");
        tableToKey.put("level_exam_scores",       "level_exam_scores");
        tableToKey.put("teaching_plans",          "teaching_plans");
        tableToKey.put("student_schedule",        "student_schedule");
        tableToKey.put("school_student",          "school_student");
        tableToKey.put("school_teacher",          "school_teacher");
        tableToKey.put("teacher_student_binding", "teacher_student_binding");

        Map<String, Long> overview = new LinkedHashMap<>();
        // 初始化为 0，防止某张表查不到时缺失 key
        tableToKey.values().forEach(k -> overview.put(k, 0L));

        try {
            String inClause = tableToKey.keySet().stream()
                    .map(t -> "'" + t + "'")
                    .collect(java.util.stream.Collectors.joining(","));
            String sql = "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" + inClause + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("TABLE_NAME");
                Object rowsObj = row.get("TABLE_ROWS");
                long count = rowsObj == null ? 0L : ((Number) rowsObj).longValue();
                String key = tableToKey.get(tableName);
                if (key != null) {
                    overview.put(key, count);
                }
            }
        } catch (Exception e) {
            logger.warn("information_schema 查询失败，降级为精确 COUNT: {}", e.getMessage());
            overview.put("administrative_class",    administrativeClassMapper.selectCount(null));
            overview.put("school_course",           schoolCourseMapper.selectCount(null));
            overview.put("teacher_courses",         teachingClassMapper.selectCount(null));
            overview.put("course_schedule",         courseScheduleMapper.selectCount(null));
            overview.put("course_selection",        courseSelectionMapper.selectCount(null));
            overview.put("level_exam_scores",       levelExamScoreMapper.selectCount(null));
            overview.put("teaching_plans",          teachingPlanMapper.selectCount(null));
            overview.put("student_schedule",        studentScheduleMapper.selectCount(null));
            overview.put("school_student",          schoolStudentMapper.selectCount(null));
            overview.put("school_teacher",          schoolTeacherMapper.selectCount(null));
            overview.put("teacher_student_binding", teacherStudentBindingMapper.selectCount(null));
        }
        return Result.success(overview);
    }

    @GetMapping("/{dataSource}/filter-fields")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<java.util.List<SchoolDataFilterFieldDto>> getFilterFields(@PathVariable("dataSource") String dataSource) {
        return Result.success(fieldMetadataService.getFilterFields(dataSource));
    }

    @PostMapping("/{dataSource}/query")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<?> querySchoolData(@PathVariable("dataSource") String dataSource,
                                     @RequestBody(required = false) SchoolDataQueryRequest request) {
        SchoolDataQueryRequest queryRequest = request == null ? new SchoolDataQueryRequest() : request;
        switch (dataSource) {
            case "administrative_class": {
                Page<AdministrativeClass> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<AdministrativeClass> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("updated_at");
                return Result.success(administrativeClassMapper.selectPage(pageReq, wrapper));
            }
            case "school_course": {
                Page<SchoolCourse> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<SchoolCourse> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("updated_at");
                return Result.success(schoolCourseMapper.selectPage(pageReq, wrapper));
            }
            case "teaching_class":
            case "teacher_courses": {
                Page<TeachingClass> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<TeachingClass> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("updated_at");
                return Result.success(teachingClassMapper.selectPage(pageReq, wrapper));
            }
            case "course_schedule": {
                Page<CourseSchedule> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<CourseSchedule> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByAsc("day_of_week", "period");
                return Result.success(courseScheduleMapper.selectPage(pageReq, wrapper));
            }
            case "course_selection": {
                Page<CourseSelection> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<CourseSelection> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("updated_at");
                return Result.success(courseSelectionMapper.selectPage(pageReq, wrapper));
            }
            case "level_exam_scores": {
                Page<LevelExamScore> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<LevelExamScore> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("exam_date");
                return Result.success(levelExamScoreMapper.selectPage(pageReq, wrapper));
            }
            case "teaching_plans": {
                Page<TeachingPlan> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<TeachingPlan> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("updated_at");
                return Result.success(teachingPlanMapper.selectPage(pageReq, wrapper));
            }
            case "student_schedule": {
                Page<StudentSchedule> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<StudentSchedule> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByAsc("day_of_week", "period");
                return Result.success(studentScheduleMapper.selectPage(pageReq, wrapper));
            }
            case "school_student": {
                Page<SchoolStudent> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<SchoolStudent> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("updated_at");
                return Result.success(schoolStudentMapper.selectPage(pageReq, wrapper));
            }
            case "school_teacher": {
                Page<SchoolTeacher> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<SchoolTeacher> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("updated_at");
                return Result.success(schoolTeacherMapper.selectPage(pageReq, wrapper));
            }
            case "teacher_student_binding": {
                Page<TeacherStudentBinding> pageReq = new Page<>(queryRequest.getPage(), queryRequest.getSize());
                QueryWrapper<TeacherStudentBinding> wrapper = new QueryWrapper<>();
                applyFilterGroups(dataSource, wrapper, queryRequest.getFilterGroups());
                wrapper.orderByDesc("sync_time");
                return Result.success(teacherStudentBindingMapper.selectPage(pageReq, wrapper));
            }
            default:
                throw new IllegalArgumentException("不支持的数据源: " + dataSource);
        }
    }

    private void applyFilterGroups(String dataSource,
                                   QueryWrapper<?> wrapper,
                                   java.util.List<SchoolDataSyncFilterRequest.FilterGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        Map<String, SchoolDataFilterFieldDto> fields = fieldMetadataService.getFilterFieldMap(dataSource);
        for (SchoolDataSyncFilterRequest.FilterGroup group : groups) {
            if (group == null || group.getConditions() == null || group.getConditions().isEmpty()) {
                continue;
            }
            boolean isOr = "OR".equalsIgnoreCase(group.getRelation());
            if (isOr) {
                wrapper.and(orWrapper -> {
                    boolean first = true;
                    for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                        if (!first) {
                            orWrapper.or();
                        }
                        applySingleFilter(orWrapper, fields, condition);
                        first = false;
                    }
                });
            } else {
                for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                    applySingleFilter(wrapper, fields, condition);
                }
            }
        }
    }

    private void applySingleFilter(QueryWrapper<?> wrapper,
                                   Map<String, SchoolDataFilterFieldDto> fields,
                                   SchoolDataSyncFilterRequest.FilterCondition condition) {
        if (condition == null || condition.getField() == null || condition.getValue() == null || condition.getValue().trim().isEmpty()) {
            return;
        }
        SchoolDataFilterFieldDto field = fields.get(condition.getField());
        if (field == null || !field.getCapabilities().isListQuery() || field.getColumn() == null || field.getColumn().isEmpty()) {
            throw new IllegalArgumentException("不支持的筛选字段: " + condition.getField());
        }
        String operator = condition.getOperator() == null || condition.getOperator().trim().isEmpty()
                ? "eq" : condition.getOperator().trim().toLowerCase(java.util.Locale.ROOT);
        if (!field.getOperators().contains(operator)) {
            throw new IllegalArgumentException("字段 " + field.getField() + " 不支持操作符: " + operator);
        }
        Object value = normalizeFilterValue(field, condition.getValue());
        switch (operator) {
            case "contains":
                wrapper.like(field.getColumn(), value);
                break;
            case "gte":
                wrapper.ge(field.getColumn(), value);
                break;
            case "lte":
                wrapper.le(field.getColumn(), value);
                break;
            case "in":
                wrapper.in(field.getColumn(), parseInValues(field, condition.getValue()));
                break;
            case "eq":
            default:
                wrapper.eq(field.getColumn(), value);
                break;
        }
    }

    private java.util.List<Object> parseInValues(SchoolDataFilterFieldDto field, String value) {
        java.util.List<Object> values = new java.util.ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.trim().isEmpty()) {
                values.add(normalizeFilterValue(field, part.trim()));
            }
        }
        return values;
    }

    private Object normalizeFilterValue(SchoolDataFilterFieldDto field, String value) {
        if ("semester".equals(field.getField())) {
            return normalizeSemesterValue(value);
        }
        if ("BOOLEAN".equals(field.getType())) {
            String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
            return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "是".equals(normalized) || "通过".equals(normalized);
        }
        if ("NUMBER".equals(field.getType())) {
            return new java.math.BigDecimal(value.trim());
        }
        return value.trim();
    }

    private void applySemesterFilter(QueryWrapper<?> wrapper, String semester) {
        Integer normalizedSemester = normalizeSemesterValue(semester);
        if (normalizedSemester != null) {
            wrapper.eq("semester", normalizedSemester);
        }
    }

    private Integer normalizeSemesterValue(String semester) {
        if (semester == null || semester.trim().isEmpty()) {
            return null;
        }
        String normalized = semester.trim();
        if ("1".equals(normalized) || "秋".equals(normalized)) {
            return 1;
        }
        if ("2".equals(normalized) || "春".equals(normalized)) {
            return 2;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析学期: " + semester);
        }
    }
}
