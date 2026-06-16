package com.english.controller;

import com.english.dto.Result;
import com.english.entity.SystemConfig;
import com.english.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器 - 运维面板动态配置
 */
@RestController
@RequestMapping("/api/ops/system-config")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    /**
     * 获取文件上传配置
     */
    @GetMapping("/upload-limits")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getUploadLimits() {
        Map<String, Object> limits = new HashMap<>();

        SystemConfig globalLimit = systemConfigService.getByKey("upload.max_file_size.global");
        limits.put("globalMaxFileSize", globalLimit != null ? globalLimit.getConfigValue() : "10MB");

        List<SystemConfig> userLimits = systemConfigService.listByGroup("upload.max_file_size.user");
        Map<String, String> userLimitMap = new HashMap<>();
        for (SystemConfig config : userLimits) {
            String username = config.getConfigKey().replace("upload.max_file_size.user.", "");
            userLimitMap.put(username, config.getConfigValue());
        }
        limits.put("userSpecificLimits", userLimitMap);

        return Result.success(limits);
    }

    /**
     * 更新全局文件上传限制
     */
    @PutMapping("/upload-limits/global")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateGlobalUploadLimit(@RequestBody Map<String, String> request) {
        String maxSize = request.get("maxFileSize");
        if (maxSize == null || !maxSize.matches("\\d+[KMG]B")) {
            return Result.error("格式错误，示例：10MB、100KB");
        }

        systemConfigService.upsert("security", "upload.max_file_size.global", maxSize, "全局文件上传大小限制");
        return Result.success("已更新全局上传限制", null);
    }

    /**
     * 设置特定用户的上传限制
     */
    @PutMapping("/upload-limits/user/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateUserUploadLimit(@PathVariable String username, @RequestBody Map<String, String> request) {
        String maxSize = request.get("maxFileSize");
        if (maxSize == null || !maxSize.matches("\\d+[KMG]B")) {
            return Result.error("格式错误，示例：10MB、100KB");
        }

        String key = "upload.max_file_size.user." + username;
        systemConfigService.upsert("upload.max_file_size.user", key, maxSize, "用户 " + username + " 的上传限制");
        return Result.success("已更新用户上传限制", null);
    }

    /**
     * 删除特定用户的上传限制
     */
    @DeleteMapping("/upload-limits/user/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteUserUploadLimit(@PathVariable String username) {
        String key = "upload.max_file_size.user." + username;
        systemConfigService.deleteByKey(key);
        return Result.success("已删除用户上传限制", null);
    }
}
