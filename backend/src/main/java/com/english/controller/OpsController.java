package com.english.controller;

import com.english.dto.Result;
import com.english.service.impl.DataCleanupServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 运维管理控制器
 * 处理系统运维相关的数据清理和统计功能
 */
@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class OpsController {

    private static final Logger logger = LoggerFactory.getLogger(OpsController.class);

    @Autowired
    private DataCleanupServiceImpl dataCleanupService;

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
}
