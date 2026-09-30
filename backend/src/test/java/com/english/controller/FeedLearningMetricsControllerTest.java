package com.english.controller;

import com.english.dto.learner.FeedLearningMetricsDto;
import com.english.mapper.RecommendationEventMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedLearningMetricsControllerTest {
    @Test
    void reportsCompletionAfterClickRatherThanOnlyClickRate() {
        RecommendationEventMapper mapper = mock(RecommendationEventMapper.class);
        when(mapper.selectFeedLearningMetrics(30)).thenReturn(Map.of(
                "exposedPairs", 100L, "clickedPairs", 40L, "completedPairs", 18L));

        FeedLearningMetricsDto metrics = new FeedLearningMetricsController(mapper)
                .getFeedLearningMetrics(30).getData();

        assertEquals(0.4, metrics.getClickRate(), 0.001);
        assertEquals(0.45, metrics.getCompletionPerClickRate(), 0.001);
    }
}
