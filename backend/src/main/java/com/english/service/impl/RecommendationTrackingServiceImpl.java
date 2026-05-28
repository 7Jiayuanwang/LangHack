package com.english.service.impl;

import com.english.entity.RecommendationEvent;
import com.english.mapper.RecommendationEventMapper;
import com.english.service.RecommendationTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationTrackingServiceImpl implements RecommendationTrackingService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationTrackingServiceImpl.class);

    @Value("${learner-profile.tracking.enabled:false}")
    private boolean trackingEnabled;

    @Autowired
    private RecommendationEventMapper recommendationEventMapper;

    @Override
    public void trackEvent(Long userId, Long textId, String eventType, String source, Integer position) {
        if (!trackingEnabled) return;

        try {
            RecommendationEvent event = new RecommendationEvent();
            event.setUserId(userId);
            event.setTextId(textId);
            event.setEventType(eventType);
            event.setSource(source);
            event.setPosition(position);
            event.setCreatedTime(LocalDateTime.now());
            recommendationEventMapper.insert(event);
        } catch (Exception e) {
            log.warn("[Tracking] 记录推荐事件失败: userId={}, textId={}, type={}", userId, textId, eventType, e);
        }
    }

    @Override
    public void trackBatchImpressions(Long userId, List<Long> textIds, String source, String personaTags) {
        if (!trackingEnabled || textIds == null) return;

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < textIds.size(); i++) {
            try {
                RecommendationEvent event = new RecommendationEvent();
                event.setUserId(userId);
                event.setTextId(textIds.get(i));
                event.setEventType("IMPRESSION");
                event.setSource(source);
                event.setPosition(i);
                event.setPersonaTags(personaTags);
                event.setCreatedTime(now);
                recommendationEventMapper.insert(event);
            } catch (Exception e) {
                log.warn("[Tracking] 批量记录IMPRESSION失败: userId={}, textId={}", userId, textIds.get(i), e);
            }
        }
    }

    @Override
    public List<Map<String, Object>> getClickThroughRateBySource(int lastNDays) {
        if (!trackingEnabled) return Collections.emptyList();
        return recommendationEventMapper.selectCtrBySource(lastNDays);
    }

    @Override
    public List<Map<String, Object>> getPersonaEffectivenessMetrics(int lastNDays) {
        if (!trackingEnabled) return Collections.emptyList();
        return recommendationEventMapper.selectCtrByPersonaTag(lastNDays);
    }
}
