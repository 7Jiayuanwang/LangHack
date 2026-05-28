package com.english.controller;

import com.english.config.DynamicUpdateScheduler;
import com.english.dto.Result;
import com.english.entity.UpdateConfig;
import com.english.exception.BusinessException;
import com.english.service.UpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ops/update")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class UpdateController {

    private final UpdateService updateService;
    private final DynamicUpdateScheduler dynamicUpdateScheduler;

    /** 部署历史日志文件路径（与 auto-update.sh 中 OPS_LOG_FILE 对应） */
    @Value("${deploy.history.log:${user.home}/LangHack/logs/deploy-history.log}")
    private String deployHistoryLogPath;

    @GetMapping("/config")
    public Result<UpdateConfig> getConfig() {
        try {
            return Result.success(updateService.getConfig());
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get update config", e);
            return Result.error("Failed to get update config. Please try again later.");
        }
    }

    @PostMapping("/config")
    public Result<UpdateConfig> saveConfig(@RequestBody UpdateConfig config) {
        try {
            UpdateConfig saved = updateService.saveConfig(config);
            dynamicUpdateScheduler.reschedule(saved);
            return Result.success(saved);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save update config", e);
            return Result.error("Failed to save update config. Please try again later.");
        }
    }

    @GetMapping(value = "/trigger", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter triggerUpdate() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitter.onTimeout(() -> log.warn("Self-update SSE connection timed out"));
        emitter.onCompletion(() -> log.debug("Self-update SSE connection completed"));
        try {
            return updateService.triggerUpdate(emitter);
        } catch (BusinessException e) {
            try {
                emitter.send(SseEmitter.event().name("done").data("UPDATE_ERROR:" + e.getMessage()));
                emitter.complete();
            } catch (Exception ignored) {
                // Ignore SSE secondary failures.
            }
            return emitter;
        } catch (Exception e) {
            log.error("Failed to trigger update", e);
            try {
                emitter.send(SseEmitter.event().name("done")
                        .data("UPDATE_ERROR:Failed to trigger update. Please try again later."));
                emitter.complete();
            } catch (Exception ignored) {
                // Ignore SSE secondary failures.
            }
            return emitter;
        }
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        try {
            UpdateConfig config = updateService.getConfig();
            Map<String, Object> data = new HashMap<>();
            data.put("lastRunAt", config.getLastRunAt() != null ? config.getLastRunAt().toString() : "");
            data.put("lastRunStatus", config.getLastRunStatus() != null ? config.getLastRunStatus() : "NEVER");
            data.put("enabled", config.getEnabled());
            data.put("cronExpr", config.getCronExpr());
            data.put("gitBranch", config.getGitBranch());
            data.put("supported", updateService.isSupported());
            data.put("unsupportedReason", updateService.getUnsupportedReason());
            data.put("hotDeployEnabled", config.getHotDeployEnabled());
            data.put("hotDeployCronExpr", config.getHotDeployCronExpr());
            data.put("hotDeployLastRunAt", config.getHotDeployLastRunAt() != null ? config.getHotDeployLastRunAt().toString() : "");
            data.put("hotDeployLastRunStatus", config.getHotDeployLastRunStatus() != null ? config.getHotDeployLastRunStatus() : "NEVER");
            return Result.success(data);
        } catch (Exception e) {
            log.error("Failed to get update status", e);
            return Result.error("Failed to get update status. Please try again later.");
        }
    }

    @GetMapping(value = "/hot-deploy/trigger", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter triggerHotDeploy(
            @RequestParam(required = false, defaultValue = "") String branch,
            @RequestParam(required = false, defaultValue = "false") boolean skipBuild,
            @RequestParam(required = false, defaultValue = "false") boolean dryRun) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1h timeout for blue/green
        emitter.onTimeout(() -> log.warn("Hot deploy SSE connection timed out"));
        emitter.onCompletion(() -> log.debug("Hot deploy SSE connection completed"));
        try {
            return updateService.triggerHotDeploy(emitter, branch, skipBuild, dryRun);
        } catch (com.english.exception.BusinessException e) {
            try {
                emitter.send(SseEmitter.event().name("done").data("HOT_DEPLOY_ERROR:" + e.getMessage()));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        } catch (Exception e) {
            log.error("Failed to trigger hot deploy", e);
            try {
                emitter.send(SseEmitter.event().name("done").data("HOT_DEPLOY_ERROR:触发热部署失败，请查看服务器日志。"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }
    }

    /**
     * 获取部署历史记录（读取 auto-update.sh 写入的结构化日志）
     * 日志格式：时间戳|事件类型|commit|描述
     */
    @GetMapping("/deploy-history")
    public Result<List<Map<String, String>>> getDeployHistory(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            Path logPath = Paths.get(deployHistoryLogPath);
            if (!Files.exists(logPath)) {
                return Result.success(Collections.emptyList());
            }
            List<String> lines = Files.readAllLines(logPath);
            int size = lines.size();
            int from = Math.max(0, size - Math.min(limit, 200));
            List<Map<String, String>> records = new ArrayList<>();
            for (int i = size - 1; i >= from; i--) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", 4);
                Map<String, String> record = new HashMap<>();
                record.put("time",   parts.length > 0 ? parts[0] : "");
                record.put("event",  parts.length > 1 ? parts[1] : "");
                record.put("commit", parts.length > 2 ? parts[2] : "");
                record.put("desc",   parts.length > 3 ? parts[3] : "");
                records.add(record);
            }
            return Result.success(records);
        } catch (IOException e) {
            log.error("Failed to read deploy history log", e);
            return Result.error("读取部署历史失败");
        }
    }
}
