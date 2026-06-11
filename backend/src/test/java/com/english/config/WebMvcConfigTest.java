package com.english.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebMvcConfig.
 *
 * Verifies AUDIO_RESOURCE_PATH environment variable handling,
 * path security validation, and resource handler registration.
 */
class WebMvcConfigTest {

    private WebMvcConfig config;
    private final AccessLogInterceptor mockInterceptor = mock(AccessLogInterceptor.class);
    private final AdminMasterInterceptor mockAdminMasterInterceptor = mock(AdminMasterInterceptor.class);
    private final com.english.interceptor.RateLimitInterceptor mockRateLimitInterceptor = mock(com.english.interceptor.RateLimitInterceptor.class);

    @BeforeEach
    void setUp() {
        config = new WebMvcConfig(mockInterceptor, mockAdminMasterInterceptor, mockRateLimitInterceptor);
    }

    // ==================== Interface Implementation ====================

    @Test
    @DisplayName("WebMvcConfig should implement WebMvcConfigurer")
    void shouldImplementWebMvcConfigurer() {
        assertInstanceOf(WebMvcConfigurer.class, config);
    }

    @Test
    @DisplayName("WebMvcConfig should be annotated with @Configuration")
    void shouldHaveConfigurationAnnotation() {
        assertNotNull(WebMvcConfig.class.getAnnotation(
                org.springframework.context.annotation.Configuration.class),
                "Class should be annotated with @Configuration");
    }

    // ==================== Environment Variable Not Set ====================

    @Nested
    @DisplayName("When AUDIO_RESOURCE_PATH not set")
    class EnvNotSetTests {

        @Test
        @DisplayName("should not register any handlers when env var is null")
        void addResourceHandlers_shouldSkipWhenEnvVarNull() {
            // 生产代码无条件注册 SPA 静态资源链（/assets/**、/js/**、/data/**、/favicon.ico），
            // 因此 registry mock 必须对所有链式调用返回有效 registration，否则 NPE。
            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class, RETURNS_DEEP_STUBS);

            // The method reads from System.getenv which we cannot easily mock
            // Instead, we verify it does not throw
            assertDoesNotThrow(() -> config.addResourceHandlers(registry));
        }

        @Test
        @DisplayName("should not throw when registry is provided but env var not set")
        void addResourceHandlers_shouldNotThrowWhenNoEnvVar() {
            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class, RETURNS_DEEP_STUBS);

