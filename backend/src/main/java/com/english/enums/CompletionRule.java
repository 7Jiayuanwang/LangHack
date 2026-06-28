package com.english.enums;

/**
 * 活动完成规则枚举
 */
public enum CompletionRule {
    ANY("完成任意任务"),
    ALL("完成全部任务"),
    THRESHOLD("完成指定数量任务");

    private final String description;

    CompletionRule(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
