package com.scuplus.module.notify.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.notify.entity.Notification;
import com.scuplus.module.notify.service.NotificationService;
import com.scuplus.module.notify.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 通知接口
 *
 * GET  /notifications/stream        SSE 长连接（text/event-stream）
 * GET  /notifications?page=&size=   我的通知列表（未读在前）
 * GET  /notifications/unread-count  未读数（红点）
 * POST /notifications/read/{id}     单条已读
 * POST /notifications/read-all      全部已读
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRegistry registry;
    private final ObjectMapper objectMapper;

    /**
     * SSE 长连接。浏览器 EventSource 断线重连会自动带 Last-Event-ID 请求头，
     * 据此补发错过的通知；curl 测试可用查询参数 lastEventId。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) Long lastEventId) {
        Long userId = currentUserId();

        SseEmitter emitter = new SseEmitter(0L); // 0 = 不自动超时，靠心跳 + 客户端重连保活
        registry.add(userId, emitter);
        emitter.onCompletion(() -> registry.remove(userId, emitter));
        emitter.onTimeout(() -> registry.remove(userId, emitter));
        emitter.onError(e -> registry.remove(userId, emitter));

        // 解析 lastId：优先查询参数，其次浏览器自动带的 Last-Event-ID 头
        Long lastId = lastEventId;
        if (lastId == null && lastEventIdHeader != null && !lastEventIdHeader.isBlank()) {
            try {
                lastId = Long.parseLong(lastEventIdHeader.trim());
            } catch (NumberFormatException ignored) {
                lastId = null;
            }
        }
        if (lastId != null) {
            for (Notification n : notificationService.replay(userId, lastId)) {
                try {
                    emitter.send(buildEvent(n));
                } catch (IOException e) {
                    break; // 客户端断了，剩下的交给下次重连
                }
            }
        }
        return emitter;
    }

    @GetMapping
    public Result<PageResult<Notification>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(notificationService.listPage(currentUserId(), page, size));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.countUnread(currentUserId()));
    }

    @PostMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(currentUserId(), id);
        return Result.success();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(currentUserId());
        return Result.success();
    }

    private SseEmitter.SseEventBuilder buildEvent(Notification n) {
        try {
            return SseEmitter.event()
                    .id(String.valueOf(n.getId()))
                    .name("notification")
                    .data(objectMapper.writeValueAsString(n));
        } catch (JsonProcessingException e) {
            return SseEmitter.event()
                    .id(String.valueOf(n.getId()))
                    .name("notification")
                    .data("{}");
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}