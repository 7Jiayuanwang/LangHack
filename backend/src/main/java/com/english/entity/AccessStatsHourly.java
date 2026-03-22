package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("access_stats_hourly")
public class AccessStatsHourly {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("stat_hour")
    private LocalDateTime statHour;

    @TableField("total_requests")
    private Integer totalRequests;

    @TableField("unique_users")
    private Integer uniqueUsers;

    @TableField("avg_response_time_ms")
    private Integer avgResponseTimeMs;

    @TableField("max_response_time_ms")
    private Integer maxResponseTimeMs;

    @TableField("error_count")
    private Integer errorCount;

    @TableField("created_time")
    private LocalDateTime createdTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getStatHour() { return statHour; }
    public void setStatHour(LocalDateTime statHour) { this.statHour = statHour; }

    public Integer getTotalRequests() { return totalRequests; }
    public void setTotalRequests(Integer totalRequests) { this.totalRequests = totalRequests; }

    public Integer getUniqueUsers() { return uniqueUsers; }
    public void setUniqueUsers(Integer uniqueUsers) { this.uniqueUsers = uniqueUsers; }

    public Integer getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(Integer avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }

    public Integer getMaxResponseTimeMs() { return maxResponseTimeMs; }
    public void setMaxResponseTimeMs(Integer maxResponseTimeMs) { this.maxResponseTimeMs = maxResponseTimeMs; }

    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
