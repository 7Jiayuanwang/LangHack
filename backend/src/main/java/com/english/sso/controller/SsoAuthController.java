package com.english.sso.controller;

import com.english.dto.Result;
import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.service.SsoLoginLogService;
import com.english.sso.service.SsoProtocolType;
import com.english.sso.service.SsoService;
import com.english.sso.service.SsoUserMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * SSO 认证控制器
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/sso")
@Profile("sso")
@ConditionalOnProperty(name = "sso.enabled", havingValue = "true")
public class SsoAuthController {

    @Autowired
    private SsoService ssoService;

    @Autowired
    private SsoUserMappingService mappingService;

    @Autowired
    private SsoLoginLogService loginLogService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    /**
     * 获取 SSO 登录 URL
     */
    @GetMapping("/login-url")
    public Result<Map<String, String>> getLoginUrl() {
        try {
            if (!ssoService.isEnabled()) {
                return Result.error("SSO 功能未启用");
            }

            String callbackUrl = appBaseUrl + "/api/auth/sso/callback";
            String loginUrl = ssoService.generateLoginUrl(callbackUrl);

            Map<String, String> data = new HashMap<>();
            data.put("loginUrl", loginUrl);
            data.put("protocol", ssoService.getProtocolType().name());

            return Result.success(data);

        } catch (Exception e) {
            log.error("获取 SSO 登录 URL 失败", e);
            return Result.error("SSO 服务暂时不可用");
        }
    }

    /**
     * SSO 回调处理
     */
    @PostMapping("/callback")
    public Result<Map<String, Object>> handleCallback(
            @RequestBody Map<String, String> callbackParams,
            HttpServletRequest request) {

        try {
            if (!ssoService.isEnabled()) {
                return Result.error("SSO 功能未启用");
            }

            String callbackUrl = appBaseUrl + "/api/auth/sso/callback";

            // 1. 验证 SSO 回调
            SsoUserInfo ssoUserInfo = ssoService.validateCallback(callbackParams, callbackUrl);

            // 2. 处理用户映射/创建
            SsoLoginResult result = mappingService.processLogin(
                    ssoUserInfo,
                    ssoService.getProtocolType()
            );

            // 3. 记录日志
            loginLogService.log(ssoUserInfo, result, request);

            // 4. 返回结果
            if (result.isNeedsApproval()) {
                return Result.error(result.getMessage());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("token", result.getToken());
            data.put("user", result.getUser());

            return Result.success("登录成功", data);

        } catch (Exception e) {
            log.error("SSO 回调处理失败", e);

            // 记录失败日志
            try {
                loginLogService.logFailure(
                        ssoService.getProtocolType().name(),
                        callbackParams.getOrDefault("ticket", callbackParams.getOrDefault("code", "unknown")),
                        "unknown",
                        e.getMessage(),
                        request
                );
            } catch (Exception logEx) {
                log.error("记录失败日志失败", logEx);
            }

            return Result.error("SSO 认证失败: " + e.getMessage());
        }
    }

    /**
     * 检查 SSO 状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", ssoService.isEnabled());
        data.put("protocol", ssoService.getProtocolType().name());

        return Result.success(data);
    }
}
