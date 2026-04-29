package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.*;
import com.english.exception.BusinessException;
import com.english.mapper.*;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import com.english.service.SchoolDataPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SchoolDataPersistServiceImpl implements SchoolDataPersistService {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataPersistServiceImpl.class);

    private final FieldMappingService fieldMappingService;
    private final SchoolDataApiClient apiClient;
    private final AdministrativeClassMapper administrativeClassMapper;
    private final SchoolCourseMapper schoolCourseMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseScheduleMapper courseScheduleMapper;
    private final CourseSelectionMapper courseSelectionMapper;
    private final LevelExamScoreMapper levelExamScoreMapper;
    private final TeachingPlanMapper teachingPlanMapper;
    private final TeachingPlanCourseMapper teachingPlanCourseMapper;
    private final StudentScheduleMapper studentScheduleMapper;
    private final SchoolStudentMapper schoolStudentMapper;
    private final SchoolTeacherMapper schoolTeacherMapper;
    private final UserMapper userMapper;

    public SchoolDataPersistServiceImpl(FieldMappingService fieldMappingService,
                                        @Qualifier("schoolDataApiClientDelegate") SchoolDataApiClient apiClient,
                                        AdministrativeClassMapper administrativeClassMapper,
                                        SchoolCourseMapper schoolCourseMapper,
                                        TeachingClassMapper teachingClassMapper,
                                        CourseScheduleMapper courseScheduleMapper,
                                        CourseSelectionMapper courseSelectionMapper,
                                        LevelExamScoreMapper levelExamScoreMapper,
                                        TeachingPlanMapper teachingPlanMapper,
                                        TeachingPlanCourseMapper teachingPlanCourseMapper,
                                        StudentScheduleMapper studentScheduleMapper,
                                        SchoolStudentMapper schoolStudentMapper,
                                        SchoolTeacherMapper schoolTeacherMapper,
                                        UserMapper userMapper) {
        this.fieldMappingService = fieldMappingService;
        this.apiClient = apiClient;
        this.administrativeClassMapper = administrativeClassMapper;
        this.schoolCourseMapper = schoolCourseMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.courseScheduleMapper = courseScheduleMapper;
        this.courseSelectionMapper = courseSelectionMapper;
        this.levelExamScoreMapper = levelExamScoreMapper;
        this.teachingPlanMapper = teachingPlanMapper;
        this.teachingPlanCourseMapper = teachingPlanCourseMapper;
        this.studentScheduleMapper = studentScheduleMapper;
        this.schoolStudentMapper = schoolStudentMapper;
        this.schoolTeacherMapper = schoolTeacherMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PersistResult persistRawData(String dataSource, List<Map<String, Object>> rawData, String syncSource) {
        int total = rawData == null ? 0 : rawData.size();
        int success = 0;
        int fail = 0;
        LocalDateTime syncTime = LocalDateTime.now();
        if (rawData == null) {
            return new PersistResult(0, 0, 0, 0);
        }
        for (Map<String, Object> sourceRecord : rawData) {
            try {
                Map<String, Object> mappedData = fieldMappingService.applyMapping(dataSource, sourceRecord);
                mappedData.put("syncTime", syncTime);
                mappedData.put("syncSource", syncSource);
                persistMappedRecord(dataSource, mappedData);
                success++;
            } catch (Exception e) {
                fail++;
                logger.warn("同步单条数据失败 dataSource={} recordPreview={} err={}",
                        dataSource, truncateRecordForLog(sourceRecord), e.getMessage(), e);
            }
        }
        return new PersistResult(total, success, fail, total - success - fail);
    }

    @Override
    public void persistMappedRecord(String dataSource, Map<String, Object> mappedData) {
        switch (dataSource) {
            case "administrative_class":
                upsertAdministrativeClass(mappedData);
                break;
            case "school_course":
            case "teaching_class":
                upsertSchoolCourse(mappedData);
                break;
            case "course_schedule":
                upsertCourseSchedule(mappedData);
                break;
            case "course_selection":
                upsertCourseSelection(mappedData);
                break;
            case "level_exam_scores":
                upsertLevelExamScore(mappedData);
                break;
            case "teaching_plans":
                upsertTeachingPlan(mappedData);
                break;
            case "teacher_courses":
                upsertTeachingClass(mappedData);
                break;
            case "student_schedule":
                upsertStudentSchedule(mappedData);
                break;
            case "school_student":
                upsertSchoolStudent(mappedData);
                break;
            case "school_teacher":
                upsertSchoolTeacher(mappedData);
                break;
            default:
                throw new BusinessException("不支持的数据源: " + dataSource);
        }
    }

    @Override
    public void persistLevelExamScores(List<Map<String, Object>> rawData, String syncSource, String studentId, LocalDateTime syncTime) {
        if (rawData == null) {
            return;
        }
        LocalDateTime effectiveSyncTime = syncTime == null ? LocalDateTime.now() : syncTime;
        for (Map<String, Object> sourceRecord : rawData) {
            try {
                Map<String, Object> mappedData = fieldMappingService.applyMapping("level_exam_scores", sourceRecord);
                mappedData.put("syncTime", effectiveSyncTime);
                mappedData.put("syncSource", syncSource);
                persistMappedRecord("level_exam_scores", mappedData);
            } catch (Exception e) {
                logger.warn("Lazy 四六级 upsert 失败 xh={} err={}", studentId, e.getMessage());
            }
        }
    }

    @Override
    public List<LevelExamScore> queryLevelExamScores(String studentId) {
        return levelExamScoreMapper.selectList(
                new QueryWrapper<LevelExamScore>().eq("student_id", studentId).orderByDesc("exam_date"));
    }

    private void upsertAdministrativeClass(Map<String, Object> data) {
        AdministrativeClass entity = new AdministrativeClass();
        entity.setClassCode(getStringValue(data, "classCode"));
        entity.setClassName(getStringValue(data, "className"));
        entity.setClassShortName(getStringValue(data, "classShortName"));
        entity.setCollegeCode(defaultIfBlank(getStringValue(data, "collegeCode"), "UNKNOWN"));
        entity.setCollegeName(defaultIfBlank(getStringValue(data, "collegeName"), "未知学院"));
        String majorCode = getStringValue(data, "majorCode");
        if (majorCode == null || majorCode.trim().isEmpty()) {
            majorCode = "UNKNOWN";
        }
        entity.setMajorCode(majorCode);
        entity.setMajorName(getStringValue(data, "majorName"));
        entity.setGrade(getStringValue(data, "grade"));
        entity.setClassType(getStringValue(data, "classType"));
        entity.setHeadTeacherCode(getStringValue(data, "headTeacherCode"));
        entity.setHeadTeacherName(getStringValue(data, "headTeacherName"));
        entity.setStudentCount(getIntegerValue(data, "studentCount"));
        entity.setMaleCount(getIntegerValue(data, "maleCount"));
        entity.setFemaleCount(getIntegerValue(data, "femaleCount"));
        entity.setClassStatus(defaultIfBlank(getStringValue(data, "classStatus"), "ACTIVE"));
        entity.setEstablishDate(getLocalDateValue(data, "establishDate"));
        entity.setGraduateDate(getLocalDateValue(data, "graduateDate"));
        entity.setRemark(getStringValue(data, "remark"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        administrativeClassMapper.upsertByClassCode(entity);
    }

    private void upsertSchoolCourse(Map<String, Object> data) {
        SchoolCourse entity = new SchoolCourse();
        entity.setCourseCode(getStringValue(data, "courseCode"));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setCourseNameEn(getStringValue(data, "courseNameEn"));
        entity.setCourseShortName(getStringValue(data, "courseShortName"));
        entity.setCourseNature(defaultIfBlank(getStringValue(data, "courseNature"), "必修"));
        entity.setCourseCategory(defaultIfBlank(getStringValue(data, "courseCategory"), "专业课"));
        entity.setCredits(getBigDecimalValue(data, "credits"));
        entity.setTotalHours(getIntegerValue(data, "totalHours"));
        entity.setTheoryHours(getIntegerValue(data, "theoryHours"));
        entity.setExperimentHours(getIntegerValue(data, "experimentHours"));
        entity.setPracticeHours(getIntegerValue(data, "practiceHours"));
        entity.setComputerHours(getIntegerValue(data, "computerHours"));
        entity.setCollegeCode(defaultIfBlank(getStringValue(data, "collegeCode"), "UNKNOWN"));
        entity.setCollegeName(getStringValue(data, "collegeName"));
        entity.setDepartmentCode(getStringValue(data, "departmentCode"));
        entity.setDepartmentName(getStringValue(data, "departmentName"));
        entity.setLeaderCode(getStringValue(data, "leaderCode"));
        entity.setLeaderName(getStringValue(data, "leaderName"));
        entity.setAssessmentMethod(getStringValue(data, "assessmentMethod"));
        entity.setIsBilingual(getStringValue(data, "isBilingual"));
        entity.setIsOnline(getStringValue(data, "isOnline"));
        entity.setApplicableMajors(getStringValue(data, "applicableMajors"));
        entity.setPrerequisiteCodes(getStringValue(data, "prerequisiteCodes"));
        entity.setPrerequisiteNames(getStringValue(data, "prerequisiteNames"));
        entity.setCourseIntro(getStringValue(data, "courseIntro"));
        entity.setTeachingObjectives(getStringValue(data, "teachingObjectives"));
        entity.setCourseStatus(defaultIfBlank(getStringValue(data, "courseStatus"), "ACTIVE"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        schoolCourseMapper.upsertByCourseCode(entity);
    }

    private void upsertTeachingClass(Map<String, Object> data) {
        TeachingClass entity = new TeachingClass();
        entity.setClassId(getStringValue(data, "classId"));
        entity.setClassName(getStringValue(data, "className"));
        entity.setCourseCode(getStringValue(data, "courseCode"));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setTeacherId(getStringValue(data, "teacherId"));
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setAcademicYear(getStringValue(data, "academicYear"));
        entity.setSemester(parseSchoolSemester(data, "semester"));
        Integer capacity = getIntegerValue(data, "capacity");
        if (capacity == null) {
            capacity = 100;
        }
        entity.setCapacity(capacity);
        entity.setEnrolledCount(getIntegerValue(data, "enrolledCount"));
        entity.setClassStatus(defaultIfBlank(getStringValue(data, "classStatus"), "ACTIVE"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        teachingClassMapper.upsertByClassId(entity);
    }

    private void upsertCourseSchedule(Map<String, Object> data) {
        String classId = getStringValue(data, "classId");
        String academicYear = getStringValue(data, "academicYear");
        Integer semester = parseSchoolSemester(data, "semester");
        Integer dayOfWeek = getIntegerValue(data, "dayOfWeek");
        String period = getStringValue(data, "period");
        String weekRange = getStringValue(data, "weekRange");
        String classLocation = getStringValue(data, "classLocation");

        // 验证必填字段：classId, academicYear, semester, dayOfWeek, period, weekRange, classLocation
        if (classId == null || academicYear == null || semester == null) {
            logger.warn("课程安排缺少主键字段，跳过 classId={} academicYear={} semester={}", classId, academicYear, semester);
            return;
        }
        if (dayOfWeek == null || period == null || weekRange == null || classLocation == null) {
            logger.warn("课程安排缺少必填字段，跳过 classId={} dayOfWeek={} period={} weekRange={} classLocation={}",
                    classId, dayOfWeek, period, weekRange, classLocation);
            return;
        }

        // 外键检查：验证 teaching_class 表中是否存在该 classId
        TeachingClass teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            logger.warn("课程安排外键检查失败，teaching_class 表中不存在 classId={}，跳过同步", classId);
            return;
        }

        courseScheduleMapper.deleteByClassIdAndSemester(classId, academicYear, semester);
        CourseSchedule entity = new CourseSchedule();
        entity.setClassId(classId);
        entity.setDayOfWeek(dayOfWeek);
        entity.setPeriod(period);
        entity.setWeekRange(weekRange);
        entity.setClassLocation(classLocation);
        entity.setAcademicYear(academicYear);
        entity.setSemester(semester);
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        courseScheduleMapper.insert(entity);
    }

    private void upsertCourseSelection(Map<String, Object> data) {
        CourseSelection entity = new CourseSelection();
        String studentId = getStringValue(data, "studentId");
        String classId = getClassIdValue(data);
        String academicYear = defaultIfBlank(getStringValue(data, "academicYear"), "UNKNOWN");
        Integer semester = parseSchoolSemester(data, "semester");
        if (semester == null) {
            semester = 0;
        }
        String selectionId = getStringValue(data, "selectionId");
        if (selectionId == null || selectionId.trim().isEmpty()) {
            if (studentId != null && classId != null && academicYear != null && semester != null) {
                selectionId = studentId + "_" + classId + "_" + academicYear + "_" + semester;
            } else {
                throw new BusinessException("选课记录缺少必要字段: studentId=" + studentId
                        + ", classId=" + classId + ", academicYear=" + academicYear + ", semester=" + semester);
            }
        }
        entity.setSelectionId(selectionId);
        entity.setStudentId(studentId);
        // 学生姓名：优先使用 API 返回值，若为空则从 school_student 或 users 表反查
        String studentName = firstNonBlank(getStringValue(data, "studentName"), resolveStudentName(studentId));
        entity.setStudentName(studentName);
        entity.setCourseCode(getCourseCodeValue(data));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setClassId(classId);
        entity.setTeacherId(getStringValue(data, "teacherId"));
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setCredits(getBigDecimalValue(data, "credits"));
        entity.setAcademicYear(academicYear);
        entity.setSemester(semester);
        entity.setClassTime(getStringValue(data, "classTime"));
        entity.setClassLocation(getStringValue(data, "classLocation"));
        entity.setSelectionTime(getLocalDateTimeValue(data, "selectionTime"));
        entity.setSelectionStatus(getStringValue(data, "selectionStatus"));
        entity.setRegularGrade(getBigDecimalValue(data, "regularGrade"));
        entity.setFinalGrade(getBigDecimalValue(data, "finalGrade"));
        entity.setTotalGrade(getBigDecimalValue(data, "totalGrade"));
        entity.setGradePoint(getBigDecimalValue(data, "gradePoint"));
        entity.setAttendanceCount(getIntegerValue(data, "attendanceCount"));
        entity.setAbsenceCount(getIntegerValue(data, "absenceCount"));
        entity.setGradeRemark(getStringValue(data, "gradeRemark"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        courseSelectionMapper.upsertBySelectionId(entity);
    }

    private void upsertLevelExamScore(Map<String, Object> data) {
        LevelExamScore entity = new LevelExamScore();
        entity.setStudentId(getStringValue(data, "studentId"));
        String studentName = firstNonBlank(getStringValue(data, "studentName"), resolveStudentName(entity.getStudentId()));
        entity.setStudentName(studentName);
        String examType = getStringValue(data, "examType");
        if (examType == null || examType.trim().isEmpty()) {
            String examName = getStringValue(data, "examName");
            if (examName != null && examName.contains("CET")) examType = "CET";
            else if (examName != null && examName.contains("四级")) examType = "CET4";
            else if (examName != null && examName.contains("六级")) examType = "CET6";
            else examType = "OTHER";
        }
        entity.setExamType(examType);
        entity.setExamName(getStringValue(data, "examName"));
        entity.setAdmissionTicketNo(getStringValue(data, "admissionTicketNo"));
        entity.setExamDate(getLocalDateValue(data, "examDate"));
        BigDecimal score = getBigDecimalValue(data, "score");
        if (score == null) {
            throw new BusinessException("等级考试成绩缺少分数");
        }
        entity.setScore(score);
        entity.setGrade(getStringValue(data, "grade"));
        Boolean isPassed = getBooleanValue(data, "isPassed");
        if (isPassed == null) {
            BigDecimal passingScore = getBigDecimalValue(data, "passingScore");
            if (passingScore != null) isPassed = score.compareTo(passingScore) >= 0;
            else isPassed = score.compareTo(new BigDecimal("60")) >= 0;
        }
        entity.setIsPassed(isPassed);
        entity.setPassingScore(getBigDecimalValue(data, "passingScore"));
        entity.setListeningScore(getBigDecimalValue(data, "listeningScore"));
        entity.setReadingScore(getBigDecimalValue(data, "readingScore"));
        entity.setWritingScore(getBigDecimalValue(data, "writingScore"));
        entity.setTranslationScore(getBigDecimalValue(data, "translationScore"));
        entity.setCertificateNo(getStringValue(data, "certificateNo"));
        entity.setCertificateDate(getLocalDateValue(data, "certificateDate"));
        entity.setExamLocation(getStringValue(data, "examLocation"));
        entity.setExamRoomNo(getStringValue(data, "examRoomNo"));
        entity.setSeatNo(getStringValue(data, "seatNo"));
        entity.setRegistrationDate(getLocalDateValue(data, "registrationDate"));
        entity.setScoreReleaseDate(getLocalDateValue(data, "scoreReleaseDate"));
        entity.setDataSource(getStringValue(data, "dataSource"));
        entity.setRemark(getStringValue(data, "remark"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        levelExamScoreMapper.upsertByStudentExam(entity);
    }

    private void upsertTeachingPlan(Map<String, Object> data) {
        TeachingPlan entity = new TeachingPlan();
        entity.setPlanId(getStringValue(data, "planId"));
        String planName = firstNonBlank(getStringValue(data, "planName"), getStringValue(data, "courseName"), getStringValue(data, "collegeName"), getStringValue(data, "grade"));
        if (planName == null) {
            planName = "教学计划-" + firstNonBlank(entity.getPlanId(), "UNKNOWN");
        }
        entity.setPlanName(planName);
        entity.setMajorCode(defaultIfBlank(getStringValue(data, "majorCode"), "UNKNOWN"));
        entity.setMajorName(defaultIfBlank(getStringValue(data, "majorName"), "未知专业"));
        entity.setGrade(getStringValue(data, "grade"));
        Integer schoolingLength = getIntegerValue(data, "schoolingLength");
        entity.setSchoolingLength(schoolingLength == null ? 4 : schoolingLength);
        entity.setDegreeType(defaultIfBlank(getStringValue(data, "degreeType"), "学士"));
        entity.setEducationLevel(defaultIfBlank(getStringValue(data, "educationLevel"), "本科"));
        entity.setCollegeCode(defaultIfBlank(getStringValue(data, "collegeCode"), "UNKNOWN"));
        entity.setCollegeName(defaultIfBlank(getStringValue(data, "collegeName"), "未知学院"));
        entity.setTotalCredits(getBigDecimalValue(data, "totalCredits"));
        BigDecimal requiredCredits = getBigDecimalValue(data, "requiredCredits");
        entity.setRequiredCredits(requiredCredits == null ? BigDecimal.ZERO : requiredCredits);
        BigDecimal electiveCredits = getBigDecimalValue(data, "electiveCredits");
        entity.setElectiveCredits(electiveCredits == null ? BigDecimal.ZERO : electiveCredits);
        entity.setPracticeCredits(getBigDecimalValue(data, "practiceCredits"));
        entity.setGeneralEducationCredits(getBigDecimalValue(data, "generalEducationCredits"));
        entity.setMajorCoreCredits(getBigDecimalValue(data, "majorCoreCredits"));
        entity.setMajorElectiveCredits(getBigDecimalValue(data, "majorElectiveCredits"));
        entity.setTrainingObjective(getStringValue(data, "trainingObjective"));
        entity.setTrainingRequirements(getStringValue(data, "trainingRequirements"));
        entity.setMainDisciplines(getStringValue(data, "mainDisciplines"));
        entity.setCoreCourses(getStringValue(data, "coreCourses"));
        entity.setPlanStatus(getStringValue(data, "planStatus"));
        LocalDate effectiveDate = getLocalDateValue(data, "effectiveDate");
        entity.setEffectiveDate(effectiveDate == null ? LocalDate.now() : effectiveDate);
        entity.setExpiryDate(getLocalDateValue(data, "expiryDate"));
        entity.setCreator(getStringValue(data, "creator"));
        entity.setReviewer(getStringValue(data, "reviewer"));
        entity.setReviewDate(getLocalDateValue(data, "reviewDate"));
        entity.setVersion(getStringValue(data, "version"));
        entity.setRevisionNotes(getStringValue(data, "revisionNotes"));
        entity.setAttachmentPath(getStringValue(data, "attachmentPath"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        teachingPlanMapper.upsertByPlanId(entity);
        if (entity.getPlanId() != null) {
            syncTeachingPlanCourses(entity.getPlanId());
        }
    }

    private void upsertStudentSchedule(Map<String, Object> data) {
        String studentId = getStringValue(data, "studentId");
        String academicYear = getStringValue(data, "academicYear");
        Integer semester = parseSchoolSemester(data, "semester");
        if (studentId != null && academicYear != null && semester != null) {
            QueryWrapper<StudentSchedule> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("student_id", studentId).eq("academic_year", academicYear).eq("semester", semester);
            studentScheduleMapper.delete(deleteWrapper);
        }
        StudentSchedule entity = new StudentSchedule();
        entity.setStudentId(studentId);
        entity.setStudentName(getStringValue(data, "studentName"));
        entity.setClassId(getClassIdValue(data));
        entity.setCourseCode(getCourseCodeValue(data));
        entity.setCourseName(getStringValue(data, "courseName"));
        entity.setTeacherId(getStringValue(data, "teacherId"));
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setDayOfWeek(getIntegerValue(data, "dayOfWeek"));
        entity.setPeriod(getStringValue(data, "period"));
        entity.setWeekRange(getStringValue(data, "weekRange"));
        entity.setClassLocation(getStringValue(data, "classLocation"));
        entity.setAcademicYear(academicYear);
        entity.setSemester(semester);
        entity.setSyncTime((LocalDateTime) data.getOrDefault("syncTime", LocalDateTime.now()));
        entity.setSyncSource((String) data.getOrDefault("syncSource", "SCHOOL_API"));
        studentScheduleMapper.insert(entity);
    }

    private void upsertSchoolStudent(Map<String, Object> data) {
        SchoolStudent entity = new SchoolStudent();
        String studentId = getStringValue(data, "studentId");
        entity.setStudentId(studentId);
        entity.setStudentName(getStringValue(data, "studentName"));
        entity.setCollegeName(getStringValue(data, "collegeName"));
        entity.setCollegeCode(getStringValue(data, "collegeCode"));
        entity.setClassCode(getStringValue(data, "classCode"));
        entity.setClassName(getStringValue(data, "className"));
        entity.setGrade(getStringValue(data, "grade"));
        entity.setMajorName(getStringValue(data, "majorName"));
        entity.setSourceCourseCodes(getStringValue(data, "sourceCourseCodes"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setLastExamSyncTime(getLocalDateTimeValue(data, "lastExamSyncTime"));
        entity.setLastScheduleSyncTime(getLocalDateTimeValue(data, "lastScheduleSyncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        schoolStudentMapper.upsertByStudentId(entity);
    }

    private void upsertSchoolTeacher(Map<String, Object> data) {
        SchoolTeacher entity = new SchoolTeacher();
        String teacherId = getStringValue(data, "teacherId");
        entity.setTeacherId(teacherId);
        entity.setTeacherName(getStringValue(data, "teacherName"));
        entity.setCollegeName(getStringValue(data, "collegeName"));
        entity.setCollegeCode(getStringValue(data, "collegeCode"));
        entity.setSyncTime(getLocalDateTimeValue(data, "syncTime"));
        entity.setLastScheduleSyncTime(getLocalDateTimeValue(data, "lastScheduleSyncTime"));
        entity.setSyncSource(getStringValue(data, "syncSource"));
        schoolTeacherMapper.upsertByTeacherId(entity);
    }

    private void syncTeachingPlanCourses(String planId) {
        try {
            List<Map<String, Object>> coursesData = apiClient.fetchTeachingPlanCourses(planId);
            if (coursesData == null || coursesData.isEmpty()) {
                return;
            }
            LocalDateTime syncTime = LocalDateTime.now();
            for (Map<String, Object> sourceRecord : coursesData) {
                try {
                    Map<String, Object> mappedData = fieldMappingService.applyMapping("teaching_plans", sourceRecord);
                    mappedData.put("planId", planId);
                    mappedData.put("syncTime", syncTime);
                    mappedData.put("syncSource", "SCHOOL_API");
                    TeachingPlanCourse courseEntity = new TeachingPlanCourse();
                    courseEntity.setDetailId(getStringValue(mappedData, "detailId"));
                    courseEntity.setPlanId(planId);
                    courseEntity.setCourseCode(getStringValue(mappedData, "courseCode"));
                    courseEntity.setCourseName(getStringValue(mappedData, "courseName"));
                    courseEntity.setCourseType(getStringValue(mappedData, "courseType"));
                    courseEntity.setCourseCategory(getStringValue(mappedData, "courseCategory"));
                    courseEntity.setCredits(getBigDecimalValue(mappedData, "credits"));
                    courseEntity.setHours(getIntegerValue(mappedData, "hours"));
                    courseEntity.setTheoryHours(getIntegerValue(mappedData, "theoryHours"));
                    courseEntity.setLabHours(getIntegerValue(mappedData, "labHours"));
                    courseEntity.setSemester(getIntegerValue(mappedData, "semester"));
                    courseEntity.setAssessmentMethod(getStringValue(mappedData, "assessmentMethod"));
                    courseEntity.setPrerequisiteCourses(getStringValue(mappedData, "prerequisiteCourses"));
                    courseEntity.setSortOrder(getIntegerValue(mappedData, "sortOrder"));
                    courseEntity.setSyncTime(syncTime);
                    courseEntity.setSyncSource("SCHOOL_API");
                    teachingPlanCourseMapper.upsertByDetailId(courseEntity);
                } catch (Exception e) {
                    logger.warn("同步教学计划课程明细失败 planId={} detailPreview={} err={}",
                            planId, truncateRecordForLog(sourceRecord), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            String errorMessage = defaultString(e.getMessage(), e.getClass().getSimpleName());
            if (errorMessage.contains("404") || errorMessage.contains("NOT_FOUND")) {
                // 教学计划课程明细接口不存在（CDSP API 不提供此接口），静默跳过
                // 不记录 INFO 日志，避免日志污染（每个 planId 都会触发一次）
                return;
            }
            logger.warn("获取教学计划课程明细失败: planId={} err={}", planId, errorMessage);
        }
    }

    private String resolveStudentName(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return null;
        }
        SchoolStudent schoolStudent = schoolStudentMapper.selectByStudentId(studentId.trim());
        if (schoolStudent != null) {
            String schoolStudentName = firstNonBlank(schoolStudent.getStudentName());
            if (schoolStudentName != null) {
                return schoolStudentName;
            }
        }
        User user = userMapper.selectActiveByUsername(studentId.trim());
        if (user == null) {
            return null;
        }
        return firstNonBlank(user.getRealName(), user.getNickname());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String getClassIdValue(Map<String, Object> data) {
        return firstNonBlank(getStringValue(data, "classId"), getStringValue(data, "courseCode"));
    }

    private String getCourseCodeValue(Map<String, Object> data) {
        return getStringValue(data, "courseCode");
    }

    private Integer getIntegerValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseSchoolSemester(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        String normalized = String.valueOf(value).trim();
        if ("1".equals(normalized) || "秋".equals(normalized)) return 1;
        if ("2".equals(normalized) || "春".equals(normalized)) return 2;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new BusinessException("无法解析学期: " + value);
        }
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        String strVal = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(strVal) || "1".equals(strVal) || "yes".equals(strVal) || "是".equals(strVal)) return true;
        if ("false".equals(strVal) || "0".equals(strVal) || "no".equals(strVal) || "否".equals(strVal)) return false;
        return null;
    }

    private LocalDate getLocalDateValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof LocalDateTime) return ((LocalDateTime) value).toLocalDate();
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime getLocalDateTimeValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        return null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String truncateRecordForLog(Map<String, Object> record) {
        if (record == null) return "null";
        String str = record.toString();
        return str.length() > 300 ? str.substring(0, 300) + "...(truncated)" : str;
    }
}
