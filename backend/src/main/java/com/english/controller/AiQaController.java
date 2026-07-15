package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.AiConversation;
import com.english.mapper.AiCallLogMapper;
import com.english.service.ConversationService;
import com.english.service.HiAgentService;
import com.english.service.UserService;
import com.english.service.impl.AiCallMetricsService;
import com.english.service.impl.AiProviderRuntimeService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/student/ai-qa")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
public class AiQaController {

    @Autowired
    private HiAgentService hiAgentService;

    @Autowired(required = false)
    private AiProviderRuntimeService aiProviderRuntimeService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserService userService;

    @Autowired
    private AiCallMetricsService aiCallMetricsService;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest request, Authentication auth) {
        if (request.getUserQuestion() == null || request.getUserQuestion().trim().isEmpty()) {
            return Result.error("问题不能为空");
        }
        if (request.getUserQuestion().length() > 500) {
            return Result.error("问题不能超过500字");
        }
        if (request.getSelectedText() != null && request.getSelectedText().length() > 2000) {
            return Result.error("选中文本不能超过2000字");
        }
        if (request.getArticleContext() != null && request.getArticleContext().length() > 5000) {
            return Result.error("文章上下文不能超过5000字");
        }
        try {
            String question = request.getUserQuestion().trim();
            String selectedText = request.getSelectedText() != null ? request.getSelectedText().trim() : "";
            String articleContext = request.getArticleContext() != null ? request.getArticleContext().trim() : "";
            String username = auth != null ? auth.getName() : "anonymous";
            String answer = null;

            long startTime = aiCallMetricsService.startTimer();

            if (aiProviderRuntimeService != null) {
                try {
                    Optional<String> configuredAnswer = aiProviderRuntimeService.completeIfConfigured(
                            "chat", "chatQA", buildChatPrompt(question, selectedText, articleContext));
                    if (configuredAnswer.isPresent()) {
                        answer = configuredAnswer.get();
                    }
                } catch (Exception providerError) {
                    log.warn("Configured chat AI provider failed, falling back to HiAgent QA: {}",
                            providerError.getMessage());
                }
            }

            if (answer == null) {
                answer = hiAgentService.chatQA(question, selectedText, articleContext);
                // 仅在 HiAgent 回退路径记录，Provider 路径由其内部记录，避免重复计数
                aiCallMetricsService.recordSuccess("QA", "chatQA", startTime, username);
            }

            // 持久化对话（仅当用户已登录且 auth 不为 null）
            Long conversationId = request.getConversationId();
            if (auth != null) {
                try {
                    Long userId = userService.getUserByUsername(auth.getName()).getId();
                    if (conversationId == null) {
                        AiConversation conv = conversationService.createConversation(userId, question);
                        conversationId = conv.getId();
                    }
                    conversationService.addMessage(conversationId, userId, "user", question, selectedText, articleContext);
                    conversationService.addMessage(conversationId, userId, "assistant", answer, null, null);
                } catch (Exception e) {
                    log.warn("Failed to persist conversation, continuing without persistence: {}", e.getMessage());
                    conversationId = null;
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("response", answer);
            data.put("success", true);
            data.put("conversationId", conversationId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("AI Q&A failed: {}", e.getMessage(), e);
            return Result.error("AI 问答服务暂时不可用，请稍后重试");
        }
    }

    @GetMapping("/usage/stats")
    public Result<Map<String, Object>> usageStats(Authentication auth) {
        String username = auth != null ? auth.getName() : "anonymous";
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        QueryWrapper<com.english.entity.AiCallLog> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("method_name", "chatQA");
        totalWrapper.eq("triggered_by", username);
        totalWrapper.eq("success", true);
        long totalCalls = aiCallLogMapper.selectCount(totalWrapper);

        QueryWrapper<com.english.entity.AiCallLog> todayWrapper = new QueryWrapper<>();
        todayWrapper.eq("method_name", "chatQA");
        todayWrapper.eq("triggered_by", username);
        todayWrapper.eq("success", true);
        todayWrapper.ge("created_time", todayStart);
        long todayCalls = aiCallLogMapper.selectCount(todayWrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("totalCalls", totalCalls);
        data.put("todayCalls", todayCalls);
        return Result.success(data);
    }

    private String buildChatPrompt(String userQuestion, String selectedText, String articleContext) {
        if (selectedText == null || selectedText.isEmpty()) {
            return "你是一位专业的英语学习辅导老师，请用中文回答学生的英语学习问题。"
                    + "回答要详细、专业，帮助学生理解英语内容。\n\n"
                    + "【学生提问】\n" + userQuestion;
        }
        return "你是一位专业的英语学习辅导老师。学生正在阅读一篇英语文章，并选中了一段文本。"
                + "请根据选中的文本和文章上下文，用中文回答学生的问题。"
                + "回答要详细、专业，帮助学生理解英语内容。\n\n"
                + "【选中文本】\n" + selectedText + "\n\n"
                + (articleContext != null && !articleContext.isEmpty() ? "【文章上下文】\n" + articleContext + "\n\n" : "")
                + "【学生提问】\n" + userQuestion;
    }

    @Data
    public static class ChatRequest {
        private String userQuestion;
        private String selectedText;
        private String articleContext;
        private Long conversationId;
    }
}

