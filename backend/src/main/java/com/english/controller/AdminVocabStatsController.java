package com.english.controller;

import com.english.dto.Result;
import com.english.mapper.VocabularyBookMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/vocab-stats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVocabStatsController {

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        String today = LocalDate.now().toString();
        String sevenDaysAgo = LocalDate.now().minusDays(6).toString();
        Map<String, Object> data = new HashMap<>();
        data.put("platform", vocabularyBookMapper.selectPlatformSummary());
        data.put("masteryDistribution", vocabularyBookMapper.selectMasteryLevelDistribution());
        data.put("efDistribution", vocabularyBookMapper.selectEFDistribution());
        data.put("intervalDistribution", vocabularyBookMapper.selectIntervalDistribution());
        data.put("sourceDistribution", vocabularyBookMapper.selectSourceTypeDistribution());
        data.put("dailyNewTrend", vocabularyBookMapper.selectDailyNewTrendByRange(sevenDaysAgo, today));
        data.put("dailyReviewTrend", vocabularyBookMapper.selectDailyReviewTrendByRange(sevenDaysAgo, today));
        return Result.success(data);
    }

    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "") String start,
            @RequestParam(defaultValue = "") String end) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate endDate   = end.isEmpty()   ? LocalDate.now()            : LocalDate.parse(end, fmt);
        LocalDate startDate = start.isEmpty() ? endDate.minusDays(6)       : LocalDate.parse(start, fmt);
        // 限制最大查询范围 366 天，避免超大结果集
        if (startDate.isBefore(endDate.minusDays(366))) startDate = endDate.minusDays(366);
        Map<String, Object> data = new HashMap<>();
        data.put("dailyNewTrend",    vocabularyBookMapper.selectDailyNewTrendByRange(startDate.toString(), endDate.toString()));
        data.put("dailyReviewTrend", vocabularyBookMapper.selectDailyReviewTrendByRange(startDate.toString(), endDate.toString()));
        data.put("start", startDate.toString());
        data.put("end",   endDate.toString());
        return Result.success(data);
    }

    @GetMapping("/students")
    public Result<List<Map<String, Object>>> getStudentStats() {
        return Result.success(vocabularyBookMapper.selectPerStudentSummary());
    }
}
