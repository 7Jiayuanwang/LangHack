package com.english.service;

import com.english.dto.learner.LearningAgentContextDto;
import com.english.dto.learner.LearningAgentMemoryRequest;
import com.english.dto.learner.LearningAgentOnboardingRequest;

public interface LearningAgentService {
    LearningAgentContextDto getContext(Long userId);

    LearningAgentContextDto saveOnboarding(Long userId, LearningAgentOnboardingRequest request);

    LearningAgentContextDto addMemory(Long userId, LearningAgentMemoryRequest request);

    LearningAgentContextDto deleteMemory(Long userId, Long memoryId);
}
