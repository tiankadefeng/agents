package com.agents.agent.core;

import java.time.Instant;

/**
 * 最终答案 (所有模式末尾)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code content}。{@code content} 为 LLM 流式输出的答案文本片段，
 * 前端 {@code FinalAnswer} 组件累积展示。Phase 2 的 {@code /api/ping} 端点会发射此事件。
 */
public record FinalAnswerEvent(Instant ts, String content) implements AgentEvent {}
