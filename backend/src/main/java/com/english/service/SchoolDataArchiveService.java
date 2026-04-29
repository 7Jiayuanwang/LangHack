package com.english.service;

import java.util.Map;

/**
 * 学校数据归档服务
 * 将超过阈值年限的历史数据归档到独立表，避免主表数据量过大
 */
public interface SchoolDataArchiveService {

    /**
     * 归档指定数据源的历史数据
     * @param dataSource 数据源名称
     * @return 归档结果统计
     */
    Map<String, Object> archiveDataSource(String dataSource);

    /**
     * 归档所有启用的数据源
     * @return 各数据源归档结果
     */
    Map<String, Map<String, Object>> archiveAll();
}
