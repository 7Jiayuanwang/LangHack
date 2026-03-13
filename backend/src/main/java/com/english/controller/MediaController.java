package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Media;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.MediaService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    // mimeType 白名单，防止数据库中的值被篡改为 text/html 触发存储型 XSS
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "video/mp4", "video/webm", "video/ogg",
        "audio/mpeg", "audio/ogg", "audio/wav", "audio/webm",
        "application/pdf"
    );

    @Autowired
    private MediaService mediaService;

    @Autowired
    private UserService userService;

    @PostMapping("/upload")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mediaType") String mediaType) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Result.error("未授权");
            }
            String username = authentication.getName();
            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            Long userId = currentUser.getId();

            Media media = mediaService.uploadMedia(file, mediaType, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("mediaId", media.getId());
            result.put("fileName", media.getFileName());
            result.put("mediaType", media.getMediaType());
            result.put("fileSize", media.getFileSize());

            return Result.success("上传成功", result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("上传失败", e);

            return Result.error("上传失败，请稍后重试");
        }
    }

    @GetMapping("/{mediaId}")
    public Result<Media> getMediaDetail(@PathVariable Long mediaId) {
        try {
            // getMediaById already checks for deleted=0, so just check if media is null
            Media media = mediaService.getMediaById(mediaId);
            if (media == null) {
                return Result.error("媒体不存在");
            }
            return Result.success(media);
        } catch (Exception e) {
            logger.error("获取失败", e);

            return Result.error("获取失败，请稍后重试");
        }
    }

    @GetMapping("/{mediaId}/download")
    public ResponseEntity<byte[]> downloadMedia(@PathVariable Long mediaId) {
        try {
            // getMediaById already checks for deleted=0
            Media media = mediaService.getMediaById(mediaId);
            if (media == null) {
                logger.warn("Media not found or deleted: {}", mediaId);
                return ResponseEntity.notFound().build();
            }

            if (media.getFileContent() == null || media.getFileContent().length == 0) {
                logger.warn("Media content is empty: {}", mediaId);
                return ResponseEntity.noContent().build();
            }

            logger.debug("Serving media: {}, size: {} bytes", mediaId, media.getFileContent().length);

            // Sanitize filename to prevent HTTP header injection and path traversal
            String rawFileName = media.getFileName() != null ? media.getFileName() : "media_" + mediaId;
            // 移除所有可能导致 HTTP header 注入的字符：CR、LF、引号、反斜杠、分号等
            String safeFileName = rawFileName.replaceAll("[\r\n\"\\\\;]", "_");
            // 额外防御：移除路径分隔符
            safeFileName = safeFileName.replaceAll("[/\\\\]", "_");

            // 对 mimeType 做白名单校验，防止数据库值被篡改为 text/html 触发 XSS
            String rawMime = media.getMimeType();
            String safeMime = (rawMime != null && ALLOWED_MIME_TYPES.contains(rawMime))
                    ? rawMime : "application/octet-stream";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName + "\"")
                    .contentType(MediaType.parseMediaType(safeMime))
                    .body(media.getFileContent());
        } catch (Exception e) {
            logger.error("Error serving media {}", mediaId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{mediaId}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<String> deleteMedia(@PathVariable Long mediaId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Result.error("未授权");
            }
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            Media media = mediaService.getMediaById(mediaId);
            if (media == null) {
                return Result.error("媒体不存在");
            }
            if (!currentUser.getId().equals(media.getUploadedBy())
                    && !"ADMIN".equals(currentUser.getRole())) {
                return Result.error("无权删除此资源");
            }
            mediaService.deleteMedia(mediaId);
            return Result.success("删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除失败", e);

            return Result.error("删除失败，请稍后重试");
        }
    }
}
