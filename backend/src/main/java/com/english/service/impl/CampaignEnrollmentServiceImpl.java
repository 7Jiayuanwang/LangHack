package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.entity.CampaignParticipation;
import com.english.entity.CampaignTask;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentMode;
import com.english.enums.EnrollmentStatus;
import com.english.mapper.CampaignActivityMapper;
import com.english.mapper.CampaignParticipationMapper;
import com.english.mapper.CampaignTaskMapper;
import com.english.service.CampaignEnrollmentService;
import com.english.service.CampaignService;
import com.english.service.CampaignStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动报名管理服务实现
 */
@Slf4j
@Service
public class CampaignEnrollmentServiceImpl implements CampaignEnrollmentService {

    @Autowired
    private CampaignParticipationMapper participationMapper;

    @Autowired
    private CampaignActivityMapper campaignActivityMapper;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignStatsService campaignStatsService;

    @Autowired
    private CampaignTaskMapper taskMapper;

    @Override
    public void enrollCampaign(Long campaignId, Long userId) {
        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null) {
            throw new RuntimeException("活动不存在");
        }

        // 检查是否已报名
        if (isEnrolled(campaignId, userId)) {
            throw new RuntimeException("您已报名该活动");
        }

        // 检查报名资格
        if (!campaignService.canUserEnroll(campaignId, userId)) {
            List<String> restrictions = campaignService.getEnrollmentRestrictions(campaignId, userId);
            throw new RuntimeException("无法报名：" + String.join("; ", restrictions));
        }

        // 检查人数限制（0 表示不限制）
        if (campaign.getMaxParticipants() != null && campaign.getMaxParticipants() > 0) {
            QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
            wrapper.eq("campaign_id", campaignId);
            wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
            long count = participationMapper.selectCount(wrapper);
            if (count >= campaign.getMaxParticipants()) {
                throw new RuntimeException("活动参与人数已满");
            }
        }

        // 创建参与记录
        CampaignParticipation participation = new CampaignParticipation();
        participation.setCampaignId(campaignId);
        participation.setUserId(userId);
        participation.setEnrollmentTime(LocalDateTime.now());
        participation.setEnrollmentSource("MANUAL");

        // 根据报名模式设置状态
        if (campaign.getEnrollmentMode() == EnrollmentMode.INVITE_ONLY) {
            participation.setEnrollmentStatus(EnrollmentStatus.PENDING);
        } else {
            participation.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
        }

        participation.setCompletionStatus("IN_PROGRESS");
        participation.setJoinTime(LocalDateTime.now());

