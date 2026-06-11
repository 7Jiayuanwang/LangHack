package com.english.sso.controller;

import com.english.entity.User;
import com.english.service.UserService;
import com.english.sso.config.SsoProperties;
import com.english.sso.controller.SsoAuthController;
import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.service.SsoProtocolType;
import com.english.sso.service.SsoService;
import com.english.sso.service.SsoUserMappingService;
import com.english.util.JwtUtil;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SsoAuthController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@ActiveProfiles("sso")
@TestPropertySource(properties = {
    "sso.enabled=true",
    "sso.protocol=CAS",
    "sso.cas.enabled=true",
    "sso.cas.server-url=https://ids.imau.edu.cn/authserver",
    "app.base-url=http://localhost:8088",
    "app.frontend-url=http://localhost:8088",
    "spring.autoconfigure.exclude=org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@Import(SsoAuthControllerTest.MockMyBatisConfig.class)
class SsoAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SsoService ssoService;

    @MockBean
    private SsoUserMappingService ssoUserMappingService;

    @MockBean
    private com.english.sso.service.SsoLoginLogService ssoLoginLogService;

    @MockBean
    private com.english.service.impl.AccessLogBufferService accessLogBufferService;

    @MockBean
    private com.english.service.impl.AccessMetricsService accessMetricsService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private SsoProperties ssoProperties;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private UserService userService;

    @MockBean
    private com.english.service.DownloadTokenService downloadTokenService;

    @MockBean
    private com.english.service.TokenBlacklistService tokenBlacklistService;

    @MockBean
    private com.english.service.impl.ElevationService elevationService;

    @TestConfiguration
    static class MockMyBatisConfig {
        @Bean
        public javax.sql.DataSource dataSource() {
            return mock(javax.sql.DataSource.class);
        }

        @Bean
        public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(javax.sql.DataSource dataSource) {
            org.apache.ibatis.session.SqlSessionFactory factory = mock(org.apache.ibatis.session.SqlSessionFactory.class);
            Configuration config = new Configuration();
            config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
            when(factory.getConfiguration()).thenReturn(config);
            return factory;
        }
    }

    private SsoUserInfo mockUserInfo;
    private SsoLoginResult mockLoginResult;

    @BeforeEach
    void setUp() {
        // Stub ssoProperties.getCas() so buildServiceUrl() doesn't NPE
        SsoProperties.CasConfig casConfig = new SsoProperties.CasConfig();
        casConfig.setServerName("http://localhost:8088");
        when(ssoProperties.getCas()).thenReturn(casConfig);

        mockUserInfo = new SsoUserInfo();
        mockUserInfo.setSsoUserId("2023122066164");
        mockUserInfo.setSsoUsername("2023122066164");
        mockUserInfo.setEmail("student@imau.edu.cn");
        mockUserInfo.setRealName("张三");
        mockUserInfo.setMemberOf(Arrays.asList("ou=学生,ou=用户,dc=imau,dc=edu,dc=cn"));

        // Create mock user
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("2023122066164");
        mockUser.setRole("STUDENT");

        mockLoginResult = new SsoLoginResult();
        mockLoginResult.setToken("mock-jwt-token");
        mockLoginResult.setUser(mockUser);
        mockLoginResult.setNeedsApproval(false);
    }

    @Test
    void testGetLoginUrl() throws Exception {
        when(ssoService.isEnabled()).thenReturn(true);
        when(ssoService.getProtocolType()).thenReturn(SsoProtocolType.CAS);
        when(ssoService.generateLoginUrl(anyString())).thenReturn("https://ids.imau.edu.cn/authserver/login?service=...");

        mockMvc.perform(get("/api/auth/sso/login-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.loginUrl").exists())
                .andExpect(jsonPath("$.data.protocol").value("CAS"));
    }

    @Test
    void testGetLoginUrl_SsoDisabled() throws Exception {
        when(ssoService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/auth/sso/login-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("SSO 功能未启用"));
    }

    @Test
    void testHandleCallback_Success() throws Exception {
        // The callback endpoint is GET and redirects to frontend with a one-time code
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(ssoService.isEnabled()).thenReturn(true);
        when(ssoService.validateCallback(any(), anyString())).thenReturn(mockUserInfo);
        when(ssoService.getProtocolType()).thenReturn(SsoProtocolType.CAS);
        when(ssoUserMappingService.processLogin(any(), any())).thenReturn(mockLoginResult);

        mockMvc.perform(get("/api/auth/sso/callback")
                        .param("ticket", "ST-123-abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("http://localhost:8088/sso/callback?code=*"));
    }

    @Test
    void testHandleCallback_PendingApproval() throws Exception {
        mockLoginResult.setNeedsApproval(true);
        mockLoginResult.setToken(null);
        mockLoginResult.setUser(null);
        mockLoginResult.setMessage("您的账号正在审批中，请联系管理员");

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(ssoService.isEnabled()).thenReturn(true);
        when(ssoService.validateCallback(any(), anyString())).thenReturn(mockUserInfo);
        when(ssoService.getProtocolType()).thenReturn(SsoProtocolType.CAS);
        when(ssoUserMappingService.processLogin(any(), any())).thenReturn(mockLoginResult);

        mockMvc.perform(get("/api/auth/sso/callback")
                        .param("ticket", "ST-123-abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("http://localhost:8088/sso/callback?code=*"));
    }

    @Test
    void testGetStatus() throws Exception {
        when(ssoService.isEnabled()).thenReturn(true);
        when(ssoService.getProtocolType()).thenReturn(SsoProtocolType.CAS);

        mockMvc.perform(get("/api/auth/sso/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.protocol").value("CAS"));
    }
}
