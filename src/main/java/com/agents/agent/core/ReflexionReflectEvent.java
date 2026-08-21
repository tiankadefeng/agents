package com.agents.agent.core;

import java.time.Instant;

/**
 * ReflexionReflectEvent - Reflexion 反思内容 (Phase 9 新增).
 *
 * <p>D-03: 字段 = {@code ts} + {@code round} + {@code reflection}。
 * {@code reflection} 为 Reflector 生成的改进方向，注入下一轮 Generator prompt。
 */
public record ReflexionReflectEvent(
    Instant ts,
    int round,
    String reflection
) implements AgentEvent {}