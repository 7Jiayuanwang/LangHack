package com.english.controller;

import com.english.dto.Result;
import com.english.util.AdminLoginTokenGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员工具控制器
 * 提供管理员专用功能，如生成管理员登录链接
 */
@RestController
@RequestMapping("/api/admin/tools")
public class AdminToolsController {

    @Autowired
    private AdminLoginTokenGenerator tokenGenerator;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    /**
     * 生成管理员登录链接
     * 仅限 ADMIN 和 OPS 角色访问
     *
     * @return 管理员登录链接
     */
    @GetMapping("/generate-admin-login-url")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> generateAdminLoginUrl() {
        long timestamp = System.currentTimeMillis();
        String token = tokenGenerator.generateToken(timestamp);
        String loginUrl = String.format("%s/admin-portal?token=%s&t=%d", baseUrl, token, timestamp);

        Map<String, Object> result = new HashMap<>();
        result.put("loginUrl", loginUrl);
        result.put("expiresIn", 300); // 5 分钟
        result.put("expiresAt", timestamp + 5 * 60 * 1000);

        return Result.success(result);
    }
}
