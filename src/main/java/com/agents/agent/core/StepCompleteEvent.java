package com.agents.agent.core;

import java.time.Instant;

/**
 * Plan-and-Execute 步骤完成 (Phase 7 用)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code stepNumber} + {@code status}。
 * placeholder - Phase 7 可能加 {@code String result} 字段承载步骤执行结果，由 planner 决定。
 * 当前 {@code status} 为步骤完成状态（如 "success" / "skipped"），供前端标记 el-steps 完成态。
 */
public record StepCompleteEvent(
    Instant ts,
    int stepNumber,
    String status
) implements AgentEvent {}
