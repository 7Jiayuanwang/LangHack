package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.*;
import com.english.exception.BusinessException;
import com.english.service.*;
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

@DisplayName("ScoreRecordController Enhanced Tests")
class ScoreRecordControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ScoreRecordService scoreRecordService;
    @Mock private UserService userService;
    @Mock private AssignmentService assignmentService;
    @Mock private ClassService classService;
    @Mock private StudentClassesService studentClassService;
    @Mock private UnitService unitService;

    @InjectMocks
    private ScoreRecordController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        // Default: admin context
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

    // ========== POST /api/scores ==========

    @Test
    @DisplayName("POST /api/scores - admin records score success")
    void recordScore_admin_success() throws Exception {
        doNothing().when(scoreRecordService).recordScore(any(ScoreRecord.class));

        ScoreRecord score = new ScoreRecord();
        score.setStudentId(2L);
        score.setAssignmentId(1L);

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(score)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("分数记录成功"));
    }

    @Test
    @DisplayName("POST /api/scores - teacher validates class ownership")
    void recordScore_teacher_classValidation() throws Exception {
        setUpSecurityContext("teacher1", "ROLE_TEACHER");
        User teacher = new User();
        teacher.setId(10L);
        teacher.setUsername("teacher1");
        teacher.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacher);

        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(5L);
        when(assignmentService.getById(1L)).thenReturn(assignment);

        com.english.entity.Class classEntity = new com.english.entity.Class();
        classEntity.setId(5L);
        classEntity.setTeacherId(10L);
        when(classService.getClassById(5L)).thenReturn(classEntity);

        when(studentClassService.getClassIdsByStudentId(2L)).thenReturn(List.of(5L));

        doNothing().when(scoreRecordService).recordScore(any(ScoreRecord.class));

        ScoreRecord score = new ScoreRecord();
        score.setStudentId(2L);
        score.setAssignmentId(1L);

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(score)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/scores - teacher no assignment returns error")
    void recordScore_teacher_noAssignment() throws Exception {
        setUpSecurityContext("teacher1", "ROLE_TEACHER");
        User teacher = new User();
        teacher.setId(10L);
        teacher.setUsername("teacher1");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacher);

        ScoreRecord score = new ScoreRecord();
        score.setStudentId(2L);
        // no assignmentId

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(score)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("教师记录分数必须关联一个任务"));
    }

    // ========== GET /api/scores/student/{studentId} ==========

    @Test
    @DisplayName("GET /api/scores/student/{id} - admin success")
    void getStudentScores_admin_success() throws Exception {
        List<Map<String, Object>> scores = List.of(Map.of("score", 90));
        when(scoreRecordService.getStudentScores(eq(2L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(scores);

        mockMvc.perform(get("/api/scores/student/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== GET /api/scores/my ==========

    @Test
    @DisplayName("GET /api/scores/my - student gets own scores")
    void getMyScores_success() throws Exception {
        setUpSecurityContext("student1", "ROLE_USER");
        User student = new User();
        student.setId(5L);
        student.setUsername("student1");
        when(userService.getUserByUsername("student1")).thenReturn(student);

        List<Map<String, Object>> scores = List.of(Map.of("score", 85));
        when(scoreRecordService.getStudentScores(eq(5L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(scores);

        mockMvc.perform(get("/api/scores/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== GET /api/scores/student/{id}/average ==========

    @Test
    @DisplayName("GET /api/scores/student/{id}/average - admin success")
    void getStudentAverageScore_success() throws Exception {
        Map<String, Object> avg = Map.of("average", 87.5);
        when(scoreRecordService.getStudentAverageScore(eq(2L), isNull(), isNull()))
                .thenReturn(avg);

        mockMvc.perform(get("/api/scores/student/2/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== GET /api/scores/statistics ==========

    @Test
    @DisplayName("GET /api/scores/statistics - admin with assignmentId")
    void getScoreStatistics_admin_success() throws Exception {
        Map<String, Object> stats = Map.of("average", 80, "count", 10);
        when(scoreRecordService.getScoreStatistics(isNull(), isNull(), eq(1L)))
                .thenReturn(stats);

        mockMvc.perform(get("/api/scores/statistics").param("assignmentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/scores/statistics - teacher no params returns error")
    void getScoreStatistics_teacher_noParams() throws Exception {
        setUpSecurityContext("teacher1", "ROLE_TEACHER");
        User teacher = new User();
        teacher.setId(10L);
        teacher.setUsername("teacher1");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacher);

        mockMvc.perform(get("/api/scores/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("必须指定任务、课程或单元"));
    }

    // ========== POST /api/scores/assignment/{id}/update ==========

    @Test
    @DisplayName("POST /api/scores/assignment/{id}/update - success")
    void updateAssignmentScore_success() throws Exception {
        setUpSecurityContext("student1", "ROLE_USER");
        User student = new User();
        student.setId(5L);
        student.setUsername("student1");
        when(userService.getUserByUsername("student1")).thenReturn(student);

        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);
        when(studentClassService.getClassIdsByStudentId(5L)).thenReturn(List.of(10L));
        doNothing().when(scoreRecordService).updateAssignmentScore(1L, 5L);

        mockMvc.perform(post("/api/scores/assignment/1/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("分数更新成功"));
    }

    @Test
    @DisplayName("POST /api/scores/assignment/{id}/update - assignment not found")
    void updateAssignmentScore_notFound() throws Exception {
        setUpSecurityContext("student1", "ROLE_USER");
        User student = new User();
        student.setId(5L);
        student.setUsername("student1");
        when(userService.getUserByUsername("student1")).thenReturn(student);

        when(assignmentService.getById(99L)).thenReturn(null);

        mockMvc.perform(post("/api/scores/assignment/99/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    @Test
    @DisplayName("POST /api/scores/assignment/{id}/update - student not in class")
    void updateAssignmentScore_notInClass() throws Exception {
        setUpSecurityContext("student1", "ROLE_USER");
        User student = new User();
        student.setId(5L);
        student.setUsername("student1");
        when(userService.getUserByUsername("student1")).thenReturn(student);

        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);
        when(studentClassService.getClassIdsByStudentId(5L)).thenReturn(List.of(99L)); // different class

        mockMvc.perform(post("/api/scores/assignment/1/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("您不属于该任务的班级"));
    }
}
