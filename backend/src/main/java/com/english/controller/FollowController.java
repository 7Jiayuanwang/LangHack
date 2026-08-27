package com.english.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.FollowService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/user/follow")
@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN', 'USER')")
public class FollowController {

    @Autowired
    private FollowService followService;

    @Autowired
    private UserService userService;

    /**
     * Follow a user
     */
    @PostMapping("/{userId}")
    public Result<Void> follow(@PathVariable Long userId, Authentication auth) {
        User currentUser = userService.getUserByUsername(auth.getName());
        if (currentUser == null) {
            return Result.error("User not found");
        }
        followService.follow(currentUser.getId(), userId);
        return Result.success();
    }

    /**
     * Unfollow a user
     */
    @DeleteMapping("/{userId}")
    public Result<Void> unfollow(@PathVariable Long userId, Authentication auth) {
        User currentUser = userService.getUserByUsername(auth.getName());
        if (currentUser == null) {
            return Result.error("User not found");
        }
        followService.unfollow(currentUser.getId(), userId);
        return Result.success();
    }

    /**
     * Check the follow relationship between the current user and a target user.
     */
    @GetMapping("/status/{userId}")
    public Result<Map<String, Object>> getFollowStatus(@PathVariable Long userId, Authentication auth) {
        User currentUser = userService.getUserByUsername(auth.getName());
        if (currentUser == null) {
            return Result.error("User not found");
        }
        boolean isFollowing = followService.isFollowing(currentUser.getId(), userId);
        boolean isFollowedBy = followService.isFollowing(userId, currentUser.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("isFollowing", isFollowing);
        data.put("isFollowedBy", isFollowedBy);
        data.put("isMutualFollowing", isFollowing && isFollowedBy);
        return Result.success(data);
    }

    /**
     * Get follow counts for a user
     */
    @GetMapping("/counts/{userId}")
    public Result<Map<String, Integer>> getFollowCounts(@PathVariable Long userId) {
        Map<String, Integer> counts = followService.getFollowCounts(userId);
        return Result.success(counts);
    }

    /**
     * Get following list (users that I follow)
     */
    @GetMapping("/following/{userId}")
    public Result<Map<String, Object>> getFollowingList(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<User> pageResult = followService.getFollowingList(userId, page, size);
        Map<String, Object> data = buildPageResult(pageResult);
        return Result.success(data);
    }

    /**
     * Get followers list (users that follow me)
     */
    @GetMapping("/followers/{userId}")
    public Result<Map<String, Object>> getFollowersList(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<User> pageResult = followService.getFollowersList(userId, page, size);
        Map<String, Object> data = buildPageResult(pageResult);
        return Result.success(data);
    }

    private Map<String, Object> buildPageResult(IPage<User> pageResult) {
        List<Map<String, Object>> userList = new ArrayList<>();
        for (User u : pageResult.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("nickname", u.getNickname());
            item.put("realName", u.getRealName());
            item.put("avatar", u.getAvatar());
            item.put("role", u.getRole());
            userList.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", userList);
        data.put("total", pageResult.getTotal());
        data.put("current", pageResult.getCurrent());
        data.put("size", pageResult.getSize());
        data.put("pages", pageResult.getPages());
        return data;
    }
}
