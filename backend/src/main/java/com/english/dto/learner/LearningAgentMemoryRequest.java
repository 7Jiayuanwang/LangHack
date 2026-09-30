package com.english.dto.learner;

import lombok.Data;

@Data
public class LearningAgentMemoryRequest {
    private String memoryType;
    private String content;
    private Boolean sensitive;
}
