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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
            result.put("url", "/api/media/" + media.getId() + "/download");

            return Result.success("上传成功", result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("上传失败", e);

            return Result.error("上传失败，请稍后重试");
        }
    }

    @GetMapping("/{mediaId}")
    public Result<Map<String, Object>> getMediaDetail(@PathVariable Long mediaId) {
        try {
            // getMediaById already checks for deleted=0, so just check if media is null
            Media media = mediaService.getMediaById(mediaId);
            if (media == null) {
                return Result.error("媒体不存在");
            }
            // 返回安全 DTO Map，不暴露 fileContent/filePath/uploadedBy 等内部字段
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", media.getId());
            dto.put("fileName", media.getFileName());
            dto.put("mediaType", media.getMediaType());
            dto.put("fileSize", media.getFileSize());
            dto.put("mimeType", media.getMimeType());
            dto.put("textId", media.getTextId());
            dto.put("description", media.getDescription());
            dto.put("width", media.getWidth());
            dto.put("height", media.getHeight());
            dto.put("duration", media.getDuration());
            return Result.success(dto);
        } catch (Exception e) {
            logger.error("获取失败", e);

            return Result.error("获取失败，请稍后重试");
        }
    }

    @GetMapping("/{mediaId}/download")
    public ResponseEntity<Resource> downloadMedia(
            @PathVariable Long mediaId,
            @RequestHeader HttpHeaders headers) {
        try {
            Media media = mediaService.getMediaById(mediaId);
            if (media == null) {
                logger.warn("Media not found or deleted: {}", mediaId);
                return ResponseEntity.notFound().build();
            }

            String rawFileName = media.getFileName() != null ? media.getFileName() : "media_" + mediaId;
            String safeFileName = rawFileName.replaceAll("[\r\n\"\\\\;]", "_").replaceAll("[/\\\\]", "_");
            String rawMime = media.getMimeType();
            String safeMime = (rawMime != null && ALLOWED_MIME_TYPES.contains(rawMime))
                    ? rawMime : "application/octet-stream";

            if (media.getFilePath() != null && !media.getFilePath().isEmpty()) {
                return serveFromFileSystem(media.getFilePath(), safeFileName, safeMime, headers);
            }

            return serveFromBlob(media, safeFileName, safeMime, headers);
        } catch (Exception e) {
            logger.error("Error serving media {}", mediaId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<Resource> serveFromFileSystem(
            String filePath, String fileName, String mimeType, HttpHeaders headers) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            logger.warn("File not found on disk: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(path);
        long fileLength = Files.size(path);

        List<HttpRange> ranges = headers.getRange();
        if (!ranges.isEmpty()) {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileLength);
            long end = range.getRangeEnd(fileLength);
            long rangeLength = end - start + 1;

            ResourceRegion region = new ResourceRegion(resource, start, rangeLength);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE,
                            "bytes " + start + "-" + end + "/" + fileLength)
                    .contentLength(rangeLength)
                    .body(resource);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(fileLength)
                .body(resource);
    }

    private ResponseEntity<Resource> serveFromBlob(
            Media media, String fileName, String mimeType, HttpHeaders headers) {
        byte[] content = media.getFileContent();
        if (content == null || content.length == 0) {
            logger.warn("Media content is empty: {}", media.getId());
            return ResponseEntity.noContent().build();
        }

        long fileLength = content.length;

        List<HttpRange> ranges = headers.getRange();
        if (!ranges.isEmpty()) {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileLength);
            long end = range.getRangeEnd(fileLength);
            long rangeLength = end - start + 1;

            byte[] rangeContent = Arrays.copyOfRange(content, (int) start, (int) end + 1);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE,
                            "bytes " + start + "-" + end + "/" + fileLength)
                    .contentLength(rangeLength)
                    .body(new ByteArrayResource(rangeContent));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(fileLength)
                .body(new ByteArrayResource(content));
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
