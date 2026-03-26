package com.english.controller;

import com.english.dto.Result;
import com.english.entity.SystemConfig;
import com.english.exception.BusinessException;
import com.english.service.SystemConfigService;
import com.english.service.TencentTranslateService;
import com.english.service.impl.DataCleanupServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维管理控制器
 * 处理系统运维相关的数据清理、统计和系统配置管理功能
 */
@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class OpsController {

    private static final Logger logger = LoggerFactory.getLogger(OpsController.class);

    @Autowired
    private DataCleanupServiceImpl dataCleanupService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private TencentTranslateService translateService;

    /**
     * 清理不一致的数据
     */
    @PostMapping("/cleanup-data")
    public Result<String> cleanupData() {
        try {
            dataCleanupService.cleanupInconsistentData();
            return Result.success("数据清理完成，请查看控制台输出获取详细信息");
        } catch (Exception e) {
            logger.error("数据清理失败", e);
            return Result.error("数据清理失败，请稍后重试");
        }
    }

    /**
     * 打印详细数据统计信息
     */
    @GetMapping("/data-stats")
    public Result<String> printDataStatistics() {
        try {
            dataCleanupService.printDataStatistics();
            return Result.success("数据统计信息已打印到控制台");
        } catch (Exception e) {
            logger.error("获取数据统计失败", e);
            return Result.error("获取数据统计失败，请稍后重试");
        }
    }

    // ==================== 系统配置管理 ====================

    /**
     * 获取指定分组的系统配置（敏感字段脱敏）
     */
    @GetMapping("/system-config")
    public Result<List<SystemConfig>> getSystemConfig(@RequestParam String group) {
        try {
            List<SystemConfig> configs = systemConfigService.listByGroup(group);
            // Mask secret values for display
            for (SystemConfig config : configs) {
                if (config.getIsSecret() != null && config.getIsSecret() == 1
                        && config.getConfigValue() != null && !config.getConfigValue().isEmpty()) {
                    String value = config.getConfigValue();
                    if (value.length() <= 4) {
                        config.setConfigValue("****");
                    } else {
                        config.setConfigValue("****" + value.substring(value.length() - 4));
                    }
                }
            }
            return Result.success(configs);
        } catch (Exception e) {
            logger.error("获取系统配置失败", e);
            return Result.error("获取系统配置失败，请稍后重试");
        }
    }

    /**
     * 批量更新系统配置
     */
    @PutMapping("/system-config")
    public Result<String> updateSystemConfig(@RequestBody List<SystemConfig> configs,
                                             Authentication authentication) {
        try {
            String username = authentication.getName();
            systemConfigService.batchUpdate(configs, username);
            return Result.success("配置更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新系统配置失败", e);
            return Result.error("更新系统配置失败，请稍后重试");
        }
    }

    /**
     * 测试翻译服务连通性
     */
    @PostMapping("/system-config/test-translate")
    public Result<Map<String, Object>> testTranslate(@RequestBody(required = false) Map<String, String> body) {
        try {
            String testText = (body != null && body.get("text") != null) ? body.get("text") : "Hello, world!";
            String result = translateService.testTranslation(testText);

            Map<String, Object> data = new HashMap<>();
            data.put("input", testText);
            data.put("output", result);
            data.put("success", true);
            return Result.success(data);
        } catch (BusinessException e) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("error", e.getMessage());
            return Result.success(data);
        } catch (Exception e) {
            logger.error("翻译测试失败", e);
            return Result.error("翻译测试失败，请稍后重试");
        }
    }
}
