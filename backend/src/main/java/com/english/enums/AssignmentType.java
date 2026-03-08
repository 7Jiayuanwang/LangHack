package com.english.enums;

/**
 * 任务类型枚举
 */
public enum AssignmentType {
    UNIT("UNIT", "单元任务"),
    TEXTS("TEXTS", "课文任务"),
    WORDS("WORDS", "单词任务");

    private final String code;
    private final String description;

    AssignmentType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static AssignmentType fromCode(String code) {
        for (AssignmentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown assignment type code: " + code);
    }
}