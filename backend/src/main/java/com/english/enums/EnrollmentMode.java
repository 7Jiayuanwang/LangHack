package com.english.enums;

/**
 * 报名模式枚举
 */
public enum EnrollmentMode {
    AUTO("自动报名"),
    MANUAL("学生自主报名"),
    INVITE_ONLY("仅邀请");

    private final String description;

    EnrollmentMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
