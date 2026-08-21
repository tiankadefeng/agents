package com.agents.agent.core;

import java.time.Instant;

/**
 * ReflexionAttemptEvent - Reflexion 每轮尝试的答案内容 (Phase 9 新增).
 *
 * <p>D-03: 字段 = {@code ts} + {@code round} + {@code answer}。
 * {@code round} 为轮次序号（1 或 2），{@code answer} 为该轮 Generator 输出的答案内容。
 */
public record ReflexionAttemptEvent(
    Instant ts,
    int round,
    String answer
) implements AgentEvent {}