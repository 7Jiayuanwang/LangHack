package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.english.entity.Class;
import com.english.entity.Course;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.ClassMapper;
import com.english.mapper.StudentClassMapper;
import com.english.service.impl.ClassServiceImpl;
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
 * Unit tests for ClassServiceImpl.
 *
 * Key observations from source code:
 * - ClassServiceImpl extends ServiceImpl<ClassMapper, Class>
 * - Injects CourseService, UserService, StudentClassMapper
 * - createClass() validates course existence, teacher role, and name uniqueness
 * - deleteClass() checks for students and throws BusinessException if any exist, then uses removeById()
 * - getClassById() uses custom mapper method selectClassWithDetails()
 * - getClassesByTeacherId() uses custom mapper method selectClassesByTeacherId()
 */
@ExtendWith(MockitoExtension.class)
class ClassServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), com.english.entity.Class.class);
    }

    @InjectMocks
    private ClassServiceImpl classService;

    @Mock
    private ClassMapper baseMapper;

    @Mock
    private CourseService courseService;

    @Mock
    private UserService userService;

    @Mock
    private StudentClassMapper studentClassMapper;

    private Class testClass;
    private Course testCourse;
    private User testTeacher;

    @BeforeEach
    void setUp() {
        testClass = new Class();
        testClass.setId(1L);
        testClass.setName("English A1");
        testClass.setCourseId(10L);
        testClass.setTeacherId(20L);
        testClass.setDescription("Beginner English class");
        testClass.setCreatedTime(LocalDateTime.now());

        testCourse = new Course();
        testCourse.setId(10L);
        testCourse.setName("English 101");

        testTeacher = new User();
        testTeacher.setId(20L);
        testTeacher.setUsername("teacher1");
        testTeacher.setRole("TEACHER");
    }

    // ==================== createClass tests ====================

    @Test
    void createClass_success_setsCreatedTime() {
        // Arrange
        Class newClass = new Class();
        newClass.setName("New Class");
        newClass.setCourseId(10L);
        newClass.setTeacherId(20L);

        when(courseService.getCourseById(10L)).thenReturn(testCourse);
        when(userService.getUserById(20L)).thenReturn(testTeacher);
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L); // name not exists
        when(baseMapper.insert(any(Class.class))).thenReturn(1);

        // Act
        classService.createClass(newClass);

        // Assert
        assertNotNull(newClass.getCreatedTime());
        verify(baseMapper).insert(any(Class.class));
    }

    @Test
    void createClass_nullName_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setName(null);
        newClass.setCourseId(10L);
        newClass.setTeacherId(20L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("班级名称不能为空", exception.getMessage());
    }

    @Test
    void createClass_emptyName_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setName("   ");
        newClass.setCourseId(10L);
        newClass.setTeacherId(20L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("班级名称不能为空", exception.getMessage());
    }

    @Test
    void createClass_nullCourseId_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setName("Test");
        newClass.setCourseId(null);
        newClass.setTeacherId(20L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("课程ID不能为空", exception.getMessage());
    }

    @Test
    void createClass_nullTeacherId_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setName("Test");
        newClass.setCourseId(10L);
        newClass.setTeacherId(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("教师ID不能为空", exception.getMessage());
    }

    @Test
    void createClass_courseNotFound_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setCourseId(999L);
        newClass.setTeacherId(20L);
        newClass.setName("Test");

        when(courseService.getCourseById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("课程不存在", exception.getMessage());
    }

    @Test
    void createClass_teacherRoleInvalid_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setCourseId(10L);
        newClass.setTeacherId(30L);
        newClass.setName("Test");

        User student = new User();
        student.setId(30L);
        student.setRole("STUDENT"); // Wrong role

        when(courseService.getCourseById(10L)).thenReturn(testCourse);
        when(userService.getUserById(30L)).thenReturn(student);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("指定的教师不存在或角色不正确", exception.getMessage());
    }

    @Test
    void createClass_teacherNotFound_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setCourseId(10L);
        newClass.setTeacherId(30L);
        newClass.setName("Test");

        when(courseService.getCourseById(10L)).thenReturn(testCourse);
        when(userService.getUserById(30L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("指定的教师不存在或角色不正确", exception.getMessage());
    }

    @Test
    void createClass_duplicateName_throwsBusinessException() {
        // Arrange
        Class newClass = new Class();
        newClass.setName("English A1"); // duplicate
        newClass.setCourseId(10L);
        newClass.setTeacherId(20L);

        when(courseService.getCourseById(10L)).thenReturn(testCourse);
        when(userService.getUserById(20L)).thenReturn(testTeacher);
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L); // name exists

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClass(newClass));
        assertEquals("班级名称已存在", exception.getMessage());
    }

    // ==================== getClassById tests ====================

    @Test
    void getClassById_found_returnsClassWithDetails() {
        // Arrange
        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);

        // Act
        Class result = classService.getClassById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("English A1", result.getName());
        verify(baseMapper).selectClassWithDetails(1L);
    }

    @Test
    void getClassById_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectClassWithDetails(999L)).thenReturn(null);

        // Act
        Class result = classService.getClassById(999L);

        // Assert
        assertNull(result);
    }

    // ==================== updateClass tests ====================

    @Test
    void updateClass_success_updatesViaBaseMapper() {
        // Arrange
        Class updateClass = new Class();
        updateClass.setId(1L);
        updateClass.setName("Updated Class Name");
        updateClass.setCourseId(10L);
        updateClass.setTeacherId(20L);

        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L); // name available
        when(baseMapper.updateById(any(Class.class))).thenReturn(1);

        // Act
        classService.updateClass(updateClass);

        // Assert
        verify(baseMapper).updateById(updateClass);
    }

    @Test
    void updateClass_classNotFound_throwsBusinessException() {
        // Arrange
        Class updateClass = new Class();
        updateClass.setId(999L);
        updateClass.setName("Some Name");

        when(baseMapper.selectClassWithDetails(999L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.updateClass(updateClass));
        assertEquals("班级不存在", exception.getMessage());
    }

    @Test
    void updateClass_nullName_throwsBusinessException() {
        // Arrange
        Class updateClass = new Class();
        updateClass.setId(1L);
        updateClass.setName(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.updateClass(updateClass));
        assertEquals("班级名称不能为空", exception.getMessage());
    }

    @Test
    void updateClass_duplicateName_throwsBusinessException() {
        // Arrange
        Class updateClass = new Class();
        updateClass.setId(1L);
        updateClass.setName("Duplicate Name");
        updateClass.setCourseId(10L);
        updateClass.setTeacherId(20L);

        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L); // name exists

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.updateClass(updateClass));
        assertEquals("班级名称已存在", exception.getMessage());
    }

    @Test
    void updateClass_changeCourseToNonExistent_throwsBusinessException() {
        // Arrange
        Class updateClass = new Class();
        updateClass.setId(1L);
        updateClass.setName("English A1");
        updateClass.setCourseId(999L); // different from testClass.courseId (10L)
        updateClass.setTeacherId(20L);

        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);
        when(courseService.getCourseById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.updateClass(updateClass));
        assertEquals("课程不存在", exception.getMessage());
    }

    @Test
    void updateClass_changeTeacherToInvalidRole_throwsBusinessException() {
        // Arrange
        Class updateClass = new Class();
        updateClass.setId(1L);
        updateClass.setName("English A1");
        updateClass.setCourseId(10L);
        updateClass.setTeacherId(30L); // different from testClass.teacherId (20L)

        User student = new User();
        student.setId(30L);
        student.setRole("STUDENT");

        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);
        when(userService.getUserById(30L)).thenReturn(student);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.updateClass(updateClass));
        assertEquals("指定的教师不存在或角色不正确", exception.getMessage());
    }

    // ==================== deleteClass tests ====================

    @Test
    void deleteClass_success_usesRemoveById() {
        // Arrange
        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(baseMapper.deleteById(1L)).thenReturn(1);

        // Act
        classService.deleteClass(1L);

        // Assert: uses removeById which calls baseMapper.deleteById
        verify(baseMapper).deleteById(1L);
    }

    @Test
    void deleteClass_classNotFound_throwsBusinessException() {
        // Arrange
        when(baseMapper.selectClassWithDetails(1L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.deleteClass(1L));
        assertEquals("班级不存在", exception.getMessage());
    }

    @Test
    void deleteClass_hasStudents_throwsBusinessException() {
        // Arrange
        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.deleteClass(1L));
        // Actual message format: "班级下还有 X 名学生，请先移除学生后再删除班级"
        assertEquals("班级下还有 5 名学生，请先移除学生后再删除班级", exception.getMessage());
    }

    // ==================== getClassesByTeacherId tests ====================

    @Test
    void getClassesByTeacherId_returnsTeacherClasses() {
        // Arrange
        List<Class> teacherClasses = Arrays.asList(testClass);
        when(baseMapper.selectClassesByTeacherId(20L)).thenReturn(teacherClasses);

        // Act
        List<Class> result = classService.getClassesByTeacherId(20L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("English A1", result.get(0).getName());
    }

    // ==================== getClassesByStudentId tests ====================

    @Test
    void getClassesByStudentId_returnsStudentClasses() {
        // Arrange
        List<Class> studentClasses = Arrays.asList(testClass);
        when(baseMapper.selectClassesByStudentIdWithJoinTime(100L)).thenReturn(studentClasses);

        // Act
        List<Class> result = classService.getClassesByStudentId(100L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("English A1", result.get(0).getName());
        verify(baseMapper).selectClassesByStudentIdWithJoinTime(100L);
    }

    // ==================== getClassesByCourseId tests ====================

    @Test
    void getClassesByCourseId_returnsClasses() {
        // Arrange: ServiceImpl.list() delegates to baseMapper.selectList()
        List<Class> courseClasses = Arrays.asList(testClass);
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(courseClasses);

        // Act
        List<Class> result = classService.getClassesByCourseId(10L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ==================== checkClassNameExists tests ====================

    @Test
    void checkClassNameExists_exists_returnsTrue() {
        // Arrange
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        // Act
        boolean result = classService.checkClassNameExists("English A1", null);

        // Assert
        assertTrue(result);
    }

    @Test
    void checkClassNameExists_notExists_returnsFalse() {
        // Arrange
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        // Act
        boolean result = classService.checkClassNameExists("New Name", null);

        // Assert
        assertFalse(result);
    }

    @Test
    void checkClassNameExists_nullName_returnsFalse() {
        // Act
        boolean result = classService.checkClassNameExists(null, null);

        // Assert
        assertFalse(result);
    }

    @Test
    void checkClassNameExists_emptyName_returnsFalse() {
        // Act
        boolean result = classService.checkClassNameExists("  ", null);

        // Assert
        assertFalse(result);
    }

    // ==================== validateTeacherPermission tests ====================

    @Test
    void validateTeacherPermission_correctTeacher_returnsTrue() {
        // Arrange
        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);

        // Act
        boolean result = classService.validateTeacherPermission(20L, 1L);

        // Assert
        assertTrue(result);
    }

    @Test
    void validateTeacherPermission_wrongTeacher_returnsFalse() {
        // Arrange
        when(baseMapper.selectClassWithDetails(1L)).thenReturn(testClass);

        // Act
        boolean result = classService.validateTeacherPermission(999L, 1L);

        // Assert
        assertFalse(result);
    }

    @Test
    void validateTeacherPermission_classNotFound_returnsFalse() {
        // Arrange
        when(baseMapper.selectClassWithDetails(999L)).thenReturn(null);

        // Act
        boolean result = classService.validateTeacherPermission(20L, 999L);

        // Assert
        assertFalse(result);
    }

    @Test
    void validateTeacherPermission_nullTeacherId_returnsFalse() {
        // Act
        boolean result = classService.validateTeacherPermission(null, 1L);

        // Assert
        assertFalse(result);
    }

    @Test
    void validateTeacherPermission_nullClassId_returnsFalse() {
        // Act
        boolean result = classService.validateTeacherPermission(20L, null);

        // Assert
        assertFalse(result);
    }

    // ==================== getClassStats tests ====================

    @Test
    void getClassStats_returnsTotalCount() {
        // Arrange
        when(baseMapper.selectClassCount()).thenReturn(42L);

        // Act
        Map<String, Object> stats = classService.getClassStats();

        // Assert
        assertNotNull(stats);
        assertEquals(42L, stats.get("totalClasses"));
    }

    // ==================== getClassByName tests ====================

    @Test
    void getClassByName_found_returnsClass() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testClass);

        // Act
        Class result = classService.getClassByName("English A1");

        // Assert
        assertNotNull(result);
        assertEquals("English A1", result.getName());
    }

    @Test
    void getClassByName_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act
        Class result = classService.getClassByName("Nonexistent");

        // Assert
        assertNull(result);
    }

    // ==================== createClassForImport tests ====================

    @Test
    void createClassForImport_success_setsCreatedTimeAndDefaultDescription() {
        // Arrange
        Class importClass = new Class();
        importClass.setName("Imported Class");
        importClass.setCourseId(10L);
        importClass.setTeacherId(20L);
        // description is null

        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L); // name not exists
        when(baseMapper.insert(any(Class.class))).thenReturn(1);

        // Act
        classService.createClassForImport(importClass);

        // Assert
        assertNotNull(importClass.getCreatedTime());
        assertEquals("通过批量导入自动创建", importClass.getDescription());
        verify(baseMapper).insert(any(Class.class));
    }

    @Test
    void createClassForImport_duplicateName_throwsBusinessException() {
        // Arrange
        Class importClass = new Class();
        importClass.setName("English A1");

        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L); // name exists

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.createClassForImport(importClass));
        assertEquals("班级名称已存在", exception.getMessage());
    }

    @Test
    void createClassForImport_withExistingDescription_preservesIt() {
        // Arrange
        Class importClass = new Class();
        importClass.setName("Imported Class");
        importClass.setDescription("Custom description");

        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(baseMapper.insert(any(Class.class))).thenReturn(1);

        // Act
        classService.createClassForImport(importClass);

        // Assert
        assertEquals("Custom description", importClass.getDescription());
    }

    // ==================== searchClassesForStudent tests ====================

    @Test
    void searchClassesForStudent_withKeyword_filtersResults() {
        // Arrange
        Class class1 = new Class();
        class1.setId(1L);
        class1.setName("English A1");
        class1.setDescription("Beginner");

        Class class2 = new Class();
        class2.setId(2L);
        class2.setName("Math B1");
        class2.setDescription("Advanced");

        when(baseMapper.selectClassesByStudentIdWithJoinTime(100L))
                .thenReturn(Arrays.asList(class1, class2));

        // Act
        List<Class> result = classService.searchClassesForStudent(100L, "english");

        // Assert
        assertEquals(1, result.size());
        assertEquals("English A1", result.get(0).getName());
    }

    @Test
    void searchClassesForStudent_nullKeyword_returnsAll() {
        // Arrange
        List<Class> allClasses = Arrays.asList(testClass);
        when(baseMapper.selectClassesByStudentIdWithJoinTime(100L)).thenReturn(allClasses);

        // Act
        List<Class> result = classService.searchClassesForStudent(100L, null);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void searchClassesForStudent_noClasses_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectClassesByStudentIdWithJoinTime(100L))
                .thenReturn(Collections.emptyList());

        // Act
        List<Class> result = classService.searchClassesForStudent(100L, "english");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
