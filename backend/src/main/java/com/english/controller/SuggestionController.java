package com.english.controller;

import com.english.dto.Result;
import com.english.entity.LearningSuggestion;
import com.english.service.LearningSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/suggestions")
@PreAuthorize("hasRole('STUDENT')")
public class SuggestionController {

    @Autowired
    private LearningSuggestionService learningSuggestionService;

    /**
     * 生成学习建议
     */
    @PostMapping("/generate")
    public Result<String> generateSuggestions(@RequestParam Long studentId) {
        try {
            learningSuggestionService.generateSuggestions(studentId);
            return Result.success("学习建议生成成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取我的建议列表
     */
    @GetMapping
    public Result<List<LearningSuggestion>> getMySuggestions(@RequestParam Long studentId) {
        try {
            List<LearningSuggestion> suggestions =
                learningSuggestionService.getStudentSuggestions(studentId);
            return Result.success(suggestions);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 标记建议完成
     */
    @PutMapping("/{id}/complete")
    public Result<String> completeSuggestion(@PathVariable Long id) {
        try {
            learningSuggestionService.completeSuggestion(id);
            return Result.success("建议已标记为完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
