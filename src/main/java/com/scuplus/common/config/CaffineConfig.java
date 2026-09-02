package com.scuplus.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Spring Cache（@Cacheable）用的 Caffeine 缓存管理器。
 * 注意：抢课列表页 CourseBrowseService 用的是 Caffeine 原生构建器（direct builder），
 * 不走这套 @Cacheable —— 二者是两套用法，可并存。
 */
@Configuration
@EnableCaching
public class CaffineConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("courseMeta");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.SECONDS));
        return cacheManager;
    }
}
