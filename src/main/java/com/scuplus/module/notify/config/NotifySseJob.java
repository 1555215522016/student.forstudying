package com.scuplus.module.notify.config;

import com.scuplus.module.notify.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSE 心跳任务：客户端只靠"读"拿不到数据，代理/防火墙会认为连接空闲而断开。
 * 定时发 :ping 注释行（SSE 心跳约定，前端 EventSource 自动忽略以 : 开头的行）维持连接。
 */
@Component
@RequiredArgsConstructor
public class NotifySseJob {

    private final SseEmitterRegistry registry;

    /** 25s 一次，比常见代理空闲超时(60s)短，保证连接不被中间层掐掉 */
    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        registry.heartbeatAll();
    }
}