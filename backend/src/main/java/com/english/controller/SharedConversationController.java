package com.english.controller;

import com.english.dto.Result;
import com.english.exception.BusinessException;
import com.english.service.ConversationService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/public/shared-conversations")
public class SharedConversationController {

    @Autowired
    private ConversationService conversationService;

    @GetMapping("/{token}/meta")
    public Result<Map<String, Object>> getShareMeta(@PathVariable String token) {
        boolean needsPassword = conversationService.shareRequiresPassword(token);
        return Result.success(Map.of("requiresPassword", needsPassword));
    }

    @PostMapping("/{token}")
    public Result<Map<String, Object>> getSharedConversation(@PathVariable String token,
                                                             @RequestBody(required = false) AccessRequest req) {
        try {
            String password = req != null ? req.getPassword() : null;
            Map<String, Object> data = conversationService.getSharedConversation(token, password);
            return Result.success(data);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @Data
    public static class AccessRequest {
        private String password;
    }
}
