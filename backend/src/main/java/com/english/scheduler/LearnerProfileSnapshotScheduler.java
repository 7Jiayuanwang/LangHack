package com.english.scheduler;

import com.english.service.LearnerProfileSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LearnerProfileSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(LearnerProfileSnapshotScheduler.class);

    @Value("${learner-profile.snapshot.enabled:false}")
    private boolean snapshotEnabled;

    @Autowired
    private LearnerProfileSnapshotService snapshotService;

    @Scheduled(cron = "0 30 2 * * ?")
    public void dailySnapshot() {
        if (!snapshotEnabled) {
            log.debug("[Snapshot] 快照功能已关闭, 跳过");
            return;
        }
        log.info("[Snapshot] 每日快照任务开始");
        snapshotService.takeDailySnapshotsForAllActiveUsers();
    }
}
