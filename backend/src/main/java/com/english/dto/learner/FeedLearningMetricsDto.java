package com.english.dto.learner;

import lombok.Data;

@Data
public class FeedLearningMetricsDto {
    private int days;
    private long exposedPairs;
    private long clickedPairs;
    private long completedPairs;
    private double clickRate;
    private double completionPerClickRate;
}
