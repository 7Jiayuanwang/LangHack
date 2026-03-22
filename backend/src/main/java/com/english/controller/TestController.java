package com.english.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * Test Controller - 仅在 dev profile 下生效
 * 用于开发环境的健康检查和负载均衡探测
 */
@Profile("dev")
@RestController
public class TestController {

    @GetMapping("/")
    @PreAuthorize("permitAll()")
    public Map<String, String> home() {
        // 简化响应，仅返回最小信息用于LB健康检查
        return Collections.singletonMap("status", "ok");
    }
}
