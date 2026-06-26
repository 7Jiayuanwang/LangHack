package com.english.service;

import com.english.entity.LearnerProfileSummary;

public interface LearnerProfileSummaryService {

    LearnerProfileSummary generateSummary(Long userId, String summaryType);

    LearnerProfileSummary getLatestSummary(Long userId, String summaryType);
}
