package com.agents.agent.core;

import java.time.Instant;

/**
 * Sealed event hierarchy - 12 record 子类型，pattern matching switch 保证编译时穷尽。
 *
 * <p>D-02: 公共字段最小化 - 仅 {@link #ts()}。不加模式 ID 字段（前端从请求上下文已知）、
 * 不加步骤序号字段（Phase 3 CoT 只有 1 步；多步模式在 record 内部加步骤号字段，
 * 如 {@link StepStartEvent}）。
 *
 * <p>D-01: SSE 帧的 {@code event} 字段使用 {@code ev.getClass().getSimpleName()}（如
 * {@code ToolCallEvent}），{@code data} 字段为该 record 的 JSON 序列化（不含 {@code type} 字段）。
 * 由 {@code SseEventEmitter.fromAgentEvent(AgentEvent)} 在序列化层处理，无需 Jackson 多态注解。
 *
 * <p>D-04: 12 个 record 字段锁定见各 CONTEXT.md 表格。{@code PlanEvent.description} 与
 * {@code StepCompleteEvent.status} 为占位字段，Phase 6/7 实现模式时再补全。
 * {@code TotNodeEvent} / {@code TotPruneEvent} 为 Phase 8 新增（树节点语义）。
 *
 * <p><strong>包结构说明 (Rule 1 deviation):</strong> 计划原定 9 个 record 放在
 * {@code com.agents.agent.core.events} 子包，但 Java 21 sealed interface 在 unnamed module 中
 * 要求所有 permitted subtypes 必须与 sealed interface 同包。本项目未启用 named module
 * (无 module-info.java)，故将 9 个 record 移至 {@code com.agents.agent.core} 包。
 * 详见 SUMMARY.md "Deviations from Plan"。
 */
public sealed interface AgentEvent
    permits ReasoningEvent,
            ToolCallEvent,
            ToolResultEvent,
            SubQuestionEvent,
            SubAnswerEvent, // Phase 6 新增
            PlanEvent,
            StepStartEvent,
            StepCompleteEvent,
            TotNodeEvent,      // Phase 8 新增
            TotPruneEvent,     // Phase 8 新增
            FinalAnswerEvent,
            ErrorEvent {

    /**
     * 事件时间戳，前端可按 ts 排序（教学价值 - 看事件发生顺序）。
     *
     * @return 事件发生的瞬时时间，永不为 null
     */
    Instant ts();
}
