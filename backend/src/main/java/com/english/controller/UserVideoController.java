package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.entity.Video;
import com.english.service.UserService;
import com.english.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户视频管理控制器
 * 处理普通用户/学生上传、修改、删除自己的视频
 */
@RestController
@RequestMapping("/api/user/videos")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
public class UserVideoController {

    private static final Logger log = LoggerFactory.getLogger(UserVideoController.class);

    @Autowired
    private VideoService videoService;

    @Autowired
    private UserService userService;

    /**
     * 用户上传视频
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadVideo(@RequestBody Map<String, Object> videoData) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            String title = (String) videoData.get("title");
            Object mediaIdObj = videoData.get("mediaId");
            String description = (String) videoData.getOrDefault("description", "");
            String tags = (String) videoData.getOrDefault("tags", "");
            String subtitleContent = (String) videoData.get("subtitleContent");
            String subtitleContentZh = (String) videoData.get("subtitleContentZh");
            Object coverMediaIdObj = videoData.get("coverMediaId");

            if (title == null || title.trim().isEmpty()) {
                return Result.error("视频标题不能为空");
            }
            if (mediaIdObj == null) {
                return Result.error("视频文件不能为空");
            }

            Video video = new Video();
            video.setTitle(title);
            video.setMediaId(Long.valueOf(mediaIdObj.toString()));
            video.setDescription(description);
            video.setTags(tags != null ? tags : "");
            video.setUserId(user.getId());
            video.setIsPublic(0); // 默认不公开，审核通过后再公开
            video.setStatus("PENDING"); // 默认待审核
            if (subtitleContent != null && !subtitleContent.trim().isEmpty()) {
                video.setSubtitleContent(subtitleContent);
            }
            if (subtitleContentZh != null && !subtitleContentZh.trim().isEmpty()) {
                video.setSubtitleContentZh(subtitleContentZh);
            }
            if (coverMediaIdObj != null) {
                video.setCoverMediaId(Long.valueOf(coverMediaIdObj.toString()));
            }

            videoService.createVideo(video);

            Map<String, Object> result = new HashMap<>();
            result.put("videoId", video.getId());
            result.put("message", "视频上传成功");
            return Result.success("上传成功", result);
        } catch (Exception e) {
            log.error("上传视频失败", e);
            return Result.error("上传失败，请稍后重试");
        }
    }

    /**
     * 获取用户上传的视频列表
     */
    @GetMapping("/my-uploads")
    public Result<List<Video>> getMyUploads() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }
            List<Video> videos = videoService.getVideosByUploader(user.getId());
            return Result.success(videos);
        } catch (Exception e) {
            log.error("获取视频列表失败", e);
            return Result.error("获取失败，请稍后重试");
        }
    }

    /**
     * 获取视频详情
     */
    @GetMapping("/{videoId}")
    public Result<Video> getVideoDetail(@PathVariable Long videoId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Video video = videoService.getVideoById(videoId);
            if (video == null) {
                return Result.error("视频不存在");
            }

            boolean isOwner = user.getId().equals(video.getUserId());
            boolean isPublic = (video.getIsPublic() != null && video.getIsPublic() == 1);
            if (!isOwner && !isPublic) {
                return Result.error("没有权限查看此视频");
            }

            videoService.incrementViewCount(videoId);
            return Result.success(video);
        } catch (Exception e) {
            log.error("获取视频详情失败 videoId={}", videoId, e);
            return Result.error("获取失败，请稍后重试");
        }
    }

    /**
     * 更新用户上传的视频
     */
    @PutMapping("/{videoId}")
    public Result<String> updateVideo(@PathVariable Long videoId, @RequestBody Map<String, Object> videoData) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Video video = videoService.getVideoById(videoId);
            if (video == null) {
                return Result.error("视频不存在");
            }
            if (!user.getId().equals(video.getUserId())) {
                return Result.error("没有权限修改此视频");
            }

            if (videoData.containsKey("title")) {
                Object titleVal = videoData.get("title");
                if (titleVal == null || titleVal.toString().trim().isEmpty()) {
                    return Result.error("视频标题不能为空");
                }
                video.setTitle(titleVal.toString());
            }
            if (videoData.containsKey("description")) {
                Object descVal = videoData.get("description");
                video.setDescription(descVal != null ? descVal.toString() : "");
            }
            if (videoData.containsKey("tags")) {
                Object tagsVal = videoData.get("tags");
                video.setTags(tagsVal != null ? tagsVal.toString() : "");
            }
            if (videoData.containsKey("subtitleContent")) {
                Object subVal = videoData.get("subtitleContent");
                video.setSubtitleContent(subVal != null ? subVal.toString() : null);
            }
            if (videoData.containsKey("subtitleContentZh")) {
                Object subZhVal = videoData.get("subtitleContentZh");
                video.setSubtitleContentZh(subZhVal != null ? subZhVal.toString() : null);
            }

            // 修改后内容需要重新审核
            video.setStatus("PENDING");

            videoService.updateById(video);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新视频失败 videoId={}", videoId, e);
            return Result.error("更新失败，请稍后重试");
        }
    }

    /**
     * 删除用户上传的视频
     */
    @DeleteMapping("/{videoId}")
    public Result<String> deleteVideo(@PathVariable Long videoId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Video video = videoService.getVideoById(videoId);
            if (video == null) {
                return Result.error("视频不存在");
            }
            if (!user.getId().equals(video.getUserId())) {
                return Result.error("没有权限删除此视频");
            }

            // 已审核通过的内容不可删除（只能修改，修改后重新进入审核）
            if ("APPROVED".equalsIgnoreCase(video.getStatus())) {
                return Result.error("已审核通过的内容不可删除，如需调整请走修改流程");
            }

            videoService.deleteVideo(videoId);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除视频失败 videoId={}", videoId, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 点赞视频
     */
    @PostMapping("/{videoId}/like")
    public Result<String> likeVideo(@PathVariable Long videoId) {
        try {
            Video video = videoService.getVideoById(videoId);
            if (video == null) {
                return Result.error("视频不存在");
            }
            if (video.getIsPublic() == null || video.getIsPublic() != 1) {
                return Result.error("只能点赞公开视频");
            }
            videoService.incrementLikeCount(videoId);
            return Result.success("点赞成功");
        } catch (Exception e) {
            log.error("点赞视频失败 videoId={}", videoId, e);
            return Result.error("点赞失败，请稍后重试");
        }
    }
}
