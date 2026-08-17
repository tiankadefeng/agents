package com.agents.api;

import com.agents.agent.core.ErrorEvent;
import com.agents.streaming.SseEventEmitter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Flux;

import java.time.Instant;

/**
 * Global exception handler for pre-stream exceptions (D-08, T-2-06).
 *
 * <p>Phase 2 重构 (D-05): 注入 SseEventEmitter 实例，发射 ErrorEvent。沿用 Phase 1 D-08 -
 * 返回 200 + text/event-stream + ErrorEvent（非 HTTP 500）。message 字段含异常 type + message，
 * 无 stacktrace（T-2-06 信息泄露防护，ASVS L1 V7.1 合规）。
 *
 * <p>Catches exceptions thrown BEFORE the reactive chain starts streaming
 * (e.g., JSON parse failure, API key missing, validation errors).
 *
 * <p>In-stream exceptions are handled by the controllers' onErrorResume() which
 * is more reliable for mid-stream failures.
 *
 * <p>注: 不直接返回 {@code ResponseEntity<Flux<ServerSentEvent>>}——Spring MVC 的
 * {@code HttpEntityMethodProcessor} 不识别 {@code ResponseEntity} 包装的响应式类型，
 * 会尝试用 {@code HttpMessageConverter} 序列化 {@code FluxJust} 而抛
 * {@code HttpMessageNotWritableException}。改为注入 {@link HttpServletResponse}
 * 设置状态码后直接返回 {@code Flux}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SseEventEmitter sseEmitter;

    public GlobalExceptionHandler(SseEventEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }

    @ExceptionHandler(Exception.class)
    public Flux<ServerSentEvent<String>> handle(Exception ex, HttpServletResponse response) {
        // D-08: SSE error event with exception type + message, no stacktrace (T-2-06)
        response.setStatus(200);
        return Flux.just(
            sseEmitter.fromAgentEvent(
                new ErrorEvent(
                    Instant.now(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage()
                )
            )
        );
    }
}
