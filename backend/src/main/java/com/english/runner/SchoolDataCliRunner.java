package com.english.runner;

import com.english.service.SchoolDataFetchService;
import com.english.service.SchoolDataPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "school.data.cli", name = "enabled", havingValue = "true")
public class SchoolDataCliRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataCliRunner.class);

    private final SchoolDataFetchService fetchService;
    private final SchoolDataPersistService persistService;

    public SchoolDataCliRunner(SchoolDataFetchService fetchService,
                               SchoolDataPersistService persistService) {
        this.fetchService = fetchService;
        this.persistService = persistService;
    }

    @Override
    public void run(String... args) {
        String mode = System.getProperty("school.data.cli.mode", System.getenv().getOrDefault("SCHOOL_DATA_CLI_MODE", ""));
        String dataSource = System.getProperty("school.data.cli.data-source", System.getenv().getOrDefault("SCHOOL_DATA_CLI_DATA_SOURCE", ""));
        if (mode.isEmpty() || dataSource.isEmpty()) {
            logger.info("school.data.cli 已启用，但未提供 mode/data-source，跳过执行");
            return;
        }

        List<Map<String, Object>> rawData = fetchService.fetchRawData(dataSource, null);
        logger.info("CLI 拉取完成 dataSource={} count={}", dataSource, rawData.size());

        if ("fetch".equalsIgnoreCase(mode)) {
            return;
        }
        if ("sync".equalsIgnoreCase(mode) || "persist".equalsIgnoreCase(mode)) {
            SchoolDataPersistService.PersistResult result = persistService.persistRawData(dataSource, rawData, "LOCAL_CLI");
            logger.info("CLI 持久化完成 dataSource={} total={} success={} fail={} skip={}",
                    dataSource, result.getTotal(), result.getSuccess(), result.getFail(), result.getSkip());
        }
    }
}
