package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.enums.EnrollmentStatus;
import com.english.enums.TaskType;
import com.english.mapper.*;
import com.english.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 活动统计与排名服务实现（简化版）
 */
@Slf4j
@Service
public class CampaignStatsServiceImpl implements CampaignStatsService {

    @Autowired
    private CampaignTaskCompletionMapper completionMapper;

    @Autowired
    private CampaignParticipationMapper participationMapper;

    @Autowired
    private CampaignTaskMapper taskMapper;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private CampaignService campaignService;

    @Override
    public void collectTaskData(Long campaignId, Long taskId) {
        CampaignTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getAutoCollect()) {
            return;
        }

        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null) {
            return;
        }

        // 获取所有参与者
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        List<CampaignParticipation> participants = participationMapper.selectList(wrapper);

        for (CampaignParticipation participant : participants) {
            try {
                BigDecimal value = collectUserTaskData(campaign, task, participant.getUserId());
                updateTaskCompletion(campaignId, taskId, participant.getUserId(), value, task.getTargetValue());
            } catch (Exception e) {
                log.error("采集用户{}任务{}数据失败", participant.getUserId(), taskId, e);
            }
        }
    }

    private BigDecimal collectUserTaskData(CampaignActivity campaign, CampaignTask task, Long userId) {
        // 根据任务类型采集数据
        switch (task.getTaskType()) {
            case READING_TIME:
                // 获取学习时长（分钟）
                long minutes = learningRecordsService.getStudyMinutesInRange(
                    userId, campaign.getStartTime(), campaign.getEndTime()
                );
                return BigDecimal.valueOf(minutes);

            case WORD_LEARNING:
            case TEXT_UPLOAD:
            case FEEDBACK:
            case AI_QA:
                // TODO: 实现其他类型的数据采集
                return BigDecimal.ZERO;

            default:
                return BigDecimal.ZERO;
        }
    }

    private void updateTaskCompletion(Long campaignId, Long taskId, Long userId, BigDecimal value, Integer targetValue) {
        QueryWrapper<CampaignTaskCompletion> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("task_id", taskId);
        wrapper.eq("user_id", userId);

        CampaignTaskCompletion completion = completionMapper.selectOne(wrapper);
        if (completion == null) {
            completion = new CampaignTaskCompletion();
            completion.setCampaignId(campaignId);
            completion.setTaskId(taskId);
            completion.setUserId(userId);
        }

        completion.setCurrentValue(value);
        completion.setIsCompleted(value.compareTo(BigDecimal.valueOf(targetValue)) >= 0);

        if (completion.getId() == null) {
            completionMapper.insert(completion);
        } else {
            completionMapper.updateById(completion);
        }

        // 更新参与记录的总得分
        updateParticipationScore(campaignId, userId);
    }

    private void updateParticipationScore(Long campaignId, Long userId) {
        // 统计已完成任务数和总得分
        QueryWrapper<CampaignTaskCompletion> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        wrapper.eq("is_completed", true);
        long completedCount = completionMapper.selectCount(wrapper);

        // 更新参与记录
        QueryWrapper<CampaignParticipation> participationWrapper = new QueryWrapper<>();
        participationWrapper.eq("campaign_id", campaignId);
        participationWrapper.eq("user_id", userId);
        CampaignParticipation participation = participationMapper.selectOne(participationWrapper);

        if (participation != null) {
            participation.setCompletedTasks((int) completedCount);
            participation.setTotalScore((int) completedCount); // 简化：1个任务=1分
            participationMapper.updateById(participation);
        }
    }

    @Override
    @CacheEvict(value = {"campaign_ranking", "campaign_overview"}, allEntries = true, cacheManager = "campaignCacheManager")
    public void collectAllCampaignData(Long campaignId) {
        List<CampaignTask> tasks = taskMapper.selectList(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId)
        );
        for (CampaignTask task : tasks) {
            collectTaskData(campaignId, task.getId());
        }
    }

    @Override
    @Cacheable(value = "campaign_ranking", key = "'individual_' + #campaignId + '_' + #current + '_' + #size", cacheManager = "campaignCacheManager")
    public Page<CampaignParticipation> getRankingByIndividual(Long campaignId, int current, int size, Map<String, Object> params) {
        Page<CampaignParticipation> page = new Page<>(current, size);
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        wrapper.orderByDesc("total_score", "completed_tasks");
        return participationMapper.selectPage(page, wrapper);
    }

    @Override
    public List<Map<String, Object>> getRankingByClass(Long campaignId) {
        // TODO: 实现班级排名
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> getRankingByCollege(Long campaignId) {
        // TODO: 实现学院排名
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> getMyRanking(Long campaignId, Long userId) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        CampaignParticipation myParticipation = participationMapper.selectOne(wrapper);

        if (myParticipation == null) {
            result.put("enrolled", false);
            result.put("myRank", null);
            result.put("totalParticipants", 0);
            result.put("aroundMe", new ArrayList<>());
            return result;
        }

        // 计算我的排名（得分比我高的人数 + 1）
        QueryWrapper<CampaignParticipation> rankWrapper = new QueryWrapper<>();
        rankWrapper.eq("campaign_id", campaignId);
        rankWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        rankWrapper.gt("total_score", myParticipation.getTotalScore());
        long myRank = participationMapper.selectCount(rankWrapper) + 1;

        // 总参与人数
        QueryWrapper<CampaignParticipation> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("campaign_id", campaignId);
        totalWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        long totalParticipants = participationMapper.selectCount(totalWrapper);

        // 周围排名（前后各2名）
        QueryWrapper<CampaignParticipation> aroundWrapper = new QueryWrapper<>();
        aroundWrapper.eq("campaign_id", campaignId);
        aroundWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        aroundWrapper.orderByDesc("total_score");
        List<CampaignParticipation> allRanked = participationMapper.selectList(aroundWrapper);

        List<Map<String, Object>> aroundMe = new ArrayList<>();
        int myIndex = -1;
        for (int i = 0; i < allRanked.size(); i++) {
            if (allRanked.get(i).getUserId().equals(userId)) {
                myIndex = i;
                break;
            }
        }
        if (myIndex >= 0) {
            int from = Math.max(0, myIndex - 2);
            int to = Math.min(allRanked.size() - 1, myIndex + 2);
            for (int i = from; i <= to; i++) {
                CampaignParticipation p = allRanked.get(i);
                Map<String, Object> item = new HashMap<>();
                item.put("rank", i + 1);
                item.put("userId", p.getUserId());
                item.put("totalScore", p.getTotalScore());
                item.put("isMe", p.getUserId().equals(userId));
                aroundMe.add(item);
            }
        }

        result.put("enrolled", true);
        result.put("myRank", myRank);
        result.put("totalParticipants", totalParticipants);
        result.put("totalScore", myParticipation.getTotalScore());
        result.put("completedTasks", myParticipation.getCompletedTasks());
        result.put("aroundMe", aroundMe);

        return result;
    }

    @Override
    public List<CampaignParticipation> getRankingAroundMe(Long campaignId, Long userId, int range) {
        // TODO: 实现周围排名
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> getParticipationStats(Long campaignId) {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        long totalParticipants = participationMapper.selectCount(wrapper);

        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        long enrolledCount = participationMapper.selectCount(wrapper);

        stats.put("totalParticipants", totalParticipants);
        stats.put("enrolledCount", enrolledCount);

        return stats;
    }

    @Override
    @Cacheable(value = "campaign_overview", key = "#campaignId", cacheManager = "campaignCacheManager")
    public Map<String, Object> getCampaignOverview(Long campaignId) {
        Map<String, Object> overview = new HashMap<>();
        overview.putAll(getParticipationStats(campaignId));

        // 任务统计
        long taskCount = taskMapper.selectCount(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId)
        );
        overview.put("taskCount", taskCount);

        return overview;
    }

    @Override
    public Map<String, Object> getMyProgress(Long campaignId, Long userId) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        CampaignParticipation myParticipation = participationMapper.selectOne(wrapper);

        if (myParticipation == null) {
            result.put("enrolled", false);
            return result;
        }

        QueryWrapper<CampaignParticipation> rankWrapper = new QueryWrapper<>();
        rankWrapper.eq("campaign_id", campaignId);
        rankWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        rankWrapper.gt("total_score", myParticipation.getTotalScore());
        long myRank = participationMapper.selectCount(rankWrapper) + 1;

        result.put("enrolled", true);
        result.put("totalScore", myParticipation.getTotalScore());
        result.put("completedTasks", myParticipation.getCompletedTasks());
        result.put("completionStatus", myParticipation.getCompletionStatus());
        result.put("myRank", myRank);
        result.put("enrollmentStatus", myParticipation.getEnrollmentStatus());

        return result;
    }

    @Override
    public List<Map<String, Object>> getMyTaskProgress(Long campaignId, Long userId) {
        List<Map<String, Object>> progress = new ArrayList<>();

        // 获取所有任务
        List<CampaignTask> tasks = taskMapper.selectList(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId)
        );

        for (CampaignTask task : tasks) {
            QueryWrapper<CampaignTaskCompletion> wrapper = new QueryWrapper<>();
            wrapper.eq("campaign_id", campaignId);
            wrapper.eq("task_id", task.getId());
            wrapper.eq("user_id", userId);
            CampaignTaskCompletion completion = completionMapper.selectOne(wrapper);

            Map<String, Object> taskProgress = new HashMap<>();
            taskProgress.put("taskId", task.getId());
            taskProgress.put("taskName", task.getTaskName());
            taskProgress.put("taskType", task.getTaskType());
            taskProgress.put("taskUnit", task.getTaskUnit());
            taskProgress.put("targetValue", task.getTargetValue());
            taskProgress.put("points", task.getPoints());
            taskProgress.put("isRequired", task.getIsRequired());
            taskProgress.put("currentValue", completion != null ? completion.getCurrentValue() : BigDecimal.ZERO);
            taskProgress.put("isCompleted", completion != null && completion.getIsCompleted());
            taskProgress.put("completionCount", completion != null ? completion.getCompletionCount() : 0);

            progress.add(taskProgress);
        }

        return progress;
    }
}
