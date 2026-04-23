package com.english.service.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.english.dto.SchoolDataFilterFieldDto;
import com.english.entity.*;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataFieldMetadataService;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SchoolDataFieldMetadataServiceImpl implements SchoolDataFieldMetadataService {

    private static final Set<String> HIDDEN_FIELDS = Set.of("id", "createdAt", "updatedAt", "syncTime", "syncSource");

    private static final Map<String, Class<?>> ENTITY_TYPES = Map.of(
            "administrative_class", AdministrativeClass.class,
            "school_course", SchoolCourse.class,
            "teaching_class", TeachingClass.class,
            "course_schedule", CourseSchedule.class,
            "course_selection", CourseSelection.class,
            "level_exam_scores", LevelExamScore.class,
            "teaching_plans", TeachingPlan.class,
            "student_schedule", StudentSchedule.class
    );

    private static final Map<String, String> FIELD_LABELS = createFieldLabels();
    private static final Map<String, Set<String>> UPSTREAM_FILTER_FIELDS = createUpstreamFields();

    private final FieldMappingService fieldMappingService;

    public SchoolDataFieldMetadataServiceImpl(FieldMappingService fieldMappingService) {
        this.fieldMappingService = fieldMappingService;
    }

    @Override
    public List<SchoolDataFilterFieldDto> getFilterFields(String dataSource) {
        Map<String, SchoolDataFilterFieldDto> fieldMap = getFilterFieldMap(dataSource);
        List<SchoolDataFilterFieldDto> fields = new ArrayList<>(fieldMap.values());
        fields.sort(Comparator.comparing(SchoolDataFilterFieldDto::getField));
        return fields;
    }

    @Override
    public Map<String, SchoolDataFilterFieldDto> getFilterFieldMap(String dataSource) {
        Class<?> entityType = ENTITY_TYPES.get(dataSource);
        if (entityType == null) {
            return Collections.emptyMap();
        }
        Map<String, SchoolDataFilterFieldDto> result = new LinkedHashMap<>();
        for (Field field : entityType.getDeclaredFields()) {
            if (HIDDEN_FIELDS.contains(field.getName())) {
                continue;
            }
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField == null) {
                continue;
            }
            SchoolDataFilterFieldDto dto = new SchoolDataFilterFieldDto();
            dto.setField(field.getName());
            dto.setColumn(tableField.value());
            dto.setType(resolveFieldType(field.getType()));
            dto.setLabel(resolveLabel(dataSource, field.getName()));
            dto.setOperators(resolveOperators(dto.getType()));
            dto.getCapabilities().setListQuery(true);
            dto.getCapabilities().setLocalSyncFilter(true);
            dto.getCapabilities().setUpstreamSyncFilter(isUpstreamFilterable(dataSource, field.getName()));
            result.put(dto.getField(), dto);
        }
        for (FieldMapping mapping : fieldMappingService.getMappingsByDataSource(dataSource)) {
            if (mapping.getTargetField() == null || mapping.getTargetField().trim().isEmpty()) {
                continue;
            }
            SchoolDataFilterFieldDto dto = result.computeIfAbsent(mapping.getTargetField(), field -> {
                SchoolDataFilterFieldDto created = new SchoolDataFilterFieldDto();
                created.setField(field);
                created.setLabel(resolveLabel(dataSource, field));
                created.setType(normalizeMappingType(mapping.getTargetType()));
                created.setOperators(resolveOperators(created.getType()));
                created.getCapabilities().setLocalSyncFilter(true);
                created.getCapabilities().setUpstreamSyncFilter(isUpstreamFilterable(dataSource, field));
                return created;
            });
            if (dto.getSourceField() == null || dto.getSourceField().isEmpty()) {
                dto.setSourceField(mapping.getSourceField());
            }
            if (dto.getType() == null || dto.getType().isEmpty()) {
                dto.setType(normalizeMappingType(mapping.getTargetType()));
                dto.setOperators(resolveOperators(dto.getType()));
            }
            dto.getCapabilities().setLocalSyncFilter(true);
            if (isUpstreamFilterable(dataSource, dto.getField())) {
                dto.getCapabilities().setUpstreamSyncFilter(true);
            }
        }
        return result;
    }

    private boolean isUpstreamFilterable(String dataSource, String field) {
        return UPSTREAM_FILTER_FIELDS.getOrDefault(dataSource, Collections.emptySet()).contains(field);
    }

    private String resolveFieldType(Class<?> type) {
        if (type == null) {
            return "STRING";
        }
        if (type == String.class) {
            return "STRING";
        }
        if (Number.class.isAssignableFrom(type) || type == int.class || type == long.class || type == double.class || type == float.class || type == short.class || type == BigDecimal.class) {
            return "NUMBER";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "BOOLEAN";
        }
        if (type == LocalDate.class || type == LocalDateTime.class || java.util.Date.class.isAssignableFrom(type)) {
            return "DATE";
        }
        return "STRING";
    }

    private String normalizeMappingType(String targetType) {
        if (targetType == null || targetType.trim().isEmpty()) {
            return "STRING";
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("NUMBER") || normalized.contains("INT") || normalized.contains("DECIMAL")) {
            return "NUMBER";
        }
        if (normalized.contains("BOOL")) {
            return "BOOLEAN";
        }
        if (normalized.contains("DATE") || normalized.contains("TIME")) {
            return "DATE";
        }
        return "STRING";
    }

    private List<String> resolveOperators(String type) {
        if ("NUMBER".equals(type) || "DATE".equals(type)) {
            return List.of("eq", "gte", "lte", "in");
        }
        if ("BOOLEAN".equals(type)) {
            return List.of("eq");
        }
        return List.of("eq", "contains", "in");
    }

    private String resolveLabel(String dataSource, String field) {
        return FIELD_LABELS.getOrDefault(dataSource + "." + field, field);
    }

    private static Map<String, String> createFieldLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("administrative_class.classCode", "班级编码");
        labels.put("administrative_class.className", "班级名称");
        labels.put("administrative_class.classShortName", "班级简称");
        labels.put("administrative_class.collegeCode", "学院代码");
        labels.put("administrative_class.collegeName", "学院名称");
        labels.put("administrative_class.majorCode", "专业代码");
        labels.put("administrative_class.majorName", "专业名称");
        labels.put("administrative_class.grade", "年级");
        labels.put("administrative_class.classType", "班级类型");
        labels.put("administrative_class.headTeacherCode", "班主任工号");
        labels.put("administrative_class.headTeacherName", "班主任姓名");
        labels.put("administrative_class.studentCount", "人数");
        labels.put("administrative_class.maleCount", "男生人数");
        labels.put("administrative_class.femaleCount", "女生人数");
        labels.put("administrative_class.classStatus", "状态");
        labels.put("administrative_class.establishDate", "建班日期");
        labels.put("administrative_class.graduateDate", "毕业日期");
        labels.put("administrative_class.remark", "备注");

        labels.put("school_course.courseCode", "课程编码");
        labels.put("school_course.courseName", "课程名称");
        labels.put("school_course.courseNameEn", "课程英文名");
        labels.put("school_course.courseShortName", "课程简称");
        labels.put("school_course.courseNature", "课程性质");
        labels.put("school_course.courseCategory", "课程类别");
        labels.put("school_course.credits", "学分");
        labels.put("school_course.totalHours", "总学时");
        labels.put("school_course.theoryHours", "理论学时");
        labels.put("school_course.experimentHours", "实验学时");
        labels.put("school_course.practiceHours", "实践学时");
        labels.put("school_course.computerHours", "上机学时");
        labels.put("school_course.collegeCode", "学院代码");
        labels.put("school_course.collegeName", "学院名称");
        labels.put("school_course.departmentCode", "系部代码");
        labels.put("school_course.departmentName", "系部名称");
        labels.put("school_course.leaderCode", "负责人代码");
        labels.put("school_course.leaderName", "负责人姓名");
        labels.put("school_course.assessmentMethod", "考核方式");
        labels.put("school_course.isBilingual", "是否双语");
        labels.put("school_course.isOnline", "是否在线");
        labels.put("school_course.applicableMajors", "适用专业");
        labels.put("school_course.prerequisiteCodes", "先修课程代码");
        labels.put("school_course.prerequisiteNames", "先修课程名称");
        labels.put("school_course.courseIntro", "课程简介");
        labels.put("school_course.teachingObjectives", "教学目标");
        labels.put("school_course.courseStatus", "课程状态");

        labels.put("teaching_class.classId", "教学班ID");
        labels.put("teaching_class.className", "教学班名称");
        labels.put("teaching_class.courseCode", "课程编码");
        labels.put("teaching_class.courseName", "课程名称");
        labels.put("teaching_class.teacherId", "教师工号");
        labels.put("teaching_class.teacherName", "教师姓名");
        labels.put("teaching_class.academicYear", "学年");
        labels.put("teaching_class.semester", "学期");
        labels.put("teaching_class.capacity", "容量");
        labels.put("teaching_class.enrolledCount", "选课人数");
        labels.put("teaching_class.classStatus", "状态");

        labels.put("course_schedule.classId", "教学班ID");
        labels.put("course_schedule.dayOfWeek", "星期");
        labels.put("course_schedule.period", "节次");
        labels.put("course_schedule.weekRange", "周次");
        labels.put("course_schedule.classLocation", "教室");
        labels.put("course_schedule.academicYear", "学年");
        labels.put("course_schedule.semester", "学期");

        labels.put("course_selection.selectionId", "选课记录ID");
        labels.put("course_selection.studentId", "学号");
        labels.put("course_selection.studentName", "姓名");
        labels.put("course_selection.courseCode", "课程编码");
        labels.put("course_selection.courseName", "课程名称");
        labels.put("course_selection.classId", "课程班号");
        labels.put("course_selection.teacherId", "教师工号");
        labels.put("course_selection.teacherName", "教师姓名");
        labels.put("course_selection.credits", "学分");
        labels.put("course_selection.academicYear", "学年");
        labels.put("course_selection.semester", "学期");
        labels.put("course_selection.classTime", "上课时间");
        labels.put("course_selection.classLocation", "上课地点");
        labels.put("course_selection.selectionTime", "选课时间");
        labels.put("course_selection.selectionStatus", "选课状态");
        labels.put("course_selection.regularGrade", "平时成绩");
        labels.put("course_selection.finalGrade", "期末成绩");
        labels.put("course_selection.totalGrade", "总成绩");
        labels.put("course_selection.gradePoint", "绩点");
        labels.put("course_selection.attendanceCount", "出勤次数");
        labels.put("course_selection.absenceCount", "缺勤次数");
        labels.put("course_selection.gradeRemark", "成绩备注");

        labels.put("level_exam_scores.studentId", "学号");
        labels.put("level_exam_scores.studentName", "姓名");
        labels.put("level_exam_scores.examType", "考试类型");
        labels.put("level_exam_scores.examName", "考试名称");
        labels.put("level_exam_scores.admissionTicketNo", "准考证号");
        labels.put("level_exam_scores.examDate", "考试日期");
        labels.put("level_exam_scores.score", "分数");
        labels.put("level_exam_scores.grade", "等级");
        labels.put("level_exam_scores.isPassed", "是否通过");
        labels.put("level_exam_scores.passingScore", "及格线");
        labels.put("level_exam_scores.listeningScore", "听力成绩");
        labels.put("level_exam_scores.readingScore", "阅读成绩");
        labels.put("level_exam_scores.writingScore", "写作成绩");
        labels.put("level_exam_scores.translationScore", "翻译成绩");
        labels.put("level_exam_scores.certificateNo", "证书编号");
        labels.put("level_exam_scores.certificateDate", "证书日期");
        labels.put("level_exam_scores.examLocation", "考试地点");
        labels.put("level_exam_scores.examRoomNo", "考场");
        labels.put("level_exam_scores.seatNo", "座位号");
        labels.put("level_exam_scores.registrationDate", "报名日期");
        labels.put("level_exam_scores.scoreReleaseDate", "成绩发布日期");
        labels.put("level_exam_scores.dataSource", "数据来源");
        labels.put("level_exam_scores.remark", "备注");

        labels.put("teaching_plans.planId", "计划编号");
        labels.put("teaching_plans.planName", "计划名称");
        labels.put("teaching_plans.majorCode", "专业代码");
        labels.put("teaching_plans.majorName", "专业名称");
        labels.put("teaching_plans.grade", "年级");
        labels.put("teaching_plans.schoolingLength", "学制");
        labels.put("teaching_plans.degreeType", "学位类型");
        labels.put("teaching_plans.educationLevel", "培养层次");
        labels.put("teaching_plans.collegeCode", "学院代码");
        labels.put("teaching_plans.collegeName", "学院名称");
        labels.put("teaching_plans.totalCredits", "总学分");
        labels.put("teaching_plans.requiredCredits", "必修学分");
        labels.put("teaching_plans.electiveCredits", "选修学分");
        labels.put("teaching_plans.practiceCredits", "实践学分");
        labels.put("teaching_plans.generalEducationCredits", "通识学分");
        labels.put("teaching_plans.majorCoreCredits", "专业核心学分");
        labels.put("teaching_plans.majorElectiveCredits", "专业选修学分");
        labels.put("teaching_plans.trainingObjective", "培养目标");
        labels.put("teaching_plans.trainingRequirements", "培养要求");
        labels.put("teaching_plans.mainDisciplines", "主干学科");
        labels.put("teaching_plans.coreCourses", "核心课程");
        labels.put("teaching_plans.planStatus", "状态");
        labels.put("teaching_plans.effectiveDate", "生效日期");
        labels.put("teaching_plans.expiryDate", "失效日期");
        labels.put("teaching_plans.creator", "创建人");
        labels.put("teaching_plans.reviewer", "审核人");
        labels.put("teaching_plans.reviewDate", "审核日期");
        labels.put("teaching_plans.version", "版本");
        labels.put("teaching_plans.revisionNotes", "修订说明");
        labels.put("teaching_plans.attachmentPath", "附件路径");

        labels.put("student_schedule.studentId", "学号");
        labels.put("student_schedule.studentName", "姓名");
        labels.put("student_schedule.classId", "教学班ID");
        labels.put("student_schedule.courseCode", "课程编码");
        labels.put("student_schedule.courseName", "课程名称");
        labels.put("student_schedule.teacherId", "教师工号");
        labels.put("student_schedule.teacherName", "教师姓名");
        labels.put("student_schedule.dayOfWeek", "星期");
        labels.put("student_schedule.period", "节次");
        labels.put("student_schedule.weekRange", "周次");
        labels.put("student_schedule.classLocation", "教室");
        labels.put("student_schedule.academicYear", "学年");
        labels.put("student_schedule.semester", "学期");
        return labels;
    }

    private static Map<String, Set<String>> createUpstreamFields() {
        Map<String, Set<String>> fields = new HashMap<>();
        fields.put("administrative_class", Set.of("collegeCode", "grade"));
        fields.put("school_course", Set.of("collegeName", "courseCode"));
        fields.put("course_schedule", Set.of("classId", "dayOfWeek", "period"));
        fields.put("course_selection", Set.of("studentId", "classId"));
        fields.put("level_exam_scores", Set.of("studentId"));
        fields.put("teaching_plans", Set.of("grade"));
        fields.put("teacher_courses", Set.of("teacherId", "courseCode", "academicYear", "semester"));
        fields.put("student_schedule", Set.of("studentId", "academicYear", "semester"));
        return fields;
    }
}
