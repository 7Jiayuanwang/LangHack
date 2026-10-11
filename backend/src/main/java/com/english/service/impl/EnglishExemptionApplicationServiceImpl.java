package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.datasource.UseMaster;
import com.english.dto.EnglishExemptionApplicationRequest;
import com.english.entity.Class;
import com.english.entity.CourseExemptionApplication;
import com.english.entity.Notification;
import com.english.entity.SchoolStudent;
import com.english.entity.TeacherStudentBinding;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.CourseExemptionApplicationMapper;
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

    private final CourseExemptionApplicationMapper applicationMapper;
    private final NotificationMapper notificationMapper;
    private final TeacherStudentBindingMapper teacherStudentBindingMapper;
    private final SchoolStudentMapper schoolStudentMapper;
    private final UserMapper userMapper;
    private final ClassService classService;

    public EnglishExemptionApplicationServiceImpl(
            CourseExemptionApplicationMapper applicationMapper,
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
    public CourseExemptionApplication getLatestForStudent(Long studentId) {
        return applicationMapper.selectOne(new LambdaQueryWrapper<CourseExemptionApplication>()
                .eq(CourseExemptionApplication::getStudentId, studentId)
                .orderByDesc(CourseExemptionApplication::getCreatedTime)
                .last("LIMIT 1"));
    }

    @Override
    @UseMaster
    @Transactional
    public CourseExemptionApplication submit(User student, EnglishExemptionApplicationRequest request) {
        Long pendingCount = applicationMapper.selectCount(new LambdaQueryWrapper<CourseExemptionApplication>()
                .eq(CourseExemptionApplication::getStudentId, student.getId())
                .eq(CourseExemptionApplication::getStatus, CourseExemptionApplication.STATUS_PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException("已有待审核的英语免修申请，请勿重复提交");
        }

        List<User> reviewers = findReviewers(student);
        if (reviewers.isEmpty()) {
            throw new BusinessException("未找到可审核该申请的任课教师，请联系学院教务老师");
        }
        Integer convertedScore = CourseExemptionService.convertedScore(request.getExamType(), request.getExamScore());
        if (convertedScore == null) {
            throw new BusinessException("考试成绩未达到免修申请条件或成绩格式不正确");
        }
        if ((CourseExemptionApplication.EXAM_IELTS.equals(request.getExamType())
                || CourseExemptionApplication.EXAM_TOEFL.equals(request.getExamType()))) {
            if (request.getExamDate() == null) {
                throw new BusinessException("雅思或托福申请必须填写成绩获得日期");
            }
            if (request.getExamDate().plusYears(2).isBefore(java.time.LocalDate.now())) {
                throw new BusinessException("雅思或托福成绩已超出两年有效期");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        CourseExemptionApplication application = new CourseExemptionApplication();
        application.setStudentId(student.getId());
        application.setCourseName(request.getCourseName().trim());
        application.setApplyType(request.getApplyType());
        application.setExamType(request.getExamType());
        application.setExamScore(request.getExamScore().trim());
        application.setExamDate(request.getExamDate());
        application.setCertificateUrl(request.getCertificateUrl().trim());
        application.setStudentRemark(trimToNull(request.getStudentRemark()));
        application.setConvertedScore(convertedScore);
        application.setStatus(CourseExemptionApplication.STATUS_PENDING);
        application.setCreatedTime(now);
        application.setUpdatedTime(now);
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

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
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
