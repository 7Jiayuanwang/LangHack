package com.english.controller;

import com.english.constants.RoleConstants;
import com.english.dto.Result;
import com.english.entity.LearningSuggestion;
import com.english.service.LearningSuggestionService;
import com.english.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/suggestions")
@PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
public class SuggestionController {

    @Autowired
    private LearningSuggestionService learningSuggestionService;

    /**
     * 生成学习建议
     */
    @PostMapping("/generate")
    public Result<String> generateSuggestions(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
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
    public Result<List<LearningSuggestion>> getMySuggestions(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
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
    public Result<String> completeSuggestion(@PathVariable Long id, Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            learningSuggestionService.completeSuggestion(id, studentId);
            return Result.success("建议已标记为完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
