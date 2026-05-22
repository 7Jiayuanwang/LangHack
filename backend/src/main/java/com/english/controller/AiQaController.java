package com.english.controller;

import com.english.dto.Result;
import com.english.entity.AiCallLog;
import com.english.mapper.AiCallLogMapper;
import com.english.service.HiAgentService;
import com.english.service.impl.AiProviderRuntimeService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private AiCallLogMapper aiCallLogMapper;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest request,
                                            Authentication authentication) {
        if (request.getUserQuestion() == null || request.getUserQuestion().trim().isEmpty()) {
            return Result.error("问题不能为空");
        }
        if (request.getSelectedText() == null || request.getSelectedText().trim().isEmpty()) {
            return Result.error("未选中文本");
        }

        long startTime = System.currentTimeMillis();
        try {
            String question = request.getUserQuestion().trim();
            String selectedText = request.getSelectedText().trim();
            String articleContext = request.getArticleContext() != null ? request.getArticleContext().trim() : "";
            String answer = null;

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
            }

            long callLogId = recordAiCallLog("HIAGENT", "chatQA", true,
                    System.currentTimeMillis() - startTime,
                    authentication != null ? authentication.getName() : "anonymous");

            Map<String, Object> data = new HashMap<>();
            data.put("response", answer);
            data.put("success", true);
            data.put("callLogId", callLogId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("AI Q&A failed: {}", e.getMessage(), e);
            recordAiCallLog("HIAGENT", "chatQA", false,
                    System.currentTimeMillis() - startTime,
                    authentication != null ? authentication.getName() : "anonymous");
            return Result.error("AI 问答服务暂时不可用，请稍后重试");
        }
    }

    private long recordAiCallLog(String service, String method, boolean success,
                                  long responseTimeMs, String triggeredBy) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setServiceName(service);
            callLog.setMethodName(method);
            callLog.setSuccess(success);
            callLog.setResponseTimeMs(responseTimeMs);
            callLog.setTriggeredBy(triggeredBy);
            callLog.setCreatedTime(LocalDateTime.now());
            aiCallLogMapper.insert(callLog);
            return callLog.getId() != null ? callLog.getId() : 0L;
        } catch (Exception e) {
            log.debug("Failed to record AI call log", e);
            return 0L;
        }
    }

    private String buildChatPrompt(String userQuestion, String selectedText, String articleContext) {
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
    }
}
