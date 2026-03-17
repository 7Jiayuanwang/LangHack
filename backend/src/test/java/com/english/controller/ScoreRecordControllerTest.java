package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Assignment;
import com.english.entity.ScoreRecord;
import com.english.entity.Unit;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
 * ScoreRecordController unit tests.
 *
 * Covers: record score, get student scores, get class scores (teacher),
 * IDOR protection on updateAssignmentScore, export class scores,
 * unitId filter (R42 uses unitService.getUnitById), BusinessException catch paths,
 * and statistics endpoint.
 */
class ScoreRecordControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ScoreRecordService scoreRecordService;

    @Mock
    private UserService userService;

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private ClassService classService;

    @Mock
    private StudentClassesService studentClassService;

    @Mock
    private UnitService unitService;

    @InjectMocks
    private ScoreRecordController scoreRecordController;

    private User teacherUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(scoreRecordController).addFilters(TestUtils.authenticationFilter()).build();

        // Default: TEACHER
        setUpSecurityContext("teacher1", "ROLE_TEACHER");

        teacherUser = new User();
        teacherUser.setId(1L);
        teacherUser.setUsername("teacher1");
        teacherUser.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        studentUser = new User();
        studentUser.setId(2L);
        studentUser.setUsername("student1");
        studentUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);
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

    private void setUpStudentContext() {
        setUpSecurityContext("student1", "ROLE_USER");
    }

    // ---- 1. Create score record (teacher) ----

    @Test
    void recordScore_teacherSuccess() throws Exception {
        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);

        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        when(classService.getClassById(10L)).thenReturn(classInfo);

        when(studentClassService.getClassIdsByStudentId(2L))
                .thenReturn(Collections.singletonList(10L));

        ScoreRecord record = new ScoreRecord();
        record.setStudentId(2L);
        record.setAssignmentId(1L);
        record.setScore(85);
        record.setScoreType("ASSIGNMENT");

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(record)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("分数记录成功"));

        verify(scoreRecordService).recordScore(any(ScoreRecord.class));
    }

    // ---- 2. Get student scores ----

    @Test
    void getStudentScores_teacherHasAccess() throws Exception {
        when(studentClassService.getClassIdsByStudentId(2L))
                .thenReturn(Collections.singletonList(10L));

        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        when(classService.getClassById(10L)).thenReturn(classInfo);

        List<Map<String, Object>> scores = new ArrayList<>();
        Map<String, Object> score = new HashMap<>();
        score.put("score", 90);
        scores.add(score);
        when(scoreRecordService.getStudentScores(eq(2L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(scores);

        mockMvc.perform(get("/api/scores/student/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].score").value(90));
    }

    // ---- 3. Get class scores (teacher validates own class) ----

    @Test
    void getStudentScores_teacherNoAccessToOtherClass() throws Exception {
        // Student belongs to class owned by another teacher
        when(studentClassService.getClassIdsByStudentId(2L))
                .thenReturn(Collections.singletonList(20L));

        com.english.entity.Class otherClass = new com.english.entity.Class();
        otherClass.setTeacherId(999L); // not our teacher
        when(classService.getClassById(20L)).thenReturn(otherClass);

        mockMvc.perform(get("/api/scores/student/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限查看该学生的分数记录"));
    }

    // ---- 4. Update assignment score: student IDOR protection (R43 fix) ----

    @Test
    void updateAssignmentScore_studentIdorProtection() throws Exception {
        setUpStudentContext();

        // Assignment is for class 10, but student is not in that class
        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);

        when(studentClassService.getClassIdsByStudentId(2L))
                .thenReturn(Collections.singletonList(20L)); // student only in class 20

        mockMvc.perform(post("/api/scores/assignment/1/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("您不属于该任务的班级"));

        verify(scoreRecordService, never()).updateAssignmentScore(anyLong(), anyLong());
    }

    @Test
    void updateAssignmentScore_studentSuccess() throws Exception {
        setUpStudentContext();

        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);

        when(studentClassService.getClassIdsByStudentId(2L))
                .thenReturn(Collections.singletonList(10L));

        mockMvc.perform(post("/api/scores/assignment/1/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("分数更新成功"));

        verify(scoreRecordService).updateAssignmentScore(1L, 2L);
    }

    // ---- 5. Export class scores ----

    @Test
    void exportClassScores_teacherSuccess() throws Exception {
        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        classInfo.setName("Class A");
        when(classService.getClassById(10L)).thenReturn(classInfo);

        List<Map<String, Object>> exportData = new ArrayList<>();
        when(scoreRecordService.exportClassScores(10L)).thenReturn(exportData);
        when(scoreRecordService.generateClassScoresExcel(exportData, 10L))
                .thenReturn(new byte[]{0x50, 0x4B}); // dummy xlsx bytes

        mockMvc.perform(get("/api/scores/class/10/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));
    }

    @Test
    void exportClassScores_teacherNoAccess() throws Exception {
        com.english.entity.Class otherClass = new com.english.entity.Class();
        otherClass.setTeacherId(999L);
        when(classService.getClassById(10L)).thenReturn(otherClass);

        mockMvc.perform(get("/api/scores/class/10/export"))
                .andExpect(status().isForbidden());
    }

    // ---- 6. Filter by unitId (R42 fix: uses unitService.getUnitById) ----

    @Test
    void getStatistics_filterByUnitId_teacherHasCourseAccess() throws Exception {
        Unit unit = new Unit();
        unit.setId(5L);
        unit.setCourseId(100L);
        when(unitService.getUnitById(5L)).thenReturn(unit);

        com.english.entity.Class teacherClass = new com.english.entity.Class();
        teacherClass.setCourseId(100L);
        teacherClass.setTeacherId(1L);
        when(classService.getClassesByTeacherId(1L))
                .thenReturn(Collections.singletonList(teacherClass));

        Map<String, Object> stats = new HashMap<>();
        stats.put("average", 82.5);
        when(scoreRecordService.getScoreStatistics(isNull(), eq(5L), isNull())).thenReturn(stats);

        mockMvc.perform(get("/api/scores/statistics")
                        .param("unitId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.average").value(82.5));

        verify(unitService).getUnitById(5L);
    }

    @Test
    void getStatistics_filterByUnitId_unitNotFound() throws Exception {
        when(unitService.getUnitById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/scores/statistics")
                        .param("unitId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单元不存在或无法关联课程"));
    }

    // ---- 7. BusinessException catch paths (R43/R45 fixes) ----

    @Test
    void recordScore_businessExceptionReturnsMessage() throws Exception {
        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);

        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        when(classService.getClassById(10L)).thenReturn(classInfo);

        when(studentClassService.getClassIdsByStudentId(2L))
                .thenReturn(Collections.singletonList(10L));

        doThrow(new BusinessException("重复分数记录"))
                .when(scoreRecordService).recordScore(any(ScoreRecord.class));

        ScoreRecord record = new ScoreRecord();
        record.setStudentId(2L);
        record.setAssignmentId(1L);
        record.setScore(85);

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(record)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("重复分数记录"));
    }

    @Test
    void updateAssignmentScore_businessExceptionReturnsMessage() throws Exception {
        setUpStudentContext();

        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);

        when(studentClassService.getClassIdsByStudentId(2L))
                .thenReturn(Collections.singletonList(10L));

        doThrow(new BusinessException("任务未完成，无法计算分数"))
                .when(scoreRecordService).updateAssignmentScore(1L, 2L);

        mockMvc.perform(post("/api/scores/assignment/1/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务未完成，无法计算分数"));
    }

    // ---- 8. Statistics endpoint ----

    @Test
    void getStatistics_teacherFilterByAssignmentId() throws Exception {
        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setClassId(10L);
        when(assignmentService.getById(1L)).thenReturn(assignment);

        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        when(classService.getClassById(10L)).thenReturn(classInfo);

        Map<String, Object> stats = new HashMap<>();
        stats.put("average", 75.0);
        stats.put("max", 100);
        stats.put("min", 40);
        when(scoreRecordService.getScoreStatistics(isNull(), isNull(), eq(1L))).thenReturn(stats);

        mockMvc.perform(get("/api/scores/statistics")
                        .param("assignmentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.average").value(75.0))
                .andExpect(jsonPath("$.data.max").value(100))
                .andExpect(jsonPath("$.data.min").value(40));
    }

    @Test
    void getStatistics_teacherWithNoFilterRejected() throws Exception {
        // Teacher must specify at least one of: assignmentId, courseId, unitId
        mockMvc.perform(get("/api/scores/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("必须指定任务、课程或单元"));
    }

    // ---- 9. Get my scores (student) ----

    @Test
    void getMyScores_studentSuccess() throws Exception {
        setUpStudentContext();

        List<Map<String, Object>> scores = new ArrayList<>();
        Map<String, Object> s = new HashMap<>();
        s.put("score", 92);
        scores.add(s);
        when(scoreRecordService.getStudentScores(eq(2L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(scores);

        mockMvc.perform(get("/api/scores/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].score").value(92));
    }

    // ---- 10. Teacher must have assignment -> class -> teacher chain ----

    @Test
    void recordScore_teacherMissingAssignmentId() throws Exception {
        ScoreRecord record = new ScoreRecord();
        record.setStudentId(2L);
        // no assignmentId set
        record.setScore(85);

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(record)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("教师记录分数必须关联一个任务"));
    }
}
