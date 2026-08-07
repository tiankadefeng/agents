package com.agents.agent.core;

import java.time.Instant;

/**
 * Plan-and-Execute 步骤开始 (Phase 7 用)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code stepNumber} + {@code description}。
 * {@code stepNumber} 从 1 开始，前端按步骤号渲染 {@code el-steps} 进度条。
 */
public record StepStartEvent(
    Instant ts,
    int stepNumber,
    String description
) implements AgentEvent {}
