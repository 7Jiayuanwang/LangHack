package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@TableName("backup_records")
public class BackupRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("filename")
    private String filename;

    @TableField("file_size")
    private Long fileSize;

    @TableField("backup_type")
    private String backupType;

    @TableField("backup_mode")
    private String backupMode;

    @TableField("base_backup_id")
    private Long baseBackupId;

    @TableField("status")
    private String status;

    @TableField("storage_path")
    @JsonIgnore
    private String storagePath;

    @TableField("created_by")
    private String createdBy;

    @TableField("error_message")
    private String errorMessage;

    @TableField("database_size")
    private Long databaseSize;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("completed_time")
    private LocalDateTime completedTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getBackupType() { return backupType; }
    public void setBackupType(String backupType) { this.backupType = backupType; }

    public String getBackupMode() { return backupMode; }
    public void setBackupMode(String backupMode) { this.backupMode = backupMode; }

    public Long getBaseBackupId() { return baseBackupId; }
    public void setBaseBackupId(Long baseBackupId) { this.baseBackupId = baseBackupId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getDatabaseSize() { return databaseSize; }
    public void setDatabaseSize(Long databaseSize) { this.databaseSize = databaseSize; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }
}
