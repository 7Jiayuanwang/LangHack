package com.english.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.entity.CampaignParticipation;
import com.english.entity.CampaignTask;
import com.english.entity.User;
import com.english.dto.Result;
import com.english.service.CampaignEnrollmentService;
import com.english.service.CampaignService;
import com.english.service.CampaignStatsService;
import com.english.service.CampaignStreakService;
import com.english.service.CampaignTaskService;
import com.english.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端活动Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/student/campaigns")
@PreAuthorize("hasAnyRole('USER', 'STUDENT')")
public class StudentCampaignController {

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignEnrollmentService enrollmentService;

    @Autowired
    private CampaignStatsService statsService;

    @Autowired
    private CampaignTaskService taskService;

    @Autowired
    private UserService userService;

    @Autowired
    private CampaignStreakService streakService;

    private Long getCurrentUserId(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        return user.getId();
    }

    /**
     * 获取可报名活动列表
     */
    @GetMapping
    public Result<Page<CampaignActivity>> getEnrollableCampaigns(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Map<String, Object> params = Map.of("keyword", keyword != null ? keyword : "");
            Page<CampaignActivity> page = enrollmentService.getEnrollableCampaigns(userId, current, size, params);
            return Result.success(page);
        } catch (Exception e) {
            log.error("获取可报名活动失败", e);
            return Result.error("获取可报名活动失败: " + e.getMessage());
        }
    }

    /**
     * 获取我参与的活动列表
     */
    @GetMapping("/enrolled")
    public Result<Page<CampaignActivity>> getMyEnrolledCampaigns(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Map<String, Object> params = Map.of("status", status != null ? status : "");
            Page<CampaignActivity> page = enrollmentService.getMyEnrolledCampaigns(userId, current, size, params);
            return Result.success(page);
        } catch (Exception e) {
            log.error("获取我参与的活动失败", e);
            return Result.error("获取我参与的活动失败: " + e.getMessage());
        }
    }

    /**
     * 获取活动详情（含任务列表、报名状态）
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getCampaignDetail(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            CampaignActivity campaign = campaignService.getCampaignById(id);
            if (campaign == null) {
                return Result.error("活动不存在");
            }
            // 填充参与人数（瞬态字段 @TableField(exist=false)）
            campaign.setParticipantCount(enrollmentService.countEnrolledParticipants(id));
            List<CampaignTask> tasks = taskService.getTasksByCampaign(id);
            CampaignParticipation participation = enrollmentService.getParticipation(id, userId);
            boolean isEnrolled = participation != null &&
                (participation.getEnrollmentStatus() == com.english.enums.EnrollmentStatus.ENROLLED ||
                    participation.getEnrollmentStatus() == com.english.enums.EnrollmentStatus.PENDING);
            boolean canEnroll = campaignService.canUserEnroll(id, userId);
            List<String> restrictions = canEnroll ? List.of() : campaignService.getEnrollmentRestrictions(id, userId);

            Map<String, Object> data = new HashMap<>();
            data.put("campaign", campaign);
            data.put("tasks", tasks);
            data.put("isEnrolled", isEnrolled);
            data.put("enrollmentStatus", participation != null ? participation.getEnrollmentStatus() : null);
            data.put("canEnroll", canEnroll);
            data.put("enrollmentMessage", restrictions.isEmpty() ? "" : String.join("；", restrictions));
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取活动详情失败", e);
            return Result.error("获取活动详情失败: " + e.getMessage());
        }
    }

    /**
     * 报名参加活动
     */
    @PostMapping("/{id}/enroll")
    public Result<String> enrollCampaign(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            enrollmentService.enrollCampaign(id, userId);
            return Result.success("报名成功");
        } catch (RuntimeException e) {
            log.warn("报名被拒绝: campaignId={}, reason={}", id, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("报名失败", e);
            return Result.error("报名失败: " + e.getMessage());
        }
    }

    /**
     * 退出活动
     */
    @DeleteMapping("/{id}/withdraw")
    public Result<String> withdrawCampaign(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            enrollmentService.withdrawCampaign(id, userId);
            return Result.success("退出成功");
        } catch (Exception e) {
            log.error("退出活动失败", e);
            return Result.error("退出活动失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的进度
     */
    @GetMapping("/{id}/progress")
    public Result<Map<String, Object>> getMyProgress(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Map<String, Object> progress = statsService.getMyProgress(id, userId);
            return Result.success(progress);
        } catch (Exception e) {
            log.error("获取我的进度失败", e);
            return Result.error("获取我的进度失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的任务进度列表
     */
    @GetMapping("/{id}/tasks")
    public Result<List<Map<String, Object>>> getMyTaskProgress(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            List<Map<String, Object>> tasks = statsService.getMyTaskProgress(id, userId);
            return Result.success(tasks);
        } catch (Exception e) {
            log.error("获取任务进度失败", e);
            return Result.error("获取任务进度失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的排名（支持按周查询）
     * @param weekStart 周起始日（周一，格式 YYYY-MM-DD），可选
     */
    @GetMapping("/{id}/ranking")
    public Result<Map<String, Object>> getMyRanking(@PathVariable Long id,
                                                     @RequestParam(required = false) String weekStart,
                                                     Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Map<String, Object> ranking = statsService.getMyRanking(id, userId, weekStart);
            return Result.success(ranking);
        } catch (Exception e) {
            log.error("获取我的排名失败", e);
            return Result.error("获取我的排名失败: " + e.getMessage());
        }
    }

    /**
     * 获取每周优秀个人和班级
     */
    @GetMapping("/{id}/weekly-awards")
    public Result<Map<String, Object>> getWeeklyAwards(@PathVariable Long id,
                                                        @RequestParam(required = false) String weekStart,
                                                        @RequestParam(required = false, defaultValue = "3") Integer topN) {
        try {
            Map<String, Object> awards = statsService.getWeeklyAwards(id, weekStart, topN);
            return Result.success(awards);
        } catch (Exception e) {
            log.error("获取周优秀失败", e);
            return Result.error("获取周优秀失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的积分明细
     */
    @GetMapping("/{id}/score-details")
    public Result<List<Map<String, Object>>> getMyScoreDetails(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            List<Map<String, Object>> details = statsService.getMyScoreDetails(id, userId);
            return Result.success(details);
        } catch (Exception e) {
            log.error("获取积分明细失败", e);
            return Result.error("获取积分明细失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的积分流水（账单式）
     */
    @GetMapping("/{id}/score-logs")
    public Result<List<Map<String, Object>>> getMyScoreLogs(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            List<Map<String, Object>> logs = statsService.getScoreLogs(id, userId);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("获取积分流水失败", e);
            return Result.error("获取积分流水失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的连续学习加成信息
     */
    @GetMapping("/{id}/streak")
    public Result<Map<String, Object>> getMyStreak(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            int streakDays = streakService.getStreakDays(id, userId);
            java.math.BigDecimal multiplier = streakService.getCurrentMultiplier(id, userId);
            Map<String, Object> data = new HashMap<>();
            data.put("streakDays", streakDays);
            data.put("currentMultiplier", multiplier);
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取连续学习信息失败", e);
            return Result.error("获取连续学习信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取班级今日活跃人数（热力图数据）
     */
    @GetMapping("/{id}/class-activity-today")
    public Result<Map<String, Object>> getClassActivityToday(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Map<String, Object> data = statsService.getClassActivityToday(id, userId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取班级活跃信息失败", e);
            return Result.error("获取班级活跃信息失败: " + e.getMessage());
        }
    }
}
