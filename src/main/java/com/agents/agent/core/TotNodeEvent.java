package com.agents.agent.core;

import java.time.Instant;

/**
 * Tree of Thoughts 树节点 (Phase 8 新增).
 *
 * <p>D-03: 字段 = {@code ts} + {@code level} + {@code nodeId} + {@code thought}
 * + {@code score} + {@code parentId}。
 * {@code level=-1} 表示根节点（原始问题，不评分 score=0），{@code level=0..2} 为分支层。
 * {@code nodeId} 为全局唯一自增 ID；{@code parentId} 为父节点 ID，{@code null} 表示根节点。
 * 前端通过 {@code parentId} 重建树结构。
 */
public record TotNodeEvent(
    Instant ts,
    int level,
    int nodeId,
    String thought,
    int score,
    Integer parentId
) implements AgentEvent {}