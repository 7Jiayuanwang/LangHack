package com.english.service.impl;

import com.english.dto.SchoolDataSyncFilterRequest;
import com.english.exception.BusinessException;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import com.english.service.SchoolDataFetchService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SchoolDataFetchServiceImpl implements SchoolDataFetchService {

    private final SchoolDataApiClient apiClient;
    private final FieldMappingService fieldMappingService;

    public SchoolDataFetchServiceImpl(@Qualifier("schoolDataApiClientDelegate") SchoolDataApiClient apiClient,
                                      FieldMappingService fieldMappingService) {
        this.apiClient = apiClient;
        this.fieldMappingService = fieldMappingService;
    }

    @Override
    public SchoolDataFetchResult fetchRawDataWithSummary(String dataSource, SchoolDataSyncFilterRequest request) {
        if ("student_schedule".equals(dataSource) && hasFilterRequest(request)) {
            String studentId = null;
            String academicYear = null;
            Integer semester = null;
            for (SchoolDataSyncFilterRequest.FilterGroup group : request.getFilterGroups()) {
                if (group == null || group.getConditions() == null) {
                    continue;
                }
                for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                    if (condition == null || condition.getField() == null) {
                        continue;
                    }
                    String field = condition.getField();
                    String value = defaultString(condition.getValue(), "").trim();
                    if (matchesAnyField(field, "studentId", "student_id", "xh", "XH", "学号")) studentId = value;
                    if (matchesAnyField(field, "academicYear", "academic_year", "xn", "XN", "学年")) academicYear = value;
                    if (matchesAnyField(field, "semester", "xq", "XQ", "学期") && !value.isEmpty()) semester = normalizeSemesterValue(value);
                }
            }
            if (studentId == null || studentId.isEmpty()) {
                throw new BusinessException("student_schedule 手动同步必须提供学号");
            }
            List<Map<String, Object>> rows = fetchStudentScheduleRaw(studentId, academicYear, semester);
            List<Map<String, Object>> filtered = applyFilterGroups(dataSource, rows, request);
            return new SchoolDataFetchResult(
                    filtered,
                    new SchoolDataFetchResult.PaginationSummary(true, "STUDENT_SCHEDULE_DIRECT", rows.isEmpty() ? 0 : 1, 0, 0, false, rows.size())
            );
        }

        Map<String, String> upstreamParams = buildUpstreamParams(dataSource, request);
        SchoolDataFetchResult fetchResult = fetchFromApiWithSummary(dataSource, upstreamParams);
        List<Map<String, Object>> filtered = applyFilterGroups(dataSource, fetchResult.getRows(), request);
        return new SchoolDataFetchResult(filtered, fetchResult.getPaginationSummary());
    }

    @Override
    public List<Map<String, Object>> fetchRawData(String dataSource, SchoolDataSyncFilterRequest request) {
        SchoolDataFetchResult fetchResult = fetchRawDataWithSummary(dataSource, request);
        ensureCompleteFetch(dataSource, fetchResult);
        return fetchResult.getRows();
    }

    private void ensureCompleteFetch(String dataSource, SchoolDataFetchResult fetchResult) {
        if (fetchResult == null) {
            throw new BusinessException("学校数据拉取结果为空: dataSource=" + dataSource);
        }
        SchoolDataFetchResult.PaginationSummary summary = fetchResult.getPaginationSummary();
        if (summary != null && !summary.isComplete()) {
            throw new BusinessException("学校数据未完整拉取: dataSource="
                    + dataSource
                    + ", reason=" + summary.getStopReason()
                    + ", fetchedRows=" + summary.getFetchedRowCount());
        }
    }

    @Override
    public List<Map<String, Object>> fetchStudentScheduleRaw(String studentId, String academicYear, Integer semester) {
        List<Map<String, Object>> fetchData = apiClient.fetchStudentSchedules(studentId, academicYear, semester);
        return fetchData == null ? Collections.emptyList() : fetchData;
    }

    private SchoolDataFetchResult fetchFromApiWithSummary(String dataSource, Map<String, String> extraParams) {
        if (apiClient instanceof SchoolDataApiClientDelegate) {
            return ((SchoolDataApiClientDelegate) apiClient).fetchDataWithSummary(dataSource, extraParams);
        }
        List<Map<String, Object>> rows = fetchFromApi(dataSource, extraParams);
        return new SchoolDataFetchResult(rows, new SchoolDataFetchResult.PaginationSummary(true, "LEGACY_CLIENT", rows.isEmpty() ? 0 : 1, 0, 0, false, rows.size()));
    }

    private List<Map<String, Object>> fetchFromApi(String dataSource, Map<String, String> extraParams) {
        Map<String, String> params = extraParams != null ? new HashMap<>(extraParams) : new HashMap<>();
        switch (dataSource) {
            case "administrative_class":
                return apiClient.fetchAdministrativeClasses(params);
            case "school_course":
                return apiClient.fetchSchoolCourses(params);
            case "teaching_class":
                return apiClient.fetchTeachingClasses(params);
            case "course_schedule":
                return apiClient.fetchCourseSchedules(params);
            case "course_selection":
                return apiClient.fetchCourseSelections(params);
            case "level_exam_scores":
                return apiClient.fetchLevelExamScores(params);
            case "teaching_plans":
                return apiClient.fetchTeachingPlans(params);
            case "teacher_courses":
                return apiClient.fetchTeacherCourses(params);
            case "student_schedule":
                return apiClient.fetchStudentSchedules(
                        firstNonBlank(params, "studentId", "student_id", "xh", "XH"),
                        firstNonBlank(params, "academicYear", "academic_year", "xn", "XN"),
                        normalizeNullableSemester(firstNonBlank(params, "semester", "xq", "XQ"))
                );
            case "student_basic_info":
                return apiClient.fetchStudentBasicInfos(params);
            default:
                return Collections.emptyList();
        }
    }

    private boolean hasFilterRequest(SchoolDataSyncFilterRequest request) {
        return request != null && request.getFilterGroups() != null && !request.getFilterGroups().isEmpty();
    }

    private Map<String, String> buildUpstreamParams(String dataSource, SchoolDataSyncFilterRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        if (!hasFilterRequest(request)) {
            return params;
        }
        for (SchoolDataSyncFilterRequest.FilterGroup group : request.getFilterGroups()) {
            if (group == null || group.getConditions() == null) {
                continue;
            }
            for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                if (condition == null || condition.getField() == null || condition.getValue() == null || condition.getValue().trim().isEmpty()) {
                    continue;
                }
                String field = condition.getField().trim();
                String value = condition.getValue().trim();
                if (!"eq".equalsIgnoreCase(defaultString(condition.getOperator(), "eq"))) {
                    continue;
                }
                switch (dataSource) {
                    case "administrative_class":
                        if (matchesAnyField(field, "collegeCode", "XSH", "xsh", "ssxybm")) params.put("XSH", value);
                        else if (matchesAnyField(field, "grade", "NJ", "nj", "ssnj")) params.put("NJ", value);
                        break;
                    case "school_course":
                        if (matchesAnyField(field, "collegeName", "kkyx")) params.put("kkyx", value);
                        else if (matchesAnyField(field, "courseCode", "kcdm")) params.put("kcdm", value);
                        break;
                    case "course_schedule":
                        if (matchesAnyField(field, "classId", "xkkch")) params.put("xkkch", value);
                        else if (matchesAnyField(field, "dayOfWeek", "skxqj")) params.put("skxqj", value);
                        else if (matchesAnyField(field, "period", "skjc")) params.put("skjc", value);
                        break;
                    case "course_selection":
                        if (matchesAnyField(field, "studentId", "XH", "xh")) params.put("XH", value);
                        else if (matchesAnyField(field, "courseClassCode", "classId", "KCBH", "kcbh")) params.put("KCBH", value);
                        break;
                    case "level_exam_scores":
                        if (matchesAnyField(field, "studentId", "xh")) params.put("xh", value);
                        break;
                    case "teaching_plans":
                        if (matchesAnyField(field, "grade", "nj")) params.put("nj", value);
                        break;
                    case "teacher_courses":
                        if (matchesAnyField(field, "teacherId", "rkjsh")) params.put("rkjsh", value);
                        else if (matchesAnyField(field, "courseCode", "kcdm")) params.put("kcdm", value);
                        else if (matchesAnyField(field, "academicYear", "xn")) params.put("xn", value);
                        else if (matchesAnyField(field, "semester", "xq")) params.put("xq", "1".equals(value) ? "秋" : "2".equals(value) ? "春" : value);
                        break;
                    case "student_schedule":
                        if (matchesAnyField(field, "studentId", "student_id", "xh", "XH", "学号")) params.put("xh", value);
                        else if (matchesAnyField(field, "academicYear", "academic_year", "xn", "XN", "学年")) params.put("xn", value);
                        else if (matchesAnyField(field, "semester", "xq", "XQ", "学期")) params.put("xq", "1".equals(value) ? "秋" : "2".equals(value) ? "春" : value);
                        break;
                    default:
                        break;
                }
            }
        }
        return params;
    }

    private boolean matchesAnyField(String field, String... candidates) {
        if (field == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && candidate.equalsIgnoreCase(field)) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> applyFilterGroups(String dataSource,
                                                        List<Map<String, Object>> fetchData,
                                                        SchoolDataSyncFilterRequest request) {
        if (!hasFilterRequest(request) || fetchData == null || fetchData.isEmpty()) {
            return fetchData == null ? Collections.emptyList() : fetchData;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : fetchData) {
            if (matchesAllGroups(dataSource, row, request.getFilterGroups())) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private boolean matchesAllGroups(String dataSource,
                                     Map<String, Object> row,
                                     List<SchoolDataSyncFilterRequest.FilterGroup> groups) {
        for (SchoolDataSyncFilterRequest.FilterGroup group : groups) {
            if (group == null || group.getConditions() == null || group.getConditions().isEmpty()) {
                continue;
            }
            boolean isOr = "OR".equalsIgnoreCase(defaultString(group.getRelation(), "AND"));
            boolean groupResult = !isOr;
            for (SchoolDataSyncFilterRequest.FilterCondition condition : group.getConditions()) {
                boolean conditionMatched = matchesCondition(dataSource, row, condition);
                if (isOr) {
                    groupResult = groupResult || conditionMatched;
                    if (groupResult) break;
                } else {
                    groupResult = groupResult && conditionMatched;
                    if (!groupResult) break;
                }
            }
            if (!groupResult) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCondition(String dataSource,
                                     Map<String, Object> row,
                                     SchoolDataSyncFilterRequest.FilterCondition condition) {
        if (condition == null || condition.getField() == null) {
            return true;
        }
        String operator = defaultString(condition.getOperator(), "eq").toLowerCase(Locale.ROOT);
        String actual = resolveFilterValue(dataSource, row, condition.getField());
        String expected = defaultString(condition.getValue(), "").trim();
        if (isSemesterFilterField(dataSource, condition.getField())) {
            actual = normalizeSemesterComparable(actual);
            expected = normalizeSemesterComparable(expected);
        }
        switch (operator) {
            case "contains":
                return actual.contains(expected);
            case "gte":
                return compareAsNumber(actual, expected) >= 0;
            case "lte":
                return compareAsNumber(actual, expected) <= 0;
            case "in":
                return Arrays.stream(expected.split(",")).map(String::trim).anyMatch(actual::equals);
            case "eq":
            default:
                return actual.equals(expected);
        }
    }

    private String resolveFilterValue(String dataSource, Map<String, Object> row, String field) {
        Object direct = getValueIgnoreCase(row, field);
        if (direct != null) {
            return stringValue(direct);
        }
        Map<String, Object> mapped = fieldMappingService.applyMapping(dataSource, row);
        Object mappedValue = getValueIgnoreCase(mapped, field);
        if (mappedValue != null) {
            return stringValue(mappedValue);
        }
        String targetField = resolveTargetFieldAlias(dataSource, field);
        if (targetField != null) {
            Object aliasValue = getValueIgnoreCase(mapped, targetField);
            if (aliasValue != null) {
                return stringValue(aliasValue);
            }
        }
        return "";
    }

    private boolean isSemesterFilterField(String dataSource, String field) {
        if (matchesAnyField(field, "semester")) {
            return true;
        }
        return "semester".equals(resolveTargetFieldAlias(dataSource, field));
    }

    private String normalizeSemesterComparable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        try {
            Integer normalized = normalizeNullableSemester(value);
            return normalized == null ? value.trim() : String.valueOf(normalized);
        } catch (BusinessException ignored) {
            return value.trim();
        }
    }

    private String resolveTargetFieldAlias(String dataSource, String field) {
        if (dataSource == null || field == null) {
            return null;
        }
        String normalized = field.trim().toLowerCase(Locale.ROOT);
        switch (dataSource) {
            case "administrative_class":
                if (matchesAnyField(normalized, "xsh", "ssxybm")) return "collegeCode";
                if (matchesAnyField(normalized, "nj", "ssnj")) return "grade";
                break;
            case "school_course":
                if (matchesAnyField(normalized, "kkyx")) return "collegeName";
                if (matchesAnyField(normalized, "kcdm", "课程代码")) return "courseCode";
                break;
            case "course_schedule":
                if (matchesAnyField(normalized, "xkkch")) return "classId";
                if (matchesAnyField(normalized, "skxqj")) return "dayOfWeek";
                if (matchesAnyField(normalized, "skjc")) return "period";
                break;
            case "course_selection":
                if (matchesAnyField(normalized, "xh")) return "studentId";
                if (matchesAnyField(normalized, "kcbh", "courseclasscode")) return "classId";
                break;
            case "level_exam_scores":
                if (matchesAnyField(normalized, "xh")) return "studentId";
                break;
            case "teaching_plans":
                if (matchesAnyField(normalized, "nj")) return "grade";
                break;
            case "teacher_courses":
                if (matchesAnyField(normalized, "rkjsh")) return "teacherId";
                if (matchesAnyField(normalized, "kcdm")) return "courseCode";
                if (matchesAnyField(normalized, "kcbh")) return "classId";
                if (matchesAnyField(normalized, "xn")) return "academicYear";
                if (matchesAnyField(normalized, "xq")) return "semester";
                break;
            case "student_schedule":
                if (matchesAnyField(normalized, "xh", "student_id", "学号")) return "studentId";
                if (matchesAnyField(normalized, "xkkh", "xkkch", "classid", "教学班id", "选课课号")) return "classId";
                if (matchesAnyField(normalized, "kcbh", "course_code", "课程编号")) return "courseCode";
                if (matchesAnyField(normalized, "kcmc", "课程名称")) return "courseName";
                if (matchesAnyField(normalized, "skjszgh", "teacher_id", "上课教师职工号")) return "teacherId";
                if (matchesAnyField(normalized, "jsmc", "teacher_name", "教师名称")) return "teacherName";
                if (matchesAnyField(normalized, "xqj", "星期几")) return "dayOfWeek";
                if (matchesAnyField(normalized, "djj", "skjc", "第几节")) return "period";
                if (matchesAnyField(normalized, "zcap", "zcdm", "周次安排", "周次代码")) return "weekRange";
                if (matchesAnyField(normalized, "js", "skdd", "教室")) return "classLocation";
                if (matchesAnyField(normalized, "xn", "academic_year", "学年")) return "academicYear";
                if (matchesAnyField(normalized, "xq", "学期")) return "semester";
                break;
            default:
                break;
        }
        return null;
    }

    private Object getValueIgnoreCase(Map<String, Object> row, String field) {
        if (row == null || field == null) {
            return null;
        }
        Object direct = row.get(field);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private int compareAsNumber(String actual, String expected) {
        try {
            return new java.math.BigDecimal(actual).compareTo(new java.math.BigDecimal(expected));
        } catch (Exception e) {
            return actual.compareTo(expected);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String firstNonBlank(Map<String, String> params, String... keys) {
        if (params == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer normalizeNullableSemester(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return normalizeSemesterValue(value);
    }

    private Integer normalizeSemesterValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if ("1".equals(normalized) || "秋".equals(normalized)) {
            return 1;
        }
        if ("2".equals(normalized) || "春".equals(normalized)) {
            return 2;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new BusinessException("无法解析学期: " + value);
        }
    }
}
