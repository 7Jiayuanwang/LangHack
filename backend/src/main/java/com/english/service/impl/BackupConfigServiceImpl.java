package com.english.service.impl;

import com.english.entity.BackupConfig;
import com.english.exception.BusinessException;
import com.english.mapper.BackupConfigMapper;
import com.english.service.BackupConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BackupConfigServiceImpl implements BackupConfigService {

    private static final Logger logger = LoggerFactory.getLogger(BackupConfigServiceImpl.class);

    private final BackupConfigMapper backupConfigMapper;

    @Value("${backup.enabled:true}")
    private boolean defaultEnabled;

    @Value("${backup.retention-days:30}")
    private int defaultRetentionDays;

    @Value("${backup.max-count:50}")
    private int defaultMaxCount;

    @Value("${backup.incremental.enabled:false}")
    private boolean defaultIncrementalEnabled;

    @Value("${backup.incremental.full-backup-interval-days:7}")
    private int defaultFullBackupIntervalDays;

    public BackupConfigServiceImpl(BackupConfigMapper backupConfigMapper) {
        this.backupConfigMapper = backupConfigMapper;
    }

    @Override
    public BackupConfig getConfig() {
        try {
            BackupConfig config = backupConfigMapper.selectById(1);
            if (config != null) {
                return config;
            }
        } catch (Exception e) {
            logger.warn("Failed to read backup config from DB, using defaults", e);
        }

        // Return defaults from application.yml
        BackupConfig defaults = new BackupConfig();
        defaults.setId(1);
        defaults.setAutoBackupEnabled(defaultEnabled);
        defaults.setBackupCron("0 0 2 * * ?");
        defaults.setBackupHour(2);
        defaults.setBackupMinute(0);
        defaults.setRetentionDays(defaultRetentionDays);
        defaults.setMaxCount(defaultMaxCount);
        defaults.setIncrementalEnabled(defaultIncrementalEnabled);
        defaults.setFullBackupIntervalDays(defaultFullBackupIntervalDays);
        return defaults;
    }

    @Override
    public BackupConfig updateConfig(BackupConfig config, String updatedBy) {
        // Validate fields
        if (config.getBackupHour() != null && (config.getBackupHour() < 0 || config.getBackupHour() > 23)) {
            throw new BusinessException("备份小时必须在 0-23 之间");
        }
        if (config.getBackupMinute() != null && (config.getBackupMinute() < 0 || config.getBackupMinute() > 59)) {
            throw new BusinessException("备份分钟必须在 0-59 之间");
        }
        if (config.getRetentionDays() != null && (config.getRetentionDays() < 1 || config.getRetentionDays() > 365)) {
            throw new BusinessException("保留天数必须在 1-365 之间");
        }
        if (config.getMaxCount() != null && (config.getMaxCount() < 1 || config.getMaxCount() > 200)) {
            throw new BusinessException("最大备份数必须在 1-200 之间");
        }
        if (config.getFullBackupIntervalDays() != null
                && (config.getFullBackupIntervalDays() < 1 || config.getFullBackupIntervalDays() > 30)) {
            throw new BusinessException("全量备份间隔必须在 1-30 天之间");
        }

        // Auto-generate cron expression from hour + minute
        Integer hour = config.getBackupHour();
        Integer minute = config.getBackupMinute();
        if (hour != null && minute != null) {
            config.setBackupCron("0 " + minute + " " + hour + " * * ?");
        }

        config.setId(1);
        config.setUpdatedBy(updatedBy);
        config.setUpdatedTime(LocalDateTime.now());

        // Use insertOrUpdate pattern
        BackupConfig existing = backupConfigMapper.selectById(1);
        if (existing != null) {
            backupConfigMapper.updateById(config);
        } else {
            backupConfigMapper.insert(config);
        }

        logger.info("Backup config updated by {}: cron={}, retention={}d, max={}, incremental={}",
                updatedBy, config.getBackupCron(), config.getRetentionDays(),
                config.getMaxCount(), config.getIncrementalEnabled());

        return backupConfigMapper.selectById(1);
    }
}
