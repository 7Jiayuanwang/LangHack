package com.english.dto.learner;

import lombok.Data;

@Data
public class LearnerAbilityDto {
    private long totalWords;
    private long newWords;
    private long learningWords;
    private long masteredWords;
    private double weakRatio;
    private long textsRead;
    private long textsCompleted;
}
