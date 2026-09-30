package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.FeedLearningMetricsDto;
import com.english.mapper.RecommendationEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/metrics")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
@RequiredArgsConstructor
public class FeedLearningMetricsController {
    private final RecommendationEventMapper eventMapper;

    @GetMapping("/feed-learning")
    public Result<FeedLearningMetricsDto> getFeedLearningMetrics(
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        Map<String, Object> row = eventMapper.selectFeedLearningMetrics(safeDays);
        FeedLearningMetricsDto dto = new FeedLearningMetricsDto();
        dto.setDays(safeDays);
        dto.setExposedPairs(asLong(row, "exposedPairs"));
        dto.setClickedPairs(asLong(row, "clickedPairs"));
        dto.setCompletedPairs(asLong(row, "completedPairs"));
        dto.setClickRate(dto.getExposedPairs() == 0 ? 0
                : (double) dto.getClickedPairs() / dto.getExposedPairs());
        dto.setCompletionPerClickRate(dto.getClickedPairs() == 0 ? 0
                : (double) dto.getCompletedPairs() / dto.getClickedPairs());
        return Result.success(dto);
    }

    private long asLong(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }
}
