package com.english.dto.learner;

import lombok.Data;

@Data
public class ArticleInsightMessageDto {
    private Long id;
    private Long textId;
    private String title;
    private String content;
}
