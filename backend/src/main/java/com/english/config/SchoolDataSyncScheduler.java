package com.english.config;

import com.english.config.SchoolDataProperties;
import com.english.entity.SyncLog;
import com.english.service.SchoolDataSyncService;
import com.english.service.SyncMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 学校数据同步调度器
 * <p>
 * 三个定时任务:
 * 1. 全量同步 - 默认每天凌晨2点执行
 * 2. 增量同步 - 默认每小时执行
 * 3. 数据校验 - 默认每天凌晨4点执行
 * <p>
 * 使用 AtomicBoolean 防止重入
 */
@Component
public class SchoolDataSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataSyncScheduler.class);

    private final TaskScheduler taskScheduler;
    private final SchoolDataSyncService syncService;
    private final SyncMonitorService monitorService;
    private final SchoolDataProperties properties;

    private ScheduledFuture<?> fullSyncTask;
    private ScheduledFuture<?> incrementalSyncTask;
    private ScheduledFuture<?> validationTask;
    private ScheduledFuture<?> collegeChainTask;

    /** 防止重入标志 */
    private final AtomicBoolean fullSyncRunning = new AtomicBoolean(false);
    private final AtomicBoolean incrementalSyncRunning = new AtomicBoolean(false);
    private final AtomicBoolean validationRunning = new AtomicBoolean(false);
    private final AtomicBoolean collegeChainRunning = new AtomicBoolean(false);

    public SchoolDataSyncScheduler(@Qualifier("syncTaskScheduler") TaskScheduler taskScheduler,
                                    @Lazy SchoolDataSyncService syncService,
                                    @Lazy SyncMonitorService monitorService,
                                    SchoolDataProperties properties) {
        this.taskScheduler = taskScheduler;
        this.syncService = syncService;
        this.monitorService = monitorService;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.getSync().isEnabled()) {
            logger.info("School data sync scheduler is disabled");
            return;
        }

        scheduleFullSync();
        scheduleIncrementalSync();
        scheduleValidation();
        scheduleCollegeChain();

        logger.info("School data sync scheduler initialized - Full: [{}], Incremental: [{}], Validation: [{}], CollegeChain: [{}] (enabled={})",
                properties.getSync().getFullSyncCron(),
                properties.getSync().getIncrementalSyncCron(),
                properties.getSync().getValidationCron(),
                properties.getSync().getCollegeChain().getCron(),
                properties.getSync().getCollegeChain().isEnabled());
    }

    /**
     * 重新调度所有任务（修改配置后调用）
     */
    public synchronized void rescheduleAll() {
        cancelAll();
        if (properties.getSync().isEnabled()) {
            scheduleFullSync();
            scheduleIncrementalSync();
            scheduleValidation();
            scheduleCollegeChain();
            logger.info("School data sync scheduler rescheduled");
        } else {
            logger.info("School data sync scheduler is disabled, all tasks cancelled");
        }
    }

    /**
     * 取消所有定时任务
     */
    public synchronized void cancelAll() {
        cancelTask(fullSyncTask, "full-sync");
        cancelTask(incrementalSyncTask, "incremental-sync");
        cancelTask(validationTask, "validation");
        cancelTask(collegeChainTask, "college-chain");
    }

    // =========================================================================
    // 调度方法
    // =========================================================================

    private void scheduleFullSync() {
        fullSyncTask = taskScheduler.schedule(
                this::executeFullSync,
                new CronTrigger(properties.getSync().getFullSyncCron())
        );
    }

    private void scheduleIncrementalSync() {
        incrementalSyncTask = taskScheduler.schedule(
                this::executeIncrementalSync,
                new CronTrigger(properties.getSync().getIncrementalSyncCron())
        );
    }

    private void scheduleValidation() {
        validationTask = taskScheduler.schedule(
                this::executeValidation,
                new CronTrigger(properties.getSync().getValidationCron())
        );
    }

    private void scheduleCollegeChain() {
        SchoolDataProperties.CollegeChainProperties cc = properties.getSync().getCollegeChain();
        if (!cc.isEnabled()) {
            logger.info("College chain refresh is disabled, skipping schedule");
            return;
        }
        collegeChainTask = taskScheduler.schedule(
                this::executeCollegeChainRefresh,
                new CronTrigger(cc.getCron())
        );
    }

    private void cancelTask(ScheduledFuture<?> task, String taskName) {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
            logger.info("Cancelled scheduled task: {}", taskName);
        }
    }

    // =========================================================================
    // 执行方法（含防重入）
    // =========================================================================

    private void executeFullSync() {
        if (!fullSyncRunning.compareAndSet(false, true)) {
            logger.warn("全量同步正在运行中，跳过本次调度");
            return;
        }

        try {
            logger.info("=== 定时全量同步开始 ===");
            List<SyncLog> logs = syncService.fullSyncAll("SCHEDULED", "SYSTEM");
            long successCount = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
            long failCount = logs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
            logger.info("=== 定时全量同步完成: 成功={}, 失败={} ===", successCount, failCount);
        } catch (Exception e) {
            logger.error("定时全量同步异常", e);
        } finally {
            fullSyncRunning.set(false);
        }
    }

    private void executeIncrementalSync() {
        if (!incrementalSyncRunning.compareAndSet(false, true)) {
            logger.warn("增量同步正在运行中，跳过本次调度");
            return;
        }

        try {
            logger.info("=== 定时增量同步开始 ===");
            List<SyncLog> logs = syncService.incrementalSyncAll("SCHEDULED", "SYSTEM");
            long successCount = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
            long failCount = logs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
            logger.info("=== 定时增量同步完成: 成功={}, 失败={} ===", successCount, failCount);
        } catch (Exception e) {
            logger.error("定时增量同步异常", e);
        } finally {
            incrementalSyncRunning.set(false);
        }
    }

    private void executeValidation() {
        if (!validationRunning.compareAndSet(false, true)) {
            logger.warn("数据校验正在运行中，跳过本次调度");
            return;
        }

        try {
            logger.info("=== 定时数据校验开始 ===");
            Map<String, Object> validationResult = syncService.validateData();
            Map<String, Object> healthResult = monitorService.checkSyncHealth();

            boolean overallHealthy = Boolean.TRUE.equals(validationResult.get("overallHealthy"));
            Map<String, Object> summary = (Map<String, Object>) healthResult.get("summary");
            String overallStatus = summary != null ? (String) summary.get("overallStatus") : "UNKNOWN";

            if (!overallHealthy || !"HEALTHY".equals(overallStatus)) {
                logger.warn("数据校验发现问题 - 状态: {}, 详情: {}", overallStatus, validationResult);
            } else {
                logger.info("=== 数据校验通过 ===");
            }
        } catch (Exception e) {
            logger.error("定时数据校验异常", e);
        } finally {
            validationRunning.set(false);
        }
    }

    private void executeCollegeChainRefresh() {
        if (!collegeChainRunning.compareAndSet(false, true)) {
            logger.warn("链式刷新正在运行中，跳过本次调度");
            return;
        }

        SchoolDataProperties.CollegeChainProperties cc = properties.getSync().getCollegeChain();
        try {
            logger.info("=== 定时链式刷新开始 colleges={} matchMode={} ===", cc.getColleges(), cc.getMatchMode());
            for (String college : cc.getColleges()) {
                if (college == null || college.trim().isEmpty()) continue;
                try {
                    Map<String, Object> result = syncService.chainFetchStudentsByCollege(college.trim(), cc.getMatchMode(), true);
                    logger.info("链式刷新完成 college={}, result={}", college, result);
                } catch (Exception e) {
                    logger.error("链式刷新单学院失败 college={}", college, e);
                }
            }
            logger.info("=== 定时链式刷新完成 ===");
        } catch (Exception e) {
            logger.error("定时链式刷新异常", e);
        } finally {
            collegeChainRunning.set(false);
        }
    }
}
