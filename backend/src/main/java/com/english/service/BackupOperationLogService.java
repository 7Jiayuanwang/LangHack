package com.english.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.BackupOperationLog;

public interface BackupOperationLogService extends IService<BackupOperationLog> {

    /**
     * Record a backup operation log entry (fire-and-forget, never throws).
     */
    void log(String operator, String operationType, Long targetId,
             String targetName, String detail, String status, String ipAddress);

    /**
     * Get paginated operation logs with optional type filter.
     */
    IPage<BackupOperationLog> getLogsByPage(int page, int size, String operationType);
}
