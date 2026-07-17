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
     * 手动录入/导入数据后直接更新指定任务的完成进度（同步执行，无需自动采集）
     * 用于 MANUAL 类型任务，value 为本次录入值，直接追加到当前进度
     *
     * @param campaignId 活动ID
     * @param taskId     任务ID
     * @param userId     用户ID
     * @param value      录入值（追加）
     */
    void applyManualRecord(Long campaignId, Long taskId, Long userId, java.math.BigDecimal value);

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
     * 获取我的排名（学生端，支持按周查询）
     * @param weekStart 周起始日（周一，格式 YYYY-MM-DD），null 表示全时段
     */
    Map<String, Object> getMyRanking(Long campaignId, Long userId, String weekStart);

    /**
     * 获取每周优秀个人和班级
     * @param campaignId 活动ID
     * @param weekStart 周起始日（周一），null 表示当周
     * @param topN 取前几名，默认3
     */
    Map<String, Object> getWeeklyAwards(Long campaignId, String weekStart, Integer topN);

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

    /**
     * 获取我的积分明细（学生端）
     * 返回每个任务的得分详情及手动录入记录
     */
    List<Map<String, Object>> getMyScoreDetails(Long campaignId, Long userId);
}
