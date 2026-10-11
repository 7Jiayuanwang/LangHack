package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.datasource.UseMaster;
import com.english.dto.EnglishExemptionApplicationRequest;
import com.english.entity.Class;
import com.english.entity.EnglishExemptionApplication;
import com.english.entity.Notification;
import com.english.entity.SchoolStudent;
import com.english.entity.TeacherStudentBinding;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.EnglishExemptionApplicationMapper;
import com.english.mapper.NotificationMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.TeacherStudentBindingMapper;
import com.english.mapper.UserMapper;
import com.english.service.ClassService;
import com.english.service.EnglishExemptionApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EnglishExemptionApplicationServiceImpl implements EnglishExemptionApplicationService {

    private final EnglishExemptionApplicationMapper applicationMapper;
    private final NotificationMapper notificationMapper;
    private final TeacherStudentBindingMapper teacherStudentBindingMapper;
    private final SchoolStudentMapper schoolStudentMapper;
    private final UserMapper userMapper;
    private final ClassService classService;

    public EnglishExemptionApplicationServiceImpl(
            EnglishExemptionApplicationMapper applicationMapper,
            NotificationMapper notificationMapper,
            TeacherStudentBindingMapper teacherStudentBindingMapper,
            SchoolStudentMapper schoolStudentMapper,
            UserMapper userMapper,
            ClassService classService) {
        this.applicationMapper = applicationMapper;
        this.notificationMapper = notificationMapper;
        this.teacherStudentBindingMapper = teacherStudentBindingMapper;
        this.schoolStudentMapper = schoolStudentMapper;
        this.userMapper = userMapper;
        this.classService = classService;
    }

    @Override
    @UseMaster
    public EnglishExemptionApplication getLatestForStudent(Long studentId) {
        return applicationMapper.selectOne(new LambdaQueryWrapper<EnglishExemptionApplication>()
                .eq(EnglishExemptionApplication::getStudentId, studentId)
                .orderByDesc(EnglishExemptionApplication::getCreatedAt)
                .last("LIMIT 1"));
    }

    @Override
    @UseMaster
    @Transactional
    public EnglishExemptionApplication submit(User student, EnglishExemptionApplicationRequest request) {
        Long pendingCount = applicationMapper.selectCount(new LambdaQueryWrapper<EnglishExemptionApplication>()
                .eq(EnglishExemptionApplication::getStudentId, student.getId())
                .eq(EnglishExemptionApplication::getStatus, "PENDING"));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException("已有待审核的英语免修申请，请勿重复提交");
        }

        List<User> reviewers = findReviewers(student);
        if (reviewers.isEmpty()) {
            throw new BusinessException("未找到可审核该申请的任课教师，请联系学院教务老师");
        }

        LocalDateTime now = LocalDateTime.now();
        EnglishExemptionApplication application = new EnglishExemptionApplication();
        application.setStudentId(student.getId());
        application.setApplicationReason(request.getApplicationReason().trim());
        application.setStatus("PENDING");
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);

        for (User reviewer : reviewers) {
            Notification notification = new Notification();
            notification.setTitle("英语免修申请待审核");
            notification.setContent("收到一条英语免修申请（编号 #" + application.getId() + "），请在审核页面处理。");
            notification.setTargetRole("TEACHER");
            notification.setTargetUserId(reviewer.getId());
            notification.setCreatedBy(student.getId());
            notification.setIsActive(true);
            notification.setCreatedAt(now);
            notification.setUpdatedAt(now);
            notificationMapper.insert(notification);
        }

        return application;
    }

    private List<User> findReviewers(User student) {
        String schoolStudentId = resolveSchoolStudentId(student);
        Set<String> teacherWorkIds = teacherStudentBindingMapper.selectByStudent(schoolStudentId, null, null).stream()
                .map(TeacherStudentBinding::getTeacherId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!teacherWorkIds.isEmpty()) {
            List<User> boundTeachers = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getUsername, teacherWorkIds)
                    .eq(User::getRole, "TEACHER")
                    .eq(User::getStatus, "ACTIVE"));
            if (!boundTeachers.isEmpty()) {
                return boundTeachers;
            }
        }

        Set<Long> teacherIds = classService.getClassesByStudentId(student.getId()).stream()
                .map(Class::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (teacherIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, teacherIds)
                .eq(User::getRole, "TEACHER")
                .eq(User::getStatus, "ACTIVE"));
    }

    private String resolveSchoolStudentId(User student) {
        if (student.getSchoolStudentId() != null) {
            SchoolStudent schoolStudent = schoolStudentMapper.selectById(student.getSchoolStudentId());
            if (schoolStudent != null && schoolStudent.getStudentId() != null && !schoolStudent.getStudentId().isBlank()) {
                return schoolStudent.getStudentId();
            }
        }
        return student.getUsername();
    }
}
