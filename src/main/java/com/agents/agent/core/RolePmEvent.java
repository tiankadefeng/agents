package com.agents.agent.core;

import java.time.Instant;

/**
 * RolePmEvent - PM（产品经理）角色发言事件 (Phase 10 新增).
 *
 * <p>D-03: 字段 = {@code ts} + {@code round} + {@code role} + {@code content}。
 * {@code round} 为轮次序号（1-5），{@code role} 固定为 "PM"，
 * {@code content} 为角色发言内容。前端按角色渲染彩色头像和发言气泡。
 *
 * <p>Phase 10 Role-playing 模式：PM 提出初始需求并持续追问，
 * 发言内容作为 {@code content} 以完整事件（非流式）发射给前端。
 */
public record RolePmEvent(
    Instant ts,
    int round,
    String role,
    String content
) implements AgentEvent {}
