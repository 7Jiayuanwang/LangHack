package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.dto.learner.LearnerProfileDto;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.LearnerProfileService;
import com.english.service.SystemConfigService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师个人数据查询控制器
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherDataController {

    private static final Logger logger = LoggerFactory.getLogger(TeacherDataController.class);
    private static final String TEACHER_SCHOOL_DATA_TOGGLE = "teacher.school_data_enabled";
    private static final String TEACHER_LEARNER_PROFILE_TOGGLE = "teacher.learner_profile_enabled";

    @Autowired
    private TeachingClassMapper teachingClassMapper;

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    @Autowired
    private CourseSelectionMapper courseSelectionMapper;

    @Autowired
    private TeacherStudentBindingMapper teacherStudentBindingMapper;

    @Autowired
    private SchoolStudentMapper schoolStudentMapper;

    @Autowired
    private LevelExamScoreMapper levelExamScoreMapper;

    @Autowired
    private UserService userService;
    
    @Autowired
    private com.english.mapper.StudentClassMapper studentClassMapper;

    @Autowired
    private com.english.service.VocabularyBooksService vocabularyBooksService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private LearnerProfileService learnerProfileService;

    @Autowired
    private com.english.mapper.UserMapper userMapper;

    /**
     * 获取当前教师的授课班级列表
     * GET /api/teacher/my-courses?academicYear=2024&semester=秋
     */
    @GetMapping("/my-courses")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<List<TeachingClass>> getMyCourses(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester,
            Authentication authentication) {
        try {
            String teacherId = getTeacherId(authentication);
            QueryWrapper<TeachingClass> wrapper = new QueryWrapper<>();
            wrapper.eq("teacher_id", teacherId);
            if (academicYear != null && !academicYear.isEmpty()) {
                wrapper.eq("academic_year", academicYear);
            }
            if (semester != null && !semester.isEmpty()) {
                wrapper.eq("semester", toIntSemester(semester));
            }
            wrapper.orderByDesc("academic_year", "semester");
            return Result.success(teachingClassMapper.selectList(wrapper));
        } catch (Exception e) {
            logger.error("获取教师授课信息失败", e);
            return Result.error("获取授课信息失败，请稍后重试");
        }
    }

    /**
     * 获取当前教师的学生列表（通过 teacher_student_binding）
     * GET /api/teacher/my-students?academicYear=2025&semester=秋&courseCode=xxx
     */
    @GetMapping("/my-students")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<List<SchoolStudent>> getMyStudents(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String courseCode,
            Authentication authentication) {
        try {
            if (!isTeacherSchoolDataEnabled()) {
                return Result.error("教师端学校数据功能已关闭");
            }
            String teacherId = getTeacherId(authentication);
            QueryWrapper<TeacherStudentBinding> bindingWrapper = new QueryWrapper<>();
            bindingWrapper.eq("teacher_id", teacherId);
            if (academicYear != null && !academicYear.isEmpty()) {
                bindingWrapper.eq("academic_year", academicYear);
            }
            if (semester != null && !semester.isEmpty()) {
                String bindingSemester = toBindingSemester(semester);
                if (bindingSemester.equals(semester)) {
                    bindingWrapper.eq("semester", bindingSemester);
                } else {
                    bindingWrapper.and(w -> w.eq("semester", bindingSemester).or().eq("semester", semester));
                }
            }
            if (courseCode != null && !courseCode.isEmpty()) {
                bindingWrapper.eq("course_code", courseCode);
            }
            List<TeacherStudentBinding> bindings = teacherStudentBindingMapper.selectList(bindingWrapper);
            if (bindings.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            Set<String> studentIds = bindings.stream()
                    .map(TeacherStudentBinding::getStudentId)
                    .collect(Collectors.toSet());
            QueryWrapper<SchoolStudent> studentWrapper = new QueryWrapper<>();
            studentWrapper.in("student_id", studentIds);
            studentWrapper.orderByAsc("student_name");
            List<SchoolStudent> students = schoolStudentMapper.selectList(studentWrapper);
            // school_student.student_name 在生产数据中 99.99% 是学号本身（脏数据）。
            // 降级为 NULL：让前端改用 studentId 显示，避免"姓名"列显示学号造成混淆。
            for (SchoolStudent s : students) {
                String name = s.getStudentName();
                if (name != null && name.matches("^[0-9]+$")) {
                    s.setStudentName(null);
                }
            }
            return Result.success(students);
        } catch (Exception e) {
            logger.error("获取教师学生列表失败", e);
            return Result.error("获取学生列表失败，请稍后重试");
        }
    }

    /**
     * 获取当前教师的课表（通过 teaching_class 关联 course_schedule）
     * GET /api/teacher/my-schedule?academicYear=2024&semester=秋
     */
    @GetMapping("/my-schedule")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<List<CourseSchedule>> getMySchedule(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester,
            Authentication authentication) {
        try {
            if (!isTeacherSchoolDataEnabled()) {
                return Result.error("教师端学校数据功能已关闭");
            }
            String teacherId = getTeacherId(authentication);
            QueryWrapper<TeachingClass> classWrapper = new QueryWrapper<>();
            classWrapper.eq("teacher_id", teacherId).select("class_id");
            if (academicYear != null && !academicYear.isEmpty()) {
                classWrapper.eq("academic_year", academicYear);
            }
            if (semester != null && !semester.isEmpty()) {
                classWrapper.eq("semester", toIntSemester(semester));
            }
            List<Map<String, Object>> classMaps = teachingClassMapper.selectMaps(classWrapper);
            if (classMaps.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            List<String> classIds = classMaps.stream()
                    .map(m -> m.get("class_id"))
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            QueryWrapper<CourseSchedule> scheduleWrapper = new QueryWrapper<>();
            scheduleWrapper.in("class_id", classIds);
            scheduleWrapper.orderByAsc("day_of_week", "period");
            return Result.success(courseScheduleMapper.selectList(scheduleWrapper));
        } catch (Exception e) {
            logger.error("获取教师课表失败", e);
            return Result.error("获取课表失败，请稍后重试");
        }
    }

    /**
     * 获取当前教师课程的选课记录
     * GET /api/teacher/my-course-selections?academicYear=2024&semester=秋&courseCode=xxx
     */
    @GetMapping("/my-course-selections")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<List<CourseSelection>> getMyCourseSelections(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String courseCode,
            Authentication authentication) {
        try {
            if (!isTeacherSchoolDataEnabled()) {
                return Result.error("教师端学校数据功能已关闭");
            }
            String teacherId = getTeacherId(authentication);
            QueryWrapper<CourseSelection> wrapper = new QueryWrapper<>();
            wrapper.eq("teacher_id", teacherId);
            if (academicYear != null && !academicYear.isEmpty()) {
                wrapper.eq("academic_year", academicYear);
            }
            if (semester != null && !semester.isEmpty()) {
                wrapper.eq("semester", toIntSemester(semester));
            }
            if (courseCode != null && !courseCode.isEmpty()) {
                wrapper.eq("course_code", courseCode);
            }
            wrapper.orderByDesc("academic_year", "semester").orderByAsc("student_name");
            List<CourseSelection> selections = courseSelectionMapper.selectList(wrapper);
            // course_selection.student_name 在生产数据中可能与 school_student.student_name 一样被填了学号。
            // 降级为 NULL：避免"姓名"列显示学号造成混淆
            for (CourseSelection cs : selections) {
                String name = cs.getStudentName();
                if (name != null && name.matches("^[0-9]+$")) {
                    cs.setStudentName(null);
                }
            }
            return Result.success(selections);
        } catch (Exception e) {
            logger.error("获取教师选课记录失败", e);
            return Result.error("获取选课记录失败，请稍后重试");
        }
    }

    /**
     * 查看指定学生的四六级成绩（仅限有绑定关系的学生）
     * GET /api/teacher/student/{studentId}/scores
     */
    @GetMapping("/student/{studentId}/scores")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<List<LevelExamScore>> getStudentScores(
            @PathVariable("studentId") String studentId,
            Authentication authentication) {
        try {
            if (!isTeacherSchoolDataEnabled()) {
                return Result.error("教师端学校数据功能已关闭");
            }
            String teacherId = getTeacherId(authentication);
            // 验证绑定关系（不限学年，历史绑定即可查看成绩，符合教学档案查阅需求）
            // 如需限制为当前学年，可在此加 academic_year 过滤
            QueryWrapper<TeacherStudentBinding> check = new QueryWrapper<>();
            check.eq("teacher_id", teacherId).eq("student_id", studentId).last("LIMIT 1");
            if (teacherStudentBindingMapper.selectOne(check) == null) {
                return Result.error("无权查看该学生成绩");
            }
            QueryWrapper<LevelExamScore> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId).orderByDesc("exam_date");
            return Result.success(levelExamScoreMapper.selectList(wrapper));
        } catch (Exception e) {
            logger.error("获取学生成绩失败 studentId={}", studentId, e);
            return Result.error("获取成绩失败，请稍后重试");
        }
    }

    /**
     * 获取教师数据统计概览
     * GET /api/teacher/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<Map<String, Object>> getStats(Authentication authentication) {
        try {
            if (!isTeacherSchoolDataEnabled()) {
                return Result.error("教师端学校数据功能已关闭");
            }
            String teacherId = getTeacherId(authentication);
            long courseCount = teachingClassMapper.selectCount(
                    new QueryWrapper<TeachingClass>().eq("teacher_id", teacherId));
            long studentCount = teacherStudentBindingMapper.selectCount(
                    new QueryWrapper<TeacherStudentBinding>().eq("teacher_id", teacherId));
            // 去重学生数：用 COUNT(DISTINCT) 避免全量加载到内存
            // 注意：selectMaps 返回的 key 名称依赖 JDBC 驱动，"cnt" 别名在 MySQL 8 + MyBatis Plus 下可靠
            // 若 cnt 为 null（驱动返回不同 key），降级使用 bindingCount 并记录警告
            QueryWrapper<TeacherStudentBinding> distinctWrapper = new QueryWrapper<>();
            distinctWrapper.eq("teacher_id", teacherId).select("COUNT(DISTINCT student_id) as cnt");
            List<Map<String, Object>> cntRows = teacherStudentBindingMapper.selectMaps(distinctWrapper);
            long uniqueStudentCount;
            if (cntRows != null && !cntRows.isEmpty() && cntRows.get(0).get("cnt") != null) {
                uniqueStudentCount = ((Number) cntRows.get(0).get("cnt")).longValue();
            } else {
                // 降级：key 名称不匹配时尝试取第一个值
                uniqueStudentCount = (cntRows != null && !cntRows.isEmpty() && !cntRows.get(0).isEmpty())
                        ? ((Number) cntRows.get(0).values().iterator().next()).longValue()
                        : studentCount;
                logger.warn("getStats: COUNT(DISTINCT) 结果 key 不是 'cnt'，已降级，teacherId={}", teacherId);
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("courseCount", courseCount);
            stats.put("bindingCount", studentCount);
            stats.put("uniqueStudentCount", uniqueStudentCount);
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取教师统计数据失败", e);
            return Result.error("获取统计数据失败，请稍后重试");
        }
    }

    private String getTeacherId(Authentication authentication) {
        com.english.entity.User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new RuntimeException("用户信息不存在");
        }
        return user.getUsername();
    }

    /**
     * 教师查看班级学生的单词学习进度
     * GET /api/teacher/word-progress?classId=123
     */
    @GetMapping("/word-progress")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<java.util.List<java.util.Map<String, Object>>> getClassWordProgress(
            @RequestParam Long classId,
            Authentication authentication) {
        try {
            if (classId == null) return Result.error("classId 不能为空");

            // 查询班级内学生
            java.util.List<com.english.entity.StudentClass> students = studentClassMapper.selectStudentsByClassId(classId);
            java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

            if (students == null || students.isEmpty()) {
                return Result.success(result);
            }

            for (com.english.entity.StudentClass sc : students) {
                Long studentId = sc.getStudentId();
                com.english.entity.User student = userService.getUserById(studentId);
                if (student == null) continue;

                java.util.Map<String, Object> stats = vocabularyBooksService.getWordStudyStats(studentId, null);

                java.util.Map<String, Object> entry = new java.util.HashMap<>();
                entry.put("studentId", studentId);
                entry.put("username", student.getUsername());
                entry.put("realName", student.getRealName());
                entry.put("classJoinTime", sc.getJoinTime());
                entry.put("wordStats", stats != null ? stats : new java.util.HashMap<>());

                result.add(entry);
            }

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取班级单词进度失败", e);
            return Result.error("获取班级单词进度失败，请稍后重试");
        }
    }

    /**
     * 获取学生的学习者画像
     * GET /api/teacher/student-profile/{studentId}
     */
    @GetMapping("/student-profile/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<LearnerProfileDto> getStudentProfile(
            @PathVariable Long studentId,
            Authentication authentication) {
        try {
            if (!isTeacherLearnerProfileEnabled()) {
                return Result.error("教师端学习者画像功能已关闭");
            }
            if (studentId == null || studentId <= 0) {
                return Result.error("无效的学生ID");
            }
            String teacherId = getTeacherId(authentication);

            // 验证教师与学生的绑定关系
            QueryWrapper<TeacherStudentBinding> bindingWrapper = new QueryWrapper<>();
            bindingWrapper.eq("teacher_id", teacherId)
                    .eq("student_id", String.valueOf(studentId));
            Long bindingCount = teacherStudentBindingMapper.selectCount(bindingWrapper);
            if (bindingCount == null || bindingCount == 0) {
                return Result.error("无权查看该学生画像");
            }

            // 根据 school_student.student_id 找到平台用户
            QueryWrapper<SchoolStudent> ssWrapper = new QueryWrapper<>();
            ssWrapper.eq("student_id", String.valueOf(studentId));
            SchoolStudent schoolStudent = schoolStudentMapper.selectOne(ssWrapper);
            if (schoolStudent == null) {
                return Result.error("学生信息不存在");
            }

            // 找到关联的平台用户
            QueryWrapper<User> userWrapper = new QueryWrapper<>();
            userWrapper.eq("school_student_id", schoolStudent.getId()).eq("deleted", 0);
            User platformUser = userMapper.selectOne(userWrapper);
            if (platformUser == null) {
                return Result.error("该学生尚未关联平台账号");
            }

            LearnerProfileDto profile = learnerProfileService.getUserProfile(platformUser.getId());
            return Result.success(profile);
        } catch (Exception e) {
            logger.error("获取学生画像失败: studentId={}", studentId, e);
            return Result.error("获取学生画像失败，请稍后重试");
        }
    }

    private boolean isTeacherSchoolDataEnabled() {
        String value = systemConfigService.get("feature_toggle", TEACHER_SCHOOL_DATA_TOGGLE, "true");
        return value == null || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private boolean isTeacherLearnerProfileEnabled() {
        String value = systemConfigService.get("feature_toggle", TEACHER_LEARNER_PROFILE_TOGGLE, "true");
        return value == null || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /** 将前端传入的 "秋"/"春" 转换为数据库存储的 Integer 1/2（用于 teaching_class / course_selection 等整型学期字段） */
    private Integer toIntSemester(String semester) {
        if (semester == null || semester.isEmpty()) return null;
        if ("秋".equals(semester) || "1".equals(semester)) return 1;
        if ("春".equals(semester) || "2".equals(semester)) return 2;
        try { return Integer.parseInt(semester); } catch (NumberFormatException e) { return null; }
    }

    private String toBindingSemester(String semester) {
        Integer value = toIntSemester(semester);
        return value == null ? semester : String.valueOf(value);
    }
}
