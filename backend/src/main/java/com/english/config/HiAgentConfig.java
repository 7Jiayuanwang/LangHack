package com.english.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * HiAgent API configuration
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hiagent")
public class HiAgentConfig {

    /**
     * HiAgent API key
     */
    private String apiKey;

    /**
     * HiAgent API URL (Base URL)
     */
    private String apiUrl;

    /**
     * HiAgent App ID
     */
    private String appId;

    /**
     * User ID for HiAgent API
     */
    private String userId;

    /**
     * Calling mode: native (HiAgent) or openai (Standard)
     */
    private String mode;

    /**
     * Request timeout in milliseconds
     */
    private Integer timeout = 60000;

    /**
     * Maximum retry attempts
     */
    private Integer maxRetries = 3;
}
