package com.english.controller;

import com.english.dto.Result;
import com.english.entity.TextExercise;
import com.english.entity.ExerciseOption;
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
            Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            com.english.entity.User creator = userService.getUserByUsername(username);
            if (creator == null) throw new com.english.exception.BusinessException("用户不存在");
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
                        option.setIsCorrect(isCorrect);
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
            Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            com.english.entity.User creator = userService.getUserByUsername(username);
            if (creator == null) throw new com.english.exception.BusinessException("用户不存在");
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
     */
    @GetMapping("/text/{textId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<List<Map<String, Object>>> getExercisesByTextId(@PathVariable Long textId) {
        try {
            List<Map<String, Object>> exercises = textExerciseService.getExercisesWithOptions(textId);
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
     */
    @GetMapping("/{exerciseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<Map<String, Object>> getExerciseDetail(@PathVariable Long exerciseId) {
        try {
            Map<String, Object> exercise = textExerciseService.getExerciseDetail(exerciseId);
            if (exercise != null) {
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
            Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new com.english.exception.BusinessException("用户不存在");

            Map<String, Object> existingExercise = textExerciseService.getExerciseDetail(exerciseId);
            if (existingExercise == null) {
                return Result.error("习题不存在");
            }
            if (!"ADMIN".equals(currentUser.getRole())) {
                Object createdByObj = existingExercise.get("createdBy");
                Long createdBy = createdByObj != null ? Long.valueOf(createdByObj.toString()) : null;
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
            Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new com.english.exception.BusinessException("用户不存在");

            Map<String, Object> existingExercise = textExerciseService.getExerciseDetail(exerciseId);
            if (existingExercise == null) {
                return Result.error("习题不存在");
            }
            if (!"ADMIN".equals(currentUser.getRole())) {
                Object createdByObj = existingExercise.get("createdBy");
                Long createdBy = createdByObj != null ? Long.valueOf(createdByObj.toString()) : null;
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
            // 验证教师权限：非ADMIN教师只能删除自己创建的课文习题
            Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new com.english.exception.BusinessException("用户不存在");

            if (!"ADMIN".equals(currentUser.getRole())) {
                // 检查该课文的习题是否由当前教师创建
                List<Map<String, Object>> exercises = textExerciseService.getExercisesWithOptions(textId);
                if (exercises != null && !exercises.isEmpty()) {
                    Object createdByObj = exercises.get(0).get("createdBy");
                    Long createdBy = createdByObj != null ? Long.valueOf(createdByObj.toString()) : null;
                    if (createdBy == null || !createdBy.equals(currentUser.getId())) {
                        return Result.error("无权删除其他教师创建的课文习题");
                    }
                }
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
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
}