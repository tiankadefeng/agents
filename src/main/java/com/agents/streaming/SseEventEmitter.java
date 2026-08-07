package com.agents.streaming;

import com.agents.agent.core.AgentEvent;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 2 重构 (D-03): @Component + ObjectMapper。处理 9 种 AgentEvent 类型，sealed interface
 * 保证编译时穷尽。废弃 Phase 1 全部 static helper 方法（message/reasoning/finalAnswer/error/ping/escape）。
 *
 * <p>event 字段 = class simple name (PascalCase，如 {@code ReasoningEvent} / {@code ToolCallEvent} /
 * {@code ErrorEvent})，D-01 推翻 Phase 1 D-02 的 {@code event=message} + data JSON 带 type 字段方案。
 *
 * <p>data 字段 = record JSON 序列化 (不含 {@code type} 字段)，由 {@link ObjectMapper} 自动处理
 * {@code Map<String,Object>} 等复杂字段（{@code ToolCallEvent.arguments}），替代 Phase 1 手写 escape()。
 *
 * <p>id 字段 = {@code ev.ts().toString()}（ISO-8601 instant string），前端可按 id 排序。
 *
 * <p>9 种事件类型统一处理，无需按类型分发（{@code getClass().getSimpleName()} + {@code writeValueAsString}
 * 已足够）。sealed interface 穷尽性在 {@link AgentEvent} 接口层保证。
 */
@Component
public class SseEventEmitter {

    private final ObjectMapper objectMapper;

    public SseEventEmitter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * D-03: 将 AgentEvent 序列化为 SSE 帧。
     *
     * @param ev 任意 AgentEvent 子类型（9 种 record 之一）
     * @return ServerSentEvent，event 字段 = class simple name，data 字段 = record JSON，id 字段 = ts
     */
    public ServerSentEvent<String> fromAgentEvent(AgentEvent ev) {
        return ServerSentEvent.<String>builder()
            .id(ev.ts().toString())
            .event(ev.getClass().getSimpleName())
            .data(toJson(ev))
            .build();
    }

    /**
     * D-03: 使用 ObjectMapper 序列化 record 为 JSON。
     * Fallback 返回 "{}" 不应在 record 上发生（record 字段简单，ObjectMapper 原生支持）。
     */
    private String toJson(AgentEvent ev) {
        try {
            return objectMapper.writeValueAsString(ev);
        } catch (JacksonException e) {
            return "{}";
        }
    }
}
