package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("backup_config")
public class BackupConfig {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    @TableField("auto_backup_enabled")
    private Boolean autoBackupEnabled;

    @TableField("backup_cron")
    private String backupCron;

    @TableField("backup_hour")
    private Integer backupHour;

    @TableField("backup_minute")
    private Integer backupMinute;

    @TableField("retention_days")
    private Integer retentionDays;

    @TableField("max_count")
    private Integer maxCount;

    @TableField("incremental_enabled")
    private Boolean incrementalEnabled;

    @TableField("full_backup_interval_days")
    private Integer fullBackupIntervalDays;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("updated_time")
    private LocalDateTime updatedTime;

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Boolean getAutoBackupEnabled() { return autoBackupEnabled; }
    public void setAutoBackupEnabled(Boolean autoBackupEnabled) { this.autoBackupEnabled = autoBackupEnabled; }

    public String getBackupCron() { return backupCron; }
    public void setBackupCron(String backupCron) { this.backupCron = backupCron; }

    public Integer getBackupHour() { return backupHour; }
    public void setBackupHour(Integer backupHour) { this.backupHour = backupHour; }

    public Integer getBackupMinute() { return backupMinute; }
    public void setBackupMinute(Integer backupMinute) { this.backupMinute = backupMinute; }

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public Integer getMaxCount() { return maxCount; }
    public void setMaxCount(Integer maxCount) { this.maxCount = maxCount; }

    public Boolean getIncrementalEnabled() { return incrementalEnabled; }
    public void setIncrementalEnabled(Boolean incrementalEnabled) { this.incrementalEnabled = incrementalEnabled; }

    public Integer getFullBackupIntervalDays() { return fullBackupIntervalDays; }
    public void setFullBackupIntervalDays(Integer fullBackupIntervalDays) { this.fullBackupIntervalDays = fullBackupIntervalDays; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
