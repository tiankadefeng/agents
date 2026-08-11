package com.agents.tool;

import com.agents.tool.builtin.CalculatorTool;
import com.agents.tool.builtin.TimeTool;
import com.agents.tool.builtin.WeatherTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-based verification that all tool classes have {@link Tool @Tool}-annotated methods.
 *
 * <p>Satisfies TOOL-03 Success Criteria #2: 工具方法用 @Tool 注解注册（Spring AI 2.0 API）.
 *
 * <p>Pure unit test (no Spring context) - uses Java reflection only.
 */
class ToolAnnotationTest {

    @Test
    void shouldHaveToolAnnotationOnAllToolMethods() {
        List<Class<?>> toolClasses = List.of(
            WeatherTool.class,
            CalculatorTool.class,
            TimeTool.class
        );

        for (Class<?> clazz : toolClasses) {
            long toolMethodCount = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .count();

            assertThat(toolMethodCount)
                .as("Each tool class must have at least one @Tool method: " + clazz.getSimpleName())
                .isGreaterThan(0);
        }
    }
}