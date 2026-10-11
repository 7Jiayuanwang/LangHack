package com.english.controller;

import com.english.dto.AssignmentContent;
import com.english.dto.Result;
import com.english.entity.Assignment;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.mapper.AssignmentStudentMapper;
import com.english.mapper.StudentClassMapper;
import com.english.service.AssignmentService;
import com.english.service.ClassService;
import com.english.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetedReadingAssignmentControllerTest {

    @Mock private AssignmentService assignmentService;
    @Mock private AssignmentStudentMapper assignmentStudentMapper;
    @Mock private StudentClassMapper studentClassMapper;
    @Mock private ClassService classService;
    @Mock private UserService userService;
    @InjectMocks private TargetedReadingAssignmentController controller;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        User teacher = new User();
        teacher.setId(7L);
        teacher.setUsername("teacher01");
        when(userService.getUserByUsername("teacher01")).thenReturn(teacher);
        authentication = new UsernamePasswordAuthenticationToken("teacher01", null, List.of());
    }

    @Test
    void createsOneAssignmentWithInnerAndOuterRequirements() {
        when(classService.validateTeacherPermission(7L, 8L)).thenReturn(true);
        StudentClass first = new StudentClass();
        first.setStudentId(11L);
        StudentClass second = new StudentClass();
        second.setStudentId(12L);
        when(studentClassMapper.selectStudentsByClassId(8L)).thenReturn(List.of(first, second));
        doAnswer(invocation -> {
            Assignment assignment = invocation.getArgument(0);
            assignment.setId(99L);
            return null;
        }).when(assignmentService).createAssignment(any(Assignment.class));

        Result<Map<String, Object>> result = controller.create(Map.of(
                "title", "本周阅读",
                "classId", 8L,
                "studentIds", List.of(11L, 12L),
                "innerRequiredCount", 2,
                "outerRequiredCount", 1
        ), authentication);

        assertEquals(200, result.getCode());
        assertEquals("BOTH", result.getData().get("articleType"));
        assertEquals(2, result.getData().get("innerRequiredCount"));
        assertEquals(1, result.getData().get("outerRequiredCount"));

        ArgumentCaptor<Assignment> assignmentCaptor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentService).createAssignment(assignmentCaptor.capture());
        AssignmentContent content = assignmentCaptor.getValue().getAssignmentContent();
        assertNotNull(content);
        assertEquals("READING_FLEX", content.getType());
        assertEquals(2, content.getContent().getInnerRequiredCount());
        assertEquals(1, content.getContent().getOuterRequiredCount());
        assertEquals("FREE", content.getContent().getInnerMode());
        assertEquals("FREE", content.getContent().getOuterMode());
        verify(assignmentStudentMapper).insertStudents(99L, List.of(11L, 12L));
    }

    @Test
    void rejectsAnAssignmentWithNoRequiredArticles() {
        Result<Map<String, Object>> result = controller.create(Map.of(
                "title", "空任务",
                "classId", 8L,
                "studentIds", List.of(11L),
                "innerRequiredCount", 0,
                "outerRequiredCount", 0
        ), authentication);

        assertEquals(500, result.getCode());
        assertEquals("课内和课外文章至少需要完成1篇", result.getMessage());
    }
}
