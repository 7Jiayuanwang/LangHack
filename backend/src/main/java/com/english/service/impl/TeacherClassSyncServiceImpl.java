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

    /** “用户上传内容”固定课程 ID：学校数据同步的班级统一挂到此课程下，不再为每个学校课程创建空课程 */
    private static final Long USER_UPLOAD_COURSE_ID = 999L;

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

    @Autowired
    private com.english.mapper.SchoolStudentMapper schoolStudentMapper;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final String SSO_PROVIDER = "cas";

    @Override
    @Transactional
    public Map<String, Object> syncClassesForTeacher(Long userId) {
        return syncClassesForTeacher(userId, null, null);
    }

    @Override
    @Transactional
    public Map<String, Object> syncClassesForTeacher(Long userId, String academicYear, String semester) {
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

        // 学年学期：优先用入参指定值，否则按当前日期推算（8月及以后=秋季(1)，2月及以后=春季(2)）
        String currentAcademicYear;
        int currentSemester;
        Integer specifiedSemester = normalizeSemesterToInt(semester);
        if (academicYear != null && !academicYear.trim().isEmpty() && specifiedSemester != null) {
            currentAcademicYear = academicYear.trim();
            currentSemester = specifiedSemester;
        } else {
            java.time.LocalDate today = java.time.LocalDate.now();
            int year = today.getYear();
            int month = today.getMonthValue();
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
        }
        logger.info("[classSync] 目标学期: academicYear={}, semester={}", currentAcademicYear, currentSemester);

        // 仅当目标学期为 2026 秋季时，允许为未注册学生自动建号
        boolean allowCreateStudents = "2026".equals(currentAcademicYear) && currentSemester == 1;
        int studentsCreated = 0;

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

            // 2.1 先按课程班号(kcbh)实时从 CDSP 拉选课学生（$filter=课程班号 eq kcbh）
            //     CDSP 选课接口不支持原生参数过滤，但支持 OData $filter，
            //     课程班号是 numeric，值不加引号。拉不到再回退查本地库。
            //     ★ 拉不到任何选课学生的教学班不建班（避免生成空班）。
            Map<String, String> studentNames = new LinkedHashMap<>();
            Set<String> studentIds = fetchStudentIdsByKcbh(kcbh, studentNames);

            if (studentIds.isEmpty()) {
                // 回退：查本地 course_selection（用 class_id/课程班号关联）
                QueryWrapper<CourseSelection> selWrapper = new QueryWrapper<>();
                selWrapper.eq("class_id", kcbh);
                List<CourseSelection> selections = courseSelectionMapper.selectList(selWrapper);
                for (CourseSelection cs : selections) {
                    String sid = cs.getStudentId();
                    if (sid != null && !sid.isEmpty()) {
                        studentIds.add(sid);
                        String nm = firstNonBlankName(cs.getStudentName());
                        if (nm != null) {
                            studentNames.putIfAbsent(sid, nm);
                        }
                    }
                }
            }

            if (studentIds.isEmpty()) {
                // 无选课学生：不建班（若之前已存在同 kcbh 班级则保留，不动）
                warnings.add(String.format("班级[%s]未拉取到选课学生，跳过建班", kcbh));
                classesSkipped++;
                continue;
            }

            // 2.2 确认有学生后，查/建平台班级（含恢复已软删除的班级），统一挂到“用户上传内容”课程
            Long courseId = USER_UPLOAD_COURSE_ID;
            Long classId;
            Class existingClass = findClassBySchoolId(kcbh, false);

            if (existingClass != null) {
                classId = existingClass.getId();
                classesSkipped++;
            } else {
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

            // 2.3 批量查询 users 表找平台用户
            QueryWrapper<User> userWrapper = new QueryWrapper<>();
            userWrapper.in("username", studentIds);
            userWrapper.eq("deleted", 0);
            List<User> matchedUsers = userMapper.selectList(userWrapper);

            Map<String, Long> usernameToUserId = matchedUsers.stream()
                    .collect(Collectors.toMap(User::getUsername, User::getId, (a, b) -> a));

            // 未在平台注册的学生：仅 2026 秋季允许自动建号，其余学期沿用旧逻辑（跳过）
            Set<String> notFound = new LinkedHashSet<>(studentIds);
            notFound.removeAll(usernameToUserId.keySet());
            if (!notFound.isEmpty()) {
                if (allowCreateStudents) {
                    int created = 0;
                    for (String sid : notFound) {
                        Long newId = createStudentAccount(sid, studentNames.get(sid));
                        if (newId != null) {
                            usernameToUserId.put(sid, newId);
                            created++;
                        }
                    }
                    studentsCreated += created;
                    logger.info("[classSync] 班级[{}] 为{}名未注册学生自动建号(成功{})", kcbh, notFound.size(), created);
                    int stillMissing = notFound.size() - created;
                    if (stillMissing > 0) {
                        warnings.add(String.format("班级[%s]有%d名学生建号失败，已跳过", kcbh, stillMissing));
                    }
                } else {
                    warnings.add(String.format("班级[%s]有%d名学生未在平台注册，已跳过", kcbh, notFound.size()));
                }
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
        result.put("studentsCreated", studentsCreated);
        result.put("totalTeachingClasses", teachingClasses.size());
        result.put("academicYear", currentAcademicYear);
        result.put("semester", currentSemester == 1 ? "秋" : "春");
        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }

        logger.info("[classSync] 完成: created={}, skipped={}, studentsAdded={}, studentsCreated={}, studentsSkipped={}",
                classesCreated, classesSkipped, studentsAdded, studentsCreated, studentsSkipped);
        return result;
    }

    /**
     * 按课程班号(kcbh)从 CDSP 选课接口实时拉取学生学号。
     * 使用 OData $filter=课程班号 eq {kcbh}（numeric，值不加引号）。
     * 拉取失败或为空时返回空集合，由调用方回退到本地库。
     */
    private Set<String> fetchStudentIdsByKcbh(String kcbh, Map<String, String> studentNames) {
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
                        if (studentNames != null) {
                            String name = extractStudentName(row);
                            if (name != null && !name.isEmpty()) {
                                studentNames.putIfAbsent(xh, name);
                            }
                        }
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
     * 从选课记录 map 中提取学生姓名，兼容中文 key「姓名」、英文别名 studentName/xm/XM。
     */
    private String extractStudentName(Map<String, Object> row) {
        Object v = row.get("studentName");
        if (v == null) v = row.get("姓名");
        if (v == null) v = row.get("XM");
        if (v == null) v = row.get("xm");
        String name = v == null ? null : String.valueOf(v).trim();
        // 纯数字视为脏数据（CDSP 早期把学号填入了姓名字段）
        if (name != null && name.matches("^[0-9]+$")) {
            return null;
        }
        return name;
    }

    /**
     * 为未注册学生创建 SSO-only STUDENT 账号（随机密码不可本地登录，走 SSO 绑定）。
     * 姓名优先用 CDSP 选课记录，缺失时回退 school_student 表，再兜底用学号。
     * @return 新建用户的 id；已存在或创建失败返回 null。
     */
    private Long createStudentAccount(String studentId, String cdspName) {
        String realName = firstNonBlankName(cdspName);
        if (realName == null) {
            SchoolStudent ss = null;
            try {
                ss = schoolStudentMapper.selectByStudentId(studentId);
            } catch (Exception ignored) {
            }
            if (ss != null) {
                realName = firstNonBlankName(ss.getStudentName());
            }
        }
        if (realName == null) {
            realName = studentId;
        }
        try {
            User user = new User();
            user.setUsername(studentId);
            user.setPassword(UUID.randomUUID().toString());
            user.setRealName(realName);
            user.setNickname(realName);
            user.setRole("STUDENT");
            user.setStatus("ACTIVE");
            user.setSsoBound(1);
            user.setSsoProvider(SSO_PROVIDER);
            userService.createUser(user);
            return user.getId();
        } catch (Exception e) {
            logger.warn("[classSync] 建学生账号失败 studentId={} err={}", studentId, e.getMessage());
            return null;
        }
    }

    /** 姓名去空白 + 过滤纯数字脏数据；无效返回 null。 */
    private String firstNonBlankName(String name) {
        if (name == null) {
            return null;
        }
        String v = name.trim();
        if (v.isEmpty() || v.matches("^[0-9]+$")) {
            return null;
        }
        return v;
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

    /**
     * @deprecated 已停用：班级现统一绑定到“用户上传内容”课程（{@link #USER_UPLOAD_COURSE_ID}），
     * 不再为每个学校课程创建独立课程。保留此方法以备将来需要恢复按课程建课的逻辑。
     */
    @Deprecated
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

    /** 学期归一化为 Integer：秋/1→1，春/2→2，其余→null（表示未指定）。 */
    private Integer normalizeSemesterToInt(String semester) {
        if (semester == null) {
            return null;
        }
        String s = semester.trim();
        if (s.isEmpty()) {
            return null;
        }
        if ("1".equals(s) || "秋".equals(s)) {
            return 1;
        }
        if ("2".equals(s) || "春".equals(s)) {
            return 2;
        }
        try {
            int v = Integer.parseInt(s);
            return (v == 1 || v == 2) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
