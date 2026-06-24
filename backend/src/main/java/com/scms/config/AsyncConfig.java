package com.scms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AsyncConfig — dedicated thread pool for @Async work (notification emails,
 * event listeners) so a slow SMTP call can never block an HTTP request
 * thread.
 *
 * MENTOR NOTE — v1.3 finding under Reliability: "No async operations — a
 * slow email (hypothetical) would block the HTTP thread." v2.0 makes email
 * genuinely async via Spring's @EventListener + @Async, backed by this named
 * executor instead of the JDK's default (which silently falls back to
 * SimpleAsyncTaskExecutor — unbounded thread creation — if you forget to
 * configure one explicitly).
 *
 * @EnableScheduling powers LoginThrottleCleanupJob (see that class) — the
 * replacement for the v1.3 "wipe the entire cache at 10,000 entries" bug.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "notificationExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("scms-async-");
        executor.initialize();
        return executor;
    }
}
