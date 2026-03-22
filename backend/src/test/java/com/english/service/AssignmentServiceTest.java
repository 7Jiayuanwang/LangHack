package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.exception.BusinessException;
import com.english.mapper.*;
import com.english.service.impl.AssignmentServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AssignmentServiceImpl.
 *
 * Key observations from source code:
 * - AssignmentServiceImpl extends ServiceImpl<AssignmentMapper, Assignment>
 * - Assignment entity HAS @TableLogic on deleted field (soft delete)
 * - deleteAssignment() uses UpdateWrapper to set deleted=1 (NOT removeById/deleteById)
 * - createAssignment() validates unitId not null, throws BusinessException, uses save()
 * - updateAssignment() uses updateById() (inherited from ServiceImpl)
 * - getAssignmentById() uses custom mapper method selectAssignmentWithDetails()
 * - validateTeacherPermission() uses getActiveAssignment() -> getById() (inherited)
 * - isAssignmentInClasses() uses getActiveAssignment() -> getById() (inherited)
 * - getAssignmentDetailWithProgress() is very complex with many internal dependency calls
 *
 * Dependencies (all @Autowired in AssignmentServiceImpl):
 * - AssignmentMapper (also inherited as baseMapper in ServiceImpl)
 * - TextExerciseMapper, UnitWordMapper, VocabularyBookMapper, LearningRecordMapper
 * - TextMapper, ClassMapper, UnitMapper, WordMapper
 * - CourseService, StudentClassService, StudentClassesService
 * - LearningRecordsService, TextExerciseService, ScoreRecordService
 * - StudentExerciseAnswerMapper
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Assignment.class);
    }

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    // AssignmentServiceImpl has both @Autowired assignmentMapper and inherited baseMapper.
    // Mockito @InjectMocks injects the same mock into both fields of the same type.
    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private TextExerciseMapper textExerciseMapper;

    @Mock
    private UnitWordMapper unitWordMapper;

    @Mock
    private VocabularyBookMapper vocabularyBookMapper;

    @Mock
    private LearningRecordMapper learningRecordMapper;

    @Mock
    private CourseService courseService;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private StudentClassesService studentClassesService;

    @Mock
    private LearningRecordsService learningRecordsService;

    @Mock
    private TextExerciseService textExerciseService;

    @Mock
    private ScoreRecordService scoreRecordService;

    @Mock
    private StudentExerciseAnswerMapper studentExerciseAnswerMapper;

    @Mock
    private TextMapper textMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private UnitMapper unitMapper;

    @Mock
    private WordMapper wordMapper;

    private Assignment testAssignment;

    @BeforeEach
    void setUp() {
        TestUtils.injectBaseMapper(assignmentService, assignmentMapper);
        testAssignment = new Assignment();
        testAssignment.setId(1L);
        testAssignment.setTitle("Unit 1 Assignment");
        testAssignment.setDescription("Complete unit 1 vocabulary");
        testAssignment.setClassId(10L);
        testAssignment.setUnitId(20L);
        testAssignment.setCreatedBy(100L);
        testAssignment.setDeadline(LocalDateTime.now().plusDays(7));
        testAssignment.setCreatedTime(LocalDateTime.now());
        testAssignment.setDeleted(0);
    }

    // ==================== createAssignment tests ====================

    @Test
    void createAssignment_success() {
        // Arrange: createAssignment() calls save() which internally uses baseMapper.insert()
        Assignment newAssignment = new Assignment();
        newAssignment.setTitle("New Assignment");
        newAssignment.setUnitId(20L);
        newAssignment.setClassId(10L);

        when(assignmentMapper.insert(any(Assignment.class))).thenReturn(1);

        // Act
        assignmentService.createAssignment(newAssignment);

        // Assert: save() delegates to baseMapper.insert()
        verify(assignmentMapper).insert(any(Assignment.class));
    }

    @Test
    void createAssignment_nullUnitId_throwsBusinessException() {
        // Arrange
        Assignment newAssignment = new Assignment();
        newAssignment.setTitle("Assignment Without Unit");
        newAssignment.setUnitId(null);

        // Act & Assert: createAssignment throws BusinessException (not RuntimeException)
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assignmentService.createAssignment(newAssignment));
        assertEquals("单元ID不能为空", exception.getMessage());
    }

    // ==================== getAssignmentById tests ====================

    @Test
    void getAssignmentById_found_returnsAssignment() {
        // Arrange: getAssignmentById() uses assignmentMapper.selectAssignmentWithDetails()
        when(assignmentMapper.selectAssignmentWithDetails(1L)).thenReturn(testAssignment);

        // Act
        Assignment result = assignmentService.getAssignmentById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("Unit 1 Assignment", result.getTitle());
        verify(assignmentMapper).selectAssignmentWithDetails(1L);
    }

    @Test
    void getAssignmentById_notFound_returnsNull() {
        // Arrange
        when(assignmentMapper.selectAssignmentWithDetails(999L)).thenReturn(null);

        // Act
        Assignment result = assignmentService.getAssignmentById(999L);

        // Assert
        assertNull(result);
    }

    // ==================== updateAssignment tests ====================

    @Test
    void updateAssignment_success() {
        // Arrange: updateAssignment() calls updateById() which uses baseMapper.updateById()
        Assignment update = new Assignment();
        update.setId(1L);
        update.setTitle("Updated Title");

        when(assignmentMapper.updateById(any(Assignment.class))).thenReturn(1);

        // Act
        assignmentService.updateAssignment(update);

        // Assert
        verify(assignmentMapper).updateById(update);
    }

    // ==================== deleteAssignment tests ====================

    @Test
    void deleteAssignment_usesSoftDeleteWithUpdateWrapper() {
        // Arrange: deleteAssignment() now uses removeById(id) which with @TableLogic
        // converts to UPDATE SET deleted=1 WHERE id=? AND deleted=0
        // ServiceImpl.removeById(id) delegates to baseMapper.deleteById(id)
        when(assignmentMapper.deleteById(1L)).thenReturn(1);

        // Act
        assignmentService.deleteAssignment(1L);

        // Assert: verify removeById was used (which calls deleteById under the hood)
        verify(assignmentMapper).deleteById(1L);
        // Verify manual update was NOT called
        verify(assignmentMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    // ==================== validateTeacherPermission tests ====================

    @Test
    void validateTeacherPermission_correctTeacher_returnsTrue() {
        // Arrange: validateTeacherPermission() calls getActiveAssignment() -> getById()
        // getById() in ServiceImpl delegates to baseMapper.selectById()
        when(assignmentMapper.selectById(1L)).thenReturn(testAssignment);

        // Act
        boolean result = assignmentService.validateTeacherPermission(100L, 1L);

        // Assert
        assertTrue(result);
    }

    @Test
    void validateTeacherPermission_wrongTeacher_returnsFalse() {
        // Arrange
        when(assignmentMapper.selectById(1L)).thenReturn(testAssignment);

        // Act
        boolean result = assignmentService.validateTeacherPermission(999L, 1L);

        // Assert
        assertFalse(result);
    }

    @Test
    void validateTeacherPermission_assignmentNotFound_returnsFalse() {
        // Arrange
        when(assignmentMapper.selectById(999L)).thenReturn(null);

        // Act
        boolean result = assignmentService.validateTeacherPermission(100L, 999L);

        // Assert
        assertFalse(result);
    }

    @Test
    void validateTeacherPermission_nullAssignmentId_returnsFalse() {
        // Arrange: getActiveAssignment(null) returns null immediately

        // Act
        boolean result = assignmentService.validateTeacherPermission(100L, null);

        // Assert
        assertFalse(result);
        // selectById should never be called when id is null
        verify(assignmentMapper, never()).selectById(any());
    }

    // ==================== isAssignmentInClasses tests ====================

    @Test
    void isAssignmentInClasses_inClassList_returnsTrue() {
        // Arrange: uses getActiveAssignment() -> getById() -> baseMapper.selectById()
        when(assignmentMapper.selectById(1L)).thenReturn(testAssignment);

        // Act
        boolean result = assignmentService.isAssignmentInClasses(1L, Arrays.asList(10L, 20L));

        // Assert
        assertTrue(result);
    }

    @Test
    void isAssignmentInClasses_notInClassList_returnsFalse() {
        // Arrange
        when(assignmentMapper.selectById(1L)).thenReturn(testAssignment);

        // Act
        boolean result = assignmentService.isAssignmentInClasses(1L, Arrays.asList(30L, 40L));

        // Assert
        assertFalse(result);
    }

    @Test
    void isAssignmentInClasses_assignmentNotFound_returnsFalse() {
        // Arrange
        when(assignmentMapper.selectById(999L)).thenReturn(null);

        // Act
        boolean result = assignmentService.isAssignmentInClasses(999L, Arrays.asList(10L, 20L));

        // Assert
        assertFalse(result);
    }

    // ==================== getTeacherStats tests ====================

    @Test
    void getTeacherStats_returnsStatsWithDefaults() {
        // Arrange: getTeacherStats() calls assignmentMapper.selectTeacherStats()
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("total_classes", 3);
        mockStats.put("total_students", 50);
        when(assignmentMapper.selectTeacherStats(100L)).thenReturn(mockStats);

        // Act
        Map<String, Object> result = assignmentService.getTeacherStats(100L);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.get("total_classes"));
        assertEquals(50, result.get("total_students"));
        // Default values should be applied for missing keys
        assertEquals(0, result.get("total_assignments"));
        assertEquals(0, result.get("completed_tasks"));
    }

    @Test
    void getTeacherStats_nullFromMapper_returnsDefaults() {
        // Arrange: mapper returns null
        when(assignmentMapper.selectTeacherStats(100L)).thenReturn(null);

        // Act
        Map<String, Object> result = assignmentService.getTeacherStats(100L);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.get("total_classes"));
        assertEquals(0, result.get("total_students"));
        assertEquals(0, result.get("total_assignments"));
        assertEquals(0, result.get("completed_tasks"));
    }

    // ==================== getRecentAssignments tests ====================

    @Test
    void getRecentAssignments_delegatesToMapper() {
        // Arrange
        List<Assignment> mockAssignments = Arrays.asList(testAssignment);
        when(assignmentMapper.selectRecentAssignments(100L, 5)).thenReturn(mockAssignments);

        // Act
        List<Assignment> result = assignmentService.getRecentAssignments(100L, 5);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Unit 1 Assignment", result.get(0).getTitle());
        verify(assignmentMapper).selectRecentAssignments(100L, 5);
    }

    // ==================== getAssignmentsByPage tests ====================

    @Test
    @SuppressWarnings("unchecked")
    void getAssignmentsByPage_returnsPagedResult() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        params.put("teacherId", 100L);

        Page<Assignment> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(testAssignment));
        mockPage.setTotal(1);

        when(assignmentMapper.selectAssignmentsWithDetails(
                any(Page.class), eq(100L), isNull(), isNull()))
                .thenReturn(mockPage);

        // Act
        Map<String, Object> result = assignmentService.getAssignmentsByPage(params);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("total"));
        assertNotNull(result.get("records"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAssignmentsByPage_defaultPageAndSize() {
        // Arrange: params without page/size should default to page=1, size=10
        Map<String, Object> params = new HashMap<>();

        Page<Assignment> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Collections.emptyList());
        mockPage.setTotal(0);

        when(assignmentMapper.selectAssignmentsWithDetails(
                any(Page.class), isNull(), isNull(), isNull()))
                .thenReturn(mockPage);

        // Act
        Map<String, Object> result = assignmentService.getAssignmentsByPage(params);

        // Assert
        assertNotNull(result);
        assertEquals(0L, result.get("total"));
    }

    // ==================== getAssignmentDetailWithProgress tests ====================

    @Test
    void getAssignmentDetailWithProgress_assignmentNotFound_returnsEmptyMap() {
        // Arrange: getActiveAssignment(999L) calls getById(999L) -> selectById(999L) -> null
        when(assignmentMapper.selectById(999L)).thenReturn(null);

        // Act
        Map<String, Object> result = assignmentService.getAssignmentDetailWithProgress(999L, 1L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getStudentAssignmentsWithStatus tests ====================

    @Test
    void getStudentAssignmentsWithStatus_emptyClassIds_returnsEmptyResult() {
        // Arrange: empty classIds should return early with empty result

        // Act
        Map<String, Object> result = assignmentService.getStudentAssignmentsWithStatus(
                Collections.emptyList(), 1L, null);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.get("total"));
        assertNotNull(result.get("assignments"));
        assertTrue(((List<?>) result.get("assignments")).isEmpty());
    }

    @Test
    void getStudentAssignmentsWithStatus_nullClassIds_returnsEmptyResult() {
        // Arrange: null classIds should return early with empty result

        // Act
        Map<String, Object> result = assignmentService.getStudentAssignmentsWithStatus(
                null, 1L, null);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.get("total"));
    }

    // ==================== startAssignment tests ====================

    @Test
    void startAssignment_assignmentNotFound_returnsEmptyMap() {
        // Arrange
        when(assignmentMapper.selectById(999L)).thenReturn(null);

        // Act
        Map<String, Object> result = assignmentService.startAssignment(999L, 1L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getAssignmentContent tests ====================

    @Test
    void getAssignmentContent_assignmentNotFound_returnsEmptyMap() {
        // Arrange
        when(assignmentMapper.selectById(999L)).thenReturn(null);

        // Act
        Map<String, Object> result = assignmentService.getAssignmentContent(999L, 1L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== completeAssignment tests ====================

    @Test
    void completeAssignment_alreadyCompleted_throwsBusinessException() {
        // Arrange: completeAssignment checks for existing COMPLETE records
        when(learningRecordMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        Map<String, Object> completionData = new HashMap<>();
        completionData.put("studyTime", 30);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assignmentService.completeAssignment(1L, 50L, completionData));
        assertEquals("任务已提交，请勿重复提交", exception.getMessage());
    }

    @Test
    void completeAssignment_success_insertsRecordAndUpdatesScore() {
        // Arrange: no existing COMPLETE record
        when(learningRecordMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(learningRecordMapper.insert(any(LearningRecord.class))).thenReturn(1);

        Map<String, Object> completionData = new HashMap<>();
        completionData.put("studyTime", 30);
        completionData.put("completedItems", 10);
        completionData.put("totalItems", 15);

        // Act
        assignmentService.completeAssignment(1L, 50L, completionData);

        // Assert
        verify(learningRecordMapper).insert(any(LearningRecord.class));
        verify(scoreRecordService).updateAssignmentScore(1L, 50L);
    }

    // ==================== getAssignmentProgress (student) tests ====================

    @Test
    void getAssignmentProgress_student_returnsProgressMap() {
        // Arrange: getAssignmentProgress(assignmentId, studentId) calls getActiveAssignment()
        // and then various calculation methods.
        // getActiveAssignment() -> getById() -> selectById()
        when(assignmentMapper.selectById(1L)).thenReturn(testAssignment);

        // For calculateAssignmentProgress: getAssignmentWords needs unitWordMapper
        // testAssignment has a plain description (not JSON), so isTextsTask/isWordsTask/isUnitTask = false
        // getAssignmentWords falls into the else branch: unitWordMapper.selectList() for unit words
        when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // getAssignmentTexts falls into else branch: textMapper.selectList() for unit texts
        when(textMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // calculateWordsLearnedInAssignment also needs unitWordMapper -> already returns empty
        // calculateTextsReadInAssignment needs texts (empty) so returns 0
        // calculateStudyTimeInAssignment uses learningRecordMapper
        when(learningRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        Map<String, Object> result = assignmentService.getAssignmentProgress(1L, 50L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("assignmentId"));
        assertEquals(50L, result.get("studentId"));
        assertEquals(0.0, result.get("completionRate"));
    }

    // ==================== getAssignmentProgress (teacher) tests ====================

    @Test
    void getAssignmentProgress_teacher_assignmentNotFound_returnsEmptyList() {
        // Arrange: overloaded getAssignmentProgress(assignmentId) for teacher view
        when(assignmentMapper.selectById(999L)).thenReturn(null);

        // Act
        List<Map<String, Object>> result = assignmentService.getAssignmentProgress(999L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== createAssignment with content types ====================

    @Test
    void createAssignment_unitContentType_success() {
        // Arrange: createAssignment with "unit" description (JSON-like format)
        Assignment unitAssignment = new Assignment();
        unitAssignment.setTitle("Unit Assignment");
        unitAssignment.setUnitId(20L);
        unitAssignment.setClassId(10L);
        unitAssignment.setDescription("unit"); // Simple unit type

        when(assignmentMapper.insert(any(Assignment.class))).thenReturn(1);

        // Act
        assignmentService.createAssignment(unitAssignment);

        // Assert
        verify(assignmentMapper).insert(any(Assignment.class));
    }

    @Test
    void createAssignment_textsContentType_success() {
        // Arrange: createAssignment with texts content type
        Assignment textsAssignment = new Assignment();
        textsAssignment.setTitle("Texts Assignment");
        textsAssignment.setUnitId(20L);
        textsAssignment.setClassId(10L);
        textsAssignment.setDescription("texts"); // Texts type

        when(assignmentMapper.insert(any(Assignment.class))).thenReturn(1);

        // Act
        assignmentService.createAssignment(textsAssignment);

        // Assert
        verify(assignmentMapper).insert(any(Assignment.class));
    }

    @Test
    void createAssignment_wordsContentType_success() {
        // Arrange: createAssignment with words content type
        Assignment wordsAssignment = new Assignment();
        wordsAssignment.setTitle("Words Assignment");
        wordsAssignment.setUnitId(20L);
        wordsAssignment.setClassId(10L);
        wordsAssignment.setDescription("words"); // Words type

        when(assignmentMapper.insert(any(Assignment.class))).thenReturn(1);

        // Act
        assignmentService.createAssignment(wordsAssignment);

        // Assert
        verify(assignmentMapper).insert(any(Assignment.class));
    }

}
