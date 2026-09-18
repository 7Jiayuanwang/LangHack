package com.english.service;

import com.english.dto.learner.LearningAgentContextDto;
import com.english.dto.learner.LearningAgentMemoryRequest;
import com.english.dto.learner.LearningAgentOnboardingRequest;
import com.english.entity.LearnerAgentProfile;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.LearnerAgentProfileMapper;
import com.english.mapper.LearnerGoalMapper;
import com.english.mapper.LearnerInterestMapper;
import com.english.mapper.LearnerMemoryMapper;
import com.english.service.impl.LearningAgentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningAgentServiceTest {

    @Mock private LearnerAgentProfileMapper profileMapper;
    @Mock private LearnerGoalMapper goalMapper;
    @Mock private LearnerInterestMapper interestMapper;
    @Mock private LearnerMemoryMapper memoryMapper;
    @Mock private LearnerProfileService learnerProfileService;
    @Mock private SchoolDataSyncService schoolDataSyncService;
    @Mock private UserService userService;

    private LearningAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LearningAgentServiceImpl(
                profileMapper, goalMapper, interestMapper, memoryMapper,
                learnerProfileService, schoolDataSyncService, userService, new ObjectMapper());
    }

    @Test
    void onboardingSupportsSeveralSimultaneousGoals() {
        LearnerAgentProfile persisted = new LearnerAgentProfile();
        persisted.setId(1L);
        persisted.setUserId(7L);
        persisted.setCurrentLevel("INTERMEDIATE");
        persisted.setWeeklyMinutes(240);
        persisted.setPreferredFormatsJson("[\"READING\",\"QUIZ\"]");
        persisted.setEncouragementStyle("GENTLE");
        persisted.setMemoryConsent(true);
        persisted.setOnboardingCompleted(true);
        when(profileMapper.selectOne(any())).thenReturn(null, persisted);
        when(goalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(interestMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(memoryMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(learnerProfileService.getUserProfile(7L)).thenReturn(null);
        User user = new User();
        user.setId(7L);
        user.setUsername("student01");
        when(userService.getUserById(7L)).thenReturn(user);
        when(schoolDataSyncService.getStudentSchedule("student01", null, null))
                .thenReturn(Collections.emptyList());

        LearningAgentOnboardingRequest request = validRequest();
        request.setGoals(List.of(goal("CET6", "通过六级", 5),
                goal("ACADEMIC_READING", "阅读专业论文", 4)));

        LearningAgentContextDto result = service.saveOnboarding(7L, request);

        assertTrue(result.isOnboardingCompleted());
        assertEquals(240, result.getWeeklyMinutes());
        verify(goalMapper, org.mockito.Mockito.times(2)).insert(any());
        verify(interestMapper, org.mockito.Mockito.times(2)).insert(any());
        verify(memoryMapper, org.mockito.Mockito.times(3)).insert(any());
    }

    @Test
    void onboardingRequiresAtLeastOneGoal() {
        LearningAgentOnboardingRequest request = validRequest();
        request.setGoals(Collections.emptyList());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveOnboarding(7L, request));

        assertEquals("请至少选择一个学习目标", error.getMessage());
        verify(profileMapper, never()).insert(any());
    }

    @Test
    void deletingAnotherUsersMemoryIsRejected() {
        when(memoryMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteMemory(7L, 99L));

        assertEquals("记忆不存在或无权删除", error.getMessage());
        verify(memoryMapper, never()).deleteById(any(Long.class));
    }

    private LearningAgentOnboardingRequest validRequest() {
        LearningAgentOnboardingRequest request = new LearningAgentOnboardingRequest();
        request.setGoals(List.of(goal("CET4", "通过四级", 4)));
        request.setInterests(List.of("科技", "音乐"));
        request.setCurrentLevel("INTERMEDIATE");
        request.setWeeklyMinutes(240);
        request.setPreferredFormats(List.of("READING", "QUIZ"));
        request.setEncouragementStyle("GENTLE");
        request.setMemoryConsent(true);
        return request;
    }

    private LearningAgentOnboardingRequest.GoalInput goal(String type, String title, int priority) {
        LearningAgentOnboardingRequest.GoalInput goal = new LearningAgentOnboardingRequest.GoalInput();
        goal.setGoalType(type);
        goal.setTitle(title);
        goal.setPriority(priority);
        return goal;
    }
}
