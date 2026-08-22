package com.english.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PastPaper {
    private Long id;
    private String paperKey;
    private String category;
    private String title;
    private String examDate;
    private Integer durationMinutes;
    private String scope;
    private Integer questionCount;
    private String contentJson;
    private Boolean published;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

