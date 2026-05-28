package com.english.dto.learner;

import lombok.Data;
import java.util.List;

@Data
public class LearnerProfileClassSummaryDto {
    private Long userId;
    private String username;
    private String realName;
    private String riskLevel;
    private int activeDays7d;
    private long studyMinutes14d;
    private double weakRatio;
    private List<LearnerTagDto> tags;
}
