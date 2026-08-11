package com.agents.tool;

import com.agents.tool.builtin.CalculatorTool;
import com.agents.tool.builtin.TimeTool;
import com.agents.tool.builtin.WeatherTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spring DI 自动收集所有 {@code @Component} 工具类（含 {@code @Tool} 注解方法），
 * 通过 {@link ToolCallbacks#from(Object...)} 反射聚合为统一的 {@code List<ToolCallback>}，
 * 提供 {@code all()} / {@code forPattern(String)} / {@code byName(String)} 三个查询接口。
 *
 * <p>D-01: 使用 {@code ToolCallbacks.from()} 收集 {@code @Tool} 注解方法。每个工具是
 * {@code @Component} 类，ToolRegistry 构造注入 {@code List<Object> toolBeans}，用
 * {@code ToolCallbacks.from(bean)} 反射提取 {@code @Tool} 方法。新增工具只需加
 * {@code @Component} 类，无需改注册表代码 - 与 AgentRegistry 的"即插即用"模式一致。
 *
 * <p>D-08: {@code forPattern(String patternId)} 支持按模式过滤工具。CoT 模式返回空列表
 * （不需要工具），其他模式（如 ReAct）返回全量。Phase 6 Self-Ask 实现时需扩展为
 * {@code Set.of("cot", "self-ask").contains(patternId)}。
 *
 * <p>反模式规避 (RESEARCH.md §反模式 5): 禁止 god controller 模式 - 本注册表只负责
 * 聚合与查找，不负责工具调用逻辑。工具调用由 ToolController 或 Phase 5 ReAct Pattern 发起。
 *
 * <p>T-4-Map (Tampering - Map 构造): {@link Collectors#toUnmodifiableMap} 返回不可变 Map，
 * 运行时无法篡改工具注册表。启动期构建，运行期只读。
 *
 * <p>Phase 11 扩展点: 构造器预留 {@code @Autowired(required = false) List<ToolCallbackProvider> providers}
 * 参数，Phase 11 加 {@code spring-ai-starter-mcp-client} 后自动注入
 * {@code SyncMcpToolCallbackProvider} 实现自动合并 MCP 工具。
 */
@Component
public class ToolRegistry {

    /** Phase 4 内置工具 Bean 列表（通过 {@code ToolCallbacks.from()} 反射提取 {@code @Tool} 方法）。 */
    private static final List<Object> BUILTIN_TOOL_BEANS = List.of(
        new WeatherTool(),
        new CalculatorTool(),
        new TimeTool()
    );

    private final List<ToolCallback> all;

    private final Map<String, ToolCallback> byName;

    /**
     * 聚合内置工具 Bean + Phase 11 MCP 工具提供者。
     *
     * <p>内置工具通过 {@code ToolCallbacks.from(bean)} 反射提取 {@code @Tool} 方法为
     * {@code ToolCallback}。{@code providers} 为 Phase 11 MCP 扩展预留，Phase 4 为空列表。
     *
     * <p>若两个 {@code @Tool} 方法返回相同 {@code name}，{@link Collectors#toUnmodifiableMap}
     * 会抛出 {@code IllegalStateException}（启动期 fail-fast，避免运行期隐藏的工具名冲突）。
     *
     * @param providers Phase 11 MCP 工具提供者（Phase 4 为空，可为 null 安全处理）
     */
    public ToolRegistry(@Autowired(required = false) List<ToolCallbackProvider> providers) {
        // D-01: ToolCallbacks.from(bean) 反射收集 @Tool 方法
        var built = BUILTIN_TOOL_BEANS.stream()
            .flatMap(b -> Arrays.stream(ToolCallbacks.from(b)))
            .toList();

        // A3: 防御性 null 检查 - Spring DI 对 List<T> 注入默认返回空 list，但安全起见加 null 检查
        var external = (providers == null ? Stream.<ToolCallbackProvider>empty() : providers.stream())
            .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
            .toList();

        this.all = Stream.concat(built.stream(), external.stream()).toList();

        // T-4-Map: 不可变 Map，启动期构建，运行期只读
        this.byName = all.stream().collect(
            Collectors.toUnmodifiableMap(
                tc -> tc.getToolDefinition().name(),
                tc -> tc
            )
        );
    }

    /**
     * 返回所有已注册的工具回调列表。
     *
     * @return 不可变的 {@code List<ToolCallback>}（可能为空 - 但 Phase 4 至少含 3 个内置工具）
     */
    public List<ToolCallback> all() {
        return all;
    }

    /**
     * 按模式 ID 返回该模式可用的工具列表。
     *
     * <p>D-08: CoT 模式返回空列表（不需要工具），其他模式（如 ReAct）返回全量。
     * 此方法在 Phase 6 Self-Ask 实现时需扩展过滤逻辑。
     *
     * @param patternId 模式 ID（如 {@code "cot"} / {@code "react"}）
     * @return 该模式可用的 {@code List<ToolCallback>}
     */
    public List<ToolCallback> forPattern(String patternId) {
        if ("cot".equals(patternId)) {
            return List.of();
        }
        return all;
    }

    /**
     * 按工具名称查找对应的 {@link ToolCallback}。
     *
     * <p>O(1) Map 查找，与 AgentRegistry 的 {@code Map<String, AgentPattern>} 模式一致。
     * 找不到时抛出 {@link NoSuchToolException}，由 ToolController 捕获并返回 HTTP 404。
     *
     * @param toolName 工具名称（如 {@code "weather"} / {@code "calculator"} / {@code "time"}）
     * @return 对应的 {@link ToolCallback} 实例
     * @throws NoSuchToolException 当 {@code toolName} 未注册时
     */
    public ToolCallback byName(String toolName) {
        ToolCallback tc = byName.get(toolName);
        if (tc == null) {
            throw new NoSuchToolException(toolName);
        }
        return tc;
    }
}