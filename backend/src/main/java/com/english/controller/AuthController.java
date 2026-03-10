package com.english.controller;

import com.english.dto.LoginRequest;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Map<String, Object> loginResult = userService.login(loginRequest);
            return Result.success("登录成功", loginResult);
        } catch (BusinessException e) {
            // 业务异常（账号/密码错误等）：透传用户友好消息
            return Result.error(e.getMessage());
        } catch (Exception e) {
            // 非预期异常（IO/SQL等）：不泄露内部细节
            log.error("登录失败", e);
            return Result.error("登录失败，请稍后重试");
        }
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        // 输入校验
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        String username = user.getUsername().trim();
        if (username.length() < 3 || username.length() > 50) {
            return Result.error("用户名长度须在 3~50 个字符之间");
        }
        if (!username.matches("^[a-zA-Z0-9_\\-\\.@]+$")) {
            return Result.error("用户名只能包含字母、数字及 _ - . @ 符号");
        }
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            return Result.error("密码长度不能少于 6 位");
        }
        if (user.getPassword().length() > 100) {
            return Result.error("密码长度不能超过 100 位");
        }
        try {
            // 强制设置角色为普通用户（必须在createUser前设置，让Service层处理TOCTOU）
            user.setRole("USER");
            user.setUsername(username);

            // 清除可能由客户端设置的危险字段，防止Mass Assignment
            user.setId(null);
            user.setDeleted(null);

            // 设置默认状态为活跃
            user.setStatus("ACTIVE");

            // createUser 内部会再次检查用户名存在性，避免 TOCTOU 竞态
            userService.createUser(user);
            return Result.success("注册成功，您已注册为普通用户");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Registration failed with runtime exception", e);
            return Result.error("注册失败，请稍后重试");
        } catch (Exception e) {
            log.error("Registration failed with unexpected exception", e);
            return Result.error("注册失败，请稍后重试");
        }
    }

    @GetMapping("/userinfo")
    public Result<User> getUserInfo() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return Result.error("未授权访问");
            }
            String username = authentication.getName();

            User user = userService.getUserByUsername(username);
            if (user != null) {
                // 不返回敏感字段
                user.setPassword(null);
                user.setDeleted(null);
                return Result.success(user);
            }
            return Result.error("用户不存在");
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.error("获取用户信息失败");
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<User>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(required = false) String role) {
        try {
            List<User> users = userService.searchUsers(keyword, role);
            // 不返回密码信息
            if (users != null) {
                users.forEach(user -> user.setPassword(null));
            }
            return Result.success(users != null ? users : java.util.Collections.emptyList());
        } catch (Exception e) {
            log.error("搜索用户失败", e);
            return Result.error("搜索失败，请稍后重试");
        }
    }

    @GetMapping("/logout")
    public Result<String> logout() {
        // JWT是无状态的，客户端删除token即可
        return Result.success("退出登录成功");
    }
}