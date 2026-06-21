package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.english.enums.TaskType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动任务实体
 */
@Data
@TableName("campaign_tasks")
public class CampaignTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campaignId;

    private String taskName;

    private TaskType taskType;

    private String taskUnit;

    private Integer targetValue;

    private Integer points;

    private Boolean isRequired;

    private Boolean autoCollect;

    private Integer sortOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;

    // 非数据库字段
    @TableField(exist = false)
    private Integer completionCount; // 完成人数
}
