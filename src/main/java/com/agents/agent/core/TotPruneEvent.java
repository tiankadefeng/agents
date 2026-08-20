package com.agents.agent.core;

import java.time.Instant;
import java.util.List;

/**
 * Tree of Thoughts 剪枝事件 (Phase 8 新增).
 *
 * <p>D-03: 字段 = {@code ts} + {@code level} + {@code prunedNodeIds} + {@code reason}。
 * {@code level} 为被剪枝节点所在层级；{@code prunedNodeIds} 为被剪枝节点 ID 列表
 * （top-K=2 贪心剪枝中评分低于阈值的节点）；{@code reason} 为剪枝原因（评分对比说明）。
 * 前端据此将对应节点标记为灰色/删除线。
 */
public record TotPruneEvent(
    Instant ts,
    int level,
    List<Integer> prunedNodeIds,
    String reason
) implements AgentEvent {}