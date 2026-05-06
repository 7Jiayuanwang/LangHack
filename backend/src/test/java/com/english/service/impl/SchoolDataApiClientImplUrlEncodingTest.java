package com.english.service.impl;

import com.english.config.SchoolDataProperties;
import com.english.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * URL 参数编码测试 - 防止 URLEncoder.encode(key) 错误再次发生
 *
 * <p><b>背景</b>：2026-04-27 发现 buildUrl 方法对参数名（key）进行了 URL 编码，
 * 导致 OData 参数 $top/$skip 被编码成 %24top/%24skip，CDSP API 返回 IY1103 错误，
 * 所有数据源只能获取 1000 条数据，浪费数小时排查。
 *
 * <p><b>规则</b>：URL 查询参数构建时，参数名（key）不能编码，只能对参数值（value）编码。
 *
 * @see <a href="file:///G:/GitHub_local/project/英语学习平台/LangHack/docs/CDSP_PAGINATION_FIX_2026-04-27.md">CDSP_PAGINATION_FIX_2026-04-27.md</a>
 * @see <a href="file:///C:/Users/ASUS/.claude/projects/G--GitHub-local-project--------LangHack/memory/feedback_url_encoding_critical.md">feedback_url_encoding_critical.md</a>
 */
class SchoolDataApiClientImplUrlEncodingTest {

    private SchoolDataApiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new SchoolDataApiClientImpl(new ObjectMapper(), new SchoolDataProperties());
    }

    /**
     * 测试 OData 分页参数不被编码
     *
     * <p><b>关键</b>：$top 和 $skip 必须保持原样，不能被编码成 %24top 和 %24skip
     */
    @Test
    void buildUrl_shouldNotEncodeODataParameterKeys() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("$top", "1000");
        params.put("$skip", "500");

        String url = invokeBuildUrl("https://api.example.com/data", params);

        // ✅ 参数名不应该被编码
        assertThat(url).contains("$top=1000");
        assertThat(url).contains("$skip=500");

        // ❌ 不应该出现编码后的参数名
        assertThat(url).doesNotContain("%24top");
        assertThat(url).doesNotContain("%24skip");
    }

    /**
     * 测试普通参数名不被编码
     */
    @Test
    void buildUrl_shouldNotEncodeRegularParameterKeys() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("xn", "2025");
        params.put("xq", "秋");
        params.put("xh", "2023122066164");

        String url = invokeBuildUrl("https://api.example.com/data", params);

        // ✅ 参数名不应该被编码
        assertThat(url).contains("xn=");
        assertThat(url).contains("xq=");
        assertThat(url).contains("xh=");
    }

    /**
     * 测试参数值正确编码
     */
    @Test
    void buildUrl_shouldEncodeParameterValues() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("xq", "秋");  // 中文
        params.put("name", "张三");  // 中文
        params.put("url", "http://example.com/path?a=1&b=2");  // 特殊字符

        String url = invokeBuildUrl("https://api.example.com/data", params);

        // ✅ 参数值应该被编码
        assertThat(url).contains("xq=%E7%A7%8B");  // "秋" 的 UTF-8 编码
        assertThat(url).contains("name=%E5%BC%A0%E4%B8%89");  // "张三" 的 UTF-8 编码
        assertThat(url).contains("url=http%3A%2F%2Fexample.com");  // URL 特殊字符编码

        // ❌ 参数值不应该是原始中文（未编码）
        assertThat(url).doesNotContain("xq=秋");
        assertThat(url).doesNotContain("name=张三");
    }

    /**
     * 测试混合参数（OData + 普通参数）
     */
    @Test
    void buildUrl_shouldHandleMixedParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("$top", "1000");
        params.put("$skip", "500");
        params.put("xn", "2025");
        params.put("xq", "秋");

        String url = invokeBuildUrl("https://api.example.com/data", params);

        // ✅ 所有参数名都不应该被编码
        assertThat(url).contains("$top=1000");
        assertThat(url).contains("$skip=500");
        assertThat(url).contains("xn=2025");
        assertThat(url).contains("xq=%E7%A7%8B");  // 只有值被编码

        // ❌ 参数名不应该被编码
        assertThat(url).doesNotContain("%24top");
        assertThat(url).doesNotContain("%24skip");
    }

    /**
     * 测试空参数
     */
    @Test
    void buildUrl_shouldHandleEmptyParameters() {
        String url = invokeBuildUrl("https://api.example.com/data", null);
        assertThat(url).isEqualTo("https://api.example.com/data");

        url = invokeBuildUrl("https://api.example.com/data", new LinkedHashMap<>());
        assertThat(url).isEqualTo("https://api.example.com/data");
    }

    /**
     * 测试参数值为空或 null
     */
    @Test
    void buildUrl_shouldSkipNullOrEmptyValues() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key1", "value1");
        params.put("key2", null);
        params.put("key3", "");
        params.put("key4", "value4");

        String url = invokeBuildUrl("https://api.example.com/data", params);

        // ✅ 只包含非空值的参数
        assertThat(url).contains("key1=value1");
        assertThat(url).contains("key4=value4");

        // ❌ 不包含 null 或空值的参数
        assertThat(url).doesNotContain("key2");
        assertThat(url).doesNotContain("key3");
    }

    @Test
    void parseResponse_shouldReportErrcodeWrapperClearly() {
        String body = "{\"errcode\":\"999\",\"errmsg\":\"系统内部错误，请联系管理员\"}";

        assertThatThrownBy(() -> invokeParseResponse(body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校 API 返回错误")
                .hasMessageContaining("999")
                .hasMessageContaining("系统内部错误");
    }

    @Test
    void parseResponse_shouldReportNestedErrorClearly() {
        String body = "{\"error\":{\"code\":\"10016\",\"message\":\"接口鉴权失败,接口签名(sign)无效\"}}";

        assertThatThrownBy(() -> invokeParseResponse(body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校 API 返回错误")
                .hasMessageContaining("10016")
                .hasMessageContaining("接口鉴权失败");
    }

    /**
     * 使用反射调用 private buildUrl 方法
     */
    private String invokeBuildUrl(String fullUrl, Map<String, String> params) {
        try {
            return (String) ReflectionTestUtils.invokeMethod(client, "buildUrl", fullUrl, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke buildUrl method", e);
        }
    }

    private Object invokeParseResponse(String responseBody) {
        try {
            return ReflectionTestUtils.invokeMethod(client, "parseResponse", responseBody, "https://api.example.com/data");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }
}
