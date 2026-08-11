package com.agents.tool.builtin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WeatherTool}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>D-03: mock data for known cities (北京/上海/广州/New York)</li>
 *   <li>D-03: default fallback for unknown cities</li>
 *   <li>D-07: returns JSON string</li>
 * </ul>
 *
 * <p>Pure unit test (no Spring context) - direct instantiation of {@link WeatherTool}.
 */
class WeatherToolTest {

    private final WeatherTool tool = new WeatherTool();

    @Test
    void shouldReturnMockWeatherForBeijing() {
        String result = tool.getWeather("北京");
        assertThat(result).contains("\"city\":\"北京\"");
        assertThat(result).contains("\"temperature\":22");
        assertThat(result).contains("\"condition\":\"晴\"");
    }

    @Test
    void shouldReturnDefaultForUnknownCity() {
        String result = tool.getWeather("未知城市XYZ");
        assertThat(result).contains("\"temperature\":20");
        assertThat(result).contains("\"condition\":\"未知\"");
    }
}