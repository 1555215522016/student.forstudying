package com.scuplus.module.notify.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 在线连接注册表（进程内）
 *
 * 单实例假设：每台 JVM 存储本机的 userId → SseEmitter。
 * 多实例扩展时改 Redis Pub/Sub：事件广播到所有实例，再由各实例查本地表推给连接。
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private final ConcurrentHashMap<Long, SseEmitter> online = new ConcurrentHashMap<>();

    public void add(Long userId, SseEmitter emitter) {
        online.put(userId, emitter);
    }

    public void remove(Long userId, SseEmitter emitter) {
        online.remove(userId, emitter);
    }

    public boolean isOnline(Long userId) {
        return online.containsKey(userId);
    }

    /** 给某用户推事件；连接已断开/异常则移出注册表，静默（重连由 Last-Event-ID 补发） */
    public void sendToUser(Long userId, SseEmitter.SseEventBuilder event) {
        SseEmitter emitter = online.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            online.remove(userId, emitter);
        }
    }

    /** 心跳：给所有存活连接发注释行保活，失败即移除死连接 */
    public void heartbeatAll() {
        online.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                online.remove(userId, emitter);
            }
        });
    }
}