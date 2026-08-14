package com.agents.agent.core;

import java.time.Instant;

/**
 * Self-Ask 子答案 (Phase 6 新增)。
 *
 * <p>D-01: 与 {@link SubQuestionEvent} 成对出现。字段 = {@code ts} + {@code question} + {@code answer}。
 * LLM 解析 JSON 后，对每个子问题依次发射 SubQuestionEvent -> SubAnswerEvent，前端按顺序展示。
 */
public record SubAnswerEvent(Instant ts, String question, String answer) implements AgentEvent {}