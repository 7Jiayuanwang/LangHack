package com.english.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/")
    @PreAuthorize("permitAll()")
    public Map<String, String> home() {
        // 简化响应，仅返回最小信息用于LB健康检查
        return Collections.singletonMap("status", "ok");
    }

    @GetMapping("/api/health")
    @PreAuthorize("permitAll()")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", System.currentTimeMillis());
    }
}