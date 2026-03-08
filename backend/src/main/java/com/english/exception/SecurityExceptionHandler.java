package com.english.exception;

import com.english.dto.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Spring Security 过滤器层的异常处理。
 *
 * 职责:
 *   - {@link AuthenticationEntryPoint}: 请求未携带/携带无效 Token → 401
 *   - {@link AccessDeniedHandler}:      Token 合法但权限不足   → 403
 *
 * 使用同一个 bean 同时实现两个接口，在 SecurityConfig 中分别注册。
 */
@Slf4j
@Component
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 未认证时调用（401 Unauthorized）
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthenticated access to [{}]: {}", request.getRequestURI(), authException.getMessage());
        writeJson(response, HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期，请重新登录");
    }

    /**
     * 已认证但权限不足时调用（403 Forbidden）
     */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied to [{}]: {}", request.getRequestURI(), accessDeniedException.getMessage());
        writeJson(response, HttpStatus.FORBIDDEN.value(), "权限不足，无法执行该操作");
    }

    private void writeJson(HttpServletResponse response, int httpStatus, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<?> result = Result.error(httpStatus, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
