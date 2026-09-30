package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.InternetContentRecommendationDto;
import com.english.dto.learner.LearningAgentContextDto;
import com.english.dto.learner.LearningAgentMemoryRequest;
import com.english.dto.learner.LearningAgentOnboardingRequest;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.LearningAgentService;
import com.english.service.InternetContentRecommendationService;
import com.english.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/agent")
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
@RequiredArgsConstructor
public class LearningAgentController {

    private final LearningAgentService learningAgentService;
    private final InternetContentRecommendationService contentRecommendationService;
    private final UserService userService;

    @GetMapping("/context")
    public Result<LearningAgentContextDto> getContext(Authentication authentication) {
        return execute(authentication, userId -> learningAgentService.getContext(userId));
    }

    @GetMapping("/content-recommendations")
    public Result<List<InternetContentRecommendationDto>> getContentRecommendations(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "6") int count,
            Authentication authentication) {
        int safeCount = Math.min(Math.max(count, 1), 12);
        return execute(authentication,
                userId -> contentRecommendationService.recommendForUser(userId, safeCount));
    }

    @PutMapping("/onboarding")
    public Result<LearningAgentContextDto> saveOnboarding(
            @RequestBody LearningAgentOnboardingRequest request,
            Authentication authentication) {
        return execute(authentication, userId -> learningAgentService.saveOnboarding(userId, request));
    }

    @PostMapping("/memories")
    public Result<LearningAgentContextDto> addMemory(
            @RequestBody LearningAgentMemoryRequest request,
            Authentication authentication) {
        return execute(authentication, userId -> learningAgentService.addMemory(userId, request));
    }

    @DeleteMapping("/memories/{memoryId}")
    public Result<LearningAgentContextDto> deleteMemory(
            @PathVariable Long memoryId,
            Authentication authentication) {
        return execute(authentication, userId -> learningAgentService.deleteMemory(userId, memoryId));
    }

    private <T> Result<T> execute(
            Authentication authentication,
            java.util.function.Function<Long, T> operation) {
        try {
            if (authentication == null) return Result.error(401, "请先登录");
            User user = userService.getUserByUsername(authentication.getName());
            if (user == null) return Result.error(404, "用户不存在");
            return Result.success(operation.apply(user.getId()));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }
}
