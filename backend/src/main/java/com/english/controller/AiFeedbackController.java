package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.AiFeedbackService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AiFeedbackController {

    @Autowired
    private AiFeedbackService aiFeedbackService;

    @Autowired
    private UserService userService;

    @PostMapping("/api/student/ai-qa/feedback")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Void> submitFeedback(@RequestBody FeedbackRequest request,
                                       Authentication authentication) {
        if (request.getCallLogId() == null) {
            return Result.error("callLogId 不能为空");
        }
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            return Result.error("用户不存在");
        }
        aiFeedbackService.submitFeedback(
                request.getCallLogId(),
                user.getId(),
                request.getIsHelpful(),
                request.getRating(),
                request.getFeedbackText()
        );
        return Result.success();
    }

    /**
     * 外部问题反馈入口点击上报：学生在导航/个人区点击"问题反馈"跳 HiAgent 网页时前端 fire-and-forget 上报。
     * 会写入一条 ai_feedback（含哨兵 feedbackText），进入活动 FEEDBACK 任务计数。
     */
    @PostMapping("/api/student/feedback/external-click")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<Void> recordExternalFeedbackClick(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            return Result.error("用户不存在");
        }
        aiFeedbackService.submitExternalFeedbackClick(user.getId(), user.getUsername());
        return Result.success();
    }

    @GetMapping("/api/admin/metrics/ai-feedback-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getFeedbackSummary() {
        return Result.success(aiFeedbackService.getFeedbackSummary());
    }

    public static class FeedbackRequest {
        private Long callLogId;
        private Boolean isHelpful;
        private Integer rating;
        private String feedbackText;

        public Long getCallLogId() { return callLogId; }
        public void setCallLogId(Long callLogId) { this.callLogId = callLogId; }

        public Boolean getIsHelpful() { return isHelpful; }
        public void setIsHelpful(Boolean isHelpful) { this.isHelpful = isHelpful; }

        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }

        public String getFeedbackText() { return feedbackText; }
        public void setFeedbackText(String feedbackText) { this.feedbackText = feedbackText; }
    }
}
