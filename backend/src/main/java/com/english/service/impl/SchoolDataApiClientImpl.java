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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        return rowsFromCompleteResult("teaching_plan_courses", fetchDataWithPath("teaching_plan_courses", basePath + "/" + planId + "/courses", null));
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

        return rowsFromCompleteResult("student_schedule", fetchDataWithPath("student_schedule", basePath, params));
    }

    /**
     * 本地学年拆分为学校接口所需的单年份。
     * 规则：2025-2026 学年 = 2025秋（第一学期）+ 2026春（第二学期）。
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
                    testEndpoint, HttpMethod.GET, entity, String.class);
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

    SchoolDataFetchResult fetchDataWithSummary(String dataSource, Map<String, String> params) {
        String endpointPath = properties.getApi().getEndpointPath(dataSource);
        if (endpointPath == null) {
            logger.error("No API endpoint mapping configured for data source: {}", dataSource);
            return new SchoolDataFetchResult(Collections.emptyList(), SchoolDataFetchResult.PaginationSummary.empty());
        }
        return fetchDataWithPath(dataSource, endpointPath, params);
    }

    private List<Map<String, Object>> fetchData(String dataSource, Map<String, String> params) {
        return rowsFromCompleteResult(dataSource, fetchDataWithSummary(dataSource, params));
    }

    private List<Map<String, Object>> rowsFromCompleteResult(String dataSource, SchoolDataFetchResult fetchResult) {
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

    /**
     * <p>
     * <b>重要</b>：CDSP API 实际不支持 $beauty 参数（返回 IY1103 错误），
     * 尽管 PDF 文档第 6 节提到该参数。API 默认返回<b>中文参数名</b>作为 JSON keys
     * （如 "学号"/"开课计划id"/"课程名称"），与 V12 {@code field_mappings.source_field} 存的
     * 英文别名（xh/kkjhid/kcmc）不一致 —— 字段映射层
     * ({@link FieldMappingServiceImpl#applyMapping}) 通过 {@link SchoolDataFieldAlias}
     * 做中文→英文别名兜底查表，保证同步不被 key 不匹配拦截。
     * <p>
     * <b>分页策略</b>：首次请求不带分页参数，如果响应显示存在分页（pages > 1 或 hasNext），
     * 则使用 $top/$skip 循环拉取所有页。CDSP API 支持 OData 标准分页参数，但不支持 $filter。
     * <p>
     * <b>HTTP 方法自适应</b>：优先使用 GET 方法，如果返回 IY1103 错误则自动降级到 POST 方法。
     * 这是因为 CDSP 不同端点对 HTTP 方法的支持不一致（文档统一标注 POST&GET，但实际行为不同）。
     */
    private SchoolDataFetchResult fetchDataWithPath(String dataSource, String fullUrl, Map<String, String> params) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            logger.error("学校数据 API URL 未配置（fetchDataWithPath 接收到空 URL），返回空集合");
            return new SchoolDataFetchResult(Collections.emptyList(), SchoolDataFetchResult.PaginationSummary.empty());
        }

        Map<String, String> baseParams = params == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(params);

        // 首次请求：不带分页参数
        ParsedResponse firstPage;
        try {
            firstPage = fetchPage(buildUrl(fullUrl, baseParams));
        } catch (BusinessException e) {
            // 如果是 IY1103 错误（NOT SUPPORTED: URI: customQuery），降级到 POST 方法
            if (e.getMessage() != null && e.getMessage().contains("IY1103")) {
                logger.warn("学校数据 API GET 请求返回 IY1103 错误，降级到 POST 方法 dataSource={} url={}",
                        dataSource, fullUrl);
                firstPage = fetchPagePost(fullUrl, baseParams);
            } else {
                throw e;
            }
        }

        SchoolDataFetchResult.PaginationSummary firstSummary = buildPaginationSummary(firstPage);

        // 如果首页已完整，直接返回
        if (firstSummary.isComplete()) {
            return new SchoolDataFetchResult(firstPage.rows, firstSummary);
        }

        // 检测到分页，尝试循环拉取
        logger.info("检测到分页数据 dataSource={} firstPageRows={} 开始循环拉取", dataSource, firstPage.rows.size());

        List<Map<String, Object>> allRows = new ArrayList<>(firstPage.rows);
        int pageSize = firstPage.rows.size();
        if (pageSize == 0) {
            pageSize = 1000; // 默认每页 1000 条
        }

        int skip = pageSize;
        int maxPages = 100; // 防止无限循环
        int fetchedPages = 1;

        try {
            while (fetchedPages < maxPages) {
                Map<String, String> pageParams = new LinkedHashMap<>(baseParams);
                pageParams.put("$top", String.valueOf(pageSize));
                pageParams.put("$skip", String.valueOf(skip));

                ParsedResponse nextPage;
                try {
                    nextPage = fetchPage(buildUrl(fullUrl, pageParams));
                } catch (BusinessException e) {
                    if (e.getMessage() != null && e.getMessage().contains("IY1103")) {
                        logger.warn("分页参数不支持，停止循环拉取 dataSource={} skip={}", dataSource, skip);
                        break;
                    }
                    throw e;
                }

                if (nextPage.rows.isEmpty()) {
                    logger.info("分页拉取完成（空页） dataSource={} totalPages={} totalRows={}",
                            dataSource, fetchedPages, allRows.size());
                    break;
                }

                allRows.addAll(nextPage.rows);
                fetchedPages++;
                skip += nextPage.rows.size();

                logger.info("拉取第 {} 页 dataSource={} pageRows={} totalRows={}",
                        fetchedPages, dataSource, nextPage.rows.size(), allRows.size());

                // 如果本页数据少于 pageSize，说明已到最后一页
                if (nextPage.rows.size() < pageSize) {
                    logger.info("分页拉取完成（最后一页） dataSource={} totalPages={} totalRows=",
                            dataSource, fetchedPages, allRows.size());
                    break;
                }
            }

            if (fetchedPages >= maxPages) {
                logger.warn("分页拉取达到最大页数限制 dataSource={} maxPages={} totalRows={}",
                        dataSource, maxPages, allRows.size());
            }

        } catch (Exception e) {
            logger.error("分页拉取失败，返回已获取数据 dataSource={} fetchedPages={} totalRows={} err={}",
                    dataSource, fetchedPages, allRows.size(), e.getMessage(), e);
        }

        SchoolDataFetchResult.PaginationSummary finalSummary = new SchoolDataFetchResult.PaginationSummary(
                true,
                "PAGINATED_COMPLETE",
                fetchedPages,
                skip,
                pageSize,
                false,
                allRows.size()
        );

        return new SchoolDataFetchResult(allRows, finalSummary);
    }

    private ParsedResponse fetchPage(String url) {
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
                    return ParsedResponse.direct(Collections.emptyList());
                }

                return parseResponse(response.getBody(), url);
            } catch (HttpStatusCodeException e) {
                String responseBody = e.getResponseBodyAsString();
                logger.error("学校数据 API HTTP 错误 url={} status={} attempt={}/{} bodyPreview={}",
                        url, e.getStatusCode(), attempt, retryAttempts,
                        truncateForLog(responseBody));
                if (attempt == retryAttempts) {
                    // 如果响应体包含 IY1103 错误，抛出包含错误信息的异常以便上层降级到 POST
                    if (responseBody != null && responseBody.contains("IY1103")) {
                        throw new BusinessException("学校数据 API 请求失败: HTTP " + e.getStatusCode() + " [IY1103]");
                    }
                    throw new BusinessException("学校数据 API 请求失败: HTTP " + e.getStatusCode());
                }
            } catch (ResourceAccessException e) {
                logger.warn("学校数据 API 网络异常 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage());
                if (attempt == retryAttempts) {
                    logger.error("学校数据 API 已重试 {} 次仍失败（网络） url={} err={}",
                            retryAttempts, url, e.getMessage(), e);
                    throw new BusinessException("学校数据 API 网络异常: " + e.getMessage());
                }
            } catch (RestClientException e) {
                logger.warn("学校数据 API 调用失败 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage());
                if (attempt == retryAttempts) {
                    logger.error("学校数据 API 已重试 {} 次仍失败 url={} err={}",
                            retryAttempts, url, e.getMessage(), e);
                    throw new BusinessException("学校数据 API 调用失败: " + e.getMessage());
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                logger.error("学校数据 API 未预期异常 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage(), e);
                throw new BusinessException("学校数据 API 响应处理失败: " + e.getMessage());
            }
        }

        throw new BusinessException("学校数据 API 请求失败");
    }

    /**
     * 使用 POST 方法请求 CDSP API，参数放在请求体中（application/x-www-form-urlencoded）
     * 用于学生课表等不支持 GET 查询参数的端点
     */
    private ParsedResponse fetchPagePost(String url, Map<String, String> params) {
        int retryAttempts = properties.getApi().getRetryAttempts();
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                HttpHeaders headers = buildHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                // 构建表单参数
                MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
                if (params != null) {
                    params.forEach(formParams::add);
                }

                HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

                logger.info("学校数据 API POST 请求中 url={} params={} attempt={}/{}", url, params, attempt, retryAttempts);
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST, entity, String.class);

                if (response.getBody() == null || response.getBody().isEmpty()) {
                    logger.warn("学校数据 API 响应体为空 url={} status={}", url, response.getStatusCode());
                    return ParsedResponse.direct(Collections.emptyList());
                }

                return parseResponse(response.getBody(), url);
            } catch (HttpStatusCodeException e) {
                String responseBody = e.getResponseBodyAsString();
                logger.error("学校数据 API HTTP 错误 url={} status={} attempt={}/{} bodyPreview={}",
                        url, e.getStatusCode(), attempt, retryAttempts,
                        truncateForLog(responseBody));
                if (attempt == retryAttempts) {
                    // 如果响应体包含 IY1103 错误，抛出包含错误信息的异常以便上层降级到 POST
                    if (responseBody != null && responseBody.contains("IY1103")) {
                        throw new BusinessException("学校数据 API 请求失败: HTTP " + e.getStatusCode() + " [IY1103]");
                    }
                    throw new BusinessException("学校数据 API 请求失败: HTTP " + e.getStatusCode());
                }
            } catch (ResourceAccessException e) {
                logger.warn("学校数据 API 网络异常 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage());
                if (attempt == retryAttempts) {
                    logger.error("学校数据 API 已重试 {} 次仍失败（网络） url={} err={}",
                            retryAttempts, url, e.getMessage(), e);
                    throw new BusinessException("学校数据 API 网络异常: " + e.getMessage());
                }
            } catch (RestClientException e) {
                logger.warn("学校数据 API 调用失败 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage());
                if (attempt == retryAttempts) {
                    logger.error("学校数据 API 已重试 {} 次仍失败 url={} err={}",
                            retryAttempts, url, e.getMessage(), e);
                    throw new BusinessException("学校数据 API 调用失败: " + e.getMessage());
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                logger.error("学校数据 API 未预期异常 url={} attempt={}/{} err={}",
                        url, attempt, retryAttempts, e.getMessage(), e);
                throw new BusinessException("学校数据 API 响应处理失败: " + e.getMessage());
            }
        }

        throw new BusinessException("学校数据 API 请求失败");
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

    private String fingerprintPage(List<Map<String, Object>> pageRows) {
        if (pageRows == null || pageRows.isEmpty()) {
            return "empty";
        }
        Map<String, Object> first = pageRows.get(0);
        Map<String, Object> last = pageRows.get(pageRows.size() - 1);
        return pageRows.size() + "|" + fingerprintRecord(first) + "|" + fingerprintRecord(last);
    }

    private String resolveDataSourceName(String fullUrl) {
        for (SchoolDataProperties.EndpointMapping endpoint : properties.getApi().getEndpoints()) {
            if (fullUrl.equals(endpoint.getPath())) {
                return endpoint.getDataSource();
            }
        }
        return null;
    }

    private String fingerprintRecord(Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            return "null";
        }
        return record.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(5)
                .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("empty");
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
    private ParsedResponse parseResponse(String responseBody, String url) {
        try {
            return ParsedResponse.direct(objectMapper.readValue(responseBody,
                    new TypeReference<List<Map<String, Object>>>() {}));
        } catch (Exception e1) {
            try {
                Map<String, Object> wrapper = objectMapper.readValue(responseBody,
                        new TypeReference<Map<String, Object>>() {});

                Object code = wrapper.get("code");
                if (code instanceof Number && ((Number) code).intValue() != 200) {
                    logger.error("学校 API 返回非 200 业务错误 url={} code={} msg={} bodyPreview={}",
                            url, code, wrapper.get("msg"), truncateForLog(responseBody));
                    throw new BusinessException("学校 API 返回错误: " + code + " " + wrapper.get("msg"));
                }

                for (String key : List.of("value", "data", "result", "records", "list", "items")) {
                    Object data = wrapper.get(key);
                    if (data instanceof List) {
                        return new ParsedResponse((List<Map<String, Object>>) data, wrapper);
                    }
                }

                logger.error("学校 API 响应 wrapper 不包含数据数组字段 url={} keys={} bodyPreview={}",
                        url, wrapper.keySet(), truncateForLog(responseBody));
                throw new BusinessException("学校 API 响应缺少数据数组字段");
            } catch (BusinessException e2) {
                throw e2;
            } catch (Exception e2) {
                logger.error("学校 API 响应体无法解析为 JSON url={} bodyPreview={} err={}",
                        url, truncateForLog(responseBody), e2.getMessage(), e2);
                throw new BusinessException("学校 API 响应体无法解析为 JSON");
            }
        }
    }

    private SchoolDataFetchResult.PaginationSummary buildPaginationSummary(ParsedResponse parsed) {
        int rowCount = parsed.rows.size();
        if (parsed.wrapper == null) {
            return new SchoolDataFetchResult.PaginationSummary(
                    true,
                    rowCount == 0 ? "EMPTY_FIRST_PAGE" : "DIRECT_ARRAY_RESPONSE",
                    rowCount == 0 ? 0 : 1,
                    0,
                    0,
                    false,
                    rowCount
            );
        }

        Integer total = firstInteger(parsed.wrapper, "total", "count", "totalCount", "recordCount");
        Integer pageSize = firstInteger(parsed.wrapper, "pageSize", "size", "limit");
        Integer page = firstInteger(parsed.wrapper, "page", "current", "pageNum", "currentPage");
        Integer pages = firstInteger(parsed.wrapper, "pages", "totalPages", "pageCount");
        Boolean hasNext = firstBoolean(parsed.wrapper, "hasNext", "hasMore", "nextPage", "next");
        boolean nextValuePresent = hasMeaningfulValue(parsed.wrapper.get("next")) || hasMeaningfulValue(parsed.wrapper.get("nextPage"));

        if ((pages != null && pages > 1)
                || Boolean.TRUE.equals(hasNext)
                || nextValuePresent) {
            logger.error("学校 API 响应显示可能存在未拉取数据 total={} rows={} pages={} page={} hasNext={} nextPresent={}",
                    total, rowCount, pages, page, hasNext, nextValuePresent);
            return new SchoolDataFetchResult.PaginationSummary(
                    false,
                    "UPSTREAM_HAS_MORE_UNSUPPORTED",
                    rowCount == 0 ? 0 : 1,
                    0,
                    pageSize == null ? 0 : pageSize,
                    true,
                    rowCount
            );
        }

        // total > rowCount 仅记录警告，不阻止同步（CDSP API 可能返回不准确的 total）
        if (total != null && total > rowCount) {
            logger.warn("学校 API 响应 total={} 大于实际返回行数 rows={}，但无明确分页标志，视为完整响应", total, rowCount);
        }

        return new SchoolDataFetchResult.PaginationSummary(
                true,
                rowCount == 0 ? "EMPTY_FIRST_PAGE" : "WRAPPED_COMPLETE_RESPONSE",
                rowCount == 0 ? 0 : 1,
                0,
                pageSize == null ? 0 : pageSize,
                false,
                rowCount
        );
    }

    private Integer firstInteger(Map<String, Object> wrapper, String... keys) {
        for (String key : keys) {
            Object value = wrapper.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(String.valueOf(value).trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }

    private Boolean firstBoolean(Map<String, Object> wrapper, String... keys) {
        for (String key : keys) {
            Object value = wrapper.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value != null) {
                String normalized = String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
                if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
                    return true;
                }
                if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
                    return false;
                }
            }
        }
        return null;
    }

    private boolean hasMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() > 0;
        }
        String text = String.valueOf(value).trim();
        return !text.isEmpty() && !"false".equalsIgnoreCase(text) && !"0".equals(text) && !"null".equalsIgnoreCase(text);
    }

    private static class ParsedResponse {
        private final List<Map<String, Object>> rows;
        private final Map<String, Object> wrapper;

        private ParsedResponse(List<Map<String, Object>> rows, Map<String, Object> wrapper) {
            this.rows = rows == null ? Collections.emptyList() : rows;
            this.wrapper = wrapper;
        }

        private static ParsedResponse direct(List<Map<String, Object>> rows) {
            return new ParsedResponse(rows, null);
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
