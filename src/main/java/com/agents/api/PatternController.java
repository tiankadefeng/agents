package com.agents.api;

import com.agents.agent.core.AgentRegistry;
import com.agents.api.dto.PatternInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /api/patterns} - 返回可用 agent 模式列表。
 *
 * <p>不调用 LLM，仅返回 {@link AgentRegistry#list()}。Phase 2 默认应用上下文无 pattern
 * 注册（{@code MockPattern} 用 {@code @TestConfiguration} 隔离，不被 {@code @ComponentScan}
 * 自动扫描），返回 {@code []} - 验证 Success Criteria #1（端点工作）。
 *
 * <p>D-06 (Claude's Discretion): 不加 {@code produces = MediaType.TEXT_EVENT_STREAM_VALUE} -
 * 此端点返回 JSON 数组（{@code application/json}），非 SSE 流。{@code @RestController}
 * 默认将 List 序列化为 JSON 数组。
 *
 * <p>反模式规避: 控制器仅做 HTTP &lt;-&gt; 领域转换，不构造提示词或调用 ChatClient
 * （RESEARCH.md §反模式 5 god controller 规避）。
 */
@RestController
public class PatternController {

    private final AgentRegistry registry;

    public PatternController(AgentRegistry registry) {
        this.registry = registry;
    }

    /**
     * 列出所有已注册的 agent 模式元数据。
     *
     * <p>Phase 2 默认返回空列表（无 pattern {@code @Component} 注册）- Success Criteria #1。
     * 添加 mock pattern 后（通过 {@code @Import(MockPattern.class)} 在测试中加载）自动返回
     * 该模式 - Success Criteria #4。
     *
     * @return 不可变的 {@code List<PatternInfo>}（可能为空）
     */
    @GetMapping("/api/patterns")
    public List<PatternInfo> list() {
        return registry.list();
    }
}
