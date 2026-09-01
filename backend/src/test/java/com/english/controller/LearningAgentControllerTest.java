package com.english.controller;

import com.english.dto.learner.LearningAgentContextDto;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.LearningAgentService;
import com.english.service.InternetContentRecommendationService;
import com.english.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LearningAgentControllerTest {

    @Mock private LearningAgentService learningAgentService;
    @Mock private InternetContentRecommendationService contentRecommendationService;
    @Mock private UserService userService;

    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new LearningAgentController(
                        learningAgentService, contentRecommendationService, userService)).build();
        authentication = new UsernamePasswordAuthenticationToken("student01", null, Collections.emptyList());
        User user = new User();
        user.setId(7L);
        user.setUsername("student01");
        when(userService.getUserByUsername("student01")).thenReturn(user);
    }

    @Test
    void getContextReturnsCurrentStudentsContext() throws Exception {
        LearningAgentContextDto context = new LearningAgentContextDto();
        context.setOnboardingCompleted(true);
        context.setGreeting("欢迎回来");
        when(learningAgentService.getContext(7L)).thenReturn(context);

        mockMvc.perform(get("/api/student/agent/context").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.greeting").value("欢迎回来"));
    }

    @Test
    void onboardingValidationErrorIsSafeForClient() throws Exception {
        when(learningAgentService.saveOnboarding(eq(7L), any()))
                .thenThrow(new BusinessException("请至少选择一个学习目标"));

        mockMvc.perform(put("/api/student/agent/onboarding")
                        .principal(authentication)
                        .contentType("application/json")
                        .content("{\"goals\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请至少选择一个学习目标"));
    }

    @Test
    void deleteMemoryIsAlwaysScopedToCurrentUser() throws Exception {
        LearningAgentContextDto context = new LearningAgentContextDto();
        when(learningAgentService.deleteMemory(7L, 19L)).thenReturn(context);

        mockMvc.perform(delete("/api/student/agent/memories/19").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void recommendationsAreScopedAndCountIsClamped() throws Exception {
        when(contentRecommendationService.recommendForUser(7L, 12))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/student/agent/content-recommendations?count=99")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}
