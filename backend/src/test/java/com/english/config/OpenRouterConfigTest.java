package com.english.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenRouterConfig.
 *
 * Verifies getter/setter for all fields (apiKey, apiUrl, model, timeout, maxRetries),
 * default values, and annotation presence.
 */
class OpenRouterConfigTest {

    private OpenRouterConfig config;

    @BeforeEach
    void setUp() {
        config = new OpenRouterConfig();
    }

    // ==================== Default Values ====================

    @Nested
    @DisplayName("Default value tests")
    class DefaultValueTests {

        @Test
        @DisplayName("apiKey should be null by default")
        void apiKey_shouldBeNullByDefault() {
            assertNull(config.getApiKey(), "apiKey should be null by default");
        }

        @Test
        @DisplayName("apiUrl should be null by default")
        void apiUrl_shouldBeNullByDefault() {
            assertNull(config.getApiUrl(), "apiUrl should be null by default");
        }

        @Test
        @DisplayName("model should be null by default")
        void model_shouldBeNullByDefault() {
            assertNull(config.getModel(), "model should be null by default");
        }

        @Test
        @DisplayName("timeout should default to 60000ms")
        void timeout_shouldDefaultTo60000() {
            assertEquals(60000, config.getTimeout(), "timeout should default to 60000ms (1 minute)");
        }

        @Test
        @DisplayName("maxRetries should default to 3")
        void maxRetries_shouldDefaultTo3() {
            assertEquals(3, config.getMaxRetries(), "maxRetries should default to 3");
        }
    }

    // ==================== Getter/Setter: apiKey ====================

    @Nested
    @DisplayName("apiKey getter/setter tests")
    class ApiKeyTests {

        @Test
        @DisplayName("setApiKey/getApiKey should work correctly")
        void setGetApiKey_shouldWorkCorrectly() {
            config.setApiKey("sk-or-v1-test-key-123");
            assertEquals("sk-or-v1-test-key-123", config.getApiKey());
        }

        @Test
        @DisplayName("setApiKey with null should set null")
        void setApiKey_withNull_shouldSetNull() {
            config.setApiKey("some-key");
            config.setApiKey(null);
            assertNull(config.getApiKey());
        }

        @Test
        @DisplayName("setApiKey with empty string should set empty")
        void setApiKey_withEmpty_shouldSetEmpty() {
            config.setApiKey("");
            assertEquals("", config.getApiKey());
        }
    }

    // ==================== Getter/Setter: apiUrl ====================

    @Nested
    @DisplayName("apiUrl getter/setter tests")
    class ApiUrlTests {

        @Test
        @DisplayName("setApiUrl/getApiUrl should work correctly")
        void setGetApiUrl_shouldWorkCorrectly() {
            config.setApiUrl("https://openrouter.ai/api/v1/chat/completions");
            assertEquals("https://openrouter.ai/api/v1/chat/completions", config.getApiUrl());
        }

        @Test
        @DisplayName("setApiUrl with null should set null")
        void setApiUrl_withNull_shouldSetNull() {
            config.setApiUrl("https://example.com");
            config.setApiUrl(null);
            assertNull(config.getApiUrl());
        }
    }

    // ==================== Getter/Setter: model ====================

    @Nested
    @DisplayName("model getter/setter tests")
    class ModelTests {

        @Test
        @DisplayName("setModel/getModel should work correctly")
        void setGetModel_shouldWorkCorrectly() {
            config.setModel("tngtech/deepseek-r1t2-chimera:free");
            assertEquals("tngtech/deepseek-r1t2-chimera:free", config.getModel());
        }

        @Test
        @DisplayName("setModel with different model names")
        void setModel_withDifferentModels() {
            String[] models = {
                    "openai/gpt-4",
                    "anthropic/claude-3-opus",
                    "meta-llama/llama-3-70b",
                    "tngtech/deepseek-r1t2-chimera:free"
            };

            for (String model : models) {
                config.setModel(model);
                assertEquals(model, config.getModel());
            }
        }

        @Test
        @DisplayName("setModel with null should set null")
        void setModel_withNull_shouldSetNull() {
            config.setModel("some-model");
            config.setModel(null);
            assertNull(config.getModel());
        }
    }

    // ==================== Getter/Setter: timeout ====================

    @Nested
    @DisplayName("timeout getter/setter tests")
    class TimeoutTests {

        @Test
        @DisplayName("setTimeout/getTimeout should work correctly")
        void setGetTimeout_shouldWorkCorrectly() {
            config.setTimeout(30000);
            assertEquals(30000, config.getTimeout());
        }

        @Test
        @DisplayName("setTimeout with zero should set zero")
        void setTimeout_withZero_shouldSetZero() {
            config.setTimeout(0);
            assertEquals(0, config.getTimeout());
        }

        @Test
        @DisplayName("setTimeout with large value should work")
        void setTimeout_withLargeValue_shouldWork() {
            config.setTimeout(300000);
            assertEquals(300000, config.getTimeout());
        }

