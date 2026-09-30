package com.english.dto.learner;

import lombok.Data;

import java.time.LocalDateTime;

/** Lightweight database projection: the feed never loads full article bodies. */
@Data
public class ArticleFeedCandidate {
    private Long id;
    private String title;
    private String excerpt;
    private String category;
    private String tags;
    private String difficulty;
    private String coverUrl;
    private Integer isFeatured;
    private Integer featuredPriority;
    private LocalDateTime createdAt;
    private Integer contentLength;
}
