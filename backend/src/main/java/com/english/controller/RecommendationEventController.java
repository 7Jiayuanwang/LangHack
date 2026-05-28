package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.RecommendationTrackingService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/recommendation")
public class RecommendationEventController {

    @Autowired
    private RecommendationTrackingService trackingService;

    @Autowired
    private UserService userService;

    @PostMapping("/event")
    public Result<?> trackEvent(@RequestBody Map<String, Object> body, Authentication auth) {
        if (auth == null) {
            return Result.error("未登录");
        }

        User user = userService.getUserByUsername(auth.getName());
        if (user == null) {
            return Result.error("用户不存在");
        }

        Object textIdObj = body.get("textId");
        String eventType = (String) body.get("eventType");
        if (textIdObj == null || eventType == null) {
            return Result.error("缺少必要参数");
        }

        if (!"CLICK".equals(eventType) && !"DISMISS".equals(eventType) && !"BOOKMARK".equals(eventType)) {
            return Result.error("无效的事件类型");
        }

        Long textId = Long.valueOf(textIdObj.toString());
        String source = body.containsKey("source") ? body.get("source").toString() : null;
        Integer position = body.containsKey("position") ? Integer.valueOf(body.get("position").toString()) : null;

        trackingService.trackEvent(user.getId(), textId, eventType, source, position);
        return Result.success();
    }
}