        @Test
        @DisplayName("setTimeout with null should set null")
        void setTimeout_withNull_shouldSetNull() {
            config.setTimeout(null);
            assertNull(config.getTimeout());
        }
    }

    // ==================== Getter/Setter: maxRetries ====================

    @Nested
    @DisplayName("maxRetries getter/setter tests")
    class MaxRetriesTests {

        @Test
        @DisplayName("setMaxRetries/getMaxRetries should work correctly")
        void setGetMaxRetries_shouldWorkCorrectly() {
            config.setMaxRetries(5);
            assertEquals(5, config.getMaxRetries());
        }

        @Test
        @DisplayName("setMaxRetries with zero should set zero")
        void setMaxRetries_withZero_shouldSetZero() {
            config.setMaxRetries(0);
            assertEquals(0, config.getMaxRetries());
        }

        @Test
        @DisplayName("setMaxRetries with null should set null")
        void setMaxRetries_withNull_shouldSetNull() {
            config.setMaxRetries(null);
            assertNull(config.getMaxRetries());
        }
    }

    // ==================== Annotation Tests ====================

    @Nested
    @DisplayName("Annotation tests")
    class AnnotationTests {

        @Test
        @DisplayName("should be annotated with @Configuration")
        void shouldHaveConfigurationAnnotation() {
            assertNotNull(OpenRouterConfig.class.getAnnotation(
                    org.springframework.context.annotation.Configuration.class),
                    "Class should be annotated with @Configuration");
        }

        @Test
        @DisplayName("should be annotated with @ConfigurationProperties")
        void shouldHaveConfigurationPropertiesAnnotation() {
            org.springframework.boot.context.properties.ConfigurationProperties props =
                    OpenRouterConfig.class.getAnnotation(
                            org.springframework.boot.context.properties.ConfigurationProperties.class);

            assertNotNull(props, "Class should be annotated with @ConfigurationProperties");
            assertEquals("openrouter", props.prefix(),
                    "prefix should be 'openrouter'");
        }
    }

    // ==================== Lombok @Data Tests ====================

    @Nested
    @DisplayName("Lombok @Data generated methods")
    class LombokDataTests {

        @Test
        @DisplayName("toString should contain all field names")
        void toString_shouldContainFieldNames() {
            config.setApiKey("test-key");
            config.setApiUrl("https://example.com");
            config.setModel("test-model");

            String toString = config.toString();

            assertTrue(toString.contains("apiKey"), "toString should contain 'apiKey'");
            assertTrue(toString.contains("apiUrl"), "toString should contain 'apiUrl'");
            assertTrue(toString.contains("model"), "toString should contain 'model'");
            assertTrue(toString.contains("timeout"), "toString should contain 'timeout'");
            assertTrue(toString.contains("maxRetries"), "toString should contain 'maxRetries'");
        }

        @Test
        @DisplayName("equals should return true for same field values")
        void equals_shouldReturnTrueForSameValues() {
            OpenRouterConfig config1 = new OpenRouterConfig();
            config1.setApiKey("key");
            config1.setApiUrl("url");
            config1.setModel("model");
            config1.setTimeout(1000);
            config1.setMaxRetries(2);

            OpenRouterConfig config2 = new OpenRouterConfig();
            config2.setApiKey("key");
            config2.setApiUrl("url");
            config2.setModel("model");
            config2.setTimeout(1000);
            config2.setMaxRetries(2);

            assertEquals(config1, config2, "Objects with same field values should be equal");
        }

        @Test
        @DisplayName("equals should return false for different field values")
        void equals_shouldReturnFalseForDifferentValues() {
            OpenRouterConfig config1 = new OpenRouterConfig();
            config1.setApiKey("key1");

            OpenRouterConfig config2 = new OpenRouterConfig();
            config2.setApiKey("key2");

            assertNotEquals(config1, config2, "Objects with different field values should not be equal");
        }

        @Test
        @DisplayName("hashCode should be equal for equal objects")
        void hashCode_shouldBeEqualForEqualObjects() {
            OpenRouterConfig config1 = new OpenRouterConfig();
            config1.setApiKey("key");
            config1.setModel("model");

            OpenRouterConfig config2 = new OpenRouterConfig();
            config2.setApiKey("key");
            config2.setModel("model");

            assertEquals(config1.hashCode(), config2.hashCode(),
                    "Equal objects should have equal hashCodes");
        }

        @Test
        @DisplayName("hashCode should be different for different objects (usually)")
        void hashCode_shouldDifferForDifferentObjects() {
            OpenRouterConfig config1 = new OpenRouterConfig();
            config1.setApiKey("key1");

            OpenRouterConfig config2 = new OpenRouterConfig();
            config2.setApiKey("key2");

            // Not guaranteed but highly likely for different data
            assertNotEquals(config1.hashCode(), config2.hashCode());
        }
    }
}
