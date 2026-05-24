package com.english.service;

import com.english.entity.AiFeedback;
import java.util.Map;

public interface AiFeedbackService {

    void submitFeedback(Long callLogId, Long userId, Boolean isHelpful, Integer rating, String feedbackText);

    Map<String, Object> getFeedbackSummary();
}
