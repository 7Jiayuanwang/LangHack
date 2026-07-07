package com.english.service.impl;

import com.english.entity.AiCallLog;
import com.english.entity.AiFeedback;
import com.english.enums.TaskType;
import com.english.mapper.AiCallLogMapper;
import com.english.mapper.AiFeedbackMapper;
import com.english.service.AiFeedbackService;
import com.english.service.CampaignStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AiFeedbackServiceImpl implements AiFeedbackService {

    /** 哨兵标记：来自外部悬浮/菜单入口的问题反馈点击，用于统计/筛除。 */
    public static final String EXTERNAL_CLICK_MARKER = "[external-hiagent-click]";

    @Autowired
    private AiFeedbackMapper aiFeedbackMapper;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @Autowired
    private CampaignStatsService campaignStatsService;

    @Override
    @CacheEvict(value = "metrics", key = "'ai-feedback-summary'")
    public void submitFeedback(Long callLogId, Long userId, Boolean isHelpful, Integer rating, String feedbackText) {
        AiFeedback feedback = new AiFeedback();
        feedback.setCallLogId(callLogId);
        feedback.setUserId(userId);
        feedback.setIsHelpful(isHelpful);
        feedback.setRating(rating);
        feedback.setFeedbackText(feedbackText);
        aiFeedbackMapper.insert(feedback);

        // 事件驱动: 触发活动数据采集
        campaignStatsService.collectUserTasksByTypeAsync(userId, TaskType.FEEDBACK);
    }

    @Override
    @Transactional
    @CacheEvict(value = "metrics", key = "'ai-feedback-summary'")
    public void submitExternalFeedbackClick(Long userId, String username) {
        // ai_feedback.call_log_id NOT NULL + FOREIGN KEY → ai_call_log(id)
        // 无法用 -1 哨兵，只能同步插入一条占位 call_log 拿到真 id
        AiCallLog callLog = new AiCallLog();
        callLog.setServiceName("HIAGENT");
        callLog.setMethodName("externalFeedbackClick");
        callLog.setSuccess(Boolean.TRUE);
        callLog.setResponseTimeMs(0L);
        callLog.setTriggeredBy(username);
        callLog.setCreatedTime(LocalDateTime.now());
        aiCallLogMapper.insert(callLog);

        AiFeedback feedback = new AiFeedback();
        feedback.setCallLogId(callLog.getId());
        feedback.setUserId(userId);
        feedback.setFeedbackText(EXTERNAL_CLICK_MARKER);
        aiFeedbackMapper.insert(feedback);

        // 事件驱动: 触发活动数据采集（FEEDBACK 任务按 ai_feedback 行数计数）
        campaignStatsService.collectUserTasksByTypeAsync(userId, TaskType.FEEDBACK);
    }

    @Override
    @Cacheable(value = "metrics", key = "'ai-feedback-summary'", unless = "#result == null")
    public Map<String, Object> getFeedbackSummary() {
        Map<String, Object> result = aiFeedbackMapper.selectFeedbackSummary();
        return result != null ? result : new HashMap<>();
    }
}
