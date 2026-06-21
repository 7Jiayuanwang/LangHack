package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.english.enums.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动参与记录实体
 */
@Data
@TableName("campaign_participations")
public class CampaignParticipation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campaignId;

    private Long userId;

    private EnrollmentStatus enrollmentStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enrollmentTime;

    private String enrollmentSource;

    private Integer totalScore;

    private Integer completedTasks;

    private String completionStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;

    // 非数据库字段
    @TableField(exist = false)
    private String username; // 用户名

    @TableField(exist = false)
    private String realName; // 真实姓名

    @TableField(exist = false)
    private String className; // 班级名称

    @TableField(exist = false)
    private String collegeName; // 学院名称

    @TableField(exist = false)
    private Integer ranking; // 排名
}
