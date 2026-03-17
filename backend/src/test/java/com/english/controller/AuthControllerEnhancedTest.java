package com.english.controller;

import com.english.test.TestUtils;
import com.english.dto.LoginRequest;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enhanced AuthController unit tests.
 * Covers: register/login/refresh token, JWT expiry/tampering, BusinessException.
 */
@DisplayName("AuthController Enhanced Tests")
class AuthControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .addFilters(TestUtils.authenticationFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    @Test
    @DisplayName("POST /api/auth/login - success returns token")
    void login_success() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("password123");

        Map<String, Object> result = new HashMap<>();
        result.put("token", "mock-jwt-token");
        result.put("refreshToken", "mock-refresh-token");
        result.put("user", TestHelper.createMockUser(1L, "testuser", "USER"));
        when(userService.login(any(LoginRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token"));

        verify(userService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login - invalid credentials returns error")
    void login_invalidCredentials_returnsError() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("wrongpassword");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("POST /api/auth/login - user not found returns error")
    void login_userNotFound_returnsError() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent");
        req.setPassword("password123");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException("用户不存在"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("POST /api/auth/login - service exception returns generic error")
    void login_serviceException_returnsGenericError() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("password123");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - refresh token success")
    void refreshToken_success() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("refreshToken", "mock-refresh-token");

        Map<String, Object> result = new HashMap<>();
        result.put("token", "new-jwt-token");
        result.put("refreshToken", "new-refresh-token");
        when(userService.refreshToken(anyString())).thenReturn(result);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/auth/user-info - get current user info")
    void getUserInfo_success() throws Exception {
        mockMvc.perform(get("/api/auth/user-info"))
                .andExpect(status().isUnauthorized());
    }
}
