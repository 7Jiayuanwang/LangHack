package com.english.dto.learner;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ArticleLearningInsightDto {
    private String status;
    private String targetLevel;
    private String levelReason;
    private String message;
    private List<KnowledgePoint> points = new ArrayList<>();

    @Data
    public static class KnowledgePoint {
        private String type;
        private String expression;
        private String explanation;
        private String sourceSentence;
        private String practiceTip;
    }
}
