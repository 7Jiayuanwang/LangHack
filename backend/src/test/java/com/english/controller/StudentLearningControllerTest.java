package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Text;
import com.english.entity.TextLearningRecord;
import com.english.entity.Unit;
import com.english.entity.User;
import com.english.entity.Class;
import com.english.exception.BusinessException;
import com.english.mapper.*;
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
 * StudentLearningController unit tests.
 *
 * Covers: dashboard, courses, course units, unit content, text detail,
 * record text view (validation), start reading, complete reading (IDOR),
 * mark text as completed, record learning, record word view, and
 * add to vocabulary.
 */
class StudentLearningControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StudentClassesService studentClassesService;

    @Mock
    private VocabularyBooksService vocabularyBooksService;

    @Mock
    private LearningRecordsService learningRecordsService;

    @Mock
    private UserService userService;

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private ScoreRecordService scoreRecordService;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private UnitMapper unitMapper;

    @Mock
    private WordMapper wordMapper;

    @Mock
    private TextMapper textMapper;

    @Mock
    private UnitWordMapper unitWordMapper;

    @Mock
    private TextLearningRecordMapper textLearningRecordMapper;

    @Mock
    private WordNetService wordNetService;

    @Mock
    private ArticleVersionService articleVersionService;

    @Mock
    private ClassService classService;

    @Mock
    private UnitService unitService;

    @InjectMocks
    private StudentLearningController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(TestUtils.authenticationFilter()).build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "student1", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("student1");
        mockUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(mockUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDashboardData_success() throws Exception {
        when(studentClassesService.countByStudentId(1L)).thenReturn(3);
        when(vocabularyBooksService.countByStudentId(1L)).thenReturn(50);
        when(learningRecordsService.getTodayStudyTime(1L)).thenReturn(120);
        when(studentClassesService.getStudentClassesWithDetails(1L)).thenReturn(List.of());
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of());
        when(studentClassesService.getStudentCoursesWithProgress(1L)).thenReturn(List.of());

        Map<String, Object> scoreStats = new HashMap<>();
        scoreStats.put("weightedAverageScore", 85.5);
        scoreStats.put("totalCount", 10);
        when(scoreRecordService.getStudentAverageScore(eq(1L), isNull(), isNull())).thenReturn(scoreStats);

        mockMvc.perform(get("/api/student/learning/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enrolledCourses").value(3))
                .andExpect(jsonPath("$.data.vocabularyCount").value(50));
    }

    @Test
    void getStudentCourses_success() throws Exception {
        List<Map<String, Object>> courses = List.of(
                Map.of("courseId", 1L, "courseName", "English 101"));
        when(studentClassesService.getStudentCoursesWithProgress(1L)).thenReturn(courses);

        mockMvc.perform(get("/api/student/learning/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courses").isArray());
    }

    @Test
    void getCourseUnits_success() throws Exception {
        // Mock assertStudentCanAccessCourse: student has class in course 5
        Class classInfo = new Class();
        classInfo.setId(100L);
        classInfo.setCourseId(5L);
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(classService.getClassById(100L)).thenReturn(classInfo);

        List<Map<String, Object>> units = List.of(
                Map.of("unitId", 1L, "unitName", "Unit 1"));
        when(learningRecordsService.getUnitsWithLearningStats(1L, 5L)).thenReturn(units);

        mockMvc.perform(get("/api/student/learning/courses/5/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.units").isArray());
    }

    @Test
    void getUnitContent_success() throws Exception {
        // Mock unitService for access check
        Unit unit = new Unit();
        unit.setId(10L);
        unit.setCourseId(5L);
        when(unitService.getUnitById(10L)).thenReturn(unit);

        // Mock assertStudentCanAccessCourse
        Class classInfo = new Class();
        classInfo.setId(100L);
        classInfo.setCourseId(5L);
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(classService.getClassById(100L)).thenReturn(classInfo);

        Map<String, Object> content = Map.of("texts", List.of(), "words", List.of());
        when(learningRecordsService.getUnitContentWithLearningStates(1L, 10L)).thenReturn(content);

        mockMvc.perform(get("/api/student/learning/units/10/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getTextDetail_success() throws Exception {
        // Mock assertStudentCanRecord: text exists, no unitId → access check passes
        Text text = new Text();
        text.setId(5L);
        when(textMapper.selectById(5L)).thenReturn(text);

        Map<String, Object> detail = Map.of("title", "Sample Text", "content", "Hello world");
        when(learningRecordsService.getTextDetailWithAnnotation(eq(1L), eq(5L), isNull()))
                .thenReturn(detail);

        mockMvc.perform(get("/api/student/learning/texts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Sample Text"));

        verify(articleVersionService).checkAndGenerateVersions(5L);
    }

    @Test
    void recordTextView_success() throws Exception {
        // Mock assertStudentCanRecord: text exists, no unitId → access check passes
        Text text = new Text();
        text.setId(5L);
        when(textMapper.selectById(5L)).thenReturn(text);

        Map<String, Object> params = new HashMap<>();
        params.put("textId", 5);
        params.put("duration", 120);

        mockMvc.perform(post("/api/student/learning/record/text-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("课文学习记录已保存"));

        verify(learningRecordsService).recordTextView(eq(1L), eq(5L), isNull(), eq(120));
    }

    @Test
    void recordTextView_missingTextId_returnsError() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("duration", 120);

        mockMvc.perform(post("/api/student/learning/record/text-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("textId不能为空"));
    }

    @Test
    void recordTextView_negativeDuration_returnsError() throws Exception {
        // Mock assertStudentCanRecord: text exists (called before duration validation)
        Text text = new Text();
        text.setId(5L);
        when(textMapper.selectById(5L)).thenReturn(text);

        Map<String, Object> params = new HashMap<>();
        params.put("textId", 5);
        params.put("duration", -10);

        mockMvc.perform(post("/api/student/learning/record/text-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("学习时长不能为负数"));
    }

    @Test
    void recordTextView_excessiveDuration_returnsError() throws Exception {
        // Mock assertStudentCanRecord: text exists (called before duration validation)
        Text text = new Text();
        text.setId(5L);
        when(textMapper.selectById(5L)).thenReturn(text);

        Map<String, Object> params = new HashMap<>();
        params.put("textId", 5);
        params.put("duration", 99999);

        mockMvc.perform(post("/api/student/learning/record/text-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("学习时长超过合理范围"));
    }

    @Test
    void startReading_success() throws Exception {
        // Mock assertStudentCanRecord: text exists, no unitId → access check passes
        Text text = new Text();
        text.setId(5L);
        when(textMapper.selectById(5L)).thenReturn(text);

        when(learningRecordsService.startReadingSession(1L, 5L, null)).thenReturn(100L);

        mockMvc.perform(post("/api/student/learning/texts/5/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    void completeReading_success() throws Exception {
        TextLearningRecord record = new TextLearningRecord();
        record.setId(100L);
        record.setStudentId(1L);
        when(textLearningRecordMapper.selectById(100L)).thenReturn(record);

        mockMvc.perform(post("/api/student/learning/records/100/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("阅读已完成"));

        verify(learningRecordsService).completeReadingSession(100L);
    }

    @Test
    void completeReading_idorProtection_returnsError() throws Exception {
        TextLearningRecord record = new TextLearningRecord();
        record.setId(100L);
        record.setStudentId(999L); // different student
        when(textLearningRecordMapper.selectById(100L)).thenReturn(record);

        mockMvc.perform(post("/api/student/learning/records/100/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权操作此记录"));
    }

    @Test
    void completeReading_notFound_returnsError() throws Exception {
        when(textLearningRecordMapper.selectById(99L)).thenReturn(null);

        mockMvc.perform(post("/api/student/learning/records/99/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("阅读记录不存在"));
    }

    @Test
    void markTextAsCompleted_success() throws Exception {
        Text text = new Text();
        text.setId(5L);
        when(textMapper.selectById(5L)).thenReturn(text);
        when(textLearningRecordMapper.selectOne(any())).thenReturn(null);
        when(textLearningRecordMapper.insert(any(TextLearningRecord.class))).thenReturn(1);

        mockMvc.perform(post("/api/student/learning/texts/5/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message").value("课文已标记为完成"));
    }

    @Test
    void markTextAsCompleted_textNotFound_returnsError() throws Exception {
        when(textMapper.selectById(99L)).thenReturn(null);

        mockMvc.perform(post("/api/student/learning/texts/99/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文不存在"));
    }

    @Test
    void recordLearning_success() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "word_study");
        params.put("wordId", 10);
        params.put("duration", 60);

        mockMvc.perform(post("/api/student/learning/record/learning")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("学习记录已保存"));

        verify(learningRecordsService).recordLearning(eq(1L), eq("word_study"), eq(10L), eq(60), isNull(), isNull());
    }

    @Test
    void recordLearning_missingType_returnsError() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("wordId", 10);

        mockMvc.perform(post("/api/student/learning/record/learning")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("type不能为空"));
    }

    @Test
    void recordWordView_success() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("wordId", 10);
        params.put("duration", 30);

        mockMvc.perform(post("/api/student/learning/record/word-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词学习记录已保存"));

        verify(learningRecordsService).recordWordView(eq(1L), eq(10L), isNull(), eq(30));
    }

    @Test
    void recordWordView_missingWordId_returnsError() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("duration", 30);

        mockMvc.perform(post("/api/student/learning/record/word-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("wordId不能为空"));
    }

    @Test
    void addToVocabulary_newWord_success() throws Exception {
        when(vocabularyBooksService.existsByStudentAndWord(1L, 10L)).thenReturn(false);

        Map<String, Object> params = Map.of("wordId", 10);

        mockMvc.perform(post("/api/student/learning/vocabulary/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词已添加到生词本"));

        verify(vocabularyBooksService).addToVocabularyWithLevel(1L, 10L, "NEW");
    }

    @Test
    void addToVocabulary_existingWord_success() throws Exception {
        when(vocabularyBooksService.existsByStudentAndWord(1L, 10L)).thenReturn(true);

        Map<String, Object> params = Map.of("wordId", 10);

        mockMvc.perform(post("/api/student/learning/vocabulary/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词已在生词本中"));
    }

    @Test
    void anyEndpoint_userNotFound_returnsBusinessException() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        mockMvc.perform(get("/api/student/learning/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }
}
