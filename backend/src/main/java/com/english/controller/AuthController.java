package com.english.controller;

import com.english.dto.LoginRequest;
import com.english.dto.RefreshTokenRequest;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        try {
            Map<String, Object> loginResult = userService.login(loginRequest);
            return Result.success("登录成功", loginResult);
        } catch (IllegalArgumentException e) {
            logger.warn("登录失败，用户名: {}, 原因: {}", loginRequest.getUsername(), e.getMessage());
            return Result.error("用户名或密码错误");
        } catch (Exception e) {
            logger.error("登录失败，用户名: {}", loginRequest.getUsername(), e);
            return Result.error("登录失败，请稍后重试");
        }
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        try {
            // 检查用户名是否已存在
            if (userService.getUserByUsername(user.getUsername()) != null) {
                return Result.error("用户名已存在");
            }

            // 设置默认角色为学生
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("STUDENT");
            }

            userService.createUser(user);
            return Result.success("注册成功");
        } catch (IllegalArgumentException e) {
            logger.warn("注册失败，用户名: {}, 原因: {}", user.getUsername(), e.getMessage());
            return Result.error("参数错误");
        } catch (Exception e) {
            logger.error("注册失败，用户名: {}", user.getUsername(), e);
            return Result.error("注册失败，请稍后重试");
        }
    }

    @GetMapping("/userinfo")
    public Result<User> getUserInfo() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.getUserByUsername(username);
            if (user != null) {
                // 不返回密码
                user.setPassword(null);
                return Result.success(user);
            }
            return Result.error("用户不存在");
        } catch (IllegalStateException e) {
            logger.warn("获取用户信息失败: {}", e.getMessage());
            return Result.error("获取用户信息失败");
        } catch (Exception e) {
            logger.error("获取用户信息失败", e);
            return Result.error("获取用户信息失败，请稍后重试");
        }
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        // JWT是无状态的，客户端删除token即可
        return Result.success("退出登录成功");
    }

    /**
     * 刷新Token
     * 使用refresh token获取新的access token
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            String refreshToken = request.getToken();

            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                return Result.error("Refresh token不能为空");
            }

            // 验证refresh token是否可以刷新
            if (!jwtUtil.canTokenBeRefreshed(refreshToken)) {
                return Result.error("Refresh token无效或已过期");
            }

            // 生成新的access token
            String newAccessToken = jwtUtil.refreshToken(refreshToken);

            // 获取用户信息
            String username = jwtUtil.getUsernameFromToken(refreshToken);
            User user = userService.getUserByUsername(username);

            if (user == null) {
                return Result.error("用户不存在");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("token", newAccessToken);
            result.put("refreshToken", refreshToken); // 保持原refresh token
            result.put("expiresIn", 86400000L); // 24小时

            return Result.success("Token刷新成功", result);
        } catch (IllegalArgumentException e) {
            logger.warn("Token刷新失败: {}", e.getMessage());
            return Result.error("Token刷新失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Token刷新失败", e);
            return Result.error("Token刷新失败，请重新登录");
        }
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("ok");
    }
}