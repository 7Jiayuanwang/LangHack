package com.english.service;

import com.english.dto.learner.InternetContentRecommendationDto;

import java.util.List;

public interface InternetContentRecommendationService {

    List<InternetContentRecommendationDto> recommendForUser(Long userId, int limit);
}
