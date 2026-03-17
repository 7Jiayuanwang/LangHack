package com.english.security;

import com.english.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityConfig.
 *
 * Tests CORS configuration and public bean creation without requiring
 * a full Spring context (no @SpringBootTest — avoids DB/Redis dependencies).
 */
class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() throws Exception {
        securityConfig = new SecurityConfig();
    }

    // ========== CORS Configuration Tests ==========

    @Test
    @DisplayName("CORS: default allowed origins include localhost:3000 and localhost:5173")
    void corsDefaultOrigins() throws Exception {
        setField(securityConfig, "allowedOriginsConfig", "http://localhost:3000,http://localhost:5173");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source, "CorsConfigurationSource should not be null");

        // Test against a registered path
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/auth/login");
        request.setRequestURI("/api/auth/login");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config, "CORS config for /api/auth/** should not be null");
        List<String> origins = config.getAllowedOrigins();
        assertNotNull(origins);
        assertTrue(origins.contains("http://localhost:3000"), "Should allow localhost:3000");
        assertTrue(origins.contains("http://localhost:5173"), "Should allow localhost:5173");
    }

    @Test
    @DisplayName("CORS: custom origins from configuration property")
    void corsCustomOrigins() throws Exception {
        setField(securityConfig, "allowedOriginsConfig", "https://example.com,https://app.example.com");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/student/profile");
        request.setRequestURI("/api/student/profile");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        List<String> origins = config.getAllowedOrigins();
        assertTrue(origins.contains("https://example.com"));
        assertTrue(origins.contains("https://app.example.com"));
        assertFalse(origins.contains("http://localhost:3000"), "Default origins should not appear when custom ones are set");
    }

    @Test
    @DisplayName("CORS: empty config string falls back to default origins")
    void corsEmptyConfigFallback() throws Exception {
        // Setting empty/whitespace-only value should trigger the fallback
        setField(securityConfig, "allowedOriginsConfig", "  ,  ");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/auth/register");
        request.setRequestURI("/api/auth/register");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        List<String> origins = config.getAllowedOrigins();
        assertTrue(origins.contains("http://localhost:3000"), "Fallback should include localhost:3000");
        assertTrue(origins.contains("http://localhost:5173"), "Fallback should include localhost:5173");
    }

    @Test
    @DisplayName("CORS: allowed methods include GET, POST, PUT, DELETE, OPTIONS")
    void corsAllowedMethods() throws Exception {
        setField(securityConfig, "allowedOriginsConfig", "http://localhost:3000");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/student/words");
        request.setRequestURI("/api/student/words");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        List<String> methods = config.getAllowedMethods();
        assertNotNull(methods);
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("POST"));
        assertTrue(methods.contains("PUT"));
        assertTrue(methods.contains("DELETE"));
        assertTrue(methods.contains("OPTIONS"));
    }

    @Test
    @DisplayName("CORS: allowed headers include Authorization and Content-Type")
    void corsAllowedHeaders() throws Exception {
        setField(securityConfig, "allowedOriginsConfig", "http://localhost:3000");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/user/info");
        request.setRequestURI("/api/user/info");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        List<String> headers = config.getAllowedHeaders();
        assertNotNull(headers);
        assertTrue(headers.contains("Authorization"), "Authorization header must be allowed for JWT");
        assertTrue(headers.contains("Content-Type"), "Content-Type header must be allowed");
        assertTrue(headers.contains("Accept"));
        assertTrue(headers.contains("X-Requested-With"));
    }

    @Test
    @DisplayName("CORS: credentials are not allowed (stateless JWT)")
    void corsCredentialsDisabled() throws Exception {
        setField(securityConfig, "allowedOriginsConfig", "http://localhost:3000");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/auth/login");
        request.setRequestURI("/api/auth/login");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertFalse(config.getAllowCredentials(), "Credentials should be disabled for stateless JWT API");
    }

    @Test
    @DisplayName("CORS: preflight cache max age is 3600 seconds")
    void corsMaxAge() throws Exception {
        setField(securityConfig, "allowedOriginsConfig", "http://localhost:3000");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/translate/text");
        request.setRequestURI("/api/translate/text");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertEquals(3600L, config.getMaxAge(), "Preflight cache should be 1 hour");
    }

    @Test
    @DisplayName("CORS: registered paths cover all API route groups")
    void corsRegisteredPaths() throws Exception {
        setField(securityConfig, "allowedOriginsConfig", "http://localhost:3000");
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        // These paths should all have CORS config
        String[] registeredPaths = {
            "/api/auth/login",
            "/api/public/info",
            "/api/wordnet/lookup",
            "/api/student/profile",
            "/api/user/settings",
            "/api/teacher/classes",
            "/api/media/image.jpg",
            "/api/translate/text",
            "/api/asr/recognize",
            "/api/bilibili/video",
            "/api/admin/users"
        };

        for (String path : registeredPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setServletPath(path);
            request.setRequestURI(path);
            CorsConfiguration config = source.getCorsConfiguration(request);
            assertNotNull(config, "CORS config should be registered for path: " + path);
        }
    }

    // ========== Bean Configuration Tests ==========

    @Test
    @DisplayName("PasswordEncoder bean returns BCryptPasswordEncoder")
    void passwordEncoderIsBCrypt() {
        var encoder = securityConfig.passwordEncoder();
        assertNotNull(encoder);
        assertTrue(encoder instanceof org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder,
            "PasswordEncoder should be BCryptPasswordEncoder");

        // Verify it actually works
        String raw = "testPassword123";
        String encoded = encoder.encode(raw);
        assertTrue(encoder.matches(raw, encoded), "Encoded password should match raw password");
        assertFalse(encoder.matches("wrongPassword", encoded), "Wrong password should not match");
    }

    // ========== Utility ==========

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
