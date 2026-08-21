package com.agents.agent.core;

import java.time.Instant;

/**
 * ReflexionEvaluateEvent - Reflexion 评估结果 (Phase 9 新增).
 *
 * <p>D-03: 字段 = {@code ts} + {@code round} + {@code score} + {@code feedback}。
 * {@code score} 为 1-10 整数评分，{@code feedback} 为评估器反馈意见。
 */
public record ReflexionEvaluateEvent(
    Instant ts,
    int round,
    int score,
    String feedback
) implements AgentEvent {}