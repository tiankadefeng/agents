package com.agents.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * {@code GET /api/patterns} 测试 - Success Criteria #1（默认空列表）。
 *
 * <p>默认 {@code @SpringBootTest} 上下文不含 {@code MockPattern}（MockPattern 用
 * {@code @TestConfiguration} 注解，不被 Spring 组件扫描自动扫描），
 * {@code AgentRegistry.list()} 返回空列表，{@code GET /api/patterns} 返回 {@code []} -
 * 验证端点工作（SC#1）。
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
    void shouldReturnEmptyPatternListByDefault() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // SC#1: 默认应用上下文无 MockPattern（MockPattern 用 @TestConfiguration 注解，
        // 不被组件扫描自动扫描），AgentRegistry.list() 返回空列表，GET /api/patterns 返回 []
        client.get().uri("/api/patterns")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");
    }
}
