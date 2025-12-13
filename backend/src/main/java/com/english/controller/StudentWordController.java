package com.english.controller;

import com.english.constants.RoleConstants;
import com.english.dto.Result;
import com.english.service.*;
import com.english.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 学生单词学习控制器
 */
@RestController
@RequestMapping("/api/student/words")
public class StudentWordController {

    @Autowired
    private VocabularyBooksService vocabularyBooksService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private WordService wordsService;

    /**
     * 获取单词详细信息（支持点击查词）
     */
    @GetMapping("/{wordId}/detail")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getWordDetail(
            @PathVariable Long wordId,
            Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Map<String, Object> detail = vocabularyBooksService.getWordDetailWithLearningState(studentId, wordId);

            // 记录单词查看记录
            learningRecordsService.recordWordView(studentId, wordId);

            return Result.success(detail);
        } catch (Exception e) {
            return Result.error("获取单词详情失败：" + e.getMessage());
        }
    }

    /**
     * 添加单词到生词本
     */
    @PostMapping("/vocabulary")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<String> addToVocabulary(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Object wordIdObj = request.get("wordId");
            if (wordIdObj == null) {
                return Result.error("单词ID不能为空");
            }
            Long wordId = Long.valueOf(wordIdObj.toString());

            // 检查是否已存在
            if (vocabularyBooksService.existsByStudentAndWord(studentId, wordId)) {
                return Result.error("单词已在生词本中");
            }

            // 添加到生词本
            vocabularyBooksService.addToVocabulary(studentId, wordId);

            // 记录学习记录
            learningRecordsService.recordVocabularyAdd(studentId, wordId);

            return Result.success("已添加到生词本");
        } catch (Exception e) {
            return Result.error("添加生词失败：" + e.getMessage());
        }
    }

    /**
     * 获取生词本列表
     */
    @GetMapping("/vocabulary")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getVocabularyList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String masteryLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String courseId,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("page", page);
            params.put("size", size);
            params.put("masteryLevel", masteryLevel);
            params.put("keyword", keyword);
            params.put("sortBy", sortBy);
            params.put("difficulty", difficulty);
            params.put("courseId", courseId);

            Map<String, Object> result = vocabularyBooksService.getVocabularyWithPagination(params);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取生词本失败：" + e.getMessage());
        }
    }

    /**
     * 更新生词掌握程度
     */
    @PutMapping("/vocabulary/{wordId}/mastery")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<String> updateMasteryLevel(
            @PathVariable Long wordId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            String masteryLevel = (String) request.get("masteryLevel");
            if (masteryLevel == null || masteryLevel.trim().isEmpty()) {
                return Result.error("掌握程度不能为空");
            }
            Boolean isCorrect = (Boolean) request.getOrDefault("isCorrect", false);

            // 更新生词本记录
            vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel);

            // 记录复习记录
            if (isCorrect != null) {
                learningRecordsService.recordWordReview(studentId, wordId, isCorrect);
            }

            return Result.success("掌握程度已更新");
        } catch (Exception e) {
            return Result.error("更新掌握程度失败：" + e.getMessage());
        }
    }

    /**
     * 开始单词学习会话
     */
    @PostMapping("/study/session")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> startStudySession(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Object unitIdObj = request.get("unitId");
            if (unitIdObj == null) {
                return Result.error("单元ID不能为空");
            }
            Long unitId = Long.valueOf(unitIdObj.toString());

            Map<String, Object> session = vocabularyBooksService.createStudySession(studentId, unitId);

            return Result.success(session);
        } catch (Exception e) {
            return Result.error("创建学习会话失败：" + e.getMessage());
        }
    }

    /**
     * 提交单词学习结果
     */
    @PostMapping("/study/submit")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<String> submitStudyResult(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            vocabularyBooksService.processStudyResults(studentId, request);

            return Result.success("学习结果已记录");
        } catch (Exception e) {
            return Result.error("提交学习结果失败：" + e.getMessage());
        }
    }

    /**
     * 获取单词学习统计
     */
    @GetMapping("/study/stats")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getWordStudyStats(
            @RequestParam(required = false) String period,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Map<String, Object> stats = vocabularyBooksService.getWordStudyStats(studentId, period);

            return Result.success(stats);
        } catch (Exception e) {
            return Result.error("获取学习统计失败：" + e.getMessage());
        }
    }

    /**
     * 批量操作生词本
     */
    @PostMapping("/vocabulary/batch")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<String> batchOperateVocabulary(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            String operation = (String) request.get("operation");
            Object wordIdsObj = request.get("wordIds");

            if (wordIdsObj == null) {
                return Result.error("请选择要操作的单词");
            }

            @SuppressWarnings("unchecked")
            java.util.List<Long> wordIds = (java.util.List<Long>) wordIdsObj;

            switch (operation) {
                case "delete":
                    vocabularyBooksService.batchDeleteVocabulary(studentId, wordIds);
                    return Result.success("批量删除成功");
                case "mark_mastered":
                    vocabularyBooksService.batchUpdateMasteryLevel(studentId, wordIds, "MASTERED");
                    return Result.success("批量标记成功");
                case "mark_learning":
                    vocabularyBooksService.batchUpdateMasteryLevel(studentId, wordIds, "LEARNING");
                    return Result.success("批量标记成功");
                default:
                    return Result.error("不支持的操作类型");
            }
        } catch (Exception e) {
            return Result.error("批量操作失败：" + e.getMessage());
        }
    }

}