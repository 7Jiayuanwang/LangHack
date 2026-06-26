package com.english.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async task configuration for article version generation
 *
 * Thread pool sizing:
 * - Core: 8 threads (handles baseline load)
 * - Max: 32 threads (handles spikes)
 * - Queue: 200 (buffer for bursty workloads)
 * - Rejection: CallerRunsPolicy (backpressure mechanism)
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${async.version-generator.core-pool-size:8}")
    private int corePoolSize;

    @Value("${async.version-generator.max-pool-size:32}")
    private int maxPoolSize;

    @Value("${async.version-generator.queue-capacity:200}")
    private int queueCapacity;

    @Value("${media.transcode.max-concurrent:1}")
    private int transcodeMaxConcurrent;

    @Value("${media.transcode.queue-capacity:50}")
    private int transcodeQueueCapacity;

    @Value("${media.transcode.timeout-minutes:30}")
    private int transcodeTimeoutMinutes;

    /**
     * Thread pool executor for version generation tasks
     *
     * 拒绝策略: CallerRunsPolicy - 队列满时，由调用线程执行任务，避免任务丢弃
     * 这样可以自然地实现背压(backpressure)，防止内存溢出
     * 
     * IMPORTANT: CallerRunsPolicy will block the calling thread when queue is full.
     * This is intentional for backpressure, but monitor queue depth to detect overload.
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
        
        // 设置拒绝策略：队列满时由调用线程执行，避免任务丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                log.warn("Thread pool rejected execution: core={}, active={}, queue={}",
                        executor.getCorePoolSize(), 
                        executor.getActiveCount(),
                        executor.getQueue().size());
                super.rejectedExecution(r, executor);
            }
        });
        
        executor.initialize();
        log.info("Version generator thread pool initialized: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean(name = "transcodeExecutor")
    public Executor transcodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(transcodeMaxConcurrent);
        executor.setMaxPoolSize(transcodeMaxConcurrent);
        executor.setQueueCapacity(transcodeQueueCapacity);
        executor.setThreadNamePrefix("transcode-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(transcodeTimeoutMinutes * 60 + 10);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Transcode thread pool initialized: concurrent={}, queue={}, awaitSec={}",
                transcodeMaxConcurrent, transcodeQueueCapacity, transcodeTimeoutMinutes * 60 + 10);
        return executor;
    }

    @Bean(name = "backupTaskScheduler")
    public TaskScheduler backupTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("backup-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "updateTaskScheduler")
    public TaskScheduler updateTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("update-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "syncTaskScheduler")
    public TaskScheduler syncTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sync-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Thread pool executor for event-driven campaign data collection
     *
     * Pool sizing:
     * - Core: 2 threads (collection is DB-bound, not CPU-bound)
     * - Max: 4 threads (handles burst of concurrent learning activities)
     * - Queue: 100 (buffer for simultaneous student actions)
     * - Rejection: CallerRunsPolicy (backpressure, blocks learning API temporarily but prevents OOM)
     */
    @Bean(name = "campaignCollectionExecutor")
    public Executor campaignCollectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("campaign-collect-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                log.warn("[Campaign Collection] 队列已满, 任务将同步执行 (active={}, pool={}, queue={})",
                        executor.getActiveCount(),
                        executor.getPoolSize(),
                        executor.getQueue().size());
                super.rejectedExecution(r, executor);
            }
        });

        executor.initialize();
        log.info("Campaign collection thread pool initialized: core=2, max=4, queue=100");
        return executor;
    }
}
