package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.StudentClassMapper;
import com.english.service.impl.StudentClassServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudentClassServiceImpl.
 *
 * Key observations from source code:
 * - StudentClassServiceImpl extends ServiceImpl<StudentClassMapper, StudentClass>
 * - Injects ClassService and UserService
 * - joinClass() validates student existence/role and class existence, then uses try-insert-catch-DuplicateKeyException
 * - leaveClass() checks isStudentInClass() first, then removes via QueryWrapper
 * - batchAddStudentsToClass() validates class existence, iterates calling joinClass() per student
 * - addStudentToClass/removeStudentFromClass() validates teacher permission first
 * - getStudentsByClassId/getClassesByStudentId() have null checks and return empty list fallback
 */
@ExtendWith(MockitoExtension.class)
class StudentClassServiceTest {

    @InjectMocks
    private StudentClassServiceImpl studentClassService;

    @Mock
    private StudentClassMapper baseMapper;

    @Mock
    private ClassService classService;

    @Mock
    private UserService userService;

    private User testStudent;
    private User testTeacher;
    private Class testClass;
    private StudentClass testStudentClass;

    @BeforeEach
    void setUp() {
        testStudent = new User();
        testStudent.setId(1L);
        testStudent.setUsername("student1");
        testStudent.setRole("STUDENT");

        testTeacher = new User();
        testTeacher.setId(10L);
        testTeacher.setUsername("teacher1");
        testTeacher.setRole("TEACHER");

        testClass = new Class();
        testClass.setId(100L);
        testClass.setName("English A1");
        testClass.setTeacherId(10L);

        testStudentClass = new StudentClass();
        testStudentClass.setId(1000L);
        testStudentClass.setStudentId(1L);
        testStudentClass.setClassId(100L);
        testStudentClass.setJoinTime(LocalDateTime.now());
    }

    // ==================== joinClass tests ====================

    @Test
    void joinClass_success_savesStudentClass() {
        // Arrange
        when(userService.getUserById(1L)).thenReturn(testStudent);
        when(classService.getClassById(100L)).thenReturn(testClass);
        when(baseMapper.insert(any(StudentClass.class))).thenReturn(1);

        // Act
        studentClassService.joinClass(1L, 100L);

        // Assert
        verify(baseMapper).insert(any(StudentClass.class));
    }

    @Test
    void joinClass_userRoleIsUser_success() {
        // Arrange: USER role should also be allowed
        User userRoleStudent = new User();
        userRoleStudent.setId(2L);
        userRoleStudent.setRole("USER");

        when(userService.getUserById(2L)).thenReturn(userRoleStudent);
        when(classService.getClassById(100L)).thenReturn(testClass);
        when(baseMapper.insert(any(StudentClass.class))).thenReturn(1);

        // Act
        studentClassService.joinClass(2L, 100L);

        // Assert
        verify(baseMapper).insert(any(StudentClass.class));
    }

    @Test
    void joinClass_studentNotFound_throwsBusinessException() {
        // Arrange
        when(userService.getUserById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.joinClass(999L, 100L));
        assertEquals("学生不存在或角色不正确", ex.getMessage());
    }

