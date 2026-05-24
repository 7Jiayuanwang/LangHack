package com.english.service.impl;

import com.english.entity.AiFeedback;
import com.english.mapper.AiFeedbackMapper;
import com.english.service.AiFeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AiFeedbackServiceImpl implements AiFeedbackService {

    @Autowired
    private AiFeedbackMapper aiFeedbackMapper;

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
    }

    @Override
    @Cacheable(value = "metrics", key = "'ai-feedback-summary'", unless = "#result == null")
    public Map<String, Object> getFeedbackSummary() {
        Map<String, Object> result = aiFeedbackMapper.selectFeedbackSummary();
        return result != null ? result : new HashMap<>();
    }
}
