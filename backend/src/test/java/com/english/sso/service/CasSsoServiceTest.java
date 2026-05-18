package com.english.sso.service;

import com.english.exception.BusinessException;
import com.english.sso.config.SsoProperties;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.service.SsoProtocolType;
import com.english.sso.service.impl.CasSsoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CasSsoServiceTest {

    @Mock
    private SsoProperties ssoProperties;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private CasSsoService casSsoService;

    private SsoProperties.CasConfig casConfig;

    @BeforeEach
    void setUp() {
        casConfig = new SsoProperties.CasConfig();
        casConfig.setEnabled(true);
        casConfig.setServerUrl("https://ids.imau.edu.cn/authserver");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("username", "uid");
        attributes.put("email", "mail");
        attributes.put("real-name", "cn");
        attributes.put("member-of", "memberOf");
        casConfig.setAttributes(attributes);

        lenient().when(ssoProperties.isEnabled()).thenReturn(true);
        lenient().when(ssoProperties.getCas()).thenReturn(casConfig);
    }

    @Test
    void testGenerateLoginUrl() {
        String callbackUrl = "https://learn.imau.edu.cn/api/auth/sso/callback";
        String loginUrl = casSsoService.generateLoginUrl(callbackUrl);

        assertNotNull(loginUrl);
        assertTrue(loginUrl.startsWith("https://ids.imau.edu.cn/authserver/login"));
        assertTrue(loginUrl.contains("service="));
    }

    @Test
    void testValidateCallback_Success() {
        // Mock CAS ticket validation response
        String xmlResponse = """
            <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
                <cas:authenticationSuccess>
                    <cas:user>2023122066164</cas:user>
                    <cas:attributes>
                        <cas:uid>2023122066164</cas:uid>
                        <cas:mail>student@imau.edu.cn</cas:mail>
                        <cas:cn>张三</cas:cn>
                        <cas:memberOf>ou=学生,ou=用户,dc=imau,dc=edu,dc=cn</cas:memberOf>
                    </cas:attributes>
                </cas:authenticationSuccess>
            </cas:serviceResponse>
            """;

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(xmlResponse);

        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("ticket", "ST-123-abc");
        String callbackUrl = "https://learn.imau.edu.cn/api/auth/sso/callback";

        SsoUserInfo userInfo = casSsoService.validateCallback(callbackParams, callbackUrl);

        assertNotNull(userInfo);
        assertEquals("2023122066164", userInfo.getSsoUserId());
        assertEquals("2023122066164", userInfo.getSsoUsername());
        assertEquals("student@imau.edu.cn", userInfo.getEmail());
        assertEquals("张三", userInfo.getRealName());
        assertNotNull(userInfo.getMemberOf());
        assertEquals(1, userInfo.getMemberOf().size());
    }

    @Test
    void testValidateCallback_UsesConfiguredUsernameAttribute() {
        String xmlResponse = """
            <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
                <cas:authenticationSuccess>
                    <cas:user>opaque-cas-subject</cas:user>
                    <cas:attributes>
                        <cas:uid>2023122066164</cas:uid>
                        <cas:mail>student@imau.edu.cn</cas:mail>
                        <cas:cn>张三</cas:cn>
                    </cas:attributes>
                </cas:authenticationSuccess>
            </cas:serviceResponse>
            """;

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(xmlResponse);

        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("ticket", "ST-123-abc");

        SsoUserInfo userInfo = casSsoService.validateCallback(
                callbackParams,
                "https://learn.imau.edu.cn/api/auth/sso/callback"
        );

        assertEquals("opaque-cas-subject", userInfo.getSsoUserId());
        assertEquals("2023122066164", userInfo.getSsoUsername());
        assertEquals("张三", userInfo.getRealName());
    }

    @Test
    void testValidateCallback_MissingTicket() {
        Map<String, String> callbackParams = new HashMap<>();
        String callbackUrl = "https://learn.imau.edu.cn/api/auth/sso/callback";

        assertThrows(BusinessException.class, () -> {
            casSsoService.validateCallback(callbackParams, callbackUrl);
        });
    }

    @Test
    void testGetProtocolType() {
        assertEquals(SsoProtocolType.CAS, casSsoService.getProtocolType());
    }

    @Test
    void testIsEnabled() {
        assertTrue(casSsoService.isEnabled());

        // Test when CAS is disabled
        casConfig.setEnabled(false);
        assertFalse(casSsoService.isEnabled());

        // Test when SSO is disabled
        casConfig.setEnabled(true);
        when(ssoProperties.isEnabled()).thenReturn(false);
        assertFalse(casSsoService.isEnabled());
    }
}
