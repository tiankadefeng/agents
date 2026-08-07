package com.agents.agent.core;

import java.time.Instant;

/**
 * ReAct 工具结果 (Phase 5 用)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code toolName} + {@code result} + {@code isError}。
 * {@code isError} 区分正常结果与工具执行异常，前端按标志渲染不同样式。
 */
public record ToolResultEvent(
    Instant ts,
    String toolName,
    String result,
    boolean isError
) implements AgentEvent {}
