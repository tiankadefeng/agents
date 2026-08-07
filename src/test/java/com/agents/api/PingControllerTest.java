package com.agents.api;

import com.agents.api.dto.PingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PingController tests for SSE-01 (D-01, D-05).
 *
 * <p>Phase 2 重构 (D-01): SSE event 字段 = class simple name（如 {@code ErrorEvent}），
 * data 字段 = record JSON 序列化（不含 {@code type} 字段）。断言从 {@code "type":"error"}
 * 改为 {@code event:ErrorEvent}，验证 SSE 帧的 {@code event:} 行。
 *
 * <p>Tests the SSE endpoint returns proper content-type and responds to valid input.
 * Note: 429 retry verification (LLM-06) is verified via application.yml config
 * (spring.ai.retry.max-attempts=3) and tested in DeepSeekIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PingControllerTest {

    @LocalServerPort
    int port;

    @Test
    void shouldReturnSseContentType() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // SSE-01: POST /api/ping returns text/event-stream
        client.post().uri("/api/ping")
            .header("Content-Type", "application/json")
            .bodyValue(new PingRequest("test question"))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("text/event-stream");
    }

    @Test
    void shouldRejectEmptyInput() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // AI-SPEC Section 6 Guardrails: empty input returns SSE error event
        // D-01: SSE event field = class simple name ("ErrorEvent"), not "message"
        client.post().uri("/api/ping")
            .header("Content-Type", "application/json")
            .bodyValue(new PingRequest(""))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("text/event-stream")
            .expectBody(String.class)
            .value(body -> assertThat(body).contains("event:ErrorEvent"));
    }
}
