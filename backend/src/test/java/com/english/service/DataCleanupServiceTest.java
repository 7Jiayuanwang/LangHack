package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.mapper.ClassMapper;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.UserMapper;
import com.english.service.impl.DataCleanupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataCleanupServiceImpl.
 *
 * Key observations from source code:
 * - DataCleanupServiceImpl is NOT extending ServiceImpl (standalone @Service)
 * - Injects UserMapper, ClassMapper, StudentClassMapper, UserService, Environment
 * - autoCleanupInconsistentData() runs on @PostConstruct, only if "dev" profile active
 * - cleanupInconsistentStudentClasses() iterates all student_classes, deletes entries
 *   where student is missing/wrong role OR class is missing
 * - ensureTeachersNoDefaultClasses() queries teachers, logs their class counts
 * - ensureStudentsNoDefaultClasses() queries students, logs their class counts
 * - printDataStatistics() queries counts for admins, teachers, students, classes, student_classes
 * - cleanupInconsistentData() is the manual trigger that calls all cleanup steps
 */
@ExtendWith(MockitoExtension.class)
class DataCleanupServiceTest {

    @InjectMocks
    private DataCleanupServiceImpl dataCleanupService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private StudentClassMapper studentClassMapper;

    @Mock
    private UserService userService;

    @Mock
    private Environment environment;

