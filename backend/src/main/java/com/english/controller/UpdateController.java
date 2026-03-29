package com.english.controller;

import com.english.config.DynamicUpdateScheduler;
import com.english.dto.Result;
import com.english.entity.UpdateConfig;
import com.english.exception.BusinessException;
import com.english.service.UpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ops/update")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class UpdateController {

    private final UpdateService updateService;
    private final DynamicUpdateScheduler dynamicUpdateScheduler;

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
            return Result.success(data);
        } catch (Exception e) {
            log.error("Failed to get update status", e);
            return Result.error("Failed to get update status. Please try again later.");
        }
    }
}
