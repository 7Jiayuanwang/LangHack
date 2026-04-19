package com.english.sso.service.impl;

import com.english.exception.BusinessException;
import com.english.sso.config.SsoProperties;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.service.SsoProtocolType;
import com.english.sso.service.SsoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 2.0 SSO 服务实现
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Slf4j
@Service
@Profile("sso")
@ConditionalOnProperty(name = "sso.protocol", havingValue = "OAUTH2")
public class OAuth2SsoService implements SsoService {

    /** Redis 中 state 的 key 前缀，用于 OAuth2 CSRF 防护 */
    static final String STATE_KEY_PREFIX = "sso:oauth2:state:";

    /** state 的 TTL（秒），覆盖正常 OAuth 登录流程的时长 */
    static final long STATE_TTL_SECONDS = 300L;

    @Autowired
    private SsoProperties ssoProperties;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public String generateLoginUrl(String callbackUrl) {
        if (!isEnabled()) {
            throw new BusinessException("OAuth 2.0 SSO 未启用");
        }

        SsoProperties.OAuth2Config oauth2 = ssoProperties.getOauth2();
        String state = UUID.randomUUID().toString();

        // 存入 Redis：callback 时必须携带此 state 并通过校验 → 防 CSRF
        // Redis 失败时记录告警但不阻塞登录（向后兼容），validateCallback 也有 graceful degradation
        try {
            redisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, "1", STATE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to persist OAuth2 state to Redis, CSRF protection degraded: {}", e.getMessage());
        }

        String authUrl = oauth2.getAuthorizationUrl() +
                "?response_type=code" +
                "&client_id=" + oauth2.getClientId() +
                "&redirect_uri=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8) +
                "&scope=" + oauth2.getScope() +
                "&state=" + state;

        log.info("生成 OAuth 2.0 登录 URL: {}", authUrl);
        return authUrl;
    }

    @Override
    public SsoUserInfo validateCallback(Map<String, String> callbackParams, String callbackUrl) {
        String code = callbackParams.get("code");
        if (code == null || code.isEmpty()) {
            throw new BusinessException("缺少 OAuth 2.0 code 参数");
        }

        // CSRF 防护：校验 state 参数
        String state = callbackParams.get("state");
        if (state == null || state.isEmpty()) {
            throw new BusinessException("缺少 state 参数");
        }
        try {
            Boolean deleted = redisTemplate.delete(STATE_KEY_PREFIX + state);
            if (Boolean.FALSE.equals(deleted)) {
                // Redis 明确返回 false → state 不存在或已被消费 → 拒绝
                throw new BusinessException("state 无效或已过期");
            }
            // deleted == null 表示 Redis 连接异常 → graceful degradation，记录告警但继续
            if (deleted == null) {
                log.warn("Redis unavailable during OAuth2 state validation, CSRF protection degraded");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OAuth2 state validation threw exception, CSRF protection degraded: {}", e.getMessage());
        }

        log.info("验证 OAuth 2.0 code: {}", code);

        try {
            // 1. 用 code 换取 access_token
            String accessToken = exchangeCodeForToken(code, callbackUrl);

            // 2. 用 access_token 获取用户信息
            return fetchUserInfo(accessToken);

        } catch (Exception e) {
            log.error("OAuth 2.0 认证失败", e);
            throw new BusinessException("OAuth 2.0 认证失败: " + e.getMessage());
        }
    }

    @Override
    public SsoProtocolType getProtocolType() {
        return SsoProtocolType.OAUTH2;
    }

    @Override
    public boolean isEnabled() {
        return ssoProperties.isEnabled() && ssoProperties.getOauth2().isEnabled();
    }

    @Override
    public String generateLogoutUrl(String serviceUrl) {
        // OAuth2 场景下，CAS 统一身份认证的 logout 端点仍然可用
        String casServerUrl = ssoProperties.getCas().getServerUrl();
        return casServerUrl + "/logout?service=" +
                URLEncoder.encode(serviceUrl, StandardCharsets.UTF_8);
    }

    /**
     * 用授权码换取访问令牌
     */
    private String exchangeCodeForToken(String code, String redirectUri) {
        SsoProperties.OAuth2Config oauth2 = ssoProperties.getOauth2();

        // 构建请求参数
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", oauth2.getClientId());
        params.add("client_secret", oauth2.getClientSecret());
        params.add("code", code);
        params.add("redirect_uri", redirectUri);

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        // 发送请求
        ResponseEntity<Map> response = restTemplate.postForEntity(
                oauth2.getTokenUrl(),
                request,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException("换取 access_token 失败");
        }

        Map<String, Object> tokenResponse = response.getBody();
        String accessToken = (String) tokenResponse.get("access_token");

        if (accessToken == null || accessToken.isEmpty()) {
            throw new BusinessException("access_token 为空");
        }

        log.info("成功获取 access_token");
        return accessToken;
    }

    /**
     * 用访问令牌获取用户信息
     */
    private SsoUserInfo fetchUserInfo(String accessToken) {
        SsoProperties.OAuth2Config oauth2 = ssoProperties.getOauth2();

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);

        // 构建请求体（某些 IdP 需要在 body 中传 access_token）
        Map<String, String> body = new HashMap<>();
        body.put("access_token", accessToken);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        // 发送请求
        ResponseEntity<Map> response = restTemplate.postForEntity(
                oauth2.getUserinfoUrl(),
                request,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException("获取用户信息失败");
        }

        Map<String, Object> userInfoResponse = response.getBody();
        log.debug("OAuth 2.0 用户信息响应: {}", userInfoResponse);

        // 解析用户信息
        SsoUserInfo userInfo = new SsoUserInfo();
        userInfo.setProvider("OAUTH2");
        userInfo.setSsoUserId((String) userInfoResponse.get("id"));
        userInfo.setSsoUsername((String) userInfoResponse.get("id"));
        userInfo.setAttributes(userInfoResponse);

        // 提取 attributes 对象
        if (userInfoResponse.containsKey("attributes")) {
            Map<String, Object> attributes = (Map<String, Object>) userInfoResponse.get("attributes");
            userInfo.setRealName((String) attributes.get("nickName"));
            userInfo.setEmail((String) attributes.get("email"));

            // 提取 memberOf
            Object memberOfValue = attributes.get("memberOf");
            userInfo.setMemberOf(parseMemberOf(memberOfValue));
        }

        log.info("OAuth 2.0 用户信息解析成功: userId={}, realName={}", userInfo.getSsoUserId(), userInfo.getRealName());
        return userInfo;
    }

    /**
     * 解析 memberOf 属性
     */
    private List<String> parseMemberOf(Object memberOfValue) {
        if (memberOfValue == null) {
            return new ArrayList<>();
        }

        if (memberOfValue instanceof String) {
            String str = (String) memberOfValue;
            if (str.contains(",")) {
                return Arrays.asList(str.split(","));
            } else {
                return Collections.singletonList(str);
            }
        } else if (memberOfValue instanceof List) {
            return (List<String>) memberOfValue;
        }

        return new ArrayList<>();
    }
}
