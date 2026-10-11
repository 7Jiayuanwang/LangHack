package com.english.service;

import com.english.dto.EnglishExemptionApplicationRequest;
import com.english.entity.Class;
import com.english.entity.CourseExemptionApplication;
import com.english.entity.CourseExemptionReviewer;
import com.english.entity.Notification;
import com.english.entity.TeacherStudentBinding;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.CourseExemptionApplicationMapper;
import com.english.mapper.CourseExemptionReviewerMapper;
import com.english.mapper.NotificationMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.TeacherStudentBindingMapper;
import com.english.mapper.UserMapper;
import com.english.service.impl.EnglishExemptionApplicationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnglishExemptionApplicationServiceImplTest {

    @Mock private CourseExemptionApplicationMapper applicationMapper;
    @Mock private CourseExemptionReviewerMapper reviewerMapper;
    @Mock private NotificationMapper notificationMapper;
    @Mock private TeacherStudentBindingMapper teacherStudentBindingMapper;
    @Mock private SchoolStudentMapper schoolStudentMapper;
    @Mock private UserMapper userMapper;
    @Mock private ClassService classService;

    @InjectMocks private EnglishExemptionApplicationServiceImpl service;

    @Test
    void submitWithOnlyTeacherStudentBindingNotifiesBoundTeacher() {
        User student = user(10L, "2023122066164", "STUDENT");
        User teacher = user(20L, "teacher-001", "TEACHER");
        TeacherStudentBinding binding = new TeacherStudentBinding();
        binding.setTeacherId(teacher.getUsername());

        prepareCommonSubmit(101L);
        when(teacherStudentBindingMapper.selectByStudent(eq(student.getUsername()), isNull(), isNull()))
                .thenReturn(List.of(binding));
        when(classService.getClassesByStudentId(student.getId())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of(teacher));

        CourseExemptionApplication submitted = service.submit(student, request());

        assertEquals(101L, submitted.getId());
        assertEquals(CourseExemptionApplication.STATUS_PENDING, submitted.getStatus());
        assertReviewersAndNotifications(Set.of(teacher.getId()));
    }

    @Test
    void submitWithOnlyPlatformClassTeacherNotifiesClassTeacher() {
        User student = user(10L, "2023122066164", "STUDENT");
        User classTeacher = user(30L, "teacher-030", "TEACHER");

        prepareCommonSubmit(102L);
        when(teacherStudentBindingMapper.selectByStudent(eq(student.getUsername()), isNull(), isNull()))
                .thenReturn(List.of());
        when(classService.getClassesByStudentId(student.getId()))
                .thenReturn(List.of(studentClass(533L, classTeacher.getId())));
        when(userMapper.selectList(any())).thenReturn(List.of(classTeacher));

        service.submit(student, request());

        assertReviewersAndNotifications(Set.of(classTeacher.getId()));
    }

    @Test
    void submitWithBindingAndPlatformTeachersUsesUnionAndDeduplicates() {
        User student = user(10L, "2023122066164", "STUDENT");
        User sharedTeacher = user(20L, "teacher-001", "TEACHER");
        User classOnlyTeacher = user(30L, "teacher-030", "TEACHER");
        TeacherStudentBinding binding = new TeacherStudentBinding();
        binding.setTeacherId(sharedTeacher.getUsername());

        prepareCommonSubmit(103L);
        when(teacherStudentBindingMapper.selectByStudent(eq(student.getUsername()), isNull(), isNull()))
                .thenReturn(List.of(binding));
        when(classService.getClassesByStudentId(student.getId())).thenReturn(List.of(
                studentClass(533L, sharedTeacher.getId()),
                studentClass(534L, classOnlyTeacher.getId())));
        when(userMapper.selectList(any()))
                .thenReturn(List.of(sharedTeacher))
                .thenReturn(List.of(sharedTeacher, classOnlyTeacher));

        service.submit(student, request());

        assertReviewersAndNotifications(Set.of(sharedTeacher.getId(), classOnlyTeacher.getId()));
    }

    @Test
    void submitRejectsWhenNoTeacherCanReview() {
        User student = user(10L, "2023122066164", "STUDENT");
        when(applicationMapper.selectCount(any())).thenReturn(0L);
        when(teacherStudentBindingMapper.selectByStudent(eq(student.getUsername()), isNull(), isNull()))
                .thenReturn(List.of());
        when(classService.getClassesByStudentId(student.getId())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(student, request()));

        assertTrue(exception.getMessage().contains("未找到可审核该申请的任课教师"));
        verify(applicationMapper, never()).insert(any(CourseExemptionApplication.class));
        verify(reviewerMapper, never()).insert(any(CourseExemptionReviewer.class));
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    private void prepareCommonSubmit(Long applicationId) {
        when(applicationMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            CourseExemptionApplication application = invocation.getArgument(0);
            application.setId(applicationId);
            return 1;
        }).when(applicationMapper).insert(any(CourseExemptionApplication.class));
    }

    private void assertReviewersAndNotifications(Set<Long> expectedTeacherIds) {
        ArgumentCaptor<CourseExemptionReviewer> reviewerCaptor =
                ArgumentCaptor.forClass(CourseExemptionReviewer.class);
        verify(reviewerMapper, times(expectedTeacherIds.size())).insert(reviewerCaptor.capture());
        Set<Long> actualReviewerIds = reviewerCaptor.getAllValues().stream()
                .map(CourseExemptionReviewer::getTeacherId)
                .collect(Collectors.toSet());
        assertEquals(expectedTeacherIds, actualReviewerIds);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, times(expectedTeacherIds.size())).insert(notificationCaptor.capture());
        Set<Long> notifiedTeacherIds = notificationCaptor.getAllValues().stream()
                .map(Notification::getTargetUserId)
                .collect(Collectors.toSet());
        assertEquals(expectedTeacherIds, notifiedTeacherIds);
        assertTrue(notificationCaptor.getAllValues().stream()
                .allMatch(notification -> "TEACHER".equals(notification.getTargetRole())));
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }

    private Class studentClass(Long id, Long teacherId) {
        Class studentClass = new Class();
        studentClass.setId(id);
        studentClass.setTeacherId(teacherId);
        return studentClass;
    }

    private EnglishExemptionApplicationRequest request() {
        EnglishExemptionApplicationRequest request = new EnglishExemptionApplicationRequest();
        request.setCourseName("大学英语2");
        request.setApplyType(CourseExemptionApplication.APPLY_EXEMPT_FULL);
        request.setExamType(CourseExemptionApplication.EXAM_CET6);
        request.setExamScore("500");
        request.setCertificateUrl("/api/media/1/download");
        request.setStudentRemark("CET-6 成绩符合学院免修要求");
        return request;
    }
}
