package com.english.service;

import com.english.entity.SchoolStudent;
import com.english.entity.SchoolTeacher;
import com.english.entity.User;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
