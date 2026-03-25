package com.english.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tencent Hunyuan model configuration (hosted on SiliconFlow, OpenAI-compatible API)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tencent.hunyuan")
public class HunyuanConfig {

    private String apiUrl;
    private String apiKey;
    private String model;
}
