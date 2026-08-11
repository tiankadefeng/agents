package com.agents.tool;

import com.agents.tool.builtin.CalculatorTool;
import com.agents.tool.builtin.TimeTool;
import com.agents.tool.builtin.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolRegistry}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>TOOL-01: ToolRegistry 聚合 3 个内置工具（weather/calculator/time）为 List&lt;ToolCallback&gt;</li>
 *   <li>D-08: ToolRegistry.forPattern("cot") 返回空列表，forPattern("react") 返回全量</li>
 *   <li>T-4-04: ToolRegistry.byName("unknown") 抛 NoSuchToolException</li>
 *   <li>TOOL-03: ToolCallbacks.from() 对 3 个工具类均生成非空 ToolCallback[]</li>
 * </ul>
 *
 * <p>This is a pure unit test (no Spring context) - fast, no Spring context startup.
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(
            List.of(new WeatherTool(), new CalculatorTool(), new TimeTool()),
            List.of()
        );
    }

    @Test
    void shouldAggregateAllBuiltinTools() {
        assertThat(registry.all()).hasSize(3);
    }

    @Test
    void shouldReturnEmptyForCoT() {
        assertThat(registry.forPattern("cot")).isEmpty();
    }

    @Test
    void shouldReturnAllForReact() {
        assertThat(registry.forPattern("react")).hasSize(3);
    }

    @Test
    void shouldFindToolByName() {
        assertThat(registry.byName("weather").getToolDefinition().name()).isEqualTo("weather");
    }

    @Test
    void shouldThrowForUnknownTool() {
        assertThatThrownBy(() -> registry.byName("unknown"))
            .isInstanceOf(NoSuchToolException.class)
            .hasMessageContaining("未知工具");
    }

    @Test
    void shouldGenerateToolCallbacksViaFrom() {
        assertThat(org.springframework.ai.support.ToolCallbacks.from(new WeatherTool())).isNotEmpty();
        assertThat(org.springframework.ai.support.ToolCallbacks.from(new CalculatorTool())).isNotEmpty();
        assertThat(org.springframework.ai.support.ToolCallbacks.from(new TimeTool())).isNotEmpty();
    }
}