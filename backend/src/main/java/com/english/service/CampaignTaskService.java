package com.english.service;

import com.english.entity.CampaignTask;

import java.util.List;

/**
 * 活动任务服务
 */
public interface CampaignTaskService {

    /**
     * 创建任务
     */
    CampaignTask createTask(Long campaignId, CampaignTask task);

    /**
     * 更新任务
     */
    CampaignTask updateTask(Long taskId, CampaignTask task);

    /**
     * 删除任务
     */
    void deleteTask(Long taskId);

    /**
     * 根据ID获取任务
     */
    CampaignTask getTaskById(Long taskId);

    /**
     * 获取活动的所有任务
     */
    List<CampaignTask> getTasksByCampaign(Long campaignId);
}
