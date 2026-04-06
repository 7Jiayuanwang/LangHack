package com.english.controller;

import com.english.dto.Result;
import com.english.service.FeatureToggleService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学生端个性化功能开关接口
 */
@RestController
@RequestMapping("/api/student/feature-toggles")
public class StudentFeatureController {

    private static final Logger logger = LoggerFactory.getLogger(StudentFeatureController.class);

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录用户的个性化功能开关（三层合并：全局 → 标签 → 用户级）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Boolean>> getPersonalFeatureToggles(Authentication authentication) {
        try {
            String username = authentication.getName();
            Long userId = userService.getUserByUsername(username).getId();
            Map<String, Boolean> toggles = featureToggleService.getMergedTogglesForUser(userId);
            return Result.success(toggles);
        } catch (Exception e) {
            logger.error("获取个性化功能开关失败", e);
            return Result.error("获取功能开关失败，请稍后重试");
        }
    }
}
