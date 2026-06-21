package com.english.enums;

/**
 * 活动状态枚举
 */
public enum CampaignStatus {
    DRAFT("草稿"),
    ACTIVE("进行中"),
    ENDED("已结束"),
    ARCHIVED("已归档");

    private final String description;

    CampaignStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
