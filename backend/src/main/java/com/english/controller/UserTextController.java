package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Text;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.service.TextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户课文管理控制器
 * 处理普通用户上传、修改、删除自己的课文
 */
@RestController
@RequestMapping("/api/user/texts")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
public class UserTextController {

    private static final Logger log = LoggerFactory.getLogger(UserTextController.class);

    @Autowired
    private TextService textService;

    @Autowired
    private UserService userService;

    /**
     * 用户上传课文
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadText(@RequestBody Map<String, Object> textData) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Text text = new Text();
            text.setUnitId(999L); // 用户上传内容的专用单元

            // 安全地获取和设置字段
            String title = (String) textData.get("title");
            String content = (String) textData.get("content");
            String difficulty = (String) textData.getOrDefault("difficulty", "MEDIUM");
            String tags = (String) textData.getOrDefault("tags", "");

            if (title == null || title.trim().isEmpty()) {
                return Result.error("课文标题不能为空");
            }
            if (content == null || content.trim().isEmpty()) {
                return Result.error("课文内容不能为空");
            }
            text.setTitle(title);
            text.setContent(content);
            text.setDifficulty(difficulty);
            text.setTags(tags != null ? tags : "");
            text.setUploadedBy(user.getId());
            text.setIsPublic(0); // 默认不公开，审核通过后再公开
            text.setStatus("PENDING"); // 默认待审核
            text.setViewCount(0);
            text.setLikeCount(0);

            // 设置排序号，简化逻辑
            try {
                Integer maxOrder = textService.getMaxOrderInUnit(999L);
                if (maxOrder != null && maxOrder >= Integer.MAX_VALUE - 1) {
                    return Result.error("单元课文数量已满，请联系管理员");
                }
                text.setOrderNum((maxOrder != null ? maxOrder : 0) + 1);
            } catch (Exception e) {
                text.setOrderNum(1); // 如果获取失败，默认设置为1
            }

            textService.save(text);

            Map<String, Object> result = new HashMap<>();
            result.put("textId", text.getId());
            result.put("message", "课文上传成功");

            return Result.success("上传成功", result);
        } catch (Exception e) {
            log.error("上传课文失败", e);
            return Result.error("上传失败，请稍后重试");
        }
    }

    /**
     * 获取用户上传的课文列表
     */
    @GetMapping("/my-uploads")
    public Result<List<Text>> getMyUploads() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            List<Text> texts = textService.getTextsByUploader(user.getId());
            return Result.success(texts);
        } catch (Exception e) {
            log.error("获取课文列表失败", e);
            return Result.error("获取失败，请稍后重试");
        }
    }

    @GetMapping("/{textId}")
    public Result<Text> getTextDetail(@PathVariable Long textId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            // 检查权限：只有上传者可以查看自己的课文，否则仅能查看公开课文
            boolean isOwner = user.getId().equals(text.getUploadedBy());
            boolean isPublic = (text.getIsPublic() != null && text.getIsPublic() == 1);
            if (!isOwner && !isPublic) {
                return Result.error("没有权限查看此课文");
            }

            // 原子递增查看次数（SQL UPDATE 已保证一致性）
            textService.incrementViewCount(textId);

            return Result.success(text);
        } catch (Exception e) {
            log.error("获取课文详情失败 textId={}", textId, e);
            return Result.error("获取失败，请稍后重试");
        }
    }

    /**
     * 更新用户上传的课文
     */
    @PutMapping("/{textId}")
    public Result<String> updateText(@PathVariable Long textId, @RequestBody Map<String, Object> textData) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            // 检查权限：只有上传者可以修改
            if (!user.getId().equals(text.getUploadedBy())) {
                return Result.error("没有权限修改此课文");
            }

            Object titleVal = textData.get("title");
            Object contentVal = textData.get("content");
            Object difficultyVal = textData.get("difficulty");
            Object tagsVal = textData.get("tags");
            if (textData.containsKey("title") && (titleVal == null || titleVal.toString().trim().isEmpty())) {
                return Result.error("课文标题不能为空");
            }
            if (textData.containsKey("content") && (contentVal == null || contentVal.toString().trim().isEmpty())) {
                return Result.error("课文内容不能为空");
            }
            text.setTitle(textData.containsKey("title") && titleVal != null ? titleVal.toString() : (text.getTitle() != null ? text.getTitle() : ""));
            text.setContent(textData.containsKey("content") && contentVal != null ? contentVal.toString() : (text.getContent() != null ? text.getContent() : ""));
            text.setDifficulty(textData.containsKey("difficulty") && difficultyVal != null ? difficultyVal.toString() : (text.getDifficulty() != null ? text.getDifficulty() : "MEDIUM"));
            text.setTags(textData.containsKey("tags") && tagsVal != null ? tagsVal.toString() : (text.getTags() != null ? text.getTags() : ""));

            textService.updateById(text);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新课文失败 textId={}", textId, e);
            return Result.error("更新失败，请稍后重试");
        }
    }

    /**
     * 删除用户上传的课文
     */
    @DeleteMapping("/{textId}")
    public Result<String> deleteText(@PathVariable Long textId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            // 检查权限：只有上传者可以删除
            if (!user.getId().equals(text.getUploadedBy())) {
                return Result.error("没有权限删除此课文");
            }

            textService.deleteText(textId);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除课文失败 textId={}", textId, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 获取所有公开的用户上传课文（供所有学生查看）
     */
    @GetMapping("/public")
    public Result<List<Text>> getPublicTexts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            List<Text> texts = textService.getPublicTexts(page, size, difficulty, keyword);
            return Result.success(texts);
        } catch (Exception e) {
            log.error("获取公开课文失败", e);
            return Result.error("获取失败，请稍后重试");
        }
    }

    /**
     * 点赞课文
     */
    @PostMapping("/{textId}/like")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> likeText(@PathVariable Long textId) {
        try {
            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            // 检查是否是公开课文
            if (text.getIsPublic() == null || text.getIsPublic() != 1) {
                return Result.error("只能点赞公开课文");
            }

            // 原子递增点赞次数
            textService.incrementLikeCount(textId);

            return Result.success("点赞成功");
        } catch (Exception e) {
            log.error("点赞课文失败 textId={}", textId, e);
            return Result.error("点赞失败，请稍后重试");
        }
    }
}
