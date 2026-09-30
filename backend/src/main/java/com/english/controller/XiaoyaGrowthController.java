package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.XiaoyaExternalLearningPageDto;
import com.english.dto.learner.XiaoyaExternalLearningRequest;
import com.english.dto.learner.XiaoyaGrowthDto;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.service.impl.XiaoyaGrowthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Function;

@RestController
@RequestMapping("/api/student/agent/growth")
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
@RequiredArgsConstructor
public class XiaoyaGrowthController {
    private final XiaoyaGrowthService growthService;
    private final UserService userService;

    @GetMapping
    public Result<XiaoyaGrowthDto> get(Authentication authentication) {
        return execute(authentication, growthService::forUser);
    }

    @GetMapping("/summary")
    public Result<XiaoyaGrowthDto> summary(Authentication authentication) {
        return execute(authentication, growthService::summaryForUser);
    }

    @PostMapping("/external")
    public Result<XiaoyaGrowthDto> addExternal(@RequestBody XiaoyaExternalLearningRequest request,
                                                Authentication authentication) {
        return execute(authentication, userId -> growthService.addExternalLearning(userId, request));
    }

    @GetMapping("/external")
    public Result<XiaoyaExternalLearningPageDto> listExternal(
            @RequestParam(defaultValue = "1") int page, Authentication authentication) {
        return execute(authentication, userId -> growthService.listExternalLearning(userId, page));
    }

    @DeleteMapping("/external/{noteId}")
    public Result<XiaoyaGrowthDto> deleteExternal(@PathVariable Long noteId,
                                                   Authentication authentication) {
        return execute(authentication, userId -> growthService.deleteExternalLearning(userId, noteId));
    }

    private <T> Result<T> execute(Authentication authentication, Function<Long, T> operation) {
        if (authentication == null) return Result.error(401, "请先登录");
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) return Result.error(404, "用户不存在");
        try {
            return Result.success(operation.apply(user.getId()));
        } catch (BusinessException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
