package com.english.controller;

import com.english.result.Result;
import com.english.service.CampaignDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 活动数据管理Controller（管理员端）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/campaigns/{campaignId}/data")
@PreAuthorize("hasRole('ADMIN')")
public class CampaignDataController {

    @Autowired
    private CampaignDataService dataService;

    /**
     * 手动录入数据
     */
    @PostMapping("/manual")
    public Result<String> recordManualData(
            @PathVariable Long campaignId,
            @RequestBody Map<String, Object> request) {
        try {
            Long taskId = Long.valueOf(request.get("taskId").toString());
            Long userId = Long.valueOf(request.get("userId").toString());
            BigDecimal value = new BigDecimal(request.get("value").toString());
            String remark = request.get("remark") != null ? request.get("remark").toString() : null;
            Long createdBy = 1L; // TODO: 从当前登录用户获取

            dataService.recordManualData(campaignId, taskId, userId, value, remark, createdBy);
            return Result.success("录入成功");
        } catch (Exception e) {
            log.error("手动录入数据失败", e);
            return Result.error("手动录入数据失败: " + e.getMessage());
        }
    }

    /**
     * Excel批量导入
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importData(
            @PathVariable Long campaignId,
            @RequestParam Long taskId,
            @RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = dataService.importFromExcel(campaignId, taskId, file);
            return Result.success(result);
        } catch (Exception e) {
            log.error("批量导入失败", e);
            return Result.error("批量导入失败: " + e.getMessage());
        }
    }

    /**
     * 导出活动数据
     */
    @GetMapping("/export")
    public Result<byte[]> exportCampaignData(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "excel") String format) {
        try {
            byte[] data = dataService.exportCampaignData(campaignId, format);
            return Result.success(data);
        } catch (Exception e) {
            log.error("导出活动数据失败", e);
            return Result.error("导出活动数据失败: " + e.getMessage());
        }
    }

    /**
     * 导出排名数据
     */
    @GetMapping("/export-ranking")
    public Result<byte[]> exportRankingData(
            @PathVariable Long campaignId,
            @RequestParam String dimension,
            @RequestParam(defaultValue = "excel") String format) {
        try {
            byte[] data = dataService.exportRankingData(campaignId, dimension, format);
            return Result.success(data);
        } catch (Exception e) {
            log.error("导出排名数据失败", e);
            return Result.error("导出排名数据失败: " + e.getMessage());
        }
    }
}
