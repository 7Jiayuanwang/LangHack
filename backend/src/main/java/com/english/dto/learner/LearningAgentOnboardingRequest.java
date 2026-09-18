package com.english.dto.learner;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class LearningAgentOnboardingRequest {
    private List<GoalInput> goals;
    private List<String> interests;
    private String currentLevel;
    private Integer weeklyMinutes;
    private List<String> preferredFormats;
    private String encouragementStyle;
    private Boolean memoryConsent;

    @Data
    public static class GoalInput {
        private String goalType;
        private String title;
        private Integer priority;
        private LocalDate targetDate;
    }
}
