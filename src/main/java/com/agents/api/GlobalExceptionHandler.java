package com.agents.api;

import com.agents.agent.core.ErrorEvent;
import com.agents.streaming.SseEventEmitter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
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
 * <p>实现说明（修正旧 javadoc 的错误论断）: {@code @ExceptionHandler} 方法的返回值由
 * {@code ExceptionHandlerExceptionResolver} 处理，其返回值处理器链<strong>没有</strong>
 * 响应式类型支持（普通 controller 方法的 {@code Flux} 返回值走
 * {@code ResponseBodyEmitterReturnValueHandler}/{@code ReactiveTypeHandler} 异步流式，
 * 异常处理器路径不走）。若返回 {@code Flux}，会落入
 * {@code RequestResponseBodyMethodProcessor}（@RestControllerAdvice = @ResponseBody 语义），
 * 尝试用 {@code HttpMessageConverter} 序列化 {@code FluxJust} 对象本身，抛
 * {@code HttpMessageNotWritableException}，客户端永远收不到 ErrorEvent 帧。
 *
 * <p>因此本 handler 声明 {@link HttpServletResponse} 参数并返回 {@code void}，
 * 手写 SSE wire format 直接写响应。声明 {@code HttpServletResponse} 参数的 handler
 * 方法会将响应标记为 fully handled（{@code mavContainer.setRequestHandled(true)}），
 * Spring 不再做任何返回值处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SseEventEmitter sseEmitter;

    public GlobalExceptionHandler(SseEventEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }

    @ExceptionHandler(Exception.class)
    public void handle(Exception ex, HttpServletResponse response) throws IOException {
        // 完整 stacktrace 仅落服务端日志（T-2-06: 客户端只收 type + message）
        log.warn("Pre-stream exception on SSE endpoint, degrading to ErrorEvent frame (D-08)", ex);

        // D-08: SSE 错误契约 - 200（非 500）+ text/event-stream + 单个 ErrorEvent 帧
        response.setStatus(200);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        // 必须在 getWriter() 之前设置；SSE 规范 UTF-8，异常 message 可能含中文
        response.setCharacterEncoding("UTF-8");

        // D-08 / T-2-06: message = 异常 simple name + ": " + message，无 stacktrace
        ServerSentEvent<String> sse = sseEmitter.fromAgentEvent(
            new ErrorEvent(
                Instant.now(),
                ex.getClass().getSimpleName() + ": " + ex.getMessage()
            )
        );

        response.getWriter().write(sseWireFormat(sse));
        response.getWriter().flush();
    }

    /**
     * 手工拼接 SSE wire format（冒号后无空格 - 前端 useSSEStream 以
     * line.slice(6)/slice(5) 解析，现有测试断言 {@code event:ErrorEvent}）。
     * 无需 null 处理：SseEventEmitter 恒定设置 id/event/data。
     */
    private String sseWireFormat(ServerSentEvent<String> sse) {
        return "id:" + sse.id() + "\n"
            + "event:" + sse.event() + "\n"
            + "data:" + sse.data() + "\n"
            + "\n";
    }
}
