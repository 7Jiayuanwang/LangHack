package com.english.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 * 配置静态资源映射
 * 
 * 安全说明：
 * - 目录白名单在环境变量 AUDIO_RESOURCE_PATH 中配置，确保只允许访问指定目录
 * - 禁止从系统临时目录或其他不受控制的位置服务文件
 * - 所有路径进行规范化和验证，防止 .. / 路径遍历
 * - 验证目录可读性，防止配置指向无法访问的位置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 从环境变量读取音频资源目录（必须配置，无默认值以避免误配置）
        String audioResourcePath = System.getenv("AUDIO_RESOURCE_PATH");
        
        if (audioResourcePath == null || audioResourcePath.trim().isEmpty()) {
            log.warn("AUDIO_RESOURCE_PATH environment variable not set. Audio resources will not be served.");
            return;
        }

        try {
            // 规范化路径并进行安全验证
            Path basePath = Paths.get(audioResourcePath).toRealPath();
            
            // 验证基础目录存在且可读
            if (!Files.isDirectory(basePath) || !Files.isReadable(basePath)) {
                log.error("Audio resource path does not exist or is not readable: {}", audioResourcePath);
                return;
            }

            // 防止路径遍历：确保 book1/audio 子目录在白名单目录内
            Path audioPath = basePath.resolve("book1").resolve("audio");
            
            // 规范化解析后的路径，防止符号链接或其他欺骗
            Path realAudioPath = audioPath.toRealPath();
            
            // 验证最终路径仍在基础路径下（防止 ../ 逃逸）
            if (!realAudioPath.startsWith(basePath)) {
                log.error("Audio resource path escaped base directory: {} is not under {}", 
                    realAudioPath, basePath);
                return;
            }

            // FIXED: 验证目录存在且可读（子目录必须可访问）
            if (!Files.exists(realAudioPath)) {
                log.warn("Audio resource directory does not exist: {}", realAudioPath);
                return;
            }
            
            if (!Files.isReadable(realAudioPath)) {
                log.error("Audio resource directory is not readable: {}", realAudioPath);
                return;
            }

            String audioPathUri = realAudioPath.toUri().toString();
            // Spring ResourceHandler 要求目录路径以 '/' 结尾
            if (!audioPathUri.endsWith("/")) {
                audioPathUri += "/";
            }

            registry.addResourceHandler("/audio/vocabulary/**")
                    .addResourceLocations(audioPathUri)
                    .setCachePeriod(3600); // 缓存1小时
            
            log.info("Audio resources configured: /audio/vocabulary/** -> {}", audioPathUri);

        } catch (Exception e) {
            log.error("Failed to configure audio resource handler: {}", e.getMessage(), e);
            // 不注册音频资源处理器，避免误配置
            return;
        }
    }
}
