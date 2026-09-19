package com.english.controller;

import com.english.dto.teacher.TeacherClassLearningOverviewDto;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.TeacherClassLearningService;
import com.english.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherClassLearningControllerTest {
    @Test
    void returnsOnlyTheAuthenticatedTeachersClassOverview() throws Exception {
        TeacherClassLearningService service = mock(TeacherClassLearningService.class);
        UserService users = mock(UserService.class);
        User teacher = new User();
        teacher.setId(7L);
        teacher.setRole("TEACHER");
        when(users.getUserByUsername("teacher01")).thenReturn(teacher);
        TeacherClassLearningOverviewDto overview = new TeacherClassLearningOverviewDto();
        overview.setClassId(8L);
        overview.setTotalStudents(3);
        when(service.getOverview(7L, 8L)).thenReturn(overview);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TeacherClassLearningController(service, users)).build();

        mvc.perform(get("/api/teacher/classes/8/learning-overview")
                        .principal(new UsernamePasswordAuthenticationToken("teacher01", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(3));
        verify(service).getOverview(7L, 8L);
    }

    @Test
    void doesNotExposeAnotherTeachersData() throws Exception {
        TeacherClassLearningService service = mock(TeacherClassLearningService.class);
        UserService users = mock(UserService.class);
        User teacher = new User();
        teacher.setId(7L);
        teacher.setRole("TEACHER");
        when(users.getUserByUsername("teacher01")).thenReturn(teacher);
        when(service.getOverview(7L, 8L)).thenThrow(new BusinessException("无权查看此班级的学习数据"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TeacherClassLearningController(service, users)).build();

        mvc.perform(get("/api/teacher/classes/8/learning-overview")
                        .principal(new UsernamePasswordAuthenticationToken("teacher01", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsInvalidClassBeforeReadingUsers() throws Exception {
        TeacherClassLearningService service = mock(TeacherClassLearningService.class);
        UserService users = mock(UserService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TeacherClassLearningController(service, users)).build();

        mvc.perform(get("/api/teacher/classes/0/learning-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(service, never()).getOverview(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAUserWhoseTeacherRoleWasRevoked() throws Exception {
        TeacherClassLearningService service = mock(TeacherClassLearningService.class);
        UserService users = mock(UserService.class);
        User formerTeacher = new User();
        formerTeacher.setId(7L);
        formerTeacher.setRole("USER");
        when(users.getUserByUsername("formerTeacher")).thenReturn(formerTeacher);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TeacherClassLearningController(service, users)).build();

        mvc.perform(get("/api/teacher/classes/8/learning-overview")
                        .principal(new UsernamePasswordAuthenticationToken("formerTeacher", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        verify(service, never()).getOverview(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
