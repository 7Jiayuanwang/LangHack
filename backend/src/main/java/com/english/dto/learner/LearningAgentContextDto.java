package com.english.dto.learner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class LearningAgentContextDto {
    private boolean onboardingCompleted;
    private String currentLevel;
    private Integer weeklyMinutes;
    private List<String> preferredFormats = new ArrayList<>();
    private String encouragementStyle;
    private boolean memoryConsent;
    private List<GoalView> goals = new ArrayList<>();
    private List<String> interests = new ArrayList<>();
    private List<MemoryView> memories = new ArrayList<>();
    @JsonIgnore
    private LearnerProfileDto learnerProfile;
    private ScheduleSummary schedule = new ScheduleSummary();
    private String greeting;
    private List<String> suggestedQuestions = new ArrayList<>();

    @Data
    public static class GoalView {
        private Long id;
        private String goalType;
        private String title;
        private Integer priority;
        private LocalDate targetDate;
        private String status;
    }

    @Data
    public static class MemoryView {
        private Long id;
        private String memoryType;
        private String content;
        private String source;
        private boolean sensitive;
        private LocalDateTime updatedTime;
    }

    @Data
    public static class ScheduleSummary {
        private int totalCourses;
        private int todayCourseCount;
        private List<String> todayCourseNames = new ArrayList<>();
    }
}
