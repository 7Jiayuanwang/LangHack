package com.english.service.impl;

import com.english.config.SchoolDataProperties;
import com.english.service.SchoolDataApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学校数据API客户端委托 - 根据 school.data.source 配置路由到对应实现
 * <p>
 * source=api → SchoolDataApiClientImpl
 * source=etl → SchoolDataEtlClientImpl
 */
@Service("schoolDataApiClientDelegate")
public class SchoolDataApiClientDelegate implements SchoolDataApiClient {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataApiClientDelegate.class);

    private final SchoolDataApiClient apiClient;
    private final SchoolDataApiClient etlClient;
    private final SchoolDataProperties properties;

    public SchoolDataApiClientDelegate(
            @Qualifier("schoolDataApiClientApi") SchoolDataApiClient apiClient,
            @Qualifier("schoolDataApiClientEtl") SchoolDataApiClient etlClient,
            SchoolDataProperties properties) {
        this.apiClient = apiClient;
        this.etlClient = etlClient;
        this.properties = properties;
    }

    private SchoolDataApiClient delegate() {
        String source = properties.getSource();
        if ("etl".equalsIgnoreCase(source)) {
            return etlClient;
        }
        return apiClient; // default to api
    }

    @Override
    public List<Map<String, Object>> fetchAdministrativeClasses(Map<String, String> params) {
        return delegate().fetchAdministrativeClasses(params);
    }

    @Override
    public List<Map<String, Object>> fetchSchoolCourses(Map<String, String> params) {
        return delegate().fetchSchoolCourses(params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingClasses(Map<String, String> params) {
        return delegate().fetchTeachingClasses(params);
    }

    @Override
    public List<Map<String, Object>> fetchCourseSchedules(Map<String, String> params) {
        return delegate().fetchCourseSchedules(params);
    }

    @Override
    public List<Map<String, Object>> fetchCourseSelections(Map<String, String> params) {
        return delegate().fetchCourseSelections(params);
    }

    @Override
    public List<Map<String, Object>> fetchLevelExamScores(Map<String, String> params) {
        return delegate().fetchLevelExamScores(params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingPlans(Map<String, String> params) {
        return delegate().fetchTeachingPlans(params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingPlanCourses(String planId) {
        return delegate().fetchTeachingPlanCourses(planId);
    }

    @Override
    public List<Map<String, Object>> fetchStudentSchedules(String studentId, String academicYear, Integer semester) {
        return delegate().fetchStudentSchedules(studentId, academicYear, semester);
    }

    @Override
    public List<Map<String, Object>> fetchTeacherCourses(Map<String, String> params) {
        return delegate().fetchTeacherCourses(params);
    }

    @Override
    public boolean testConnection() {
        return delegate().testConnection();
    }
}
