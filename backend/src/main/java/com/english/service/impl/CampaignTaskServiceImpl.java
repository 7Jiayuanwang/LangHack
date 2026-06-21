package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.CampaignTask;
import com.english.mapper.CampaignTaskMapper;
import com.english.service.CampaignTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动任务服务实现
 */
@Slf4j
@Service
public class CampaignTaskServiceImpl implements CampaignTaskService {

    @Autowired
    private CampaignTaskMapper campaignTaskMapper;

    @Override
    public CampaignTask createTask(Long campaignId, CampaignTask task) {
        task.setCampaignId(campaignId);
        task.setCreatedTime(LocalDateTime.now());

        // 设置默认值
        if (task.getIsRequired() == null) {
            task.setIsRequired(false);
        }
        if (task.getAutoCollect() == null) {
            task.setAutoCollect(true);
        }
        if (task.getPoints() == null) {
            task.setPoints(1);
        }
        if (task.getSortOrder() == null) {
            task.setSortOrder(0);
        }

        campaignTaskMapper.insert(task);
        return task;
    }

    @Override
    public CampaignTask updateTask(Long taskId, CampaignTask task) {
        CampaignTask existing = getTaskById(taskId);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }

        task.setId(taskId);
        campaignTaskMapper.updateById(task);
        return getTaskById(taskId);
    }

    @Override
    public void deleteTask(Long taskId) {
        CampaignTask task = getTaskById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        campaignTaskMapper.deleteById(taskId);
    }

    @Override
    public CampaignTask getTaskById(Long taskId) {
        return campaignTaskMapper.selectById(taskId);
    }

    @Override
    public List<CampaignTask> getTasksByCampaign(Long campaignId) {
        QueryWrapper<CampaignTask> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.orderByAsc("sort_order", "id");
        return campaignTaskMapper.selectList(wrapper);
    }
}
