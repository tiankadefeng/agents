package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.FinalAnswerEvent;
import org.springframework.boot.test.context.TestConfiguration;
import reactor.core.publisher.Flux;

import java.time.Instant;

/**
 * 临时 mock pattern - 验证 Strategy + Plugin Registry 工作。
 *
 * <p><strong>隔离策略:</strong> 使用 {@code @TestConfiguration}（测试专用注解），不被
 * Spring 的组件扫描机制自动扫描（Spring Boot 测试惯例），仅在被
 * {@code @Import(MockPattern.class)} 显式导入时生效。这区别于生产模式实现（Phase 3+ 真实
 * 模式用 Spring bean 注解自动注册）。
 *
 * <p>这保证默认 {@code @SpringBootTest} 上下文不含 MockPattern - 验证 Success Criteria #1
 * （{@code GET /api/patterns} 返回空列表）；而 {@code @SpringBootTest + @Import(MockPattern.class)}
 * 加载后 MockPattern 自动注册到 {@code AgentRegistry}，{@code GET /api/patterns} 返回
 * {@code [{id:'mock',...}]} - 验证 Success Criteria #4。
 *
 * <p><strong>Success Criteria #4:</strong> 添加此 {@code @TestConfiguration} 后通过
 * {@code @Import} 加载，{@code GET /api/patterns} 自动返回 mock 模式 - 证明 Strategy + Plugin
 * Registry "即插即用" 红利（新增模式只需新建一个 bean，无需改控制器或注册表）。
 *
 * <p>验证后此文件可移除（Phase 3+ 真实模式实现后不再需要 mock）。
 *
 * <p><strong>Rule 1 偏离（包路径修正）:</strong> 计划 Task 3 action 引用
 * {@code com.agents.agent.core.events.FinalAnswerEvent}，但 Plan 02-01 因 Java 21 sealed
 * interface unnamed module 限制将 9 个 record 移至 {@code com.agents.agent.core} 包（非
 * {@code events} 子包）。故此处 import {@code com.agents.agent.core.FinalAnswerEvent}。
 */
@TestConfiguration
public class MockPattern implements AgentPattern {

    @Override
    public String id() {
        return "mock";
    }

    @Override
    public String displayName() {
        return "Mock 模式（验证用）";
    }

    @Override
    public String description() {
        return "临时验证 Strategy + Plugin Registry，验证后移除。";
    }

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.just(
            new FinalAnswerEvent(Instant.now(), "Mock 答案：" + ctx.question())
        );
    }
}
