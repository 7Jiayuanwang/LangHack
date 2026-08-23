package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.dto.Result;
import com.english.entity.Notification;
import com.english.entity.NotificationRead;
import com.english.entity.User;
import com.english.mapper.NotificationMapper;
import com.english.mapper.NotificationReadMapper;
import com.english.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class NotificationController {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private NotificationReadMapper notificationReadMapper;

    @Autowired
    private UserService userService;

    @PostMapping("/api/admin/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Notification> createNotification(@RequestBody Notification notification) {
        try {
            User currentUser = getCurrentUser();
            notification.setCreatedBy(currentUser.getId());
            notification.setIsActive(true);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setUpdatedAt(LocalDateTime.now());
            notificationMapper.insert(notification);
            return Result.success(notification);
        } catch (Exception e) {
            log.error("创建通知失败", e);
            return Result.error("创建通知失败: " + e.getMessage());
        }
    }

    @PutMapping("/api/admin/notifications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Notification> updateNotification(@PathVariable Long id, @RequestBody Notification notification) {
        try {
            Notification existing = notificationMapper.selectById(id);
            if (existing == null) {
                return Result.error(404, "通知不存在");
            }
            existing.setTitle(notification.getTitle());
            existing.setContent(notification.getContent());
            existing.setTargetRole(notification.getTargetRole());
            existing.setIsActive(notification.getIsActive() != null ? notification.getIsActive() : existing.getIsActive());
            existing.setUpdatedAt(LocalDateTime.now());
            notificationMapper.updateById(existing);
            return Result.success(existing);
        } catch (Exception e) {
            log.error("更新通知失败", e);
            return Result.error("更新通知失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/api/admin/notifications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteNotification(@PathVariable Long id) {
        try {
            notificationMapper.deleteById(id);
            notificationReadMapper.delete(
                new LambdaQueryWrapper<NotificationRead>().eq(NotificationRead::getNotificationId, id)
            );
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除通知失败", e);
            return Result.error("删除通知失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/admin/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Notification>> getAllNotifications() {
        try {
            List<Notification> list = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>().orderByDesc(Notification::getCreatedAt)
            );
            list.forEach(n -> {
                User creator = userService.getUserById(n.getCreatedBy());
                if (creator != null) {
                    n.setCreatedByName(creator.getRealName() != null ? creator.getRealName() : creator.getUsername());
                }
            });
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取通知列表失败", e);
            return Result.error("获取通知列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/notifications/unread")
    public Result<List<Notification>> getUnreadNotifications() {
        try {
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return Result.error(401, "未登录");
            }

            String userRole = currentUser.getRole();
            LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getIsActive, true)
                .and(w -> w.eq(Notification::getTargetRole, "ALL")
                    .or().eq(Notification::getTargetRole, userRole))
                .orderByDesc(Notification::getCreatedAt);

            List<Notification> allNotifications = notificationMapper.selectList(query);

            List<Long> readNotificationIds = notificationReadMapper.selectList(
                new LambdaQueryWrapper<NotificationRead>().eq(NotificationRead::getUserId, currentUser.getId())
            ).stream().map(NotificationRead::getNotificationId).collect(Collectors.toList());

            List<Notification> unread = allNotifications.stream()
                .filter(n -> !readNotificationIds.contains(n.getId()))
                .collect(Collectors.toList());

            return Result.success(unread);
        } catch (Exception e) {
            log.error("获取未读通知失败", e);
            return Result.error("获取未读通知失败: " + e.getMessage());
        }
    }

    @PostMapping("/api/notifications/{id}/read")
    public Result<String> markAsRead(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return Result.error(401, "未登录");
            }

            Long count = notificationReadMapper.selectCount(
                new LambdaQueryWrapper<NotificationRead>()
                    .eq(NotificationRead::getNotificationId, id)
                    .eq(NotificationRead::getUserId, currentUser.getId())
            );

            if (count == 0) {
                NotificationRead read = new NotificationRead();
                read.setNotificationId(id);
                read.setUserId(currentUser.getId());
                read.setReadAt(LocalDateTime.now());
                notificationReadMapper.insert(read);
            }

            return Result.success("已标记为已读");
        } catch (Exception e) {
            log.error("标记已读失败", e);
            return Result.error("标记已读失败: " + e.getMessage());
        }
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userService.getUserByUsername(auth.getName());
    }
}
