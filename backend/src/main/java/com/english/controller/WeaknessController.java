package com.english.controller;

import com.english.dto.Result;
import com.english.entity.WeaknessAnalysis;
import com.english.service.WeaknessAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/weakness")
@PreAuthorize("hasRole('STUDENT')")
public class WeaknessController {

    @Autowired
    private WeaknessAnalysisService weaknessAnalysisService;

    /**
     * 触发薄弱点分析
     */
    @PostMapping("/analyze")
    public Result<String> analyzeWeakness(@RequestParam Long studentId) {
        try {
            weaknessAnalysisService.analyzeStudentWeakness(studentId);
            return Result.success("薄弱点分析完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取薄弱点列表
     */
    @GetMapping("/list")
    public Result<List<WeaknessAnalysis>> getWeaknessList(@RequestParam Long studentId) {
        try {
            List<WeaknessAnalysis> weaknessList =
                weaknessAnalysisService.getStudentWeaknessList(studentId);
            return Result.success(weaknessList);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取详细报告
     */
    @GetMapping("/report")
    public Result<Map<String, Object>> getWeaknessReport(@RequestParam Long studentId) {
        try {
            Map<String, Object> report = weaknessAnalysisService.getWeaknessReport(studentId);
            return Result.success(report);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取最薄弱知识点
     */
    @GetMapping("/top")
    public Result<List<WeaknessAnalysis>> getTopWeaknesses(
            @RequestParam Long studentId,
            @RequestParam(defaultValue = "5") int limit) {

        try {
            List<WeaknessAnalysis> topWeaknesses =
                weaknessAnalysisService.getTopWeaknesses(studentId, limit);
            return Result.success(topWeaknesses);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
