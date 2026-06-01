package com.english.dto.learner;

import lombok.Data;

@Data
public class LearnerRiskDto {
    private String riskLevel;
    private int inactiveDays;
    private boolean churnRisk;
}
