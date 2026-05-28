package com.english.dto.learner;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ClassCohortAnalysisDto {

    private Long classId;

    private String className;

    private int totalStudents;

    private Map<String, Integer> riskDistribution;

    private Map<String, Integer> tagHitDistribution;

    private double avgActiveDays7d;

    private double avgStudyMinutes14d;

    private double avgWeakRatio;

    private List<LearnerProfileClassSummaryDto> topRiskStudents;

    private List<CohortSubgroupDto> subgroups;

    private String llmCohortSummary;
}
