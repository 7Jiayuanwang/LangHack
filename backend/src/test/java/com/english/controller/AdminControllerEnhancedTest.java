package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.*;
import com.english.exception.BusinessException;
import com.english.service.*;
import com.english.service.impl.DataCleanupServiceImpl;
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

/**
 * Enhanced AdminController unit tests.
 * Covers: User CRUD (create/update/delete/roles), Course/Unit/Word CRUD,
 * Unit-Word association, stats, data cleanup, and cascade operations.
 */
@DisplayName("AdminController Enhanced Tests")
class AdminControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private UserService userService;
    @Mock private CourseService courseService;
    @Mock private WordService wordService;
    @Mock private ClassService classService;
    @Mock private StudentClassService studentClassService;
    @Mock private DataCleanupServiceImpl dataCleanupService;
    @Mock private UnitService unitService;
    @Mock private TextService textService;
    @Mock private UnitWordService unitWordService;
    @Mock private TextExerciseService textExerciseService;
    @Mock private ArticleVersionService articleVersionService;

    @InjectMocks
    private AdminController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("admin1", "ROLE_ADMIN");

        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin1");
        adminUser.setRole("ADMIN");
        when(userService.getUserByUsername("admin1")).thenReturn(adminUser);
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

    // ========== User Management ==========

    @Test
    @DisplayName("GET /api/admin/users - get users paginated success")
    void getUsers_success() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", new ArrayList<>());
        result.put("total", 0);
        when(userService.getUsersByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/users").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).getUsersByPage(anyMap());
    }

    @Test
    @DisplayName("GET /api/admin/users - enriched map records do not cause cast errors")
    void getUsers_withEnrichedMapRecords_success() throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 2L);
        row.put("username", "student1");
        row.put("role", "STUDENT");
        row.put("collegeName", "外国语学院");

        Map<String, Object> result = new HashMap<>();
        result.put("records", new ArrayList<>(Collections.singletonList(row)));
        result.put("total", 1);
        when(userService.getUsersByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/users").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].username").value("student1"))
                .andExpect(jsonPath("$.data.records[0].collegeName").value("外国语学院"));
    }

    @Test
    @DisplayName("POST /api/admin/users - create user with valid data success")
    void createUser_success() throws Exception {
        when(userService.getUserByUsername("newuser")).thenReturn(null);
        doNothing().when(userService).createUser(any(User.class));

        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "password123");
        newUser.put("role", "STUDENT");

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("用户创建成功"));
    }

    @Test
    @DisplayName("POST /api/admin/users - empty username returns error")
    void createUser_emptyUsername_returnsError() throws Exception {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));
    }

    @Test
    @DisplayName("POST /api/admin/users - password too short returns error")
    void createUser_shortPassword_returnsError() throws Exception {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "newuser");
        newUser.put("password", "12345");

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("密码长度不能少于 6 位"));
    }

    @Test
    @DisplayName("POST /api/admin/users - duplicate username returns error")
    void createUser_duplicateUsername_returnsError() throws Exception {
        User existing = new User();
        existing.setUsername("existinguser");
        when(userService.getUserByUsername("existinguser")).thenReturn(existing);

        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", "existinguser");
        newUser.put("password", "password123");

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @DisplayName("PUT /api/admin/users/{id} - update user success")
    void updateUser_success() throws Exception {
        User existingUser = new User();
        existingUser.setId(2L);
        existingUser.setUsername("user2");
        when(userService.getUserById(2L)).thenReturn(existingUser);
        doNothing().when(userService).updateUser(any(User.class));

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("username", "user2_updated");
        updateData.put("role", "TEACHER");

        mockMvc.perform(put("/api/admin/users/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("用户更新成功"));
    }

    @Test
    @DisplayName("PUT /api/admin/users/{id} - user not found returns error")
    void updateUser_notFound_returnsError() throws Exception {
        when(userService.getUserById(99L)).thenReturn(null);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("username", "updated");

        mockMvc.perform(put("/api/admin/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("DELETE /api/admin/users/{id} - delete user success")
    void deleteUser_success() throws Exception {
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("user2");
        when(userService.getUserById(2L)).thenReturn(targetUser);
        doNothing().when(userService).deleteUser(2L);

        mockMvc.perform(delete("/api/admin/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("用户删除成功"));

        verify(userService).deleteUser(2L);
    }

    @Test
    @DisplayName("DELETE /api/admin/users/{id} - cannot delete default admin")
    void deleteUser_defaultAdmin_returnsError() throws Exception {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        when(userService.getUserById(1L)).thenReturn(adminUser);

        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("不能删除默认管理员账户"));

        verify(userService, never()).deleteUser(1L);
    }

    @Test
    @DisplayName("GET /api/admin/users/roles - get available roles")
    void getUserRoles_success() throws Exception {
        mockMvc.perform(get("/api/admin/users/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("STUDENT"));
    }

    // ========== Stats ==========

    @Test
    @DisplayName("GET /api/admin/stats - get all stats success")
    void getStats_success() throws Exception {
        when(userService.getUserStats()).thenReturn(Map.of("totalUsers", 100));
        when(courseService.getCourseStats()).thenReturn(Map.of("totalCourses", 10));
        when(wordService.getWordStats()).thenReturn(Map.of("totalWords", 500));
        when(classService.getClassStats()).thenReturn(Map.of("totalClasses", 5));

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(100))
                .andExpect(jsonPath("$.data.totalCourses").value(10));
    }

    @Test
    @DisplayName("GET /api/admin/stats - service exception returns error")
    void getStats_serviceException_returnsError() throws Exception {
        when(userService.getUserStats()).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取统计信息失败，请稍后重试"));
    }

    // ========== Courses ==========

    @Test
    @DisplayName("GET /api/admin/courses - get courses paginated")
    void getCourses_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(courseService.getCoursesByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/courses").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== Units ==========

    @Test
    @DisplayName("GET /api/admin/units - get units paginated")
    void getUnits_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(unitService.getUnitsByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/units").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/admin/units - create unit success")
    void createUnit_success() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setDeleted(0);
        when(courseService.getCourseById(1L)).thenReturn(course);
        doNothing().when(unitService).createUnit(any(Unit.class));

        Map<String, Object> unitData = new HashMap<>();
        unitData.put("courseId", 1);
        unitData.put("name", "Unit 1");
        unitData.put("orderNum", 1);

        mockMvc.perform(post("/api/admin/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unitData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单元创建成功"));

        verify(unitService).createUnit(any(Unit.class));
    }

    @Test
    @DisplayName("DELETE /api/admin/units/{id} - delete unit success")
    void deleteUnit_success() throws Exception {
        doNothing().when(unitService).deleteUnit(1L);

        mockMvc.perform(delete("/api/admin/units/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单元删除成功"));

        verify(unitService).deleteUnit(1L);
    }

    // ========== Words ==========

    @Test
    @DisplayName("GET /api/admin/words - get words paginated")
    void getWords_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(wordService.getWordsByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/words").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/admin/words - create word success")
    void createWord_success() throws Exception {
        when(wordService.createWord(any(Word.class))).thenReturn(true);

        Map<String, Object> wordData = new HashMap<>();
        wordData.put("word", "hello");
        wordData.put("definition", "a greeting");

        mockMvc.perform(post("/api/admin/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wordData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词创建成功"));
    }

    @Test
    @DisplayName("POST /api/admin/words - missing definition returns error")
    void createWord_missingFields_returnsError() throws Exception {
        Map<String, Object> wordData = Map.of("word", "hello");

        mockMvc.perform(post("/api/admin/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wordData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("word 和 definition 为必填字段"));
    }

    @Test
    @DisplayName("DELETE /api/admin/words/{id} - delete word success")
    void deleteWord_success() throws Exception {
        Word existingWord = new Word();
        existingWord.setId(1L);
        when(wordService.getWordById(1L)).thenReturn(existingWord);
        when(wordService.deleteWord(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/admin/words/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词删除成功"));

        verify(wordService).deleteWord(1L);
    }

    // ========== Unit-Word Association ==========

    @Test
    @DisplayName("POST /api/admin/units/{unitId}/words/{wordId} - add word to unit success")
    void addWordToUnit_success() throws Exception {
        when(unitWordService.exists(1L, 10L)).thenReturn(false);
        doNothing().when(unitWordService).addToUnit(1L, 10L);

        mockMvc.perform(post("/api/admin/units/1/words/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词添加到单元成功"));

        verify(unitWordService).addToUnit(1L, 10L);
    }

    @Test
    @DisplayName("POST /api/admin/units/{unitId}/words/{wordId} - word already in unit returns error")
    void addWordToUnit_alreadyExists_returnsError() throws Exception {
        when(unitWordService.exists(1L, 10L)).thenReturn(true);

        mockMvc.perform(post("/api/admin/units/1/words/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该单词已在单元中"));

        verify(unitWordService, never()).addToUnit(anyLong(), anyLong());
    }

    @Test
    @DisplayName("DELETE /api/admin/units/{unitId}/words/{wordId} - remove word from unit")
    void removeWordFromUnit_success() throws Exception {
        doNothing().when(unitWordService).removeFromUnit(1L, 10L);

        mockMvc.perform(delete("/api/admin/units/1/words/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词从单元移除成功"));

        verify(unitWordService).removeFromUnit(1L, 10L);
    }

    // Data cleanup tests moved to OpsControllerTest (endpoints migrated to /api/ops/)
}
