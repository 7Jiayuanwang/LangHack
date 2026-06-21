package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手动数据录入实体
 */
@Data
@TableName("campaign_manual_records")
public class CampaignManualRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campaignId;

    private Long taskId;

    private Long userId;

    private BigDecimal recordValue;

    private String recordType;

    private String importBatchId;

    private String remark;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;

    // 非数据库字段
    @TableField(exist = false)
    private String username; // 用户名

    @TableField(exist = false)
    private String taskName; // 任务名称

    @TableField(exist = false)
    private String createdByName; // 录入人姓名
}
