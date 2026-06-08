package com.english.controller;

import com.english.dto.Result;
import com.english.mapper.VocabularyBookMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Map<String, Object> data = new HashMap<>();
        data.put("platform", vocabularyBookMapper.selectPlatformSummary());
        data.put("masteryDistribution", vocabularyBookMapper.selectMasteryLevelDistribution());
        data.put("efDistribution", vocabularyBookMapper.selectEFDistribution());
        data.put("intervalDistribution", vocabularyBookMapper.selectIntervalDistribution());
        return Result.success(data);
    }

    @GetMapping("/students")
    public Result<List<Map<String, Object>>> getStudentStats() {
        return Result.success(vocabularyBookMapper.selectPerStudentSummary());
    }
}
