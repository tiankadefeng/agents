package com.agents.agent.core;

import java.time.Instant;

/**
 * RoleDevEvent - Dev（开发者）角色发言事件 (Phase 10 新增).
 *
 * <p>D-03: 字段 = {@code ts} + {@code round} + {@code role} + {@code content}。
 * {@code round} 为轮次序号（1-5），{@code role} 固定为 "Dev"，
 * {@code content} 为角色发言内容。前端按角色渲染彩色头像和发言气泡。
 *
 * <p>Phase 10 Role-playing 模式：Dev 根据 PM 的需求和 Tester 的反馈
 * 逐步实现和改进技术方案，发言内容以完整事件（非流式）发射给前端。
 */
public record RoleDevEvent(
    Instant ts,
    int round,
    String role,
    String content
) implements AgentEvent {}
