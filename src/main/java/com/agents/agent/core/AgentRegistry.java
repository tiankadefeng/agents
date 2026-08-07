package com.agents.agent.core;

import com.agents.api.dto.PatternInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring DI 自动收集所有 {@link AgentPattern} 实现，按 {@code id} 查找。
 *
 * <p>新增模式只需新建 {@code @Component} 类实现 {@link AgentPattern} - {@code AgentRegistry}
 * 通过 Spring DI 自动收集到 {@code List<AgentPattern>}，无需改控制器或注册表代码。
 * 这是 Phase 2 "即插即用" 红利的关键 - Strategy + Plugin Registry 模式落地。
 *
 * <p>D-06 (Claude's Discretion): 构造注入 {@code List<AgentPattern>}，启动期构建
 * 不可变 {@code Map<String, AgentPattern>}，运行期只读。{@code require(String id)}
 * 找不到时抛出 {@link NoSuchPatternException}，由 {@code AgentController} 捕获并返回
 * 404 + {@code ErrorEvent} SSE 帧（Success Criteria #2，D-06 兼容方式）。
 *
 * <p>反模式规避 (RESEARCH.md §反模式 2): 禁止控制器用 {@code switch(patternId)} 硬编码分发 -
 * 必须经 {@code AgentRegistry.require(id)} 查找。本注册表是反模式 2 的解药。
 *
 * <p>T-2-15 (Tampering - Map 构造): {@link Collectors#toUnmodifiableMap} 返回不可变 Map，
 * 运行时无法篡改 patterns 注册表。启动期构建，运行期只读。
 */
@Component
public class AgentRegistry {

    private final Map<String, AgentPattern> patterns;

    /**
     * Spring DI 构造注入 - 自动收集所有 {@link AgentPattern} {@code @Component} 实现。
     *
     * <p>若两个 pattern 返回相同 {@code id()}，{@link Collectors#toUnmodifiableMap}
     * 会抛出 {@code IllegalStateException}（启动期 fail-fast，避免运行期隐藏的 ID 冲突）。
     *
     * @param all Spring DI 注入的 {@code List<AgentPattern>}（可能为空 - Phase 2 默认无模式注册）
     */
    public AgentRegistry(List<AgentPattern> all) {
        this.patterns = all.stream()
            .collect(Collectors.toUnmodifiableMap(AgentPattern::id, Function.identity()));
    }

    /**
     * 按 {@code id} 查找 pattern，找不到时抛出 {@link NoSuchPatternException}。
     *
     * @param id 模式 ID（如 {@code "cot"} / {@code "react"}）
     * @return 对应的 {@link AgentPattern} 实例
     * @throws NoSuchPatternException 当 {@code id} 未注册时
     */
    public AgentPattern require(String id) {
        AgentPattern p = patterns.get(id);
        if (p == null) {
            throw new NoSuchPatternException(id);
        }
        return p;
    }

    /**
     * 列出所有已注册的模式元数据，供 {@code GET /api/patterns} 端点返回。
     *
     * <p>返回顺序由 {@link Map#values()} 迭代顺序决定（{@code toUnmodifiableMap}
     * 返回的 Map 迭代顺序与输入流顺序一致，但不应依赖顺序 - 前端按需排序）。
     *
     * @return 不可变的 {@code List<PatternInfo>}（可能为空 - Phase 2 默认无模式注册，Success Criteria #1）
     */
    public List<PatternInfo> list() {
        return patterns.values().stream()
            .map(p -> new PatternInfo(p.id(), p.displayName(), p.description()))
            .toList();
    }
}
