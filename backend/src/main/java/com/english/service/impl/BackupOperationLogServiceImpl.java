package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.BackupOperationLog;
import com.english.mapper.BackupOperationLogMapper;
import com.english.service.BackupOperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BackupOperationLogServiceImpl
        extends ServiceImpl<BackupOperationLogMapper, BackupOperationLog>
        implements BackupOperationLogService {

    private static final Logger logger = LoggerFactory.getLogger(BackupOperationLogServiceImpl.class);

    @Override
    public void log(String operator, String operationType, Long targetId,
                    String targetName, String detail, String status, String ipAddress) {
        try {
            BackupOperationLog log = new BackupOperationLog();
            log.setOperator(operator != null ? operator : "UNKNOWN");
            log.setOperationType(operationType);
            log.setTargetId(targetId);
            log.setTargetName(targetName);
            // Truncate detail to 1000 chars to match column size
            if (detail != null && detail.length() > 1000) {
                detail = detail.substring(0, 997) + "...";
            }
            log.setDetail(detail);
            log.setStatus(status != null ? status : "SUCCESS");
            log.setIpAddress(ipAddress);
            log.setCreatedTime(LocalDateTime.now());
            save(log);
        } catch (Exception e) {
            // Fire-and-forget: logging failure must NEVER affect the main business operation
            logger.error("Failed to save backup operation log: operator={}, type={}, target={}",
                    operator, operationType, targetName, e);
        }
    }

    @Override
    public IPage<BackupOperationLog> getLogsByPage(int page, int size, String operationType) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        QueryWrapper<BackupOperationLog> wrapper = new QueryWrapper<>();
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq("operation_type", operationType);
        }
        wrapper.orderByDesc("created_time");
        return page(new Page<>(page, size), wrapper);
    }
}
