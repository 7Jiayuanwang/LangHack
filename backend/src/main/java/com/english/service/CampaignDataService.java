package com.english.service;

import com.english.entity.CampaignManualRecord;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
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
     * 获取活动最近的录入记录（含用户名、任务名、录入人姓名）
     */
    List<CampaignManualRecord> getRecentRecords(Long campaignId, int limit);

    /**
     * 导出活动数据
     */
    byte[] exportCampaignData(Long campaignId, String format);

    /**
     * 导出排名数据
     */
    byte[] exportRankingData(Long campaignId, String dimension, String format);
}
