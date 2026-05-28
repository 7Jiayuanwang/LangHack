package com.english.service;

import com.english.entity.RecommendationEvent;
import com.english.mapper.RecommendationEventMapper;
import com.english.service.impl.RecommendationTrackingServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationTrackingServiceTest {

    @Mock
    private RecommendationEventMapper recommendationEventMapper;

    @InjectMocks
    private RecommendationTrackingServiceImpl service;

    private void enableTracking() {
        ReflectionTestUtils.setField(service, "trackingEnabled", true);
    }

    private void disableTracking() {
        ReflectionTestUtils.setField(service, "trackingEnabled", false);
    }

    @Nested
    @DisplayName("Feature Toggle")
    class FeatureToggleTests {

        @Test
        @DisplayName("disabled → trackEvent does nothing")
        void disabled_trackEventNoop() {
            disableTracking();
            service.trackEvent(1L, 100L, "CLICK", "RECOMMEND", 0);
            verify(recommendationEventMapper, never()).insert(any());
        }

        @Test
        @DisplayName("disabled → trackBatchImpressions does nothing")
        void disabled_batchNoop() {
            disableTracking();
            service.trackBatchImpressions(1L, List.of(1L, 2L, 3L), "RECOMMEND", "VOCAB_WEAK");
            verify(recommendationEventMapper, never()).insert(any());
        }

        @Test
        @DisplayName("disabled → getClickThroughRateBySource returns empty")
        void disabled_ctrReturnsEmpty() {
            disableTracking();
            List<Map<String, Object>> result = service.getClickThroughRateBySource(30);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("disabled → getPersonaEffectivenessMetrics returns empty")
        void disabled_personaMetricsReturnsEmpty() {
            disableTracking();
            List<Map<String, Object>> result = service.getPersonaEffectivenessMetrics(30);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("trackEvent")
    class TrackEventTests {

        @Test
        @DisplayName("inserts event with correct fields")
        void tracksCorrectFields() {
            enableTracking();
            service.trackEvent(1L, 100L, "CLICK", "RECOMMEND", 3);

            ArgumentCaptor<RecommendationEvent> captor = ArgumentCaptor.forClass(RecommendationEvent.class);
            verify(recommendationEventMapper).insert(captor.capture());
            RecommendationEvent event = captor.getValue();
            assertEquals(1L, event.getUserId());
            assertEquals(100L, event.getTextId());
            assertEquals("CLICK", event.getEventType());
            assertEquals("RECOMMEND", event.getSource());
            assertEquals(3, event.getPosition());
            assertNotNull(event.getCreatedTime());
        }

        @Test
        @DisplayName("mapper exception → swallowed, no throw")
        void mapperException_swallowed() {
            enableTracking();
            doThrow(new RuntimeException("DB error")).when(recommendationEventMapper).insert(any());
            assertDoesNotThrow(() -> service.trackEvent(1L, 100L, "CLICK", "RECOMMEND", 0));
        }
    }

    @Nested
    @DisplayName("trackBatchImpressions")
    class TrackBatchImpressionsTests {

        @Test
        @DisplayName("inserts one event per text ID with correct positions")
        void batchInserts() {
            enableTracking();
            List<Long> textIds = List.of(10L, 20L, 30L);
            service.trackBatchImpressions(1L, textIds, "RECOMMEND", "INACTIVE_RISK");

            ArgumentCaptor<RecommendationEvent> captor = ArgumentCaptor.forClass(RecommendationEvent.class);
            verify(recommendationEventMapper, times(3)).insert(captor.capture());

            List<RecommendationEvent> events = captor.getAllValues();
            for (int i = 0; i < 3; i++) {
                assertEquals(textIds.get(i), events.get(i).getTextId());
                assertEquals(i, events.get(i).getPosition());
                assertEquals("IMPRESSION", events.get(i).getEventType());
                assertEquals("INACTIVE_RISK", events.get(i).getPersonaTags());
            }
        }

        @Test
        @DisplayName("null textIds → no insert")
        void nullTextIds_noop() {
            enableTracking();
            service.trackBatchImpressions(1L, null, "RECOMMEND", null);
            verify(recommendationEventMapper, never()).insert(any());
        }

        @Test
        @DisplayName("null personaTags → event created without persona tags")
        void nullPersonaTags_ok() {
            enableTracking();
            service.trackBatchImpressions(1L, List.of(10L), "RECOMMEND", null);

            ArgumentCaptor<RecommendationEvent> captor = ArgumentCaptor.forClass(RecommendationEvent.class);
            verify(recommendationEventMapper).insert(captor.capture());
            assertNull(captor.getValue().getPersonaTags());
        }

        @Test
        @DisplayName("partial failure → continues remaining inserts")
        void partialFailure_continues() {
            enableTracking();
            doThrow(new RuntimeException("fail"))
                    .doNothing()
                    .when(recommendationEventMapper).insert(any());
            service.trackBatchImpressions(1L, List.of(10L, 20L), "RECOMMEND", null);
            verify(recommendationEventMapper, times(2)).insert(any());
        }
    }

    @Nested
    @DisplayName("Analytics Queries")
    class AnalyticsQueryTests {

        @Test
        @DisplayName("getClickThroughRateBySource delegates to mapper")
        void ctr_delegates() {
            enableTracking();
            List<Map<String, Object>> expected = List.of(Map.of("source", "RECOMMEND", "ctr", 0.15));
            when(recommendationEventMapper.selectCtrBySource(30)).thenReturn(expected);

            List<Map<String, Object>> result = service.getClickThroughRateBySource(30);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("getPersonaEffectivenessMetrics delegates to mapper")
        void persona_delegates() {
            enableTracking();
            List<Map<String, Object>> expected = List.of(Map.of("tag", "VOCAB_WEAK", "ctr", 0.2));
            when(recommendationEventMapper.selectCtrByPersonaTag(7)).thenReturn(expected);

            List<Map<String, Object>> result = service.getPersonaEffectivenessMetrics(7);
            assertEquals(expected, result);
        }
    }
}
