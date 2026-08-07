package com.agents.agent.core;

import java.time.Instant;

/**
 * CoT 思维链 chunk (Phase 3 用)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code content}。{@code content} 为 LLM 流式输出的推理文本片段，
 * 前端 {@code ReasoningPanel} 累积展示。
 */
public record ReasoningEvent(Instant ts, String content) implements AgentEvent {}
