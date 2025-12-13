package com.english.controller;

import com.english.constants.RoleConstants;
import com.english.dto.Result;
import com.english.entity.WeaknessAnalysis;
import com.english.service.WeaknessAnalysisService;
import com.english.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/weakness")
@PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
public class WeaknessController {

    @Autowired
    private WeaknessAnalysisService weaknessAnalysisService;

    /**
     * 触发薄弱点分析
     */
    @PostMapping("/analyze")
    public Result<String> analyzeWeakness(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
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
    public Result<List<WeaknessAnalysis>> getWeaknessList(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
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
    public Result<Map<String, Object>> getWeaknessReport(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
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
            Authentication authentication,
            @RequestParam(defaultValue = "5") int limit) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            List<WeaknessAnalysis> topWeaknesses =
                weaknessAnalysisService.getTopWeaknesses(studentId, limit);
            return Result.success(topWeaknesses);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
