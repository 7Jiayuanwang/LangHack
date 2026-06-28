package com.english.enums;

/**
 * 报名范围枚举
 */
public enum EnrollmentScope {
    ALL("全部用户"),
    COLLEGE("指定学院"),
    GRADE("指定年级"),
    CLASS("指定班级"),
    CUSTOM("自定义用户列表");

    private final String description;

    EnrollmentScope(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
