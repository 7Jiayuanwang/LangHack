package com.english.controller;

import com.english.dto.AuthorDTO;
import com.english.dto.Result;
import com.english.entity.Text;
import com.english.entity.User;
import com.english.entity.Video;
import com.english.service.AuthorEnricher;
import com.english.service.TextService;
import com.english.service.UserService;
import com.english.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开作者主页控制器
 * 为所有用户提供查看作者信息及其公开作品
 */
@RestController
@RequestMapping("/api/public/authors")
public class PublicAuthorController {

    private static final Logger log = LoggerFactory.getLogger(PublicAuthorController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private TextService textService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private AuthorEnricher authorEnricher;

    /**
     * 获取作者主页信息（作者信息 + 其公开的文章和视频）
     */
    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getAuthorProfile(@PathVariable Long userId) {
        try {
            // 获取作者信息
            User user = userService.getUserById(userId);
            if (user == null) {
                return Result.error("作者不存在");
            }

            AuthorDTO author = AuthorDTO.from(user);

            // 获取作者的公开文章（已审核通过）
            List<Text> texts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getUploadedBy, userId)
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .orderByDesc(Text::getCreatedAt)
                    .list();
            authorEnricher.enrichTexts(texts);

            // 获取作者的公开视频（已审核通过）
            List<Video> videos = videoService.lambdaQuery()
                    .eq(Video::getDeleted, 0)
                    .eq(Video::getUserId, userId)
                    .eq(Video::getIsPublic, 1)
                    .eq(Video::getStatus, "APPROVED")
                    .orderByDesc(Video::getCreatedTime)
                    .list();
            authorEnricher.enrichVideos(videos);

            Map<String, Object> result = new HashMap<>();
            result.put("author", author);
            result.put("texts", texts);
            result.put("videos", videos);
            result.put("totalTexts", texts.size());
            result.put("totalVideos", videos.size());

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取作者主页失败, userId={}", userId, e);
            return Result.error("获取作者主页失败，请稍后重试");
        }
    }
}
