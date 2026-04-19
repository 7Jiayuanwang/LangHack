package com.english.service.impl;

import com.english.config.SchoolDataProperties;
import com.english.exception.BusinessException;
import com.english.service.SchoolDataApiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 学校数据API客户端实现 - 通过REST API对接学校数据源
 * <p>
 * 每个数据源使用独立的完整 URL（基于官方接口文档）:
 * - administrative_class: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337
 * - school_course: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529094506435
 * - teaching_class: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_172180403086015
 * - course_schedule: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529037780151
 * - course_selection: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529480287192
 * - level_exam_scores: https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076
 * - teaching_plans: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171528965502646
 * - teacher_courses: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529464567780
 * - student_schedule: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529439727434
 * <p>
 * 仅在 school.data.source=api 时激活
 */
@Service("schoolDataApiClientApi")
public class SchoolDataApiClientImpl implements SchoolDataApiClient {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataApiClientImpl.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SchoolDataProperties properties;

    public SchoolDataApiClientImpl(ObjectMapper objectMapper, SchoolDataProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;

        // 创建带超时配置的 RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getApi().getConnectTimeout());
        factory.setReadTimeout(properties.getApi().getReadTimeout());
        this.restTemplate = new RestTemplate(factory);

        logger.info("SchoolDataApiClient initialized - connectTimeout: {}ms, readTimeout: {}ms, endpoints: {}",
                properties.getApi().getConnectTimeout(),
                properties.getApi().getReadTimeout(),
                properties.getApi().getEndpoints().size());
    }

    @Override
    public List<Map<String, Object>> fetchAdministrativeClasses(Map<String, String> params) {
        return fetchData("administrative_class", params);
    }

