package com.agents.api;

import com.agents.agent.patterns.MockPattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * {@code GET /api/patterns} 测试 - Success Criteria #4（mock pattern 自动注册）。
 *
 * <p>独立测试类（与 {@link PatternControllerTest} 分离），类级别 {@code @Import(MockPattern.class)}
 * 加载 {@code @TestConfiguration} MockPattern。MockPattern 通过 Spring DI 自动收集到
 * {@code List<AgentPattern>}，{@code AgentRegistry} 启动期构建 {@code Map<String, AgentPattern>}
 * 时包含 {@code "mock"} ID，{@code GET /api/patterns} 返回 {@code [{id:'mock',...}]} -
 * 验证 Strategy + Plugin Registry "即插即用" 红利（SC#4）。
 *
 * <p>新增模式只需新建一个 bean（{@code @Component} 或 {@code @TestConfiguration} + {@code @Import}），
 * 无需改控制器或注册表 - 这是 Phase 2 架构的核心价值。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MockPattern.class)
class PatternControllerWithMockTest {

    @LocalServerPort
    int port;

    @Test
    void shouldReturnMockPatternWhenComponentRegistered() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // SC#4: @Import(MockPattern.class) 后 MockPattern 自动注册到 AgentRegistry，
        // GET /api/patterns 返回含 cot（Phase 3）、react（Phase 5）、selfAsk（Phase 6）和 mock 的模式列表
        client.get().uri("/api/patterns")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
.jsonPath("$.length()").isEqualTo(4)
            .jsonPath("[?(@.id=='mock')].id").isNotEmpty()
            .jsonPath("[?(@.id=='mock')].displayName").isEqualTo("Mock 模式（验证用）")
            .jsonPath("[?(@.id=='mock')].description").isEqualTo("临时验证 Strategy + Plugin Registry，验证后移除。");
    }
}
