package com.example.ingestion.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures the thread pool ({@code categorization.async.*}) used by the
 * asynchronous categorisation trigger. The pool shuts down gracefully and applies
 * a caller-runs policy for back-pressure.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final CategorisationProperties properties;

    public AsyncConfig(CategorisationProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "categorisationExecutor")
    public ThreadPoolTaskExecutor categorisationExecutor() {
        CategorisationProperties.Async async = properties.async();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.coreSize());
        executor.setMaxPoolSize(async.maxSize());
        executor.setQueueCapacity(async.queueCapacity());
        executor.setThreadNamePrefix(async.threadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Uncaught exception in async method {}", method.getName(), ex);
            new SimpleAsyncUncaughtExceptionHandler().handleUncaughtException(ex, method, params);
        };
    }
}

