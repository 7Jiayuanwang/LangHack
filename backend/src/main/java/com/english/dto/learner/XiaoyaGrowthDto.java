package com.english.dto.learner;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class XiaoyaGrowthDto {
    private String stage;
    private String stageLabel;
    private int growthPoints;
    private Integer nextStageAt;
    private int meaningfulDays7d;
    private int meaningfulDaysAll;
    private long completedArticles;
    private long practicedArticles;
    private long externalDays;
    private int todayExternalCount;
    private String encouragement;
    private List<ExternalLearning> recentExternalLearning = new ArrayList<>();

    @Data
    public static class ExternalLearning {
        private Long id;
        private LocalDate learningDay;
        private String sourceType;
        private String sourceTitle;
        private String takeawayEn;
    }
}
