package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.datasource.UseMaster;
import com.english.entity.UpdateConfig;
import com.english.exception.BusinessException;
import com.english.mapper.UpdateConfigMapper;
import com.english.service.UpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateServiceImpl implements UpdateService {

    private static final Pattern GIT_BRANCH_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]{1,100}$");

    private final UpdateConfigMapper updateConfigMapper;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Value("${app.update.script:scripts/auto-update.sh}")
    private String updateScript;

    @Value("${app.update.supported:true}")
    private boolean updateSupported;

    @Value("${app.update.unsupported-reason:Self-update is not supported in the current deployment mode.}")
    private String unsupportedReason;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "update-worker");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Update executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @UseMaster
    public UpdateConfig getConfig() {
        UpdateConfig config = updateConfigMapper.selectOne(
                new LambdaQueryWrapper<UpdateConfig>()
                        .orderByAsc(UpdateConfig::getId)
                        .last("LIMIT 1"));
        return config != null ? config : createDefaultConfig();
    }

    @Override
    @UseMaster
    public UpdateConfig saveConfig(UpdateConfig config) {
        ensureSupported();
        if (config == null) {
            throw new BusinessException("Update config is required.");
        }

        String cronExpr = normalizeToNull(config.getCronExpr());
        String gitBranch = normalizeToNull(config.getGitBranch());

        if (Boolean.TRUE.equals(config.getEnabled()) && cronExpr == null) {
            throw new BusinessException("Cron expression is required when scheduled updates are enabled.");
        }

        if (gitBranch != null && !GIT_BRANCH_PATTERN.matcher(gitBranch).matches()) {
            throw new BusinessException("Invalid git branch name.");
        }

        if (cronExpr != null) {
            try {
                new CronTrigger(cronExpr);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid cron expression: " + cronExpr);
            }
        }

        UpdateConfig existing = getConfig();
        boolean hasPersistedRecord = existing.getId() != null;

        if (cronExpr != null) {
            existing.setCronExpr(cronExpr);
        }
        if (config.getEnabled() != null) {
            existing.setEnabled(config.getEnabled());
        }
        if (gitBranch != null) {
            existing.setGitBranch(gitBranch);
        }
        if (config.getBackupBeforeUpdate() != null) {
            existing.setBackupBeforeUpdate(config.getBackupBeforeUpdate());
        }

        if (normalizeToNull(existing.getCronExpr()) == null) {
            existing.setCronExpr(createDefaultConfig().getCronExpr());
        }
        if (normalizeToNull(existing.getGitBranch()) == null) {
            existing.setGitBranch(createDefaultConfig().getGitBranch());
        }
        if (existing.getEnabled() == null) {
            existing.setEnabled(false);
        }
        if (existing.getBackupBeforeUpdate() == null) {
            existing.setBackupBeforeUpdate(true);
        }
        if (normalizeToNull(existing.getLastRunStatus()) == null) {
            existing.setLastRunStatus("NEVER");
        }

        if (hasPersistedRecord) {
            updateConfigMapper.updateById(existing);
        } else {
            updateConfigMapper.insert(existing);
        }
        return existing;
    }

    @Override
    @UseMaster
    public SseEmitter triggerUpdate(SseEmitter emitter) {
        ensureSupported();

        if (!isRunning.compareAndSet(false, true)) {
            throw new BusinessException("An update is already running. Please wait for it to finish.");
        }

        UpdateConfig config = getConfig();
        emitter.onCompletion(() -> log.debug("Self-update SSE connection completed"));
        emitter.onTimeout(() -> log.warn("Self-update SSE connection timed out"));

        try {
            executor.submit(() -> runInteractiveUpdate(config, emitter));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            isRunning.set(false);
            log.error("Update task submission rejected", e);
            throw new BusinessException("Update service is shutting down. Please try again later.");
        }
        return emitter;
    }

    @Override
    @UseMaster
    public void runUpdate() {
        if (!isSupported()) {
            log.warn("Skipping self-update: {}", getUnsupportedReason());
            return;
        }

        UpdateConfig config = getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Skipping scheduled self-update because another update is running");
            return;
        }

        setRunning(config.getId());
        boolean success = false;
        try {
            List<String> command = buildCommand(config);
            log.info("Scheduled self-update command: {}", command);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[auto-update] {}", line);
                }
            }

            success = process.waitFor() == 0;
            log.info("Scheduled self-update finished, success={}", success);
        } catch (Exception e) {
            log.error("Scheduled self-update failed", e);
        } finally {
            finishUpdate(config.getId(), success);
            isRunning.set(false);
        }
    }

    @Override
    public boolean isSupported() {
        return resolveUnsupportedReason() == null;
    }

    @Override
    public String getUnsupportedReason() {
        String reason = resolveUnsupportedReason();
        return reason == null ? "" : reason;
    }

    private void runInteractiveUpdate(UpdateConfig config, SseEmitter emitter) {
        setRunning(config.getId());
        boolean success = false;
        Process process = null;
        try {
            List<String> command = buildCommand(config);
            log.info("Interactive self-update command: {}", command);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();

            boolean clientDisconnected = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        emitter.send(SseEmitter.event().data(line));
                    } catch (Exception ignored) {
                        clientDisconnected = true;
                        if (process != null) {
                            process.destroyForcibly();
                        }
                        break;
                    }
                }
            }

            if (clientDisconnected) {
                waitForProcessExit(process);
            } else {
                int exitCode = process.waitFor();
                success = exitCode == 0;
                emitter.send(SseEmitter.event().name("done")
                        .data(success ? "UPDATE_SUCCESS" : "UPDATE_FAILED:" + exitCode));
                emitter.complete();
            }
        } catch (Exception e) {
            log.error("Interactive self-update failed", e);
            try {
                emitter.send(SseEmitter.event().name("done")
                        .data("UPDATE_ERROR:Update execution failed. Check server logs for details."));
                emitter.complete();
            } catch (Exception ignored) {
                // Ignore secondary SSE failures.
            }
        } finally {
            finishUpdate(config.getId(), success);
            isRunning.set(false);
        }
    }

    private void waitForProcessExit(Process process) throws InterruptedException {
        if (process != null && !process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            log.warn("Update process did not exit within 30 seconds after client disconnect");
        }
    }

    private List<String> buildCommand(UpdateConfig config) {
        Path scriptPath = resolveScriptPath();
        if (!Files.isRegularFile(scriptPath)) {
            throw new BusinessException("Self-update script not found: " + scriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(scriptPath.normalize().toString());
        command.add("--branch");
        command.add(defaultBranch(config));
        if (Boolean.TRUE.equals(config.getBackupBeforeUpdate())) {
            command.add("--backup-before-update");
        } else {
            command.add("--no-backup");
        }
        return command;
    }

    private void setRunning(Long id) {
        if (id == null) {
            return;
        }
        UpdateConfig update = new UpdateConfig();
        update.setId(id);
        update.setLastRunAt(LocalDateTime.now());
        update.setLastRunStatus("RUNNING");
        updateConfigMapper.updateById(update);
    }

    private void finishUpdate(Long id, boolean success) {
        if (id == null) {
            return;
        }
        UpdateConfig update = new UpdateConfig();
        update.setId(id);
        update.setLastRunAt(LocalDateTime.now());
        update.setLastRunStatus(success ? "SUCCESS" : "FAILED");
        updateConfigMapper.updateById(update);
    }

    private UpdateConfig createDefaultConfig() {
        UpdateConfig config = new UpdateConfig();
        config.setCronExpr("0 0 3 * * ?");
        config.setEnabled(false);
        config.setGitBranch("master");
        config.setBackupBeforeUpdate(true);
        config.setLastRunStatus("NEVER");
        return config;
    }

    private void ensureSupported() {
        String reason = resolveUnsupportedReason();
        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultBranch(UpdateConfig config) {
        String branch = config == null ? null : normalizeToNull(config.getGitBranch());
        return branch != null ? branch : createDefaultConfig().getGitBranch();
    }

    private String resolveUnsupportedReason() {
        if (!updateSupported) {
            return unsupportedReason;
        }
        if (isWindowsHost()) {
            return "Self-update is only supported on Linux deployments that can run the bundled bash script.";
        }

        Path scriptPath = resolveScriptPath();
        if (!Files.isRegularFile(scriptPath)) {
            return "Self-update script not found: " + scriptPath;
        }

        return null;
    }

    private Path resolveScriptPath() {
        Path scriptPath = Paths.get(updateScript);
        if (!scriptPath.isAbsolute()) {
            scriptPath = Paths.get(System.getProperty("user.dir")).resolve(scriptPath);
        }
        return scriptPath.normalize();
    }

    private boolean isWindowsHost() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
