package com.english.sso.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * SSO RestTemplate 配置
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Configuration
@Profile("sso")
public class SsoRestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 秒连接超时
        factory.setReadTimeout(30000);    // 30 秒读取超时

        return new RestTemplate(factory);
    }
}
