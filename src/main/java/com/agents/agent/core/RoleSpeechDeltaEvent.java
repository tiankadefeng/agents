package com.agents.agent.core;

import java.time.Instant;

/**
 * Role-play 角色发言的流式增量（Phase 11 前置 quick-260825-gtx 新增）。
 *
 * <p>临时态事件：LLM 调用期间逐 chunk 发射，(round, role) 为前端气泡分组键。
 * 发言结束后由完整 {@link RolePmEvent} / {@link RoleDevEvent} / {@link RoleTesterEvent}
 * 收口，前端用完整事件替换 delta 拼接出的临时气泡（完整事件是 source of truth）。
 *
 * <p>reasoning_content 在角色发言调用中丢弃（与现状仅取 content 对齐），
 * 仅 content 映射到此事件。
 */
public record RoleSpeechDeltaEvent(Instant ts, int round, String role, String content) implements AgentEvent {}