package com.english.util;

import com.english.entity.User;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 安全工具类
 * 提供统一的用户身份获取方法，包含完善的空值检查
 */
@Component
public class SecurityUtil {

    private static UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        SecurityUtil.userService = userService;
    }

    /**
     * 获取当前登录的学生ID
     *
     * @param authentication Spring Security认证对象
     * @return 学生ID
     * @throws IllegalStateException 如果未认证、用户不存在或用户ID为空
     */
    public static Long getCurrentStudentId(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("用户未登录");
        }

        String username = authentication.getName();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("无法获取用户名");
        }

        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new IllegalStateException("用户不存在：" + username);
        }

        Long userId = user.getId();
        if (userId == null) {
            throw new IllegalStateException("用户ID为空：" + username);
        }

        return userId;
    }

    /**
     * 获取当前登录的教师ID
     *
     * @param authentication Spring Security认证对象
     * @return 教师ID
     * @throws IllegalStateException 如果未认证、用户不存在或用户ID为空
     */
    public static Long getCurrentTeacherId(Authentication authentication) {
        return getCurrentUserId(authentication, "教师");
    }

    /**
     * 获取当前登录的管理员ID
     *
     * @param authentication Spring Security认证对象
     * @return 管理员ID
     * @throws IllegalStateException 如果未认证、用户不存在或用户ID为空
     */
    public static Long getCurrentAdminId(Authentication authentication) {
        return getCurrentUserId(authentication, "管理员");
    }

    /**
     * 获取当前登录的用户ID（通用方法）
     *
     * @param authentication Spring Security认证对象
     * @return 用户ID
     * @throws IllegalStateException 如果未认证、用户不存在或用户ID为空
     */
    public static Long getCurrentUserId(Authentication authentication) {
        return getCurrentUserId(authentication, "用户");
    }

    /**
     * 获取当前登录的用户ID（内部方法，带角色描述）
     *
     * @param authentication Spring Security认证对象
     * @param roleDescription 角色描述（用于错误信息）
     * @return 用户ID
     * @throws IllegalStateException 如果未认证、用户不存在或用户ID为空
     */
    private static Long getCurrentUserId(Authentication authentication, String roleDescription) {
        if (authentication == null) {
            throw new IllegalStateException(roleDescription + "未登录");
        }

        String username = authentication.getName();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("无法获取" + roleDescription + "用户名");
        }

        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new IllegalStateException(roleDescription + "不存在：" + username);
        }

        Long userId = user.getId();
        if (userId == null) {
            throw new IllegalStateException(roleDescription + "ID为空：" + username);
        }

        return userId;
    }

    /**
     * 获取当前登录的用户信息
     *
     * @param authentication Spring Security认证对象
     * @return 用户对象
     * @throws IllegalStateException 如果未认证或用户不存在
     */
    public static User getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("用户未登录");
        }

        String username = authentication.getName();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("无法获取用户名");
        }

        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new IllegalStateException("用户不存在：" + username);
        }

        return user;
    }

    /**
     * 安全地获取用户名（不抛出异常，返回null表示未登录）
     *
     * @param authentication Spring Security认证对象
     * @return 用户名，如果未登录则返回null
     */
    public static String getUsername(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return authentication.getName();
    }
}
