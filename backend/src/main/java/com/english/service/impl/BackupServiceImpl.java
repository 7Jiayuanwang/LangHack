package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.BackupConfig;
import com.english.entity.BackupRecord;
import com.english.exception.BusinessException;
import com.english.mapper.BackupRecordMapper;
import com.english.service.BackupConfigService;
import com.english.service.BackupOperationLogService;
import com.english.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BackupServiceImpl extends ServiceImpl<BackupRecordMapper, BackupRecord> implements BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupServiceImpl.class);
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter SQL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicBoolean backupInProgress = new AtomicBoolean(false);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private static final Set<String> SUCCESS_STATUSES = Set.of("SUCCESS", "COMPLETED");

    private final Executor executor;
    private final DataSource masterDataSource;
    private final BackupOperationLogService operationLogService;
    private final BackupConfigService backupConfigService;

    @Value("${backup.enabled:true}")
    private boolean backupEnabled;

    @Value("${backup.dir:./backups}")
    private String backupDir;

    @Value("${backup.retention-days:30}")
    private int retentionDays;

    @Value("${backup.max-count:50}")
    private int maxCount;

    @Value("${backup.mysqldump-path:mysqldump}")
    private String mysqldumpPath;

    @Value("${backup.incremental.enabled:false}")
    private boolean incrementalEnabled;

    @Value("${backup.incremental.full-backup-interval-days:7}")
    private int fullBackupIntervalDays;

    @Value("${spring.datasource.master.jdbc-url}")
    private String jdbcUrl;

    @Value("${spring.datasource.master.username}")
    private String dbUsername;

    @Value("${spring.datasource.master.password}")
    private String dbPassword;

    public BackupServiceImpl(@Qualifier("versionGeneratorExecutor") Executor executor,
                             @Qualifier("masterDataSource") DataSource masterDataSource,
                             BackupOperationLogService operationLogService,
                             BackupConfigService backupConfigService) {
        this.executor = executor;
        this.masterDataSource = masterDataSource;
        this.operationLogService = operationLogService;
        this.backupConfigService = backupConfigService;
    }

    @PostConstruct
    public void init() {
        try {
            Path backupPath = Paths.get(backupDir);
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
                logger.info("Backup directory created: {}", backupPath.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create backup directory: {}", backupDir, e);
        }

        // Cleanup stale IN_PROGRESS records from previous application crash/restart
        try {
            QueryWrapper<BackupRecord> staleWrapper = new QueryWrapper<>();
            staleWrapper.eq("status", "IN_PROGRESS");
            List<BackupRecord> staleRecords = list(staleWrapper);
            for (BackupRecord record : staleRecords) {
                record.setStatus("FAILED");
                record.setErrorMessage("应用重启导致备份中断");
                record.setCompletedTime(LocalDateTime.now());
                updateById(record);
                logger.warn("Marked stale IN_PROGRESS backup as FAILED: {}", record.getFilename());
                // Audit trail for stale record cleanup
                operationLogService.log("SYSTEM", "BACKUP_INTERRUPTED", record.getId(),
                        record.getFilename(), "应用重启，中断中的备份任务自动标记为失败", "FAILED", null);
            }
        } catch (Exception e) {
            logger.error("Failed to cleanup stale backup records", e);
        }

        // Reset lock in case of Spring context reload (devtools, tests)
        backupInProgress.set(false);
    }

    @Override
    public BackupRecord createManualBackup(String createdBy, String mode) {
        if (!backupEnabled) {
            throw new BusinessException("备份功能已禁用");
        }

        // Pre-flight checks before acquiring lock
        long currentCount = count();
        if (currentCount >= maxCount) {
            throw new BusinessException("备份数量已达上限(" + maxCount + ")，请先清理旧备份");
        }
        long usableSpace = Paths.get(backupDir).toFile().getUsableSpace();
        if (usableSpace < 100 * 1024 * 1024) { // 100MB minimum
            throw new BusinessException("磁盘空间不足，请先清理旧备份或扩展存储");
        }

        if (!backupInProgress.compareAndSet(false, true)) {
            throw new BusinessException("已有备份任务正在执行，请稍后再试");
        }

        // Validate mode
        if (!"FULL".equals(mode) && !"INCREMENTAL".equals(mode)) {
            backupInProgress.set(false);
            throw new BusinessException("无效的备份模式，仅支持 FULL 或 INCREMENTAL");
        }

        // For incremental, verify a full backup exists
        if ("INCREMENTAL".equals(mode)) {
            BackupRecord lastFull = getLastSuccessfulFullBackup();
            if (lastFull == null) {
                backupInProgress.set(false);
                throw new BusinessException("尚无全量备份，请先执行全量备份");
            }
        }

        BackupRecord record;
        try {
            record = createBackupRecord("MANUAL", mode, createdBy);
            save(record);
        } catch (BusinessException e) {
            backupInProgress.set(false);
            throw e;
        } catch (Exception e) {
            backupInProgress.set(false);
            logger.error("Failed to create backup record", e);
            throw new BusinessException("创建备份记录失败，请稍后重试");
        }

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    if ("INCREMENTAL".equals(record.getBackupMode())) {
                        executeIncrementalBackup(record);
                    } else {
                        executeFullBackup(record);
                    }
                    // Log async completion result
                    String logDetail = isSuccessfulStatus(record.getStatus())
                            ? "备份执行成功，文件大小: " + formatFileSizeInternal(record.getFileSize())
                            : "备份执行失败: " + record.getErrorMessage();
                    operationLogService.log(record.getCreatedBy(), "BACKUP_CREATE", record.getId(),
                            record.getFilename(), logDetail, record.getStatus(), null);
                } catch (Exception e) {
                    logger.error("Backup task failed unexpectedly", e);
                    try {
                        record.setStatus("FAILED");
                        record.setErrorMessage("备份执行异常");
                        record.setCompletedTime(LocalDateTime.now());
                        updateById(record);
                    } catch (Exception updateEx) {
                        logger.error("Failed to update backup record after failure", updateEx);
                    }
                    operationLogService.log(record.getCreatedBy(), "BACKUP_CREATE", record.getId(),
                            record.getFilename(), "备份执行异常", "FAILED", null);
                } finally {
                    backupInProgress.set(false);
                }
            }, executor);
        } catch (Throwable t) {
            backupInProgress.set(false);
            logger.error("Failed to submit backup task to executor", t);
            record.setStatus("FAILED");
            record.setErrorMessage("提交备份任务失败");
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
            if (t instanceof Error) throw (Error) t;
            throw new BusinessException("备份任务提交失败，请稍后重试");
        }

        return record;
    }

    // 分类备份表映射
    private static final Map<String, List<String>> CATEGORY_TABLES = new LinkedHashMap<>();
    static {
        CATEGORY_TABLES.put("system_config", Arrays.asList(
                "system_config", "sso_config", "ai_provider", "backup_config", "update_config"));
        CATEGORY_TABLES.put("user_data", Arrays.asList(
                "users", "student_classes", "user_tags", "article_favorites",
                "learning_records", "text_learning_records", "user_text_reads",
                "vocabulary_books", "underlines"));
        CATEGORY_TABLES.put("business_data", Arrays.asList(
                "courses", "units", "texts", "classes", "assignments",
                "words", "media", "videos", "score_records"));
        CATEGORY_TABLES.put("school_data", Arrays.asList(
                "school_student", "school_teacher", "school_course", "teaching_class",
                "course_schedule", "course_selection", "level_exam_scores",
                "teacher_student_binding", "administrative_class", "teaching_plans",
                "teaching_plan_courses", "sync_logs"));
    }

    @Override
    public BackupRecord createCategoryBackup(String createdBy, String category) {
        if (!backupEnabled) {
            throw new BusinessException("备份功能已禁用");
        }
        if (!CATEGORY_TABLES.containsKey(category)) {
            throw new BusinessException("不支持的备份分类: " + category
                    + "，可选: " + String.join(", ", CATEGORY_TABLES.keySet()));
        }
        long usableSpace = Paths.get(backupDir).toFile().getUsableSpace();
        if (usableSpace < 100 * 1024 * 1024) {
            throw new BusinessException("磁盘空间不足，请先清理旧备份或扩展存储");
        }
        if (!backupInProgress.compareAndSet(false, true)) {
            throw new BusinessException("已有备份任务正在执行，请稍后再试");
        }

        BackupRecord record;
        try {
            String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
            String filename = "english_learning_" + category + "_" + timestamp + ".sql";
            record = new BackupRecord();
            record.setFilename(filename);
            record.setBackupType("MANUAL");
            record.setBackupMode("FULL");
            record.setCategory(category);
            record.setStatus("IN_PROGRESS");
            record.setCreatedBy(createdBy);
            record.setCreatedTime(LocalDateTime.now());
            record.setStoragePath(Paths.get(backupDir, filename).toAbsolutePath().toString());
            save(record);
        } catch (Exception e) {
            backupInProgress.set(false);
            logger.error("Failed to create category backup record", e);
            throw new BusinessException("创建备份记录失败，请稍后重试");
        }

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    executeCategoryBackup(record, CATEGORY_TABLES.get(category));
                    String logDetail = isSuccessfulStatus(record.getStatus())
                            ? "分类备份[" + category + "]完成，文件大小: " + formatFileSizeInternal(record.getFileSize())
                            : "分类备份[" + category + "]失败: " + record.getErrorMessage();
                    operationLogService.log(record.getCreatedBy(), "BACKUP_CREATE", record.getId(),
                            record.getFilename(), logDetail, record.getStatus(), null);
                } catch (Exception e) {
                    logger.error("Category backup task failed unexpectedly", e);
                    try {
                        record.setStatus("FAILED");
                        record.setErrorMessage("备份执行异常");
                        record.setCompletedTime(LocalDateTime.now());
                        updateById(record);
                    } catch (Exception updateEx) {
                        logger.error("Failed to update backup record after failure", updateEx);
                    }
                    operationLogService.log(record.getCreatedBy(), "BACKUP_CREATE", record.getId(),
                            record.getFilename(), "分类备份执行异常", "FAILED", null);
                } finally {
                    backupInProgress.set(false);
                }
            }, executor);
        } catch (Throwable t) {
            backupInProgress.set(false);
            logger.error("Failed to submit category backup task to executor", t);
            record.setStatus("FAILED");
            record.setErrorMessage("提交备份任务失败");
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
            if (t instanceof Error) throw (Error) t;
            throw new BusinessException("备份任务提交失败，请稍后重试");
        }

        return record;
    }

    @Override
    public BackupRecord importBackup(String createdBy, MultipartFile file, String mode, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的备份文件");
        }

        String originalFilename = defaultString(file.getOriginalFilename(), "backup.sql").trim();
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".sql") || lowerName.endsWith(".gz"))) {
            throw new BusinessException("仅支持导入 .sql 或 .gz 备份文件");
        }

        String normalizedMode = normalizeImportMode(mode, originalFilename);
        String normalizedCategory = normalizeImportCategory(category, originalFilename);
        String safeFilename = buildImportedFilename(originalFilename);
        Path targetPath = resolveImportTargetPath(safeFilename);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            logger.error("Failed to store imported backup: {}", originalFilename, e);
            throw new BusinessException("保存导入备份文件失败");
        }

        BackupRecord record = new BackupRecord();
        record.setFilename(safeFilename);
        record.setFileSize(file.getSize());
        record.setBackupType("IMPORTED");
        record.setBackupMode(normalizedMode);
        record.setCategory(normalizedCategory);
        record.setStatus("SUCCESS");
        record.setStoragePath(targetPath.toString());
        record.setCreatedBy(createdBy);
        record.setDatabaseSize(0L);
        record.setCreatedTime(LocalDateTime.now());
        record.setCompletedTime(LocalDateTime.now());
        try {
            save(record);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException cleanupEx) {
                logger.warn("Failed to clean up imported backup file after DB save failure: {}", targetPath, cleanupEx);
            }
            logger.error("Failed to save imported backup record", e);
            throw new BusinessException("写入导入备份记录失败");
        }
        return enrichBackupRecord(record);
    }

    private void executeCategoryBackup(BackupRecord record, List<String> tables) {
        Path outputPath = Paths.get(record.getStoragePath()).toAbsolutePath().normalize();
        Path allowedDir = Paths.get(backupDir).toAbsolutePath().normalize();
        if (!outputPath.startsWith(allowedDir)) {
            logger.error("Path traversal detected in category backup output: {}", record.getFilename());
            record.setStatus("FAILED");
            record.setErrorMessage("备份输出路径异常");
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
            return;
        }

        try {
            DbConnectionInfo connInfo = parseJdbcUrl();
            String databaseName = extractDatabaseName();

            List<String> cmd = new ArrayList<>(Arrays.asList(
                    mysqldumpPath,
                    "-h", connInfo.host,
                    "-P", String.valueOf(connInfo.port),
                    "-u", dbUsername,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    "--result-file=" + record.getStoragePath(),
                    databaseName
            ));
            cmd.addAll(tables);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("MYSQL_PWD", dbPassword);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try {
                StringBuilder output = new StringBuilder();
                Thread outputReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (IOException ignored) {}
                });
                outputReader.setDaemon(true);
                outputReader.start();

                boolean finished = process.waitFor(10, TimeUnit.MINUTES);
                outputReader.join(5000);

                if (!finished) {
                    process.destroyForcibly();
                    record.setStatus("FAILED");
                    record.setErrorMessage("备份超时（10分钟）");
                    record.setCompletedTime(LocalDateTime.now());
                    updateById(record);
                    return;
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    String errMsg = output.toString().trim();
                    logger.error("mysqldump category backup failed (exit {}): {}", exitCode, errMsg);
                    record.setStatus("FAILED");
                    record.setErrorMessage("mysqldump 退出码: " + exitCode);
                    record.setCompletedTime(LocalDateTime.now());
                    updateById(record);
                    return;
                }
            } finally {
                process.destroy();
            }

            Path filePath = Paths.get(record.getStoragePath());
            long fileSize = Files.exists(filePath) ? Files.size(filePath) : 0;
            record.setFileSize(fileSize);
            record.setStatus("SUCCESS");
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
            logger.info("Category backup [{}] completed: {} ({} bytes)", record.getCategory(), record.getFilename(), fileSize);

        } catch (Exception e) {
            logger.error("Category backup execution failed", e);
            record.setStatus("FAILED");
            record.setErrorMessage("备份执行失败: " + e.getMessage());
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
        }
    }

    @Override
    public void executeScheduledBackup() {
        BackupConfig effectiveConfig = getEffectiveConfig();
        if (!Boolean.TRUE.equals(effectiveConfig.getAutoBackupEnabled())) {
            logger.debug("Scheduled backup skipped: backup is disabled");
            return;
        }
        if (!backupInProgress.compareAndSet(false, true)) {
            logger.warn("Scheduled backup skipped: another backup is in progress");
            return;
        }

        BackupRecord record = null;
        try {
            // Determine backup mode inside try-finally to guarantee lock release on DB failure
            String mode = determineScheduledBackupMode();
            logger.info("Starting scheduled {} backup...", mode);

            record = createBackupRecord("SCHEDULED", mode, "SYSTEM");
            save(record);
            if ("INCREMENTAL".equals(mode)) {
                executeIncrementalBackup(record);
            } else {
                executeFullBackup(record);
            }
            // Log scheduled backup result
            String modeLabel = "INCREMENTAL".equals(mode) ? "增量" : "全量";
            String logDetail = isSuccessfulStatus(record.getStatus())
                    ? "定时" + modeLabel + "备份完成，文件大小: " + formatFileSizeInternal(record.getFileSize())
                    : "定时" + modeLabel + "备份失败: " + record.getErrorMessage();
            operationLogService.log("SYSTEM", "BACKUP_SCHEDULED", record.getId(),
                    record.getFilename(), logDetail, record.getStatus(), null);
        } catch (Throwable e) {
            logger.error("Scheduled backup failed", e);
            // Log scheduled backup failure
            String safeMsg = (e instanceof BusinessException) ? e.getMessage() : "内部执行异常";
            operationLogService.log("SYSTEM", "BACKUP_SCHEDULED",
                    record != null ? record.getId() : null,
                    record != null ? record.getFilename() : null,
                    "定时备份失败: " + safeMsg, "FAILED", null);
            // Update record status if it was created but not yet marked as FAILED/SUCCESS
            if (record != null && record.getId() != null
                    && !isSuccessfulStatus(record.getStatus()) && !"FAILED".equals(normalizeStatus(record.getStatus()))) {
                try {
                    record.setStatus("FAILED");
                    record.setErrorMessage("定时备份执行异常");
                    record.setCompletedTime(LocalDateTime.now());
                    updateById(record);
                } catch (Exception updateEx) {
                    logger.error("Failed to update backup record after scheduled backup failure", updateEx);
                }
            }
        } finally {
            backupInProgress.set(false);
        }

        // Auto-cleanup after scheduled backup
        try {
            int cleaned = cleanupOldBackups();
            if (cleaned > 0) {
                operationLogService.log("SYSTEM", "BACKUP_CLEANUP", null, null,
                        "定时备份后自动清理，删除 " + cleaned + " 个过期文件", "SUCCESS", null);
            }
        } catch (Exception e) {
            logger.error("Auto cleanup after scheduled backup failed", e);
            operationLogService.log("SYSTEM", "BACKUP_CLEANUP", null, null,
                    "定时备份后自动清理失败", "FAILED", null);
        }
    }

    @Override
    public IPage<BackupRecord> getBackupsByPage(int page, int size, String backupType, String status) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        // Lazily clean up IN_PROGRESS records older than 2 hours (handles cases where
        // @PostConstruct cleanup was skipped because the container was not restarted)
        try {
            LocalDateTime twoHoursAgo = LocalDateTime.now().minus(2, ChronoUnit.HOURS);
            QueryWrapper<BackupRecord> staleWrapper = new QueryWrapper<>();
            staleWrapper.eq("status", "IN_PROGRESS").lt("created_time", twoHoursAgo);
            List<BackupRecord> staleRecords = list(staleWrapper);
            for (BackupRecord stale : staleRecords) {
                stale.setStatus("FAILED");
                stale.setErrorMessage("备份超时（超过2小时未完成）");
                stale.setCompletedTime(LocalDateTime.now());
                updateById(stale);
                logger.warn("Marked timed-out IN_PROGRESS backup as FAILED: id={}, file={}", stale.getId(), stale.getFilename());
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup timed-out backup records: {}", e.getMessage());
        }

        QueryWrapper<BackupRecord> wrapper = new QueryWrapper<>();
        if (backupType != null && !backupType.isEmpty()) {
            wrapper.eq("backup_type", backupType);
        }
        if (status != null && !status.isEmpty()) {
            if (isSuccessfulStatus(status)) {
                wrapper.in("status", SUCCESS_STATUSES);
            } else {
                wrapper.eq("status", normalizeStatus(status));
            }
        }
        wrapper.orderByDesc("created_time");
        IPage<BackupRecord> result = page(new Page<>(page, size), wrapper);
        enrichBackupPage(result);
        return result;
    }

    @Override
    public BackupRecord getBackupById(Long id) {
        BackupRecord record = getById(id);
        if (record == null) {
            throw new BusinessException("备份记录不存在");
        }
        return enrichBackupRecord(record);
    }

    @Override
    public Map<String, Object> getBackupPreview(Long id, int maxLines, int maxChars) {
        BackupRecord record = getBackupById(id);
        if (!isSuccessfulStatus(record.getStatus())) {
            throw new BusinessException("仅可预览状态为成功的备份文件");
        }

        Path filePath = resolveValidatedBackupPath(record, true);
        int effectiveMaxLines = Math.min(Math.max(maxLines, 1), 300);
        int effectiveMaxChars = Math.min(Math.max(maxChars, 200), 30000);
        StringBuilder preview = new StringBuilder();
        int lineCount = 0;
        boolean truncated = false;

        try (BufferedReader reader = openPreviewReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineCount >= effectiveMaxLines || preview.length() + line.length() + 1 > effectiveMaxChars) {
                    truncated = true;
                    break;
                }
                if (lineCount > 0) {
                    preview.append('\n');
                }
                preview.append(line);
                lineCount++;
            }
        } catch (IOException e) {
            logger.error("Failed to read backup preview: {}", filePath, e);
            throw new BusinessException("读取备份预览失败");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("preview", preview.toString());
        result.put("truncated", truncated);
        result.put("lineCount", lineCount);
        result.put("maxLines", effectiveMaxLines);
        result.put("maxChars", effectiveMaxChars);
        return result;
    }

    @Override
    public void deleteBackup(Long id) {
        BackupRecord record = getBackupById(id);

        // Prevent deleting a backup that is currently in progress
        if ("IN_PROGRESS".equals(record.getStatus())) {
            throw new BusinessException("备份正在执行中，无法删除");
        }

        // Delete the physical file
        try {
            Path filePath = resolveValidatedBackupPath(record, false);

            if (Files.exists(filePath)) {
                if (!Files.isRegularFile(filePath)) {
                    throw new BusinessException("备份文件类型无效，无法删除");
                }
                Files.delete(filePath);
                logger.info("Backup file deleted: {}", filePath);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to delete backup file: {}, database record will not be removed", record.getFilename(), e);
            throw new BusinessException("备份文件删除失败，请检查文件权限");
        }

        removeById(id);
    }

    @Override
    public Path getBackupFilePath(Long id) {
        BackupRecord record = getBackupById(id);
        if (!isSuccessfulStatus(record.getStatus())) {
            throw new BusinessException("仅可下载状态为成功的备份文件");
        }
        return resolveValidatedBackupPath(record, true);
    }

    @Override
    public void restoreFromBackup(Long id, String confirmDatabaseName) {
        String databaseName = extractDatabaseName();
        if (!databaseName.equals(confirmDatabaseName)) {
            throw new BusinessException("数据库名称确认不匹配，请确认后重新输入");
        }

        // Prevent concurrent backup/restore operations
        if (!backupInProgress.compareAndSet(false, true)) {
            throw new BusinessException("已有备份或恢复任务正在执行，请稍后再试");
        }

        try {
            BackupRecord record = getBackupById(id);
            if (!isSuccessfulStatus(record.getStatus())) {
                throw new BusinessException("仅可从成功的备份中恢复");
            }

            // For incremental backups, warn that full backup should be restored first
            if ("INCREMENTAL".equals(record.getBackupMode())) {
                logger.warn("Restoring incremental backup {}. Ensure base backup (ID: {}) has been restored first.",
                        id, record.getBaseBackupId());
            }

            Path filePath = resolveValidatedBackupPath(record, true);

            DbConnectionInfo connInfo = parseJdbcUrl();
            String mysqlPath;
            int lastIdx = mysqldumpPath.lastIndexOf("mysqldump");
            if (lastIdx >= 0) {
                mysqlPath = mysqldumpPath.substring(0, lastIdx) + "mysql" + mysqldumpPath.substring(lastIdx + "mysqldump".length());
            } else {
                mysqlPath = "mysql";
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        mysqlPath,
                        "-h", connInfo.host,
                        "-P", String.valueOf(connInfo.port),
                        "-u", dbUsername,
                        databaseName
                );
                pb.environment().put("MYSQL_PWD", dbPassword);
                pb.redirectErrorStream(true);

                Process process = pb.start();
                try {
                    AtomicReference<IOException> inputError = new AtomicReference<>();
                    Thread inputWriter = new Thread(() -> {
                        try (OutputStream processInput = process.getOutputStream();
                             InputStream backupInput = openBackupInputStream(filePath)) {
                            backupInput.transferTo(processInput);
                        } catch (IOException e) {
                            inputError.set(e);
                            logger.error("Error streaming backup into mysql process", e);
                        }
                    });
                    inputWriter.setDaemon(true);
                    inputWriter.start();

                    // Read stdout in daemon thread to prevent readLine blocking timeout
                    StringBuilder output = new StringBuilder();
                    Thread outputReader = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                output.append(line).append("\n");
                            }
                        } catch (IOException e) {
                            logger.warn("Error reading mysql restore output", e);
                        }
                    });
                    outputReader.setDaemon(true);
                    outputReader.start();

                    boolean completed = process.waitFor(60, TimeUnit.MINUTES);
                    if (!completed) {
                        process.destroyForcibly();
                        inputWriter.join(5000);
                        outputReader.join(5000);
                        logger.error("Database restore timed out after 60 minutes");
                        throw new BusinessException("数据库恢复超时，已中止");
                    }

                    inputWriter.join(5000);
                    outputReader.join(5000);
                    if (inputWriter.isAlive()) {
                        process.destroyForcibly();
                        logger.error("Timed out waiting for backup input writer thread to finish");
                        throw new BusinessException("数据库恢复失败，备份输入未完成");
                    }
                    int exitCode = process.exitValue();
                    if (exitCode != 0) {
                        logger.error("Database restore failed with exit code {}: {}", exitCode, output);
                        throw new BusinessException("数据库恢复失败，请检查日志");
                    }
                    if (inputError.get() != null) {
                        throw new BusinessException("数据库恢复失败，备份文件读取或输入异常");
                    }

                    logger.warn("Database restored from backup: {}. Application restart is recommended to refresh cached data.", record.getFilename());
                } finally {
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Database restore failed", e);
                throw new BusinessException("数据库恢复失败");
            }
        } finally {
            backupInProgress.set(false);
        }
    }

    @Override
    public void restoreFromBackupIncremental(Long id, String confirmDatabaseName) {
        String databaseName = extractDatabaseName();
        if (!databaseName.equals(confirmDatabaseName)) {
            throw new BusinessException("数据库名称确认不匹配，请确认后重新输入");
        }

        if (!backupInProgress.compareAndSet(false, true)) {
            throw new BusinessException("已有备份或恢复任务正在执行，请稍后再试");
        }

        try {
            BackupRecord record = getBackupById(id);
            if (!isSuccessfulStatus(record.getStatus())) {
                throw new BusinessException("仅可从成功的备份中恢复");
            }

            Path filePath = resolveValidatedBackupPath(record, true);

            DbConnectionInfo connInfo = parseJdbcUrl();
            String mysqlPath;
            int lastIdx = mysqldumpPath.lastIndexOf("mysqldump");
            if (lastIdx >= 0) {
                mysqlPath = mysqldumpPath.substring(0, lastIdx) + "mysql" + mysqldumpPath.substring(lastIdx + "mysqldump".length());
            } else {
                mysqlPath = "mysql";
            }

            try {
                // Convert backup SQL to incremental restore format (INSERT -> INSERT IGNORE INTO)
                Path convertedPath = convertToIncrementalRestore(filePath);
                ProcessBuilder pb = new ProcessBuilder(
                        mysqlPath,
                        "-h", connInfo.host,
                        "-P", String.valueOf(connInfo.port),
                        "-u", dbUsername,
                        databaseName
                );
                pb.environment().put("MYSQL_PWD", dbPassword);
                pb.redirectErrorStream(true);

                Process process = pb.start();
                try {
                    AtomicReference<IOException> inputError = new AtomicReference<>();
                    Thread inputWriter = new Thread(() -> {
                        try (OutputStream processInput = process.getOutputStream();
                             InputStream backupInput = openBackupInputStream(convertedPath)) {
                            backupInput.transferTo(processInput);
                        } catch (IOException e) {
                            inputError.set(e);
                            logger.error("Error streaming converted backup into mysql process", e);
                        }
                    });
                    inputWriter.setDaemon(true);
                    inputWriter.start();

                    StringBuilder output = new StringBuilder();
                    Thread outputReader = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                output.append(line).append("\n");
                            }
                        } catch (IOException e) {
                            logger.warn("Error reading mysql restore output", e);
                        }
                    });
                    outputReader.setDaemon(true);
                    outputReader.start();

                    boolean completed = process.waitFor(60, TimeUnit.MINUTES);
                    if (!completed) {
                        process.destroyForcibly();
                        inputWriter.join(5000);
                        outputReader.join(5000);
                        logger.error("Incremental restore timed out after 60 minutes");
                        throw new BusinessException("增量恢复超时，已中止");
                    }

                    inputWriter.join(5000);
                    outputReader.join(5000);
                    if (inputWriter.isAlive()) {
                        process.destroyForcibly();
                        logger.error("Timed out waiting for backup input writer thread to finish");
                        throw new BusinessException("增量恢复失败，备份输入未完成");
                    }
                    int exitCode = process.exitValue();
                    if (exitCode != 0) {
                        logger.error("Incremental restore failed with exit code {}: {}", exitCode, output);
                        throw new BusinessException("增量恢复失败，请检查日志");
                    }
                    if (inputError.get() != null) {
                        throw new BusinessException("增量恢复失败，备份文件读取或输入异常");
                    }

                    logger.info("Database incrementally restored from backup: {}. New data preserved.", record.getFilename());
                } finally {
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                    // Clean up temporary converted file
                    try {
                        Files.deleteIfExists(convertedPath);
                    } catch (IOException e) {
                        logger.warn("Failed to delete temporary converted backup file: {}", convertedPath, e);
                    }
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Incremental restore failed", e);
                throw new BusinessException("增量恢复失败");
            }
        } finally {
            backupInProgress.set(false);
        }
    }

    /**
     * Convert backup SQL to incremental restore format:
     * - Comment out DROP TABLE / TRUNCATE TABLE statements
     * - Convert INSERT INTO to INSERT IGNORE INTO
     * - Preserve existing data by skipping duplicate key conflicts
     */
    private Path convertToIncrementalRestore(Path originalPath) throws IOException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path convertedPath = tempDir.resolve("incremental_restore_" + System.currentTimeMillis() + ".sql");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openBackupInputStream(originalPath), StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(convertedPath, StandardCharsets.UTF_8)) {

            String line;
            while ((line = reader.readLine()) != null) {
                // Comment out DROP TABLE statements
                if (line.trim().toUpperCase().startsWith("DROP TABLE")) {
                    writer.write("-- [INCREMENTAL RESTORE] ");
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                // Comment out TRUNCATE TABLE statements
                if (line.trim().toUpperCase().startsWith("TRUNCATE TABLE")) {
                    writer.write("-- [INCREMENTAL RESTORE] ");
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                // Convert INSERT INTO to INSERT IGNORE INTO
                if (line.trim().toUpperCase().startsWith("INSERT INTO")) {
                    String converted = line.replaceFirst("(?i)INSERT INTO", "INSERT IGNORE INTO");
                    writer.write(converted);
                    writer.newLine();
                    continue;
                }

                // Write other lines as-is
                writer.write(line);
                writer.newLine();
            }
        }

        logger.info("Converted backup to incremental restore format: {} -> {}", originalPath, convertedPath);
        return convertedPath;
    }

    @Override
    public Map<String, Object> getBackupStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalCount = count();
        stats.put("totalCount", totalCount);

        QueryWrapper<BackupRecord> successWrapper = new QueryWrapper<>();
        successWrapper.in("status", SUCCESS_STATUSES);
        long successCount = count(successWrapper);
        stats.put("successCount", successCount);

        // Total size of successful backups
        QueryWrapper<BackupRecord> sizeWrapper = new QueryWrapper<>();
        sizeWrapper.in("status", SUCCESS_STATUSES);
        sizeWrapper.select("COALESCE(SUM(file_size), 0) as file_size");
        Map<String, Object> sizeResult = getMap(sizeWrapper);
        long totalSize = 0;
        if (sizeResult != null && sizeResult.get("file_size") != null) {
            Object sizeObj = sizeResult.get("file_size");
            if (sizeObj instanceof Number) {
                totalSize = ((Number) sizeObj).longValue();
            } else {
                try {
                    totalSize = Long.parseLong(sizeObj.toString());
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse backup total size: {}", sizeObj);
                }
            }
        }
        stats.put("totalSize", totalSize);

        // Last backup time
        QueryWrapper<BackupRecord> lastWrapper = new QueryWrapper<>();
        lastWrapper.in("status", SUCCESS_STATUSES).orderByDesc("created_time").last("LIMIT 1");
        BackupRecord lastBackup = getOne(lastWrapper, false);
        stats.put("lastBackupTime", lastBackup != null ? lastBackup.getCreatedTime() : null);

        int readableCount = 0;
        int missingFileCount = 0;
        QueryWrapper<BackupRecord> readableWrapper = new QueryWrapper<>();
        readableWrapper.in("status", SUCCESS_STATUSES);
        for (BackupRecord record : list(readableWrapper)) {
            BackupRecord enriched = enrichBackupRecord(record);
            if (Boolean.TRUE.equals(enriched.getReadable())) {
                readableCount++;
            } else if (Boolean.FALSE.equals(enriched.getFileAvailable())) {
                missingFileCount++;
            }
        }
        stats.put("readableCount", readableCount);
        stats.put("missingFileCount", missingFileCount);

        // Read from effective DB config (not @Value defaults) so frontend stays in sync after config changes
        BackupConfig effectiveConfig = getEffectiveConfig();
        stats.put("backupEnabled", Boolean.TRUE.equals(effectiveConfig.getAutoBackupEnabled()));
        stats.put("incrementalEnabled", Boolean.TRUE.equals(effectiveConfig.getIncrementalEnabled()));

        return stats;
    }

    @Override
    public Map<String, Object> getBackupConfig() {
        BackupConfig config = getEffectiveConfig();
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", config.getAutoBackupEnabled());
        result.put("autoBackupEnabled", config.getAutoBackupEnabled());
        result.put("backupCron", config.getBackupCron());
        result.put("backupHour", config.getBackupHour());
        result.put("backupMinute", config.getBackupMinute());
        result.put("retentionDays", config.getRetentionDays());
        result.put("maxCount", config.getMaxCount());
        result.put("incrementalEnabled", config.getIncrementalEnabled());
        result.put("fullBackupIntervalDays", config.getFullBackupIntervalDays());
        result.put("updatedBy", config.getUpdatedBy());
        result.put("updatedTime", config.getUpdatedTime());
        return result;
    }

    /**
     * Get effective backup config: DB config with fallback to @Value defaults.
     */
    private BackupConfig getEffectiveConfig() {
        try {
            return backupConfigService.getConfig();
        } catch (Exception e) {
            logger.warn("Failed to read backup config from DB, using @Value defaults", e);
            BackupConfig fallback = new BackupConfig();
            fallback.setAutoBackupEnabled(backupEnabled);
            fallback.setBackupCron("0 0 2 * * ?");
            fallback.setBackupHour(2);
            fallback.setBackupMinute(0);
            fallback.setRetentionDays(retentionDays);
            fallback.setMaxCount(maxCount);
            fallback.setIncrementalEnabled(incrementalEnabled);
            fallback.setFullBackupIntervalDays(fullBackupIntervalDays);
            return fallback;
        }
    }

    @Override
    public int cleanupOldBackups() {
        // Prevent cleanup during active backup/restore operations
        if (backupInProgress.get()) {
            logger.warn("Cleanup skipped: backup or restore is in progress");
            return 0;
        }

        BackupConfig effectiveConfig = getEffectiveConfig();
        int effectiveRetentionDays = effectiveConfig.getRetentionDays() != null ? effectiveConfig.getRetentionDays() : retentionDays;
        int effectiveMaxCount = effectiveConfig.getMaxCount() != null ? effectiveConfig.getMaxCount() : maxCount;

        int deletedCount = 0;

        // Protect the latest successful full backup (needed as incremental base)
        BackupRecord latestFull = getLastSuccessfulFullBackup();
        Long protectedId = latestFull != null ? latestFull.getId() : null;

        // Collect all backup IDs that are referenced as base by incremental backups
        QueryWrapper<BackupRecord> incrWrapper = new QueryWrapper<>();
        incrWrapper.isNotNull("base_backup_id");
        List<BackupRecord> incrementalBackups = list(incrWrapper);
        Set<Long> referencedBaseIds = new HashSet<>();
        if (incrementalBackups != null) {
            for (BackupRecord incr : incrementalBackups) {
                if (incr.getBaseBackupId() != null) {
                    referencedBaseIds.add(incr.getBaseBackupId());
                }
            }
        }

        // Delete by retention days
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(effectiveRetentionDays);
        QueryWrapper<BackupRecord> oldWrapper = new QueryWrapper<>();
        oldWrapper.lt("created_time", cutoffTime).ne("status", "IN_PROGRESS");
        List<BackupRecord> oldBackups = list(oldWrapper);
        for (BackupRecord record : oldBackups) {
            if (protectedId != null && protectedId.equals(record.getId())) {
                continue; // Never delete the latest full backup
            }
            if (referencedBaseIds.contains(record.getId())) {
                logger.info("Skipping cleanup of backup {} (referenced as base by incremental backups)", record.getFilename());
                continue; // Never delete a backup referenced as base by incremental backups
            }
            try {
                deleteBackup(record.getId());
                deletedCount++;
            } catch (Exception e) {
                logger.error("Failed to cleanup old backup: {}", record.getFilename(), e);
            }
        }

        // Delete excess backups (keep max-count most recent)
        long totalCount = count();
        if (totalCount > effectiveMaxCount) {
            QueryWrapper<BackupRecord> excessWrapper = new QueryWrapper<>();
            excessWrapper.ne("status", "IN_PROGRESS");
            excessWrapper.orderByDesc("created_time");
            excessWrapper.last("LIMIT " + (totalCount - effectiveMaxCount) + " OFFSET " + effectiveMaxCount);
            List<BackupRecord> excessBackups = list(excessWrapper);
            for (BackupRecord record : excessBackups) {
                if (protectedId != null && protectedId.equals(record.getId())) {
                    continue; // Never delete the latest full backup
                }
                if (referencedBaseIds.contains(record.getId())) {
                    logger.info("Skipping cleanup of backup {} (referenced as base by incremental backups)", record.getFilename());
                    continue; // Never delete a backup referenced as base by incremental backups
                }
                try {
                    deleteBackup(record.getId());
                    deletedCount++;
                } catch (Exception e) {
                    logger.error("Failed to cleanup excess backup: {}", record.getFilename(), e);
                }
            }
        }

        if (deletedCount > 0) {
            logger.info("Cleanup completed: {} backup(s) deleted", deletedCount);
        }
        return deletedCount;
    }

    // ========== Private helpers ==========

    private void enrichBackupPage(IPage<BackupRecord> backupPage) {
        if (backupPage == null || backupPage.getRecords() == null) {
            return;
        }
        for (BackupRecord record : backupPage.getRecords()) {
            enrichBackupRecord(record);
        }
    }

    private BackupRecord enrichBackupRecord(BackupRecord record) {
        if (record == null) {
            return null;
        }
        record.setStatus(normalizeStatus(record.getStatus()));
        if (isSuccessfulStatus(record.getStatus())) {
            try {
                Path filePath = resolveValidatedBackupPath(record, true);
                record.setFileAvailable(true);
                record.setReadable(Files.isReadable(filePath));
                record.setAvailabilityMessage(Boolean.TRUE.equals(record.getReadable()) ? "文件可读" : "文件不可读");
                if ((record.getFileSize() == null || record.getFileSize() == 0L) && Files.isRegularFile(filePath)) {
                    record.setFileSize(Files.size(filePath));
                }
            } catch (BusinessException e) {
                record.setFileAvailable(false);
                record.setReadable(false);
                record.setAvailabilityMessage(e.getMessage());
            } catch (IOException e) {
                record.setFileAvailable(false);
                record.setReadable(false);
                record.setAvailabilityMessage("读取备份文件属性失败");
            }
        } else {
            record.setFileAvailable(false);
            record.setReadable(false);
            if ("IN_PROGRESS".equals(record.getStatus())) {
                record.setAvailabilityMessage("备份执行中");
            } else if ("FAILED".equals(record.getStatus())) {
                record.setAvailabilityMessage(defaultString(record.getErrorMessage(), "备份执行失败"));
            } else {
                record.setAvailabilityMessage("当前状态不可读取");
            }
        }
        return record;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "UNKNOWN";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(normalized)) {
            return "SUCCESS";
        }
        if ("RUNNING".equals(normalized)) {
            return "IN_PROGRESS";
        }
        return normalized;
    }

    private boolean isSuccessfulStatus(String status) {
        return "SUCCESS".equals(normalizeStatus(status));
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String normalizeImportMode(String mode, String filename) {
        String normalized = defaultString(mode, "AUTO").trim().toUpperCase(Locale.ROOT);
        if ("FULL".equals(normalized) || "INCREMENTAL".equals(normalized)) {
            return normalized;
        }
        String lowerName = defaultString(filename, "").toLowerCase(Locale.ROOT);
        if (lowerName.contains("_incr") || lowerName.contains("incremental")) {
            return "INCREMENTAL";
        }
        return "FULL";
    }

    private String normalizeImportCategory(String category, String filename) {
        String normalized = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty() && CATEGORY_TABLES.containsKey(normalized)) {
            return normalized;
        }
        String lowerName = defaultString(filename, "").toLowerCase(Locale.ROOT);
        for (String candidate : CATEGORY_TABLES.keySet()) {
            if (lowerName.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private String buildImportedFilename(String originalFilename) {
        String sanitized = defaultString(originalFilename, "backup.sql")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
        return "imported_" + timestamp + "_" + sanitized;
    }

    private Path resolveImportTargetPath(String filename) {
        Path allowedDir = Paths.get(backupDir).toAbsolutePath().normalize();
        Path targetPath = allowedDir.resolve(filename).normalize();
        if (!targetPath.startsWith(allowedDir)) {
            throw new BusinessException("导入备份文件路径无效");
        }
        return targetPath;
    }

    private Path resolveValidatedBackupPath(BackupRecord record, boolean requireExists) {
        if (record == null || record.getStoragePath() == null || record.getStoragePath().trim().isEmpty()) {
            throw new BusinessException("备份文件路径为空");
        }

        Path filePath = Paths.get(record.getStoragePath()).toAbsolutePath().normalize();
        Path allowedDir = Paths.get(backupDir).toAbsolutePath().normalize();

        if (!filePath.startsWith(allowedDir)) {
            logger.error("Path traversal attempt detected: {} is outside {}", filePath, allowedDir);
            throw new BusinessException("备份文件路径无效");
        }

        if (requireExists && !Files.exists(filePath)) {
            throw new BusinessException("备份文件不存在，可能已被手动删除");
        }
        if (Files.exists(filePath) && !Files.isRegularFile(filePath)) {
            throw new BusinessException("备份文件类型无效");
        }

        return filePath;
    }

    private BufferedReader openPreviewReader(Path filePath) throws IOException {
        InputStream inputStream = Files.newInputStream(filePath);
        if (filePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            inputStream = new GZIPInputStream(inputStream);
        }
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private InputStream openBackupInputStream(Path filePath) throws IOException {
        InputStream inputStream = Files.newInputStream(filePath);
        if (filePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            inputStream = new GZIPInputStream(inputStream);
        }
        return inputStream;
    }

    private BackupRecord createBackupRecord(String type, String mode, String createdBy) {
        String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
        String databaseName = extractDatabaseName();
        String modePrefix = "INCREMENTAL".equals(mode) ? "_incr" : "";
        String filename = databaseName + modePrefix + "_" + timestamp + ".sql";

        BackupRecord record = new BackupRecord();
        record.setFilename(filename);
        record.setFileSize(0L);
        record.setBackupType(type);
        record.setBackupMode(mode);
        record.setStatus("IN_PROGRESS");
        record.setStoragePath(Paths.get(backupDir, filename).toString());
        record.setCreatedBy(createdBy);
        record.setDatabaseSize(0L);
        record.setCreatedTime(LocalDateTime.now());

        // For incremental, set base backup reference (chain-based: most recent backup)
        if ("INCREMENTAL".equals(mode)) {
            BackupRecord lastBackup = getLastSuccessfulBackup();
            if (lastBackup != null) {
                record.setBaseBackupId(lastBackup.getId());
            }
        }

        return record;
    }

    /**
     * Determine whether to run a FULL or INCREMENTAL backup for the scheduled run.
     * - If incremental is disabled → FULL
     * - If no successful FULL backup exists → FULL
     * - If last FULL backup was >= fullBackupIntervalDays ago → FULL
     * - Otherwise → INCREMENTAL
     */
    private String determineScheduledBackupMode() {
        BackupConfig effectiveConfig = getEffectiveConfig();
        boolean effectiveIncrementalEnabled = Boolean.TRUE.equals(effectiveConfig.getIncrementalEnabled());
        int effectiveFullInterval = effectiveConfig.getFullBackupIntervalDays() != null
                ? effectiveConfig.getFullBackupIntervalDays() : fullBackupIntervalDays;

        if (!effectiveIncrementalEnabled) {
            return "FULL";
        }

        BackupRecord lastFull = getLastSuccessfulFullBackup();
        if (lastFull == null) {
            return "FULL";
        }

        long daysSinceLastFull = ChronoUnit.DAYS.between(lastFull.getCreatedTime(), LocalDateTime.now());
        if (daysSinceLastFull >= effectiveFullInterval) {
            return "FULL";
        }

        return "INCREMENTAL";
    }

    /**
     * Find the most recent successful FULL backup.
     */
    private BackupRecord getLastSuccessfulFullBackup() {
        QueryWrapper<BackupRecord> wrapper = new QueryWrapper<>();
        wrapper.in("status", SUCCESS_STATUSES)
               .eq("backup_mode", "FULL")
               .orderByDesc("created_time")
               .last("LIMIT 1");
        return getOne(wrapper, false);
    }

    /**
     * Find the most recent successful backup (FULL or INCREMENTAL).
     * Used for chain-based incremental backups.
     */
    private BackupRecord getLastSuccessfulBackup() {
        QueryWrapper<BackupRecord> wrapper = new QueryWrapper<>();
        wrapper.in("status", SUCCESS_STATUSES)
               .orderByDesc("created_time")
               .last("LIMIT 1");
        return getOne(wrapper, false);
    }

    /**
     * Execute a full database backup using mysqldump.
     */
    private void executeFullBackup(BackupRecord record) {
        // Defense-in-depth: validate output path
        Path outputPath = Paths.get(record.getStoragePath()).toAbsolutePath().normalize();
        Path allowedDir = Paths.get(backupDir).toAbsolutePath().normalize();
        if (!outputPath.startsWith(allowedDir)) {
            logger.error("Path traversal detected in backup output: {}", record.getFilename());
            record.setStatus("FAILED");
            record.setErrorMessage("备份输出路径异常");
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
            return;
        }

        try {
            DbConnectionInfo connInfo = parseJdbcUrl();
            String databaseName = extractDatabaseName();

            ProcessBuilder pb = new ProcessBuilder(
                    mysqldumpPath,
                    "-h", connInfo.host,
                    "-P", String.valueOf(connInfo.port),
                    "-u", dbUsername,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    "--result-file=" + record.getStoragePath(),
                    databaseName
            );
            pb.environment().put("MYSQL_PWD", dbPassword);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try {
                // Read stdout in daemon thread to prevent readLine blocking timeout
                StringBuilder output = new StringBuilder();
                Thread outputReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        logger.warn("Error reading mysqldump output", e);
                    }
                });
                outputReader.setDaemon(true);
                outputReader.start();

                boolean completed = process.waitFor(30, TimeUnit.MINUTES);
                if (!completed) {
                    process.destroyForcibly();
                    outputReader.join(5000);
                    logger.error("mysqldump timed out after 30 minutes");
                    record.setStatus("FAILED");
                    record.setErrorMessage("备份执行超时");
                } else {
                    outputReader.join(5000);
                    int exitCode = process.exitValue();
                    if (exitCode != 0) {
                        logger.error("mysqldump failed with exit code {}: {}", exitCode, output.toString().trim());
                        record.setStatus("FAILED");
                        record.setErrorMessage("备份执行失败");
                    } else {
                        File backupFile = new File(record.getStoragePath());
                        if (!backupFile.exists() || backupFile.length() == 0) {
                            logger.error("mysqldump exited successfully but backup file is missing or empty: {}", record.getStoragePath());
                            record.setStatus("FAILED");
                            record.setErrorMessage("备份文件生成异常（文件为空或不存在）");
                        } else {
                            record.setStatus("SUCCESS");
                            record.setFileSize(backupFile.length());
                            logger.info("Full backup completed: {} ({} bytes)", record.getFilename(), backupFile.length());
                        }
                    }
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception e) {
            logger.error("Full backup execution failed", e);
            record.setStatus("FAILED");
            record.setErrorMessage("备份执行异常");
        }

        // Clean up residual file on failure
        if ("FAILED".equals(record.getStatus())) {
            try {
                Path failedFile = Paths.get(record.getStoragePath()).toAbsolutePath().normalize();
                if (Files.exists(failedFile) && failedFile.startsWith(Paths.get(backupDir).toAbsolutePath().normalize())) {
                    Files.delete(failedFile);
                    logger.info("Cleaned up residual backup file after failure: {}", record.getFilename());
                }
            } catch (IOException cleanupEx) {
                logger.warn("Failed to clean up residual backup file: {}", record.getFilename(), cleanupEx);
            }
        }

        record.setCompletedTime(LocalDateTime.now());
        updateById(record);
    }

    /**
     * Execute an incremental backup.
     * Dumps only data that has changed since the last successful full backup,
     * using REPLACE INTO to allow applying on top of a full backup restore.
     *
     * Strategy:
     * 1. Find tables with created_time/updated_time columns → dump with --where filter
     * 2. Tables without timestamp columns → dump fully (data-only, --replace)
     * 3. All dumps use --no-create-info (schema is in the full backup)
     */
    private void executeIncrementalBackup(BackupRecord record) {
        // Defense-in-depth: validate output path
        Path outputPath = Paths.get(record.getStoragePath()).toAbsolutePath().normalize();
        Path allowedDir = Paths.get(backupDir).toAbsolutePath().normalize();
        if (!outputPath.startsWith(allowedDir)) {
            logger.error("Path traversal detected in incremental backup output: {}", record.getFilename());
            record.setStatus("FAILED");
            record.setErrorMessage("备份输出路径异常");
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
            return;
        }

        // Chain-based incremental: base on most recent backup (FULL or INCREMENTAL)
        BackupRecord lastBackup = getLastSuccessfulBackup();

        if (lastBackup == null) {
            // Fallback to full backup if no full backup exists
            logger.warn("No full backup found, falling back to full backup");
            record.setBackupMode("FULL");
            executeFullBackup(record);
            return;
        }

        String sinceStr = lastBackup.getCreatedTime().format(SQL_DATETIME_FORMATTER);
        // Defense-in-depth: validate datetime format before using in --where clause
        if (!sinceStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            logger.error("Invalid sinceStr format: {}", sinceStr);
            record.setStatus("FAILED");
            record.setErrorMessage("增量备份时间基准格式异常");
            record.setCompletedTime(LocalDateTime.now());
            updateById(record);
            return;
        }
        record.setBaseBackupId(lastBackup.getId());

        try {
            DbConnectionInfo connInfo = parseJdbcUrl();
            String databaseName = extractDatabaseName();

            // Classify tables by their timestamp columns
            Map<String, Set<String>> tableTimestampCols = getTableTimestampColumns(databaseName);
            List<String> allTables = getAllTableNames(databaseName);

            if (allTables.isEmpty()) {
                record.setStatus("FAILED");
                record.setErrorMessage("未找到数据库表");
                record.setCompletedTime(LocalDateTime.now());
                updateById(record);
                return;
            }

            // Group tables by timestamp column availability
            List<String> tablesWithBoth = new ArrayList<>();
            List<String> tablesWithCreatedOnly = new ArrayList<>();
            List<String> tablesWithUpdatedOnly = new ArrayList<>();
            List<String> tablesWithoutTimestamp = new ArrayList<>();

            for (String table : allTables) {
                Set<String> cols = tableTimestampCols.getOrDefault(table, Collections.emptySet());
                boolean hasCreated = cols.contains("created_time");
                boolean hasUpdated = cols.contains("updated_time");

                if (hasCreated && hasUpdated) {
                    tablesWithBoth.add(table);
                } else if (hasCreated) {
                    tablesWithCreatedOnly.add(table);
                } else if (hasUpdated) {
                    tablesWithUpdatedOnly.add(table);
                } else {
                    tablesWithoutTimestamp.add(table);
                }
            }

            // Write incremental backup header
            File outputFile = new File(record.getStoragePath());
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                String header = "-- Incremental backup (chain-based)\n" +
                        "-- Since: " + sinceStr + "\n" +
                        "-- Base backup: " + lastBackup.getFilename() + " (ID: " + lastBackup.getId() + ", Mode: " + lastBackup.getBackupMode() + ")\n" +
                        "-- Database: " + databaseName + "\n" +
                        "-- Generated: " + LocalDateTime.now().format(SQL_DATETIME_FORMATTER) + "\n" +
                        "-- Note: Restore requires full chain - first restore the latest FULL backup, then all INCREMENTALs in order\n\n" +
                        "SET FOREIGN_KEY_CHECKS = 0;\n\n";
                fos.write(header.getBytes(StandardCharsets.UTF_8));

                boolean success = true;

                // Dump tables with both timestamp columns
                if (!tablesWithBoth.isEmpty()) {
                    String where = "created_time >= '" + sinceStr + "' OR updated_time >= '" + sinceStr + "'";
                    if (!dumpTablesIncremental(fos, connInfo, databaseName, tablesWithBoth, where)) {
                        success = false;
                    }
                }

                // Dump tables with only created_time
                if (success && !tablesWithCreatedOnly.isEmpty()) {
                    String where = "created_time >= '" + sinceStr + "'";
                    if (!dumpTablesIncremental(fos, connInfo, databaseName, tablesWithCreatedOnly, where)) {
                        success = false;
                    }
                }

                // Dump tables with only updated_time
                if (success && !tablesWithUpdatedOnly.isEmpty()) {
                    String where = "updated_time >= '" + sinceStr + "'";
                    if (!dumpTablesIncremental(fos, connInfo, databaseName, tablesWithUpdatedOnly, where)) {
                        success = false;
                    }
                }

                // Dump tables without timestamp columns (full data, small reference tables)
                if (success && !tablesWithoutTimestamp.isEmpty()) {
                    if (!dumpTablesIncremental(fos, connInfo, databaseName, tablesWithoutTimestamp, null)) {
                        success = false;
                    }
                }

                if (success) {
                    // Write footer only on success
                    String footer = "\nSET FOREIGN_KEY_CHECKS = 1;\n";
                    fos.write(footer.getBytes(StandardCharsets.UTF_8));

                    if (!outputFile.exists() || outputFile.length() == 0) {
                        logger.error("Incremental backup produced empty file: {}", record.getStoragePath());
                        record.setStatus("FAILED");
                        record.setErrorMessage("增量备份文件生成异常（文件为空或不存在）");
                    } else {
                        record.setStatus("SUCCESS");
                        record.setFileSize(outputFile.length());
                        logger.info("Incremental backup completed: {} ({} bytes), tables: {} with-ts + {} without-ts",
                                record.getFilename(), outputFile.length(),
                                tablesWithBoth.size() + tablesWithCreatedOnly.size() + tablesWithUpdatedOnly.size(),
                                tablesWithoutTimestamp.size());
                    }
                } else {
                    record.setStatus("FAILED");
                    record.setErrorMessage("增量备份执行失败");
                }
            }
        } catch (Exception e) {
            logger.error("Incremental backup execution failed", e);
            record.setStatus("FAILED");
            record.setErrorMessage("增量备份执行异常");
        }

        // Clean up residual file on failure
        if ("FAILED".equals(record.getStatus())) {
            try {
                Path failedFile = Paths.get(record.getStoragePath()).toAbsolutePath().normalize();
                if (Files.exists(failedFile) && failedFile.startsWith(Paths.get(backupDir).toAbsolutePath().normalize())) {
                    Files.delete(failedFile);
                    logger.info("Cleaned up residual incremental backup file after failure: {}", record.getFilename());
                }
            } catch (IOException cleanupEx) {
                logger.warn("Failed to clean up residual backup file: {}", record.getFilename(), cleanupEx);
            }
        }

        record.setCompletedTime(LocalDateTime.now());
        updateById(record);
    }

    /**
     * Dump specific tables using mysqldump with --no-create-info and --replace.
     * Output is appended to the provided FileOutputStream.
     *
     * @param fos         Output stream to append to
     * @param connInfo    Database connection info
     * @param database    Database name
     * @param tables      Tables to dump
     * @param whereClause Optional WHERE clause (null for full data dump)
     * @return true if successful, false otherwise
     */
    private boolean dumpTablesIncremental(FileOutputStream fos, DbConnectionInfo connInfo,
                                          String database, List<String> tables, String whereClause) {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                mysqldumpPath,
                "-h", connInfo.host,
                "-P", String.valueOf(connInfo.port),
                "-u", dbUsername,
                "--single-transaction",
                "--no-create-info",
                "--replace",
                "--skip-triggers"
        ));
        if (whereClause != null) {
            cmd.add("--where=" + whereClause);
        }
        cmd.add(database);
        cmd.addAll(tables);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("MYSQL_PWD", dbPassword);
            pb.redirectErrorStream(false);

            Process process = pb.start();
            try {
                // Read stderr in a separate daemon thread to prevent deadlock
                StringBuilder errorOutput = new StringBuilder();
                Thread errorReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorOutput.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        logger.warn("Error reading mysqldump stderr", e);
                    }
                });
                errorReader.setDaemon(true);
                errorReader.start();

                // Read stdout in a separate daemon thread to allow timeout to be reachable
                Thread outputCopier = new Thread(() -> {
                    try (InputStream is = process.getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    } catch (IOException e) {
                        logger.warn("Error reading incremental mysqldump output", e);
                    }
                });
                outputCopier.setDaemon(true);
                outputCopier.start();

                boolean completed = process.waitFor(30, TimeUnit.MINUTES);
                if (!completed) {
                    process.destroyForcibly();
                    outputCopier.join(5000);
                    errorReader.join(5000);
                    logger.error("Incremental mysqldump timed out for tables: {}", tables);
                    return false;
                }

                outputCopier.join(5000); // Wait for stdout copier to finish writing
                errorReader.join(5000); // Wait for error reader to finish

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    logger.error("Incremental mysqldump failed (exit {}): {}", exitCode, errorOutput.toString().trim());
                    return false;
                }

                return true;
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception e) {
            logger.error("Incremental dump failed for tables: {}", tables, e);
            return false;
        }
    }

    /**
     * Query information_schema to find tables and their timestamp columns.
     * Returns a map of table_name → set of timestamp column names (created_time, updated_time).
     */
    private Map<String, Set<String>> getTableTimestampColumns(String databaseName) {
        Map<String, Set<String>> result = new HashMap<>();
        String sql = "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND COLUMN_NAME IN ('created_time', 'updated_time')";

        try (Connection conn = masterDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, databaseName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    result.computeIfAbsent(tableName, k -> new HashSet<>()).add(columnName);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to query table timestamp columns", e);
            throw new BusinessException("查询数据库表结构失败");
        }

        return result;
    }

    /**
     * Get all table names in the specified database.
     */
    private List<String> getAllTableNames(String databaseName) {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'";

        try (Connection conn = masterDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, databaseName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (SAFE_IDENTIFIER.matcher(tableName).matches()) {
                        tables.add(tableName);
                    } else {
                        logger.warn("Skipping table with unsafe name: {}", tableName);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to query table names", e);
            throw new BusinessException("查询数据库表列表失败");
        }

        return tables;
    }

    private String extractDatabaseName() {
        Pattern pattern = Pattern.compile("jdbc:mysql://[^/]+/([^?]+)");
        Matcher matcher = pattern.matcher(jdbcUrl);
        if (matcher.find()) {
            String name = matcher.group(1);
            if (!SAFE_IDENTIFIER.matcher(name).matches()) {
                throw new BusinessException("数据库名称格式异常");
            }
            return name;
        }
        throw new BusinessException("无法从JDBC URL中解析数据库名称");
    }

    private DbConnectionInfo parseJdbcUrl() {
        Pattern pattern = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/");
        Matcher matcher = pattern.matcher(jdbcUrl);
        if (matcher.find()) {
            String host = matcher.group(1);
            int port = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 3306;
            return new DbConnectionInfo(host, port);
        }
        throw new BusinessException("无法从JDBC URL中解析数据库连接信息");
    }

    private static class DbConnectionInfo {
        final String host;
        final int port;

        DbConnectionInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static String formatFileSizeInternal(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int idx = 0;
        double size = bytes;
        while (size >= 1024 && idx < units.length - 1) {
            size /= 1024;
            idx++;
        }
        return idx == 0 ? String.format("%.0f %s", size, units[idx])
                        : String.format("%.1f %s", size, units[idx]);
    }
}
