package com.english.service.impl;

import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.EnglishTeacherOnboardService;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import com.english.service.TeacherClassSyncService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 英语教师一键接入服务实现。见 {@link EnglishTeacherOnboardService}。
 */
@Service
public class EnglishTeacherOnboardServiceImpl implements EnglishTeacherOnboardService {

    private static final Logger logger = LoggerFactory.getLogger(EnglishTeacherOnboardServiceImpl.class);

    private static final String DEFAULT_KEYWORD = "英语";
    private static final String SSO_PROVIDER = "cas";
    private static final String SYNC_SOURCE = "TEACHER_COURSE_ONBOARD";

    private final SchoolDataApiClient apiClient;
    private final FieldMappingService fieldMappingService;
    private final TeacherClassSyncService teacherClassSyncService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final com.english.mapper.TeachingClassMapper teachingClassMapper;

    public EnglishTeacherOnboardServiceImpl(
            @Qualifier("schoolDataApiClientDelegate") SchoolDataApiClient apiClient,
            FieldMappingService fieldMappingService,
            TeacherClassSyncService teacherClassSyncService,
            UserService userService,
            PasswordEncoder passwordEncoder,
            com.english.mapper.TeachingClassMapper teachingClassMapper) {
        this.apiClient = apiClient;
        this.fieldMappingService = fieldMappingService;
        this.teacherClassSyncService = teacherClassSyncService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> onboardEnglishTeachers(String academicYear, String semester, String courseKeyword) {
        if (academicYear == null || academicYear.trim().isEmpty()) {
            throw new BusinessException("学年不能为空");
        }
        String year = academicYear.trim();
        String targetSemester = normalizeSemester(semester);
        if (targetSemester.isEmpty()) {
            throw new BusinessException("学期不能为空（秋/春）");
        }
        String keyword = (courseKeyword == null || courseKeyword.trim().isEmpty())
                ? DEFAULT_KEYWORD : courseKeyword.trim();
        LocalDateTime syncTime = LocalDateTime.now();
        logger.info("[englishOnboard] 开始: year={}, semester={}, keyword={}", year, targetSemester, keyword);

        List<String> warnings = new ArrayList<>();

        // Step 1: 按学年 $filter 拉 teacher_courses（CDSP 只认 $filter，学年 numeric 不加引号）
        List<Map<String, Object>> rawRows;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("$filter", "学年 eq " + year);
            rawRows = apiClient.fetchTeacherCourses(params);
        } catch (Exception e) {
            logger.error("[englishOnboard] 拉取 teacher_courses 失败 year={}", year, e);
            throw new BusinessException("拉取教师授课数据失败: " + e.getMessage());
        }
        if (rawRows == null) {
            rawRows = new ArrayList<>();
        }

        // Step 2: 客户端筛选（学期 + 课程名称含关键词），写 teaching_class，收集教师
        int upstreamCount = rawRows.size();
        int matchedClassCount = 0;
        int teachingClassPersisted = 0;
        int teachingClassErrors = 0;
        Set<String> matchedClassIds = new LinkedHashSet<>();
        // teacherId -> teacherName
        Map<String, String> teachers = new LinkedHashMap<>();

        for (Map<String, Object> raw : rawRows) {
            Map<String, Object> m = fieldMappingService.applyMapping("teacher_courses", raw);
            String rowSemester = normalizeSemester(stringValue(m.get("semester")));
            if (!targetSemester.equals(rowSemester)) {
                continue;
            }
            String courseName = stringValue(m.get("courseName"));
            if (courseName.isEmpty() || !courseName.contains(keyword)) {
                continue;
            }
            String classId = stringValue(m.get("classId"));
            if (classId.isEmpty()) {
                continue;
            }
            String teacherId = stringValue(m.get("teacherId"));
            String teacherName = stringValue(m.get("teacherName"));

            // 课序号(kxh)未配置字段映射，applyMapping 会跳过，需从原始 raw 直接取（CDSP 返回中文 key「课序号」）
            String courseSequence = firstRaw(raw, "课序号", "kxh", "KXH");

            // 写 teaching_class（供 syncClassesForTeacher 读取）
            if (matchedClassIds.add(classId)) {
                matchedClassCount++;
                Map<String, Object> tcData = new LinkedHashMap<>();
                tcData.put("classId", classId);
                tcData.put("className", stringValue(m.get("className")));
                tcData.put("courseSequence", courseSequence);
                tcData.put("courseCode", stringValue(m.get("courseCode")));
                tcData.put("courseName", courseName);
                tcData.put("teacherId", teacherId);
                tcData.put("teacherName", teacherName);
                tcData.put("academicYear", year);
                tcData.put("semester", targetSemester);
                tcData.put("syncTime", syncTime);
                tcData.put("syncSource", SYNC_SOURCE);
                try {
                    upsertTeachingClass(tcData);
                    teachingClassPersisted++;
                } catch (Exception e) {
                    teachingClassErrors++;
                    logger.warn("[englishOnboard] upsert teaching_class 失败 classId={} err={}", classId, e.getMessage());
                }
            }

            if (!teacherId.isEmpty()) {
                teachers.putIfAbsent(teacherId, teacherName);
            }
        }
        logger.info("[englishOnboard] 筛选: upstream={}, matchedClasses={}, teachers={}",
                upstreamCount, matchedClassCount, teachers.size());

        if (teachers.isEmpty()) {
            Map<String, Object> empty = baseResult(year, targetSemester, keyword, upstreamCount);
            empty.put("message", String.format(
                    "未匹配到 %s 学年 %s 学期、课程名称含「%s」的教师授课记录", year, targetSemester, keyword));
            empty.put("warnings", warnings);
            return empty;
        }

        // Step 3: 建教师账号（SSO-only TEACHER，已存在跳过）
        int teacherCreated = 0;
        int teacherExisted = 0;
        int teacherFailed = 0;
        // teacherId -> platform userId
        Map<String, Long> teacherUserIds = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : teachers.entrySet()) {
            String teacherId = e.getKey();
            String teacherName = e.getValue();
            try {
                User existing = userService.getUserByUsername(teacherId);
                if (existing != null) {
                    teacherExisted++;
                    teacherUserIds.put(teacherId, existing.getId());
                    continue;
                }
                User user = new User();
                user.setUsername(teacherId);
                user.setPassword(UUID.randomUUID().toString()); // SSO-only，随机密码不可本地登录
                user.setRealName(teacherName);
                user.setNickname(teacherName);
                user.setRole("TEACHER");
                user.setStatus("ACTIVE");
                user.setSsoBound(1);
                user.setSsoProvider(SSO_PROVIDER);
                userService.createUser(user);
                teacherCreated++;
                teacherUserIds.put(teacherId, user.getId());
            } catch (Exception ex) {
                teacherFailed++;
                logger.warn("[englishOnboard] 建教师账号失败 teacherId={} err={}", teacherId, ex.getMessage());
            }
        }
        logger.info("[englishOnboard] 建号: created={}, existed={}, failed={}",
                teacherCreated, teacherExisted, teacherFailed);

