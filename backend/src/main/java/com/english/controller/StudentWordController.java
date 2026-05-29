package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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

    @Autowired
    private ECDICTService ecdictService;

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

            // 参数校验
            if (wordId == null || wordId <= 0) {
                return Result.error("单词ID无效");
            }

            Map<String, Object> detail = vocabularyBooksService.getWordDetailWithLearningState(studentId, wordId);
            if (detail == null || detail.isEmpty()) {
                return Result.error("单词不存在");
            }

            // 记录单词查看记录
            learningRecordsService.recordWordView(studentId, wordId);

            return Result.success(detail);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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
            Long wordId;
            try {
                wordId = Long.valueOf(wordIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("wordId格式无效");
            }

            if (wordId <= 0) {
                return Result.error("wordId必须大于0");
            }

            // 检查是否已存在
            if (vocabularyBooksService.existsByStudentAndWord(studentId, wordId)) {
                // 单词已在生词本中，保持现有状态不变
                learningRecordsService.recordVocabularyAdd(studentId, wordId);
                return Result.success("单词已在生词本中");
            }

            // 添加到生词本，新词默认状态为 NEW
            vocabularyBooksService.addToVocabularyWithLevel(studentId, wordId, "NEW");

            // 记录学习记录
            learningRecordsService.recordVocabularyAdd(studentId, wordId);

            return Result.success("已添加到生词本");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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

            if (word.length() > 100) {
                return Result.error("单词长度不能超过100个字符");
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
            Long wordId;
            try {
                wordId = Long.valueOf(wordIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("单词ID格式无效");
            }

            if (wordId <= 0) {
                return Result.error("单词ID无效");
            }

            // 第二步：检查是否已在生词本中
            if (vocabularyBooksService.existsByStudentAndWord(studentId, wordId)) {
                // 单词已在生词本中，保持现有状态不变
                learningRecordsService.recordVocabularyAdd(studentId, wordId);

                wordResult.put("vocabularyAction", "exists");
                wordResult.put("message", "单词已在生词本中");
                return Result.success(wordResult);
            }

            // 第三步：添加到生词本，新词默认状态为 NEW
            vocabularyBooksService.addToVocabularyWithLevel(studentId, wordId, "NEW");

            // 记录学习记录
            learningRecordsService.recordVocabularyAdd(studentId, wordId);

            // 返回完整的词汇信息和操作结果
            wordResult.put("vocabularyAction", "added");
            wordResult.put("message",
                    Boolean.TRUE.equals(wordResult.get("created")) ? "新词汇已创建并添加到生词本" : "已添加到生词本");

            return Result.success(wordResult);

        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String sourceType,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("page", page);
            params.put("size", size);
            params.put("masteryLevel", masteryLevel);
            params.put("keyword", keyword);
            params.put("sortBy", sortBy);
            params.put("difficulty", difficulty);
            params.put("courseId", courseId);
            params.put("sourceType", sourceType);

            Map<String, Object> result = vocabularyBooksService.getVocabularyWithPagination(params);

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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

            // 参数校验
            if (wordId == null || wordId <= 0) {
                return Result.error("单词ID无效");
            }

            Object masteryLevelObj = request.get("masteryLevel");
            if (masteryLevelObj == null) {
                return Result.error("掌握程度不能为空");
            }
            String masteryLevel = masteryLevelObj.toString().trim();
            Object isCorrectObj = request.get("isCorrect");

            // 验证masteryLevel枚举值
            if (masteryLevel.isEmpty() || !java.util.Set.of("NEW", "LEARNING", "FAMILIAR", "MASTERED").contains(masteryLevel)) {
                return Result.error("无效的掌握程度");
            }

            // 更新生词本记录
            vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, request);

            // 可选：把记录升级为 ARTICLE 来源
            // 用于"在生词本里把已掌握的词重置为学习中"场景：让这个词出现在"每日背诵-加入文章生词"列表里
            if (Boolean.TRUE.equals(request.get("upgradeSourceToArticle"))) {
                vocabularyBooksService.upgradeSourceTypeToArticle(studentId, wordId);
            }

            // 记录复习记录
            if (isCorrectObj != null) {
                boolean isCorrect = Boolean.TRUE.equals(isCorrectObj);
                learningRecordsService.recordWordReview(studentId, wordId, isCorrect);
            }

            return Result.success("掌握程度已更新");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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

            Long unitId;
            try {
                unitId = Long.valueOf(unitIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("unitId格式无效");
            }

            if (unitId <= 0) {
                return Result.error("unitId必须大于0");
            }

            Map<String, Object> session = vocabularyBooksService.createStudySession(studentId, unitId);

            return Result.success(session);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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

            if (request == null) {
                return Result.error("请求体不能为空");
            }

            vocabularyBooksService.processStudyResults(studentId, request);

            return Result.success("学习结果已记录");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
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
            // 参数校验：operation
            Object operationObj = request.get("operation");
            if (operationObj == null) {
                return Result.error("请指定操作类型");
            }
            String operation = operationObj.toString().trim();
            if (operation.isEmpty()) {
                return Result.error("请指定操作类型");
            }

            // 参数校验：wordIds
            Object wordIdsObj = request.get("wordIds");

            if (wordIdsObj == null) {
                return Result.error("请选择要操作的单词");
            }

            if (!(wordIdsObj instanceof java.util.List)) {
                return Result.error("wordIds 格式不正确，应为数组");
            }

            @SuppressWarnings("unchecked")
            java.util.List<Long> wordIds = ((java.util.List<?>) wordIdsObj).stream()
                    .map(id -> {
                        try {
                            return Long.valueOf(id.toString());
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid wordId in batch: {}", id);
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

            if (wordIds.isEmpty()) {
                return Result.error("没有有效的单词ID");
            }

            // 限制批量大小，防止 DoS
            if (wordIds.size() > 200) {
                return Result.error("批量操作的单词数量不能超过200");
            }

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
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("批量操作失败", e);

            return Result.error("批量操作失败，请稍后重试");
        }
    }

    /**
     * 获取MCQ测验干扰项（从ECDICT词库生成）
     */
    @GetMapping("/quiz-options")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<List<String>> getQuizDistractors(
            @RequestParam String word,
            @RequestParam(defaultValue = "3") int count,
            Authentication authentication) {
        try {
            getCurrentStudentId(authentication); // 验证用户身份

            if (word == null || word.trim().isEmpty()) {
                return Result.error("单词不能为空");
            }
            if (word.trim().length() > 100) {
                return Result.error("单词长度不能超过100");
            }
            count = Math.max(1, Math.min(count, 10));

            List<String> distractors = ecdictService.generateQuizDistractors(word.trim(), count);
            return Result.success(distractors);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取MCQ干扰项失败", e);
            return Result.error("获取干扰项失败，请稍后重试");
        }
    }

    /**
     * 获取MCQ测验完整数据（正确答案来自ECDICT词库 + 干扰项）
     * 确保正确答案和干扰项风格一致，都来自同一词库
     */
    @GetMapping("/quiz-data")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getQuizData(
            @RequestParam String word,
            @RequestParam(defaultValue = "3") int count,
            Authentication authentication) {
        try {
            getCurrentStudentId(authentication);

            if (word == null || word.trim().isEmpty()) {
                return Result.error("单词不能为空");
            }
            if (word.trim().length() > 100) {
                return Result.error("单词长度不能超过100");
            }
            count = Math.max(1, Math.min(count, 10));

            Map<String, Object> quizData = ecdictService.generateQuizData(word.trim(), count);
            return Result.success(quizData);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取MCQ完整选项数据失败", e);
            return Result.error("获取选项数据失败，请稍后重试");
        }
    }

    /**
     * 获取当前学生ID
     */
    private Long getCurrentStudentId(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user.getId();
    }
}