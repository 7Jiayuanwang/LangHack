package com.english.dto.learner;

import lombok.Data;

import java.util.List;

@Data
public class CohortSubgroupDto {

    private String subgroupName;

    private int studentCount;

    private double avgActiveDays7d;

    private double avgStudyMinutes14d;

    private double avgWeakRatio;

    private String suggestedAction;

    private List<Long> studentIds;
}
