package com.english.service;

import com.english.dto.learner.LearnerTagDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CompositeTagEngine {

    List<LearnerTagDto> evaluateCompositeTags(Set<String> baseTagCodes);

    List<LearnerTagDto> evaluateAll(Map<String, Double> metrics);
}
