package com.agents.api;

import com.agents.api.dto.ToolInfo;
import com.agents.api.dto.ToolInvokeRequest;
import com.agents.tool.NoSuchToolException;
import com.agents.tool.ToolRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * RESTful 测试端点：{@code GET /api/tools}（列表）+ {@code POST /api/tools/{toolName}/invoke}（调用）。
 *
 * <p>D-06: RESTful 风格端点永久保留 - {@code GET /api/tools} 列出工具元数据
 * （name + description + JSON schema），{@code POST /api/tools/{toolName}/invoke}
 * 程序化调用 {@link ToolCallback#call(String)} 执行工具。此端点让工具层可在
 * 不依赖 LLM 的情况下被直接调用验证（SC#3），为 Phase 5 ReAct 铺路（同一调用路径）。
 * 未来可被前端工具调试面板复用。
 *
 * <p>反模式规避 (RESEARCH.md §反模式 5 god controller): 控制器仅做 HTTP &lt;-&gt; 领域转换 -
 * 解析请求、查 {@link ToolRegistry}、序列化 arguments、回流工具结果。工具业务逻辑
 * （mock 天气数据、exp4j 计算、LocalDateTime）封装在工具类内，不写入控制器。
 *
 * <p>T-4-04 (Information Disclosure): 未知 {@code toolName} 由 {@code catch (NoSuchToolException)}
 * 返回 HTTP 404 + {@code {"error":"未知工具：{toolName}"}}，不泄露已注册工具列表
 * （仅 {@code GET /api/tools} 设计意图暴露工具列表）。
 *
 * <p>T-4-05 (Information Disclosure): 工具执行异常由 {@code catch (Exception)} 返回
 * HTTP 500 + {@code {"error":"工具执行失败：" + ex.getMessage()}} - 仅 message，无 stacktrace
 * （ASVS L1 V7.1 合规，遵循 GlobalExceptionHandler T-2-13 模式）。
 *
 * <p>Pitfall 6 (04-RESEARCH.md): {@code ToolCallback.call(String)} 期望方法参数的平铺 JSON
 * （如 {@code {"city":"北京"}}），非嵌套的 {@code {"arguments":{"city":"北京"}}}。本控制器
 * 从 {@link ToolInvokeRequest} 取出 {@code arguments} Map 直接序列化（flat JSON）后传给
 * {@code tc.call(jsonArgs)}。
 *
 * <p>ToolController 是普通 REST 端点（application/json），非 SSE（区别于 AgentController）。
 * 开放端点，无认证（PROJECT.md Out of Scope - 本地教学工具）。
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public ToolController(ToolRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出所有已注册工具的工具元数据（名称 + 描述 + JSON schema）。
     *
     * <p>通过 {@link ToolCallback#getToolDefinition()} 提取元数据，映射为
     * {@link ToolInfo} record。无 {@code produces} - {@code @RestController} 默认将
     * List 序列化为 {@code application/json}（与 PatternController 一致）。
     *
     * @return 不可变的 {@code List<ToolInfo>}（Phase 4 至少含 3 个内置工具）
     */
    @GetMapping
    public List<ToolInfo> list() {
        return registry.all().stream()
            .map(tc -> {
                ToolDefinition def = tc.getToolDefinition();
                return new ToolInfo(def.name(), def.description(), def.inputSchema());
            })
            .toList();
    }

    /**
     * 程序化调用指定工具并返回结果。
     *
     * <p>RESTful 调用端点（D-06）：{@code POST /api/tools/{toolName}/invoke}。
     * 请求体 {@link ToolInvokeRequest} 的 {@code arguments} Map 序列化为平铺 JSON
     * （Pitfall 6），传给 {@link ToolCallback#call(String)} 执行工具。
     *
     * @param toolName 工具名称（如 {@code weather} / {@code calculator} / {@code time}）
     * @param req      请求体（{@code arguments} 参数 Map）
     * @return 200 + 工具 JSON 结果；404 + 未知工具错误；500 + 工具执行失败（仅 message）
     */
    @PostMapping(value = "/{toolName}/invoke", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> invoke(@PathVariable String toolName, @RequestBody ToolInvokeRequest req) {
        ToolCallback tc;
        try {
            tc = registry.byName(toolName);
        } catch (NoSuchToolException ex) {
            // T-4-04: 未知工具返回 404，不泄露工具列表
            return ResponseEntity.status(404)
                .body("{\"error\":\"未知工具：" + toolName + "\"}");
        }
        try {
            // Pitfall 6: 序列化 arguments Map 为平铺 JSON（{"city":"北京"}），非嵌套 {"arguments":...}
            String jsonArgs = objectMapper.writeValueAsString(req.arguments());
            String result = tc.call(jsonArgs);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            // T-4-05: 工具执行失败返回 500 + 仅 message，无 stacktrace
            // 捕获 Exception 而非 RuntimeException：ObjectMapper.writeValueAsString 抛出
            // 受检 JacksonException（extends IOException），RuntimeException 范围不够。
            return ResponseEntity.status(500)
                .body("{\"error\":\"工具执行失败：" + ex.getMessage() + "\"}");
        }
    }
}