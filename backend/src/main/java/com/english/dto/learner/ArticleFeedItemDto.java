package com.english.dto.learner;

import lombok.Data;

@Data
public class ArticleFeedItemDto {
    private Long id;
    private String title;
    private String excerpt;
    private String category;
    private String tags;
    private String difficulty;
    private String coverUrl;
    private int estimatedMinutes;
    private String reason;
    private String slot;
    private int position;
}
