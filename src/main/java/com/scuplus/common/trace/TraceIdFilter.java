package com.scuplus.common.trace;

import cn.hutool.core.util.IdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全链路追踪：给每个请求生成一个 traceId，塞进 SLF4J 的 MDC。
 * 日志 pattern 加了 [%X{traceId}] 后，同一请求里的所有日志（Controller/Service/Redis/MySQL）
 * 都带上同一个 traceId → 出问题时按 id 一条线拉出完整调用链。
 *
 * 注意：
 *  1. finally 里必须 MDC.remove —— 容器线程池会复用线程，不清会串 traceId 到下一个请求
 *  2. 异步线程（@Async / MQ 消费端）默认拿不到父线程的 MDC → 生产要给线程池配 TaskDecorator 透传
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 允许上游（网关/前端）按约定传一个 id 作为链路标识；没传就自生成 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = IdUtil.simpleUUID();
        }
        MDC.put("traceId", traceId);
        // 回写响应头：前端 console 能看到，出问题时报这一个 id 就能反查整条链路
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}