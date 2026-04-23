package com.english.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import java.util.List;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    private final AccessLogInterceptor accessLogInterceptor;
    private final AdminMasterInterceptor adminMasterInterceptor;

    public WebMvcConfig(AccessLogInterceptor accessLogInterceptor,
                        AdminMasterInterceptor adminMasterInterceptor) {
        this.accessLogInterceptor = accessLogInterceptor;
        this.adminMasterInterceptor = adminMasterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ops/monitoring/realtime/stream",
                                     "/api/ops/update/trigger",
                                     "/audio/**", "/favicon.ico");

        // Force master datasource for admin/ops paths to avoid read-after-write inconsistency
        registry.addInterceptor(adminMasterInterceptor)
                .addPathPatterns("/api/admin/**", "/api/ops/**");
    }

    public void addViewControllers(ViewControllerRegistry registry) {
        // 前端路由回退到 index.html（SPA 支持）
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/articles").setViewName("forward:/index.html");
        registry.addViewController("/article/**").setViewName("forward:/index.html");
        registry.addViewController("/student").setViewName("forward:/index.html");
        registry.addViewController("/student/**").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 前端构建产物：先注册，避免后续音频配置失败时 return 跳过静态资源
        // SPA 路由（/、/articles、/article/**、/student/**、/login）由 addViewControllers 转发到 index.html
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(3600);
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(3600);
        registry.addResourceHandler("/data/**")
                .addResourceLocations("classpath:/static/data/")
                .setCachePeriod(3600);
        registry.addResourceHandler("/favicon.ico", "/index.html")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);

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
        }
    }
}
