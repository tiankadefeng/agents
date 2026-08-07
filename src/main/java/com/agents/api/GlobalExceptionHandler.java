package com.agents.api;

import com.agents.agent.core.ErrorEvent;
import com.agents.streaming.SseEventEmitter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * <p>In-stream exceptions are handled by PingController.onErrorResume() which
 * is more reliable for mid-stream failures.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SseEventEmitter sseEmitter;

    public GlobalExceptionHandler(SseEventEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Flux<ServerSentEvent<String>>> handle(Exception ex) {
        // D-08: SSE error event with exception type + message, no stacktrace (T-2-06)
        Flux<ServerSentEvent<String>> flux = Flux.just(
            sseEmitter.fromAgentEvent(
                new ErrorEvent(
                    Instant.now(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage()
                )
            )
        );
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(flux);
    }
}