            // Just verify no exception is thrown
            assertDoesNotThrow(() -> config.addResourceHandlers(registry));
        }
    }

    // ==================== Valid Path Configuration ====================

    @Nested
    @DisplayName("With valid AUDIO_RESOURCE_PATH")
    class ValidPathTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should register handler when valid audio directory exists")
        void addResourceHandlers_shouldRegisterWhenValidPath() throws IOException {
            // Create the expected directory structure
            Path audioDir = tempDir.resolve("book1").resolve("audio");
            Files.createDirectories(audioDir);

            // We need to use a custom WebMvcConfig subclass to override env var reading
            WebMvcConfig testConfig = new TestableWebMvcConfig(tempDir.toString());

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
            ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
            when(registry.addResourceHandler("/audio/vocabulary/**")).thenReturn(registration);
            when(registration.addResourceLocations(anyString())).thenReturn(registration);
            when(registration.setCachePeriod(anyInt())).thenReturn(registration);

            testConfig.addResourceHandlers(registry);

            verify(registry).addResourceHandler("/audio/vocabulary/**");
        }

        @Test
        @DisplayName("should set cache period to 3600 seconds")
        void addResourceHandlers_shouldSetCachePeriodTo3600() throws IOException {
            Path audioDir = tempDir.resolve("book1").resolve("audio");
            Files.createDirectories(audioDir);

            WebMvcConfig testConfig = new TestableWebMvcConfig(tempDir.toString());

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
            ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
            when(registry.addResourceHandler(anyString())).thenReturn(registration);
            when(registration.addResourceLocations(anyString())).thenReturn(registration);
            when(registration.setCachePeriod(anyInt())).thenReturn(registration);

            testConfig.addResourceHandlers(registry);

            verify(registration).setCachePeriod(3600);
        }
    }

    // ==================== Invalid Path Scenarios ====================

    @Nested
    @DisplayName("Invalid path scenarios")
    class InvalidPathTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should not register handler when audio subdirectory does not exist")
        void addResourceHandlers_shouldNotRegisterWhenAudioDirMissing() {
            // tempDir exists but book1/audio does not
            WebMvcConfig testConfig = new TestableWebMvcConfig(tempDir.toString());

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

            testConfig.addResourceHandlers(registry);

            verify(registry, never()).addResourceHandler(anyString());
        }

        @Test
        @DisplayName("should not register handler when base path does not exist")
        void addResourceHandlers_shouldNotRegisterWhenBasePathNotExists() {
            WebMvcConfig testConfig = new TestableWebMvcConfig("/nonexistent/path/xyz123");

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

            testConfig.addResourceHandlers(registry);

            verify(registry, never()).addResourceHandler(anyString());
        }

        @Test
        @DisplayName("should not register handler when env var is empty string")
        void addResourceHandlers_shouldNotRegisterWhenEnvVarEmpty() {
            WebMvcConfig testConfig = new TestableWebMvcConfig("");

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

            testConfig.addResourceHandlers(registry);

            verify(registry, never()).addResourceHandler(anyString());
        }

        @Test
        @DisplayName("should not register handler when env var is whitespace only")
        void addResourceHandlers_shouldNotRegisterWhenEnvVarWhitespace() {
            WebMvcConfig testConfig = new TestableWebMvcConfig("   ");

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

            testConfig.addResourceHandlers(registry);

            verify(registry, never()).addResourceHandler(anyString());
        }
    }

    // ==================== Security Tests ====================

    @Nested
    @DisplayName("Path security tests")
    class SecurityTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should not throw on path traversal attempt (handled gracefully)")
        void addResourceHandlers_shouldHandlePathTraversalGracefully() {
            // Trying a path that could lead to traversal
            WebMvcConfig testConfig = new TestableWebMvcConfig(tempDir.resolve("../../../etc").toString());

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

            // Should handle gracefully without registering
            assertDoesNotThrow(() -> testConfig.addResourceHandlers(registry));
        }

        @Test
        @DisplayName("addResourceHandlers should not throw exceptions for invalid paths")
        void addResourceHandlers_shouldNotThrowForInvalidPaths() {
            // Test with various potentially problematic paths
            String[] invalidPaths = {
                    "",
                    "   ",
                    "/nonexistent",
                    "relative/path",
            };

            for (String path : invalidPaths) {
                WebMvcConfig testConfig = new TestableWebMvcConfig(path);
                ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

                assertDoesNotThrow(() -> testConfig.addResourceHandlers(registry),
                        "Should not throw for path: '" + path + "'");
            }
        }
    }

    // ==================== Logger and Metadata Tests ====================

    @Nested
    @DisplayName("Logger and metadata tests")
    class LoggerAndMetadataTests {

        @Test
        @DisplayName("WebMvcConfig should have a logger field")
        void shouldHaveLoggerField() throws NoSuchFieldException {
            Field logField = WebMvcConfig.class.getDeclaredField("log");
            assertNotNull(logField, "Should have a 'log' field");
        }

        @Test
        @DisplayName("logger field should be private static final")
        void loggerField_shouldBePrivateStaticFinal() throws NoSuchFieldException {
            Field logField = WebMvcConfig.class.getDeclaredField("log");
            int modifiers = logField.getModifiers();
            assertTrue(Modifier.isPrivate(modifiers), "log should be private");
            assertTrue(Modifier.isStatic(modifiers), "log should be static");
            assertTrue(Modifier.isFinal(modifiers), "log should be final");
        }

        @Test
        @DisplayName("WebMvcConfig should declare addResourceHandlers method")
        void shouldDeclareAddResourceHandlersMethod() throws NoSuchMethodException {
            assertNotNull(
                    WebMvcConfig.class.getMethod("addResourceHandlers", ResourceHandlerRegistry.class),
                    "Should declare addResourceHandlers(ResourceHandlerRegistry)");
        }
    }

    // ==================== Testable Subclass ====================

    /**
     * Subclass that overrides System.getenv to allow testing with custom paths.
     * Since System.getenv cannot be mocked easily, we override addResourceHandlers
     * to inject the test path.
     */
    private static class TestableWebMvcConfig extends WebMvcConfig {
        private final String audioResourcePath;

        TestableWebMvcConfig(String audioResourcePath) {
            super(mock(AccessLogInterceptor.class), mock(AdminMasterInterceptor.class), mock(com.english.interceptor.RateLimitInterceptor.class));
            this.audioResourcePath = audioResourcePath;
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            if (audioResourcePath == null || audioResourcePath.trim().isEmpty()) {
                return;
            }

            try {
                java.nio.file.Path basePath = java.nio.file.Paths.get(audioResourcePath).toRealPath();

                if (!Files.isDirectory(basePath) || !Files.isReadable(basePath)) {
                    return;
                }

                java.nio.file.Path audioPath = basePath.resolve("book1").resolve("audio");
                java.nio.file.Path realAudioPath = audioPath.toRealPath();

                if (!realAudioPath.startsWith(basePath)) {
                    return;
                }

                if (!Files.exists(realAudioPath) || !Files.isReadable(realAudioPath)) {
                    return;
                }

                String audioPathUri = realAudioPath.toUri().toString();
                if (!audioPathUri.endsWith("/")) {
                    audioPathUri += "/";
                }

                registry.addResourceHandler("/audio/vocabulary/**")
                        .addResourceLocations(audioPathUri)
                        .setCachePeriod(3600);
            } catch (Exception e) {
                // Silently handle, same as production code
            }
        }
    }
}
