package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Unit;
import com.english.entity.User;
import com.english.entity.Word;
import com.english.exception.BusinessException;
import com.english.service.*;
import com.english.service.impl.DataCleanupServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
 * AdminController unit tests.
 *
 * Covers: user CRUD (list, create, update, delete, roles), stats,
 * course list, unit CRUD, word CRUD, add/remove word from unit,
 * data cleanup, and error paths.
 */
class AdminControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @Mock
    private CourseService courseService;

    @Mock
    private WordService wordService;

    @Mock
    private ClassService classService;

    @Mock
    private com.english.service.StudentClassService studentClassService;

    @Mock
    private DataCleanupServiceImpl dataCleanupService;

    @Mock
    private UnitService unitService;

    @Mock
    private TextService textService;

    @Mock
    private UnitWordService unitWordService;

    @Mock
    private TextExerciseService textExerciseService;

    @Mock
    private ArticleVersionService articleVersionService;

    @InjectMocks
    private AdminController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin1", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

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

    // ========== User Management ==========

    @Test
    void getUsers_success() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", new ArrayList<User>());
        result.put("total", 0);
        when(userService.getUsersByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createUser_success() throws Exception {
        when(userService.getUserByUsername("newuser")).thenReturn(null);

        // Use Map for clarity (password is WRITE_ONLY on User entity)
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
    void createUser_emptyUsername_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
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
    void createUser_shortPassword_returnsError() throws Exception {
        // Use Map for clarity (password is WRITE_ONLY on User entity)
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
    void createUser_duplicateUsername_returnsError() throws Exception {
        User existing = new User();
        existing.setUsername("existinguser");
        when(userService.getUserByUsername("existinguser")).thenReturn(existing);

        // Use Map for clarity (password is WRITE_ONLY on User entity)
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
    void updateUser_success() throws Exception {
        User existingUser = new User();
        existingUser.setId(2L);
        existingUser.setUsername("user2");
        when(userService.getUserById(2L)).thenReturn(existingUser);

        // Use Map for clarity (password is WRITE_ONLY on User entity)
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("username", "user2_updated");
        updateData.put("password", "newpassword123");
        updateData.put("role", "TEACHER");

        mockMvc.perform(put("/api/admin/users/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("用户更新成功"));
    }

    @Test
    void updateUser_notFound_returnsError() throws Exception {
        when(userService.getUserById(99L)).thenReturn(null);

        User updateData = new User();
        updateData.setUsername("updated");

        mockMvc.perform(put("/api/admin/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void deleteUser_success() throws Exception {
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("user2");
        when(userService.getUserById(2L)).thenReturn(targetUser);

        mockMvc.perform(delete("/api/admin/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("用户删除成功"));

        verify(userService).deleteUser(2L);
    }

    @Test
    void deleteUser_defaultAdmin_returnsError() throws Exception {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        when(userService.getUserById(1L)).thenReturn(adminUser);

        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("不能删除默认管理员账户"));
    }

    @Test
    void deleteUser_notFound_returnsError() throws Exception {
        when(userService.getUserById(99L)).thenReturn(null);

        mockMvc.perform(delete("/api/admin/users/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void getUserRoles_success() throws Exception {
        mockMvc.perform(get("/api/admin/users/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("STUDENT"));
    }

    // ========== Stats ==========

    @Test
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
    void getStats_serviceException_returnsError() throws Exception {
        when(userService.getUserStats()).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取统计信息失败，请稍后重试"));
    }

    // ========== Courses ==========

    @Test
    void getCourses_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(courseService.getCoursesByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/courses")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== Units ==========

    @Test
    void getUnits_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(unitService.getUnitsByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/units")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createUnit_success() throws Exception {
        com.english.entity.Course course = new com.english.entity.Course();
        course.setId(1L);
        course.setDeleted(0);
        when(courseService.getCourseById(1L)).thenReturn(course);

        Map<String, Object> unitData = new HashMap<>();
        unitData.put("courseId", 1);
        unitData.put("name", "Unit 1");
        unitData.put("description", "First unit");
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
    void createUnit_missingFields_returnsError() throws Exception {
        Map<String, Object> unitData = Map.of("name", "Unit 1");
        // missing courseId

        mockMvc.perform(post("/api/admin/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unitData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("courseId 和 name 为必填字段"));
    }

    @Test
    void deleteUnit_success() throws Exception {
        mockMvc.perform(delete("/api/admin/units/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单元删除成功"));

        verify(unitService).deleteUnit(1L);
    }

    // ========== Words ==========

    @Test
    void getWords_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(wordService.getWordsByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/admin/words")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createWord_success() throws Exception {
        when(wordService.createWord(any(Word.class))).thenReturn(true);

        Map<String, Object> wordData = new HashMap<>();
        wordData.put("word", "hello");
        wordData.put("definition", "a greeting");
        wordData.put("pronunciation", "/helo/");

        mockMvc.perform(post("/api/admin/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wordData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词创建成功"));
    }

    @Test
    void createWord_missingFields_returnsError() throws Exception {
        Map<String, Object> wordData = Map.of("word", "hello");
        // missing definition

        mockMvc.perform(post("/api/admin/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wordData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("word 和 definition 为必填字段"));
    }

    @Test
    void updateWord_success() throws Exception {
        Word existingWord = new Word();
        existingWord.setId(1L);
        when(wordService.getWordById(1L)).thenReturn(existingWord);

        Map<String, Object> wordData = new HashMap<>();
        wordData.put("word", "hello");
        wordData.put("definition", "updated greeting");

        mockMvc.perform(put("/api/admin/words/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wordData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词更新成功"));
    }

    @Test
    void updateWord_notFound_returnsError() throws Exception {
        when(wordService.getWordById(99L)).thenReturn(null);

        Map<String, Object> wordData = new HashMap<>();
        wordData.put("word", "hello");
        wordData.put("definition", "greeting");

        mockMvc.perform(put("/api/admin/words/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wordData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不存在"));
    }

    @Test
    void deleteWord_success() throws Exception {
        Word existingWord = new Word();
        existingWord.setId(1L);
        when(wordService.getWordById(1L)).thenReturn(existingWord);

        mockMvc.perform(delete("/api/admin/words/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词删除成功"));

        verify(wordService).deleteWord(1L);
    }

    @Test
    void deleteWord_notFound_returnsError() throws Exception {
        when(wordService.getWordById(99L)).thenReturn(null);

        mockMvc.perform(delete("/api/admin/words/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不存在"));
    }

    // ========== Unit-Word ==========

    @Test
    void addWordToUnit_success() throws Exception {
        when(unitWordService.exists(1L, 10L)).thenReturn(false);

        mockMvc.perform(post("/api/admin/units/1/words/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词添加到单元成功"));

        verify(unitWordService).addToUnit(1L, 10L);
    }

    @Test
    void addWordToUnit_alreadyExists_returnsError() throws Exception {
        when(unitWordService.exists(1L, 10L)).thenReturn(true);

        mockMvc.perform(post("/api/admin/units/1/words/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该单词已在单元中"));
    }

    @Test
    void removeWordFromUnit_success() throws Exception {
        mockMvc.perform(delete("/api/admin/units/1/words/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词从单元移除成功"));

        verify(unitWordService).removeFromUnit(1L, 10L);
    }

    // Data cleanup tests moved to OpsControllerTest (endpoints migrated to /api/ops/)
}
