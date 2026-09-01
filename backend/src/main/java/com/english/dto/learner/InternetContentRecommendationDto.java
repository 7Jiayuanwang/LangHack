package com.english.dto.learner;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InternetContentRecommendationDto {
    private String id;
    private String title;
    private String summary;
    private String url;
    private String sourceName;
    private String sourceType;
    private String topic;
    private String reason;
    private LocalDateTime publishedTime;
    private double score;
    private boolean external;
}
