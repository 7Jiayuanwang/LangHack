package com.english.service;

import com.english.dto.EnglishExemptionApplicationRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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
    void submitPersistsApplicationAndNotifiesBoundTeacher() {
        User student = user(10L, "2023122066164", "STUDENT");
        User teacher = user(20L, "teacher-001", "TEACHER");
        TeacherStudentBinding binding = new TeacherStudentBinding();
        binding.setTeacherId("teacher-001");
        EnglishExemptionApplicationRequest request = new EnglishExemptionApplicationRequest();
        fillRequest(request);

        when(applicationMapper.selectCount(any())).thenReturn(0L);
        when(teacherStudentBindingMapper.selectByStudent(eq("2023122066164"), isNull(), isNull()))
                .thenReturn(List.of(binding));
        when(userMapper.selectList(any())).thenReturn(List.of(teacher));
        doAnswer(invocation -> {
            CourseExemptionApplication application = invocation.getArgument(0);
            application.setId(101L);
            return 1;
        }).when(applicationMapper).insert(any(CourseExemptionApplication.class));

        CourseExemptionApplication submitted = service.submit(student, request);

        assertEquals(101L, submitted.getId());
        assertEquals("PENDING", submitted.getStatus());
        ArgumentCaptor<CourseExemptionReviewer> reviewerCaptor = ArgumentCaptor.forClass(CourseExemptionReviewer.class);
        verify(reviewerMapper).insert(reviewerCaptor.capture());
        assertEquals(101L, reviewerCaptor.getValue().getApplicationId());
        assertEquals(teacher.getId(), reviewerCaptor.getValue().getTeacherId());
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertEquals(teacher.getId(), notification.getTargetUserId());
        assertEquals("TEACHER", notification.getTargetRole());
        assertEquals("英语免修申请待审核", notification.getTitle());
    }

    @Test
    void submitRejectsWhenNoTeacherCanReview() {
        User student = user(10L, "2023122066164", "STUDENT");
        EnglishExemptionApplicationRequest request = new EnglishExemptionApplicationRequest();
        fillRequest(request);

        when(applicationMapper.selectCount(any())).thenReturn(0L);
        when(teacherStudentBindingMapper.selectByStudent(eq("2023122066164"), isNull(), isNull()))
                .thenReturn(List.of());
        when(classService.getClassesByStudentId(student.getId())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.submit(student, request));
        verify(applicationMapper, never()).insert(any(CourseExemptionApplication.class));
        verify(reviewerMapper, never()).insert(any(CourseExemptionReviewer.class));
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }

    private void fillRequest(EnglishExemptionApplicationRequest request) {
        request.setCourseName("大学英语2");
        request.setApplyType(CourseExemptionApplication.APPLY_EXEMPT_FULL);
        request.setExamType(CourseExemptionApplication.EXAM_CET6);
        request.setExamScore("500");
        request.setCertificateUrl("/api/media/1/download");
        request.setStudentRemark("CET-6 成绩符合学院免修要求");
    }
}