    @Override
    public List<Map<String, Object>> fetchSchoolCourses(Map<String, String> params) {
        return fetchData("school_course", params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingClasses(Map<String, String> params) {
        return fetchData("teaching_class", params);
    }

    @Override
    public List<Map<String, Object>> fetchCourseSchedules(Map<String, String> params) {
        return fetchData("course_schedule", params);
    }

    @Override
    public List<Map<String, Object>> fetchCourseSelections(Map<String, String> params) {
        return fetchData("course_selection", params);
    }

    @Override
    public List<Map<String, Object>> fetchLevelExamScores(Map<String, String> params) {
        return fetchData("level_exam_scores", params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingPlans(Map<String, String> params) {
        return fetchData("teaching_plans", params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingPlanCourses(String planId) {
        // 教学计划课程明细：在教学计划端点下追加 /{planId}/courses
        String basePath = properties.getApi().getEndpointPath("teaching_plans");
        if (basePath == null) {
            logger.warn("No endpoint mapping for teaching_plans");
            return Collections.emptyList();
        }
        return fetchDataWithPath(basePath + "/" + planId + "/courses", null);
    }

    @Override
    public List<Map<String, Object>> fetchStudentSchedules(String studentId, String academicYear, Integer semester) {
        String basePath = properties.getApi().getEndpointPath("student_schedule");
        if (basePath == null) {
            logger.warn("No endpoint mapping for student_schedule");
            return Collections.emptyList();
        }
        Map<String, String> params = new java.util.HashMap<>();
        if (studentId != null) params.put("studentId", studentId);
        if (academicYear != null) params.put("academicYear", academicYear);
        if (semester != null) params.put("semester", String.valueOf(semester));
        return fetchDataWithPath(basePath, params);
    }

    @Override
    public List<Map<String, Object>> fetchTeacherCourses(Map<String, String> params) {
        return fetchData("teacher_courses", params);
    }

    @Override
    public boolean testConnection() {
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            // 使用行政班接口测试连接（完整 URL）
            String testEndpoint = properties.getApi().getEndpointPath("administrative_class");
            if (testEndpoint == null || testEndpoint.isEmpty()) {
                logger.warn("School data API endpoint for administrative_class is not configured");
                return false;
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    testEndpoint + "?pageSize=1", HttpMethod.GET, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (BusinessException e) {
            logger.error("School data API connection test failed (auth not configured): {}", e.getMessage());
            return false;
        } catch (RestClientException e) {
            logger.error("School data API connection test failed", e);
            return false;
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * 通过数据源名称获取数据（使用配置的端点映射）
     */
    private List<Map<String, Object>> fetchData(String dataSource, Map<String, String> params) {
        String endpointPath = properties.getApi().getEndpointPath(dataSource);
        if (endpointPath == null) {
            logger.error("No API endpoint mapping configured for data source: {}", dataSource);
            return Collections.emptyList();
        }
        return fetchDataWithPath(endpointPath, params);
    }

    /**
     * 通过完整 URL 获取数据
     */
    private List<Map<String, Object>> fetchDataWithPath(String fullUrl, Map<String, String> params) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            logger.error("School data API URL is not configured");
            return Collections.emptyList();
        }

        String url = buildUrl(fullUrl, params);

        int retryAttempts = properties.getApi().getRetryAttempts();
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                HttpHeaders headers = buildHeaders();
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                logger.info("Fetching data from: {} (attempt {}/{})", url, attempt, retryAttempts);
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                if (response.getBody() == null || response.getBody().isEmpty()) {
                    logger.warn("Empty response from: {}", url);
                    return Collections.emptyList();
                }

                return parseResponse(response.getBody());
            } catch (RestClientException e) {
                logger.warn("Failed to fetch data from: {} (attempt {}/{}): {}", url, attempt, retryAttempts, e.getMessage());
                if (attempt == retryAttempts) {
                    logger.error("All {} retry attempts exhausted for: {}", retryAttempts, url);
                    return Collections.emptyList();
                }
            } catch (Exception e) {
                logger.error("Unexpected error fetching data from: {}", url, e);
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

    /**
     * Build full URL with query parameters
     */
    private String buildUrl(String fullUrl, Map<String, String> params) {
        StringBuilder url = new StringBuilder(fullUrl);

        if (params != null && !params.isEmpty()) {
            url.append("?");
            params.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    url.append(key).append("=").append(value).append("&");
                }
            });
            // Remove trailing '&'
            if (url.charAt(url.length() - 1) == '&') {
                url.setLength(url.length() - 1);
            }
        }

        return url.toString();
    }

    /**
     * Build HTTP headers with authentication
     * <p>
     * 官方接口认证规范（cdsp.imau.edu.cn data-api v2，基于行政班基本信息接口文档.pdf 第 1 页 2.1 节）：
     *   请求头:
     *     appId     = <应用ID>
     *     timestamp = <毫秒时间戳>
     *     sign      = Base64( hex(MD5(appId + appSecret + timestamp)).getBytes(UTF-8) )
     *                 （先 MD5 得到 32 字符 hex 字符串，再对该字符串做 Base64）
     * <p>
     * 每次请求实时生成 timestamp 和 sign，保证签名时效性。
     * <p>
     * 认证缺失 → 抛 {@link BusinessException}，避免向学校 API 发送无效的无认证请求。
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");

        String appId = properties.getApi().getAppId();
        long timestamp = System.currentTimeMillis();
        String sign = properties.getApi().sign(timestamp);

        if (appId != null && !appId.isEmpty() && sign != null) {
            headers.set("appId", appId);
            headers.set("timestamp", String.valueOf(timestamp));
            headers.set("sign", sign);
            return headers;
        }

        logger.error("School API auth missing — appId or appSecret not configured. "
                + "请在运维面板 → 学校数据配置 填入 App ID / App Secret，或设置 "
                + "SCHOOL_API_APP_ID / SCHOOL_API_APP_SECRET 环境变量并重启后端。");
        throw new BusinessException("学校 API 认证未配置，请先在运维面板或 .env 中设置 App ID / App Secret");
    }

    /**
     * Parse API response - supports both array and wrapped object formats
     * School API format: {"code": 200, "data": [...], "msg": "success"}
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResponse(String responseBody) {
        try {
            // Try parsing as array directly
            return objectMapper.readValue(responseBody,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e1) {
            try {
                // Try parsing as wrapped response: {"code": 200, "data": [...], "msg": "success"}
                Map<String, Object> wrapper = objectMapper.readValue(responseBody,
                        new TypeReference<Map<String, Object>>() {});

                // Check for school API response code
                Object code = wrapper.get("code");
                if (code instanceof Number && ((Number) code).intValue() != 200) {
                    logger.warn("School API returned non-200 code: {}, msg: {}", code, wrapper.get("msg"));
                    return Collections.emptyList();
                }

                // Check common wrapper keys
                for (String key : List.of("data", "result", "records", "list", "items")) {
                    Object data = wrapper.get(key);
                    if (data instanceof List) {
                        return (List<Map<String, Object>>) data;
                    }
                }

                logger.warn("Response wrapper does not contain a recognized data array");
                return Collections.emptyList();
            } catch (Exception e2) {
                logger.error("Failed to parse API response", e2);
                return Collections.emptyList();
            }
        }
    }
}
