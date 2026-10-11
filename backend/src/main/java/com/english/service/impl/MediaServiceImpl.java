package com.english.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Media;
import com.english.mapper.MediaMapper;
import com.english.service.MediaService;
import com.english.service.MediaTranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.english.exception.BusinessException;
import com.english.service.SystemConfigService;
import com.english.util.SizeParseUtil;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaServiceImpl.class);

    private final Semaphore uploadSemaphore = new Semaphore(5);

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "ogg", "mov", "avi", "mkv");
    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = Set.of("mp3", "wav", "ogg", "webm", "m4a", "aac");
    private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS = Set.of("pdf");

    @Value("${media.upload.max-image-size:10485760}")
    private long defaultMaxImageSize;

    @Value("${media.upload.max-video-size:536870912}")
    private long defaultMaxVideoSize;
    @Value("${media.upload.max-audio-size:536870912}")
    private long defaultMaxAudioSize;

    @Value("${media.upload.storage-path:/app/uploads}")
    private String storagePath;

    @Autowired
    private MediaTranscodeService transcodeService;

    @Autowired
    private SystemConfigService systemConfigService;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(storagePath, "video"));
            Files.createDirectories(Paths.get(storagePath, "image"));
            Files.createDirectories(Paths.get(storagePath, "audio"));
        } catch (IOException e) {
            logger.error("Failed to create upload directories", e);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "media", allEntries = true)
    public Media uploadMedia(MultipartFile file, String mediaType, Long uploadedBy) {
        try {
            if (!uploadSemaphore.tryAcquire(30, TimeUnit.SECONDS)) {
                throw new BusinessException("服务器上传队列已满，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("上传操作被中断");
        }
        try {
            if (file.isEmpty()) {
                throw new BusinessException("文件不能为空");
            }

            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null || originalFileName.isEmpty()) {
                originalFileName = "upload_" + System.currentTimeMillis();
            }
            originalFileName = new java.io.File(originalFileName).getName()
                    .replaceAll("[/\\\\]", "_");
            if (originalFileName.isEmpty()) {
                originalFileName = "upload_" + System.currentTimeMillis();
            }
            String mimeType = file.getContentType();

            long maxSize = resolveMaxSize(mediaType, uploadedBy);
            if (file.getSize() > maxSize) {
                throw new BusinessException("文件大小超过限制（最大 " + SizeParseUtil.formatSize(maxSize) + "）");
            }

            if (!isValidMediaType(mediaType, mimeType, originalFileName)) {
                throw new BusinessException("不支持的文件类型");
            }

            Media media = new Media();
            media.setMediaType(mediaType);
            media.setFileName(originalFileName);
            media.setFileSize(file.getSize());
            media.setMimeType(mimeType);
            media.setUploadedBy(uploadedBy);
            media.setStatus("APPROVED");
            media.setCreatedAt(LocalDateTime.now());
            media.setUpdatedAt(LocalDateTime.now());

            if ("VIDEO".equals(mediaType) || "AUDIO".equals(mediaType)) {
                String filePath = saveToFileSystem(file, mediaType, originalFileName);
                media.setFilePath(filePath);
                media.setFileContent(new byte[0]);

                MediaTranscodeService.ProbeResult probe = "VIDEO".equals(mediaType)
                        ? transcodeService.probe(new File(filePath)) : null;
                if (probe != null) {
                    media.setOriginalCodec(probe.videoCodec);
                    if (probe.width > 0) media.setWidth(probe.width);
                    if (probe.height > 0) media.setHeight(probe.height);
                }
                if ("VIDEO".equals(mediaType) && probe != null && transcodeService.needsTranscode(probe)) {
                    media.setTranscodeStatus("PENDING");
                } else {
                    media.setTranscodeStatus("SKIPPED");
                }
            } else {
                try {
                    media.setFileContent(file.getBytes());
                } catch (IOException e) {
                    throw new BusinessException("文件读取失败");
                }
                media.setTranscodeStatus("SKIPPED");
            }

            this.save(media);

            if ("VIDEO".equals(mediaType) && "PENDING".equals(media.getTranscodeStatus())) {
                final Long mediaId = media.getId();
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            transcodeService.transcodeAsync(mediaId);
                        }
                    });
                } else {
                    transcodeService.transcodeAsync(mediaId);
                }
            }
            return media;
        } finally {
            uploadSemaphore.release();
        }
    }

    private String saveToFileSystem(MultipartFile file, String mediaType, String originalFileName) {
        String ext = getFileExtension(originalFileName);
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String uniqueName = UUID.randomUUID().toString() + (ext.isEmpty() ? "" : "." + ext);
        Path dir = Paths.get(storagePath, mediaType.toLowerCase(), dateDir);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(uniqueName);
            file.transferTo(target.toFile());
            return target.toString();
        } catch (IOException e) {
            logger.error("Failed to save file to disk: {}", originalFileName, e);
            throw new BusinessException("文件保存失败");
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private boolean isValidMediaType(String mediaType, String mimeType, String fileName) {
        if ("IMAGE".equals(mediaType)) {
            if (!(mimeType != null && mimeType.startsWith("image/"))) {
                return false;
            }
            String ext = getFileExtension(fileName).toLowerCase();
            return !ext.isEmpty() && ALLOWED_IMAGE_EXTENSIONS.contains(ext);
        } else if ("VIDEO".equals(mediaType)) {
            if (!(mimeType != null && mimeType.startsWith("video/"))) {
                return false;
            }
            String ext = getFileExtension(fileName).toLowerCase();
            return !ext.isEmpty() && ALLOWED_VIDEO_EXTENSIONS.contains(ext);
        } else if ("AUDIO".equals(mediaType)) {
            // 兼容浏览器/客户端对 mp3 等文件返回不同 MIME（audio/mpeg、audio/mp3、application/octet-stream 甚至空值）的情况，
            // 只要文件扩展名是合法音频扩展名即放行
            String ext = getFileExtension(fileName).toLowerCase();
            return !ext.isEmpty() && ALLOWED_AUDIO_EXTENSIONS.contains(ext);
        } else if ("DOCUMENT".equals(mediaType)) {
            String ext = getFileExtension(fileName).toLowerCase();
            return "application/pdf".equals(mimeType) && ALLOWED_DOCUMENT_EXTENSIONS.contains(ext);
        }
        return false;
    }

    @Override
    public Media getMediaById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "media", allEntries = true)
    public void deleteMedia(Long id) {
        Media existing = getMediaById(id);
        if (existing == null) {
            throw new BusinessException("媒体文件不存在或已被删除");
        }
        removeById(id);
        if (existing.getFilePath() != null && !existing.getFilePath().isEmpty()) {
            try {
                Files.deleteIfExists(Paths.get(existing.getFilePath()));
            } catch (IOException e) {
                logger.warn("Failed to delete file on disk: {}", existing.getFilePath(), e);
            }
        }
    }

    /**
     * 按内容类型 + 上传者角色解析允许的最大文件大小。
     * 优先级：角色覆盖 (content_limits.upload.content_size.{type}.role.{role})
     *          > 全局默认 (content_limits.upload.content_size.{type})
     *          > 环境变量兜底
     */
    private long resolveMaxSize(String mediaType, Long uploadedBy) {
        boolean isImage = "IMAGE".equals(mediaType);
        boolean isAudio = "AUDIO".equals(mediaType);
        boolean isDocument = "DOCUMENT".equals(mediaType);
        String typeKey  = isImage || isDocument ? "upload.content_size.image" : isAudio ? "upload.content_size.audio" : "upload.content_size.video";
        long   envFallback = isImage || isDocument ? defaultMaxImageSize : isAudio ? defaultMaxAudioSize : defaultMaxVideoSize;

        // 尝试读取角色覆盖
        if (uploadedBy != null) {
            try {
                com.english.entity.User user = baseMapper.selectById(uploadedBy) != null
                        ? null : null; // 不走 mapper，改走 SecurityContext 减少 DB 查询
                // 从 SecurityContext 取角色，避免额外 DB 查询
                org.springframework.security.core.Authentication auth =
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication();
                if (auth != null && auth.getAuthorities() != null) {
                    for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                        String role = ga.getAuthority().replace("ROLE_", "");
                        String roleKey = typeKey + ".role." + role;
                        String roleVal = systemConfigService.get("content_limits", roleKey, null);
                        if (roleVal != null) {
                            return SizeParseUtil.parseSize(roleVal, envFallback);
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("resolveMaxSize 角色覆盖查询失败，降级到全局默认: {}", e.getMessage());
            }
        }

        String globalVal = systemConfigService.get("content_limits", typeKey, null);
        return SizeParseUtil.parseSize(globalVal, envFallback);
    }
}
