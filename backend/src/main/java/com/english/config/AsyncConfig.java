package com.english.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async task configuration for article version generation
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.version-generator.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.version-generator.max-pool-size:12}")
    private int maxPoolSize;

    @Value("${async.version-generator.queue-capacity:50}")
    private int queueCapacity;

    /**
     * Thread pool executor for version generation tasks
     */
    @Bean(name = "versionGeneratorExecutor")
    public Executor versionGeneratorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("version-gen-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
