package com.scuplus.module.course.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 抢课 PHASE1(Redis主) 的异步落库线程池。
 * 作用：Redis 原子判赢后，把赢家写进 MySQL 这件"慢写"移出请求线程，
 * 让抢课请求路径只剩 Redis（并发峰值全压在 Redis 上）。
 * 队满直接拒绝：调用方捕获 RejectedExecutionException 降级给对账补齐，绝不阻塞请求线程。
 */
@Configuration
public class CourseAsyncConfig {

    @Bean("coursePersistExecutor")
    public Executor coursePersistExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        // 万人抢课 ≈ 每门课容量100，峰值瞬时几千行；2 万队列容量足够吸收，溢出走对账兜底
        executor.setQueueCapacity(20000);
        executor.setThreadNamePrefix("course-persist-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}