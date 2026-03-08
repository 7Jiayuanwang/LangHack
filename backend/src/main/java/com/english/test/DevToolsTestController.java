package com.english.test;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DevTools测试控制器（仅在 dev profile 下生效）
 */
@Profile("dev")
@RestController
@RequestMapping("/api/test")
public class DevToolsTestController {

    @GetMapping("/devtools")
    public String testDevTools() {
        return "DevTools is working!";
    }
}
