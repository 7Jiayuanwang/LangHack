package com.english.config;

import com.english.entity.BackupConfig;
import com.english.service.BackupConfigService;
import com.english.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledFuture;

@Component
public class DynamicBackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DynamicBackupScheduler.class);

    private final TaskScheduler taskScheduler;
    private final BackupConfigService backupConfigService;
    private final BackupService backupService;
    private ScheduledFuture<?> scheduledTask;

    public DynamicBackupScheduler(TaskScheduler backupTaskScheduler,
                                   BackupConfigService backupConfigService,
                                   @Lazy BackupService backupService) {
        this.taskScheduler = backupTaskScheduler;
        this.backupConfigService = backupConfigService;
        this.backupService = backupService;
    }

    @PostConstruct
    public void init() {
        reschedule();
    }

    public synchronized void reschedule() {
        // Cancel existing task
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            scheduledTask = null;
            logger.info("Previous backup schedule cancelled");
        }

        try {
            BackupConfig config = backupConfigService.getConfig();

            if (!Boolean.TRUE.equals(config.getAutoBackupEnabled())) {
                logger.info("Auto backup is disabled, no task scheduled");
                return;
            }

            String cron = config.getBackupCron();
            if (cron == null || cron.isEmpty()) {
                cron = "0 0 2 * * ?";
            }

            scheduledTask = taskScheduler.schedule(
                    () -> {
                        try {
                            backupService.executeScheduledBackup();
                        } catch (Exception e) {
                            logger.error("Dynamic scheduled backup failed", e);
                        }
                    },
                    new CronTrigger(cron)
            );

            logger.info("Backup task scheduled with cron: {}", cron);
        } catch (Exception e) {
            logger.error("Failed to reschedule backup task", e);
        }
    }
}
