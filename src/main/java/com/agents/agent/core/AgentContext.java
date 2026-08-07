package com.agents.agent.core;

import java.util.Map;

/**
 * Context passed to {@link AgentPattern#execute(AgentContext)}.
 *
 * <p>D-06: Phase 2 锁定最小字段 - {@code question} + {@code options}。
 * {@code options} 为 Phase 3+ 预留参数通道（如 CoT 的 {@code maxSteps}、ReAct 的
 * {@code maxIterations} 等），Phase 2 不消费此字段。
 *
 * <p>反模式规避: 不加 Lombok {@code @Data} / {@code @Builder} - Java 21 record 已足够；
 * 不加 JPA {@code @Entity} - PROJECT.md Out of Scope（无 DB）。
 */
public record AgentContext(String question, Map<String, Object> options) {}
