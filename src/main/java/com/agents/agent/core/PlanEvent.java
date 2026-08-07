package com.agents.agent.core;

import java.time.Instant;

/**
 * Plan-and-Execute 计划生成 (Phase 7 用)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code description}。
 * placeholder - Phase 7 将扩展为 {@code List<String> steps}，由 planner 决定具体字段形态。
 * 当前 {@code description} 为计划的自然语言摘要，供前端展示。
 */
public record PlanEvent(Instant ts, String description) implements AgentEvent {}
