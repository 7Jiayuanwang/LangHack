package com.english.service;

import com.english.dto.learner.LearnerProfileClassSummaryDto;
import com.english.dto.learner.LearnerProfileDto;

import java.util.List;

public interface LearnerProfileService {

    LearnerProfileDto getUserProfile(Long userId);

    List<LearnerProfileClassSummaryDto> getClassProfileSummary(Long classId);
}