    private User testStudent;
    private User testTeacher;
    private Class testClass;

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
    }

    // ==================== autoCleanupInconsistentData tests ====================

    @Test
    void autoCleanupInconsistentData_devProfile_executesCleanup() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        // Setup mocks for the cleanup chain
        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(classMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(studentClassMapper.selectList(isNull())).thenReturn(Collections.emptyList());

        // Act
        dataCleanupService.autoCleanupInconsistentData();

        // Assert: cleanup was executed (selectList on studentClassMapper was called for cleanup)
        verify(studentClassMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void autoCleanupInconsistentData_nonDevProfile_doesNothing() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        // Act
        dataCleanupService.autoCleanupInconsistentData();

        // Assert: no cleanup executed
        verify(studentClassMapper, never()).selectList(any());
        verify(userMapper, never()).selectList(any());
    }

    @Test
    void autoCleanupInconsistentData_noActiveProfiles_doesNothing() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{});

        // Act
        dataCleanupService.autoCleanupInconsistentData();

        // Assert
        verify(studentClassMapper, never()).selectList(any());
    }

    @Test
    void autoCleanupInconsistentData_multipleProfiles_includesDev_executesCleanup() {
        // Arrange: multiple profiles, one of which is "dev"
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "docker"});
        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(classMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(studentClassMapper.selectList(isNull())).thenReturn(Collections.emptyList());

        // Act
        dataCleanupService.autoCleanupInconsistentData();

        // Assert
        verify(studentClassMapper).selectList(any(QueryWrapper.class));
    }

    // ==================== cleanupInconsistentStudentClasses tests ====================

    @Test
    void cleanupInconsistentStudentClasses_noRecords_doesNothing() {
        // Arrange
        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert: no deleteBatchIds called
        verify(studentClassMapper, never()).deleteBatchIds(any());
    }

    @Test
    void cleanupInconsistentStudentClasses_nullResult_doesNothing() {
        // Arrange
        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert
        verify(studentClassMapper, never()).deleteBatchIds(any());
    }

    @Test
    void cleanupInconsistentStudentClasses_allValid_deletesNothing() {
        // Arrange
        StudentClass sc = new StudentClass();
        sc.setId(1L);
        sc.setStudentId(1L);
        sc.setClassId(100L);

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(sc));
        when(userMapper.selectById(1L)).thenReturn(testStudent);
        when(classMapper.selectById(100L)).thenReturn(testClass);

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert: no deleteBatchIds since all records are valid
        verify(studentClassMapper, never()).deleteBatchIds(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupInconsistentStudentClasses_studentNotFound_deletesInvalidRecord() {
        // Arrange
        StudentClass sc = new StudentClass();
        sc.setId(1L);
        sc.setStudentId(999L);
        sc.setClassId(100L);

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(sc));
        when(userMapper.selectById(999L)).thenReturn(null);

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(studentClassMapper).deleteBatchIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(1L, captor.getValue().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupInconsistentStudentClasses_studentWrongRole_deletesInvalidRecord() {
        // Arrange: user exists but has TEACHER role
        StudentClass sc = new StudentClass();
        sc.setId(2L);
        sc.setStudentId(10L);
        sc.setClassId(100L);

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(sc));
        when(userMapper.selectById(10L)).thenReturn(testTeacher);

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(studentClassMapper).deleteBatchIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(2L, captor.getValue().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupInconsistentStudentClasses_classNotFound_deletesInvalidRecord() {
        // Arrange: student valid but class does not exist
        StudentClass sc = new StudentClass();
        sc.setId(3L);
        sc.setStudentId(1L);
        sc.setClassId(999L);

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(sc));
        when(userMapper.selectById(1L)).thenReturn(testStudent);
        when(classMapper.selectById(999L)).thenReturn(null);

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(studentClassMapper).deleteBatchIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(3L, captor.getValue().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupInconsistentStudentClasses_mixedValidAndInvalid_deletesOnlyInvalid() {
        // Arrange: two records, one valid, one invalid (student missing)
        StudentClass validSc = new StudentClass();
        validSc.setId(1L);
        validSc.setStudentId(1L);
        validSc.setClassId(100L);

        StudentClass invalidSc = new StudentClass();
        invalidSc.setId(2L);
        invalidSc.setStudentId(999L);
        invalidSc.setClassId(100L);

        when(studentClassMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(validSc, invalidSc));
        when(userMapper.selectById(1L)).thenReturn(testStudent);
        when(classMapper.selectById(100L)).thenReturn(testClass);
        when(userMapper.selectById(999L)).thenReturn(null);

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert: only the invalid record deleted
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(studentClassMapper).deleteBatchIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(2L, captor.getValue().get(0));
    }

    @Test
    void cleanupInconsistentStudentClasses_userRoleIsUser_isValid() {
        // Arrange: USER role should be considered valid
        StudentClass sc = new StudentClass();
        sc.setId(1L);
        sc.setStudentId(2L);
        sc.setClassId(100L);

        User userRoleStudent = new User();
        userRoleStudent.setId(2L);
        userRoleStudent.setRole("USER");

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(sc));
        when(userMapper.selectById(2L)).thenReturn(userRoleStudent);
        when(classMapper.selectById(100L)).thenReturn(testClass);

        // Act
        dataCleanupService.cleanupInconsistentStudentClasses();

        // Assert: no deletion since USER is valid
        verify(studentClassMapper, never()).deleteBatchIds(any());
    }

    // ==================== ensureTeachersNoDefaultClasses tests ====================

    @Test
    void ensureTeachersNoDefaultClasses_noTeachers_doesNothing() {
        // Arrange
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        dataCleanupService.ensureTeachersNoDefaultClasses();

        // Assert: no classMapper interactions for individual teachers
        verify(classMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void ensureTeachersNoDefaultClasses_nullTeachers_doesNothing() {
        // Arrange
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        // Act
        dataCleanupService.ensureTeachersNoDefaultClasses();

        // Assert
        verify(classMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void ensureTeachersNoDefaultClasses_teacherWithClasses_logsInfo() {
        // Arrange
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(testTeacher));
        when(classMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(testClass));

        // Act: does not throw, just logs
        assertDoesNotThrow(() -> dataCleanupService.ensureTeachersNoDefaultClasses());

        // Assert: queries were made
        verify(classMapper).selectList(any(QueryWrapper.class));
    }

    // ==================== ensureStudentsNoDefaultClasses tests ====================

    @Test
    void ensureStudentsNoDefaultClasses_noStudents_doesNothing() {
        // Arrange
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        dataCleanupService.ensureStudentsNoDefaultClasses();

        // Assert
        verify(studentClassMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void ensureStudentsNoDefaultClasses_nullStudents_doesNothing() {
        // Arrange
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        // Act
        dataCleanupService.ensureStudentsNoDefaultClasses();

        // Assert
        verify(studentClassMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void ensureStudentsNoDefaultClasses_studentWithClasses_logsInfo() {
        // Arrange
        StudentClass sc = new StudentClass();
        sc.setId(1L);
        sc.setStudentId(1L);
        sc.setClassId(100L);

        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(testStudent));
        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(sc));

        // Act: does not throw, just logs
        assertDoesNotThrow(() -> dataCleanupService.ensureStudentsNoDefaultClasses());
    }

    // ==================== printDataStatistics tests ====================

    @Test
    void printDataStatistics_success_queriesAllCounts() {
        // Arrange
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(2L); // called 3 times: admin, teacher, student
        when(classMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(10L);
        when(studentClassMapper.selectList(isNull())).thenReturn(Collections.emptyList());

        // Act
        assertDoesNotThrow(() -> dataCleanupService.printDataStatistics());

        // Assert: all count queries made
        verify(userMapper, times(3)).selectCount(any(QueryWrapper.class));
        verify(classMapper).selectCount(any(QueryWrapper.class));
        verify(studentClassMapper).selectCount(any(QueryWrapper.class));
    }

    @Test
    void printDataStatistics_withStudentClassRecords_logsDetails() {
        // Arrange
        StudentClass sc = new StudentClass();
        sc.setStudentId(1L);
        sc.setClassId(100L);
        sc.setJoinTime(LocalDateTime.of(2026, 3, 1, 10, 0));

        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(classMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(studentClassMapper.selectList(isNull())).thenReturn(Arrays.asList(sc));
        when(userMapper.selectById(1L)).thenReturn(testStudent);
        when(classMapper.selectById(100L)).thenReturn(testClass);

        // Act
        assertDoesNotThrow(() -> dataCleanupService.printDataStatistics());

        // Assert: individual record lookups
        verify(userMapper).selectById(1L);
        verify(classMapper).selectById(100L);
    }

    @Test
    void printDataStatistics_nullStudentAndClass_handlesGracefully() {
        // Arrange: student class record references non-existent student and class
        StudentClass sc = new StudentClass();
        sc.setStudentId(999L);
        sc.setClassId(999L);
        sc.setJoinTime(LocalDateTime.now());

        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(classMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(studentClassMapper.selectList(isNull())).thenReturn(Arrays.asList(sc));
        when(userMapper.selectById(999L)).thenReturn(null);
        when(classMapper.selectById(999L)).thenReturn(null);

        // Act: should not throw, logs "未知" for missing student/class
        assertDoesNotThrow(() -> dataCleanupService.printDataStatistics());
    }

    // ==================== cleanupInconsistentData (manual trigger) tests ====================

    @Test
    void cleanupInconsistentData_executesAllSteps() {
        // Arrange: setup all mocks for the full cleanup chain
        // cleanupInconsistentStudentClasses
        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        // ensureTeachersNoDefaultClasses
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        // printDataStatistics
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(classMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(studentClassMapper.selectList(isNull())).thenReturn(Collections.emptyList());

        // Act
        dataCleanupService.cleanupInconsistentData();

        // Assert: all three cleanup steps plus statistics were called
        // studentClassMapper.selectList called once for cleanup, once for printDataStatistics
        verify(studentClassMapper, atLeastOnce()).selectList(any());
        verify(userMapper, atLeastOnce()).selectList(any());
    }
}
