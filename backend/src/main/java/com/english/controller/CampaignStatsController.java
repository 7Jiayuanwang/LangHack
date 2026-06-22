package com.english.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignParticipation;
import com.english.dto.Result;
import com.english.service.CampaignStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 活动统计Controller（管理员端）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/campaigns/{campaignId}/stats")
@PreAuthorize("hasRole('ADMIN')")
public class CampaignStatsController {

    @Autowired
    private CampaignStatsService statsService;

    /**
     * 获取活动概览
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getCampaignOverview(@PathVariable Long campaignId) {
        try {
            Map<String, Object> overview = statsService.getCampaignOverview(campaignId);
            return Result.success(overview);
        } catch (Exception e) {
            log.error("获取活动概览失败", e);
            return Result.error("获取活动概览失败: " + e.getMessage());
        }
    }

    /**
     * 获取个人排名
     */
    @GetMapping("/ranking/individual")
    public Result<Page<CampaignParticipation>> getIndividualRanking(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<CampaignParticipation> ranking = statsService.getRankingByIndividual(campaignId, current, size, Map.of());
            return Result.success(ranking);
        } catch (Exception e) {
            log.error("获取个人排名失败", e);
            return Result.error("获取个人排名失败: " + e.getMessage());
        }
    }

    /**
     * 获取班级排名
     */
    @GetMapping("/ranking/class")
    public Result<List<Map<String, Object>>> getClassRanking(@PathVariable Long campaignId) {
        try {
            List<Map<String, Object>> ranking = statsService.getRankingByClass(campaignId);
            return Result.success(ranking);
        } catch (Exception e) {
            log.error("获取班级排名失败", e);
            return Result.error("获取班级排名失败: " + e.getMessage());
        }
    }

    /**
     * 获取学院排名
     */
    @GetMapping("/ranking/college")
    public Result<List<Map<String, Object>>> getCollegeRanking(@PathVariable Long campaignId) {
        try {
            List<Map<String, Object>> ranking = statsService.getRankingByCollege(campaignId);
            return Result.success(ranking);
        } catch (Exception e) {
            log.error("获取学院排名失败", e);
            return Result.error("获取学院排名失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发数据采集
     */
    @PostMapping("/collect")
    public Result<String> collectData(@PathVariable Long campaignId) {
        try {
            statsService.collectAllCampaignData(campaignId);
            return Result.success("数据采集完成");
        } catch (Exception e) {
            log.error("数据采集失败", e);
            return Result.error("数据采集失败: " + e.getMessage());
        }
    }
}
