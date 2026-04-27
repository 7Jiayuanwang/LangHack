package com.english.service;

import com.english.entity.Video;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface VideoService extends IService<Video> {

    Map<String, Object> getVideosByPage(Map<String, Object> params);

    Video getVideoById(Long id);

    void createVideo(Video video);

    void updateVideo(Video video);

    void deleteVideo(Long id);

    List<Video> getVideosByUploader(Long userId);

    List<Video> getFeaturedVideos(int count);

    List<Video> getPublicVideos(int page, int size, String keyword);

    Map<String, Object> getVideoStats();

    void incrementViewCount(Long videoId);

    void incrementLikeCount(Long videoId);

    // 后期扩展：字幕转文章
    // Long transcribeToArticle(Long videoId);
}
