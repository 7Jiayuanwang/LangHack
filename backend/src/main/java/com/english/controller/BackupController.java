package com.english.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.dto.Result;
import com.english.entity.BackupConfig;
import com.english.entity.BackupOperationLog;
import com.english.entity.BackupRecord;
import com.english.exception.BusinessException;
import com.english.service.BackupConfigService;
import com.english.service.BackupOperationLogService;
import com.english.service.BackupService;
import com.english.config.DynamicBackupScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);

    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupOperationLogService operationLogService;

    @Autowired
    private BackupConfigService backupConfigService;

    @Autowired
    private DynamicBackupScheduler dynamicBackupScheduler;

    @GetMapping("/list")
    public Result<?> getBackupList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String backupType,
            @RequestParam(required = false) String status) {
        try {
            IPage<BackupRecord> backups = backupService.getBackupsByPage(page, size, backupType, status);
            return Result.success(backups);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取备份列表失败", e);
            return Result.error("获取备份列表失败，请稍后重试");
        }
    }

    @PostMapping("/create")
    public Result<?> createBackup(Authentication authentication,
                                  @RequestParam(defaultValue = "FULL") String mode,
                                  HttpServletRequest request) {
        String username = authentication.getName();
        String ip = getClientIp(request);
        String modeLabel = "INCREMENTAL".equals(mode) ? "增量" : "全量";
        try {
            if (!"FULL".equals(mode) && !"INCREMENTAL".equals(mode)) {
                return Result.error("无效的备份模式，仅支持 FULL 或 INCREMENTAL");
            }
            BackupRecord record = backupService.createManualBackup(username, mode);
            operationLogService.log(username, "BACKUP_CREATE", record.getId(),
                    record.getFilename(), "手动创建" + modeLabel + "备份(任务已提交)", "SUCCESS", ip);
            return Result.success("备份任务已创建", record);
        } catch (BusinessException e) {
            operationLogService.log(username, "BACKUP_CREATE", null,
                    null, "创建" + modeLabel + "备份失败: " + e.getMessage(), "FAILED", ip);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建备份失败", e);
            operationLogService.log(username, "BACKUP_CREATE", null,
                    null, "创建" + modeLabel + "备份失败: 系统内部错误", "FAILED", ip);
            return Result.error("创建备份失败，请稍后重试");
        }
    }

    @GetMapping("/{id}")
    public Result<?> getBackupDetail(@PathVariable Long id) {
        try {
            BackupRecord record = backupService.getBackupById(id);
            return Result.success(record);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取备份详情失败", e);
            return Result.error("获取备份详情失败，请稍后重试");
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadBackup(@PathVariable Long id,
                                                    Authentication authentication,
                                                    HttpServletRequest request) {
        String username = authentication != null ? authentication.getName() : "UNKNOWN";
        String ip = getClientIp(request);
        String filename = null;
        try {
            BackupRecord record = backupService.getBackupById(id);
            filename = record.getFilename();
            Path filePath = backupService.getBackupFilePath(id);

            String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            Resource resource = new org.springframework.core.io.FileSystemResource(filePath);

            long fileSize = Files.size(filePath);
            operationLogService.log(username, "BACKUP_DOWNLOAD", id,
                    filename, "下载备份文件，大小: " + fileSize + " 字节", "SUCCESS", ip);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileSize)
                    .body(resource);
        } catch (BusinessException e) {
            logger.warn("下载备份文件失败: {}", e.getMessage());
            operationLogService.log(username, "BACKUP_DOWNLOAD", id,
                    filename, "下载失败: " + e.getMessage(), "FAILED", ip);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            logger.error("读取备份文件失败", e);
            operationLogService.log(username, "BACKUP_DOWNLOAD", id,
                    filename, "下载失败: 文件读取错误", "FAILED", ip);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            logger.error("下载备份文件失败", e);
            operationLogService.log(username, "BACKUP_DOWNLOAD", id,
                    filename, "下载失败: 系统内部错误", "FAILED", ip);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteBackup(@PathVariable Long id,
                                   Authentication authentication,
                                   HttpServletRequest request) {
        String username = authentication.getName();
        String ip = getClientIp(request);
        String filename = null;
        try {
            BackupRecord record = backupService.getBackupById(id);
            filename = record.getFilename();
            backupService.deleteBackup(id);
            operationLogService.log(username, "BACKUP_DELETE", id,
                    filename, "删除备份文件", "SUCCESS", ip);
            return Result.success("备份已删除");
        } catch (BusinessException e) {
            operationLogService.log(username, "BACKUP_DELETE", id,
                    filename, "删除失败: " + e.getMessage(), "FAILED", ip);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除备份失败", e);
            operationLogService.log(username, "BACKUP_DELETE", id,
                    filename, "删除备份失败: 系统内部错误", "FAILED", ip);
            return Result.error("删除备份失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/restore")
    public Result<?> restoreBackup(@PathVariable Long id,
                                    @RequestBody Map<String, String> body,
                                    Authentication authentication,
                                    HttpServletRequest request) {
        String username = authentication.getName();
        String ip = getClientIp(request);
        String filename = null;
        try {
            String confirmDatabaseName = body.get("confirmDatabaseName");
            if (confirmDatabaseName == null || confirmDatabaseName.trim().isEmpty()) {
                return Result.error("请输入数据库名称以确认恢复操作");
            }
            BackupRecord record = backupService.getBackupById(id);
            filename = record.getFilename();
            backupService.restoreFromBackup(id, confirmDatabaseName.trim());
            operationLogService.log(username, "BACKUP_RESTORE", id,
                    filename, "从备份恢复数据库 [" + confirmDatabaseName.trim() + "]", "SUCCESS", ip);
            return Result.success("数据库恢复成功");
        } catch (BusinessException e) {
            operationLogService.log(username, "BACKUP_RESTORE", id,
                    filename, "恢复失败: " + e.getMessage(), "FAILED", ip);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("恢复数据库失败", e);
            operationLogService.log(username, "BACKUP_RESTORE", id,
                    filename, "恢复失败: 系统内部错误", "FAILED", ip);
            return Result.error("恢复数据库失败，请稍后重试");
        }
    }

    @GetMapping("/stats")
    public Result<?> getBackupStats() {
        try {
            Map<String, Object> stats = backupService.getBackupStats();
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取备份统计失败", e);
            return Result.error("获取备份统计失败，请稍后重试");
        }
    }

    @GetMapping("/config")
    public Result<?> getBackupConfig() {
        try {
            Map<String, Object> config = backupService.getBackupConfig();
            return Result.success(config);
        } catch (Exception e) {
            logger.error("获取备份配置失败", e);
            return Result.error("获取备份配置失败，请稍后重试");
        }
    }

    @PutMapping("/config")
    public Result<?> updateBackupConfig(@RequestBody BackupConfig config,
                                         Authentication authentication,
                                         HttpServletRequest request) {
        String username = authentication.getName();
        String ip = getClientIp(request);
        try {
            BackupConfig updated = backupConfigService.updateConfig(config, username);
            dynamicBackupScheduler.reschedule();
            operationLogService.log(username, "CONFIG_UPDATE", null,
                    null, "更新备份配置: cron=" + updated.getBackupCron()
                            + ", retention=" + updated.getRetentionDays() + "d"
                            + ", incremental=" + updated.getIncrementalEnabled(),
                    "SUCCESS", ip);
            return Result.success("备份配置已更新", updated);
        } catch (BusinessException e) {
            operationLogService.log(username, "CONFIG_UPDATE", null,
                    null, "更新备份配置失败: " + e.getMessage(), "FAILED", ip);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新备份配置失败", e);
            operationLogService.log(username, "CONFIG_UPDATE", null,
                    null, "更新备份配置失败: 系统内部错误", "FAILED", ip);
            return Result.error("更新备份配置失败，请稍后重试");
        }
    }

    @PostMapping("/cleanup")
    public Result<?> triggerCleanup(Authentication authentication,
                                     HttpServletRequest request) {
        String username = authentication.getName();
        String ip = getClientIp(request);
        try {
            int deletedCount = backupService.cleanupOldBackups();
            operationLogService.log(username, "BACKUP_CLEANUP", null,
                    null, "清理过期备份，删除 " + deletedCount + " 个", "SUCCESS", ip);
            return Result.success("清理完成，共删除 " + deletedCount + " 个过期备份", deletedCount);
        } catch (BusinessException e) {
            operationLogService.log(username, "BACKUP_CLEANUP", null,
                    null, "清理失败: " + e.getMessage(), "FAILED", ip);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("清理备份失败", e);
            operationLogService.log(username, "BACKUP_CLEANUP", null,
                    null, "清理备份失败: 系统内部错误", "FAILED", ip);
            return Result.error("清理备份失败，请稍后重试");
        }
    }

    // ========== Operation Logs Endpoint ==========

    @GetMapping("/logs")
    public Result<?> getOperationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String operationType) {
        try {
            IPage<BackupOperationLog> logs = operationLogService.getLogsByPage(page, size, operationType);
            return Result.success(logs);
        } catch (Exception e) {
            logger.error("获取操作日志失败", e);
            return Result.error("获取操作日志失败，请稍后重试");
        }
    }

    // ========== Helper ==========

    private static final java.util.regex.Pattern IP_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F.:]{1,45}$");

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For may contain multiple IPs, take the first (client IP)
            int commaIdx = ip.indexOf(',');
            ip = commaIdx > 0 ? ip.substring(0, commaIdx).trim() : ip.trim();
            if (IP_PATTERN.matcher(ip).matches()) return ip;
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            ip = ip.trim();
            if (IP_PATTERN.matcher(ip).matches()) return ip;
        }
        return request.getRemoteAddr();
    }
}