        // Step 4: 逐个教师触发班级+学生绑定（复用 syncClassesForTeacher，走 student_class，不碰 course_selection）
        int teacherSynced = 0;
        int teacherSyncFailed = 0;
        int totalClassesCreated = 0;
        int totalStudentsAdded = 0;
        for (Map.Entry<String, Long> e : teacherUserIds.entrySet()) {
            Long uid = e.getValue();
            try {
                Map<String, Object> r = teacherClassSyncService.syncClassesForTeacher(uid, year, targetSemester, keyword);
                teacherSynced++;
                totalClassesCreated += toInt(r.get("classesCreated"));
                totalStudentsAdded += toInt(r.get("studentsAdded"));
            } catch (Exception ex) {
                teacherSyncFailed++;
                logger.warn("[englishOnboard] 教师班级同步失败 teacherId={} uid={} err={}",
                        e.getKey(), uid, ex.getMessage());
            }
        }
        logger.info("[englishOnboard] 完成: synced={}, syncFailed={}, classesCreated={}, studentsAdded={}",
                teacherSynced, teacherSyncFailed, totalClassesCreated, totalStudentsAdded);

        Map<String, Object> result = baseResult(year, targetSemester, keyword, upstreamCount);
        result.put("matchedClassCount", matchedClassCount);
        result.put("teachingClassPersisted", teachingClassPersisted);
        result.put("teachingClassErrors", teachingClassErrors);
        result.put("teacherCount", teachers.size());
        result.put("teacherCreated", teacherCreated);
        result.put("teacherExisted", teacherExisted);
        result.put("teacherFailed", teacherFailed);
        result.put("teacherSynced", teacherSynced);
        result.put("teacherSyncFailed", teacherSyncFailed);
        result.put("totalClassesCreated", totalClassesCreated);
        result.put("totalStudentsAdded", totalStudentsAdded);
        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }
        return result;
    }

    private Map<String, Object> baseResult(String year, String semester, String keyword, int upstreamCount) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("academicYear", year);
        r.put("semester", semester);
        r.put("courseKeyword", keyword);
        r.put("upstreamRowCount", upstreamCount);
        r.put("role", "TEACHER");
        r.put("provisionedAt", LocalDateTime.now());
        return r;
    }

    private void upsertTeachingClass(Map<String, Object> data) {
        com.english.entity.TeachingClass entity = new com.english.entity.TeachingClass();
        entity.setClassId(stringValue(data.get("classId")));
        entity.setClassName(stringValue(data.get("className")));
        entity.setCourseSequence(stringValue(data.get("courseSequence")));
        entity.setCourseCode(stringValue(data.get("courseCode")));
        entity.setCourseName(stringValue(data.get("courseName")));
        entity.setTeacherId(stringValue(data.get("teacherId")));
        entity.setTeacherName(stringValue(data.get("teacherName")));
        entity.setAcademicYear(stringValue(data.get("academicYear")));
        entity.setSemester("秋".equals(data.get("semester")) ? 1 : 2);
        entity.setCapacity(100);
        entity.setClassStatus("ACTIVE");
        entity.setSyncTime((LocalDateTime) data.get("syncTime"));
        entity.setSyncSource(stringValue(data.get("syncSource")));
        teachingClassMapper.upsertByClassId(entity);
    }

    /** 学期归一化为 "秋"/"春"/""；兼容 1/2 数字。 */
    private String normalizeSemester(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return "";
        }
        if ("1".equals(v) || "秋".equals(value.trim())) {
            return "秋";
        }
        if ("2".equals(v) || "春".equals(value.trim())) {
            return "春";
        }
        return value.trim();
    }

    private String stringValue(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** 从原始 raw map 里按多个候选 key 取第一个非空值（用于未配置字段映射的字段，如课序号 kxh）。 */
    private String firstRaw(Map<String, Object> raw, String... keys) {
        if (raw == null) {
            return "";
        }
        for (String k : keys) {
            Object v = raw.get(k);
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return String.valueOf(v).trim();
            }
        }
        return "";
    }

    private int toInt(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
