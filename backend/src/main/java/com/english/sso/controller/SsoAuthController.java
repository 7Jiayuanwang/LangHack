package com.english.sso.controller;

import com.english.dto.Result;
import com.english.sso.config.SsoProperties;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * SSO 认证控制器
 *
 * 回调流程（参考统一身份认证集成开发指南）：
 * 1. CAS/OAuth2 认证服务器 302 重定向浏览器到 /api/auth/sso/callback?ticket=xxx
 * 2. 后端验证 ticket，生成一次性 code 存入 Redis（5分钟有效）
 * 3. 302 重定向到前端 /sso/callback?code=oneTimeCode
 * 4. 前端 POST /api/auth/sso/token 用一次性 code 换取 JWT
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

    private static final String SSO_CODE_PREFIX = "sso:code:";
    private static final long SSO_CODE_TTL_MINUTES = 5;

    @Autowired
    private SsoService ssoService;

    @Autowired
    private SsoProperties ssoProperties;

    @Autowired
    private SsoUserMappingService mappingService;

    @Autowired
    private SsoLoginLogService loginLogService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${app.frontend-url:${app.base-url}}")
    private String appFrontendUrl;

    /**
     * 获取 SSO 登录 URL
     */
    @GetMapping("/login-url")
    public Result<Map<String, String>> getLoginUrl(HttpServletRequest request) {
        try {
            if (!ssoService.isEnabled()) {
                return Result.error("SSO 功能未启用");
            }

            String callbackUrl = buildCallbackUrl(request);
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
     * SSO 回调处理（CAS/OAuth2 认证服务器 302 重定向到此处）
     *
     * CAS: /api/auth/sso/callback?ticket=ST-xxx
     * OAuth2: /api/auth/sso/callback?code=xxx&state=xxx
     */
    @GetMapping("/callback")
    public void handleCallback(
            @RequestParam(required = false) String ticket,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        try {
            if (!ssoService.isEnabled()) {
                redirectToFrontendWithError(response, "SSO 功能未启用");
                return;
            }

            // 构建回调参数
            Map<String, String> callbackParams = new HashMap<>();
            if (ticket != null && !ticket.isEmpty()) {
                callbackParams.put("ticket", ticket);
            } else if (code != null && !code.isEmpty()) {
                callbackParams.put("code", code);
                if (state != null) {
                    callbackParams.put("state", state);
                }
            } else {
                redirectToFrontendWithError(response, "缺少认证参数");
                return;
            }

            String callbackUrl = buildCallbackUrl(request);

            // 1. 验证 SSO 回调
            SsoUserInfo ssoUserInfo = ssoService.validateCallback(callbackParams, callbackUrl);

            // 2. 处理用户映射/创建
            SsoLoginResult result = mappingService.processLogin(
                    ssoUserInfo,
                    ssoService.getProtocolType()
            );

            // 3. 记录日志
            loginLogService.log(ssoUserInfo, result, request);

            // 4. 生成一次性 code，存入 Redis
            String oneTimeCode = UUID.randomUUID().toString();

            if (result.isNeedsApproval()) {
                // 待审批状态 — 仍然重定向到前端，但标记为待审批
                stringRedisTemplate.opsForValue().set(
                        SSO_CODE_PREFIX + oneTimeCode,
                        "PENDING:" + result.getMessage(),
                        SSO_CODE_TTL_MINUTES, TimeUnit.MINUTES
                );
                log.info("SSO 待审批用户，生成一次性 code: {}", oneTimeCode);
            } else {
                // 登录成功 — 存储 JWT token
                stringRedisTemplate.opsForValue().set(
                        SSO_CODE_PREFIX + oneTimeCode,
                        result.getToken(),
                        SSO_CODE_TTL_MINUTES, TimeUnit.MINUTES
                );
                log.info("SSO 登录成功，生成一次性 code: {}", oneTimeCode);
            }

            // 5. 重定向到前端 SSO 回调页面
            String redirectUrl = appFrontendUrl + "/sso/callback?code=" + oneTimeCode;
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("SSO 回调处理失败", e);

            // 记录失败日志
            try {
                loginLogService.logFailure(
                        ssoService.getProtocolType().name(),
                        ticket != null ? ticket : (code != null ? code : "unknown"),
                        "unknown",
                        e.getMessage(),
                        request
                );
            } catch (Exception logEx) {
                log.error("记录失败日志失败", logEx);
            }

            redirectToFrontendWithError(response, "SSO 认证失败: " + e.getMessage());
        }
    }

    /**
     * 用一次性 code 换取 JWT token
     */
    @PostMapping("/token")
    public Result<Map<String, Object>> exchangeToken(
            @RequestBody Map<String, String> request) {

        String oneTimeCode = request.get("code");
        if (oneTimeCode == null || oneTimeCode.isEmpty()) {
            return Result.error("缺少授权码");
        }

        // 从 Redis 获取并删除一次性 code（一次性使用）
        String redisKey = SSO_CODE_PREFIX + oneTimeCode;
        String value = stringRedisTemplate.opsForValue().getAndDelete(redisKey);

        if (value == null) {
            log.warn("一次性 code 无效或已过期: {}", oneTimeCode);
            return Result.error("授权码无效或已过期，请重新登录");
        }

        // 检查是否为待审批状态
        if (value.startsWith("PENDING:")) {
            String message = value.substring("PENDING:".length());
            Map<String, Object> data = new HashMap<>();
            data.put("pending", true);
            data.put("message", message);
            return Result.error(message);
        }

        // 登录成功 — value 就是 JWT token
        Map<String, Object> data = new HashMap<>();
        data.put("token", value);

        log.info("一次性 code 换取 token 成功");
        return Result.success("登录成功", data);
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

    /**
     * 获取 CAS 登出 URL（供前端退出登录后跳转，确保 CAS 侧也登出）
     *
     * 统一身份认证对接要求：点击退出后返回统一身份认证页面，
     * 否则用户在 CAS 侧仍处于登录状态，再次 SSO 登录会自动跳回而无法切换账号。
     */
    @GetMapping("/logout-url")
    public Result<Map<String, String>> getLogoutUrl(HttpServletRequest request) {
        Map<String, String> data = new HashMap<>();
        String callbackUrl = buildCallbackUrl(request);
        // CAS logout 协议：service 参数指定登出后跳转回的地址
        String logoutUrl = ssoService.generateLogoutUrl(callbackUrl);
        data.put("logoutUrl", logoutUrl);
        return Result.success(data);
    }

    /**
     * 构建 CAS service URL（回调地址）
     *
     * 参照官方 cas-client-core-3.2.1 的 serverName 模式：
     * 标准 CAS 客户端通过 serverName + request.getRequestURI() 动态构建 service URL，
     * 确保 CAS 颁发 ticket 时记录的 service 与验证时提供的 service 完全一致。
     *
     * 优先级：
     * 1. SSO_CAS_SERVICE_URL 环境变量（精确指定，适用于特殊场景）
     * 2. 从当前 HTTP 请求动态构建（与官方 CAS 客户端行为一致）
     *    - Nginx 反向代理场景下，通过 X-Forwarded-Proto/Header 还原真实协议和域名
     * 3. 回退到 APP_BASE_URL + 路径（兼容无代理的直连场景）
     */
    private String buildCallbackUrl(HttpServletRequest request) {
        // 优先使用显式配置的 service URL
        String configuredServiceUrl = ssoProperties.getCas().getServiceUrl();
        if (configuredServiceUrl != null && !configuredServiceUrl.isEmpty()) {
            log.info("使用配置的 CAS service URL: {}", configuredServiceUrl);
            return configuredServiceUrl;
        }

        // 从请求动态构建（参照 CAS 客户端 serverName 模式）
        // Nginx 设置了 X-Forwarded-Proto 和 Host，可还原用户实际访问的 URL
        String scheme = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("Host");

        if (scheme != null && !scheme.isEmpty() && host != null && !host.isEmpty()) {
            // 反向代理场景：使用用户实际访问的协议和域名
            String callbackUrl = scheme + "://" + host + "/api/auth/sso/callback";
            log.info("从请求头动态构建 CAS service URL: {} (X-Forwarded-Proto={}, Host={})",
                    callbackUrl, scheme, host);
            return callbackUrl;
        }

        // 回退：使用 APP_BASE_URL
        String fallbackUrl = appBaseUrl + "/api/auth/sso/callback";
        log.info("回退使用 APP_BASE_URL 构建 CAS service URL: {}", fallbackUrl);
        return fallbackUrl;
    }

    /**
     * 重定向到前端并携带错误信息
     */
    private void redirectToFrontendWithError(HttpServletResponse response, String error) throws IOException {
        String redirectUrl = appFrontendUrl + "/sso/callback?error=" +
                java.net.URLEncoder.encode(error, java.nio.charset.StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}
