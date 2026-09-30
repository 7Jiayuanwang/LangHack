package com.english.controller;

import com.english.dto.learner.ArticleLearningInsightDto;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.service.impl.ArticleInsightService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArticleInsightControllerTest {
    @Test
    void returnsOnlyTheAuthenticatedStudentsCompletedArticleInsight() throws Exception {
        ArticleInsightService service = mock(ArticleInsightService.class);
        UserService users = mock(UserService.class);
        User student = new User();
        student.setId(7L);
        when(users.getUserByUsername("student01")).thenReturn(student);
        ArticleLearningInsightDto dto = new ArticleLearningInsightDto();
        dto.setStatus("READY");
        when(service.getForCompletedArticle(7L, 8L)).thenReturn(dto);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArticleInsightController(service, users)).build();

        mvc.perform(get("/api/student/agent/article-insights/8")
                        .principal(new UsernamePasswordAuthenticationToken("student01", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));
        verify(service).getForCompletedArticle(7L, 8L);
    }

    @Test
    void rejectsArticleThatStudentHasNotCompleted() throws Exception {
        ArticleInsightService service = mock(ArticleInsightService.class);
        UserService users = mock(UserService.class);
        User student = new User();
        student.setId(7L);
        when(users.getUserByUsername("student01")).thenReturn(student);
        when(service.getForCompletedArticle(7L, 8L))
                .thenThrow(new BusinessException("请先完成这篇文章的阅读"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArticleInsightController(service, users)).build();

        mvc.perform(get("/api/student/agent/article-insights/8")
                        .principal(new UsernamePasswordAuthenticationToken("student01", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void doesNotAcceptAnonymousReadAcknowledgement() throws Exception {
        ArticleInsightService service = mock(ArticleInsightService.class);
        UserService users = mock(UserService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArticleInsightController(service, users)).build();

        mvc.perform(post("/api/student/agent/article-insights/messages/5/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        verify(service, never()).markRead(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
