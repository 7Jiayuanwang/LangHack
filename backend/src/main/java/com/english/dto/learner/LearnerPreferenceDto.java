package com.english.dto.learner;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class LearnerPreferenceDto {
    private String primaryContentVersion;
    private Map<String, Double> versionDistribution;
    private String targetExamLevel;
    private List<String> topCategories;
}
