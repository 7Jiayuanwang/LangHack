package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.TokenBlacklistService;
import com.english.service.UserService;
import com.english.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT 刷新令牌控制器 — 实现 Access Token(15分钟) + Refresh Token(7天) 双Token机制
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class RefreshTokenController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    /**
     * 刷新访问令牌
     * 客户端在 access token 过期前，使用 refresh token 换取新的 access token
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshToken(@RequestHeader("Refresh-Token") String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return Result.error(401, "Refresh Token 不能为空");
        }

        // 验证是否为 refresh token 类型
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            log.warn("Invalid token type for refresh endpoint");
            return Result.error(401, "无效的 Refresh Token");
        }

        // 检查黑名单
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            log.warn("Refresh token in blacklist");
            return Result.error(401, "Token 已失效，请重新登录");
        }

        String username = jwtUtil.getUsernameFromToken(refreshToken);
        if (username == null) {
            return Result.error(401, "无效的 Refresh Token");
        }

        // 验证用户是否存在且状态正常
        User user = userService.getUserByUsername(username);
        if (user == null || user.getDeleted() == 1) {
            return Result.error(401, "用户不存在或已被删除");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            return Result.error(401, "用户账号已被禁用");
        }

        // 验证 refresh token 是否有效
        if (!jwtUtil.validateToken(refreshToken, username)) {
            return Result.error(401, "Refresh Token 已过期，请重新登录");
        }

        // 生成新的 access token
        String newAccessToken = jwtUtil.generateAccessToken(username, user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("expiresIn", JwtUtil.ACCESS_TOKEN_EXPIRATION_MILLIS / 1000); // 秒

        log.info("Access token refreshed for user: {}", username);
        return Result.success("令牌刷新成功", result);
    }

    /**
     * 撤销 refresh token（登出时调用）
     */
    @PostMapping("/revoke")
    public Result<Void> revokeToken(@RequestHeader("Refresh-Token") String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return Result.error(400, "Refresh Token 不能为空");
        }

        long remainingTime = jwtUtil.getTokenRemainingTimeMillis(refreshToken);
        if (remainingTime > 0) {
            tokenBlacklistService.addToBlacklist(refreshToken, remainingTime);
            log.info("Refresh token revoked");
        }

        return Result.success("Token 已撤销");
    }
}
