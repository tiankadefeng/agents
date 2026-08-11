package com.agents.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/tools} + {@code POST /api/tools/{toolName}/invoke} 集成测试。
 *
 * <p>验证 SC#3（直接工具调用端点）: 通过 WebTestClient 直接调用 ToolController 端点，
 * 不依赖 LLM，验证工具层可独立工作。
 *
 * <p>5 个测试方法，对应 VALIDATION.md §Per-Task Verification Map 4-04-01 ~ 4-04-05：
 * - shouldListAllTools: GET /api/tools 返回 3 个工具元数据
 * - shouldInvokeWeatherTool: POST /api/tools/weather/invoke 返回天气 JSON
 * - shouldInvokeCalculatorTool: POST /api/tools/calculator/invoke 返回计算结果
 * - shouldInvokeTimeTool: POST /api/tools/time/invoke 返回当前时间
 * - shouldReturn404ForUnknownTool: POST /api/tools/unknown/invoke 返回 404（T-4-04）
 *
 * <p>与 AgentControllerTest 不同，此处使用真实 {@code @Component} 工具类
 * （WeatherTool / CalculatorTool / TimeTool），由 Spring 组件扫描自动注册。
 * 无需 {@code @Import} 任何 mock 类。
 *
 * <p>注意：content-type 是 {@code application/json}（非 SSE 的 {@code text/event-stream}），
 * 因为 ToolController 是普通 REST 端点，区别于 AgentController 的 SSE 流式端点。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ToolControllerTest {

    @LocalServerPort
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Test
    void shouldListAllTools() {
        // 4-04-01: GET /api/tools 返回 3 个工具的 name/description/inputSchema 元数据列表
        WebTestClient client = client();
        client.get().uri("/api/tools")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            // 验证 3 个工具
            .jsonPath("$.length()").isEqualTo(3)
            // 使用 filter 断言避免索引顺序依赖（WR-03）
            .jsonPath("[?(@.name=='weather')].description").isNotEmpty()
            .jsonPath("[?(@.name=='calculator')].description").isNotEmpty()
            .jsonPath("[?(@.name=='time')].description").isNotEmpty()
            // 验证 inputSchema 存在（JSON schema 字符串）
            .jsonPath("[?(@.name=='weather')].inputSchema").isNotEmpty();
    }

    @Test
    void shouldInvokeWeatherTool() {
        // 4-04-02: POST /api/tools/weather/invoke 返回天气 JSON
        String requestBody = "{\"arguments\":{\"city\":\"北京\"}}";
        WebTestClient client = client();
        client.post().uri("/api/tools/weather/invoke")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                // 验证返回的 JSON 包含预期字段
                assertThat(body).contains("\"city\":\"北京\"");
                assertThat(body).contains("\"temperature\":22");
                assertThat(body).contains("\"condition\":\"晴\"");
            });
    }

    @Test
    void shouldInvokeCalculatorTool() {
        // 4-04-03: POST /api/tools/calculator/invoke 返回计算结果
        String requestBody = "{\"arguments\":{\"expression\":\"2+3\"}}";
        WebTestClient client = client();
        client.post().uri("/api/tools/calculator/invoke")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                // 验证返回 JSON 包含 result=5
                assertThat(body).contains("\"result\":5");
                assertThat(body).contains("\"expression\":\"2+3\"");
            });
    }

    @Test
    void shouldInvokeTimeTool() {
        // 4-04-04: POST /api/tools/time/invoke 返回当前时间 JSON
        String requestBody = "{\"arguments\":{}}";
        WebTestClient client = client();
        client.post().uri("/api/tools/time/invoke")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                // 验证返回 JSON 包含 datetime/timezone/weekday 字段
                assertThat(body).contains("datetime");
                assertThat(body).contains("timezone");
                assertThat(body).contains("weekday");
            });
    }

    @Test
    void shouldReturn404ForUnknownTool() {
        // 4-04-05: POST /api/tools/unknown/invoke 返回 404（T-4-04）
        // 不泄露已注册工具列表
        String requestBody = "{\"arguments\":{}}";
        WebTestClient client = client();
        client.post().uri("/api/tools/unknown/invoke")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody(String.class)
            .value(body -> {
                assertThat(body).contains("error");
                assertThat(body).contains("未知工具");
            });
    }
}