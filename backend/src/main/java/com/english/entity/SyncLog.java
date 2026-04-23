package com.english.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步日志实体
 */
@Data
@TableName("sync_logs")
public class SyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sync_type")
    private String syncType;

    @TableField("data_source")
    private String dataSource;

    @TableField("sync_method")
    private String syncMethod;

    @TableField("status")
    private String status;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("skip_count")
    private Integer skipCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("operator")
    private String operator;

    @TableField("pagination_complete")
    private Boolean paginationComplete;

    @TableField("pagination_stop_reason")
    private String paginationStopReason;

    @TableField("pagination_pages_fetched")
    private Integer paginationPagesFetched;

    @TableField("pagination_last_skip")
    private Integer paginationLastSkip;

    @TableField("pagination_page_size")
    private Integer paginationPageSize;

    @TableField("pagination_guard_hit")
    private Boolean paginationGuardHit;

    @TableField("fetched_row_count")
    private Integer fetchedRowCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
