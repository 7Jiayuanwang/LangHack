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

    /**
     * 将代码字符串转换为枚举值
     * @param code 任务类型代码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果代码为null或未知
     */
    public static AssignmentType fromCode(String code) {
        // 输入验证 - 防止null引用导致的NullPointerException
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Assignment type code cannot be null or empty");
        }
        for (AssignmentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown assignment type code: " + code);
    }
}