        participationMapper.insert(participation);
    }

    @Override
    public void withdrawCampaign(Long campaignId, Long userId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        CampaignParticipation participation = participationMapper.selectOne(wrapper);

        if (participation == null) {
            throw new RuntimeException("您未报名该活动");
        }

        participation.setEnrollmentStatus(EnrollmentStatus.WITHDRAWN);
        participation.setLastUpdateTime(LocalDateTime.now());
        participationMapper.updateById(participation);
    }

    @Override
    public boolean isEnrolled(Long campaignId, Long userId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        wrapper.in("enrollment_status", EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);
        return participationMapper.selectCount(wrapper) > 0;
    }

    @Override
    public CampaignParticipation getParticipation(Long campaignId, Long userId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        return participationMapper.selectOne(wrapper);
    }

    @Override
    public Page<CampaignActivity> getEnrollableCampaigns(Long userId, int current, int size, Map<String, Object> params) {
        Page<CampaignActivity> page = new Page<>(current, size);
        QueryWrapper<CampaignActivity> wrapper = new QueryWrapper<>();

        // 只显示进行中的活动；启动后立即可见（不考虑报名时间窗口）
        wrapper.eq("status", CampaignStatus.ACTIVE);

        // 时间范围筛选：活动尚未结束
        LocalDateTime now = LocalDateTime.now();
        wrapper.ge("end_time", now);

        if (params.containsKey("keyword")) {
            String keyword = (String) params.get("keyword");
            wrapper.and(w -> w.like("title", keyword).or().like("description", keyword));
        }

        wrapper.orderByDesc("created_time");
        Page<CampaignActivity> result = campaignActivityMapper.selectPage(page, wrapper);

        // 填充参与人数和当前用户报名状态
        enrichCampaignList(result.getRecords(), userId);

        return result;
    }

    @Override
    public Page<CampaignActivity> getMyEnrolledCampaigns(Long userId, int current, int size, Map<String, Object> params) {
        // 获取用户参与的活动ID列表
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        List<CampaignParticipation> participations = participationMapper.selectList(wrapper);

        Page<CampaignActivity> page = new Page<>(current, size);
        if (participations.isEmpty()) {
            return page;
        }

        List<Long> campaignIds = participations.stream()
            .map(CampaignParticipation::getCampaignId)
            .toList();

        QueryWrapper<CampaignActivity> campaignWrapper = new QueryWrapper<>();
        campaignWrapper.in("id", campaignIds);

        if (params.containsKey("status")) {
            campaignWrapper.eq("status", params.get("status"));
        }

        campaignWrapper.orderByDesc("created_time");
        Page<CampaignActivity> result = campaignActivityMapper.selectPage(page, campaignWrapper);

        // 填充参与人数、报名状态和进度信息
        enrichCampaignList(result.getRecords(), userId);
        enrichMyEnrolledProgress(result.getRecords(), userId);

        return result;
    }

    /**
     * 填充"我的活动"列表的进度信息（总分、完成任务数、排名等）
     */
    private void enrichMyEnrolledProgress(List<CampaignActivity> campaigns, Long userId) {
        if (campaigns == null || campaigns.isEmpty() || userId == null) {
            return;
        }

        for (CampaignActivity campaign : campaigns) {
            // 获取用户进度数据
            Map<String, Object> progress = campaignStatsService.getMyProgress(campaign.getId(), userId);

            campaign.setCompletionStatus((String) progress.get("completionStatus"));
            campaign.setTotalScore((Integer) progress.get("totalScore"));
            campaign.setCompletedTasks((Integer) progress.get("completedTasks"));

            // 获取活动总任务数
            QueryWrapper<CampaignTask> taskWrapper = new QueryWrapper<>();
            taskWrapper.eq("campaign_id", campaign.getId());
            long totalTasks = taskMapper.selectCount(taskWrapper);
            campaign.setTotalTasks((int) totalTasks);

            // 排名
            Object rankObj = progress.get("myRank");
            if (rankObj instanceof Long) {
                campaign.setMyRank((Long) rankObj);
            } else if (rankObj instanceof Integer) {
                campaign.setMyRank(((Integer) rankObj).longValue());
            }
        }
    }

    /**
     * 填充活动列表的额外信息（参与人数、当前用户报名状态）
     */
    private void enrichCampaignList(List<CampaignActivity> campaigns, Long userId) {
        if (campaigns == null || campaigns.isEmpty()) {
            return;
        }

        List<Long> campaignIds = campaigns.stream()
            .map(CampaignActivity::getId)
            .toList();

        // 批量查询每个活动的参与人数
        for (CampaignActivity campaign : campaigns) {
            QueryWrapper<CampaignParticipation> countWrapper = new QueryWrapper<>();
            countWrapper.eq("campaign_id", campaign.getId());
            countWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
            long count = participationMapper.selectCount(countWrapper);
            campaign.setParticipantCount((int) count);
        }

        // 批量查询当前用户的报名状态
        if (userId != null) {
            QueryWrapper<CampaignParticipation> userWrapper = new QueryWrapper<>();
            userWrapper.eq("user_id", userId);
            userWrapper.in("campaign_id", campaignIds);
            userWrapper.in("enrollment_status", EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);
            List<CampaignParticipation> userParticipations = participationMapper.selectList(userWrapper);

            // 构建 campaignId -> isEnrolled 的映射
            java.util.Set<Long> enrolledCampaignIds = userParticipations.stream()
                .map(CampaignParticipation::getCampaignId)
                .collect(java.util.stream.Collectors.toSet());

            // 设置每个活动的 isEnrolled 状态
            for (CampaignActivity campaign : campaigns) {
                campaign.setIsEnrolled(enrolledCampaignIds.contains(campaign.getId()));
            }
        }
    }

    @Override
    public void approveEnrollment(Long participationId) {
        CampaignParticipation participation = participationMapper.selectById(participationId);
        if (participation == null) {
            throw new RuntimeException("报名记录不存在");
        }
        if (participation.getEnrollmentStatus() != EnrollmentStatus.PENDING) {
            throw new RuntimeException("只能审核待审核状态的报名");
        }

        participation.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
        participation.setLastUpdateTime(LocalDateTime.now());
        participationMapper.updateById(participation);
    }

    @Override
    public void rejectEnrollment(Long participationId, String reason) {
        CampaignParticipation participation = participationMapper.selectById(participationId);
        if (participation == null) {
            throw new RuntimeException("报名记录不存在");
        }
        if (participation.getEnrollmentStatus() != EnrollmentStatus.PENDING) {
            throw new RuntimeException("只能审核待审核状态的报名");
        }

        participation.setEnrollmentStatus(EnrollmentStatus.REJECTED);
        participation.setLastUpdateTime(LocalDateTime.now());
        participationMapper.updateById(participation);
    }

    @Override
    public List<CampaignParticipation> getPendingEnrollments(Long campaignId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.PENDING);
        wrapper.orderByAsc("enrollment_time");
        return participationMapper.selectList(wrapper);
    }

    @Override
    public void batchEnrollUsers(Long campaignId, List<Long> userIds) {
        for (Long userId : userIds) {
            try {
                if (!isEnrolled(campaignId, userId)) {
                    CampaignParticipation participation = new CampaignParticipation();
                    participation.setCampaignId(campaignId);
                    participation.setUserId(userId);
                    participation.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
                    participation.setEnrollmentSource("ADMIN");
                    participation.setEnrollmentTime(LocalDateTime.now());
                    participation.setCompletionStatus("IN_PROGRESS");
                    participation.setJoinTime(LocalDateTime.now());
                    participationMapper.insert(participation);
                }
            } catch (Exception e) {
                log.error("批量添加用户{}失败", userId, e);
            }
        }
    }
}
