package com.agents.api.dto;

import java.util.Map;

/**
 * Request body for {@code POST /api/agent/execute}.
 *
 * <p>D-06 (Claude's Discretion): {@code patternId} + {@code question} + 可选 {@code options} Map。
 * {@code options} 为 Phase 3+ 预留参数通道（如 CoT 的 {@code maxSteps}、ReAct 的
 * {@code maxIterations}），Phase 2 不消费此字段。
 *
 * <p>Mirrors {@code frontend/src/types/agent.ts} {@code AgentRequest} (UI-10) -
 * 前端 TypeScript 类型 1:1 镜像后端 record 字段。
 *
 * <p>反模式规避: {@code options} 字段可为 {@code null}（Phase 2 不用），但不加
 * {@code @Nullable} 注解 - Java 21 record + Map 字段允许 null，加注解增加噪音无实际收益。
 * 不加 Lombok {@code @Data} / {@code @Builder} - Java 21 record 已足够；
 * 不加 JPA {@code @Entity} - PROJECT.md Out of Scope（无 DB）。
 */
public record AgentRequest(String patternId, String question, Map<String, Object> options) {}
