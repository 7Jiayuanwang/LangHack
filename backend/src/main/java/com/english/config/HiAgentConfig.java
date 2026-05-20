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
     * Q&A App API key (for article highlight AI Q&A)
     */
    private String qaApiKey;

    /**
     * Q&A App ID (for article highlight AI Q&A)
     */
    private String qaAppId;

    /**
     * Quiz App API key (for AI quiz generation)
     */
    private String quizApiKey;

    /**
     * Quiz App ID (for AI quiz generation)
     */
    private String quizAppId;

    /**
     * Request timeout in milliseconds
     */
    private Integer timeout = 60000;

    /**
     * Max retries for API calls
     */
    private int maxRetries = 3;
}
