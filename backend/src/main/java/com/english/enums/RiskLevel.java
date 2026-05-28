package com.english.enums;

public enum RiskLevel {
    LOW("low", "低风险"),
    MEDIUM("medium", "中风险"),
    HIGH("high", "高风险");

    private final String code;
    private final String displayName;

    RiskLevel(String code, String displayName) {
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
