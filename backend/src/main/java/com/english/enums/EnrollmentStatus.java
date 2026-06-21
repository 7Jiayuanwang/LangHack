package com.english.enums;

/**
 * 报名状态枚举
 */
public enum EnrollmentStatus {
    PENDING("待审核"),
    ENROLLED("已报名"),
    REJECTED("已驳回"),
    WITHDRAWN("已退出");

    private final String description;

    EnrollmentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
