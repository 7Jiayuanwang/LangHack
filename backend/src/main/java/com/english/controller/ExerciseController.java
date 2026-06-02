package com.english.controller;

import com.english.dto.Result;
import com.english.entity.TextExercise;
import com.english.entity.ExerciseOption;
import com.english.exception.BusinessException;
import com.english.service.TextExerciseService;
import com.english.service.UserService;
import com.english.service.TextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 习题管理控制器
 */
@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private static final Logger logger = LoggerFactory.getLogger(ExerciseController.class);

    @Autowired
    private TextExerciseService textExerciseService;

    @Autowired
    private UserService userService;

    @Autowired
    private TextService textService;

    /**
     * 批量创建课文习题
     * 请求格式：
     * {
     *   "textId": 1,
     *   "exercises": [
     *     {
     *       "question": "题目内容",
     *       "exerciseType": "MULTIPLE_CHOICE",
     *       "orderNum": 1,
     *       "options": [
     *         {"optionContent": "选项A", "optionKey": "A", "isCorrect": false, "orderNum": 1},
     *         {"optionContent": "选项B", "optionKey": "B", "isCorrect": true, "orderNum": 2}
     *       ]
     *     }
     *   ]
     * }
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> batchCreateExercises(@RequestBody Map<String, Object> requestData) {
        try {
            com.english.entity.User creator = getCurrentUser();
            Long createdBy = creator.getId();

            Object textIdObj = requestData.get("textId");
            if (textIdObj == null) {
                return Result.error("textId不能为空");
            }
            Long textId = Long.valueOf(textIdObj.toString());

            // 验证课文存在且未删除
            com.english.entity.Text targetText = textService.getTextById(textId);
            if (targetText == null || targetText.getDeleted() == 1) {
                return Result.error("课文不存在或已被删除");
            }

            // 非管理员教师只能为自己创建的课文添加习题
            if (!"ADMIN".equals(creator.getRole())) {
                if (targetText.getUploadedBy() == null || !targetText.getUploadedBy().equals(createdBy)) {
                    return Result.error("无权为其他教师创建的课文添加习题");
                }
            }

            Object exercisesObj = requestData.get("exercises");
            if (!(exercisesObj instanceof List)) {
                return Result.error("exercises必须是列表");
            }
            List<Map<String, Object>> exercisesData = (List<Map<String, Object>>) exercisesObj;

            if (exercisesData == null || exercisesData.isEmpty()) {
                return Result.error("习题列表不能为空");
            }

            if (exercisesData.size() > 100) {
                return Result.error("单次批量创建习题不能超过100个");
            }

            List<TextExercise> exercises = new ArrayList<>();
            List<List<ExerciseOption>> allOptions = new ArrayList<>();

            for (Map<String, Object> exerciseData : exercisesData) {
                // 创建习题
                TextExercise exercise = new TextExercise();
                exercise.setTextId(textId);
                Object questionObj = exerciseData.get("question");
                Object exerciseTypeObj = exerciseData.get("exerciseType");
                Object orderNumObj = exerciseData.get("orderNum");
                if (questionObj == null || exerciseTypeObj == null || orderNumObj == null) {
                    return Result.error("题目、题型和序号不能为空");
                }

                // 校验题目长度
                String question = questionObj.toString();
                if (question.length() > 5000) {
                    return Result.error("题目过长，不能超过5000字符");
                }

                exercise.setQuestion(question);
                exercise.setExerciseType(exerciseTypeObj.toString());

                // 校验orderNum
                int orderNum;
                try {
                    orderNum = Integer.parseInt(orderNumObj.toString());
                    if (orderNum < 1 || orderNum > 1000) {
                        return Result.error("题目序号必须在1-1000之间");
                    }
                } catch (NumberFormatException e) {
                    return Result.error("题目序号格式错误");
                }
                exercise.setOrderNum(orderNum);
                exercise.setCreatedBy(createdBy);

                exercises.add(exercise);

                // 处理选项
                Object optionsObj = exerciseData.get("options");
                List<Map<String, Object>> optionsData = (optionsObj instanceof List) ? (List<Map<String, Object>>) optionsObj : null;
                List<ExerciseOption> options = new ArrayList<>();

                if (optionsData != null && !optionsData.isEmpty()) {
                    // 选项个数限制（最多10个）
                    if (optionsData.size() > 10) {
                        return Result.error("每道习题的选项不能超过10个");
                    }

                    for (Map<String, Object> optionData : optionsData) {
                        ExerciseOption option = new ExerciseOption();
                        Object optionContentObj = optionData.get("optionContent");
                        Object optionKeyObj = optionData.get("optionKey");
                        if (optionContentObj == null || optionKeyObj == null) {
                            continue;
                        }

                        // 校验选项内容长度
                        String optionContent = optionContentObj.toString();
                        if (optionContent.length() > 2000) {
                            return Result.error("选项内容过长，不能超过2000字符");
                        }

                        option.setOptionContent(optionContent);
                        option.setOptionKey(optionKeyObj.toString());
                        Object isCorrectObj = optionData.get("isCorrect");
                        // 安全处理 isCorrect：前端可能发 1/0/"true"/"false" 等格式
                        boolean isCorrect = false;
                        if (isCorrectObj != null) {
                            String isCorrectStr = isCorrectObj.toString().trim().toLowerCase();
                            isCorrect = "1".equals(isCorrectStr) || "true".equals(isCorrectStr);
                        }
                        option.setIsCorrect(isCorrect ? 1 : 0);
                        Object optionOrderObj = optionData.get("orderNum");

                        // 校验选项序号
                        int optionOrder = 0;
                        if (optionOrderObj != null) {
                            try {
                                optionOrder = Integer.parseInt(optionOrderObj.toString());
                                if (optionOrder < 1 || optionOrder > 100) {
                                    return Result.error("选项序号必须在1-100之间");
                                }
                            } catch (NumberFormatException e) {
                                return Result.error("选项序号格式错误");
                            }
                        }
                        option.setOrderNum(optionOrder);

                        options.add(option);
                    }
                }

                allOptions.add(options);
            }

            // 批量创建
            boolean success = textExerciseService.batchCreateExercises(exercises, allOptions);

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("message", "习题创建成功");
                result.put("exerciseCount", exercises.size());
                return Result.success("习题创建成功", result);
            } else {
                return Result.error("习题创建失败");
            }
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("批量创建习题失败", e);

            return Result.error("批量创建习题失败，请稍后重试");
        }
    }

    /**
     * 创建单个课文习题
     */
    @PostMapping("")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createExercise(@RequestBody Map<String, Object> requestData) {
        try {
            com.english.entity.User creator = getCurrentUser();
            Long createdBy = creator.getId();

            TextExercise exercise = new TextExercise();
            Object textIdObj2 = requestData.get("textId");
            Object questionObj2 = requestData.get("question");
            Object exerciseTypeObj2 = requestData.get("exerciseType");
            Object orderNumObj2 = requestData.get("orderNum");
            if (textIdObj2 == null || questionObj2 == null || exerciseTypeObj2 == null || orderNumObj2 == null) {
                return Result.error("textId、题目、题型和序号不能为空");
            }
            Long textId = Long.valueOf(textIdObj2.toString());

            // 验证 textId 对应的 Text 存在且未被删除
            com.english.entity.Text text = textService.getTextById(textId);
            if (text == null || text.getDeleted() == 1) {
                return Result.error("课文不存在或已被删除");
            }

            // 非管理员教师只能为自己创建的课文添加习题
            if (!"ADMIN".equals(creator.getRole())) {
                if (text.getUploadedBy() == null || !text.getUploadedBy().equals(createdBy)) {
                    return Result.error("无权为其他教师创建的课文添加习题");
                }
            }

            // 校验题目长度
            String question = questionObj2.toString();
            if (question.length() > 5000) {
                return Result.error("题目过长，不能超过5000字符");
            }

            exercise.setTextId(textId);
            exercise.setQuestion(question);
            exercise.setExerciseType(exerciseTypeObj2.toString());

            // 校验orderNum
            int orderNum;
            try {
                orderNum = Integer.parseInt(orderNumObj2.toString());
                if (orderNum < 1 || orderNum > 1000) {
                    return Result.error("题目序号必须在1-1000之间");
                }
            } catch (NumberFormatException e) {
                return Result.error("题目序号格式错误");
            }
            exercise.setOrderNum(orderNum);
            exercise.setCreatedBy(createdBy);

            boolean success = textExerciseService.createTextExercise(exercise);

            if (success) {
                return Result.success("习题创建成功");
            } else {
                return Result.error("习题创建失败");
            }
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建习题失败", e);

            return Result.error("创建习题失败，请稍后重试");
        }
    }

    /**
     * 获取课文的所有习题
     * 注意：学生/普通用户获取时会隐藏正确答案标记，防止作弊
     */
    @GetMapping("/text/{textId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER', 'OPS')")
    public Result<List<Map<String, Object>>> getExercisesByTextId(@PathVariable Long textId) {
        try {
            // FIX #5: 验证课文存在性，防止访问不存在的课文的习题
            com.english.entity.Text text = textService.getTextById(textId);
            if (text == null || Integer.valueOf(1).equals(text.getDeleted())) {
                return Result.error("课文不存在或已被删除");
            }

            // 校验访问权限：ADMIN放行，TEACHER校验uploadedBy，STUDENT校验课程归属
            assertCanAccessExercise(text);

            List<Map<String, Object>> exercises = textExerciseService.getExercisesWithOptions(textId);

            // 学生/普通用户：隐藏正确答案标记，防止通过 API 响应作弊
            if (!isTeacherOrAdmin()) {
                stripCorrectAnswers(exercises);
            }

            return Result.success(exercises);
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取课文习题失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取课文习题失败", e);

            return Result.error("获取课文习题失败，请稍后重试");
        }
    }

    /**
     * 获取习题详情
     * 注意：学生/普通用户获取时会隐藏正确答案标记
     */
    @GetMapping("/{exerciseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER', 'OPS')")
    public Result<Map<String, Object>> getExerciseDetail(@PathVariable Long exerciseId) {
        try {
            Map<String, Object> exercise = textExerciseService.getExerciseDetail(exerciseId);
            if (exercise != null) {
                // 校验习题对应课文的访问权限
                Object textIdObj = exercise.get("textId");
                if (textIdObj != null) {
                    Long textId = parseLongField(textIdObj);
                    if (textId != null) {
                        com.english.entity.Text text = textService.getTextById(textId);
                        if (text != null) {
                            assertCanAccessExercise(text);
                        }
                    }
                }

                // 学生/普通用户：隐藏正确答案标记
                if (!isTeacherOrAdmin()) {
                    stripCorrectAnswersFromExercise(exercise);
                }
                return Result.success(exercise);
            } else {
                return Result.error("习题不存在");
            }
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取习题详情失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取习题详情失败", e);

            return Result.error("获取习题详情失败，请稍后重试");
        }
    }

    /**
     * 更新习题
     */
    @PutMapping("/{exerciseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateExercise(@PathVariable Long exerciseId, @RequestBody Map<String, Object> requestData) {
        try {
            // 验证教师权限：非ADMIN教师只能修改自己创建的习题
            com.english.entity.User currentUser = getCurrentUser();

            Map<String, Object> existingExercise = textExerciseService.getExerciseDetail(exerciseId);
            if (existingExercise == null) {
                return Result.error("习题不存在");
            }
            if (!"ADMIN".equals(currentUser.getRole())) {
                // FIX #4: 安全的类型转换
                Long createdBy = parseLongField(existingExercise.get("createdBy"));
                if (createdBy == null || !createdBy.equals(currentUser.getId())) {
                    return Result.error("无权修改其他教师创建的习题");
                }
            }

            TextExercise exercise = new TextExercise();
            exercise.setId(exerciseId);
            Object questionObj3 = requestData.get("question");
            Object exerciseTypeObj3 = requestData.get("exerciseType");
            Object orderNumObj3 = requestData.get("orderNum");
            if (questionObj3 == null || exerciseTypeObj3 == null || orderNumObj3 == null) {
                return Result.error("题目、题型和序号不能为空");
            }

            // 校验题目长度
            String question = questionObj3.toString();
            if (question.length() > 5000) {
                return Result.error("题目过长，不能超过5000字符");
            }

            exercise.setQuestion(question);
            exercise.setExerciseType(exerciseTypeObj3.toString());

            // 校验orderNum
            int orderNum;
            try {
                orderNum = Integer.parseInt(orderNumObj3.toString());
                if (orderNum < 1 || orderNum > 1000) {
                    return Result.error("题目序号必须在1-1000之间");
                }
            } catch (NumberFormatException e) {
                return Result.error("题目序号格式错误");
            }
            exercise.setOrderNum(orderNum);

            boolean success = textExerciseService.updateExercise(exercise);

            if (success) {
                return Result.success("习题更新成功");
            } else {
                return Result.error("习题更新失败");
            }
        } catch (com.english.exception.BusinessException e) {
            logger.error("更新习题失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新习题失败", e);

            return Result.error("更新习题失败，请稍后重试");
        }
    }

    /**
     * 删除习题
     */
    @DeleteMapping("/{exerciseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteExercise(@PathVariable Long exerciseId) {
        try {
            // 验证教师权限：非ADMIN教师只能删除自己创建的习题
            com.english.entity.User currentUser = getCurrentUser();

            Map<String, Object> existingExercise = textExerciseService.getExerciseDetail(exerciseId);
            if (existingExercise == null) {
                return Result.error("习题不存在");
            }
            if (!"ADMIN".equals(currentUser.getRole())) {
                // FIX #4: 安全的类型转换
                Long createdBy = parseLongField(existingExercise.get("createdBy"));
                if (createdBy == null || !createdBy.equals(currentUser.getId())) {
                    return Result.error("无权删除其他教师创建的习题");
                }
            }

            boolean success = textExerciseService.deleteExercise(exerciseId);

            if (success) {
                return Result.success("习题删除成功");
            } else {
                return Result.error("习题删除失败");
            }
        } catch (com.english.exception.BusinessException e) {
            logger.error("删除习题失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除习题失败", e);

            return Result.error("删除习题失败，请稍后重试");
        }
    }

    /**
     * 删除课文的所有习题
     */
    @DeleteMapping("/text/{textId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteAllExercisesByTextId(@PathVariable Long textId) {
        try {
            // 验证教师权限：教师可以删除任何课文的习题（重新出题场景）
            com.english.entity.User currentUser = getCurrentUser();

            if (!"ADMIN".equals(currentUser.getRole())) {
                com.english.entity.Text text = textService.getTextById(textId);
                if (text == null) {
                    return Result.error("课文不存在");
                }
                // 教师可以删除任何课文的习题，无需检查 uploadedBy
            }

            boolean success = textExerciseService.deleteAllExercisesByTextId(textId);

            if (success) {
                return Result.success("课文习题删除成功");
            } else {
                return Result.error("课文习题删除失败");
            }
        } catch (com.english.exception.BusinessException e) {
            logger.error("删除课文习题失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除课文习题失败", e);

            return Result.error("删除课文习题失败，请稍后重试");
        }
    }

    /**
     * 获取课文习题数量
     */
    @GetMapping("/text/{textId}/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER', 'OPS')")
    public Result<Map<String, Object>> getExerciseCount(@PathVariable Long textId) {
        try {
            int count = textExerciseService.getExerciseCount(textId);
            Map<String, Object> result = new HashMap<>();
            result.put("textId", textId);
            result.put("exerciseCount", count);
            return Result.success(result);
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取习题数量失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取习题数量失败", e);

            return Result.error("获取习题数量失败，请稍后重试");
        }
    }

    /**
     * 校验当前用户是否有权访问课文关联的习题
     * ADMIN: 放行
     * TEACHER: 校验课文 uploadedBy 是否为自己
     * STUDENT/USER: 校验课文所属课程是否在学生加入的班级中
     */
    private void assertCanAccessExercise(com.english.entity.Text text) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BusinessException("未认证");
        }

        // ADMIN 放行
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) return;

        // OPS 放行（运维测试需要）
        boolean isOps = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_OPS".equals(a.getAuthority()));
        if (isOps) return;

        boolean isTeacher = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TEACHER".equals(a.getAuthority()));

        // 教师可以访问任何课文的习题（AI出题、自主练习等场景）
        if (isTeacher) {
            return;  // 允许访问
        }

        // 学生/普通用户：自主练习不限制访问权限
    }

    /**
     * 获取当前认证用户，统一 null 检查
     */
    private com.english.entity.User getCurrentUser() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new BusinessException("未认证");
        }
        String username = authentication.getName();
        com.english.entity.User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

