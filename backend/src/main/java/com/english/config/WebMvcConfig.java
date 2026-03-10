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
        java.nio.file.Path parentPath = Paths.get("").toAbsolutePath().getParent();
        if (parentPath == null) {
            // 降级处理：无法获取上级目录时，使用当前目录的上一层
            try {
                parentPath = Paths.get("..").toAbsolutePath();
                if (parentPath == null) {
                    // 最后的保险：使用系统临时目录作为fallback
                    parentPath = Paths.get(System.getProperty("java.io.tmpdir"));
                }
            } catch (Exception e) {
                // 如果仍然异常，不注册音频资源处理器
                return;
            }
        }
        String audioPath = parentPath.resolve("book1").resolve("audio").toUri().toString();
        // Spring ResourceHandler 要求目录路径以 '/' 结尾
        if (!audioPath.endsWith("/")) {
            audioPath += "/";
        }

        registry.addResourceHandler("/audio/vocabulary/**")
                .addResourceLocations(audioPath)
                .setCachePeriod(3600); // 缓存1小时
    }
}
