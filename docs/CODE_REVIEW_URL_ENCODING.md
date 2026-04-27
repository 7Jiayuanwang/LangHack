# URL 构建代码审查检查清单

**目的**：防止 URL 参数编码错误再次发生

**背景**：2026-04-27 发现 `SchoolDataApiClientImpl.buildUrl` 方法对参数名进行了 URL 编码，导致 CDSP API 分页功能完全失效，浪费数小时排查。

---

## 🔴 强制检查项（必须通过）

### 1. URL 参数编码规则

**规则**：构建 URL 查询参数时，**参数名（key）不能编码，只能对参数值（value）编码**。

#### ✅ 正确示例

```java
// 方式 1：手动构建（推荐用于简单场景）
String url = baseUrl + "?key1=" + URLEncoder.encode(value1, StandardCharsets.UTF_8) 
                     + "&key2=" + URLEncoder.encode(value2, StandardCharsets.UTF_8);

// 方式 2：StringBuilder（推荐用于多参数）
StringBuilder url = new StringBuilder(baseUrl);
url.append("?");
params.forEach((key, value) -> {
    url.append(key)  // ✅ key 不编码
       .append("=")
       .append(URLEncoder.encode(value, StandardCharsets.UTF_8))  // ✅ value 编码
       .append("&");
});

// 方式 3：UriComponentsBuilder（推荐用于复杂场景）
String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
    .queryParam("key1", value1)  // 自动处理编码
    .queryParam("key2", value2)
    .toUriString();
```

#### ❌ 错误示例

```java
// ❌ 错误：对 key 也进行编码
url.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
   .append("=")
   .append(URLEncoder.encode(value, StandardCharsets.UTF_8));

// 结果：$top=1000 → %24top=1000 → API 无法识别
```

### 2. OData 参数特别注意

OData 参数（`$top`, `$skip`, `$filter`, `$orderby` 等）是协议关键字，**必须保持原样**。

```java
// ✅ 正确
String url = baseUrl + "?$top=1000&$skip=500";

// ❌ 错误
String url = baseUrl + "?%24top=1000&%24skip=500";  // API 返回 IY1103 错误
```

### 3. 单元测试覆盖

**所有手动构建 URL 的方法必须有单元测试**，测试用例必须包括：

- ✅ OData 参数（`$top`, `$skip`）不被编码
- ✅ 普通参数名不被编码
- ✅ 参数值正确编码（中文、特殊字符）
- ✅ 空参数、null 值处理

**参考测试**：`SchoolDataApiClientImplUrlEncodingTest.java`

### 4. 日志打印完整 URL

**所有 HTTP 请求必须打印完整 URL**，便于快速发现编码问题。

```java
// ✅ 正确
logger.info("API 请求 URL: {}", url);

// ❌ 错误：只打印端点，看不到参数
logger.info("API 请求端点: {}", endpoint);
```

---

## ⚠️ 建议检查项（推荐通过）

### 5. 使用框架工具而非手动拼接

**推荐**：优先使用 `UriComponentsBuilder` 或 `RestTemplate` 的 `uriVariables` 参数，避免手动处理编码。

```java
// ✅ 推荐：使用 UriComponentsBuilder
String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
    .queryParam("$top", 1000)
    .queryParam("$skip", 500)
    .queryParam("xq", "秋")
    .toUriString();

// ✅ 推荐：使用 RestTemplate uriVariables
Map<String, Object> uriVars = Map.of("top", 1000, "skip", 500);
restTemplate.getForObject(baseUrl + "?$top={top}&$skip={skip}", String.class, uriVars);
```

**注意**：`UriComponentsBuilder` 会自动处理编码，但要确保参数名不被编码。

### 6. 代码注释说明编码规则

手动构建 URL 的方法应该有注释说明编码规则：

```java
/**
 * Build full URL with query parameters.
 * ⚠️ 关键：参数 key 不编码（保留 $ 等 OData 特殊字符），只对 value 编码。
 * 原因：URLEncoder.encode("$top") → "%24top"，API 无法识别。
 */
private String buildUrl(String fullUrl, Map<String, String> params) {
    // ...
}
```

### 7. 集成测试验证

除了单元测试，还应该有集成测试验证实际 API 调用：

```java
@Test
void fetchData_shouldSupportPagination() {
    // 拉取第 1 页
    List<Map<String, Object>> page1 = client.fetchData("data_source", Map.of("$top", "100", "$skip", "0"));
    assertThat(page1).hasSize(100);
    
    // 拉取第 2 页
    List<Map<String, Object>> page2 = client.fetchData("data_source", Map.of("$top", "100", "$skip", "100"));
    assertThat(page2).hasSize(100);
    
    // 验证两页数据不重复
    assertThat(page1).doesNotContainAnyElementsOf(page2);
}
```

---

## 📋 代码审查检查表

在审查涉及 URL 构建的代码时，使用以下检查表：

- [ ] **参数名是否编码**？→ 必须是 NO
- [ ] **参数值是否编码**？→ 必须是 YES（如果包含特殊字符）
- [ ] **OData 参数是否保持原样**？→ 必须是 YES（`$top`, `$skip` 等）
- [ ] **是否有单元测试**？→ 必须是 YES
- [ ] **单元测试是否覆盖 OData 参数**？→ 必须是 YES
- [ ] **是否打印完整 URL 日志**？→ 必须是 YES
- [ ] **是否考虑使用框架工具**？→ 推荐 YES
- [ ] **是否有代码注释说明编码规则**？→ 推荐 YES

---

## 🔍 排查指南

### 症状：API 返回 `[IY1103] NOT SUPPORTED: URI: customQuery`

**可能原因**：
1. ❌ 参数名被错误编码（如 `$top` → `%24top`）
2. ❌ 使用了不支持的参数（如 `$filter`, `$beauty`）
3. ❌ 参数格式错误（如 `xn=2025-2026` 应该是 `xn=2025`）

**排查步骤**：
1. 检查日志中的完整 URL
2. 确认参数名是否包含 `%` 编码字符
3. 对照 API 文档确认参数名和格式
4. 使用 curl 或 Postman 手动测试正确的 URL

### 症状：分页功能不工作，只能获取 1000 条数据

**可能原因**：
1. ❌ 分页参数被错误编码
2. ❌ 分页参数没有传递到 API
3. ❌ API 不支持分页（需要确认文档）

**排查步骤**：
1. 检查日志中是否有"分页拉取"相关日志
2. 检查第 2 页请求的 URL 是否包含 `$skip` 参数
3. 确认 `$skip` 参数值是否正确（应该是 1000, 2000, ...）
4. 使用 curl 手动测试分页 URL

---

## 📚 相关文档

- **修复文档**：`docs/CDSP_PAGINATION_FIX_2026-04-27.md`
- **记忆文档**：`memory/feedback_url_encoding_critical.md`
- **单元测试**：`backend/src/test/java/com/english/service/impl/SchoolDataApiClientImplUrlEncodingTest.java`
- **CDSP API 规范**：`docs/SCHOOL_DATA_API_SPEC.md`

---

## 🎯 最佳实践总结

1. **参数名不编码，只编码参数值**
2. **优先使用框架工具（UriComponentsBuilder）**
3. **所有 URL 构建方法必须有单元测试**
4. **打印完整 URL 日志便于排查**
5. **代码注释说明编码规则**
6. **遇到 API 错误先检查 URL 格式**
7. **用户反馈往往比我们的假设更接近真相**

---

**记住**：这不是一个小错误，这是一个架构级的失误。永远不要再犯！
