package com.english.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenRouter API configuration
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterConfig {

    /**
     * OpenRouter API key
     */
    private String apiKey;

    /**
     * OpenRouter API URL
     */
    private String apiUrl;

    /**
     * Model name (e.g., tngtech/deepseek-r1t2-chimera:free)
     */
    private String model;

    /**
     * Request timeout in milliseconds
     */
    private Integer timeout = 60000;

    /**
     * Maximum retry attempts
     */
    private Integer maxRetries = 3;
}