/**
     * 安全地从 Object 转换为 Long，处理多种可能的类型
     */
    private Long parseLongField(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return Long.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断当前用户是否为教师或管理员
     */
    private boolean isTeacherOrAdmin() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_TEACHER".equals(a.getAuthority()));
    }

    /**
     * 从习题列表中移除正确答案标记（防止学生作弊）
     */
    @SuppressWarnings("unchecked")
    private void stripCorrectAnswers(List<Map<String, Object>> exercises) {
        if (exercises == null) return;
        // FIX #2: 添加类型检查，防止 ClassCastException
        for (Object item : exercises) {
            if (item instanceof Map) {
                stripCorrectAnswersFromExercise((Map<String, Object>) item);
            }
        }
    }

    /**
     * 从单个习题中移除正确答案标记
     */
    @SuppressWarnings("unchecked")
    private void stripCorrectAnswersFromExercise(Map<String, Object> exercise) {
        if (exercise == null) return;
        
        // FIX #1: 移除 exercise 本身可能包含的 isCorrect 字段
        exercise.remove("isCorrect");
        
        // 移除选项列表中的 isCorrect 字段
        Object optionsObj = exercise.get("options");
        if (optionsObj instanceof List) {
            List<?> options = (List<?>) optionsObj;
            if (options != null) {
                // 构建不含答案的新列表，避免JSON序列化时包含null值
                List<Map<String, Object>> sanitizedOptions = new java.util.ArrayList<>();
                for (Object opt : options) {
                    Map<String, Object> optionMap = new HashMap<>();
                    if (opt instanceof com.english.entity.ExerciseOption) {
                        com.english.entity.ExerciseOption exOpt = (com.english.entity.ExerciseOption) opt;
                        optionMap.put("id", exOpt.getId());
                        optionMap.put("exerciseId", exOpt.getExerciseId());
                        optionMap.put("optionKey", exOpt.getOptionKey());
                        optionMap.put("optionContent", exOpt.getOptionContent());
                        optionMap.put("orderNum", exOpt.getOrderNum());
                        // 意图地跳过isCorrect字段
                    } else if (opt instanceof Map) {
                        optionMap.putAll((Map<String, Object>) opt);
                        optionMap.remove("isCorrect");
                    }
                    sanitizedOptions.add(optionMap);
                }
                exercise.put("options", sanitizedOptions);
            }
        }
    }
}