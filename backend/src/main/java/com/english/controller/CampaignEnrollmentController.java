package com.english.controller;

import com.english.entity.CampaignParticipation;
import com.english.dto.Result;
import com.english.service.CampaignEnrollmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 活动报名管理Controller（管理员端）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/campaigns/{campaignId}/enrollments")
@PreAuthorize("hasRole('ADMIN')")
public class CampaignEnrollmentController {

    @Autowired
    private CampaignEnrollmentService enrollmentService;

    /**
     * 获取待审核报名列表
     */
    @GetMapping("/pending")
    public Result<List<CampaignParticipation>> getPendingEnrollments(@PathVariable Long campaignId) {
        try {
            List<CampaignParticipation> pending = enrollmentService.getPendingEnrollments(campaignId);
            return Result.success(pending);
        } catch (Exception e) {
            log.error("获取待审核报名失败", e);
            return Result.error("获取待审核报名失败: " + e.getMessage());
        }
    }

    /**
     * 批准报名
     */
    @PutMapping("/{participationId}/approve")
    public Result<String> approveEnrollment(@PathVariable Long campaignId, @PathVariable Long participationId) {
        try {
            enrollmentService.approveEnrollment(participationId);
            return Result.success("已批准");
        } catch (Exception e) {
            log.error("批准报名失败", e);
            return Result.error("批准报名失败: " + e.getMessage());
        }
    }

    /**
     * 驳回报名
     */
    @PutMapping("/{participationId}/reject")
    public Result<String> rejectEnrollment(
            @PathVariable Long campaignId,
            @PathVariable Long participationId,
            @RequestBody Map<String, String> request) {
        try {
            String reason = request.get("reason");
            enrollmentService.rejectEnrollment(participationId, reason);
            return Result.success("已驳回");
        } catch (Exception e) {
            log.error("驳回报名失败", e);
            return Result.error("驳回报名失败: " + e.getMessage());
        }
    }

    /**
     * 批量添加参与者
     */
    @PostMapping("/batch-enroll")
    public Result<String> batchEnrollUsers(@PathVariable Long campaignId, @RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> userIds = request.get("userIds");
            enrollmentService.batchEnrollUsers(campaignId, userIds);
            return Result.success("批量添加成功");
        } catch (Exception e) {
            log.error("批量添加参与者失败", e);
            return Result.error("批量添加参与者失败: " + e.getMessage());
        }
    }
}
