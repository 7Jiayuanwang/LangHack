package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Video;
import com.english.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.english.entity.User;
import com.english.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员视频管理控制器
 */
@RestController
@RequestMapping("/api/admin/videos")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVideoController {

    private static final Logger log = LoggerFactory.getLogger(AdminVideoController.class);

    @Autowired
    private VideoService videoService;

    @Autowired
    private UserService userService;

    /**
     * 分页获取视频列表
     */
    @GetMapping
    public Result<Map<String, Object>> getVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (keyword != null && !keyword.trim().isEmpty()) {
                params.put("keyword", keyword);
            }
            Map<String, Object> result = videoService.getVideosByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取视频列表失败", e);
            return Result.error("获取视频列表失败");
        }
    }

    /**
     * 获取视频统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getVideoStats() {
        try {
            return Result.success(videoService.getVideoStats());
        } catch (Exception e) {
            log.error("获取视频统计失败", e);
            return Result.error("获取视频统计失败");
        }
    }

    /**
     * 获取视频详情
     */
    @GetMapping("/{id}")
    public Result<Video> getVideoDetail(@PathVariable Long id) {
        try {
            Video video = videoService.getVideoById(id);
            if (video == null) {
                return Result.error("视频不存在");
            }
            return Result.success(video);
        } catch (Exception e) {
            log.error("获取视频详情失败, id={}", id, e);
            return Result.error("获取视频详情失败");
        }
    }

    /**
     * 创建视频
     */
    @PostMapping
    public Result<Map<String, Object>> createVideo(@RequestBody Video video) {
        try {
            if (video.getTitle() == null || video.getTitle().trim().isEmpty()) {
                return Result.error("视频标题不能为空");
            }
            if (video.getMediaId() == null) {
                return Result.error("视频文件不能为空");
            }

            // 获取当前管理员用户ID
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User adminUser = userService.getUserByUsername(username);
            if (adminUser != null) {
                video.setUserId(adminUser.getId());
            } else {
                return Result.error("当前管理员用户不存在");
            }

            videoService.createVideo(video);

            Map<String, Object> result = new HashMap<>();
            result.put("videoId", video.getId());
            result.put("message", "视频创建成功");
            return Result.success("创建成功", result);
        } catch (Exception e) {
            log.error("创建视频失败", e);
            return Result.error("创建视频失败");
        }
    }

    /**
     * 更新视频
     */
    @PutMapping("/{id}")
    public Result<String> updateVideo(@PathVariable Long id, @RequestBody Video video) {
        try {
            video.setId(id);
            videoService.updateVideo(video);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新视频失败, id={}", id, e);
            return Result.error("更新视频失败");
        }
    }

    /**
     * 删除视频
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteVideo(@PathVariable Long id) {
        try {
            videoService.deleteVideo(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除视频失败, id={}", id, e);
            return Result.error("删除视频失败");
        }
    }

    /**
     * 设为/取消推荐
     */
    @PutMapping("/{id}/featured")
    public Result<String> toggleFeatured(@PathVariable Long id, @RequestParam int featured) {
        try {
            Video video = videoService.getVideoById(id);
            if (video == null) {
                return Result.error("视频不存在");
            }
            video.setIsFeatured(featured == 1 ? 1 : 0);
            videoService.updateById(video);
            return Result.success(featured == 1 ? "已设为推荐" : "已取消推荐");
        } catch (Exception e) {
            log.error("切换推荐状态失败, id={}", id, e);
            return Result.error("操作失败");
        }
    }

    /**
     * 设置推荐优先级
     */
    @PutMapping("/{id}/featured-priority")
    public Result<String> setFeaturedPriority(@PathVariable Long id, @RequestParam int priority) {
        try {
            Video video = videoService.getVideoById(id);
            if (video == null) {
                return Result.error("视频不存在");
            }
            priority = Math.max(0, Math.min(100, priority));
            video.setFeaturedPriority(priority);
            videoService.updateById(video);
            return Result.success("优先级已更新");
        } catch (Exception e) {
            log.error("设置优先级失败, id={}", id, e);
            return Result.error("设置失败");
        }
    }
}
