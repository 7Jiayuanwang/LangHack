package com.english.enums;

public enum ProfileStatus {
    READY("ready", "数据充足"),
    PARTIAL("partial", "部分数据"),
    EMPTY("empty", "无数据");

    private final String code;
    private final String displayName;

    ProfileStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }
}
