package com.english.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置
 * 配置静态资源映射
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 使用 URI 构建路径，确保跨平台兼容（Windows 反斜杠 / Linux 正斜杠均正确处理）
        String audioPath = Paths.get("").toAbsolutePath().getParent()
                .resolve("book1").resolve("audio").toUri().toString();
        // Spring ResourceHandler 要求目录路径以 '/' 结尾
        if (!audioPath.endsWith("/")) {
            audioPath += "/";
        }

        registry.addResourceHandler("/audio/vocabulary/**")
                .addResourceLocations(audioPath)
                .setCachePeriod(3600); // 缓存1小时
    }
}
