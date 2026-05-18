package com.english.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 学校数据归档配置
 */
@Component
@ConfigurationProperties(prefix = "school.data.archive")
public class SchoolDataArchiveProperties {

    /**
     * 是否启用归档功能
     */
    private boolean enabled = false;

    /**
     * 归档阈值（年），超过此年限的数据将被归档
     */
    private int thresholdYears = 3;

    /**
     * 每个数据源的归档配置
     */
    private Map<String, DataSourceArchiveConfig> dataSources = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getThresholdYears() {
        return thresholdYears;
    }

    public void setThresholdYears(int thresholdYears) {
        this.thresholdYears = thresholdYears;
    }

    public Map<String, DataSourceArchiveConfig> getDataSources() {
        return dataSources;
    }

    public void setDataSources(Map<String, DataSourceArchiveConfig> dataSources) {
        this.dataSources = dataSources;
    }

    public static class DataSourceArchiveConfig {
        /**
         * 是否启用此数据源的归档
         */
        private boolean enabled = true;

        /**
         * 归档表名（默认为 {原表名}_archive）
         */
        private String archiveTable;

        /**
         * 时间字段名（用于判断数据年限）
         */
        private String timeField = "academic_year";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getArchiveTable() {
            return archiveTable;
        }

        public void setArchiveTable(String archiveTable) {
            this.archiveTable = archiveTable;
        }

        public String getTimeField() {
            return timeField;
        }

        public void setTimeField(String timeField) {
            this.timeField = timeField;
        }
    }
}
