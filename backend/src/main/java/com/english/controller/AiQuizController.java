package com.english.controller;

import com.english.dto.AiQuizResponseDTO;
import com.english.dto.Result;
import com.english.entity.AiQuizSession;
import com.english.entity.ExerciseOption;
import com.english.entity.Text;
import com.english.entity.TextExercise;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.AiQuizSessionMapper;
import com.english.service.StudentAiQuizService;
import com.english.service.TextExerciseService;
import com.english.service.TextService;
import com.english.service.UserService;
import com.english.service.impl.AiProviderRuntimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/student/ai-quiz")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
public class AiQuizController {

    @Autowired
    private TextService textService;

    @Autowired
    private TextExerciseService textExerciseService;

    @Autowired
    private UserService userService;

    @Autowired
    private AiQuizSessionMapper aiQuizSessionMapper;

    @Autowired
    private StudentAiQuizService studentAiQuizService;

    @Autowired(required = false)
    private AiProviderRuntimeService aiProviderRuntimeService;

    @Autowired(required = false)
    private com.english.service.impl.HiAgentServiceImpl hiAgentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/generate")
    public Result<Map<String, Object>> generateQuiz(@RequestBody GenerateQuizRequest request) {
        try {
            if (request.getTextId() == null) {
                return Result.error("textId 不能为空");
            }

            Text text = textService.getTextById(request.getTextId());
            if (text == null || Integer.valueOf(1).equals(text.getDeleted())) {
                return Result.error("文章不存在或已被删除");
            }

            String content = text.getMarkdownContent();
            if (content == null || content.isBlank()) {
                content = text.getContent();
            }
            if (content == null || content.isBlank()) {
                return Result.error("文章内容为空");
            }

            User currentUser = getCurrentUser();

            if (aiProviderRuntimeService == null && hiAgentService == null) {
                return Result.error("AI 服务未配置");
            }

            // 重新出题：先清空该课文的旧题（含选项 + 答题记录）
            if (Boolean.TRUE.equals(request.getRegenerate())) {
                try {
                    textExerciseService.deleteAllExercisesByTextId(request.getTextId());
                } catch (Exception e) {
                    log.warn("清空旧题失败: textId={}", request.getTextId(), e);
                }
            }

            int count = request.getCount() != null ? request.getCount() : 5;
            String prompt = buildPrompt(text.getTitle(), content, count);

            Optional<String> aiResponse = aiProviderRuntimeService != null
                    ? aiProviderRuntimeService.completeIfConfigured("quiz", "generateQuiz", prompt)
                    : Optional.empty();

            String responseText;
            if (aiResponse.isPresent()) {
                responseText = aiResponse.get();
            } else if (hiAgentService != null) {
                responseText = hiAgentService.generateQuiz(text.getTitle(), content, count);
            } else {
                return Result.error("AI 出题服务未配置，请联系运维在运维面板配置 quiz 类型的 AI 提供商");
            }
            String jsonStr = stripCodeFence(responseText);

            AiQuizResponseDTO quizResponse;
            try {
                quizResponse = objectMapper.readValue(jsonStr, AiQuizResponseDTO.class);
            } catch (Exception e) {
                log.error("AI 返回 JSON 解析失败: {}", jsonStr, e);
                return Result.error("AI 返回格式解析失败");
            }

            if (quizResponse.getQuestions() == null || quizResponse.getQuestions().isEmpty()) {
                return Result.error("AI 未生成任何题目");
            }

            List<TextExercise> exercises = new ArrayList<>();
            List<List<ExerciseOption>> allOptions = new ArrayList<>();
            int orderNum = 1;

            for (AiQuizResponseDTO.QuestionItem item : quizResponse.getQuestions()) {
                TextExercise exercise = new TextExercise();
                exercise.setTextId(text.getId());
                exercise.setQuestion(item.getQuestion());
                exercise.setExerciseType("MULTIPLE_CHOICE");
                exercise.setQuestionType(item.getType() != null ? item.getType() : "MULTIPLE_CHOICE");
                exercise.setAnalysis(item.getAnalysis());
                exercise.setOrderNum(orderNum++);
                exercise.setCreatedBy(currentUser.getId());

                if (item.getDistractorAnalysis() != null) {
                    exercise.setDistractorAnalysis(objectMapper.writeValueAsString(item.getDistractorAnalysis()));
                }

                exercises.add(exercise);

                List<ExerciseOption> options = new ArrayList<>();
                if (item.getOptions() != null) {
                    int optOrder = 1;
                    for (Map.Entry<String, String> entry : item.getOptions().entrySet()) {
                        ExerciseOption option = new ExerciseOption();
                        option.setOptionKey(entry.getKey());
                        option.setOptionContent(entry.getValue());
                        option.setIsCorrect(entry.getKey().equalsIgnoreCase(item.getCorrectAnswer()) ? 1 : 0);
                        option.setOrderNum(optOrder++);
                        options.add(option);
                    }
                }
                allOptions.add(options);
            }

            textExerciseService.batchCreateExercises(exercises, allOptions);

            AiQuizSession session = new AiQuizSession();
            session.setTextId(text.getId());
            session.setDifficulty(quizResponse.getQualityMetrics() != null
                    ? quizResponse.getQualityMetrics().getDifficultyMatch() : null);
            session.setPassageAnalysis(quizResponse.getPassageAnalysis() != null
                    ? objectMapper.writeValueAsString(quizResponse.getPassageAnalysis()) : null);
            session.setQualityMetrics(quizResponse.getQualityMetrics() != null
                    ? objectMapper.writeValueAsString(quizResponse.getQualityMetrics()) : null);
            session.setQuestionCount(exercises.size());
            session.setAiService("quiz");
            session.setCreatedBy(currentUser.getId());
            aiQuizSessionMapper.insert(session);

            Map<String, Object> result = new HashMap<>();
            result.put("message", "AI 出题成功");
            result.put("questionCount", exercises.size());
            result.put("sessionId", session.getId());
            if (quizResponse.getPassageAnalysis() != null) {
                result.put("passageAnalysis", quizResponse.getPassageAnalysis());
            }
            if (quizResponse.getQualityMetrics() != null) {
                result.put("qualityMetrics", quizResponse.getQualityMetrics());
            }

            return Result.success("AI 出题成功", result);

        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("AI 出题失败", e);
            return Result.error("AI 出题失败: " + e.getMessage());
        }
    }

