package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.FeatureToggleService;
import com.english.service.SystemConfigService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已登录用户功能开关接口
 *
 * <p>对任意已登录用户（USER/STUDENT/TEACHER/OPS/ADMIN）均返回三层合并结果：
 * 全局默认 → 标签覆盖 → 用户级覆盖。</p>
 *
 * <p>区别于 {@link StudentFeatureController}（仅 USER/STUDENT）和
 * {@link HealthController#getFeatureToggles()}（未登录用户，仅全局默认）。</p>
 */
@RestController
@RequestMapping("/api/user/feature-toggles")
public class UserFeatureController {

    private static final Logger logger = LoggerFactory.getLogger(UserFeatureController.class);

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemConfigService systemConfigService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Boolean>> getFeatureTogglesForCurrentUser(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                logger.warn("Authenticated user '{}' not found in database, falling back to global toggles", username);
                return Result.success(toBooleanMap(systemConfigService.getByGroup("feature_toggle")));
            }
            Map<String, Boolean> toggles = featureToggleService.getMergedTogglesForUser(user.getId());
            return Result.success(toggles != null ? toggles : new LinkedHashMap<>());
        } catch (Exception e) {
            logger.error("获取用户功能开关失败，降级返回全局配置", e);
            try {
                return Result.success(toBooleanMap(systemConfigService.getByGroup("feature_toggle")));
            } catch (Exception fallbackEx) {
                logger.error("降级查询全局配置亦失败", fallbackEx);
                return Result.success(new HashMap<>());
            }
        }
    }

    private Map<String, Boolean> toBooleanMap(Map<String, String> raw) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (raw == null) return result;
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            result.put(entry.getKey(), "true".equalsIgnoreCase(entry.getValue()));
        }
        return result;
    }
}
