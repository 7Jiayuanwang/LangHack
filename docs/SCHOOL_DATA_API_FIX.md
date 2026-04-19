# 学校数据接口地址修复

> **⚠️ 2026-04-18 更新**: 官方认证方式为 **appId + appSecret + timestamp 签名**，非 Bearer Token。
> - 请求头: `appId`, `timestamp`（毫秒）, `sign`（Base64(MD5(appId+appSecret+timestamp))）
> - 环境变量: `SCHOOL_API_APP_ID` + `SCHOOL_API_APP_SECRET`（旧 `SCHOOL_API_TOKEN` 已废弃）
> - 每次请求实时生成 timestamp 和 sign，保证签名时效性

## 问题发现

代码中配置的接口地址与官方文档**完全不符**：

### 错误配置（修复前）
```java
baseUrl = "https://api.imau.edu.cn"
endpoints = [
    "/api/xzxx",   // 行政班
    "/api/kcxx",   // 课程
    "/api/bzkcxx", // 教学班
    ...
]
```

### 官方实际接口（基于官方文档）
每个接口都是**完全独立的 URL**，不是 baseUrl + path 的组合：

| 数据源 | 官方完整 URL |
|--------|-------------|
| 行政班基本信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337` |
| 课程基本信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529094506435` |
| 教学班信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_172180403086015` |
| 课程安排 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529037780151` |
| 学生选课 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529480287192` |
| 等级考试成绩 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076` |
| 教学计划 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171528965502646` |
| 教师授课 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529464567780` |
| 学生课表 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529439727434` |

## 修复内容

### 1. SchoolDataProperties.java

**修改位置**：第 78-105 行

```java
public static class ApiProperties {
    /** API 基础URL（已废弃，每个接口使用完整独立 URL） */
    @Deprecated
    private String baseUrl = "https://cdsp.imau.edu.cn";

    /** API 端点完整 URL 映射（基于官方接口文档） */
    private List<EndpointMapping> endpoints = Arrays.asList(
            new EndpointMapping("administrative_class", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337"),
            new EndpointMapping("school_course", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529094506435"),
            new EndpointMapping("teaching_class", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_172180403086015"),
            new EndpointMapping("course_schedule", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529037780151"),
            new EndpointMapping("course_selection", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529480287192"),
            new EndpointMapping("level_exam_scores", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076"),
            new EndpointMapping("teaching_plans", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171528965502646"),
            new EndpointMapping("teacher_courses", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529464567780"),
            new EndpointMapping("student_schedule", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529439727434")
    );
}
```

**白名单更新**：第 23-27 行
```java
private static final List<String> API_ALLOWED_DOMAINS = Arrays.asList(
        "cdsp.imau.edu.cn",  // 新增：官方数据服务平台域名
        "api.imau.edu.cn",
        "imau.edu.cn"
);
```

### 2. SchoolDataApiClientImpl.java

**修改 1**：类注释（第 22-36 行）
```java
/**
 * 学校数据API客户端实现 - 通过REST API对接学校数据源
 * <p>
 * 每个数据源使用独立的完整 URL（基于官方接口文档）:
 * - administrative_class: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337
 * - school_course: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529094506435
 * - teaching_class: https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_172180403086015
 * ...
 */
```

**修改 2**：构造函数日志（第 57-60 行）
```java
logger.info("SchoolDataApiClient initialized - connectTimeout: {}ms, readTimeout: {}ms, endpoints: {}",
        properties.getApi().getConnectTimeout(),
        properties.getApi().getReadTimeout(),
        properties.getApi().getEndpoints().size());
```

**修改 3**：fetchDataWithPath 方法（第 169-210 行）
```java
/**
 * 通过完整 URL 获取数据
 */
private List<Map<String, Object>> fetchDataWithPath(String fullUrl, Map<String, String> params) {
    if (fullUrl == null || fullUrl.isEmpty()) {
        logger.error("School data API URL is not configured");
        return Collections.emptyList();
    }

    String url = buildUrl(fullUrl, params);
    // ... 后续逻辑不变
}
```

**修改 4**：buildUrl 方法（第 212-236 行）
```java
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
```

**修改 5**：testConnection 方法（第 128-151 行）
```java
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
    } catch (RestClientException e) {
        logger.error("School data API connection test failed", e);
        return false;
    }
}
```

## 关键变化

1. **不再拼接 URL**：`path` 字段现在存储完整 URL，不再是相对路径
2. **移除 baseUrl 依赖**：`buildUrl()` 方法直接使用完整 URL
3. **白名单更新**：添加 `cdsp.imau.edu.cn` 域名
4. **测试连接优化**：直接使用完整 URL 测试

## 验证方法

### 1. 单元测试
```bash
cd backend
mvn test -Dtest=SchoolDataApiClientImplTest
```

### 2. 集成测试
1. 运维端 → 学校数据配置
2. 配置 API Token
3. 点击"测试连接"
4. 预期：连接成功（如果 token 有效）

### 3. 数据同步测试
1. 运维端 → 数据浏览
2. 选择"行政班"
3. 点击"刷新数据"
4. 预期：能够成功拉取数据

## 注意事项

1. **Token 认证**：所有接口需要在请求头中携带 `Authorization: Bearer {token}`
2. **请求方法**：支持 POST 和 GET
3. **响应格式**：JSON
4. **接口 ID 唯一性**：每个接口的 ID（如 `cdsp_171094991198337`）是唯一的，不能随意修改

## 相关文件

- `backend/src/main/java/com/english/config/SchoolDataProperties.java`
- `backend/src/main/java/com/english/service/impl/SchoolDataApiClientImpl.java`
- `backend/src/main/resources/db/migration/V19__school_data_field_mappings.sql`

## 参考文档

官方接口文档位置：
```
E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\
├── 行政班基本信息接口文档.pdf
├── 课程基本信息接口文档.pdf
├── 学生课表接口文档.pdf
├── 教师授课接口文档.pdf
├── 本专科生课程信息接口文档.pdf
├── 本专科生等级考试成绩接口文档.pdf
├── 课程安排信息接口文档.pdf
├── 教学计划信息接口文档.pdf
└── 学生选课接口文档.pdf
```
