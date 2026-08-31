package com.english.controller;

import com.english.dto.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.entity.SentenceFavorite;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.SentenceFavoriteService;
import com.english.service.UserService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/student/sentence-favorites")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
public class SentenceFavoriteController {
    @Autowired
    private SentenceFavoriteService sentenceFavoriteService;

    @Autowired
    private UserService userService;

    @PostMapping("/toggle")
    public Result<Map<String, Object>> toggle(@RequestBody ToggleRequest request, Authentication authentication) {
        if (request == null) throw new BusinessException("请求内容不能为空");
        User user = currentUser(authentication);
        return Result.success(sentenceFavoriteService.toggle(
                user.getId(), request.getTextId(), request.getSentence(), hasStaffAccess(authentication)));
    }

    @GetMapping
    public Result<IPage<SentenceFavorite>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return Result.success(sentenceFavoriteService.list(currentUser(authentication).getId(), page, size));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        sentenceFavoriteService.delete(id, currentUser(authentication).getId());
        return Result.success();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null) throw new BusinessException("未登录");
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) throw new BusinessException("用户不存在");
        return user;
    }

    private boolean hasStaffAccess(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN") ||
                authority.getAuthority().equals("ROLE_TEACHER") ||
                authority.getAuthority().equals("ROLE_OPS"));
    }

    @Data
    public static class ToggleRequest {
        private Long textId;
        private String sentence;
    }
}
