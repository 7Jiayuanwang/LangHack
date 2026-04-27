# CDSP API 分页 1000 条限制根因分析与修复

**修复日期**: 2026-04-27  
**问题描述**: 所有 CDSP API 数据源只能获取 1000 条数据，分页参数 `$top` 和 `$skip` 返回 IY1103 错误

---

## 一、问题现象

### 1.1 症状

所有 9 个数据源（administrative_class, school_course, teaching_class, course_schedule, course_selection, level_exam_scores, teaching_plans, teacher_courses, student_schedule）在尝试拉取第 2 页数据时均失败：

```
HTTP 400 Bad Request
[IY1103] NOT SUPPORTED: URI: customQuery
URL: .../cdsp_XXX?%24top=1000&%24skip=1000
```

日志模式：
```
POST 分页也不支持，停止循环拉取 dataSource=XXX skip=1000
```

### 1.2 用户反馈

用户明确指出："学校可能没有这方面限制"，提示问题可能在我们的实现中，而非 API 限制。

---

## 二、根因分析

### 2.1 URL 参数编码错误（根本原因）

**问题代码**（`SchoolDataApiClientImpl.java:558`）：

```java
url.append(URLEncoder.encode(key, StandardCharsets.UTF_8))  // ❌ 错误
   .append("=")
   .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
```

**错误行为**：
- `$top` 被编码成 `%24top`
- `$skip` 被编码成 `%24skip`
- CDSP API 无法识别 `%24top`，返回 `[IY1103] NOT SUPPORTED: URI: customQuery`

**证据**：
- PDF 文档第 6 节明确说明分页参数为 `$top` 和 `$skip`（保留 `$` 符号）
- OData 规范要求参数名保持原样，不进行 URL 编码

### 2.2 错误的 POST 降级逻辑（次要问题）

**问题代码**（`SchoolDataApiClientImpl.java:356-375`）：

```java
if (e.getMessage() != null && e.getMessage().contains("IY1103")) {
    // 如果 GET 分页失败，尝试切换到 POST
    if (!usePostMethod) {
        logger.warn("分页参数 GET 不支持，切换到 POST 方法");
        usePostMethod = true;
        nextPage = fetchPagePost(fullUrl, pageParams);
    }
}
```

**错误行为**：
1. 遇到 IY1103 错误时切换到 POST 方法
2. `fetchPagePost` 把参数放在请求体（form data）中
3. PDF 文档第 3 节明确说明：**所有接口均为 GET 请求**

**为什么这个降级逻辑是错误的**：
- CDSP API 不支持 POST 方法传递查询参数
- 分页参数必须在 URL 查询字符串中，不能在请求体中
- 这个降级逻辑掩盖了真正的问题（URL 编码错误）

---

## 三、修复方案

### 3.1 修复 URL 构建逻辑

**修复文件**：`backend/src/main/java/com/english/service/impl/SchoolDataApiClientImpl.java`

**修复内容**（line 547-571）：

```java
/**
 * Build full URL with query parameters.
 * ⚠️ 关键修复：参数 key 不编码（保留 $ 等 OData 特殊字符），只对 value 编码。
 * 原因：URLEncoder.encode("$top") → "%24top"，CDSP API 无法识别，返回 IY1103 错误。
 */
private String buildUrl(String fullUrl, Map<String, String> params) {
    StringBuilder url = new StringBuilder(fullUrl);

    if (params != null && !params.isEmpty()) {
        url.append("?");
        params.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                url.append(key)  // ✅ key 不编码，保留 $top/$skip 等 OData 参数
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
```

### 3.2 简化分页错误处理

**修复内容**（line 343-360）：

```java
try {
    // ✅ 修复：始终使用 GET 方法，参数放在 URL 查询字符串中（符合 PDF 文档规范）
    // 之前的 POST 降级逻辑是错误的，IY1103 错误是因为 buildUrl 对 $top/$skip 的 key 进行了编码
    if (usePostMethod) {
        nextPage = fetchPagePost(fullUrl, pageParams);
    } else {
        nextPage = fetchPage(buildUrl(fullUrl, pageParams));
    }
} catch (BusinessException e) {
    // 如果分页失败，记录错误并停止循环
    logger.warn("分页拉取失败，停止循环 dataSource={} skip={} error={}",
            dataSource, skip, e.getMessage());
    break;
}
```

