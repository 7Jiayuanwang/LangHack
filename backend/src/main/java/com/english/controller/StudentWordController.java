package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(StudentWordController.class);

    @Autowired
    private VocabularyBooksService vocabularyBooksService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private WordService wordsService;

    @Autowired
    private UserService userService;

    @Autowired
    private WordNetService wordNetService;

    /**
     * 获取单词详细信息（支持点击查词）
     */
    @GetMapping("/{wordId}/detail")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getWordDetail(
            @PathVariable Long wordId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> detail = vocabularyBooksService.getWordDetailWithLearningState(studentId, wordId);
            if (detail == null) {
                return Result.error("单词不存在");
            }

            // 记录单词查看记录
            learningRecordsService.recordWordView(studentId, wordId);

            return Result.success(detail);
        } catch (Exception e) {
            logger.error("获取单词详情失败", e);

            return Result.error("获取单词详情失败，请稍后重试");
        }
    }

    /**
     * 添加单词到生词本
     */
    @PostMapping("/vocabulary")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> addToVocabulary(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object wordIdObj = request.get("wordId");
            if (wordIdObj == null) {
                return Result.error("wordId不能为空");
            }
            Long wordId = Long.valueOf(wordIdObj.toString());

            // 检查是否已存在
            if (vocabularyBooksService.existsByStudentAndWord(studentId, wordId)) {
                // 如果已存在，更新掌握程度为学习中
                vocabularyBooksService.updateMasteryLevel(studentId, wordId, "LEARNING", null);
                // 记录学习记录
                learningRecordsService.recordVocabularyAdd(studentId, wordId);
                return Result.success("单词学习状态已更新");
            }

            // 添加到生词本，并直接设置为学习中状态
            vocabularyBooksService.addToVocabularyWithLevel(studentId, wordId, "LEARNING");

            // 记录学习记录
            learningRecordsService.recordVocabularyAdd(studentId, wordId);

            return Result.success("已添加到生词本");
        } catch (Exception e) {
            logger.error("添加生词失败", e);

            return Result.error("添加生词失败，请稍后重试");
        }
    }

    /**
     * 通过单词字符串自动添加到生词本（支持自动创建新词汇）
     * 如果数据库中不存在该词，则从ECDICT获取信息并自动创建，然后加入生词本
     */
    @PostMapping("/vocabulary/by-word")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> addToVocabularyByWord(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object wordObj = request.get("word");
            if (wordObj == null) {
                return Result.error("单词不能为空");
            }
            String word = wordObj.toString().trim().toLowerCase();

            if (word.isEmpty()) {
                return Result.error("单词不能为空");
            }

            // 第一步：查询或创建单词（自动创建功能）
            Map<String, Object> wordResult = wordNetService.getOrCreateWord(word);

            if (wordResult.get("found") == null || !Boolean.TRUE.equals(wordResult.get("found"))) {
                logger.warn("单词查询/创建失败: {}", wordResult.get("error"));
                return Result.error("无法获取或创建单词信息，请稍后重试");
            }

            Object wordIdObj = wordResult.get("wordId");
            if (wordIdObj == null) {
                return Result.error("单词ID缺失，无法添加到生词本");
            }
            Long wordId = Long.valueOf(wordIdObj.toString());

            // 第二步：检查是否已在生词本中
            if (vocabularyBooksService.existsByStudentAndWord(studentId, wordId)) {
                // 如果已存在，更新掌握程度为学习中
                vocabularyBooksService.updateMasteryLevel(studentId, wordId, "LEARNING", null);
                learningRecordsService.recordVocabularyAdd(studentId, wordId);

                wordResult.put("vocabularyAction", "updated");
                wordResult.put("message", "单词学习状态已更新");
                return Result.success(wordResult);
            }

            // 第三步：添加到生词本，并设置为学习中状态
            vocabularyBooksService.addToVocabularyWithLevel(studentId, wordId, "LEARNING");

            // 记录学习记录
            learningRecordsService.recordVocabularyAdd(studentId, wordId);

            // 返回完整的词汇信息和操作结果
            wordResult.put("vocabularyAction", "added");
            wordResult.put("message",
                    Boolean.TRUE.equals(wordResult.get("created")) ? "新词汇已创建并添加到生词本" : "已添加到生词本");

            return Result.success(wordResult);

        } catch (Exception e) {
            logger.error("添加生词失败", e);

            return Result.error("添加生词失败，请稍后重试");
        }
    }

    /**
     * 获取生词本列表
     */
    @GetMapping("/vocabulary")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
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
            Long studentId = getCurrentStudentId(authentication);

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
            logger.error("获取生词本失败", e);

            return Result.error("获取生词本失败，请稍后重试");
        }
    }

    /**
     * 更新生词掌握程度
     */
    @PutMapping("/vocabulary/{wordId}/mastery")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> updateMasteryLevel(
            @PathVariable Long wordId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);
            String masteryLevel = (String) request.get("masteryLevel");
            Object isCorrectObj = request.get("isCorrect");

            // 验证masteryLevel枚举值
            if (masteryLevel == null || !java.util.Set.of("NEW", "LEARNING", "REVIEWING", "MASTERED").contains(masteryLevel)) {
                return Result.error("无效的掌握程度");
            }

            // 更新生词本记录
            vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, request);

            // 记录复习记录
            if (isCorrectObj != null) {
                boolean isCorrect = Boolean.TRUE.equals(isCorrectObj);
                learningRecordsService.recordWordReview(studentId, wordId, isCorrect);
            }

            return Result.success("掌握程度已更新");
        } catch (Exception e) {
            logger.error("更新掌握程度失败", e);

            return Result.error("更新掌握程度失败，请稍后重试");
        }
    }

    /**
     * 开始单词学习会话
     */
    @PostMapping("/study/session")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> startStudySession(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);
            Object unitIdObj = request.get("unitId");
            if (unitIdObj == null) {
                return Result.error("unitId不能为空");
            }
            Long unitId = Long.valueOf(unitIdObj.toString());

            Map<String, Object> session = vocabularyBooksService.createStudySession(studentId, unitId);

            return Result.success(session);
        } catch (Exception e) {
            logger.error("创建学习会话失败", e);

            return Result.error("创建学习会话失败，请稍后重试");
        }
    }

    /**
     * 提交单词学习结果
     */
    @PostMapping("/study/submit")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> submitStudyResult(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            vocabularyBooksService.processStudyResults(studentId, request);

            return Result.success("学习结果已记录");
        } catch (Exception e) {
            logger.error("提交学习结果失败", e);

            return Result.error("提交学习结果失败，请稍后重试");
        }
    }

    /**
     * 获取单词学习统计
     */
    @GetMapping("/study/stats")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getWordStudyStats(
            @RequestParam(required = false) String period,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> stats = vocabularyBooksService.getWordStudyStats(studentId, period);

            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取学习统计失败", e);

            return Result.error("获取学习统计失败，请稍后重试");
        }
    }

    /**
     * 批量操作生词本
     */
    @PostMapping("/vocabulary/batch")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> batchOperateVocabulary(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);
            String operation = (String) request.get("operation");
            Object wordIdsObj = request.get("wordIds");

            if (operation == null || operation.isEmpty()) {
                return Result.error("请指定操作类型");
            }

            if (wordIdsObj == null) {
                return Result.error("请选择要操作的单词");
            }

            if (!(wordIdsObj instanceof java.util.List)) {
                return Result.error("wordIds 格式不正确，应为数组");
            }

            @SuppressWarnings("unchecked")
            java.util.List<Long> wordIds = ((java.util.List<?>) wordIdsObj).stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(java.util.stream.Collectors.toList());

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
            logger.error("批量操作失败", e);

            return Result.error("批量操作失败，请稍后重试");
        }
    }

    /**
     * 获取当前学生ID
     */
    private Long getCurrentStudentId(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new com.english.exception.BusinessException("用户不存在");
        }
        return user.getId();
    }
}