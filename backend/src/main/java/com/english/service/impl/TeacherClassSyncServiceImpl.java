package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.*;
import com.english.entity.Class;
import com.english.mapper.*;
import com.english.service.TeacherClassSyncService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherClassSyncServiceImpl implements TeacherClassSyncService {

    private static final Logger logger = LoggerFactory.getLogger(TeacherClassSyncServiceImpl.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeachingClassMapper teachingClassMapper;

    @Autowired
    private CourseSelectionMapper courseSelectionMapper;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("schoolDataApiClientDelegate")
    private com.english.service.SchoolDataApiClient schoolDataApiClient;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Override
    @Transactional
    public Map<String, Object> syncClassesForTeacher(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        int classesCreated = 0;
        int classesSkipped = 0;
        int studentsAdded = 0;
        int studentsSkipped = 0;
        List<String> warnings = new ArrayList<>();

        User teacher = userService.getUserById(userId);
        if (teacher == null) {
            result.put("success", false);
            result.put("message", "教师用户不存在");
            return result;
        }

        String teacherWorkId = teacher.getUsername();
        logger.info("[classSync] 开始同步: userId={}, username={}", userId, teacherWorkId);

        // 计算当前学年学期：8月及以后为秋季(1)，2月及以后为春季(2)
        java.time.LocalDate today = java.time.LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        String currentAcademicYear;
        int currentSemester;
        if (month >= 8) {
            currentAcademicYear = String.valueOf(year);
            currentSemester = 1;
        } else if (month >= 2) {
            currentAcademicYear = String.valueOf(year - 1);
            currentSemester = 2;
        } else {
            currentAcademicYear = String.valueOf(year - 1);
            currentSemester = 1;
        }
        logger.info("[classSync] 当前学期: academicYear={}, semester={}", currentAcademicYear, currentSemester);

        // 1. 查询该教师在 teaching_class 中当前学期的教学班
        QueryWrapper<TeachingClass> tcWrapper = new QueryWrapper<>();
        tcWrapper.eq("teacher_id", teacherWorkId);
        tcWrapper.eq("academic_year", currentAcademicYear);
        tcWrapper.eq("semester", currentSemester);
        tcWrapper.orderByDesc("academic_year", "semester");
        List<TeachingClass> teachingClasses = teachingClassMapper.selectList(tcWrapper);

        if (teachingClasses.isEmpty()) {
            result.put("success", true);
            result.put("message", "未找到该教师的学校教学班数据，请先执行学校数据同步");
            result.put("classesCreated", 0);
            result.put("studentsAdded", 0);
            return result;
        }

        // 2. 按课程分组处理
        for (TeachingClass tc : teachingClasses) {
            String kcbh = tc.getClassId();
            String courseCode = tc.getCourseCode();
            String courseName = tc.getCourseName();

            if (kcbh == null || kcbh.isEmpty()) {
                warnings.add("教学班缺少 class_id(kcbh)，跳过");
                classesSkipped++;
                continue;
            }

            // 2.1 查/建平台课程
            Long courseId = findOrCreateCourse(courseCode, courseName, userId);

            // 2.2 查/建平台班级（含恢复已软删除的班级）
            Long classId;
            Class existingClass = findClassBySchoolId(kcbh, false);

            if (existingClass != null) {
                classId = existingClass.getId();
                classesSkipped++;
            } else {
                // 检查是否有软删除的同 school_class_id 班级，有则恢复
                Class deletedClass = findClassBySchoolId(kcbh, true);
                if (deletedClass != null) {
                    String className = buildClassName(courseName, tc.getClassName(), kcbh);
                    classMapper.restoreClass(deletedClass.getId(), className, courseId, userId, buildDescription(tc));
                    classId = deletedClass.getId();
                    classesCreated++;
                    logger.info("[classSync] 恢复已删除班级: kcbh={}", kcbh);
                } else {
                    String className = buildClassName(courseName, tc.getClassName(), kcbh);
                    Class newClass = new Class();
                    newClass.setName(className);
                    newClass.setCourseId(courseId);
                    newClass.setTeacherId(userId);
                    newClass.setSchoolClassId(kcbh);
                    newClass.setDescription(buildDescription(tc));
                    classMapper.insert(newClass);
                    classId = newClass.getId();
                    classesCreated++;
                    logger.info("[classSync] 创建班级: name={}, kcbh={}", className, kcbh);
                }
            }

            // 2.3 按课程班号(kcbh)实时从 CDSP 拉选课学生（$filter=课程班号 eq kcbh）
            //     CDSP 选课接口不支持原生参数过滤，但支持 OData $filter，
            //     课程班号是 numeric，值不加引号。拉不到再回退查本地库。
            Set<String> studentIds = fetchStudentIdsByKcbh(kcbh);

            if (studentIds.isEmpty()) {
                // 回退：查本地 course_selection（用 class_id/课程班号关联）
                QueryWrapper<CourseSelection> selWrapper = new QueryWrapper<>();
                selWrapper.eq("class_id", kcbh);
                List<CourseSelection> selections = courseSelectionMapper.selectList(selWrapper);
                studentIds = selections.stream()
                        .map(CourseSelection::getStudentId)
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.toSet());
            }

            if (studentIds.isEmpty()) {
                warnings.add(String.format("班级[%s]未拉取到选课学生", kcbh));
                continue;
            }

            // 批量查询 users 表找平台用户
            QueryWrapper<User> userWrapper = new QueryWrapper<>();
            userWrapper.in("username", studentIds);
            userWrapper.eq("deleted", 0);
            List<User> matchedUsers = userMapper.selectList(userWrapper);

            Map<String, Long> usernameToUserId = matchedUsers.stream()
                    .collect(Collectors.toMap(User::getUsername, User::getId, (a, b) -> a));

            int notFoundCount = studentIds.size() - usernameToUserId.size();
            if (notFoundCount > 0) {
                warnings.add(String.format("班级[%s]有%d名学生未在平台注册，已跳过", kcbh, notFoundCount));
            }

            for (Long studentUserId : usernameToUserId.values()) {
                // 检查是否已在班级中
                Integer exists = studentClassMapper.checkStudentInClass(studentUserId, classId);
                if (exists != null && exists > 0) {
                    studentsSkipped++;
                    continue;
                }
                StudentClass sc = new StudentClass();
                sc.setStudentId(studentUserId);
                sc.setClassId(classId);
                sc.setJoinTime(LocalDateTime.now());
                studentClassMapper.insert(sc);
                studentsAdded++;
            }
        }

        result.put("success", true);
        result.put("message", "同步完成");
        result.put("classesCreated", classesCreated);
        result.put("classesSkipped", classesSkipped);
        result.put("studentsAdded", studentsAdded);
        result.put("studentsSkipped", studentsSkipped);
        result.put("totalTeachingClasses", teachingClasses.size());
        result.put("academicYear", currentAcademicYear);
        result.put("semester", currentSemester == 1 ? "秋" : "春");
        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }

        logger.info("[classSync] 完成: created={}, skipped={}, studentsAdded={}, studentsSkipped={}",
                classesCreated, classesSkipped, studentsAdded, studentsSkipped);
        return result;
    }

    /**
     * 按课程班号(kcbh)从 CDSP 选课接口实时拉取学生学号。
     * 使用 OData $filter=课程班号 eq {kcbh}（numeric，值不加引号）。
     * 拉取失败或为空时返回空集合，由调用方回退到本地库。
     */
    private Set<String> fetchStudentIdsByKcbh(String kcbh) {
        Set<String> studentIds = new LinkedHashSet<>();
        if (kcbh == null || kcbh.trim().isEmpty()) {
            return studentIds;
        }
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("$filter", "课程班号 eq " + kcbh.trim());
            List<Map<String, Object>> rows = schoolDataApiClient.fetchCourseSelections(params);
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    String xh = extractStudentId(row);
                    if (xh != null && !xh.isEmpty()) {
                        studentIds.add(xh);
                    }
                }
            }
            logger.info("[classSync] kcbh={} 从CDSP拉到{}个学生", kcbh, studentIds.size());
        } catch (Exception e) {
            logger.warn("[classSync] kcbh={} 从CDSP拉取选课失败，将回退本地库: {}", kcbh, e.getMessage());
        }
        return studentIds;
    }

    /**
     * 从选课记录 map 中提取学号，兼容中文 key「学号」、英文别名 studentId/xh/XH。
     */
    private String extractStudentId(Map<String, Object> row) {
        Object v = row.get("studentId");
        if (v == null) v = row.get("学号");
        if (v == null) v = row.get("XH");
        if (v == null) v = row.get("xh");
        return v == null ? null : String.valueOf(v).trim();
    }

    /**
     * 查找班级：onlyDeleted=false 查未删除（走正常逻辑），=true 查已删除（绕过 @TableLogic）
     */
    private Class findClassBySchoolId(String schoolClassId, boolean onlyDeleted) {
        if (onlyDeleted) {
            return classMapper.selectDeletedBySchoolClassId(schoolClassId);
        }
        QueryWrapper<Class> wrapper = new QueryWrapper<>();
        wrapper.eq("school_class_id", schoolClassId);
        wrapper.last("LIMIT 1");
        return classMapper.selectOne(wrapper);
    }

    private Long findOrCreateCourse(String courseCode, String courseName, Long teacherId) {
        if (courseCode != null && !courseCode.isEmpty()) {
            QueryWrapper<Course> wrapper = new QueryWrapper<>();
            wrapper.eq("school_course_code", courseCode);
            Course existing = courseMapper.selectOne(wrapper);
            if (existing != null) {
                return existing.getId();
            }
            // 检查是否有已软删除的同 course_code 课程，恢复它
            Course deleted = courseMapper.selectDeletedBySchoolCourseCode(courseCode);
            if (deleted != null) {
                String name = (courseName != null ? courseName : "未命名课程");
                courseMapper.restoreById(deleted.getId(), name);
                logger.info("[classSync] 恢复已删除课程: code={}", courseCode);
                return deleted.getId();
            }
        }

        Course course = new Course();
        course.setName(courseName != null ? courseName : "未命名课程");
        course.setSchoolCourseCode(courseCode);
        course.setCreatedBy(teacherId);
        course.setStatus("ACTIVE");
        courseMapper.insert(course);
        logger.info("[classSync] 创建课程: name={}, code={}", courseName, courseCode);
        return course.getId();
    }

    private String buildClassName(String courseName, String className, String kcbh) {
        if (className != null && !className.isEmpty()) {
            return courseName + " - " + className;
        }
        return courseName + " - " + kcbh;
    }

    private String buildDescription(TeachingClass tc) {
        StringBuilder sb = new StringBuilder();
        sb.append("学校数据自动同步创建");
        if (tc.getAcademicYear() != null) {
            sb.append(" | 学年: ").append(tc.getAcademicYear());
        }
        if (tc.getSemester() != null) {
            sb.append(" | 学期: ").append(tc.getSemester() == 1 ? "秋" : "春");
        }
        if (tc.getTeacherName() != null) {
            sb.append(" | 教师: ").append(tc.getTeacherName());
        }
        return sb.toString();
    }
}
