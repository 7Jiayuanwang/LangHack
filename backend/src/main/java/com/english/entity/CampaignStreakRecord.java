package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 活动连续学习记录实体
 */
@Data
@TableName("campaign_streak_records")
public class CampaignStreakRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campaignId;

    private Long userId;

    private LocalDate weekStart;

    private Integer streakDays;

    private BigDecimal currentMultiplier;

    private LocalDate lastActiveDate;

    private String activeDates;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
