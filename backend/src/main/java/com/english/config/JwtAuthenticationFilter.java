package com.english.config;

import com.english.entity.User;
import com.english.service.TokenBlacklistService;
import com.english.service.UserService;
import com.english.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "TEACHER", "STUDENT", "USER");

    /**
     * Redis 缓存前缀和 TTL：缓存用户活跃状态，避免每次请求查库。
     * 缓存值 "1" = 用户有效（存在且未删除），缺失 = 需要重新校验。
     * TTL 5 分钟：用户被删除/禁用后最多 5 分钟内仍可访问。
     */
    private static final String USER_ACTIVE_CACHE_PREFIX = "user:active:";
    private static final long USER_ACTIVE_CACHE_TTL_SECONDS = 300; // 5 minutes

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    @Lazy
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String requestTokenHeader = request.getHeader("Authorization");

        String username = null;
        String jwtToken = null;

        // 检查 Authorization header：必须是 "Bearer " 开头，且总长度 > 7 (至少要有1个token字符)
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ") && requestTokenHeader.length() > 7) {
            jwtToken = requestTokenHeader.substring(7);
            // 检查token是否为空
            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                jwtToken = null;
            }
            try {
                username = jwtUtil.getUsernameFromToken(jwtToken);
            } catch (Exception e) {
                logger.warn("Unable to get JWT Token");
            }
        }

        // SSE fallback: support token via query parameter for EventSource endpoints
        if (jwtToken == null) {
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.trim().isEmpty()) {
                jwtToken = tokenParam.trim();
                try {
                    username = jwtUtil.getUsernameFromToken(jwtToken);
                } catch (Exception e) {
                    logger.warn("Unable to get JWT Token from query parameter");
                }
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 检查 token 是否在黑名单中（已登出的 token）
                if (tokenBlacklistService.isBlacklisted(jwtToken)) {
                    logger.warn("Blacklisted token used for user: {}", username);
                    // 不设置 SecurityContext，用户被视为未认证
                } else {
                    String role = jwtUtil.getRoleFromToken(jwtToken);

                    if (role == null || role.trim().isEmpty()) {
                        logger.warn("JWT token missing or invalid role claim for user: {}", username);
                    } else if (!VALID_ROLES.contains(role)) {
                        logger.warn("JWT token contains invalid role [{}] for user: {}", role, username);
                    } else if (!jwtUtil.validateToken(jwtToken, username)) {
                        logger.warn("JWT token validation failed for user: {}", username);
                    } else if (!isUserActive(username, role)) {
                        logger.warn("User '{}' is deleted/disabled or role changed, rejecting token", username);
                    } else {
                        UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                            );
                        authToken.setDetails(username); // 保存用户名用于后续审计
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception e) {
                logger.warn("JWT Token validation failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 校验用户是否仍然活跃（未删除/未禁用、角色匹配）。
     * 使用 Redis 缓存减少数据库查询：缓存有效期 5 分钟。
     *
     * @param username 用户名
     * @param tokenRole JWT 中的角色
     * @return true 如果用户活跃且角色匹配
     */
    private boolean isUserActive(String username, String tokenRole) {
        String cacheKey = USER_ACTIVE_CACHE_PREFIX + username + ":" + tokenRole;

        // 先查 Redis 缓存
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if ("1".equals(cached)) {
                return true;
            }
        } catch (Exception e) {
            // Redis 不可用时降级为直接查库
            logger.debug("Redis cache unavailable for user active check, falling back to DB");
        }

        // 缓存未命中或 Redis 不可用 → 查库
        // getUserByUsername 内部已有 deleted=0 过滤，查不到即已删除
        User user = userService.getUserByUsername(username);
        if (user == null) {
            logger.warn("User '{}' not found (deleted?), invalidating token", username);
            return false;
        }

        // 校验角色是否与 token 一致（防止角色变更后旧 token 仍有高权限）
        if (!tokenRole.equals(user.getRole())) {
            logger.warn("User '{}' role changed from '{}' to '{}', invalidating token",
                    username, tokenRole, user.getRole());
            return false;
        }

        // 写入缓存
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, "1",
                    USER_ACTIVE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("Failed to cache user active status, will check DB next time");
        }

        return true;
    }
}