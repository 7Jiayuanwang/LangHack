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
    private List<Long> enrollmentTargetIdList; // 解析后的ID列表

    @TableField(exist = false)
    private Integer participantCount; // 参与人数

    @TableField(exist = false)
    private Integer taskCount; // 任务数量
}
