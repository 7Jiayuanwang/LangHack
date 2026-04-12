package com.english.config;

import com.english.service.SchoolDataConfigRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 学校数据配置初始化器
 * <p>
 * 应用启动完成后，从 DB 加载 school_data 配置覆盖 YAML 默认值
 */
@Component
public class SchoolDataConfigInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataConfigInitializer.class);

    private final SchoolDataConfigRefreshService refreshService;

    public SchoolDataConfigInitializer(SchoolDataConfigRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            refreshService.refreshFromDb();
            logger.info("School data config initialized from DB on startup");
        } catch (Exception e) {
            logger.warn("Failed to load school data config from DB on startup, using YAML defaults: {}", e.getMessage());
        }
    }
}
