package com.english.service;

import java.util.List;
import java.util.Map;

public interface RecommendationTrackingService {

    void trackEvent(Long userId, Long textId, String eventType, String source, Integer position);

    void trackBatchImpressions(Long userId, List<Long> textIds, String source, String personaTags);

    List<Map<String, Object>> getClickThroughRateBySource(int lastNDays);

    List<Map<String, Object>> getPersonaEffectivenessMetrics(int lastNDays);
}
