package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Media;
import com.english.mapper.MediaMapper;
import com.english.service.MediaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements MediaService {

    private final Semaphore uploadSemaphore = new Semaphore(5);

    // 文件扩展名白名单
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "ogg", "mov", "avi", "mkv");

    @Value("${media.upload.max-image-size:10485760}")
    private long maxImageSize;

    @Value("${media.upload.max-video-size:104857600}")
    private long maxVideoSize;

    @Override
    @Transactional
    @CacheEvict(value = "media", allEntries = true)
    public Media uploadMedia(MultipartFile file, String mediaType, Long uploadedBy) throws Exception {
        if (!uploadSemaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new BusinessException("服务器上传队列已满，请稍后重试");
        }
        try {
            if (file.isEmpty()) {
                throw new BusinessException("文件不能为空");
            }

            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null || originalFileName.isEmpty()) {
                originalFileName = "upload_" + System.currentTimeMillis();
            }
            String mimeType = file.getContentType();

            // 验证文件大小（在读取文件内容之前，防止大文件 OOM）
            long maxSize = "IMAGE".equals(mediaType) ? maxImageSize : maxVideoSize;
            if (file.getSize() > maxSize) {
                String limit = maxSize / (1024 * 1024) + "MB";
                throw new BusinessException("文件大小超过限制（最大 " + limit + "）");
            }

            // 验证文件类型（MIME + 扩展名）
            if (!isValidMediaType(mediaType, mimeType, originalFileName)) {
                throw new BusinessException("不支持的文件类型");
            }

            // 读取文件内容（已通过文件大小检查）
            byte[] fileContent = file.getBytes();

            // 创建Media对象
            Media media = new Media();
            media.setMediaType(mediaType);
            media.setFileName(originalFileName);
            media.setFileContent(fileContent);
            media.setFileSize(file.getSize());
            media.setMimeType(mimeType);
            media.setUploadedBy(uploadedBy);
            media.setStatus("APPROVED");
            media.setCreatedAt(LocalDateTime.now());
            media.setUpdatedAt(LocalDateTime.now());

            // 保存到数据库
            this.save(media);
            return media;
        } finally {
            uploadSemaphore.release();
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
        QueryWrapper<Media> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("deleted", 0);
        return getOne(wrapper);
    }

    @Override
    @Transactional
    @CacheEvict(value = "media", allEntries = true)
    public void deleteMedia(Long id) {
        Media media = new Media();
        media.setId(id);
        media.setDeleted(1);
        media.setUpdatedAt(LocalDateTime.now());
        updateById(media);
    }
}
