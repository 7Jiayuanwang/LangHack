package com.english.service;

import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.impl.SchoolDataPersistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolDataPersistServiceImplTest {

    private SchoolDataPersistServiceImpl persistService;

    @Mock
    private FieldMappingService fieldMappingService;
    @Mock
    private SchoolDataApiClient apiClient;
    @Mock
    private AdministrativeClassMapper administrativeClassMapper;
    @Mock
    private SchoolCourseMapper schoolCourseMapper;
    @Mock
    private TeachingClassMapper teachingClassMapper;
    @Mock
    private CourseScheduleMapper courseScheduleMapper;
    @Mock
    private CourseSelectionMapper courseSelectionMapper;
    @Mock
    private LevelExamScoreMapper levelExamScoreMapper;
    @Mock
    private TeachingPlanMapper teachingPlanMapper;
    @Mock
    private TeachingPlanCourseMapper teachingPlanCourseMapper;
    @Mock
    private StudentScheduleMapper studentScheduleMapper;
    @Mock
    private SchoolStudentMapper schoolStudentMapper;
    @Mock
    private SchoolTeacherMapper schoolTeacherMapper;
    @Mock
    private TeacherStudentBindingMapper teacherStudentBindingMapper;
    @Mock
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        persistService = new SchoolDataPersistServiceImpl(
                fieldMappingService,
                apiClient,
                administrativeClassMapper,
                schoolCourseMapper,
                teachingClassMapper,
                courseScheduleMapper,
                courseSelectionMapper,
                levelExamScoreMapper,
                teachingPlanMapper,
                teachingPlanCourseMapper,
                studentScheduleMapper,
                schoolStudentMapper,
                schoolTeacherMapper,
                teacherStudentBindingMapper,
                userMapper
        );
    }

    @Test
    @DisplayName("persistMappedRecord 写入 school_student 时保留目录与同步时间字段")
    void persistMappedRecord_schoolStudent_passesAllDirectoryFields() {
        LocalDateTime syncTime = LocalDateTime.of(2026, 4, 22, 10, 0);
        LocalDateTime lastExamSyncTime = LocalDateTime.of(2026, 4, 21, 9, 0);
        LocalDateTime lastScheduleSyncTime = LocalDateTime.of(2026, 4, 20, 8, 0);

        Map<String, Object> mapped = new HashMap<>();
        mapped.put("studentId", "2023122066164");
        mapped.put("studentName", "张三");
        mapped.put("collegeName", "外国语学院");
        mapped.put("collegeCode", "FL");
        mapped.put("classCode", "FL2301");
        mapped.put("className", "外语2301");
        mapped.put("grade", "2023");
        mapped.put("majorName", "英语");
        mapped.put("sourceCourseCodes", "ENG101,ENG102");
        mapped.put("syncTime", syncTime);
        mapped.put("lastExamSyncTime", lastExamSyncTime);
        mapped.put("lastScheduleSyncTime", lastScheduleSyncTime);
        mapped.put("syncSource", "CHAIN_SYNC");

        persistService.persistMappedRecord("school_student", mapped);

        ArgumentCaptor<SchoolStudent> captor = ArgumentCaptor.forClass(SchoolStudent.class);
        verify(schoolStudentMapper).upsertByStudentId(captor.capture());
        SchoolStudent entity = captor.getValue();
        assertEquals("2023122066164", entity.getStudentId());
        assertEquals("FL2301", entity.getClassCode());
        assertEquals("外语2301", entity.getClassName());
        assertEquals("英语", entity.getMajorName());
        assertEquals(lastExamSyncTime, entity.getLastExamSyncTime());
        assertEquals(lastScheduleSyncTime, entity.getLastScheduleSyncTime());
        assertEquals("CHAIN_SYNC", entity.getSyncSource());
    }

    @Test
    @DisplayName("persistMappedRecord 写入 school_teacher 时保留 lastScheduleSyncTime")
    void persistMappedRecord_schoolTeacher_passesLastScheduleSyncTime() {
        LocalDateTime syncTime = LocalDateTime.of(2026, 4, 22, 11, 0);
        LocalDateTime lastScheduleSyncTime = LocalDateTime.of(2026, 4, 21, 7, 30);

        Map<String, Object> mapped = new HashMap<>();
        mapped.put("teacherId", "T001");
        mapped.put("teacherName", "李老师");
        mapped.put("collegeName", "外国语学院");
        mapped.put("collegeCode", "FL");
        mapped.put("syncTime", syncTime);
        mapped.put("lastScheduleSyncTime", lastScheduleSyncTime);
        mapped.put("syncSource", "CHAIN_SYNC");

        persistService.persistMappedRecord("school_teacher", mapped);

        ArgumentCaptor<SchoolTeacher> captor = ArgumentCaptor.forClass(SchoolTeacher.class);
        verify(schoolTeacherMapper).upsertByTeacherId(captor.capture());
        SchoolTeacher entity = captor.getValue();
        assertEquals("T001", entity.getTeacherId());
        assertEquals(lastScheduleSyncTime, entity.getLastScheduleSyncTime());
        assertEquals("CHAIN_SYNC", entity.getSyncSource());
    }

    @Test
    @DisplayName("course_schedule batch keeps all rows for the same class semester")
    void persistRawData_courseScheduleDeletesOncePerBatchKey() {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setClassId("CLS1");
        teachingClass.setAcademicYear("2025");
        teachingClass.setSemester(1);
        when(teachingClassMapper.selectOne(any())).thenReturn(teachingClass);

        Map<String, Object> mapped1 = new HashMap<>();
        mapped1.put("classId", "CLS1");
        mapped1.put("academicYear", "2025");
        mapped1.put("semester", 1);
        mapped1.put("dayOfWeek", 1);
        mapped1.put("period", "1-2");
        mapped1.put("weekRange", "1-16");
        mapped1.put("classLocation", "A101");

        Map<String, Object> mapped2 = new HashMap<>(mapped1);
        mapped2.put("dayOfWeek", 3);
        mapped2.put("period", "3-4");

        Map<String, Object> raw1 = Map.of("row", 1);
        Map<String, Object> raw2 = Map.of("row", 2);
        when(fieldMappingService.applyMapping(eq("course_schedule"), eq(raw1))).thenReturn(mapped1);
        when(fieldMappingService.applyMapping(eq("course_schedule"), eq(raw2))).thenReturn(mapped2);

        persistService.persistRawData("course_schedule", List.of(raw1, raw2), "SCHOOL_API");

        verify(courseScheduleMapper, times(1)).deleteByClassIdAndSemester("CLS1", "2025", 1);
        verify(courseScheduleMapper, times(2)).insert(any(CourseSchedule.class));
    }

    @Test
    @DisplayName("course_selection enriches teaching class fields and binds teacher to student")
    void persistMappedRecord_courseSelection_enrichesAndCreatesBinding() {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setClassId("CLS1");
        teachingClass.setCourseCode("ENG101");
        teachingClass.setCourseName("College English");
        teachingClass.setTeacherId("T001");
        teachingClass.setTeacherName("Teacher Li");
        teachingClass.setAcademicYear("2025");
        teachingClass.setSemester(1);
        when(teachingClassMapper.selectOne(any())).thenReturn(teachingClass);

        LocalDateTime syncTime = LocalDateTime.of(2026, 4, 22, 13, 0);
        Map<String, Object> mapped = new HashMap<>();
        mapped.put("studentId", "2023001");
        mapped.put("classId", "CLS1");
        mapped.put("syncTime", syncTime);
        mapped.put("syncSource", "SCHOOL_API");

        persistService.persistMappedRecord("course_selection", mapped);

        ArgumentCaptor<CourseSelection> selectionCaptor = ArgumentCaptor.forClass(CourseSelection.class);
        verify(courseSelectionMapper).upsertBySelectionId(selectionCaptor.capture());
        CourseSelection selection = selectionCaptor.getValue();
        assertEquals("2023001_CLS1_2025_1", selection.getSelectionId());
        assertEquals("ENG101", selection.getCourseCode());
        assertEquals("College English", selection.getCourseName());
        assertEquals("T001", selection.getTeacherId());
        assertEquals("2025", selection.getAcademicYear());
        assertEquals(1, selection.getSemester());
        assertEquals("SELECTED", selection.getSelectionStatus());

        ArgumentCaptor<TeacherStudentBinding> bindingCaptor = ArgumentCaptor.forClass(TeacherStudentBinding.class);
        verify(teacherStudentBindingMapper).upsertBinding(bindingCaptor.capture());
        TeacherStudentBinding binding = bindingCaptor.getValue();
        assertEquals("T001", binding.getTeacherId());
        assertEquals("2023001", binding.getStudentId());
        assertEquals("ENG101", binding.getCourseCode());
        assertEquals("CLS1", binding.getCourseClassCode());
        assertEquals("2025", binding.getAcademicYear());
        assertEquals("1", binding.getSemester());
    }

    @Test
    @DisplayName("student_schedule batch keeps all rows for the same student semester")
    void persistRawData_studentScheduleDeletesOncePerBatchKey() {
        Map<String, Object> mapped1 = new HashMap<>();
        mapped1.put("studentId", "2023001");
        mapped1.put("studentName", "Student Zhang");
        mapped1.put("classId", "CLS1");
        mapped1.put("courseCode", "ENG101");
        mapped1.put("courseName", "College English");
        mapped1.put("academicYear", "2025");
        mapped1.put("semester", 1);
        mapped1.put("dayOfWeek", 1);
        mapped1.put("period", "1-2");

        Map<String, Object> mapped2 = new HashMap<>(mapped1);
        mapped2.put("dayOfWeek", 4);
        mapped2.put("period", "5-6");

        Map<String, Object> raw1 = Map.of("row", 1);
        Map<String, Object> raw2 = Map.of("row", 2);
        when(fieldMappingService.applyMapping(eq("student_schedule"), eq(raw1))).thenReturn(mapped1);
        when(fieldMappingService.applyMapping(eq("student_schedule"), eq(raw2))).thenReturn(mapped2);

        persistService.persistRawData("student_schedule", List.of(raw1, raw2), "SCHOOL_API");

        verify(studentScheduleMapper, times(1)).delete(any());
        verify(studentScheduleMapper, times(2)).insert(any(StudentSchedule.class));
    }

    @Test
    @DisplayName("level_exam_scores 姓名回填只读查询用户，不修改角色")
    void persistMappedRecord_levelExamScores_readsUserNameOnly() {
        User user = new User();
        user.setUsername("2023122066164");
        user.setRealName("张三");
        when(userMapper.selectActiveByUsername("2023122066164")).thenReturn(user);

        Map<String, Object> mapped = new HashMap<>();
        mapped.put("studentId", "2023122066164");
        mapped.put("examName", "大学英语四级");
        mapped.put("examDate", java.time.LocalDate.of(2026, 4, 1));
        mapped.put("score", 500);
        mapped.put("syncTime", LocalDateTime.of(2026, 4, 22, 12, 0));
        mapped.put("syncSource", "SCHOOL_API");

        persistService.persistMappedRecord("level_exam_scores", mapped);

        verify(userMapper).selectActiveByUsername("2023122066164");
    }
}