    private String buildPrompt(String title, String content, int count) {
        return "请为以下文章生成 " + count + " 道选择题。\n\n"
                + "【文章标题】\n" + title + "\n\n"
                + "【文章内容】\n" + content;
    }

    private String stripCodeFence(String text) {
        if (text == null) return "";
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.strip();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new BusinessException("未认证");
        }
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    @Data
    public static class GenerateQuizRequest {
        private Long textId;
        private Integer count;
        private Boolean regenerate;
    }

    /**
     * 学生端：查自己的做题尝试列表
     */
    @GetMapping("/my-attempts")
    public Result<java.util.List<com.english.entity.StudentAiQuizAttempt>> listMyAttempts(
            @RequestParam(required = false) Long textId) {
        try {
            User user = getCurrentUser();
            return Result.success(studentAiQuizService.listMyAttempts(user.getId(), textId));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询学生做题记录失败", e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 学生端：查自己某次尝试的明细
     */
    @GetMapping("/my-attempts/{attemptId}")
    public Result<Map<String, Object>> getMyAttemptDetail(@PathVariable Long attemptId) {
        try {
            User user = getCurrentUser();
            return Result.success(studentAiQuizService.getMyAttemptDetail(user.getId(), attemptId));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询学生做题明细失败 attemptId={}", attemptId, e);
            return Result.error("查询失败，请稍后重试");
        }
    }
}
