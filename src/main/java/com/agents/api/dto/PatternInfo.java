package com.agents.api.dto;

/**
 * Pattern metadata returned by {@code GET /api/patterns}.
 *
 * <p>Mirrors {@code frontend/src/types/agent.ts} {@code PatternInfo} (UI-10) -
 * 前端 TypeScript discriminated union 1:1 镜像后端 record 字段。
 *
 * <p>D-06 (Claude's Discretion): Phase 2 仅锁 3 字段 - {@code id} / {@code displayName} /
 * {@code description}。{@code features} 字段（{@code PatternFeature} 枚举，如 TOOLS / MULTI_STEP）
 * 推迟到 Phase 3+ 按需引入 - 当前无模式实现，无法预知所需 UI hints。
 *
 * <p>反模式规避: 不加 Lombok {@code @Data} - Java 21 record 已足够；
 * 不加 JPA {@code @Entity} - PROJECT.md Out of Scope（无 DB）。
 */
public record PatternInfo(String id, String displayName, String description) {}
