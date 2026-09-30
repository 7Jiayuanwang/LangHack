package com.english.dto.learner;

import lombok.Data;

@Data
public class ArticleFeedEventRequest {
    private Long textId;
    private String eventType;
    private String source;
    private Integer position;
}
