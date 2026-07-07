package com.english.service;

import com.english.entity.AiFeedback;
import java.util.Map;

public interface AiFeedbackService {

    void submitFeedback(Long callLogId, Long userId, Boolean isHelpful, Integer rating, String feedbackText);

    /**
     * 记录一次「外部问题反馈入口」点击（导航栏/个人区跳 HiAgent 网页对话时上报）。
     * 会先插入一条占位 ai_call_log（满足 ai_feedback.call_log_id 外键），
     * 再插入一条带哨兵 feedbackText 的 ai_feedback，用于活动 FEEDBACK 任务计数。
     */
    void submitExternalFeedbackClick(Long userId, String username);

    Map<String, Object> getFeedbackSummary();
}
