package com.english.exception;

import com.english.dto.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityExceptionHandler.
 *
 * Verifies AuthenticationEntryPoint (401) and AccessDeniedHandler (403)
 * behavior, JSON response format, and security headers.
 */
class SecurityExceptionHandlerTest {

    private SecurityExceptionHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new SecurityExceptionHandler();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    // ==================== Interface Implementation ====================

    @Test
    @DisplayName("should implement AuthenticationEntryPoint")
    void shouldImplementAuthenticationEntryPoint() {
        assertInstanceOf(AuthenticationEntryPoint.class, handler);
    }

    @Test
    @DisplayName("should implement AccessDeniedHandler")
    void shouldImplementAccessDeniedHandler() {
        assertInstanceOf(AccessDeniedHandler.class, handler);
    }

    // ==================== commence (401 Unauthorized) ====================

    @Nested
    @DisplayName("commence (401 Unauthorized) tests")
    class CommenceTests {

        @Test
        @DisplayName("should set HTTP status to 401")
        void commence_shouldSetStatus401() throws IOException {
            request.setRequestURI("/api/student/words");
            AuthenticationException authEx = new TestAuthenticationException("Token expired");

            handler.commence(request, response, authEx);

            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        }

        @Test
        @DisplayName("should set Content-Type to application/json")
        void commence_shouldSetContentTypeJson() throws IOException {
            request.setRequestURI("/api/student/words");
            AuthenticationException authEx = new TestAuthenticationException("Bad credentials");

            handler.commence(request, response, authEx);

            assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE),
                    "Content-Type should start with application/json");
        }

        @Test
        @DisplayName("should set UTF-8 character encoding")
        void commence_shouldSetUtf8Encoding() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("No token");

            handler.commence(request, response, authEx);

            assertEquals("UTF-8", response.getCharacterEncoding());
        }

        @Test
        @DisplayName("should set X-Content-Type-Options nosniff header")
        void commence_shouldSetNosniffHeader() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("Missing auth");

            handler.commence(request, response, authEx);

            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        }

        @Test
        @DisplayName("should return Result JSON with code 401")
        void commence_shouldReturnResultJsonWithCode401() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("Unauthenticated");

            handler.commence(request, response, authEx);

            String responseBody = response.getContentAsString();
            Result<?> result = objectMapper.readValue(responseBody, Result.class);
            assertEquals(401, result.getCode());
        }

        @Test
        @DisplayName("should return Chinese login message")
        void commence_shouldReturnChineseLoginMessage() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("Unauthenticated");

            handler.commence(request, response, authEx);

            String responseBody = response.getContentAsString();
            Result<?> result = objectMapper.readValue(responseBody, Result.class);
            assertEquals("未登录或登录已过期，请重新登录", result.getMessage());
        }

        @Test
        @DisplayName("should NOT expose original exception message in response")
        void commence_shouldNotExposeOriginalMessage() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("Internal JWT parsing error at line 42");

            handler.commence(request, response, authEx);

            String responseBody = response.getContentAsString();
            assertFalse(responseBody.contains("Internal JWT parsing error"),
                    "Should not expose internal exception details");
        }

        @Test
        @DisplayName("should produce valid JSON response")
        void commence_shouldProduceValidJson() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("test");

            handler.commence(request, response, authEx);

            String responseBody = response.getContentAsString();
            assertDoesNotThrow(() -> objectMapper.readTree(responseBody),
                    "Response should be valid JSON");
        }
    }

    // ==================== handle (403 Forbidden) ====================

    @Nested
    @DisplayName("handle (403 Forbidden) tests")
    class HandleTests {

        @Test
        @DisplayName("should set HTTP status to 403")
        void handle_shouldSetStatus403() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("Access denied");

            handler.handle(request, response, accessEx);

            assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        }

        @Test
        @DisplayName("should set Content-Type to application/json")
        void handle_shouldSetContentTypeJson() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("Forbidden");

            handler.handle(request, response, accessEx);

            assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE),
                    "Content-Type should start with application/json");
        }

        @Test
        @DisplayName("should set UTF-8 character encoding")
        void handle_shouldSetUtf8Encoding() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("Forbidden");

            handler.handle(request, response, accessEx);

            assertEquals("UTF-8", response.getCharacterEncoding());
        }

        @Test
        @DisplayName("should set X-Content-Type-Options nosniff header")
        void handle_shouldSetNosniffHeader() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("Forbidden");

            handler.handle(request, response, accessEx);

            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        }

        @Test
        @DisplayName("should return Result JSON with code 403")
        void handle_shouldReturnResultJsonWithCode403() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("Access denied");

            handler.handle(request, response, accessEx);

            String responseBody = response.getContentAsString();
            Result<?> result = objectMapper.readValue(responseBody, Result.class);
            assertEquals(403, result.getCode());
        }

        @Test
        @DisplayName("should return Chinese permission denied message")
        void handle_shouldReturnChinesePermissionMessage() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("Access denied");

            handler.handle(request, response, accessEx);

            String responseBody = response.getContentAsString();
            Result<?> result = objectMapper.readValue(responseBody, Result.class);
            assertEquals("权限不足，无法执行该操作", result.getMessage());
        }

        @Test
        @DisplayName("should NOT expose original AccessDeniedException message")
        void handle_shouldNotExposeOriginalMessage() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("User role=USER attempted to access admin resource");

            handler.handle(request, response, accessEx);

            String responseBody = response.getContentAsString();
            assertFalse(responseBody.contains("User role=USER"),
                    "Should not expose original exception details");
        }

        @Test
        @DisplayName("should produce valid JSON response")
        void handle_shouldProduceValidJson() throws IOException {
            request.setRequestURI("/api/admin/users");
            AccessDeniedException accessEx = new AccessDeniedException("test");

            handler.handle(request, response, accessEx);

            String responseBody = response.getContentAsString();
            assertDoesNotThrow(() -> objectMapper.readTree(responseBody),
                    "Response should be valid JSON");
        }
    }

    // ==================== Response Structure Tests ====================

    @Nested
    @DisplayName("Response structure tests")
    class ResponseStructureTests {

        @Test
        @DisplayName("401 response should have code and message fields")
        void unauthorizedResponse_shouldHaveCodeAndMessage() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("test");

            handler.commence(request, response, authEx);

            String responseBody = response.getContentAsString();
            var jsonNode = objectMapper.readTree(responseBody);
            assertTrue(jsonNode.has("code"), "Should have 'code' field");
            assertTrue(jsonNode.has("message"), "Should have 'message' field");
        }

        @Test
        @DisplayName("403 response should have code and message fields")
        void forbiddenResponse_shouldHaveCodeAndMessage() throws IOException {
            request.setRequestURI("/api/test");
            AccessDeniedException accessEx = new AccessDeniedException("test");

            handler.handle(request, response, accessEx);

            String responseBody = response.getContentAsString();
            var jsonNode = objectMapper.readTree(responseBody);
            assertTrue(jsonNode.has("code"), "Should have 'code' field");
            assertTrue(jsonNode.has("message"), "Should have 'message' field");
        }

        @Test
        @DisplayName("401 response should NOT have data field (null excluded by @JsonInclude)")
        void unauthorizedResponse_shouldNotHaveDataField() throws IOException {
            request.setRequestURI("/api/test");
            AuthenticationException authEx = new TestAuthenticationException("test");

            handler.commence(request, response, authEx);

            String responseBody = response.getContentAsString();
            var jsonNode = objectMapper.readTree(responseBody);
            assertFalse(jsonNode.has("data"),
                    "data field should be excluded when null (due to @JsonInclude.NON_NULL)");
        }

        @Test
        @DisplayName("403 response should NOT have data field (null excluded by @JsonInclude)")
        void forbiddenResponse_shouldNotHaveDataField() throws IOException {
            request.setRequestURI("/api/test");
            AccessDeniedException accessEx = new AccessDeniedException("test");

            handler.handle(request, response, accessEx);

            String responseBody = response.getContentAsString();
            var jsonNode = objectMapper.readTree(responseBody);
            assertFalse(jsonNode.has("data"),
                    "data field should be excluded when null (due to @JsonInclude.NON_NULL)");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge case tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("commence with various request URIs should work")
        void commence_withVariousUris_shouldWork() throws IOException {
            String[] uris = {"/api/test", "/api/student/words", "/", "/api/ops/backup"};
            for (String uri : uris) {
                MockHttpServletResponse resp = new MockHttpServletResponse();
                request.setRequestURI(uri);

                handler.commence(request, resp, new TestAuthenticationException("test"));

                assertEquals(401, resp.getStatus(), "Should return 401 for URI: " + uri);
            }
        }

        @Test
        @DisplayName("handle with various request URIs should work")
        void handle_withVariousUris_shouldWork() throws IOException {
            String[] uris = {"/api/admin/users", "/api/student/stats", "/api/test"};
            for (String uri : uris) {
                MockHttpServletResponse resp = new MockHttpServletResponse();
                request.setRequestURI(uri);

                handler.handle(request, resp, new AccessDeniedException("denied"));

                assertEquals(403, resp.getStatus(), "Should return 403 for URI: " + uri);
            }
        }
    }

    // ==================== Test Helper ====================

    /**
     * Concrete implementation of AuthenticationException for testing.
     * AuthenticationException is abstract, so we need a concrete subclass.
     */
    private static class TestAuthenticationException extends AuthenticationException {
        TestAuthenticationException(String msg) {
            super(msg);
        }
    }
}
