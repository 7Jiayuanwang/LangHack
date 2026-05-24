package com.english.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.AiConversation;
import com.english.entity.AiMessage;
import com.english.entity.ConversationShare;
import com.english.service.ConversationService;
import com.english.service.UserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/student/conversations")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserService userService;

    @PostMapping
    public Result<AiConversation> createConversation(@RequestBody CreateConversationRequest req,
                                                     Authentication auth) {
        Long userId = getUserId(auth);
        AiConversation conv = conversationService.createConversation(userId, req.getFirstQuestion());
        return Result.success(conv);
    }

    @GetMapping
    public Result<Page<AiConversation>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = getUserId(auth);
        return Result.success(conversationService.listUserConversations(userId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getConversation(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        List<AiMessage> messages = conversationService.getMessages(id, userId);
        return Result.success(Map.of("messages", messages));
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteConversation(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        conversationService.deleteConversation(id, userId);
        return Result.success(null);
    }

    @PostMapping("/{id}/share")
    public Result<ConversationShare> shareConversation(@PathVariable Long id,
                                                       @RequestBody ShareRequest req,
                                                       Authentication auth) {
        Long userId = getUserId(auth);
        ConversationShare share = conversationService.generateShareToken(
                id, userId, req.getExpiresInDays(), req.getPassword());
        share.setAccessPassword(share.getAccessPassword() != null ? "***" : null);
        return Result.success(share);
    }

    private Long getUserId(Authentication auth) {
        return userService.getUserByUsername(auth.getName()).getId();
    }

    @Data
    public static class CreateConversationRequest {
        private String firstQuestion;
    }

    @Data
    public static class ShareRequest {
        private Integer expiresInDays;
        private String password;
    }
}
