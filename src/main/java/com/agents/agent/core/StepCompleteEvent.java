package com.agents.agent.core;

import java.time.Instant;

/**
 * Plan-and-Execute 步骤完成 (Phase 7).
 *
 * <p>D-08: 字段 = {@code ts} + {@code stepNumber} + {@code status} + {@code result}。
 * {@code status} 取值: "done" 表示成功完成，"failed" 表示失败 (D-09)。
 * {@code result} 为步骤执行结果文本（可为 null 或空字符串）。
 * 前端按 status 渲染绿色（done）或红色（failed）标记。
 */
public record StepCompleteEvent(
    Instant ts,
    int stepNumber,
    String status,
    String result
) implements AgentEvent {}