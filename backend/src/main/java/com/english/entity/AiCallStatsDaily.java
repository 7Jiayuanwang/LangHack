package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("ai_call_stats_daily")
public class AiCallStatsDaily {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("service_name")
    private String serviceName;

    @TableField("total_calls")
    private Integer totalCalls;

    @TableField("success_calls")
    private Integer successCalls;

    @TableField("failed_calls")
    private Integer failedCalls;

    @TableField("avg_response_time_ms")
    private Integer avgResponseTimeMs;

    @TableField("max_response_time_ms")
    private Integer maxResponseTimeMs;

    @TableField("total_input_tokens")
    private Long totalInputTokens;

    @TableField("total_output_tokens")
    private Long totalOutputTokens;

    @TableField("created_time")
    private LocalDateTime createdTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public Integer getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Integer totalCalls) { this.totalCalls = totalCalls; }

    public Integer getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(Integer successCalls) { this.successCalls = successCalls; }

    public Integer getFailedCalls() { return failedCalls; }
    public void setFailedCalls(Integer failedCalls) { this.failedCalls = failedCalls; }

    public Integer getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(Integer avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }

    public Integer getMaxResponseTimeMs() { return maxResponseTimeMs; }
    public void setMaxResponseTimeMs(Integer maxResponseTimeMs) { this.maxResponseTimeMs = maxResponseTimeMs; }

    public Long getTotalInputTokens() { return totalInputTokens; }
    public void setTotalInputTokens(Long totalInputTokens) { this.totalInputTokens = totalInputTokens; }

    public Long getTotalOutputTokens() { return totalOutputTokens; }
    public void setTotalOutputTokens(Long totalOutputTokens) { this.totalOutputTokens = totalOutputTokens; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
