package com.english.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.BatchEnrollResult;
import com.english.entity.CampaignActivity;
import com.english.entity.CampaignParticipation;

import java.util.List;
import java.util.Map;

/**
 * 活动报名管理服务
 */
public interface CampaignEnrollmentService {

    /**
     * 学生报名参加活动
     */
    void enrollCampaign(Long campaignId, Long userId);

    /**
     * 学生退出活动
     */
    void withdrawCampaign(Long campaignId, Long userId);

    /**
     * 检查是否已报名
     */
    boolean isEnrolled(Long campaignId, Long userId);

    /**
     * 获取用户报名记录
     */
    CampaignParticipation getParticipation(Long campaignId, Long userId);

    /**
     * 获取可报名的活动列表
     */
    Page<CampaignActivity> getEnrollableCampaigns(Long userId, int current, int size, Map<String, Object> params);

    /**
     * 获取我参与的活动列表
     */
    Page<CampaignActivity> getMyEnrolledCampaigns(Long userId, int current, int size, Map<String, Object> params);

    /**
     * 批准报名（INVITE_ONLY模式）
     */
    void approveEnrollment(Long campaignId, Long participationId);

    /**
     * 驳回报名
     */
    void rejectEnrollment(Long campaignId, Long participationId, String reason);

    /**
     * 获取待审核报名列表
     */
    List<CampaignParticipation> getPendingEnrollments(Long campaignId);

    /**
     * 获取已通过报名列表
     */
    List<CampaignParticipation> getEnrolledList(Long campaignId);

    /**
     * 获取已驳回报名列表
     */
    List<CampaignParticipation> getRejectedList(Long campaignId);

    /**
     * 批量添加参与者（管理员）
     * 检查人数上限 + 报名范围；force=false 时不符合范围的用户跳过并返回原因，
     * force=true 时强制报名（仍受人数上限约束）。
     */
    BatchEnrollResult batchEnrollUsers(Long campaignId, List<Long> userIds, boolean force);

    /**
     * 统计活动已报名（ENROLLED）参与人数
     */
    int countEnrolledParticipants(Long campaignId);
}
