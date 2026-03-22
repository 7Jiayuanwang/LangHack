package com.english.controller;

import com.english.dto.Result;
import com.english.service.HiAgentService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/student/ai-qa")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
public class AiQaController {

    @Autowired
    private HiAgentService hiAgentService;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        if (request.getUserQuestion() == null || request.getUserQuestion().trim().isEmpty()) {
            return Result.error("问题不能为空");
        }
        if (request.getSelectedText() == null || request.getSelectedText().trim().isEmpty()) {
            return Result.error("未选中文本");
        }

        try {
            String answer = hiAgentService.chatQA(
                    request.getUserQuestion().trim(),
                    request.getSelectedText().trim(),
                    request.getArticleContext() != null ? request.getArticleContext().trim() : ""
            );

            Map<String, Object> data = new HashMap<>();
            data.put("response", answer);
            data.put("success", true);
            return Result.success(data);
        } catch (Exception e) {
            log.error("AI Q&A failed: {}", e.getMessage(), e);
            return Result.error("AI 问答服务暂时不可用，请稍后重试");
        }
    }

    @Data
    public static class ChatRequest {
        private String userQuestion;
        private String selectedText;
        private String articleContext;
    }
}
