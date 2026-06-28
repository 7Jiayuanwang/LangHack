package com.english.enums;

/**
 * 活动任务类型枚举
 */
public enum TaskType {
    READING_TIME("阅读时长", "MINUTES"),
    WORD_LEARNING("单词学习", "COUNT"),
    TEXT_UPLOAD("文章上传", "COUNT"),
    FEEDBACK("问题反馈", "COUNT"),
    AI_QA("AI问答", "TIMES"),
    MANUAL("手动录入", "COUNT");

    private final String description;
    private final String defaultUnit;

    TaskType(String description, String defaultUnit) {
        this.description = description;
        this.defaultUnit = defaultUnit;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }
}
