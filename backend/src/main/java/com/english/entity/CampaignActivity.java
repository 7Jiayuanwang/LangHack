package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.english.enums.CampaignStatus;
import com.english.enums.CompletionRule;
import com.english.enums.EnrollmentMode;
import com.english.enums.EnrollmentScope;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动实体
 */
@Data
@TableName("campaign_activities")
public class CampaignActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enrollmentStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enrollmentEndTime;

    private CampaignStatus status;

    private CompletionRule completionRule;

    private Integer completionThreshold;

    // 报名设置
    private EnrollmentMode enrollmentMode;

    private EnrollmentScope enrollmentScope;

    private String enrollmentTargetIds; // JSON数组字符串

    private Integer maxParticipants;

    // 隐私设置
    private Boolean showScores;

    private Boolean showRanking;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;

    // 非数据库字段
    @TableField(exist = false)
    private List<Object> enrollmentTargetIdList; // 解析后的ID/名称列表（元素可能是 String 或 Map）

    @TableField(exist = false)
    private Integer participantCount; // 参与人数

    @TableField(exist = false)
    private Integer taskCount; // 任务数量

    @TableField(exist = false)
    private Boolean isEnrolled; // 当前用户是否已报名

    // "我的活动"列表额外字段
    @TableField(exist = false)
    private String completionStatus; // 完成状态: NOT_STARTED, IN_PROGRESS, COMPLETED

    @TableField(exist = false)
    private Integer totalScore; // 当前总分

    @TableField(exist = false)
    private Integer completedTasks; // 已完成任务数

    @TableField(exist = false)
    private Integer totalTasks; // 总任务数

    @TableField(exist = false)
    private Long myRank; // 我的排名
}
