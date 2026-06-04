package com.english.controller;

import com.english.dto.HotDeployRequest;
import com.english.dto.Result;
import com.english.service.HotDeployService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ops/hot-deploy")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class HotDeployController {

    private final HotDeployService hotDeployService;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        try {
            return Result.success(hotDeployService.getStatus());
        } catch (Exception e) {
            log.error("Failed to load hot-deploy status", e);
            return Result.error("加载热更新状态失败");
        }
    }

    @PostMapping("/trigger")
    public Result<Map<String, Object>> trigger(@RequestBody(required = false) HotDeployRequest req) {
        if (!hotDeployService.isSupported()) {
            return Result.error("当前部署环境不支持热更新：" + hotDeployService.getUnsupportedReason());
        }
        try {
            String deployId = hotDeployService.trigger(req);
            if (deployId == null) {
                return Result.error("启动 deployer 容器失败，请查看后端日志");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("deployId", deployId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("Failed to trigger hot-deploy", e);
            return Result.error("触发失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs/{deployId}")
    public Result<Map<String, Object>> logs(
            @PathVariable String deployId,
            @RequestParam(name = "fromLine", defaultValue = "0") int fromLine) {
        try {
            return Result.success(hotDeployService.getLogs(deployId, fromLine));
        } catch (Exception e) {
            log.error("Failed to read hot-deploy logs", e);
            return Result.error("读取部署日志失败");
        }
    }
}
