package com.agents.agent.core;

import java.time.Instant;

/**
 * 错误事件 (含异常 type + message，无 stacktrace)。
 *
 * <p>D-04: 字段 = {@code ts} + {@code message}。
 * D-08 沿用 Phase 1: 错误事件通过 SSE 流回（不返回 HTTP 500），由 {@code GlobalExceptionHandler}
 * 与 {@code PingController.onErrorResume} / {@code AgentController.onErrorResume} 发射。
 *
 * <p>T-2-02: {@code message} 仅含异常 type + 简短描述，不含 stacktrace（V7.1 错误处理 ASVS L1 合规）。
 */
public record ErrorEvent(Instant ts, String message) implements AgentEvent {}
