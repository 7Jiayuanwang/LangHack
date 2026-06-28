package com.english.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;

import java.util.List;
import java.util.Map;

/**
 * 活动管理服务
 */
public interface CampaignService {

    /**
     * 创建活动
     */
    CampaignActivity createCampaign(CampaignActivity campaign);

    /**
     * 更新活动
     */
    CampaignActivity updateCampaign(Long id, CampaignActivity campaign);

    /**
     * 删除活动（软删除）
     */
    void deleteCampaign(Long id);

    /**
     * 根据ID获取活动
     */
    CampaignActivity getCampaignById(Long id);

    /**
     * 分页查询活动列表
     */
    Page<CampaignActivity> getCampaigns(int current, int size, Map<String, Object> params);

    /**
     * 启动活动
     */
    void startCampaign(Long id);

    /**
     * 结束活动
     */
    void endCampaign(Long id);

    /**
     * 归档活动
     */
    void archiveCampaign(Long id);

    /**
     * 检查用户是否可以报名
     */
    boolean canUserEnroll(Long campaignId, Long userId);

    /**
     * 获取报名限制说明
     */
    List<String> getEnrollmentRestrictions(Long campaignId, Long userId);

    /**
     * 获取所有进行中的活动（用于定时任务）
     */
    List<CampaignActivity> getActiveCampaigns();

    /**
     * 获取所有已到期但仍在进行中的活动（用于定时任务）
     */
    List<CampaignActivity> getExpiredActiveCampaigns();
}
