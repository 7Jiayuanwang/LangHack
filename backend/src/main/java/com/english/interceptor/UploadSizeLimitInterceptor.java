package com.english.interceptor;

import com.english.dto.Result;
import com.english.service.SystemConfigService;
import com.english.util.SizeParseUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 文件上传大小限制拦截器 - 对齐 upload.content_size.* 配置体系
 * 优先级：用户特权 > 角色覆盖 > 全局默认 > 硬编码兜底
 */
@Component
public class UploadSizeLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UploadSizeLimitInterceptor.class);
    private static final long DEFAULT_IMAGE_SIZE = 10L  * 1024 * 1024;  // 10MB
    private static final long DEFAULT_VIDEO_SIZE = 512L * 1024 * 1024;  // 512MB
    private static final long DEFAULT_BACKUP_IMPORT_SIZE = 2L * 1024 * 1024 * 1024;  // 2GB

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

        String username = getCurrentUsername();
        String requestUri = request.getRequestURI();
        String mediaType = multipartRequest.getParameter("mediaType");
        long maxBytes = resolveMaxSize(requestUri, mediaType, username);

        for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet()) {
            MultipartFile file = entry.getValue();
            if (file.getSize() > maxBytes) {
                log.warn("文件上传被拒绝: user={}, filename={}, size={} bytes, limit={} bytes",
                        username, file.getOriginalFilename(), file.getSize(), maxBytes);
                response.setStatus(413);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(
                        Result.error(413, String.format("文件过大，限制为 %s，实际为 %s",
                                SizeParseUtil.formatSize(maxBytes), SizeParseUtil.formatSize(file.getSize())))));
                return false;
            }
        }
        return true;
    }

    /**
     * 按请求类型 + 当前用户解析允许的最大文件大小。
     * 优先级：用户特权 > 角色覆盖 > 全局默认 > 硬编码兜底
     */
    private long resolveMaxSize(String requestUri, String mediaType, String username) {
        if (isBackupImport(requestUri)) {
            return resolveConfiguredLimit("upload.content_size.backup_import", username, DEFAULT_BACKUP_IMPORT_SIZE);
        }

        boolean isVideo = "VIDEO".equalsIgnoreCase(mediaType);
        String typeKey = isVideo ? "upload.content_size.video" : "upload.content_size.image";
        long fallback = isVideo ? DEFAULT_VIDEO_SIZE : DEFAULT_IMAGE_SIZE;
        return resolveConfiguredLimit(typeKey, username, fallback);
    }

    private long resolveConfiguredLimit(String typeKey, String username, long fallback) {
        if (username != null) {
            String userVal = systemConfigService.get("content_limits", typeKey + ".user." + username, null);
            if (userVal != null) return SizeParseUtil.parseSize(userVal, fallback);
        }

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    String role = ga.getAuthority().replace("ROLE_", "");
                    String roleVal = systemConfigService.get("content_limits", typeKey + ".role." + role, null);
                    if (roleVal != null) return SizeParseUtil.parseSize(roleVal, fallback);
                }
            }
        } catch (Exception e) {
            log.debug("resolveMaxSize 角色查询失败，降级到全局默认: {}", e.getMessage());
        }

        String globalVal = systemConfigService.get("content_limits", typeKey, null);
        return SizeParseUtil.parseSize(globalVal, fallback);
    }

    private boolean isBackupImport(String requestUri) {
        return requestUri != null && requestUri.endsWith("/api/ops/backup/import");
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

}
