package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Media;
import com.english.mapper.MediaMapper;
import com.english.service.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    @Value("${media.upload.max-image-size:10485760}")
    private long maxImageSize;

    @Value("${media.upload.max-video-size:536870912}")
    private long maxVideoSize;

    @Value("${media.upload.storage-path:/app/uploads}")
    private String storagePath;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(storagePath, "video"));
            Files.createDirectories(Paths.get(storagePath, "image"));
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

            long maxSize = "IMAGE".equals(mediaType) ? maxImageSize : maxVideoSize;
            if (file.getSize() > maxSize) {
                String limit = maxSize / (1024 * 1024) + "MB";
                throw new BusinessException("文件大小超过限制（最大 " + limit + "）");
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

            if ("VIDEO".equals(mediaType)) {
                String filePath = saveToFileSystem(file, mediaType, originalFileName);
                media.setFilePath(filePath);
                media.setFileContent(new byte[0]);
            } else {
                try {
                    media.setFileContent(file.getBytes());
                } catch (IOException e) {
                    throw new BusinessException("文件读取失败");
                }
            }

            this.save(media);
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
            // 校验 MIME 类型
            if (!(mimeType != null && mimeType.startsWith("image/"))) {
                return false;
            }
            // 校验文件扩展名
            String ext = getFileExtension(fileName).toLowerCase();
            return !ext.isEmpty() && ALLOWED_IMAGE_EXTENSIONS.contains(ext);
        } else if ("VIDEO".equals(mediaType)) {
            // 校验 MIME 类型
            if (!(mimeType != null && mimeType.startsWith("video/"))) {
                return false;
            }
            // 校验文件扩展名
            String ext = getFileExtension(fileName).toLowerCase();
            return !ext.isEmpty() && ALLOWED_VIDEO_EXTENSIONS.contains(ext);
        }
        return false;
    }

    @Override
    public Media getMediaById(Long id) {
        // 注意：@TableLogic 已在 Media 实体定义，MyBatis Plus 会自动过滤已删除的记录
        // 所以这里不需要手动添加 eq("deleted", 0) 条件
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
}
