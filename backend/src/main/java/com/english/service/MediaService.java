package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.Media;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService extends IService<Media> {
    Media uploadMedia(MultipartFile file, String mediaType, Long uploadedBy);

    /**
     * 根据ID获取未删除的媒体
     */
    Media getMediaById(Long id);

    /**
     * 逻辑删除媒体
     */
    void deleteMedia(Long id);
}
