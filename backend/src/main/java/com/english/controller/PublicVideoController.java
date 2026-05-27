package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Video;
import com.english.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 公开视频控制器
 * 为所有用户提供推荐视频访问
 */
@RestController
@RequestMapping("/api/public/videos")
public class PublicVideoController {

    private static final Logger log = LoggerFactory.getLogger(PublicVideoController.class);

    @Autowired
    private VideoService videoService;

    /**
     * 获取首页推荐视频
     */
    @GetMapping("/featured")
    public Result<List<Video>> getFeaturedVideos(
            @RequestParam(required = false) Integer count) {
        try {
            List<Video> videos = videoService.getFeaturedVideos(count);
            return Result.success(videos);
        } catch (Exception e) {
            log.error("获取推荐视频失败", e);
            return Result.error("获取推荐视频失败，请稍后重试");
        }
    }

    /**
     * 获取随机公开视频列表
     */
    @GetMapping("/random")
    public Result<List<Video>> getRandomVideos(
            @RequestParam(defaultValue = "8") int count,
            @RequestParam(required = false) String keyword) {
        try {
            count = Math.min(Math.max(count, 1), 50);
            List<Video> videos = videoService.getPublicVideos(1, count, keyword);
            if (videos != null && !videos.isEmpty()) {
                Collections.shuffle(videos);
            }
            return Result.success(videos != null ? videos : new ArrayList<>());
        } catch (Exception e) {
            log.error("获取随机视频失败", e);
            return Result.error("获取随机视频失败，请稍后重试");
        }
    }

    /**
     * 获取视频详情
     */
    @GetMapping("/{videoId}")
    public Result<Video> getVideoDetail(@PathVariable Long videoId) {
        try {
            Video video = videoService.getVideoById(videoId);
            if (video == null) {
                return Result.error("视频不存在");
            }

            // 仅公开视频可通过公开接口访问
            if (video.getIsPublic() == null || video.getIsPublic() != 1) {
                return Result.error("视频不存在或未公开");
            }

            videoService.incrementViewCount(videoId);
            return Result.success(video);
        } catch (Exception e) {
            log.error("获取视频详情失败, videoId={}", videoId, e);
            return Result.error("获取视频详情失败，请稍后重试");
        }
    }

    /**
     * 获取公开视频统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getVideoStats() {
        try {
            return Result.success(videoService.getVideoStats());
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return Result.error("获取统计数据失败，请稍后重试");
        }
    }
}