    @Test
    void joinClass_studentWrongRole_throwsBusinessException() {
        // Arrange
        when(userService.getUserById(10L)).thenReturn(testTeacher); // TEACHER role

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.joinClass(10L, 100L));
        assertEquals("学生不存在或角色不正确", ex.getMessage());
    }

    @Test
    void joinClass_classNotFound_throwsBusinessException() {
        // Arrange
        when(userService.getUserById(1L)).thenReturn(testStudent);
        when(classService.getClassById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.joinClass(1L, 999L));
        assertEquals("班级不存在", ex.getMessage());
    }

    @Test
    void joinClass_duplicateEntry_throwsBusinessException() {
        // Arrange
        when(userService.getUserById(1L)).thenReturn(testStudent);
        when(classService.getClassById(100L)).thenReturn(testClass);
        when(baseMapper.insert(any(StudentClass.class))).thenThrow(new DuplicateKeyException("Duplicate"));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.joinClass(1L, 100L));
        assertEquals("学生已在此班级中", ex.getMessage());
    }

    // ==================== leaveClass tests ====================

    @Test
    void leaveClass_success_removesRecord() {
        // Arrange
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(1);
        when(baseMapper.delete(any(QueryWrapper.class))).thenReturn(1);

        // Act
        studentClassService.leaveClass(1L, 100L);

        // Assert
        verify(baseMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void leaveClass_notInClass_throwsBusinessException() {
        // Arrange
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(0);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.leaveClass(1L, 100L));
        assertEquals("学生不在此班级中", ex.getMessage());
    }

    @Test
    void leaveClass_checkReturnsNull_throwsBusinessException() {
        // Arrange: checkStudentInClass returns null
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.leaveClass(1L, 100L));
        assertEquals("学生不在此班级中", ex.getMessage());
    }

    // ==================== isStudentInClass tests ====================

    @Test
    void isStudentInClass_exists_returnsTrue() {
        // Arrange
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(1);

        // Act & Assert
        assertTrue(studentClassService.isStudentInClass(1L, 100L));
    }

    @Test
    void isStudentInClass_notExists_returnsFalse() {
        // Arrange
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(0);

        // Act & Assert
        assertFalse(studentClassService.isStudentInClass(1L, 100L));
    }

    @Test
    void isStudentInClass_nullResult_returnsFalse() {
        // Arrange
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(null);

        // Act & Assert
        assertFalse(studentClassService.isStudentInClass(1L, 100L));
    }

    // ==================== getStudentsByClassId tests ====================

    @Test
    void getStudentsByClassId_returnsStudents() {
        // Arrange
        List<StudentClass> students = Arrays.asList(testStudentClass);
        when(baseMapper.selectStudentsByClassId(100L)).thenReturn(students);

        // Act
        List<StudentClass> result = studentClassService.getStudentsByClassId(100L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getStudentId());
    }

    @Test
    void getStudentsByClassId_nullClassId_throwsIllegalArgument() {
        // Act & Assert
        assertThrows(BusinessException.class,
                () -> studentClassService.getStudentsByClassId(null));
    }

    @Test
    void getStudentsByClassId_nullResult_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectStudentsByClassId(100L)).thenReturn(null);

        // Act
        List<StudentClass> result = studentClassService.getStudentsByClassId(100L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getClassesByStudentId tests ====================

    @Test
    void getClassesByStudentId_returnsClasses() {
        // Arrange
        List<StudentClass> classes = Arrays.asList(testStudentClass);
        when(baseMapper.selectClassesByStudentId(1L)).thenReturn(classes);

        // Act
        List<StudentClass> result = studentClassService.getClassesByStudentId(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getClassId());
    }

    @Test
    void getClassesByStudentId_nullStudentId_throwsIllegalArgument() {
        // Act & Assert
        assertThrows(BusinessException.class,
                () -> studentClassService.getClassesByStudentId(null));
    }

    @Test
    void getClassesByStudentId_nullResult_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectClassesByStudentId(1L)).thenReturn(null);

        // Act
        List<StudentClass> result = studentClassService.getClassesByStudentId(1L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getStudentClassesByPage tests ====================

    @Test
    @SuppressWarnings("unchecked")
    void getStudentClassesByPage_returnsPagedResults() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        params.put("keyword", "test");
        params.put("classId", 100L);

        IPage<StudentClass> mockPage = mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(Arrays.asList(testStudentClass));
        when(mockPage.getTotal()).thenReturn(1L);
        when(mockPage.getPages()).thenReturn(1L);
        when(mockPage.getCurrent()).thenReturn(1L);
        when(mockPage.getSize()).thenReturn(10L);
        when(baseMapper.selectStudentClassesWithDetails(any(Page.class), eq(100L), isNull(), eq("test")))
                .thenReturn(mockPage);

        // Act
        Map<String, Object> result = studentClassService.getStudentClassesByPage(params);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("total"));
        assertEquals(1L, result.get("pages"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getStudentClassesByPage_defaultsWhenParamsNull() {
        // Arrange: empty params map, defaults page=1, size=10
        Map<String, Object> params = new HashMap<>();

        IPage<StudentClass> mockPage = mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(Collections.emptyList());
        when(mockPage.getTotal()).thenReturn(0L);
        when(mockPage.getPages()).thenReturn(0L);
        when(mockPage.getCurrent()).thenReturn(1L);
        when(mockPage.getSize()).thenReturn(10L);
        when(baseMapper.selectStudentClassesWithDetails(any(Page.class), isNull(), isNull(), isNull()))
                .thenReturn(mockPage);

        // Act
        Map<String, Object> result = studentClassService.getStudentClassesByPage(params);

        // Assert
        assertNotNull(result);
        assertEquals(0L, result.get("total"));
    }

    // ==================== getStudentClassById tests ====================

    @Test
    void getStudentClassById_found_returnsStudentClass() {
        // Arrange
        when(baseMapper.selectById(1000L)).thenReturn(testStudentClass);

        // Act
        StudentClass result = studentClassService.getStudentClassById(1000L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getStudentId());
        assertEquals(100L, result.getClassId());
    }

    @Test
    void getStudentClassById_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectById(9999L)).thenReturn(null);

        // Act
        StudentClass result = studentClassService.getStudentClassById(9999L);

        // Assert
        assertNull(result);
    }

    // ==================== batchAddStudentsToClass tests ====================

    @Test
    void batchAddStudentsToClass_success_addsMultipleStudents() {
        // Arrange
        when(classService.getClassById(100L)).thenReturn(testClass);

        User student2 = new User();
        student2.setId(2L);
        student2.setRole("USER");

        when(userService.getUserById(1L)).thenReturn(testStudent);
        when(userService.getUserById(2L)).thenReturn(student2);
        when(classService.getClassById(100L)).thenReturn(testClass);
        when(baseMapper.insert(any(StudentClass.class))).thenReturn(1);

        // Act
        studentClassService.batchAddStudentsToClass(100L, Arrays.asList(1L, 2L));

        // Assert
        verify(baseMapper, times(2)).insert(any(StudentClass.class));
    }

    @Test
    void batchAddStudentsToClass_nullList_doesNothing() {
        // Act
        studentClassService.batchAddStudentsToClass(100L, null);

        // Assert: no interactions with classService or baseMapper
        verify(classService, never()).getClassById(anyLong());
    }

    @Test
    void batchAddStudentsToClass_emptyList_doesNothing() {
        // Act
        studentClassService.batchAddStudentsToClass(100L, Collections.emptyList());

        // Assert
        verify(classService, never()).getClassById(anyLong());
    }

    @Test
    void batchAddStudentsToClass_classNotFound_throwsBusinessException() {
        // Arrange
        when(classService.getClassById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.batchAddStudentsToClass(999L, Arrays.asList(1L)));
        assertEquals("班级不存在", ex.getMessage());
    }

    @Test
    void batchAddStudentsToClass_partialFailure_continuesWithOthers() {
        // Arrange: first student fails validation, second succeeds
        when(classService.getClassById(100L)).thenReturn(testClass);
        when(userService.getUserById(999L)).thenReturn(null); // triggers BusinessException
        when(userService.getUserById(1L)).thenReturn(testStudent);
        when(baseMapper.insert(any(StudentClass.class))).thenReturn(1);

        // Act: should not throw, swallows individual failures
        studentClassService.batchAddStudentsToClass(100L, Arrays.asList(999L, 1L));

        // Assert: at least one insert happened for the successful student
        verify(baseMapper, atLeastOnce()).insert(any(StudentClass.class));
    }

    // ==================== batchRemoveStudentsFromClass tests ====================

    @Test
    void batchRemoveStudentsFromClass_success_removesMultiple() {
        // Arrange
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(1);
        when(baseMapper.checkStudentInClass(2L, 100L)).thenReturn(1);
        when(baseMapper.delete(any(QueryWrapper.class))).thenReturn(1);

        // Act
        studentClassService.batchRemoveStudentsFromClass(100L, Arrays.asList(1L, 2L));

        // Assert
        verify(baseMapper, times(2)).delete(any(QueryWrapper.class));
    }

    @Test
    void batchRemoveStudentsFromClass_nullList_doesNothing() {
        // Act
        studentClassService.batchRemoveStudentsFromClass(100L, null);

        // Assert
        verify(baseMapper, never()).delete(any(QueryWrapper.class));
    }

    @Test
    void batchRemoveStudentsFromClass_emptyList_doesNothing() {
        // Act
        studentClassService.batchRemoveStudentsFromClass(100L, Collections.emptyList());

        // Assert
        verify(baseMapper, never()).delete(any(QueryWrapper.class));
    }

    // ==================== getStudentCountByClassId / getClassCountByStudentId ====================

    @Test
    void getStudentCountByClassId_returnsCount() {
        // Arrange
        when(baseMapper.selectStudentCountByClassId(100L)).thenReturn(25L);

        // Act & Assert
        assertEquals(25L, studentClassService.getStudentCountByClassId(100L));
    }

    @Test
    void getClassCountByStudentId_returnsCount() {
        // Arrange
        when(baseMapper.selectClassCountByStudentId(1L)).thenReturn(3L);

        // Act & Assert
        assertEquals(3L, studentClassService.getClassCountByStudentId(1L));
    }

    // ==================== addStudentToClass (teacher) tests ====================

    @Test
    void addStudentToClass_teacherHasPermission_success() {
        // Arrange
        when(classService.validateTeacherPermission(10L, 100L)).thenReturn(true);
        when(userService.getUserById(1L)).thenReturn(testStudent);
        when(classService.getClassById(100L)).thenReturn(testClass);
        when(baseMapper.insert(any(StudentClass.class))).thenReturn(1);

        // Act
        studentClassService.addStudentToClass(10L, 100L, 1L);

        // Assert
        verify(baseMapper).insert(any(StudentClass.class));
    }

    @Test
    void addStudentToClass_teacherNoPermission_throwsBusinessException() {
        // Arrange
        when(classService.validateTeacherPermission(10L, 200L)).thenReturn(false);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.addStudentToClass(10L, 200L, 1L));
        assertEquals("教师没有权限管理此班级", ex.getMessage());
    }

    // ==================== removeStudentFromClass (teacher) tests ====================

    @Test
    void removeStudentFromClass_teacherHasPermission_success() {
        // Arrange
        when(classService.validateTeacherPermission(10L, 100L)).thenReturn(true);
        when(baseMapper.checkStudentInClass(1L, 100L)).thenReturn(1);
        when(baseMapper.delete(any(QueryWrapper.class))).thenReturn(1);

        // Act
        studentClassService.removeStudentFromClass(10L, 100L, 1L);

        // Assert
        verify(baseMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void removeStudentFromClass_teacherNoPermission_throwsBusinessException() {
        // Arrange
        when(classService.validateTeacherPermission(10L, 200L)).thenReturn(false);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentClassService.removeStudentFromClass(10L, 200L, 1L));
        assertEquals("教师没有权限管理此班级", ex.getMessage());
    }
}
