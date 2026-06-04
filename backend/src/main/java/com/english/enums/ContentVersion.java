package com.english.enums;

public enum ContentVersion {

    ORIGINAL("original", "原文"),
    CET4("cet4", "四级版"),
    CET6("cet6", "六级版"),
    ACADEMIC("academic", "考研版"),
    CHINESE_PARALLEL("chinese_parallel", "中英对照");

    private final String code;
    private final String displayName;

    ContentVersion(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ContentVersion fromCode(String code) {
        if (code == null) return null;
        for (ContentVersion v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        return null;
    }
}
