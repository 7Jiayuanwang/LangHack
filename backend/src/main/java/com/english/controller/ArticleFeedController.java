package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.ArticleFeedEventRequest;
import com.english.dto.learner.ArticleFeedPageDto;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.service.impl.ArticleFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/feed")
@PreAuthorize("hasAnyRole('STUDENT', 'USER', 'TEACHER', 'ADMIN', 'OPS')")
@RequiredArgsConstructor
public class ArticleFeedController {
    private final ArticleFeedService feedService;
    private final UserService userService;

    @GetMapping("/articles")
    public Result<ArticleFeedPageDto> articles(
            @RequestParam(defaultValue = "FOR_YOU") String mode,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long seed,
            @RequestParam(required = false) String exclude,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return Result.error(401, "请先登录");
        try {
            return Result.success(feedService.getPage(user.getId(), mode, category,
                    page, size, seed, parseIds(exclude)));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/events")
    public Result<Void> event(@RequestBody ArticleFeedEventRequest request,
                              Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return Result.error(401, "请先登录");
        try {
            feedService.recordEvent(user.getId(), request);
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    private User currentUser(Authentication authentication) {
        return authentication == null ? null : userService.getUserByUsername(authentication.getName());
    }

    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (ids.size() >= 80) break;
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                // Ignore bad client input instead of building SQL from it.
            }
        }
        return ids;
    }
}
