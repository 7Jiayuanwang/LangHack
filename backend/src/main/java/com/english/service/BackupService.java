package com.english.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.BackupRecord;

import java.nio.file.Path;
import java.util.Map;

public interface BackupService extends IService<BackupRecord> {

    BackupRecord createManualBackup(String createdBy, String mode);

    void executeScheduledBackup();

    IPage<BackupRecord> getBackupsByPage(int page, int size, String backupType, String status);

    BackupRecord getBackupById(Long id);

    void deleteBackup(Long id);

    Path getBackupFilePath(Long id);

    void restoreFromBackup(Long id, String confirmDatabaseName);

    Map<String, Object> getBackupStats();

    Map<String, Object> getBackupConfig();

    int cleanupOldBackups();
}
