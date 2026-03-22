package com.english.service;

import com.english.entity.BackupConfig;

public interface BackupConfigService {

    BackupConfig getConfig();

    BackupConfig updateConfig(BackupConfig config, String updatedBy);
}
