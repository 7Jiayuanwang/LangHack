package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.*;
import com.english.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
            @RequestParam(required = false) Integer semester) {
        Page<TeachingClass> pageReq = new Page<>(page, size);
        QueryWrapper<TeachingClass> wrapper = new QueryWrapper<>();
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        if (semester != null) wrapper.eq("semester", semester);
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
            @RequestParam(required = false) Integer semester) {
        Page<CourseSchedule> pageReq = new Page<>(page, size);
        QueryWrapper<CourseSchedule> wrapper = new QueryWrapper<>();
        if (classId != null && !classId.isEmpty()) wrapper.eq("class_id", classId);
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        if (semester != null) wrapper.eq("semester", semester);
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
            @RequestParam(required = false) Integer semester) {
        Page<CourseSelection> pageReq = new Page<>(page, size);
        QueryWrapper<CourseSelection> wrapper = new QueryWrapper<>();
        if (studentId != null && !studentId.isEmpty()) wrapper.eq("student_id", studentId);
        if (courseCode != null && !courseCode.isEmpty()) wrapper.eq("course_code", courseCode);
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        if (semester != null) wrapper.eq("semester", semester);
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
            @PathVariable String planId) {
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
            @RequestParam(required = false) Integer semester) {
        Page<StudentSchedule> pageReq = new Page<>(page, size);
        QueryWrapper<StudentSchedule> wrapper = new QueryWrapper<>();
        if (studentId != null && !studentId.isEmpty()) wrapper.eq("student_id", studentId);
        if (academicYear != null && !academicYear.isEmpty()) wrapper.eq("academic_year", academicYear);
        if (semester != null) wrapper.eq("semester", semester);
        wrapper.orderByAsc("day_of_week", "period");
        return Result.success(studentScheduleMapper.selectPage(pageReq, wrapper));
    }

    // =========================================================================
    // 数据概览（各表记录数）
    // =========================================================================

    /**
     * GET /api/admin/school-data/overview
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Long>> getOverview() {
        Map<String, Long> overview = new LinkedHashMap<>();
        overview.put("administrative_class", administrativeClassMapper.selectCount(null));
        overview.put("school_course", schoolCourseMapper.selectCount(null));
        overview.put("teaching_class", teachingClassMapper.selectCount(null));
        overview.put("course_schedule", courseScheduleMapper.selectCount(null));
        overview.put("course_selection", courseSelectionMapper.selectCount(null));
        overview.put("level_exam_scores", levelExamScoreMapper.selectCount(null));
        overview.put("teaching_plans", teachingPlanMapper.selectCount(null));
        overview.put("student_schedule", studentScheduleMapper.selectCount(null));
        return Result.success(overview);
    }
}
