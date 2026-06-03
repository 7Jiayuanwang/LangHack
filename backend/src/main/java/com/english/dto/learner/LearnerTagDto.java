package com.english.dto.learner;

import lombok.Data;

@Data
public class LearnerTagDto {
    private String code;
    private String displayName;
    private String tagType;
    private int priority;
    private String actionHint;

    public LearnerTagDto() {}

    public LearnerTagDto(String code, String displayName, String tagType, int priority, String actionHint) {
        this.code = code;
        this.displayName = displayName;
        this.tagType = tagType;
        this.priority = priority;
        this.actionHint = actionHint;
    }
}
