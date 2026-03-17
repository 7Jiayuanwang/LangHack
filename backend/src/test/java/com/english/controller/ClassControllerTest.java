package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.service.ClassService;
import com.english.service.StudentClassService;
import com.english.service.UserService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClassControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ClassService classService;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ClassController classController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(classController).addFilters(TestUtils.authenticationFilter()).build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/classes ==========

    @Test
    @DisplayName("GET /api/classes - ADMIN sees all classes")
    void getClasses_admin_seesAllClasses() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of(createMockClass(1L, "Class A", 2L)));
        result.put("total", 1L);

        when(classService.getClassesByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/classes")
                        .principal(auth)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("GET /api/classes - TEACHER only sees own classes (teacherId auto-set)")
    void getClasses_teacher_seesOnlyOwnClasses() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0L);

        when(classService.getClassesByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/classes")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(classService).getClassesByPage(paramsCaptor.capture());
        // Teacher's ID should be forced as teacherId
        Assertions.assertEquals(10L, paramsCaptor.getValue().get("teacherId"));
    }

    @Test
    @DisplayName("GET /api/classes - with keyword search")
    void getClasses_withKeyword() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0L);

        when(classService.getClassesByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/classes")
                        .principal(auth)
                        .param("keyword", "Advanced"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(classService).getClassesByPage(paramsCaptor.capture());
        Assertions.assertEquals("Advanced", paramsCaptor.getValue().get("keyword"));
    }

    // ========== GET /api/classes/{id} ==========

    @Test
    @DisplayName("GET /api/classes/{id} - ADMIN can view any class")
    void getClassById_admin_canViewAny() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        // Note: ClassController L79 checks userService.getUserByUsername().getRole() — DB role without prefix
        // But the condition on L82 compares with "ROLE_ADMIN" — so for ADMIN, role from DB is "ADMIN",
        // and "ROLE_ADMIN".equals("ADMIN") is FALSE, so it enters the non-admin block.
        // This is actually a bug in the controller, but we test the existing behavior.
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        Class classInfo = createMockClass(1L, "Class A", 2L);
        when(classService.getClassById(1L)).thenReturn(classInfo);

        mockMvc.perform(get("/api/classes/1")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Class A"));
    }

    @Test
    @DisplayName("GET /api/classes/{id} - non-existent class returns error")
    void getClassById_nonExistent_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);
        when(classService.getClassById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/classes/999")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("班级不存在"));
    }

    // ========== POST /api/classes ==========

    @Test
    @DisplayName("POST /api/classes - TEACHER creates class with own teacherId")
    void createClass_teacher_setsOwnTeacherId() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        Class classInfo = new Class();
        classInfo.setName("New Class");
        classInfo.setCourseId(1L);

        doNothing().when(classService).createClass(any(Class.class));

        mockMvc.perform(post("/api/classes")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        ArgumentCaptor<Class> classCaptor = ArgumentCaptor.forClass(Class.class);
        verify(classService).createClass(classCaptor.capture());
        Assertions.assertEquals(10L, classCaptor.getValue().getTeacherId());
    }

    @Test
    @DisplayName("POST /api/classes - ADMIN creates class with specified teacherId")
    void createClass_admin_keepsSpecifiedTeacherId() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        Class classInfo = new Class();
        classInfo.setName("New Class");
        classInfo.setCourseId(1L);
        classInfo.setTeacherId(20L);

        doNothing().when(classService).createClass(any(Class.class));

        mockMvc.perform(post("/api/classes")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Class> classCaptor = ArgumentCaptor.forClass(Class.class);
        verify(classService).createClass(classCaptor.capture());
        // ADMIN does not override teacherId
        Assertions.assertEquals(20L, classCaptor.getValue().getTeacherId());
    }

    // ========== PUT /api/classes/{id} ==========

    @Test
    @DisplayName("PUT /api/classes/{id} - ADMIN updates class")
    void updateClass_admin_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        Class classInfo = new Class();
        classInfo.setName("Updated Class");
        classInfo.setDescription("Updated description");

        doNothing().when(classService).updateClass(any(Class.class));

        mockMvc.perform(put("/api/classes/1")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        ArgumentCaptor<Class> classCaptor = ArgumentCaptor.forClass(Class.class);
        verify(classService).updateClass(classCaptor.capture());
        Assertions.assertEquals(1L, classCaptor.getValue().getId());
    }

    @Test
    @DisplayName("PUT /api/classes/{id} - TEACHER without permission returns error")
    void updateClass_teacherNoPermission_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);
        when(classService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        Class classInfo = new Class();
        classInfo.setName("Updated Class");

        mockMvc.perform(put("/api/classes/1")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限修改此班级"));

        verify(classService, never()).updateClass(any(Class.class));
    }

    // ========== DELETE /api/classes/{id} ==========

    @Test
    @DisplayName("DELETE /api/classes/{id} - ADMIN deletes class")
    void deleteClass_admin_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        doNothing().when(classService).deleteClass(1L);

        mockMvc.perform(delete("/api/classes/1")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(classService).deleteClass(1L);
    }

    @Test
    @DisplayName("DELETE /api/classes/{id} - TEACHER without permission returns error")
    void deleteClass_teacherNoPermission_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);
        when(classService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        mockMvc.perform(delete("/api/classes/1")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限删除此班级"));

        verify(classService, never()).deleteClass(anyLong());
    }

    // ========== POST /api/classes/join ==========

    @Test
    @DisplayName("POST /api/classes/join - student joins class successfully")
    void joinClass_student_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("student1", "STUDENT");
        User studentUser = TestHelper.createMockUser(20L, "student1", "STUDENT");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);

        doNothing().when(studentClassService).joinClass(20L, 1L);

        Map<String, Long> params = new HashMap<>();
        params.put("classId", 1L);

        mockMvc.perform(post("/api/classes/join")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(studentClassService).joinClass(20L, 1L);
    }

    // ========== POST /api/classes/leave ==========

    @Test
    @DisplayName("POST /api/classes/leave - student leaves class successfully")
    void leaveClass_student_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("student1", "STUDENT");
        User studentUser = TestHelper.createMockUser(20L, "student1", "STUDENT");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);

        doNothing().when(studentClassService).leaveClass(20L, 1L);

        Map<String, Long> params = new HashMap<>();
        params.put("classId", 1L);

        mockMvc.perform(post("/api/classes/leave")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(studentClassService).leaveClass(20L, 1L);
    }

    // ========== GET /api/classes/{classId}/students ==========

    @Test
    @DisplayName("GET /api/classes/{classId}/students - ADMIN gets student list")
    void getStudentsByClassId_admin_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        List<StudentClass> students = List.of(new StudentClass(), new StudentClass());
        when(studentClassService.getStudentsByClassId(1L)).thenReturn(students);

        mockMvc.perform(get("/api/classes/1/students")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/classes/{classId}/students - TEACHER without permission returns error")
    void getStudentsByClassId_teacherNoPermission_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);
        when(classService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/classes/1/students")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限查看此班级的学生"));
    }

    // ========== POST /api/classes/{classId}/students ==========

    @Test
    @DisplayName("POST /api/classes/{classId}/students - TEACHER adds student")
    void addStudentToClass_teacher_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        // Controller checks teacher permission first, then calls joinClass(studentId, classId)
        when(classService.validateTeacherPermission(10L, 1L)).thenReturn(true);
        doNothing().when(studentClassService).joinClass(20L, 1L);

        Map<String, Long> params = new HashMap<>();
        params.put("studentId", 20L);

        mockMvc.perform(post("/api/classes/1/students")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(classService).validateTeacherPermission(10L, 1L);
        verify(studentClassService).joinClass(20L, 1L);
    }

    // ========== DELETE /api/classes/{classId}/students/{studentId} ==========

    @Test
    @DisplayName("DELETE /api/classes/{classId}/students/{studentId} - TEACHER removes student")
    void removeStudentFromClass_teacher_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        // Controller checks teacher permission first, then calls leaveClass(studentId, classId)
        when(classService.validateTeacherPermission(10L, 1L)).thenReturn(true);
        doNothing().when(studentClassService).leaveClass(20L, 1L);

        mockMvc.perform(delete("/api/classes/1/students/20")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(classService).validateTeacherPermission(10L, 1L);
        verify(studentClassService).leaveClass(20L, 1L);
    }

    // ========== POST /api/classes/{classId}/students/batch ==========

    @Test
    @DisplayName("POST /api/classes/{classId}/students/batch - TEACHER batch adds students")
    void batchAddStudentsToClass_teacher_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);
        when(classService.validateTeacherPermission(10L, 1L)).thenReturn(true);

        doNothing().when(studentClassService).batchAddStudentsToClass(eq(1L), anyList());

        Map<String, List<Long>> params = new HashMap<>();
        params.put("studentIds", List.of(20L, 21L, 22L));

        mockMvc.perform(post("/api/classes/1/students/batch")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(studentClassService).batchAddStudentsToClass(eq(1L), eq(List.of(20L, 21L, 22L)));
    }

    @Test
    @DisplayName("POST /api/classes/{classId}/students/batch - TEACHER without permission returns error")
    void batchAddStudentsToClass_teacherNoPermission_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("teacher1", "TEACHER");
        User teacherUser = TestHelper.createMockUser(10L, "teacher1", "TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);
        when(classService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        Map<String, List<Long>> params = new HashMap<>();
        params.put("studentIds", List.of(20L, 21L));

        mockMvc.perform(post("/api/classes/1/students/batch")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限管理此班级"));

        verify(studentClassService, never()).batchAddStudentsToClass(anyLong(), anyList());
    }

    // ========== GET /api/classes/search ==========

    @Test
    @DisplayName("GET /api/classes/search - returns matching classes")
    void searchClasses_returnsResults() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        List<Class> classes = List.of(createMockClass(1L, "Advanced English", 2L));
        when(classService.searchClasses("Advanced")).thenReturn(classes);

        mockMvc.perform(get("/api/classes/search")
                        .principal(auth)
                        .param("keyword", "Advanced"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ========== GET /api/classes/stats ==========

    @Test
    @DisplayName("GET /api/classes/stats - returns class statistics")
    void getClassStats_returnsStats() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClasses", 5);
        stats.put("totalStudents", 100);

        when(classService.getClassStats()).thenReturn(stats);

        mockMvc.perform(get("/api/classes/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalClasses").value(5));
    }

    // ========== Helper Methods ==========

    private Class createMockClass(Long id, String name, Long teacherId) {
        Class classInfo = new Class();
        classInfo.setId(id);
        classInfo.setName(name);
        classInfo.setTeacherId(teacherId);
        return classInfo;
    }
}
