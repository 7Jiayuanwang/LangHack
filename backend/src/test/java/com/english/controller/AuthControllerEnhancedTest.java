package com.english.controller;

import com.english.test.TestUtils;
import com.english.dto.LoginRequest;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.TokenBlacklistService;
import com.english.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AuthController Enhanced Tests")
class AuthControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private UserService userService;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private AuthController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("user1", "ROLE_USER");

        User user = new User();
        user.setId(1L);
        user.setUsername("user1");
        user.setRole("USER");
        when(userService.getUserByUsername("user1")).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpSecurityContext(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ========== POST /api/auth/login ==========

    @Test
    @DisplayName("POST /api/auth/login - success")
    void login_success() throws Exception {
        Map<String, Object> loginResult = Map.of("token", "jwt-token", "user", Map.of("id", 1));
        when(userService.login(any(LoginRequest.class))).thenReturn(loginResult);

        LoginRequest request = new LoginRequest();
        request.setUsername("user1");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/auth/login - invalid credentials")
    void login_invalidCredentials() throws Exception {
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException("用户名或密码错误"));

        LoginRequest request = new LoginRequest();
        request.setUsername("user1");
        request.setPassword("wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("POST /api/auth/login - unexpected exception")
    void login_unexpectedException() throws Exception {
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("DB error"));

        LoginRequest request = new LoginRequest();
        request.setUsername("user1");
        request.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录失败，请稍后重试"));
    }

    // ========== POST /api/auth/register ==========

    @Test
    @DisplayName("POST /api/auth/register - success")
    void register_success() throws Exception {
        doNothing().when(userService).createUser(any(User.class));

        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("注册成功，您已注册为普通用户"));
    }

    @Test
    @DisplayName("POST /api/auth/register - empty username")
    void register_emptyUsername() throws Exception {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));
    }

    @Test
    @DisplayName("POST /api/auth/register - username too short")
    void register_usernameTooShort() throws Exception {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "ab");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名长度须在 3~50 个字符之间"));
    }

    @Test
    @DisplayName("POST /api/auth/register - invalid username characters")
    void register_invalidUsernameChars() throws Exception {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "user name!");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名只能包含字母、数字及 _ - . @ 符号"));
    }

    @Test
    @DisplayName("POST /api/auth/register - short password")
    void register_shortPassword() throws Exception {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "validuser");
        newUser.put("password", "12345");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("密码长度不能少于 6 位"));
    }

    @Test
    @DisplayName("POST /api/auth/register - invalid email")
    void register_invalidEmail() throws Exception {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "validuser");
        newUser.put("password", "password123");
        newUser.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("邮箱格式无效或长度超过100字符"));
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate username")
    void register_duplicateUsername() throws Exception {
        doThrow(new BusinessException("用户名已存在")).when(userService).createUser(any(User.class));

        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "existinguser");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    // ========== GET /api/auth/userinfo ==========

    @Test
    @DisplayName("GET /api/auth/userinfo - success")
    void getUserInfo_success() throws Exception {
        mockMvc.perform(get("/api/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/auth/userinfo - user not found")
    void getUserInfo_userNotFound() throws Exception {
        setUpSecurityContext("unknown", "ROLE_USER");
        when(userService.getUserByUsername("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    // ========== GET /api/auth/search ==========

    @Test
    @DisplayName("GET /api/auth/search - success")
    void searchUsers_success() throws Exception {
        setUpSecurityContext("admin1", "ROLE_ADMIN");
        List<User> users = new ArrayList<>();
        User u = new User();
        u.setId(2L);
        u.setUsername("found");
        users.add(u);
        when(userService.searchUsers("found", null)).thenReturn(users);

        mockMvc.perform(get("/api/auth/search").param("keyword", "found"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/auth/search - empty keyword")
    void searchUsers_emptyKeyword() throws Exception {
        mockMvc.perform(get("/api/auth/search").param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("搜索关键词不能为空"));
    }

    // ========== POST /api/auth/logout ==========

    @Test
    @DisplayName("POST /api/auth/logout - success")
    void logout_success() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("退出登录成功"));

        verify(tokenBlacklistService).blacklistToken("mock-jwt-token");
    }
}