**改进点**：
- 移除了复杂的 IY1103 错误检测和 POST 降级逻辑
- 简化为直接记录错误并停止循环
- 保留 `usePostMethod` 标志以支持 student_schedule 等特殊端点（这些端点本身就需要 POST）

---

## 四、验证步骤

### 4.1 重启应用

```bash
cd G:\GitHub_local\project\英语学习平台\LangHack
docker-compose restart backend
```

### 4.2 触发同步

运维面板 → 学校数据配置 → 手动同步 → 选择任意数据源（如 administrative_class）

### 4.3 检查日志

```bash
docker logs langhack-backend | grep "分页拉取\|totalRows"
```

**预期输出**（如果数据源有超过 1000 条数据）：

```
检测到大量数据（>= 1000 条），启动分页拉取 dataSource=administrative_class firstPageRows=1000
拉取第 2 页 dataSource=administrative_class pageRows=500 totalRows=1500
拉取第 3 页 dataSource=administrative_class pageRows=200 totalRows=1700
分页拉取完成（最后一页） dataSource=administrative_class totalPages=3 totalRows=1700
```

### 4.4 验证 URL 格式

检查日志中的 URL 格式：

```bash
docker logs langhack-backend | grep "学校数据 API 请求中"
```

**预期 URL 格式**：

```
✅ 正确：.../cdsp_XXX?$top=1000&$skip=1000
❌ 错误：.../cdsp_XXX?%24top=1000&%24skip=1000
```

---

## 五、技术要点总结

### 5.1 OData 参数编码规范

| 参数类型 | 是否编码 | 示例 |
|---------|---------|------|
| 参数名（key） | ❌ 不编码 | `$top`, `$skip`, `xn`, `xq` |
| 参数值（value） | ✅ 编码 | `2025` → `2025`, `秋` → `%E7%A7%8B` |

**原因**：
- OData 参数名（如 `$top`, `$skip`, `$filter`）是协议关键字，必须保持原样
- 参数值可能包含特殊字符（如中文、空格），需要编码以避免破坏 URL 结构

### 5.2 CDSP API 请求方式

| 端点 | 请求方式 | 参数位置 | 备注 |
|------|---------|---------|------|
| 大部分端点 | GET | URL 查询字符串 | 符合 PDF 文档第 3 节规范 |
| student_schedule | POST | 请求体（form data） | 特殊端点，不支持 GET |

**关键区别**：
- GET 请求：分页参数在 URL 中，如 `?$top=1000&$skip=1000`
- POST 请求：业务参数在请求体中，但**分页参数仍应在 URL 中**（待验证）

### 5.3 错误排查思路

当遇到 `[IY1103] NOT SUPPORTED: URI: customQuery` 错误时：

1. **检查 URL 格式**：参数名是否被错误编码（如 `%24top` 而非 `$top`）
2. **检查请求方式**：是否使用了正确的 HTTP 方法（GET vs POST）
3. **检查参数位置**：参数是在 URL 查询字符串还是请求体中
4. **对照 PDF 文档**：确认参数名、格式、请求方式是否符合规范

---

## 六、相关文档

- **PDF 接口文档**: `E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\`
  - 学生课表接口文档.pdf（第 6 节：分页参数说明）
  - 其他 8 个数据源的接口文档
- **CDSP API 规范**: `docs/SCHOOL_DATA_API_SPEC.md`（第 68 行：所有接口均为 GET 请求）
- **字段映射修复**: `docs/CDSP_FIELD_MAPPING_FIX_2026-04-27.md`

---

## 七、后续优化建议

### 7.1 单元测试

添加 `buildUrl` 方法的单元测试，确保：
- OData 参数名（`$top`, `$skip`）不被编码
- 中文参数值正确编码
- 特殊字符（`&`, `=`, 空格）正确处理

### 7.2 日志增强

在分页循环中添加更详细的日志：
- 每次请求的完整 URL（便于排查编码问题）
- 每页返回的记录数（便于判断是否到达最后一页）
- 累计拉取的总记录数

### 7.3 性能优化

- 考虑并行拉取多个数据源（当前是串行）
- 考虑增加分页大小（如果 API 支持 `$top=2000`）

---

**修复完成，等待验证。**
