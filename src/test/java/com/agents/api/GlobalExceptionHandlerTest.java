package com.agents.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GlobalExceptionHandler} 回归测试 - D-08（SSE 错误契约）在 pre-stream 异常路径上的落地。
 *
 * <p>验证契约: {@code POST /api/agent/execute} 携带 malformed JSON body 触发
 * {@code HttpMessageNotReadableException}（@RequestBody 解析失败，早于 controller 方法执行），
 * 全局异常处理器必须返回 HTTP <strong>200</strong> + {@code Content-Type: text/event-stream} +
 * 单个完整 {@code ErrorEvent} SSE 帧（D-08），message = 异常 simple name + ": " + message，
 * 无 stacktrace 泄露到客户端（T-2-06，ASWF L1 V7.1 合规）。
 *
 * <p>回归背景: 旧实现返回裸 {@code Flux<ServerSentEvent<String>>}。普通 controller 方法的
 * 响应式返回值由 {@code ReactiveTypeHandler} 流式处理，但 {@code @ExceptionHandler} 的返回值
 * 走 {@code ExceptionHandlerExceptionResolver}，其返回值处理器链无响应式类型支持，
 * {@code Flux} 落入 {@code RequestResponseBodyMethodProcessor} 被当作普通对象序列化，
 * 抛出 {@code HttpMessageNotWritableException: No converter for [FluxJust]}，
 * 客户端永远收不到 ErrorEvent 帧。此错误路径此前无测试覆盖，故一直未被发觉。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GlobalExceptionHandlerTest {

    @LocalServerPort
    int port;

    @Test
    void shouldReturnErrorEventFrameForMalformedJsonBody() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // malformed JSON（非 JSON string）触发 @RequestBody 解析失败，
        // 在 controller 方法执行前抛 HttpMessageNotReadableException
        client.post().uri("/api/agent/execute")
            .header("Content-Type", "application/json")
            .bodyValue("not-json")
            .exchange()
            // D-08: 错误以 SSE ErrorEvent 表达，而非 HTTP 4xx/5xx
            .expectStatus().isOk()
            // compatible-with 而非 exact: Tomcat 在 getWriter 后可能追加 ;charset=UTF-8，
            // 按 SSE 规范无害
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectBody(String.class)
            .value(body -> {
                // D-01: SSE event 字段 = class simple name（"ErrorEvent"）
                assertThat(body).contains("event:ErrorEvent");
                // D-08: message 含异常 simple name（HttpMessageNotReadableException）
                assertThat(body).contains("HttpMessageNotReadableException");
                // 单行 JSON payload（可被前端 JSON.parse）
                assertThat(body).contains("data:{");
                // SSE 帧终止符：前端 useSSEStream 以 \n\n 切帧，缺失则帧永不派发
                assertThat(body).endsWith("\n\n");
            });
    }
}
