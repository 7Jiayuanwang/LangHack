package com.english.config;

import com.english.entity.UpdateConfig;
import com.english.service.UpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class DynamicUpdateScheduler {

    private final TaskScheduler taskScheduler;
    private final UpdateService updateService;

    private ScheduledFuture<?> currentFuture;
    private ScheduledFuture<?> currentHotDeployFuture;

    public DynamicUpdateScheduler(@Qualifier("updateTaskScheduler") TaskScheduler taskScheduler,
                                  UpdateService updateService) {
        this.taskScheduler = taskScheduler;
        this.updateService = updateService;
    }

    @PostConstruct
    public void init() {
        try {
            reload();
        } catch (Exception e) {
            log.warn("[UpdateScheduler] Initialization skipped because update config is unavailable", e);
        }
    }

    public synchronized void reload() {
        try {
            reschedule(updateService.getConfig());
        } catch (Exception e) {
            log.warn("[UpdateScheduler] Reload skipped because update config could not be loaded", e);
        }
    }

    public synchronized void reschedule(UpdateConfig config) {
        // 普通更新调度
        if (currentFuture != null && !currentFuture.isCancelled()) {
            currentFuture.cancel(false);
            currentFuture = null;
            log.info("[UpdateScheduler] Previous self-update schedule cancelled");
        }

        // 热部署调度
        if (currentHotDeployFuture != null && !currentHotDeployFuture.isCancelled()) {
            currentHotDeployFuture.cancel(false);
            currentHotDeployFuture = null;
            log.info("[UpdateScheduler] Previous hot-deploy schedule cancelled");
        }

        if (!updateService.isSupported()) {
            log.info("[UpdateScheduler] Self-update disabled: {}", updateService.getUnsupportedReason());
            return;
        }

        if (config == null) {
            log.info("[UpdateScheduler] No update config available, skipping schedule registration");
            return;
        }

        if (Boolean.TRUE.equals(config.getEnabled()) && config.getCronExpr() != null) {
            try {
                currentFuture = taskScheduler.schedule(updateService::runUpdate, new CronTrigger(config.getCronExpr()));
                log.info("[UpdateScheduler] Registered update schedule: cron={}, branch={}",
                        config.getCronExpr(), config.getGitBranch());
            } catch (Exception e) {
                log.error("[UpdateScheduler] Failed to register update schedule", e);
            }
        } else {
            log.info("[UpdateScheduler] Self-update scheduler disabled");
        }

        if (Boolean.TRUE.equals(config.getHotDeployEnabled()) && config.getHotDeployCronExpr() != null) {
            try {
                currentHotDeployFuture = taskScheduler.schedule(updateService::runHotDeploy,
                        new CronTrigger(config.getHotDeployCronExpr()));
                log.info("[UpdateScheduler] Registered hot-deploy schedule: cron={}", config.getHotDeployCronExpr());
            } catch (Exception e) {
                log.error("[UpdateScheduler] Failed to register hot-deploy schedule", e);
            }
        } else {
            log.info("[UpdateScheduler] Hot-deploy scheduler disabled");
        }
    }
}
