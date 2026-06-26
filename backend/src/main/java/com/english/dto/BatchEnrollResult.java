package com.english.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量报名结果 DTO
 * 用于批量添加参与者时返回详细结果（成功/失败/范围外/人数已满）
 */
@Data
public class BatchEnrollResult {
    /** 成功添加的用户ID列表 */
    private List<Long> successUserIds = new ArrayList<>();

    /** 已报名（跳过）的用户ID列表 */
    private List<Long> alreadyEnrolledUserIds = new ArrayList<>();

    /** 不在报名范围内的用户ID列表 + 原因 */
    private List<UserReason> outOfScopeUserIds = new ArrayList<>();

    /** 因人数上限未能添加的用户ID列表 */
    private List<Long> capacityExceededUserIds = new ArrayList<>();

    /** 其他失败的用户ID列表 + 原因 */
    private List<UserReason> failedUserIds = new ArrayList<>();

    /** 活动人数上限（用于前端提示） */
    private Integer maxParticipants;

    /** 当前已报名人数（含本次成功） */
    private Integer currentCount;

    @Data
    public static class UserReason {
        private Long userId;
        private String reason;

        public UserReason() {}

        public UserReason(Long userId, String reason) {
            this.userId = userId;
            this.reason = reason;
        }
    }
}
