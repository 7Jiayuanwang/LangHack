package com.english.service;

import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 活动数据管理服务
 */
public interface CampaignDataService {

    /**
     * 手动录入数据
     */
    void recordManualData(Long campaignId, Long taskId, Long userId, BigDecimal value, String remark, Long createdBy);

    /**
     * Excel批量导入
     */
    Map<String, Object> importFromExcel(Long campaignId, Long taskId, MultipartFile file);

    /**
     * 导出活动数据
     */
    byte[] exportCampaignData(Long campaignId, String format);

    /**
     * 导出排名数据
     */
    byte[] exportRankingData(Long campaignId, String dimension, String format);
}
