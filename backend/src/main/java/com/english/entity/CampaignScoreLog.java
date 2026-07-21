package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("campaign_score_logs")
public class CampaignScoreLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campaignId;

    private Long userId;

    private Long taskId;

    private String taskName;

    private String action;

    private BigDecimal baseScore;

    private BigDecimal multiplier;

    private BigDecimal finalScore;

    private Integer streakDays;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}
