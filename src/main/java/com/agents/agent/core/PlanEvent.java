package com.agents.agent.core;

import java.time.Instant;
import java.util.List;

/**
 * Plan-and-Execute 计划生成 (Phase 7).
 *
 * <p>D-01: 字段 = {@code ts} + {@code steps}。{@code steps} 为结构化步骤列表，
 * 每步含步骤号、描述、预期输出。Planner 生成完整计划后一次性发射此事件。
 */
public record PlanEvent(Instant ts, List<Step> steps) implements AgentEvent {
    /**
     * 单个步骤定义。
     *
     * @param stepNumber     步骤号（从 1 开始）
     * @param description    步骤描述（做什么）
     * @param expectedOutput 预期输出（步骤完成后应产出的结果描述）
     */
    public record Step(int stepNumber, String description, String expectedOutput) {}
}