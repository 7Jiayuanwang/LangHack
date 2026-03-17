package com.english.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Result DTO.
 *
 * Covers all static factory methods (success, error), getter/setter,
 * generic type behavior, and edge cases.
 */
class ResultTest {

    // ==================== success() without data ====================

    @Nested
    @DisplayName("success() without data")
    class SuccessNoDataTests {

        @Test
        @DisplayName("should return code 200")
        void success_shouldReturnCode200() {
            Result<Void> result = Result.success();
            assertEquals(200, result.getCode());
        }

        @Test
        @DisplayName("should return default success message")
        void success_shouldReturnDefaultMessage() {
            Result<Void> result = Result.success();
            assertEquals("操作成功", result.getMessage());
        }

        @Test
        @DisplayName("should have null data")
        void success_shouldHaveNullData() {
            Result<Void> result = Result.success();
            assertNull(result.getData());
        }

        @Test
        @DisplayName("should return non-null result")
        void success_shouldReturnNonNull() {
            Result<Void> result = Result.success();
            assertNotNull(result);
        }
    }

    // ==================== success(T data) ====================

    @Nested
    @DisplayName("success(T data)")
    class SuccessWithDataTests {

        @Test
        @DisplayName("should return code 200 with String data")
        void successWithString_shouldReturnCode200() {
            Result<String> result = Result.success("hello");
            assertEquals(200, result.getCode());
            assertEquals("hello", result.getData());
        }

        @Test
        @DisplayName("should return default success message with data")
        void successWithData_shouldReturnDefaultMessage() {
            Result<String> result = Result.success("test");
            assertEquals("操作成功", result.getMessage());
        }

        @Test
        @DisplayName("should work with Integer data")
        void successWithInteger_shouldWork() {
            Result<Integer> result = Result.success(42);
            assertEquals(42, result.getData());
            assertEquals(200, result.getCode());
        }

        @Test
        @DisplayName("should work with List data")
        void successWithList_shouldWork() {
            List<String> data = Arrays.asList("a", "b", "c");
            Result<List<String>> result = Result.success(data);
            assertEquals(data, result.getData());
            assertEquals(3, result.getData().size());
        }

        @Test
        @DisplayName("should work with Map data")
        void successWithMap_shouldWork() {
            Map<String, Integer> data = new HashMap<>();
            data.put("count", 10);
            Result<Map<String, Integer>> result = Result.success(data);
            assertEquals(10, result.getData().get("count"));
        }

        @Test
        @DisplayName("should work with null data")
        void successWithNullData_shouldWork() {
            Result<String> result = Result.success((String) null);
            assertEquals(200, result.getCode());
            assertNull(result.getData());
        }
    }

    // ==================== success(String message, T data) ====================

    @Nested
    @DisplayName("success(String message, T data)")
    class SuccessWithMessageAndDataTests {

        @Test
        @DisplayName("should use custom message")
        void successWithCustomMessage_shouldUseCustomMessage() {
            Result<String> result = Result.success("自定义消息", "data");
            assertEquals("自定义消息", result.getMessage());
            assertEquals("data", result.getData());
        }

        @Test
        @DisplayName("should return code 200")
        void successWithCustomMessage_shouldReturnCode200() {
            Result<String> result = Result.success("ok", "data");
            assertEquals(200, result.getCode());
        }

        @Test
        @DisplayName("should work with null message")
        void successWithNullMessage_shouldWork() {
            Result<String> result = Result.success(null, "data");
            assertNull(result.getMessage());
            assertEquals("data", result.getData());
        }

        @Test
        @DisplayName("should work with empty message")
        void successWithEmptyMessage_shouldWork() {
            Result<String> result = Result.success("", "data");
            assertEquals("", result.getMessage());
        }
    }

    // ==================== error(String message) ====================

    @Nested
    @DisplayName("error(String message)")
    class ErrorWithMessageTests {

        @Test
        @DisplayName("should return code 500")
        void error_shouldReturnCode500() {
            Result<Void> result = Result.error("服务异常");
            assertEquals(500, result.getCode());
        }

        @Test
        @DisplayName("should return the provided error message")
        void error_shouldReturnProvidedMessage() {
            Result<Void> result = Result.error("服务异常，请稍后重试");
            assertEquals("服务异常，请稍后重试", result.getMessage());
        }

