package com.english.controller;

import com.english.dto.ElevateRequest;
import com.english.dto.LoginRequest;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.TokenBlacklistService;
import com.english.service.UserService;
import com.english.service.impl.ElevationService;
import com.english.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    // RFC 5322 简化版邮箱正则：更严格的验证
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    @Autowired
    private UserService userService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private ElevationService elevationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private static final Set<String> ELEVATION_ALLOWED = Set.of("ADMIN", "OPS");

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
        // 空请求体判空
        if (user == null) {
            return Result.error("请求参数不能为空");
        }
        // 输入校验
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        String username = user.getUsername().trim();
        if (username.length() < 3 || username.length() > 50) {
            return Result.error("用户名长度须在 3~50 个字符之间");
        }
        if (!username.matches("^[a-zA-Z0-9_\\-.@]+$")) {
            return Result.error("用户名只能包含字母、数字及 _ - . @ 符号");
        }
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            return Result.error("密码长度不能少于 6 位");
        }
        if (user.getPassword().length() > 100) {
            return Result.error("密码长度不能超过 100 位");
        }

        // 邮箱格式校验（如果提供）
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            String email = user.getEmail().trim();
            if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
                return Result.error("邮箱格式无效或长度超过100字符");
            }
            user.setEmail(email);
        }

        try {
            // 强制设置角色为普通用户（必须在createUser前设置，让Service层处理TOCTOU）
            // OPS 角色只能由管理员在后台创建，不允许通过注册接口创建
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
            if (authentication == null || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getName())) {
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
            // Input validation: keyword required and must be reasonable length
            if (keyword == null || keyword.trim().isEmpty()) {
                return Result.error("搜索关键词不能为空");
            }
            if (keyword.length() > 100) {
                return Result.error("搜索关键词长度不能超过100个字符");
            }
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

    /**
     * 二次鉴权提权
     * ADMIN 可提权到 OPS，OPS 可提权到 ADMIN。需要重新输入密码。
     */
    @PostMapping("/elevate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> elevate(@Valid @RequestBody ElevateRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Result.error("未认证");
            }

            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 暴力破解保护：检查提权尝试次数
            if (!elevationService.checkRateLimit(username)) {
                log.warn("Elevation rate limit exceeded for user {}", username);
                return Result.error("提权尝试次数过多，请 15 分钟后重试");
            }

            String currentRole = user.getRole();
            String targetRole = request.getTargetRole();

            // 验证当前角色是否允许提权
            if (!ELEVATION_ALLOWED.contains(currentRole)) {
                return Result.error("当前角色不支持提权");
            }

            // 验证目标角色有效性
            if (!ELEVATION_ALLOWED.contains(targetRole)) {
                return Result.error("无效的目标角色");
            }

            // 不允许提权到自己的角色
            if (currentRole.equals(targetRole)) {
                return Result.error("已拥有该角色权限");
            }

            // 验证密码
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                log.warn("Elevation failed: wrong password for user {}", username);
                return Result.error("密码验证失败");
            }

            // 写入提权状态到 Redis（按 username 存储，供 JwtFilter 读取）
            elevationService.elevate(username, targetRole);
            // 提权成功后清除失败计数
            elevationService.clearRateLimit(username);

            Map<String, Object> result = new HashMap<>();
            result.put("elevatedRole", targetRole);
            result.put("expiresAt", Instant.now().plusSeconds(7200).toString());

            log.info("User {} ({}) elevated to {}", username, currentRole, targetRole);
            return Result.success("提权成功", result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("提权失败", e);
            return Result.error("提权失败，请稍后重试");
        }
    }

    /**
     * 撤销提权
     */
    @PostMapping("/revoke-elevation")
    @PreAuthorize("isAuthenticated()")
    public Result<String> revokeElevation() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Result.error("未认证");
            }
            elevationService.revoke(authentication.getName());
            return Result.success("已撤销提权");
        } catch (Exception e) {
            log.error("撤销提权失败", e);
            return Result.error("撤销提权失败");
        }
    }

    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 7) {
                String token = authHeader.substring(7);
                // 同时撤销提权状态
                try {
                    String username = null;
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null && authentication.isAuthenticated()
                            && !"anonymousUser".equals(authentication.getName())) {
                        username = authentication.getName();
                    }
                    if (username == null && jwtUtil != null) {
                        username = jwtUtil.getUsernameFromToken(token);
                    }
                    if (username != null) {
                        elevationService.revoke(username);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to resolve logout username or revoke elevation: {}", ex.getMessage());
                }
                tokenBlacklistService.blacklistToken(token);
                log.info("User logged out, token blacklisted, elevation revoked");
            }
        } catch (Exception e) {
            // 即使黑名单操作失败，仍然返回登出成功（前端会清理本地 token）
            log.error("Failed to blacklist token during logout", e);
        }
        return Result.success("退出登录成功");
    }
}
