package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.Course;
import com.english.entity.StudentClass;
import com.english.entity.Unit;
import com.english.exception.BusinessException;
import com.english.mapper.ClassMapper;
import com.english.mapper.CourseMapper;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.UnitMapper;
import com.english.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CourseServiceImpl.
 *
 * Actual dependencies (constructor-injected):
 *   UnitMapper, UnitService, ClassService, StudentClassMapper, ClassMapper
 *
 * Key behaviors verified:
 * - Course entity uses createdAt/updatedAt (Java fields), mapped to created_time/updated_time (DB columns)
 * - Course entity has NO teacherId field
 * - createCourse() validates name, sets createdAt/updatedAt/deleted, calls save()
 * - updateCourse() validates name, sets updatedAt, calls updateById()
 * - deleteCourse() cascades: classes->studentClass cleanup, unitService.deleteUnit(), then removeById()
 * - getCourseById() uses getOne(wrapper) with deleted=0 filter (NOT selectById)
 * - getCourseStats() counts ACTIVE and INACTIVE separately, returns totalActive/totalInactive/totalCourses
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Course.class);
    }

    @InjectMocks
    private CourseServiceImpl courseService;

    /**
     * CourseServiceImpl extends ServiceImpl<CourseMapper, Course>.
     * The baseMapper field name must match what ServiceImpl expects for @InjectMocks.
     */
    @Mock
    private CourseMapper baseMapper;

    @Mock
    private UnitMapper unitMapper;

    @Mock
    private UnitService unitService;

    @Mock
    private ClassService classService;

    @Mock
    private StudentClassMapper studentClassMapper;

    @Mock
    private ClassMapper classMapper;

    private Course testCourse;

    @BeforeEach
    void setUp() {
        TestUtils.injectBaseMapper(courseService, baseMapper);
        testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setName("English 101");
        testCourse.setDescription("Basic English Course");
        testCourse.setStatus("ACTIVE");
        testCourse.setCreatedBy(10L);
        testCourse.setDeleted(0);
        testCourse.setCreatedAt(LocalDateTime.now());
        testCourse.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== createCourse tests ====================

    @Test
    void createCourse_success_setsDefaultFields() {
        // Arrange
        Course newCourse = new Course();
        newCourse.setName("New Course");
        newCourse.setDescription("Description");

        when(baseMapper.insert(any(Course.class))).thenReturn(1);

        // Act
        courseService.createCourse(newCourse);

        // Assert: createdAt, updatedAt, deleted are all set
        assertNotNull(newCourse.getCreatedAt());
        assertNotNull(newCourse.getUpdatedAt());
        assertEquals(0, newCourse.getDeleted());
        verify(baseMapper).insert(any(Course.class));
    }

    @Test
    void createCourse_setsDefaultStatusToActive_whenStatusNull() {
        // Arrange
        Course newCourse = new Course();
        newCourse.setName("New Course");
        // status is null

        when(baseMapper.insert(any(Course.class))).thenReturn(1);

        // Act
        courseService.createCourse(newCourse);

        // Assert
        assertEquals("ACTIVE", newCourse.getStatus());
    }

    @Test
    void createCourse_preservesExistingStatus() {
        // Arrange
        Course newCourse = new Course();
        newCourse.setName("New Course");
        newCourse.setStatus("INACTIVE");

        when(baseMapper.insert(any(Course.class))).thenReturn(1);

        // Act
        courseService.createCourse(newCourse);

        // Assert: status is not overwritten
        assertEquals("INACTIVE", newCourse.getStatus());
    }

    @Test
    void createCourse_nullName_throwsBusinessException() {
        Course newCourse = new Course();
        newCourse.setName(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.createCourse(newCourse));
        assertEquals("课程名称不能为空", ex.getMessage());
        verify(baseMapper, never()).insert(any());
    }

    @Test
    void createCourse_emptyName_throwsBusinessException() {
        Course newCourse = new Course();
        newCourse.setName("   ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.createCourse(newCourse));
        assertEquals("课程名称不能为空", ex.getMessage());
        verify(baseMapper, never()).insert(any());
    }

    // ==================== getCourseById tests ====================

    @Test
    void getCourseById_found_returnsCourse() {
        // Arrange: getCourseById uses getOne(wrapper) which calls selectOne on baseMapper
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testCourse);

        // Act
        Course result = courseService.getCourseById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("English 101", result.getName());
        assertEquals(1L, result.getId());
    }

    @Test
    void getCourseById_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act
        Course result = courseService.getCourseById(999L);

        // Assert
        assertNull(result);
    }

    // ==================== updateCourse tests ====================

    @Test
    void updateCourse_success_updatesTimestamp() {
        // Arrange
        Course updateCourse = new Course();
        updateCourse.setId(1L);
        updateCourse.setName("Updated Course Name");

        when(baseMapper.updateById(any(Course.class))).thenReturn(1);

        // Act
        courseService.updateCourse(updateCourse);

        // Assert: updatedAt is set
        assertNotNull(updateCourse.getUpdatedAt());
        verify(baseMapper).updateById(updateCourse);
    }

    @Test
    void updateCourse_nullName_throwsBusinessException() {
        Course updateCourse = new Course();
        updateCourse.setId(1L);
        updateCourse.setName(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.updateCourse(updateCourse));
        assertEquals("课程名称不能为空", ex.getMessage());
        verify(baseMapper, never()).updateById(any());
    }

    @Test
    void updateCourse_emptyName_throwsBusinessException() {
        Course updateCourse = new Course();
        updateCourse.setId(1L);
        updateCourse.setName("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.updateCourse(updateCourse));
        assertEquals("课程名称不能为空", ex.getMessage());
        verify(baseMapper, never()).updateById(any());
    }

    // ==================== deleteCourse tests ====================

    @Test
    void deleteCourse_courseNotFound_throwsBusinessException() {
        // Arrange: getCourseById uses getOne(wrapper) internally
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.deleteCourse(999L));
        assertEquals("课程不存在", ex.getMessage());
    }

    @Test
    void deleteCourse_withClassesAndUnits_cascadesAll() {
        // Arrange: getCourseById returns the course
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testCourse);

        // Classes under this course
        com.english.entity.Class class1 = new com.english.entity.Class();
        class1.setId(10L);
        class1.setName("Class A");
        com.english.entity.Class class2 = new com.english.entity.Class();
        class2.setId(20L);
        class2.setName("Class B");
        when(classService.getClassesByCourseId(1L)).thenReturn(Arrays.asList(class1, class2));

        // StudentClass deletion for each class
        when(studentClassMapper.delete(any(QueryWrapper.class))).thenReturn(5);
        // Class physical deletion
        when(classMapper.deleteById(anyLong())).thenReturn(1);

        // Units under this course
        Unit unit1 = new Unit();
        unit1.setId(100L);
        Unit unit2 = new Unit();
        unit2.setId(200L);
        when(unitMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(unit1, unit2));

        // removeById for the course soft-delete (ServiceImpl.removeById calls baseMapper.deleteById)
        // Course entity has both @TableLogic and fill=INSERT_UPDATE, so removeById(id)
        // internally creates an entity and calls deleteById(Course entity), not deleteById(Serializable id)
        when(baseMapper.deleteById(any(Course.class))).thenReturn(1);

        // Act
        courseService.deleteCourse(1L);

        // Assert: student-class associations deleted for each class
        verify(studentClassMapper, times(2)).delete(any(QueryWrapper.class));

        // Assert: classes physically deleted
        verify(classMapper).deleteById(10L);
        verify(classMapper).deleteById(20L);

        // Assert: units deleted via unitService
        verify(unitService).deleteUnit(100L);
        verify(unitService).deleteUnit(200L);

        // Assert: course soft-deleted via removeById (which calls baseMapper.deleteById with @TableLogic)
        verify(baseMapper).deleteById(any(Course.class));
    }

    @Test
    void deleteCourse_noClassesNoUnits_onlySoftDeletesCourse() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testCourse);
        when(classService.getClassesByCourseId(1L)).thenReturn(Collections.emptyList());
        when(unitMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(baseMapper.deleteById(any(Course.class))).thenReturn(1);

        // Act
        courseService.deleteCourse(1L);

        // Assert: no cascade operations
        verify(studentClassMapper, never()).delete(any(QueryWrapper.class));
        verify(classMapper, never()).deleteById(anyLong());
        verify(unitService, never()).deleteUnit(anyLong());

        // Assert: course itself still soft-deleted
        verify(baseMapper).deleteById(any(Course.class));
    }

    @Test
    void deleteCourse_nullClassesList_handlesGracefully() {
        // Arrange: classService returns null
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testCourse);
        when(classService.getClassesByCourseId(1L)).thenReturn(null);
        when(unitMapper.selectList(any(QueryWrapper.class))).thenReturn(null);
        when(baseMapper.deleteById(any(Course.class))).thenReturn(1);

        // Act -- should not throw NPE
        courseService.deleteCourse(1L);

        // Assert
        verify(studentClassMapper, never()).delete(any(QueryWrapper.class));
        verify(unitService, never()).deleteUnit(anyLong());
        verify(baseMapper).deleteById(any(Course.class));
    }

    // ==================== getActiveCourses tests ====================

    @Test
    void getActiveCourses_returnsList() {
        // Arrange
        List<Course> courses = Arrays.asList(testCourse);
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(courses);

        // Act
        List<Course> result = courseService.getActiveCourses();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("English 101", result.get(0).getName());
    }

    @Test
    void getActiveCourses_empty_returnsEmptyList() {
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Course> result = courseService.getActiveCourses();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getCoursesByKeyword tests ====================

    @Test
    void getCoursesByKeyword_withKeyword_returnsList() {
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(testCourse));

        List<Course> result = courseService.getCoursesByKeyword("English");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getCoursesByKeyword_nullKeyword_returnsAllActive() {
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(testCourse));

        List<Course> result = courseService.getCoursesByKeyword(null);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ==================== getCoursesByPage tests ====================

    @Test
    @SuppressWarnings("unchecked")
    void getCoursesByPage_defaultParams_returnsPagedResult() {
        // Arrange
        IPage<Course> mockPage = mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(Arrays.asList(testCourse));
        when(mockPage.getTotal()).thenReturn(1L);
        when(mockPage.getPages()).thenReturn(1L);
        when(mockPage.getCurrent()).thenReturn(1L);
        when(mockPage.getSize()).thenReturn(10L);

        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        Map<String, Object> params = new HashMap<>();
        // page and size default to 1 and 10

        // Act
        Map<String, Object> result = courseService.getCoursesByPage(params);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("total"));
        assertEquals(1L, result.get("pages"));
        assertEquals(1L, result.get("current"));
        assertEquals(10L, result.get("size"));
        assertNotNull(result.get("records"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCoursesByPage_withKeywordAndStatus_appliesFilters() {
        IPage<Course> mockPage = mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(Collections.emptyList());
        when(mockPage.getTotal()).thenReturn(0L);
        when(mockPage.getPages()).thenReturn(0L);
        when(mockPage.getCurrent()).thenReturn(1L);
        when(mockPage.getSize()).thenReturn(10L);

        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        params.put("keyword", "English");
        params.put("status", "ACTIVE");

        Map<String, Object> result = courseService.getCoursesByPage(params);

        assertNotNull(result);
        assertEquals(0L, result.get("total"));
    }

    // ==================== getCourseStats tests ====================

    @Test
    void getCourseStats_returnsActiveAndInactiveCounts() {
        // Arrange: getCourseStats calls count() twice -- once for ACTIVE, once for INACTIVE
        // ServiceImpl.count(wrapper) delegates to baseMapper.selectCount(wrapper)
        // First call: ACTIVE count = 10, Second call: INACTIVE count = 5
        when(baseMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(10L)   // first call: ACTIVE
                .thenReturn(5L);   // second call: INACTIVE

        // Act
        Map<String, Object> stats = courseService.getCourseStats();

        // Assert
        assertNotNull(stats);
        assertEquals(10L, stats.get("totalActive"));
        assertEquals(5L, stats.get("totalInactive"));
        assertEquals(15L, stats.get("totalCourses"));

        // Verify selectCount was called exactly twice
        verify(baseMapper, times(2)).selectCount(any(QueryWrapper.class));
    }

    @Test
    void getCourseStats_zeroCourses_returnsZeros() {
        when(baseMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(0L)
                .thenReturn(0L);

        Map<String, Object> stats = courseService.getCourseStats();

        assertNotNull(stats);
        assertEquals(0L, stats.get("totalActive"));
        assertEquals(0L, stats.get("totalInactive"));
        assertEquals(0L, stats.get("totalCourses"));
    }
}
