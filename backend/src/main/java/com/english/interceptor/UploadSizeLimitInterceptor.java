package com.english.interceptor;

import com.english.dto.Result;
import com.english.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 文件上传大小限制拦截器 - 支持全局和用户级动态限制
 */
@Component
public class UploadSizeLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UploadSizeLimitInterceptor.class);

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(request instanceof MultipartHttpServletRequest)) {
            return true;
        }

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();

        if (fileMap.isEmpty()) {
            return true;
        }

        // 获取当前用户
        String username = getCurrentUsername();

        // 获取用户特定限制，如果不存在则使用全局限制
        long maxSizeBytes = getUserMaxFileSize(username);

        // 检查每个上传文件
        for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet()) {
            MultipartFile file = entry.getValue();
            long fileSize = file.getSize();

            if (fileSize > maxSizeBytes) {
                log.warn("文件上传被拒绝: user={}, filename={}, size={} bytes, limit={} bytes",
                        username, file.getOriginalFilename(), fileSize, maxSizeBytes);

                response.setStatus(413); // Payload Too Large
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(413, String.format("文件过大，限制为 %s，实际为 %s",
                            formatSize(maxSizeBytes), formatSize(fileSize)))
                ));
                return false;
            }
        }

        return true;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    private long getUserMaxFileSize(String username) {
        if (username != null) {
            String userLimitKey = "upload.max_file_size.user." + username;
            try {
                com.english.entity.SystemConfig config = systemConfigService.getByKey(userLimitKey);
                if (config != null) {
                    return parseSize(config.getConfigValue());
                }
            } catch (Exception e) {
                log.warn("获取用户上传限制失败: {}", e.getMessage());
            }
        }

        // 使用全局限制
        try {
            com.english.entity.SystemConfig globalConfig = systemConfigService.getByKey("upload.max_file_size.global");
            if (globalConfig != null) {
                return parseSize(globalConfig.getConfigValue());
            }
        } catch (Exception e) {
            log.warn("获取全局上传限制失败: {}", e.getMessage());
        }

        // 默认 10MB
        return 10 * 1024 * 1024;
    }

    private long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.trim().isEmpty()) {
            return 10 * 1024 * 1024;
        }

        sizeStr = sizeStr.trim().toUpperCase();
        long multiplier = 1;

        if (sizeStr.endsWith("KB")) {
            multiplier = 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        } else if (sizeStr.endsWith("MB")) {
            multiplier = 1024 * 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        } else if (sizeStr.endsWith("GB")) {
            multiplier = 1024 * 1024 * 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        }

        try {
            return Long.parseLong(sizeStr.trim()) * multiplier;
        } catch (NumberFormatException e) {
            log.warn("解析文件大小失败: {}", sizeStr);
            return 10 * 1024 * 1024;
        }
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        return bytes + " bytes";
    }
}
