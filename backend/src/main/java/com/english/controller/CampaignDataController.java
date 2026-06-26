package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.CampaignDataService;
import com.english.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Autowired
    private UserService userService;

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

            // P3 修复: 从当前登录用户获取 createdBy，避免硬编码
            Long createdBy = getCurrentUserId();

            dataService.recordManualData(campaignId, taskId, userId, value, remark, createdBy);
            return Result.success("录入成功");
        } catch (Exception e) {
            log.error("手动录入数据失败", e);
            return Result.error("手动录入数据失败: " + e.getMessage());
        }
    }

    /**
     * 从 SecurityContext 获取当前登录用户 ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("未登录");
        }
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }
        return user.getId();
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
    public ResponseEntity<Resource> exportCampaignData(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "excel") String format) {
        try {
            byte[] data = dataService.exportCampaignData(campaignId, format);
            ByteArrayResource resource = new ByteArrayResource(data);

            String filename = "campaign_" + campaignId + "_data." + format;

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("导出活动数据失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 导出排名数据
     */
    @GetMapping("/export-ranking")
    public ResponseEntity<Resource> exportRankingData(
            @PathVariable Long campaignId,
            @RequestParam String dimension,
            @RequestParam(defaultValue = "excel") String format) {
        try {
            byte[] data = dataService.exportRankingData(campaignId, dimension, format);
            ByteArrayResource resource = new ByteArrayResource(data);

            String filename = "campaign_" + campaignId + "_ranking_" + dimension + "." + format;

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("导出排名数据失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
