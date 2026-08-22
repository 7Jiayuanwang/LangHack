package com.english.service.impl;

import com.english.entity.SchoolTeacher;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.SchoolTeacherMapper;
import com.english.service.TeacherAccountProvisionService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 教师账号批量开通服务实现。
 * <p>
 * 依据 {@code school_teacher} 目录批量创建 SSO-only TEACHER 账号：
 * 用户名 = 教工号（与 SSO 登录解析出的 userId 对齐），密码为随机值（无法本地登录），
 * 已存在同名账号则跳过，不覆盖已有角色/密码。
 */
@Service
public class TeacherAccountProvisionServiceImpl implements TeacherAccountProvisionService {

    private static final Logger logger = LoggerFactory.getLogger(TeacherAccountProvisionServiceImpl.class);

    /** 与 CAS SSO 一致的 provider 标识，便于后续 SSO 登录识别为同一来源 */
    private static final String SSO_PROVIDER = "cas";

    private final SchoolTeacherMapper schoolTeacherMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public TeacherAccountProvisionServiceImpl(SchoolTeacherMapper schoolTeacherMapper,
                                              UserService userService,
                                              PasswordEncoder passwordEncoder) {
        this.schoolTeacherMapper = schoolTeacherMapper;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> provisionTeacherAccountsByCollege(String collegeName, String matchMode) {
        if (collegeName == null || collegeName.trim().isEmpty()) {
            throw new BusinessException("学院名称不能为空");
        }
        String normalizedCollege = collegeName.trim();
        boolean contains = "contains".equalsIgnoreCase(
                matchMode == null ? "eq" : matchMode.trim().toLowerCase(Locale.ROOT));

        List<SchoolTeacher> teachers = schoolTeacherMapper.selectByCollege(normalizedCollege, contains);
        if (teachers == null) {
            teachers = new ArrayList<>();
        }
        logger.info("[provisionTeacher] 开始: college={}, matchMode={}, directoryCount={}",
                normalizedCollege, contains ? "contains" : "eq", teachers.size());

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> createdList = new ArrayList<>();
        List<Map<String, Object>> failedList = new ArrayList<>();

        for (SchoolTeacher t : teachers) {
            String teacherId = t.getTeacherId() == null ? "" : t.getTeacherId().trim();
            if (teacherId.isEmpty()) {
                continue;
            }
            try {
                if (userService.getUserByUsername(teacherId) != null) {
                    skipped++;
                    continue;
                }
                User user = new User();
                user.setUsername(teacherId);
                // SSO-only：随机密码，无法用于本地登录，只能通过统一身份认证登录
                user.setPassword(UUID.randomUUID().toString());
                user.setRealName(t.getTeacherName());
                user.setNickname(t.getTeacherName());
                user.setRole("TEACHER");
                user.setStatus("ACTIVE");
                user.setSsoBound(1);
                user.setSsoProvider(SSO_PROVIDER);
                userService.createUser(user);
                created++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("teacherId", teacherId);
                row.put("teacherName", t.getTeacherName());
                createdList.add(row);
            } catch (Exception e) {
                failed++;
                logger.warn("[provisionTeacher] 建号失败 teacherId={} err={}", teacherId, e.getMessage());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("teacherId", teacherId);
                row.put("teacherName", t.getTeacherName());
                row.put("error", e.getMessage());
                failedList.add(row);
            }
        }

        logger.info("[provisionTeacher] 完成: college={}, directory={}, created={}, skipped={}, failed={}",
                normalizedCollege, teachers.size(), created, skipped, failed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collegeName", normalizedCollege);
        result.put("matchMode", contains ? "contains" : "eq");
        result.put("directoryCount", teachers.size());
        result.put("createdCount", created);
        result.put("skippedCount", skipped);
        result.put("failedCount", failed);
        result.put("role", "TEACHER");
        result.put("ssoOnly", true);
        result.put("createdTeachers", createdList);
        result.put("failedTeachers", failedList);
        result.put("provisionedAt", LocalDateTime.now());
        if (teachers.isEmpty()) {
            result.put("message", String.format(
                    "school_teacher 目录中未找到学院“%s”的教师，请先执行链式同步拉取教师目录", normalizedCollege));
        }
        return result;
    }
}
