package com.agents.agent.core;

import java.time.Instant;

/**
 * Self-Ask 子问题 (Phase 6 用)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code question}。LLM 将大问题拆解为子问题后发射此事件，
 * 前端按子问题顺序展示多跳事实检索过程。
 */
public record SubQuestionEvent(Instant ts, String question) implements AgentEvent {}
