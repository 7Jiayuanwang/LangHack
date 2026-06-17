package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Underline;
import com.english.entity.User;
import com.english.service.UnderlineService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/underlines")
public class UnderlineController {

    private static final Logger logger = LoggerFactory.getLogger(UnderlineController.class);

    @Autowired
    private UnderlineService underlineService;

    @Autowired
    private UserService userService;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<Void> createUnderline(@RequestBody Map<String, Object> payload) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Result.error("未授权");
            }
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            Long userId = currentUser.getId();
            Object textIdObj = payload.get("textId");
            if (textIdObj == null) {
                return Result.error("textId不能为空");
            }
            Long textId = Long.valueOf(textIdObj.toString());
            String selectedText = (String) payload.get("selectedText");
            if (selectedText != null && selectedText.length() > 2000) {
                return Result.error("划线文本不能超过2000字");
            }
            String serializedRange = (String) payload.get("serializedRange");
            if (serializedRange != null && serializedRange.length() > 5000) {
                return Result.error("范围数据异常");
            }
            Object startObj = payload.get("startIndex");
            Object endObj = payload.get("endIndex");
            Integer startIndex = startObj != null ? ((Number) startObj).intValue() : null;
            Integer endIndex = endObj != null ? ((Number) endObj).intValue() : null;

            underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, serializedRange);
            return Result.success();
        } catch (Exception e) {
            logger.error("创建划线失败", e);

            return Result.error("创建划线失败，请稍后重试");
        }
    }

    @GetMapping("/text/{textId}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<List<Underline>> getUnderlines(@PathVariable Long textId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Result.error("未授权");
            }
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            return Result.success(underlineService.getUnderlinesByTextIdAndUserId(textId, currentUser.getId()));
        } catch (Exception e) {
            logger.error("获取划线失败", e);

            return Result.error("获取划线失败，请稍后重试");
        }
    }

    /**
     * 删除划线记录（仅允许划线所有者操作）
     */
    @DeleteMapping("/{underlineId}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<Void> deleteUnderline(@PathVariable Long underlineId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Result.error("未授权");
            }
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                return Result.error("用户不存在");
            }

            // IDOR防护：验证underline属于当前用户
            Underline underline = underlineService.getUnderlineById(underlineId);
            if (underline == null) {
                return Result.error("划线记录不存在");
            }
            if (!currentUser.getId().equals(underline.getUserId())) {
                return Result.error("没有权限删除此划线");
            }

            boolean deleted = underlineService.deleteUnderline(underlineId, currentUser.getId());
            if (!deleted) {
                return Result.error("删除失败，请稍后重试");
            }
            return Result.success();
        } catch (Exception e) {
            logger.error("删除划线失败", e);

            return Result.error("删除划线失败，请稍后重试");
        }
    }
}
