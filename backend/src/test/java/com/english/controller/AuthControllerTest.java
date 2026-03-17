package com.english.controller;
import com.english.test.TestUtils;

import com.english.dto.LoginRequest;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setMessageConverters(converter)
                .addFilters(TestUtils.authenticationFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== POST /api/auth/login ==========

    @Test
    @DisplayName("POST /api/auth/login - success returns token and user info")
    void login_success() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        Map<String, Object> loginResult = new HashMap<>();
        loginResult.put("token", "mock-jwt-token");
        loginResult.put("refreshToken", "mock-refresh-token");
        loginResult.put("user", TestHelper.createMockUser(1L, "testuser", "USER"));

        when(userService.login(any(LoginRequest.class))).thenReturn(loginResult);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token"));

        verify(userService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login - BusinessException returns business error message")
    void login_businessException_returnsBusinessError() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("wrongpassword");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("POST /api/auth/login - non-business exception returns generic error")
    void login_wrongPassword_returnsGenericError() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("wrongpassword");

        // IllegalArgumentException is NOT a BusinessException, so it falls into catch(Exception)
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录失败，请稍后重试"));
    }

    @Test
    @DisplayName("POST /api/auth/login - unexpected exception returns generic error")
    void login_unexpectedException_returnsGenericError() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录失败，请稍后重试"));
    }

    // ========== POST /api/auth/register ==========

    @Test
    @DisplayName("POST /api/auth/register - success")
    void register_success() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "password123");

        doNothing().when(userService).createUser(any(User.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // Result.success("注册成功，您已注册为普通用户") calls success(T data)
                // so message="操作成功", data="注册成功，您已注册为普通用户"
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value("注册成功，您已注册为普通用户"));

        verify(userService).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate username returns error via BusinessException")
    void register_duplicateUsername_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "existinguser");
        newUser.put("password", "password123");

        // Controller calls createUser directly; duplicate detection is inside createUser
        // which throws BusinessException when username already exists
        doThrow(new BusinessException("用户名已存在"))
                .when(userService).createUser(any(User.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        verify(userService).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - always sets role to USER regardless of input")
    void register_setsDefaultRoleToUser() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "password123");
        // Even if role is null, controller forces USER

        doNothing().when(userService).createUser(any(User.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).createUser(userCaptor.capture());
        Assertions.assertEquals("USER", userCaptor.getValue().getRole());
    }

    @Test
    @DisplayName("POST /api/auth/register - overrides ADMIN role to USER (mass assignment protection)")
    void register_overridesAdminRoleToUser() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "password123");
        newUser.put("role", "ADMIN"); // attacker tries to set ADMIN

        doNothing().when(userService).createUser(any(User.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).createUser(userCaptor.capture());
        Assertions.assertEquals("USER", userCaptor.getValue().getRole());
        // id and deleted should be cleared
        Assertions.assertNull(userCaptor.getValue().getId());
        Assertions.assertNull(userCaptor.getValue().getDeleted());
    }

    @Test
    @DisplayName("POST /api/auth/register - password too short returns validation error")
    void register_shortPassword_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "pw"); // length < 6

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("密码长度不能少于 6 位"));

        // createUser should never be called because validation fails first
        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - empty username returns validation error")
    void register_emptyUsername_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - null username returns validation error")
    void register_nullUsername_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("password", "password123");
        // username is null (not in map)

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - username too short returns validation error")
    void register_usernameTooShort_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "ab"); // length < 3
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名长度须在 3~50 个字符之间"));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - invalid username characters returns error")
    void register_invalidUsernameChars_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "user name!"); // contains space and !
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名只能包含字母、数字及 _ - . @ 符号"));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - RuntimeException returns generic error")
    void register_runtimeException_returnsGenericError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "password123");

        doThrow(new RuntimeException("DB error"))
                .when(userService).createUser(any(User.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("注册失败，请稍后重试"));
    }

    @Test
    @DisplayName("POST /api/auth/register - invalid email format returns error")
    void register_invalidEmail_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "password123");
        newUser.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("邮箱格式无效或长度超过100字符"));

        verify(userService, never()).createUser(any(User.class));
    }

    // ========== GET /api/auth/userinfo ==========

    @Test
    @DisplayName("GET /api/auth/userinfo - authenticated user returns user info without password")
    void getUserInfo_authenticated_returnsUserInfo() throws Exception {
        TestHelper.createMockAuthentication("testuser", "USER");
        User mockUser = TestHelper.createMockUser(1L, "testuser", "USER");
        mockUser.setPassword("encoded-password");

        when(userService.getUserByUsername("testuser")).thenReturn(mockUser);

        mockMvc.perform(get("/api/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/userinfo - user not found returns error")
    void getUserInfo_userNotFound_returnsError() throws Exception {
        TestHelper.createMockAuthentication("ghostuser", "USER");

        when(userService.getUserByUsername("ghostuser")).thenReturn(null);

        mockMvc.perform(get("/api/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("GET /api/auth/userinfo - unauthenticated returns error")
    void getUserInfo_unauthenticated_returnsError() throws Exception {
        // No authentication set in SecurityContext — defaults to anonymousUser
        // MockMvc standalone does not set up a SecurityContext, so authentication is null
        mockMvc.perform(get("/api/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("未授权访问"));
    }

    // ========== GET /api/auth/logout ==========

    @Test
    @DisplayName("GET /api/auth/logout - always returns success")
    void logout_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value("退出登录成功"));
    }
}
