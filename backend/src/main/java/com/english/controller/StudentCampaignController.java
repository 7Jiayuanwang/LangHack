package com.english.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.entity.CampaignParticipation;
import com.english.result.Result;
import com.english.service.CampaignEnrollmentService;
import com.english.service.CampaignService;
import com.english.service.CampaignStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 获取可报名活动列表
     */
    @GetMapping
    public Result<Page<CampaignActivity>> getEnrollableCampaigns(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        try {
            Long userId = 1L; // TODO: 从当前登录用户获取
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
            @RequestParam(required = false) String status) {
        try {
            Long userId = 1L; // TODO: 从当前登录用户获取
            Map<String, Object> params = Map.of("status", status != null ? status : "");
            Page<CampaignActivity> page = enrollmentService.getMyEnrolledCampaigns(userId, current, size, params);
            return Result.success(page);
        } catch (Exception e) {
            log.error("获取我参与的活动失败", e);
            return Result.error("获取我参与的活动失败: " + e.getMessage());
        }
    }

    /**
     * 获取活动详情
     */
    @GetMapping("/{id}")
    public Result<CampaignActivity> getCampaignDetail(@PathVariable Long id) {
        try {
            CampaignActivity campaign = campaignService.getCampaignById(id);
            if (campaign == null) {
                return Result.error("活动不存在");
            }
            return Result.success(campaign);
        } catch (Exception e) {
            log.error("获取活动详情失败", e);
            return Result.error("获取活动详情失败: " + e.getMessage());
        }
    }

    /**
     * 报名参加活动
     */
    @PostMapping("/{id}/enroll")
    public Result<String> enrollCampaign(@PathVariable Long id) {
        try {
            Long userId = 1L; // TODO: 从当前登录用户获取
            enrollmentService.enrollCampaign(id, userId);
            return Result.success("报名成功");
        } catch (Exception e) {
            log.error("报名失败", e);
            return Result.error("报名失败: " + e.getMessage());
        }
    }

    /**
     * 退出活动
     */
    @DeleteMapping("/{id}/withdraw")
    public Result<String> withdrawCampaign(@PathVariable Long id) {
        try {
            Long userId = 1L; // TODO: 从当前登录用户获取
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
    public Result<Map<String, Object>> getMyProgress(@PathVariable Long id) {
        try {
            Long userId = 1L; // TODO: 从当前登录用户获取
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
    public Result<List<Map<String, Object>>> getMyTaskProgress(@PathVariable Long id) {
        try {
            Long userId = 1L; // TODO: 从当前登录用户获取
            List<Map<String, Object>> tasks = statsService.getMyTaskProgress(id, userId);
            return Result.success(tasks);
        } catch (Exception e) {
            log.error("获取任务进度失败", e);
            return Result.error("获取任务进度失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的排名
     */
    @GetMapping("/{id}/ranking")
    public Result<Map<String, Object>> getMyRanking(@PathVariable Long id) {
        try {
            Long userId = 1L; // TODO: 从当前登录用户获取
            Map<String, Object> ranking = statsService.getMyRanking(id, userId);
            return Result.success(ranking);
        } catch (Exception e) {
            log.error("获取我的排名失败", e);
            return Result.error("获取我的排名失败: " + e.getMessage());
        }
    }
}
