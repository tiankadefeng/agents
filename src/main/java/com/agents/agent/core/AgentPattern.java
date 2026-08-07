package com.agents.agent.core;

import reactor.core.publisher.Flux;

/**
 * Strategy 契约 - 7 种 agent 模式实现此接口，由 {@code AgentRegistry} 自动收集。
 *
 * <p>新增模式只需新建 {@code @Component} 类实现此接口，{@code AgentRegistry} 通过
 * Spring DI 自动收集到 {@code List<AgentPattern>}，无需改控制器或注册表代码。
 *
 * <p>D-06 (Claude's Discretion): Phase 2 仅锁 4 方法签名。{@code features()} 方法
 * (返回 {@code PatternFeature} 枚举，如 TOOLS / MULTI_STEP) 推迟到 Phase 3+ 按需引入。
 *
 * <p>反模式规避 (RESEARCH.md §反模式 2): 禁止在控制器用 {@code switch(patternId)} 硬编码分发 -
 * 必须经 {@code AgentRegistry.require(id)} 查找。
 */
public interface AgentPattern {

    /**
     * 模式唯一标识符（如 {@code "cot"} / {@code "react"} / {@code "plan-and-execute"}）。
     *
     * <p>用于 {@code POST /api/agent/execute} 请求的 {@code patternId} 字段匹配。
     * 必须唯一，{@code AgentRegistry} 启动期检测重复 ID。
     *
     * @return 模式 ID，永不为 null 或空串
     */
    String id();

    /**
     * 模式中文显示名（如 {@code "CoT 思维链"} / {@code "ReAct 推理+行动"}）。
     *
     * <p>前端 {@code PatternSelector} 组件渲染选项卡标题用。
     *
     * @return 显示名，永不为 null
     */
    String displayName();

    /**
     * 模式描述（一句话说明模式核心思想与典型场景）。
     *
     * <p>前端 {@code PatternSelector} 渲染选项卡副标题 / tooltip 用。
     *
     * @return 描述，永不为 null
     */
    String description();

    /**
     * 执行 agent 模式，返回 {@link AgentEvent} 流（SSE 流式发射）。
     *
     * <p>实现方应返回 cold {@link Flux}（在订阅时才开始执行），不应在方法体内 block。
     * 流末尾通常以 {@link FinalAnswerEvent} 结束，错误以 {@link ErrorEvent} 结束。
     *
     * @param ctx 执行上下文（用户问题 + 可选参数）
     * @return AgentEvent 流，永不为 null
     */
    Flux<AgentEvent> execute(AgentContext ctx);
}
