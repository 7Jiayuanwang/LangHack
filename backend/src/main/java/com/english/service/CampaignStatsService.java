package com.english.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignParticipation;
import com.english.enums.TaskType;

import java.util.List;
import java.util.Map;

/**
 * 活动统计与排名服务
 */
public interface CampaignStatsService {

    /**
     * 采集指定任务的数据
     */
    void collectTaskData(Long campaignId, Long taskId);

    /**
     * 采集活动所有任务的数据
     */
    void collectAllCampaignData(Long campaignId);

    /**
     * 为单个用户采集指定类型的任务数据（事件驱动触发）
     * 仅采集用户已报名的活跃活动中该类型的任务
     *
     * @param userId 用户ID
     * @param taskType 任务类型
     */
    void collectUserTasksByTypeAsync(Long userId, TaskType taskType);

    /**
     * 获取个人排名（管理员）
     */
    Page<CampaignParticipation> getRankingByIndividual(Long campaignId, int current, int size, Map<String, Object> params);

    /**
     * 获取班级排名
     */
    List<Map<String, Object>> getRankingByClass(Long campaignId);

    /**
     * 获取学院排名
     */
    List<Map<String, Object>> getRankingByCollege(Long campaignId);

    /**
     * 获取我的排名（学生端）
     */
    Map<String, Object> getMyRanking(Long campaignId, Long userId);

    /**
     * 获取我周围的排名
     */
    List<CampaignParticipation> getRankingAroundMe(Long campaignId, Long userId, int range);

    /**
     * 获取参与统计
     */
    Map<String, Object> getParticipationStats(Long campaignId);

    /**
     * 获取活动概览
     */
    Map<String, Object> getCampaignOverview(Long campaignId);

    /**
     * 获取我的进度（学生端）
     */
    Map<String, Object> getMyProgress(Long campaignId, Long userId);

    /**
     * 获取我的任务进度列表
     */
    List<Map<String, Object>> getMyTaskProgress(Long campaignId, Long userId);
}
