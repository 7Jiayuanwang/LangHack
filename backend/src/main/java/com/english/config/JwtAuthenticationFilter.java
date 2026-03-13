package com.english.config;

import com.english.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "TEACHER", "STUDENT", "USER");

    @Autowired
    private JwtUtil jwtUtil;

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

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String role = jwtUtil.getRoleFromToken(jwtToken);

                if (role == null || role.trim().isEmpty()) {
                    logger.warn("JWT token missing or invalid role claim for user: {}", username);
                } else if (!VALID_ROLES.contains(role)) {
                    logger.warn("JWT token contains invalid role [{}] for user: {}", role, username);
                } else if (!jwtUtil.validateToken(jwtToken, username)) {
                    logger.warn("JWT token validation failed for user: {}", username);
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
            } catch (Exception e) {
                logger.warn("JWT Token validation failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}