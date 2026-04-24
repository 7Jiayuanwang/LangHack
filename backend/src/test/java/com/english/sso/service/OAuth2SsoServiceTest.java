package com.english.sso.service;

import com.english.exception.BusinessException;
import com.english.sso.config.SsoProperties;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.service.impl.OAuth2SsoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2SsoService 单元测试
 *
 * 重点覆盖 2026-04-19 修复的 state CSRF 漏洞：
 *   - generateLoginUrl 将 state 写入 Redis（5 min TTL）
 *   - validateCallback 校验 state 存在且未被消费
 *   - Redis 不可用时 graceful degradation（记录告警但不阻塞登录）
 */
@ExtendWith(MockitoExtension.class)
class OAuth2SsoServiceTest {

    @Mock
    private SsoProperties ssoProperties;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OAuth2SsoService oauth2SsoService;

    private SsoProperties.OAuth2Config oauth2Config;

    @BeforeEach
    void setUp() {
        oauth2Config = new SsoProperties.OAuth2Config();
        oauth2Config.setEnabled(true);
        oauth2Config.setAuthorizationUrl("https://ids.imau.edu.cn/oauth2/authorize");
        oauth2Config.setTokenUrl("https://ids.imau.edu.cn/oauth2/token");
        oauth2Config.setUserinfoUrl("https://ids.imau.edu.cn/oauth2/userinfo");
        oauth2Config.setClientId("langhack");
        oauth2Config.setClientSecret("secret");
        oauth2Config.setScope("profile");

        lenient().when(ssoProperties.isEnabled()).thenReturn(true);
        lenient().when(ssoProperties.getOauth2()).thenReturn(oauth2Config);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void generateLoginUrl_savesStateToRedis() {
        String callbackUrl = "https://langhack.imau.edu.cn/api/auth/sso/callback";

        String loginUrl = oauth2SsoService.generateLoginUrl(callbackUrl);

        assertNotNull(loginUrl);
        assertTrue(loginUrl.startsWith("https://ids.imau.edu.cn/oauth2/authorize"));
        assertTrue(loginUrl.contains("state="));

        // 验证 state 被存入 Redis，key 以预期前缀开头，TTL=5 min
        verify(valueOperations, times(1)).set(
                contains(OAuth2SsoService.STATE_KEY_PREFIX),
                eq("1"),
                eq(OAuth2SsoService.STATE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void generateLoginUrl_redisFailure_doesNotThrow() {
        // Redis 宕机时 generateLoginUrl 应优雅降级（记录告警但正常返回 URL）
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        String callbackUrl = "https://langhack.imau.edu.cn/api/auth/sso/callback";
        assertDoesNotThrow(() -> {
            String url = oauth2SsoService.generateLoginUrl(callbackUrl);
            assertNotNull(url);
            assertTrue(url.contains("state="));
        });
    }

    @Test
    void validateCallback_missingCode_throwsBusinessException() {
        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("state", "some-state");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> oauth2SsoService.validateCallback(callbackParams, "https://example.com/callback"));
        assertTrue(ex.getMessage().contains("code"));
    }

    @Test
    void validateCallback_missingState_throwsBusinessException() {
        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("code", "auth-code-123");
        // 故意不设置 state

        BusinessException ex = assertThrows(BusinessException.class,
                () -> oauth2SsoService.validateCallback(callbackParams, "https://example.com/callback"));
        assertTrue(ex.getMessage().contains("state"));
    }

    @Test
    void validateCallback_invalidState_throwsBusinessException() {
        // Redis 返回 false → state 不存在或已被消费 → 拒绝
        when(redisTemplate.delete(anyString())).thenReturn(Boolean.FALSE);

        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("code", "auth-code-123");
        callbackParams.put("state", "forged-state");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> oauth2SsoService.validateCallback(callbackParams, "https://example.com/callback"));
        assertTrue(ex.getMessage().contains("state"));

        // 验证 Redis 被调用（尝试删除 state）
        verify(redisTemplate, times(1)).delete(contains(OAuth2SsoService.STATE_KEY_PREFIX));
    }

    @Test
    void validateCallback_validState_proceedsAndReturnsUserInfo() {
        // 1. state 存在且被成功删除
        when(redisTemplate.delete(anyString())).thenReturn(Boolean.TRUE);

        // 2. mock token 交换
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "access-xyz");
        ResponseEntity<Map> tokenResp = new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        // 3. mock userinfo 接口
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nickName", "张三");
        attributes.put("email", "zhangsan@imau.edu.cn");
        Map<String, Object> userinfoResponse = new HashMap<>();
        userinfoResponse.put("id", "2023122066164");
        userinfoResponse.put("attributes", attributes);
        ResponseEntity<Map> userinfoResp = new ResponseEntity<>(userinfoResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(eq(oauth2Config.getTokenUrl()), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(tokenResp);
        when(restTemplate.postForEntity(eq(oauth2Config.getUserinfoUrl()), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(userinfoResp);

        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("code", "auth-code-123");
        callbackParams.put("state", "valid-state");

        SsoUserInfo userInfo = oauth2SsoService.validateCallback(callbackParams, "https://example.com/callback");

        assertNotNull(userInfo);
        assertEquals("2023122066164", userInfo.getSsoUserId());
        assertEquals("张三", userInfo.getRealName());
        assertEquals("zhangsan@imau.edu.cn", userInfo.getEmail());

        // 验证 state 被消费
        verify(redisTemplate, times(1)).delete(contains(OAuth2SsoService.STATE_KEY_PREFIX));
    }

    @Test
    void validateCallback_redisDown_degradesGracefully() {
        // Redis 返回 null → 连接异常 → graceful degradation（记录告警但继续）
        when(redisTemplate.delete(anyString())).thenReturn(null);

        // mock token 交换 + userinfo
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "access-xyz");
        ResponseEntity<Map> tokenResp = new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        Map<String, Object> userinfoResponse = new HashMap<>();
        userinfoResponse.put("id", "2023122066164");
        userinfoResponse.put("attributes", new HashMap<>());
        ResponseEntity<Map> userinfoResp = new ResponseEntity<>(userinfoResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(eq(oauth2Config.getTokenUrl()), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(tokenResp);
        when(restTemplate.postForEntity(eq(oauth2Config.getUserinfoUrl()), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(userinfoResp);

        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("code", "auth-code-123");
        callbackParams.put("state", "some-state");

        SsoUserInfo userInfo = assertDoesNotThrow(() ->
                oauth2SsoService.validateCallback(callbackParams, "https://example.com/callback"));
        assertNotNull(userInfo);
    }
}
