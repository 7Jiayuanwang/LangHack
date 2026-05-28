package com.english.dto.learner;

import lombok.Data;
import java.util.List;

@Data
public class LearnerProfileDto {
    private String profileStatus;
    private LearnerIdentityDto identity;
    private LearnerAbilityDto ability;
    private LearnerHabitDto habit;
    private LearnerPreferenceDto preference;
    private LearnerRiskDto risk;
    private List<LearnerTagDto> tags;
    private LearnerEvidenceDto evidence;
}
