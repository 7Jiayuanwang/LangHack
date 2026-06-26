package com.english.enums;

public enum LearnerTagCode {

    INACTIVE_RISK("INACTIVE_RISK", "流失风险", "risk", 1,
            "14天内活跃天数<3，建议教师主动联系"),

    VOCAB_WEAK("VOCAB_WEAK", "词汇弱势", "ability", 2,
            "词汇弱势率≥60%，建议加强词汇练习"),

    REVIEW_OVERDUE("REVIEW_OVERDUE", "复习逾期", "habit", 3,
            "逾期复习率≥50%，建议提醒学生复习"),

    CONSISTENT_LEARNER("CONSISTENT_LEARNER", "学习稳定", "positive", 4,
            "7天内活跃天数≥5，学习习惯良好"),

    CONTENT_LEVEL_STUCK("CONTENT_LEVEL_STUCK", "版本停滞", "preference", 5,
            "30天内阅读版本未变且≥20次，建议鼓励挑战更高版本"),

    CONTENT_LEVEL_JUMP("CONTENT_LEVEL_JUMP", "版本跳跃", "risk", 6,
            "7天内从低版本跳到高版本（跨2级），可能面临挫折"),

    IMPROVING_TREND("IMPROVING_TREND", "进步趋势", "positive", 7,
            "近14天活跃度较前14天提升≥30%，学习势头良好"),

    DECLINING_TREND("DECLINING_TREND", "下滑趋势", "risk", 8,
            "近14天活跃度较前14天下降≥40%，建议教师关注"),

    REVIEW_CHAMPION("REVIEW_CHAMPION", "复习达人", "positive", 9,
            "连续3次快照逾期复习率<5%，复习习惯优秀");

    private final String code;
    private final String displayName;
    private final String tagType;
    private final int priority;
    private final String actionHint;

    LearnerTagCode(String code, String displayName, String tagType, int priority, String actionHint) {
        this.code = code;
        this.displayName = displayName;
        this.tagType = tagType;
        this.priority = priority;
        this.actionHint = actionHint;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTagType() {
        return tagType;
    }

    public int getPriority() {
        return priority;
    }

    public String getActionHint() {
        return actionHint;
    }
}
