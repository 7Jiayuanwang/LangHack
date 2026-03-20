package com.english.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenRouter API configuration (Fallback AI Service)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterConfig {

    private String apiKey;
    private String apiUrl;
    private String model;
    private String mode;
    private Integer timeout = 60000;
    private Integer maxRetries = 3;
}