        @Test
        @DisplayName("should have null data")
        void error_shouldHaveNullData() {
            Result<Void> result = Result.error("error");
            assertNull(result.getData());
        }

        @Test
        @DisplayName("should work with null message")
        void error_withNullMessage_shouldWork() {
            Result<Void> result = Result.error((String) null);
            assertEquals(500, result.getCode());
            assertNull(result.getMessage());
        }
    }

    // ==================== error(Integer code, String message) ====================

    @Nested
    @DisplayName("error(Integer code, String message)")
    class ErrorWithCodeAndMessageTests {

        @Test
        @DisplayName("should use custom error code 400")
        void error_withCode400_shouldReturnCode400() {
            Result<Void> result = Result.error(400, "参数校验失败");
            assertEquals(400, result.getCode());
            assertEquals("参数校验失败", result.getMessage());
        }

        @Test
        @DisplayName("should use custom error code 401")
        void error_withCode401_shouldReturnCode401() {
            Result<Void> result = Result.error(401, "未登录");
            assertEquals(401, result.getCode());
            assertEquals("未登录", result.getMessage());
        }

        @Test
        @DisplayName("should use custom error code 403")
        void error_withCode403_shouldReturnCode403() {
            Result<Void> result = Result.error(403, "权限不足");
            assertEquals(403, result.getCode());
        }

        @Test
        @DisplayName("should use custom error code 404")
        void error_withCode404_shouldReturnCode404() {
            Result<Void> result = Result.error(404, "资源不存在");
            assertEquals(404, result.getCode());
        }

        @Test
        @DisplayName("should have null data")
        void error_withCodeAndMessage_shouldHaveNullData() {
            Result<Void> result = Result.error(500, "error");
            assertNull(result.getData());
        }
    }

    // ==================== Getter/Setter ====================

    @Nested
    @DisplayName("Getter/Setter tests")
    class GetterSetterTests {

        @Test
        @DisplayName("setCode/getCode should work correctly")
        void setGetCode_shouldWork() {
            Result<String> result = new Result<>();
            result.setCode(201);
            assertEquals(201, result.getCode());
        }

        @Test
        @DisplayName("setMessage/getMessage should work correctly")
        void setGetMessage_shouldWork() {
            Result<String> result = new Result<>();
            result.setMessage("test message");
            assertEquals("test message", result.getMessage());
        }

        @Test
        @DisplayName("setData/getData should work correctly")
        void setGetData_shouldWork() {
            Result<String> result = new Result<>();
            result.setData("some data");
            assertEquals("some data", result.getData());
        }

        @Test
        @DisplayName("newly created Result should have all null fields")
        void newResult_shouldHaveAllNullFields() {
            Result<String> result = new Result<>();
            assertNull(result.getCode());
            assertNull(result.getMessage());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("setCode with null should set null")
        void setCode_withNull_shouldSetNull() {
            Result<String> result = Result.success("data");
            result.setCode(null);
            assertNull(result.getCode());
        }
    }

    // ==================== Generic Type Tests ====================

    @Nested
    @DisplayName("Generic type tests")
    class GenericTypeTests {

        @Test
        @DisplayName("should work with custom object")
        void shouldWorkWithCustomObject() {
            UserDTO user = new UserDTO();
            user.setId(1L);
            user.setUsername("testuser");

            Result<UserDTO> result = Result.success(user);

            assertEquals(200, result.getCode());
            assertNotNull(result.getData());
            assertEquals(1L, result.getData().getId());
            assertEquals("testuser", result.getData().getUsername());
        }

        @Test
        @DisplayName("should work with nested generics")
        void shouldWorkWithNestedGenerics() {
            List<Map<String, Object>> data = Arrays.asList(
                    Map.of("key", "value"),
                    Map.of("count", 5)
            );
            Result<List<Map<String, Object>>> result = Result.success(data);

            assertEquals(200, result.getCode());
            assertEquals(2, result.getData().size());
        }

        @Test
        @DisplayName("should work with Boolean data")
        void shouldWorkWithBoolean() {
            Result<Boolean> result = Result.success(true);
            assertTrue(result.getData());
        }

        @Test
        @DisplayName("should work with Long data")
        void shouldWorkWithLong() {
            Result<Long> result = Result.success(999999999L);
            assertEquals(999999999L, result.getData());
        }
    }
}
