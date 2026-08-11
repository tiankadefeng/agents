package com.agents.tool.builtin;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具（mock 数据）。
 *
 * <p>D-03: 全部 mock 数据，根据城市名模拟不同温度/天气，无外部依赖。
 * 教学演示用，避免外部 API 依赖导致演示环境不可用。
 * PROJECT.md Out of Scope 已明确"真实外部 API 用 mock 工具"。
 *
 * <p>D-07: 返回结构化 JSON 字符串（String），便于 LLM 解析。
 * 工具名为简短英文 {@code weather}。
 */
@Component
public class WeatherTool {

    @Tool(name = "weather",
          description = "查询指定城市的当前天气。返回 JSON 字符串，含 city, temperature (摄氏度), condition 字段。")
    public String getWeather(@ToolParam(description = "城市名称，支持中文或英文，如 北京 / Shanghai / New York") String city) {
        if (city == null || city.isBlank()) {
            return "{\"error\":\"城市名不能为空\"}";
        }
        // D-03: 全 mock 数据，根据城市名模拟不同温度/天气
        return switch (city) {
            case "北京", "Beijing", "beijing" ->
                "{\"city\":\"北京\",\"temperature\":22,\"condition\":\"晴\"}";
            case "上海", "Shanghai", "shanghai" ->
                "{\"city\":\"上海\",\"temperature\":26,\"condition\":\"多云\"}";
            case "广州", "Guangzhou", "guangzhou" ->
                "{\"city\":\"广州\",\"temperature\":30,\"condition\":\"雷阵雨\"}";
            case "New York", "纽约" ->
                "{\"city\":\"New York\",\"temperature\":15,\"condition\":\"cloudy\"}";
            default ->
                "{\"city\":\"" + city + "\",\"temperature\":20,\"condition\":\"未知\"}";
        };
    }
}