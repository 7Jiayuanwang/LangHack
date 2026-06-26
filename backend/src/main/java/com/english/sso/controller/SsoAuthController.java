package com.english.sso.controller;

import com.alibaba.fastjson.JSON;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.sso.config.SsoProperties;
import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.exception.SsoBusinessException;
import com.english.sso.exception.SsoErrorCode;
import com.english.sso.service.SsoLoginLogService;
import com.english.sso.service.SsoProtocolType;
import com.english.sso.service.SsoService;
import com.english.sso.service.SsoUserMappingService;
import com.english.service.UserService;
import com.english.util.JwtUtil;
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

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Value("${app.frontend-url:${app.base-url}}")
    private String appFrontendUrl;

    /**
     * 获取 SSO 登录 URL
     */
    @GetMapping("/login-url")
    public Result<Map<String, String>> getLoginUrl() {
        try {
            if (!ssoService.isEnabled()) {
                return Result.error("SSO 功能未启用");
            }

            String serviceUrl = buildServiceUrl();
            String loginUrl = ssoService.generateLoginUrl(serviceUrl);

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
     *
     * state 的有效性校验由 SsoService.validateCallback(...) 负责。
     */
    @GetMapping("/callback")
    @ConditionalOnProperty(name = "sso.enabled", havingValue = "true")
    public void handleCallback(
            @RequestParam(required = false) String ticket,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        try {
            if (!ssoService.isEnabled()) {
                redirectToFrontendWithError(response, SsoErrorCode.SSO_DISABLED, "SSO 功能未启用");
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
                redirectToFrontendWithError(response, SsoErrorCode.MISSING_PARAM, "缺少认证参数");
                return;
            }

            String serviceUrl = buildServiceUrl();

            // 1. 验证 SSO 回调
            SsoUserInfo ssoUserInfo;
            try {
                ssoUserInfo = ssoService.validateCallback(callbackParams, serviceUrl);
            } catch (Exception ex) {
                // 票据/code 校验失败 — 归类到 AUTH_FAILED
                throw new SsoBusinessException(SsoErrorCode.AUTH_FAILED, ex.getMessage(), ex);
            }

            // 2. 处理用户映射/创建
            SsoLoginResult result = mappingService.processLogin(
                    ssoUserInfo,
                    ssoService.getProtocolType()
            );

            // 3. 记录日志
            loginLogService.log(ssoUserInfo, result, request);

            // 4. 生成一次性 code，存入 Redis
            String oneTimeCode = UUID.randomUUID().toString();

            Map<String, Object> payload = new HashMap<>();
            payload.put("status", result.isNeedsApproval() ? "PENDING" : "SUCCESS");
            payload.put("token", result.getToken()); // 兼容字段 = accessToken
            payload.put("accessToken", result.getAccessToken());
            payload.put("refreshToken", result.getRefreshToken());
            payload.put("needsApproval", result.isNeedsApproval() || result.isApprovalPendingAfterLogin());
            payload.put("approvalPendingAfterLogin", result.isApprovalPendingAfterLogin());
            payload.put("bindingId", result.getBindingId());
            payload.put("message", result.getMessage());
            stringRedisTemplate.opsForValue().set(
                    SSO_CODE_PREFIX + oneTimeCode,
                    JSON.toJSONString(payload),
                    SSO_CODE_TTL_MINUTES, TimeUnit.MINUTES
            );
            log.info("SSO 回调结果已写入一次性 code: code={}, status={}", oneTimeCode, payload.get("status"));

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

            // 分类错误：已知 SsoBusinessException 保留其 errorCode，其他归为 SYSTEM_ERROR
            SsoErrorCode errorCode;
            String message;
            if (e instanceof SsoBusinessException) {
                errorCode = ((SsoBusinessException) e).getErrorCode();
                message = e.getMessage();
            } else if (e instanceof com.english.exception.BusinessException) {
                // 未知业务异常兜底到 UNKNOWN
                errorCode = SsoErrorCode.UNKNOWN;
                message = e.getMessage();
            } else {
                errorCode = SsoErrorCode.SYSTEM_ERROR;
                message = "SSO 认证失败: " + e.getMessage();
            }
            redirectToFrontendWithError(response, errorCode, message);
        }
    }

    /**
     * 用一次性 code 换取 JWT token
     */
    @PostMapping("/token")
    @ConditionalOnProperty(name = "sso.enabled", havingValue = "true")
    public Result<Map<String, Object>> exchangeToken(
            @RequestBody Map<String, String> request) {

        String oneTimeCode = request.get("code");
        if (oneTimeCode == null || oneTimeCode.isEmpty()) {
            return errorWithCode(SsoErrorCode.MISSING_PARAM, "缺少授权码");
        }

        // 从 Redis 获取并删除一次性 code（一次性使用）
        String redisKey = SSO_CODE_PREFIX + oneTimeCode;
        String value = stringRedisTemplate.opsForValue().getAndDelete(redisKey);

        if (value == null) {
            log.warn("一次性 code 无效或已过期: {}", oneTimeCode);
            return errorWithCode(SsoErrorCode.CODE_EXPIRED, "授权码无效或已过期，请重新登录");
        }

        Map<String, Object> payload;
        try {
            payload = JSON.parseObject(value);
        } catch (Exception e) {
            log.warn("一次性 code 数据格式非法: {}", value);
            return errorWithCode(SsoErrorCode.CODE_EXPIRED, "授权码无效或已过期，请重新登录");
        }

        String status = String.valueOf(payload.getOrDefault("status", "SUCCESS"));
        if ("PENDING".equals(status)) {
            return errorWithCode(SsoErrorCode.PENDING_APPROVAL, String.valueOf(payload.getOrDefault("message", "待审批")));
        }

        String token = payload.get("token") == null ? null : String.valueOf(payload.get("token"));
        if (token == null || token.isBlank()) {
            return errorWithCode(SsoErrorCode.CODE_EXPIRED, "授权码无效或已过期，请重新登录");
        }
        // 双 token：accessToken/refreshToken 优先；缺失则回退到旧 token（仅 accessToken 的旧路径）
        String accessToken = payload.get("accessToken") != null
                ? String.valueOf(payload.get("accessToken"))
                : token;
        String refreshToken = payload.get("refreshToken") != null
                ? String.valueOf(payload.get("refreshToken"))
                : null;
        String pendingApprovalMessage = payload.get("message") == null ? null : String.valueOf(payload.get("message"));
        Long pendingBindingId = null;
        Object bindingIdValue = payload.get("bindingId");
        if (bindingIdValue instanceof Number) {
            pendingBindingId = ((Number) bindingIdValue).longValue();
        }
        Boolean approvalPendingAfterLogin = Boolean.TRUE.equals(payload.get("approvalPendingAfterLogin"));

        Map<String, Object> data = new HashMap<>();
        data.put("token", accessToken); // 兼容字段
        data.put("accessToken", accessToken);
        if (refreshToken != null) {
            data.put("refreshToken", refreshToken);
            data.put("refreshExpiresIn", JwtUtil.REFRESH_TOKEN_EXPIRATION_MILLIS);
        }

        // 从 JWT 中提取用户名，查询完整用户信息返回给前端
        try {
            String username = jwtUtil.getUsernameFromToken(accessToken);
            User user = userService.getUserByUsername(username);
            if (user != null) {
                user.setPassword(null); // 清除密码，避免返回给前端
                data.put("user", user);
            }
            // accessToken 过期时间（前端用于主动刷新定时器）
            data.put("expiresIn", JwtUtil.ACCESS_TOKEN_EXPIRATION_MILLIS);
            if (pendingApprovalMessage != null && approvalPendingAfterLogin) {
                data.put("needsApproval", true);
                data.put("approvalPendingAfterLogin", true);
                data.put("approvalMessage", pendingApprovalMessage);
                if (pendingBindingId != null) {
                    data.put("bindingId", pendingBindingId);
                }
            }
        } catch (Exception e) {
            log.warn("SSO token 交换时获取用户信息失败，仅返回 token", e);
        }

        log.info("一次性 code 换取 token 成功");
        return Result.success("登录成功", data);
    }

    /**
     * 构造带 errorCode 的错误响应
     */
    private Result<Map<String, Object>> errorWithCode(SsoErrorCode errorCode, String message) {
        Result<Map<String, Object>> result = Result.error(message);
        Map<String, Object> data = new HashMap<>();
        data.put("errorCode", errorCode.getCode());
        result.setData(data);
        return result;
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
     * 参照官方 caslogout.jsp：
     *   casLogoutURL + "?service=" + URLEncoder.encode("http://app:port/login.jsp")
     * service 参数是登出后跳转回的应用页面（前端登录页），不是 CAS 回调地址。
     */
    @GetMapping("/logout-url")
    public Result<Map<String, String>> getLogoutUrl() {
        Map<String, String> data = new HashMap<>();
        String logoutServiceUrl = appFrontendUrl + "/login";
        String logoutUrl = ssoService.generateLogoutUrl(logoutServiceUrl);
        data.put("logoutUrl", logoutUrl);
        return Result.success(data);
    }

    /**
     * 构建 CAS service URL
     *
     * 与官方 cas-client-core-3.2.1 的 serverName 模式完全一致：
     *   官方: service URL = serverName + request.getRequestURI()
     *   本项目: service URL = serverName + "/api/auth/sso/callback"
     *
     * serverName 是固定配置值（对应官方 web.xml 的 serverName 参数），
     * 不依赖任何 HTTP 请求头推断，保证登录和验证时 service URL 绝对一致。
     *
     * @see <a href="https://github.com/apereo/java-cas-client/blob/3.2.x/cas-client-core/src/main/java/org/jasig/cas/client/util/CommonUtils.java">CommonUtils.constructServiceUrl()</a>
     */
    private String buildServiceUrl() {
        String serverName = ssoProperties.getCas().getServerName();
        if (serverName == null || serverName.isEmpty()) {
            throw new IllegalStateException(
                    "sso.cas.server-name (SSO_CAS_SERVER_NAME) 未配置。" +
                    "此参数对应官方 web.xml 的 serverName，格式: 协议://域名[:端口]，" +
                    "示例: https://langhack.imau.edu.cn");
        }
        // 去掉尾部斜杠（与官方 AbstractCasFilter.setServerName() 一致）
        if (serverName.endsWith("/")) {
            serverName = serverName.substring(0, serverName.length() - 1);
        }
        String serviceUrl = serverName + "/api/auth/sso/callback";
        log.debug("CAS service URL: {}", serviceUrl);
        return serviceUrl;
    }

    /**
     * 重定向到前端并携带错误信息（附带 errorCode 供前端分类展示）
     */
    private void redirectToFrontendWithError(HttpServletResponse response, SsoErrorCode errorCode, String error)
            throws IOException {
        String safeMessage = error == null ? errorCode.getDefaultMessage() : error;
        String redirectUrl = appFrontendUrl + "/sso/callback?error="
                + java.net.URLEncoder.encode(safeMessage, java.nio.charset.StandardCharsets.UTF_8)
                + "&errorCode=" + errorCode.getCode();
        response.sendRedirect(redirectUrl);
    }
}
