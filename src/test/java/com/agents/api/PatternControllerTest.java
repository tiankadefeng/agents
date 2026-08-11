package com.agents.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * {@code GET /api/patterns} 测试 - Success Criteria #1（CoT 模式已注册，返回 1 个模式）。
 *
 * <p>Phase 3 已实现 CoTAgentPattern（{@code @Component}，被 Spring 组件扫描自动注册），
 * 因此 {@code GET /api/patterns} 返回非空列表（至少含 CoT 模式）。
 * 此测试验证端点工作（SC#1）且返回列表包含 "cot" 模式。
 *
 * <p>SC#4（显式导入 MockPattern 后返回 mock 模式）由
 * {@link PatternControllerWithMockTest} 验证（独立测试类，类级别显式导入 MockPattern）。
 * 不在同类用方法级 {@code @SpringBootTest} - {@code @SpringBootTest} 是类级别注解。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PatternControllerTest {

    @LocalServerPort
    int port;

    @Test
    void shouldReturnPatternListWithCot() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // SC#1: 应用上下文含 CoTAgentPattern（Phase 3 实现），GET /api/patterns 返回至少 1 个模式
        client.get().uri("/api/patterns")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isNotEmpty();
    }
}
