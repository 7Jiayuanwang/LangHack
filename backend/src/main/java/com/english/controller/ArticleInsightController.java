package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.ArticleInsightMessageDto;
import com.english.dto.learner.ArticleLearningInsightDto;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.service.impl.ArticleInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/agent/article-insights")
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
@RequiredArgsConstructor
public class ArticleInsightController {
    private final ArticleInsightService insightService;
    private final UserService userService;

    @GetMapping("/{textId}")
    public Result<ArticleLearningInsightDto> get(@PathVariable Long textId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return Result.error(401, "请先登录");
        try {
            return Result.success(insightService.getForCompletedArticle(user.getId(), textId));
        } catch (BusinessException e) {
            int code = e.getMessage().contains("先完成") ? 403 : 404;
            return Result.error(code, e.getMessage());
        }
    }

    @GetMapping("/unread")
    public Result<List<ArticleInsightMessageDto>> unread(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(insightService.getUnread(user.getId()));
    }

    @PostMapping("/messages/{messageId}/read")
    public Result<String> markRead(@PathVariable Long messageId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return Result.error(401, "请先登录");
        try {
            insightService.markRead(user.getId(), messageId);
            return Result.success("已标记为已读");
        } catch (BusinessException e) {
            return Result.error(404, e.getMessage());
        }
    }

    private User currentUser(Authentication authentication) {
        return authentication == null ? null : userService.getUserByUsername(authentication.getName());
    }
}
