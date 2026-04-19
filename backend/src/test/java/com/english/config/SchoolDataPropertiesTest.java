package com.english.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SchoolDataProperties#ApiProperties#sign(long)} 算法测试
 * <p>
 * 官方规范（行政班基本信息接口文档.pdf 2.1 节）：
 *   "对拼接后的字符串做 md5 变换，然后再对变换后的字符串做 base64 变换"
 *   — "变换后的字符串" = MD5 的十六进制字符串表示（32 字符小写）
 *   — 最终 sign = Base64(hex.getBytes(UTF-8))
 */
class SchoolDataPropertiesTest {

    @Test
    void sign_producesBase64OfHexMd5_notBase64OfRawBytes() {
        SchoolDataProperties.ApiProperties api = new SchoolDataProperties.ApiProperties();
        api.setAppId("testapp");
        api.setAppSecret("testsecret");

        // Python 参考值（等价 Python 实现）：
        //   raw = "testapptestsecret1234567890"
        //   md5_hex = hashlib.md5(raw.encode()).hexdigest()
        //           = "4b2d24a3a6081235b166313b3512c51f"
        //   sign = base64.b64encode(md5_hex.encode()).decode()
        //        = "NGIyZDI0YTNhNjA4MTIzNWIxNjYzMTNiMzUxMmM1MWY="
        String actual = api.sign(1234567890L);
        assertEquals("NGIyZDI0YTNhNjA4MTIzNWIxNjYzMTNiMzUxMmM1MWY=", actual);
        assertEquals(44, actual.length(), "Base64 of 32-char hex 应为 44 字符（含 padding），非 24 字符");
    }

    @Test
    void sign_returnsNullWhenCredentialsMissing() {
        SchoolDataProperties.ApiProperties api = new SchoolDataProperties.ApiProperties();
        assertNull(api.sign(System.currentTimeMillis()));

        api.setAppId("");
        api.setAppSecret("abc");
        assertNull(api.sign(System.currentTimeMillis()));

        api.setAppId("abc");
        api.setAppSecret("");
        assertNull(api.sign(System.currentTimeMillis()));
    }

    @Test
    void sign_producesDifferentSignaturesForDifferentTimestamps() {
        SchoolDataProperties.ApiProperties api = new SchoolDataProperties.ApiProperties();
        api.setAppId("app");
        api.setAppSecret("secret");
        assertNotEquals(api.sign(1000L), api.sign(2000L));
    }
}
