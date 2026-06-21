package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务完成记录实体
 */
@Data
@TableName("campaign_task_completions")
public class CampaignTaskCompletion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campaignId;

    private Long taskId;

    private Long userId;

    private Integer completionCount;

    private BigDecimal currentValue;

    private Boolean isCompleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;

    // 非数据库字段
    @TableField(exist = false)
    private String taskName; // 任务名称

    @TableField(exist = false)
    private Integer targetValue; // 目标值

    @TableField(exist = false)
    private String taskUnit; // 任务单位
}
