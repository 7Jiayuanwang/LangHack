package com.english.config;

import com.english.service.impl.AccessLogBufferService;
import com.english.service.impl.AccessMetricsService;
import com.english.monitor.UserRequestMonitor;
import com.english.entity.AccessLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

@Component
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogInterceptor.class);

    private final AccessLogBufferService bufferService;
    private final AccessMetricsService metricsService;
    private final UserRequestMonitor userRequestMonitor;

    public AccessLogInterceptor(AccessLogBufferService bufferService,
                                 AccessMetricsService metricsService,
                                 UserRequestMonitor userRequestMonitor) {
        this.bufferService = bufferService;
        this.metricsService = metricsService;
        this.userRequestMonitor = userRequestMonitor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("_startTime", System.currentTimeMillis());

        String username = extractUsername();
        String path = request.getRequestURI();

        metricsService.recordRequestStart(username);
        userRequestMonitor.recordRequest(username, path);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute("_startTime");
            long responseTimeMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

            metricsService.recordRequestEnd();

            String username = extractUsername();
            // 登录请求时 SecurityContext 尚未设置，从 Controller 存入的 attribute 补充
            if (username == null) {
                Object attrUser = request.getAttribute("_auth_username");
                if (attrUser instanceof String) {
                    username = (String) attrUser;
                }
            }

            AccessLog log = new AccessLog();
            log.setUsername(username);
            log.setMethod(request.getMethod());

            String uri = request.getRequestURI();
            if (uri != null && uri.length() > 500) {
                uri = uri.substring(0, 500);
            }
            log.setUri(uri);

            String queryString = request.getQueryString();
            if (queryString != null && queryString.length() > 1000) {
                queryString = queryString.substring(0, 1000);
            }
            log.setQueryString(queryString);

            log.setStatusCode(response.getStatus());
            log.setResponseTimeMs(responseTimeMs);
            log.setIpAddress(getClientIp(request));

            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 500) {
                userAgent = userAgent.substring(0, 500);
            }
            log.setUserAgent(userAgent);
            log.setCreatedTime(LocalDateTime.now());

            bufferService.add(log);

            // Track error count for monitoring dashboard
            if (response.getStatus() >= 400) {
                metricsService.recordError();
            }
        } catch (Exception e) {
            logger.debug("Failed to log access", e);
        }
    }

    private String extractUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int commaIdx = ip.indexOf(',');
            return commaIdx > 0 ? ip.substring(0, commaIdx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
