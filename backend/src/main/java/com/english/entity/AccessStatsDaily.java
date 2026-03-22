package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("access_stats_daily")
public class AccessStatsDaily {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("total_requests")
    private Integer totalRequests;

    @TableField("unique_users")
    private Integer uniqueUsers;

    @TableField("peak_concurrent")
    private Integer peakConcurrent;

    @TableField("avg_response_time_ms")
    private Integer avgResponseTimeMs;

    @TableField("max_response_time_ms")
    private Integer maxResponseTimeMs;

    @TableField("error_count")
    private Integer errorCount;

    @TableField("top_pages")
    private String topPages;

    @TableField("created_time")
    private LocalDateTime createdTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public Integer getTotalRequests() { return totalRequests; }
    public void setTotalRequests(Integer totalRequests) { this.totalRequests = totalRequests; }

    public Integer getUniqueUsers() { return uniqueUsers; }
    public void setUniqueUsers(Integer uniqueUsers) { this.uniqueUsers = uniqueUsers; }

    public Integer getPeakConcurrent() { return peakConcurrent; }
    public void setPeakConcurrent(Integer peakConcurrent) { this.peakConcurrent = peakConcurrent; }

    public Integer getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(Integer avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }

    public Integer getMaxResponseTimeMs() { return maxResponseTimeMs; }
    public void setMaxResponseTimeMs(Integer maxResponseTimeMs) { this.maxResponseTimeMs = maxResponseTimeMs; }

    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

    public String getTopPages() { return topPages; }
    public void setTopPages(String topPages) { this.topPages = topPages; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
