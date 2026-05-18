package com.english.scheduler;

import com.english.service.SchoolDataArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 学校数据归档定时任务
 */
@Component
@ConditionalOnProperty(prefix = "school.data.archive", name = "enabled", havingValue = "true")
public class SchoolDataArchiveScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataArchiveScheduler.class);

    @Autowired
    private SchoolDataArchiveService archiveService;

    /**
     * 每月1日凌晨2点执行归档
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void executeArchive() {
        logger.info("开始执行学校数据归档任务");
        try {
            archiveService.archiveAll();
            logger.info("学校数据归档任务完成");
        } catch (Exception e) {
            logger.error("学校数据归档任务失败", e);
        }
    }
}
