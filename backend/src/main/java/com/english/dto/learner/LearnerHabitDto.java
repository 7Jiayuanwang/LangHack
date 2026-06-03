package com.english.dto.learner;

import lombok.Data;

@Data
public class LearnerHabitDto {
    private int activeDays7d;
    private int activeDays14d;
    private int consecutiveDays;
    private long studyMinutes14d;
    private int todayStudyMinutes;
    private int overdueReviewCount;
    private int dueReviewCount;
    private double overdueReviewRatio;
}
