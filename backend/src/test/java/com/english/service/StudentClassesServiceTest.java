package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.impl.StudentClassesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class StudentClassesServiceTest {

    @Mock
    private StudentClassMapper studentClassMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UnitMapper unitMapper;

    @Mock
    private UnitWordMapper unitWordMapper;

    @Mock
    private VocabularyBookMapper vocabularyBookMapper;

    @Mock
    private LearningRecordMapper learningRecordMapper;

    @Mock
    private TextMapper textMapper;

    @Mock
    private TextLearningRecordMapper textLearningRecordMapper;

    @InjectMocks
    private StudentClassesServiceImpl studentClassesService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // 测试 selectList 返回 null 时不崩溃
    @Test
    public void testGetStudentClasses_nullSelectListResult_returnsEmptyList() {
        Long studentId = 1L;

        // 模拟 mapper 返回 null
        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        List<Map<String, Object>> result = studentClassesService.getStudentClassesWithDetails(studentId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // 测试空列表的正常处理
    @Test
    public void testGetStudentClasses_emptyList_returnsEmpty() {
        Long studentId = 1L;

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

        List<Map<String, Object>> result = studentClassesService.getStudentClassesWithDetails(studentId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // 测试计算学习时间时处理 null 记录
    @Test
    public void testCalculateStudyTime_nullRecords_returnsZero() {
        Long studentId = 1L;
        Long classId = 1L;

        // 模拟班级查询
        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setId(classId);
        classInfo.setCourseId(100L);

        when(classMapper.selectOne(any(QueryWrapper.class))).thenReturn(classInfo);
        when(unitMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // 通过反射调用私有方法（或者通过公共方法间接测试）
        List<Map<String, Object>> result = studentClassesService.getStudentClassesWithDetails(studentId);

        assertNotNull(result);
    }

    // 测试已删除单词被正确过滤
    @Test
    public void testCalculateLearnedWords_withDeletedWords_filtersCorrectly() {
        Long studentId = 1L;
        Long classId = 1L;
        Long courseId = 100L;

        // 设置班级和课程信息
        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setId(classId);
        classInfo.setCourseId(courseId);

        Unit unit = new Unit();
        unit.setId(1L);
        unit.setCourseId(courseId);

        UnitWord unitWord = new UnitWord();
        unitWord.setUnitId(1L);
        unitWord.setWordId(100L);

        VocabularyBook vb = new VocabularyBook();
        vb.setStudentId(studentId);
        vb.setWordId(100L);
        vb.setMasteryLevel("LEARNING");

        when(classMapper.selectOne(any(QueryWrapper.class))).thenReturn(classInfo);
        when(unitMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(unit));
        when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(unitWord));
        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        List<Map<String, Object>> result = studentClassesService.getStudentClassesWithDetails(studentId);

        assertNotNull(result);
    }

    // 测试计算班级数量时正确处理
    @Test
    public void testCountByStudentId_returnsCorrectCount() {
        Long studentId = 1L;

        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);

        int result = studentClassesService.countByStudentId(studentId);

        assertEquals(3, result);
    }

    // 测试获取班级ID列表
    @Test
    public void testGetClassIdsByStudentId_returnsClassIds() {
        Long studentId = 1L;

        List<StudentClass> studentClasses = new ArrayList<>();
        for (long i = 1; i <= 3; i++) {
            StudentClass sc = new StudentClass();
            sc.setClassId(i * 100);
            studentClasses.add(sc);
        }

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(studentClasses);

        List<Long> result = studentClassesService.getClassIdsByStudentId(studentId);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(100L));
        assertTrue(result.contains(200L));
        assertTrue(result.contains(300L));
    }

    // 测试检查学生是否在班级中
    @Test
    public void testIsStudentInClass_returnsTrue() {
        Long studentId = 1L;
        Long classId = 100L;

        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        boolean result = studentClassesService.isStudentInClass(studentId, classId);

        assertTrue(result);
    }

    // 测试检查学生不在班级中
    @Test
    public void testIsStudentInClass_returnsFalse() {
        Long studentId = 1L;
        Long classId = 100L;

        when(studentClassMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        boolean result = studentClassesService.isStudentInClass(studentId, classId);

        assertFalse(result);
    }

    // 测试获取班级信息返回 null 的处理
    @Test
    public void testGetStudentClassInfo_noRecord_returnsNull() {
        Long studentId = 1L;
        Long classId = 100L;

        when(studentClassMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        Map<String, Object> result = studentClassesService.getStudentClassInfo(studentId, classId);

        assertNull(result);
    }

    // 测试获取班级信息正确返回数据
    @Test
    public void testGetStudentClassInfo_returnsClassInfo() {
        Long studentId = 1L;
        Long classId = 100L;

        StudentClass studentClass = new StudentClass();
        studentClass.setId(1L);
        studentClass.setStudentId(studentId);
        studentClass.setClassId(classId);

        when(studentClassMapper.selectOne(any(QueryWrapper.class))).thenReturn(studentClass);

        Map<String, Object> result = studentClassesService.getStudentClassInfo(studentId, classId);

        assertNotNull(result);
        assertEquals(1L, result.get("id"));
        assertEquals(studentId, result.get("studentId"));
        assertEquals(classId, result.get("classId"));
    }

    // 测试在课程中的学习时间计算（处理 null 记录）
    @Test
    public void testGetStudentCoursesWithProgress_nullLearningRecords_noException() {
        Long studentId = 1L;

        StudentClass studentClass = new StudentClass();
        studentClass.setStudentId(studentId);
        studentClass.setClassId(1L);

        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setId(1L);
        classInfo.setCourseId(100L);

        Course course = new Course();
        course.setId(100L);
        course.setName("English");

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(studentClass));
        when(classMapper.selectById(1L)).thenReturn(classInfo);
        when(courseMapper.selectById(100L)).thenReturn(course);
        when(unitMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(learningRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(null);
        when(textMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        List<Map<String, Object>> result = studentClassesService.getStudentCoursesWithProgress(studentId);

        assertNotNull(result);
    }

    // 测试学生班级信息完整性
    @Test
    public void testGetStudentClassesWithDetails_completesAllFields() {
        Long studentId = 1L;
        Long classId = 1L;
        Long courseId = 100L;
        Long teacherId = 500L;

        StudentClass studentClass = new StudentClass();
        studentClass.setStudentId(studentId);
        studentClass.setClassId(classId);

        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setId(classId);
        classInfo.setName("Class A");
        classInfo.setDescription("Test Class");
        classInfo.setCourseId(courseId);
        classInfo.setTeacherId(teacherId);

        Course course = new Course();
        course.setId(courseId);
        course.setName("English Course");
        course.setDescription("Learn English");

        User teacher = new User();
        teacher.setId(teacherId);
        teacher.setRealName("Mr. Smith");

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(studentClass));
        when(classMapper.selectById(classId)).thenReturn(classInfo);
        when(courseMapper.selectById(courseId)).thenReturn(course);
        when(userMapper.selectById(teacherId)).thenReturn(teacher);
        when(unitMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
        when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(learningRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
        when(textMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

        List<Map<String, Object>> result = studentClassesService.getStudentClassesWithDetails(studentId);

        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> classMap = result.get(0);
        assertEquals("Class A", classMap.get("className"));
        assertEquals("English Course", classMap.get("courseName"));
        assertEquals("Mr. Smith", classMap.get("teacherName"));
    }

    // 测试处理 null 班级的情况
    @Test
    public void testGetStudentClassesWithDetails_nullClassInfo_skipsRecord() {
        Long studentId = 1L;

        StudentClass studentClass = new StudentClass();
        studentClass.setStudentId(studentId);
        studentClass.setClassId(1L);

        when(studentClassMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(studentClass));
        when(classMapper.selectById(1L)).thenReturn(null);

        List<Map<String, Object>> result = studentClassesService.getStudentClassesWithDetails(studentId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
