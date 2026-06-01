package com.english.dto.learner;

import lombok.Data;

import java.util.List;

@Data
public class StudentFacingProfileDto {
    private LearnerIdentityDto identity;
    private LearnerAbilityDto ability;
    private int activeDays7d;
    private int consecutiveDays;
    private int studyMinutes14d;
    private String primaryContentVersion;
    private String targetExamLevel;
    private List<LearnerTagDto> positiveTags;
    private String naturalLanguageSummary;
}
