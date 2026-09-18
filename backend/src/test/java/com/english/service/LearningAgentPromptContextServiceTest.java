package com.english.service;

import com.english.dto.learner.LearnerAbilityDto;
import com.english.dto.learner.LearnerEvidenceDto;
import com.english.dto.learner.LearnerHabitDto;
import com.english.dto.learner.LearnerIdentityDto;
import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.LearningAgentContextDto;
import com.english.service.impl.LearningAgentPromptContextServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningAgentPromptContextServiceTest {

    @Mock
    private LearningAgentService learningAgentService;

    private LearningAgentPromptContextServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LearningAgentPromptContextServiceImpl(learningAgentService);
    }

    @Test
    void includesLearningSignalsButExcludesIdentityAndSensitiveMemories() {
        LearningAgentContextDto context = context(true);
        when(learningAgentService.getContext(7L)).thenReturn(context);

        String promptContext = service.buildForUser(7L);

        assertTrue(promptContext.contains("当前英语水平：INTERMEDIATE"));
        assertTrue(promptContext.contains("学习目标：通过大学英语六级"));
        assertTrue(promptContext.contains("兴趣：人工智能、电影"));
        assertTrue(promptContext.contains("今天的课程：数据结构"));
        assertTrue(promptContext.contains("连续学习3天"));
        assertTrue(promptContext.contains("晚上适合精读"));
        assertFalse(promptContext.contains("家庭健康情况"));
        assertFalse(promptContext.contains("2025112104289"));
        assertFalse(promptContext.contains("测试学生"));
    }

    @Test
    void excludesAllLongTermMemoriesWithoutConsent() {
        LearningAgentContextDto context = context(false);
        when(learningAgentService.getContext(7L)).thenReturn(context);

        String promptContext = service.buildForUser(7L);

        assertFalse(promptContext.contains("晚上适合精读"));
        assertFalse(promptContext.contains("家庭健康情况"));
        assertTrue(promptContext.contains("兴趣：人工智能、电影"));
    }

    private LearningAgentContextDto context(boolean memoryConsent) {
        LearningAgentContextDto context = new LearningAgentContextDto();
        context.setOnboardingCompleted(true);
        context.setCurrentLevel("INTERMEDIATE");
        context.setWeeklyMinutes(240);
        context.setPreferredFormats(List.of("READING", "CONVERSATION"));
        context.setEncouragementStyle("GENTLE");
        context.setMemoryConsent(memoryConsent);
        context.setInterests(List.of("人工智能", "电影"));

        LearningAgentContextDto.GoalView goal = new LearningAgentContextDto.GoalView();
        goal.setGoalType("CET6");
        goal.setTitle("通过大学英语六级");
        goal.setPriority(5);
        context.setGoals(List.of(goal));

        LearningAgentContextDto.ScheduleSummary schedule = new LearningAgentContextDto.ScheduleSummary();
        schedule.setTotalCourses(6);
        schedule.setTodayCourseCount(1);
        schedule.setTodayCourseNames(List.of("数据结构"));
        context.setSchedule(schedule);

        LearningAgentContextDto.MemoryView normalMemory = new LearningAgentContextDto.MemoryView();
        normalMemory.setMemoryType("HABIT");
        normalMemory.setContent("晚上适合精读");
        normalMemory.setSensitive(false);
        LearningAgentContextDto.MemoryView sensitiveMemory = new LearningAgentContextDto.MemoryView();
        sensitiveMemory.setMemoryType("PROFILE");
        sensitiveMemory.setContent("家庭健康情况");
        sensitiveMemory.setSensitive(true);
        context.setMemories(List.of(normalMemory, sensitiveMemory));

        LearnerProfileDto profile = new LearnerProfileDto();
        LearnerIdentityDto identity = new LearnerIdentityDto();
        identity.setUsername("2025112104289");
        identity.setRealName("测试学生");
        profile.setIdentity(identity);
        LearnerEvidenceDto evidence = new LearnerEvidenceDto();
        evidence.setDataPointCount(30);
        profile.setEvidence(evidence);
        LearnerHabitDto habit = new LearnerHabitDto();
        habit.setActiveDays7d(4);
        habit.setConsecutiveDays(3);
        habit.setTodayStudyMinutes(20);
        profile.setHabit(habit);
        LearnerAbilityDto ability = new LearnerAbilityDto();
        ability.setMasteredWords(120);
        ability.setLearningWords(40);
        ability.setTextsRead(8);
        ability.setTextsCompleted(5);
        profile.setAbility(ability);
        context.setLearnerProfile(profile);
        return context;
    }
}
