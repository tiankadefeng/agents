package com.agents.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code POST /api/agent/execute} 测试 - Success Criteria #2（未知 patternId 返回 404 + ErrorEvent）。
 *
 * <p>验证 SC#2 落地（D-06 兼容方式）: 未知 {@code patternId} 请求触发
 * {@code AgentRegistry.require(id)} 抛出 {@code NoSuchPatternException}，{@code AgentController}
 * 捕获后返回 HTTP <strong>404</strong> + {@code Content-Type: text/event-stream} + 单个
 * {@code ErrorEvent} SSE 帧（02-UI-SPEC.md §Copywriting 锁定文案）。
 *
 * <p>不验证真实模式执行（需 DeepSeek API key + 真实 pattern 实现）- 仅验证错误路径。
 * 真实模式执行由 Phase 3+ CoT pattern 测试覆盖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentControllerTest {

    @LocalServerPort
    int port;

    @Test
    void shouldReturnErrorEventForUnknownPatternId() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // SC#2: 未知 patternId "nonexistent" 返回 404 + text/event-stream + ErrorEvent SSE 帧
        // 请求体: {"patternId":"nonexistent","question":"test"}
        String requestBody = "{\"patternId\":\"nonexistent\",\"question\":\"test\"}";

        client.post().uri("/api/agent/execute")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM)
            .expectBody(String.class)
            .value(body -> {
                // D-01: SSE event 字段 = class simple name（"ErrorEvent"）
                assertThat(body).contains("event:ErrorEvent");
                // 02-UI-SPEC.md §Copywriting 锁定文案
                assertThat(body).contains("未知模式：nonexistent");
            });
    }
}
