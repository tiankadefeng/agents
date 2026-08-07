package com.agents.agent.core;

import java.time.Instant;
import java.util.Map;

/**
 * ReAct 工具调用 (Phase 5 用)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code toolName} + {@code arguments}。
 * {@code arguments} 用 {@code Map<String,Object>} 类型，由 {@code ObjectMapper} 自动序列化复杂参数
 * （这是 D-03 引入 {@code ObjectMapper} 的主要动因）。
 *
 * <p>T-2-03: Phase 5 实现时需在构造时用 {@code Map.copyOf} 包裹保证不可变；Phase 2 不消费此字段。
 */
public record ToolCallEvent(
    Instant ts,
    String toolName,
    Map<String, Object> arguments
) implements AgentEvent {}
