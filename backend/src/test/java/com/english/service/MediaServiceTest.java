package com.english.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.Media;
import com.english.exception.BusinessException;
import com.english.mapper.MediaMapper;
import com.english.service.impl.MediaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import com.english.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 单元测试: MediaServiceImpl
 * 测试文件上传、验证、删除等操作
 */
public class MediaServiceTest {

    @Mock
    private MediaMapper mediaMapper;

    @InjectMocks
    private MediaServiceImpl mediaService;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        // 使用反射设置私有字段（@Value 注入的字段）
        java.lang.reflect.Field maxImageSizeField = MediaServiceImpl.class.getDeclaredField("maxImageSize");
        maxImageSizeField.setAccessible(true);
        Long maxImageSizeValue = 10L * 1024 * 1024; // 10MB
        if (maxImageSizeValue != null) {
            maxImageSizeField.set(mediaService, maxImageSizeValue);
        }

        java.lang.reflect.Field maxVideoSizeField = MediaServiceImpl.class.getDeclaredField("maxVideoSize");
        maxVideoSizeField.setAccessible(true);
        Long maxVideoSizeValue = 100L * 1024 * 1024; // 100MB
        if (maxVideoSizeValue != null) {
            maxVideoSizeField.set(mediaService, maxVideoSizeValue);
        }
    }

    // ==================== 文件上传测试 ====================

    /**
     * 测试: 上传有效的 JPG 图片成功
     * 期望: 文件被保存，返回 Media 对象
     */
    @Test
    public void testUploadMedia_validImage_success() throws Exception {
        // 准备测试数据
        byte[] imageContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // JPG 魔数
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            imageContent
        );
        Long uploadedBy = 1L;

        // 模拟 save 方法
        when(mediaMapper.insert(any(Media.class))).thenReturn(1);

        // 执行上传
        Media result = mediaService.uploadMedia(file, "IMAGE", uploadedBy);

        // 验证结果
        assertNotNull(result);
        assertEquals("test.jpg", result.getFileName());
        assertEquals("IMAGE", result.getMediaType());
        assertEquals("image/jpeg", result.getMimeType());
        assertEquals(imageContent.length, result.getFileSize());
        assertEquals(uploadedBy, result.getUploadedBy());
        assertEquals("APPROVED", result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        // 验证 save 被调用
        verify(mediaMapper, times(1)).insert(any(Media.class));
    }

    /**
     * 测试: 拒绝上传 SVG 文件（安全修复验证）
     * 期望: 抛出异常，因为 SVG 不在白名单中
     */
    @Test
    public void testUploadMedia_svgRejected() {
        // 准备测试数据
        byte[] svgContent = "<svg></svg>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.svg",
            "image/svg+xml",
            svgContent
        );

        // 执行上传，期望抛出异常
        assertThrows(BusinessException.class, () -> {
            mediaService.uploadMedia(file, "IMAGE", 1L);
        });

        // 验证 save 未被调用
        verify(mediaMapper, never()).insert(any(Media.class));
    }

    /**
     * 测试: 拒绝上传空文件
     * 期望: 抛出异常
     */
    @Test
    public void testUploadMedia_emptyFile_throwsException() {
        // 准备空文件
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.jpg",
            "image/jpeg",
            new byte[0]
        );

        // 执行上传，期望抛出异常
        assertThrows(BusinessException.class, () -> {
            mediaService.uploadMedia(file, "IMAGE", 1L);
        });

        // 验证 save 未被调用
        verify(mediaMapper, never()).insert(any(Media.class));
    }

    /**
     * 测试: MIME 类型不匹配扩展名
     * 期望: 抛出异常（MIME 检查失败）
     */
    @Test
    public void testUploadMedia_invalidMimeType_throwsException() {
        // 准备测试数据：MIME 类型声称是图片，但扩展名是视频
        byte[] imageContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.mp4", // 视频扩展名
            "image/jpeg", // 图片 MIME 类型
            imageContent
        );

        // 执行上传，期望抛出异常
        assertThrows(BusinessException.class, () -> {
            mediaService.uploadMedia(file, "IMAGE", 1L);
        });

        verify(mediaMapper, never()).insert(any(Media.class));
    }

    /**
     * 测试: 文件超过大小限制
     * 期望: 抛出异常
     */
    @Test
    public void testUploadMedia_fileTooLarge_throwsException() throws Exception {
        // 准备超大文件（11MB）
        byte[] largeContent = new byte[11 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large.jpg",
            "image/jpeg",
            largeContent
        );

        // 执行上传，期望抛出异常
        assertThrows(BusinessException.class, () -> {
            mediaService.uploadMedia(file, "IMAGE", 1L);
        });

        verify(mediaMapper, never()).insert(any(Media.class));
    }

    /**
     * 测试: 上传有效视频文件
     * 期望: 视频文件被保存成功
     */
    @Test
    public void testUploadMedia_validVideo_success() throws Exception {
        // 准备视频文件
        byte[] videoContent = new byte[]{0x00, 0x00, 0x00, 0x20}; // MP4 魔数
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "video.mp4",
            "video/mp4",
            videoContent
        );

        when(mediaMapper.insert(any(Media.class))).thenReturn(1);

        // 执行上传
        Media result = mediaService.uploadMedia(file, "VIDEO", 2L);

        // 验证结果
        assertNotNull(result);
        assertEquals("video.mp4", result.getFileName());
        assertEquals("VIDEO", result.getMediaType());
        assertEquals("video/mp4", result.getMimeType());
        assertEquals(2L, result.getUploadedBy());

        verify(mediaMapper, times(1)).insert(any(Media.class));
    }

    /**
     * 测试: 文件名为 null 时自动生成名称（无扩展名）
     * 期望: 由于自动生成的名称无扩展名，文件类型验证失败
     */
    @Test
    public void testUploadMedia_nullFileName_throwsException() {
        // 准备文件，不指定原始文件名
        byte[] imageContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        MockMultipartFile file = new MockMultipartFile(
            "file",
            null, // 无原始文件名
            "image/jpeg",
            imageContent
        );

        // 自动生成的名称 "upload_xxx" 无扩展名，验证应失败
        assertThrows(BusinessException.class, () -> {
            mediaService.uploadMedia(file, "IMAGE", 1L);
        });

        verify(mediaMapper, never()).insert(any(Media.class));
    }

    // ==================== 获取媒体测试 ====================

    /**
     * 测试: 获取存在的媒体
     * 期望: 返回 Media 对象
     */
    @Test
    public void testGetMediaById_existingMedia_success() {
        // 准备测试数据
        Long mediaId = 1L;
        Media media = new Media();
        media.setId(mediaId);
        media.setFileName("test.jpg");
        media.setMediaType("IMAGE");
        media.setDeleted(0);

        // 模拟查询
        when(mediaMapper.selectOne(any(QueryWrapper.class))).thenReturn(media);

        // 执行查询
        Media result = mediaService.getMediaById(mediaId);

        // 验证结果
        assertNotNull(result);
        assertEquals(mediaId, result.getId());
        assertEquals("test.jpg", result.getFileName());
        assertEquals("IMAGE", result.getMediaType());

        verify(mediaMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    /**
     * 测试: 获取已删除的媒体
     * 期望: 返回 null（deleted=1 被过滤）
     */
    @Test
    public void testGetMediaById_deletedMedia_returnsNull() {
        // 准备测试数据
        Long mediaId = 1L;

        // 模拟查询结果为空（已被删除）
        when(mediaMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // 执行查询
        Media result = mediaService.getMediaById(mediaId);

        // 验证结果
        assertNull(result);
        verify(mediaMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    /**
     * 测试: 获取不存在的媒体
     * 期望: 返回 null
     */
    @Test
    public void testGetMediaById_nonExistentMedia_returnsNull() {
        Long mediaId = 999L;

        when(mediaMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        Media result = mediaService.getMediaById(mediaId);

        assertNull(result);
        verify(mediaMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    // ==================== 删除媒体测试 ====================

    /**
     * 测试: 删除媒体设置 deleted 标志
     * 期望: deleted 字段被设置为 1，时间戳更新
     */
    @Test
    public void testDeleteMedia_setsDeletedFlag() {
        // 准备测试数据
        Long mediaId = 1L;

        when(mediaMapper.updateById(any(Media.class))).thenReturn(1);

        // 执行删除
        mediaService.deleteMedia(mediaId);

        // 验证更新被调用，且 deleted 被设置为 1
        verify(mediaMapper, times(1)).updateById(argThat(media ->
            media.getId().equals(mediaId) &&
            media.getDeleted() == 1 &&
            media.getUpdatedAt() != null
        ));
    }

    /**
     * 测试: 多次删除同一媒体
     * 期望: 每次删除都调用 updateById
     */
    @Test
    public void testDeleteMedia_multipleDeletes() {
        Long mediaId = 1L;
        when(mediaMapper.updateById(any(Media.class))).thenReturn(1);

        mediaService.deleteMedia(mediaId);
        mediaService.deleteMedia(mediaId);

        verify(mediaMapper, times(2)).updateById(any(Media.class));
    }

    /**
     * 测试: 删除不存在的媒体
     * 期望: updateById 仍被调用，但返回 0
     */
    @Test
    public void testDeleteMedia_nonExistentMedia() {
        Long mediaId = 999L;
        when(mediaMapper.updateById(any(Media.class))).thenReturn(0);

        // 执行删除（不应抛异常）
        mediaService.deleteMedia(mediaId);

        verify(mediaMapper, times(1)).updateById(any(Media.class));
    }

    // ==================== 边界和异常测试 ====================

    /**
     * 测试: 支持的图片格式都通过验证
     * 期望: JPG, PNG, GIF, WEBP 都被接受
     */
    @Test
    public void testUploadMedia_allImageFormatsSupported() throws Exception {
        String[] formats = {"jpg", "jpeg", "png", "gif", "webp"};
        String[] mimes = {"image/jpeg", "image/jpeg", "image/png", "image/gif", "image/webp"};

        for (int i = 0; i < formats.length; i++) {
            byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8};
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "test." + formats[i],
                mimes[i],
                content
            );

            when(mediaMapper.insert(any(Media.class))).thenReturn(1);

            Media result = mediaService.uploadMedia(file, "IMAGE", 1L);
            assertNotNull(result, "Format " + formats[i] + " should be supported");
        }
    }

    /**
     * 测试: 支持的视频格式都通过验证
     * 期望: MP4, WEBM, OGG, MOV, AVI, MKV 都被接受
     */
    @Test
    public void testUploadMedia_allVideoFormatsSupported() throws Exception {
        String[] formats = {"mp4", "webm", "ogg", "mov", "avi", "mkv"};
        String[] mimes = {"video/mp4", "video/webm", "video/ogg", "video/quicktime", "video/x-msvideo", "video/x-matroska"};

        for (int i = 0; i < formats.length; i++) {
            byte[] content = new byte[]{0x00, 0x00, 0x00, 0x20};
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "test." + formats[i],
                mimes[i],
                content
            );

            when(mediaMapper.insert(any(Media.class))).thenReturn(1);

            Media result = mediaService.uploadMedia(file, "VIDEO", 1L);
            assertNotNull(result, "Format " + formats[i] + " should be supported");
        }
    }

    /**
     * 测试: 无效的媒体类型
     * 期望: 抛出异常
     */
    @Test
    public void testUploadMedia_invalidMediaType() {
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8};
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            content
        );

        assertThrows(BusinessException.class, () -> {
            mediaService.uploadMedia(file, "AUDIO", 1L); // 不支持 AUDIO
        });
    }

}
