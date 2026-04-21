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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 学校数据API客户端实现 - 通过REST API对接学校数据源
 * <p>
 * 每个数据源使用独立的完整 URL（基于官方接口文档，PDF 原文核对 2026-04-20）:
 * - administrative_class: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1710949911983374368
 * - school_course:        https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715290945064353805
 * - teaching_class:       https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1721804030860152850
 * - course_schedule:      https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715290377801515044
 * - course_selection:     https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794
 * - level_exam_scores:    https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076
 * - teaching_plans:       https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715289655026466836
 * - teacher_courses:      https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294645677801497
 * - student_schedule:     https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294397274341377
 * <p>
 * <b>请求参数规范</b>（详见 docs/SCHOOL_DATA_API_SPEC.md）：
 * <ul>
 *   <li><b>不支持 OData $filter URI customQuery</b>（错误码 IY1103）—— 必须使用 PDF 第 3 节定义的原生参数</li>
 *   <li><b>字段大小写按端点区分</b>:
 *     <ul>
 *       <li>大写端点: administrative_class (XZBH/BJMC), teaching_class (KCMC/KCH), course_selection (XH/KSXZDM)</li>
 *       <li>小写端点: 其余 6 个 (xh/xn/xq 等)</li>
 *     </ul>
 *   </li>
 *   <li><b>xn (学年)</b>: numeric 单起始年份，如 "2025"（不是 "2025-2026"）</li>
 *   <li><b>xq (学期)</b>: varchar 中文汉字，"秋"=第一学期, "春"=第二学期</li>
 *   <li><b>updatedSince</b>: 非 PDF/OData 标准参数，学校 API 会忽略，增量同步实际退化为全量拉取</li>
 * </ul>
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

        // 与学校API官方文档一致：仅传递文档定义参数 xh/xn/xq，且按返回示例使用原始值。
        // 本地学年若为 "2025-2026"，统一拆分为单年份：2025 秋、2026 春。
        // 这里仅向接口传单年份 xn，避免把双年份字符串直接传给学校接口。
        Map<String, String> params = new java.util.HashMap<>();
        if (studentId != null && !studentId.isEmpty()) {
            params.put("xh", studentId);
        }
        if (academicYear != null && !academicYear.isEmpty()) {
            params.put("xn", splitAcademicYearToSingleYear(academicYear, semester));
        }
        if (semester != null) {
            params.put("xq", toSchoolSemester(semester));
        }

        return fetchDataWithPath(basePath, params);
    }

    /**
     * 本地学年拆分为学校接口所需的单年份。
     * 规则：2025-2026 = 2025秋 + 2026春。
     * 若 semester 未提供，默认取起始年；若已是单年份则原样返回。
     */
    private String splitAcademicYearToSingleYear(String academicYear, Integer semester) {
        if (academicYear == null || academicYear.isEmpty()) {
            return null;
        }
        int dash = academicYear.indexOf('-');
        if (dash <= 0) {
            return academicYear;
        }
        String startYear = academicYear.substring(0, dash);
        String endYear = academicYear.substring(dash + 1);
        return semester != null && semester == 2 ? endYear : startYear;
    }

    /**
     * 学期格式转换：本地 Integer 1/2 → 学校API "秋"/"春"
     * 对齐 PDF 返回示例（varchar 中文）：秋=第一学期(Fall), 春=第二学期(Spring)。
     */
    private String toSchoolSemester(Integer semester) {
        if (semester == null) {
            return null;
        }
        switch (semester) {
            case 1:
                return "秋";
            case 2:
                return "春";
            default:
                return String.valueOf(semester);
        }
    }

    @Override
    public List<Map<String, Object>> fetchTeacherCourses(Map<String, String> params) {
        return fetchData("teacher_courses", params);
    }

    @Override
    public boolean testConnection() {
        String testEndpoint = properties.getApi().getEndpointPath("administrative_class");
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            // 使用行政班接口测试连接（完整 URL）
            if (testEndpoint == null || testEndpoint.isEmpty()) {
                logger.warn("学校 API 连接测试失败：administrative_class 端点未配置");
                return false;
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    testEndpoint + "?$top=1", HttpMethod.GET, entity, String.class);
            boolean ok = response.getStatusCode().is2xxSuccessful();
            if (!ok) {
                logger.error("学校 API 连接测试返回非成功状态 endpoint={} status={} bodyPreview={}",
                        testEndpoint, response.getStatusCode(), truncateForLog(response.getBody()));
            }
            return ok;
        } catch (BusinessException e) {
            logger.error("学校 API 连接测试失败（认证未配置） endpoint={} err={}",
                    testEndpoint, e.getMessage());
            return false;
        } catch (HttpStatusCodeException e) {
            logger.error("学校 API 连接测试失败（HTTP 错误） endpoint={} status={} bodyPreview={} err={}",
                    testEndpoint, e.getStatusCode(), truncateForLog(e.getResponseBodyAsString()),
                    e.getMessage(), e);
            return false;
        } catch (RestClientException e) {
            logger.error("学校 API 连接测试失败（网络/超时） endpoint={} err={}",
                    testEndpoint, e.getMessage(), e);
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
     * <p>
     * <b>重要</b>：CDSP API 实际不支持 $beauty 参数（返回 IY1103 错误），
     * 尽管 PDF 文档第 6 节提到该参数。API 默认返回<b>中文参数名</b>作为 JSON keys
     * （如 "学号"/"开课计划id"/"课程名称"），与 V12 {@code field_mappings.source_field} 存的
     * 英文别名（xh/kkjhid/kcmc）不一致 —— 字段映射层
     * ({@link FieldMappingServiceImpl#applyMapping}) 通过 {@link SchoolDataFieldAlias}
     * 做中文→英文别名兜底查表，保证同步不被 key 不匹配拦截。
     * <p>
     * 支持的 OData 参数仅限：$top, $skip（分页），其他参数（$filter/$select/$beauty）均不支持。
     */
    private List<Map<String, Object>> fetchDataWithPath(String fullUrl, Map<String, String> params) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            logger.error("学校数据 API URL 未配置（fetchDataWithPath 接收到空 URL），返回空集合");
            return Collections.emptyList();
        }

        Map<String, String> effectiveParams = params == null
                ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(params);

        String url = buildUrl(fullUrl, effectiveParams);

        int retryAttempts = properties.getApi().getRetryAttempts();
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                HttpHeaders headers = buildHeaders();
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                logger.info("学校数据 API 请求中 url={} attempt={}/{}", url, attempt, retryAttempts);
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                if (response.getBody() == null || response.getBody().isEmpty()) {
                    logger.warn("学校数据 API 响应体为空 url={} status={}", url, response.getStatusCode());
                    return Collections.emptyList();
                }

                return parseResponse(response.getBody(), url);
            } catch (HttpStatusCodeException e) {
                // 4xx/5xx：必定含状态码 + 响应体，属于上游业务错误（鉴权失败/参数错误/限流等）
                logger.error("学校数据 API HTTP 错误 url={} status={} attempt={}/{} bodyPreview={}",
                        url, e.getStatusCode(), attempt, retryAttempts,
                        truncateForLog(e.getResponseBodyAsString()));
                if (attempt == retryAttempts) {
                    return Collections.emptyList();
                }
            } catch (ResourceAccessException e) {
                // 网络层错误（连接超时、DNS 失败、SSL 握手）
                logger.warn("学校数据 API 网络异常 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage());
                if (attempt == retryAttempts) {
                    logger.error("学校数据 API 已重试 {} 次仍失败（网络） url={} err={}",
                            retryAttempts, url, e.getMessage(), e);
                    return Collections.emptyList();
                }
            } catch (RestClientException e) {
                logger.warn("学校数据 API 调用失败 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage());
                if (attempt == retryAttempts) {
                    logger.error("学校数据 API 已重试 {} 次仍失败 url={} err={}",
                            retryAttempts, url, e.getMessage(), e);
                    return Collections.emptyList();
                }
            } catch (Exception e) {
                logger.error("学校数据 API 未预期异常 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage(), e);
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

    /**
     * Build full URL with query parameters.
     * 对 key 和 value 做 URL 编码，防止含 &/=/空格/中文的值破坏 URL 结构。
     */
    private String buildUrl(String fullUrl, Map<String, String> params) {
        StringBuilder url = new StringBuilder(fullUrl);

        if (params != null && !params.isEmpty()) {
            url.append("?");
            params.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    url.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                       .append("=")
                       .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
                       .append("&");
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
    private List<Map<String, Object>> parseResponse(String responseBody, String url) {
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
                    // 上游业务错误码（鉴权失败/参数错误/限流），记录为 error 级别
                    logger.error("学校 API 返回非 200 业务错误 url={} code={} msg={} bodyPreview={}",
                            url, code, wrapper.get("msg"), truncateForLog(responseBody));
                    return Collections.emptyList();
                }

                // Check common wrapper keys（"value" 是 OData 规范标准字段，cdsp v2 使用）
                for (String key : List.of("value", "data", "result", "records", "list", "items")) {
                    Object data = wrapper.get(key);
                    if (data instanceof List) {
                        return (List<Map<String, Object>>) data;
                    }
                }

                logger.error("学校 API 响应 wrapper 不包含数据数组字段 url={} keys={} bodyPreview={}",
                        url, wrapper.keySet(), truncateForLog(responseBody));
                return Collections.emptyList();
            } catch (Exception e2) {
                logger.error("学校 API 响应体无法解析为 JSON url={} bodyPreview={} err={}",
                        url, truncateForLog(responseBody), e2.getMessage(), e2);
                return Collections.emptyList();
            }
        }
    }

    /**
     * 截断响应体用于日志，避免泄漏过长内容或敏感字段回显
     */
    private String truncateForLog(String body) {
        if (body == null) return "null";
        return body.length() > 300 ? body.substring(0, 300) + "...(truncated)" : body;
    }
}
