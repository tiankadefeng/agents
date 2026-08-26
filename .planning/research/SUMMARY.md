# 项目研究总结

**Project:** Agent 设计模式教学案例库 (Agent Design Patterns Teaching Library)
**Domain:** 教学 / 开发者教育 - 7 种 agent 设计模式，配可运行示例
**Researched:** 2026-08-04
**Confidence:** HIGH（技术栈版本已通过 Maven Central + npm 验证；API 已通过 Context7 核对 Spring AI 2.0 参考文档；陷阱已通过 DeepSeek API 文档和 WHATWG SSE 规范验证）

## 执行摘要

这是一个全新构建的教学平台，基于 **Spring Boot 4.1.0 + Spring AI 2.0.0 + Java 21 + Vue 3.5.40 + Element Plus 2.14.3 + Vite 8.2.0**。项目的核心教学价值在于通过 SSE 实时将 agent 推理轨迹流式推送到浏览器，让学习者可以观察 7 种模式（CoT、Self-Ask、ReAct、Plan-and-Execute、ToT、Reflexion、Role-playing）如何一步步"思考"。架构采用 Strategy + Plugin Registry：每个模式是一个实现统一 `AgentPattern` 接口的 `@Component`，由 Spring 自动收集到 `AgentRegistry` 中。新增一个模式只需单文件操作，无需修改 controller - 这是教学代码库的不可妥协属性，因为学习者会逐行阅读每一行代码。

推荐方案使用 Spring MVC（而非 WebFlux）返回响应式 `Flux<ServerSentEvent>` 进行流式传输，使用**原生** `spring-ai-starter-model-deepseek` starter（而非 OpenAI 兼容 shim - 这对访问 DeepSeek 的 `reasoning_content` 字段至关重要，CoT/Reflexion 模式依赖此字段），以及密封的 `AgentEvent` 层次结构，前端用 TypeScript 可辨识联合类型镜像。前端必须使用 `fetch + ReadableStream`（而非 `EventSource`）消费 SSE，因为 agent 端点需要 `POST` + JSON body - `EventSource` 仅支持 GET。

主要风险集中在三个方面：(1) **Spring AI 版本碎片化** - 该框架经历了三次破坏性 API 整理（1.0 -> 1.1 -> 2.0），多数中文教程引用的是 GA 之前的 API，会静默失败；(2) **SSE 缓冲**存在于多个层面（Spring MVC 默认缓冲、Nginx `proxy_buffering`、浏览器 fetch 分块）- 必须在 Phase 1 端到端验证；(3) **每种模式特有的失败模式**会破坏教学目的 - ReAct 无 `max_iterations` 导致死循环、ToT 不剪枝导致分支爆炸、Reflexion 自评估循环卡住。这三点都可通过显式的 phase 级验证来解决。

## 关键发现

1. **Spring AI 2.0.0 是 2026 年全新项目的合理底座**（HIGH 置信度，Maven Central 已验证）。这是 DeepSeek、MCP、tool calling、structured output 首次同时达到 API 成熟度的稳定版本。使用 BOM（`spring-ai-bom:2.0.0`）是必须的 - 混用模块版本会触发静默失败。与 Spring Boot 4.0.x 和 4.1.x 兼容；统一使用 4.1.0。

2. **原生 DeepSeek starter 不可妥协**（HIGH 置信度）。`spring-ai-starter-model-deepseek` 暴露的 `DeepSeekChatModel` 提供原生 `reasoning_content` 访问。OpenAI 兼容 shim 对普通聊天可用，但会丢失推理轨迹字段 - 摧毁 CoT/Reflexion 的教学价值。`getReasoningContent()` 非空检查是 Phase 1 的验收标准。

3. **`EventSource` 不能用于 SSE 消费**（HIGH 置信度，MDN 已验证）。Agent 端点需要 `POST` + JSON body；`EventSource` 仅支持 GET，无法自定义请求头。使用 `fetch()` + `response.body.getReader()` + 手动 SSE 帧解析。集中在 `composables/useSSEStream.ts`。

4. **Strategy + Plugin Registry 是 7 个可互换模式的唯一合理架构**（HIGH 置信度）。`AgentPattern` 接口 + 每个模式一个 `@Component` + `AgentRegistry` 通过 Spring DI 收集 `List<AgentPattern>`。Controller 永远不对 patternId 做 `if/else` 判断。新增 Pattern 8 = 一个新的 `@Component` 类，零修改现有代码。

5. **ReAct 教学目的需要手动 tool-call 循环**（HIGH 置信度）。自动 `ToolCallingAdvisor` 会隐藏 Think -> Act -> Observe 循环。对于 ReAct/Plan-and-Execute，通过 `toolCallingAdvisorAutoRegister(false)` 禁用自动注册，使用 `ToolCallingManager` + `MessageAggregator` 手动驱动循环。Role-playing 用自动 advisor 即可。

6. **每种模式都有特定的失败模式，必须在其实现阶段防御**（HIGH 置信度）。ReAct -> 无 `max_iterations=8-10` 和工具调用去重会死循环。Plan-and-Execute -> 无结构化 JSON 和失败重规划会导致计划漂移。ToT -> 无 `branching=2-3, depth=3, top-K=2` 剪枝会分支爆炸。Reflexion -> 无 `max_reflections=2-3` 和 improvement-epsilon 停止会导致自评估卡住。

7. **教学项目过度工程化本身就是最高优先级的陷阱**（HIGH 置信度）。每个模式的核心逻辑必须可在单文件中阅读（< 200 行）。不要 `AgentFactory`，不要 `@Agent` AOP 魔法，不要强制统一框架。CoT 字面上就是调用 `chatModel.stream(prompt)`。代码即教程，不是代码即框架。

## 推荐技术栈

### 核心技术

| Technology | Version | Purpose | Notes |
|------------|---------|---------|-------|
| JDK | Java 21 (LTS) | 运行时 + 编译目标 | 已在 pom.xml 中。LTS 至 2028 年 9 月。 |
| Spring Boot | 4.1.0 | 应用框架，内嵌 Tomcat | 最新 GA 线；Spring AI 2.0.x 必需。 |
| Spring AI | 2.0.0 (BOM) | Agent 框架 | 首个 GA，成熟的 tool/MCP/DeepSeek API。使用 BOM。 |
| Spring AI DeepSeek starter | `spring-ai-starter-model-deepseek` | 原生 DeepSeek 集成 | 不是 OpenAI shim。暴露 `reasoning_content`。 |
| Spring AI MCP client starter | `spring-ai-starter-mcp-client` | MCP 工具服务器 | 可选 - 仅 Phase 7+ 需要。 |
| Vue | 3.5.40 | 前端（Composition API） | `<script setup lang="ts">` 默认。 |
| Element Plus | 2.14.3 | UI 组件 | 中文 Vue 3 UI 的标准选择。 |
| Vite | 8.2.0 | 开发服务器 + 打包器 | `@vitejs/plugin-vue@6.0.8` peer 支持 Vite 8。 |
| @vitejs/plugin-vue | 6.0.8 | Vue 3 SFC 编译 | `.vue` 文件必需。 |
| Maven | 3.9+ | 后端构建 | 已选定。 |
| Node.js | 22 LTS | 前端运行时 | Vite 8.x 必需。 |

### 支持库

- `spring-boot-starter-web` 4.1.0（通过 parent） - servlet 栈，MVC + 响应式返回类型用于 SSE。**不要添加 `spring-boot-starter-webflux`**。
- `@element-plus/icons-vue` 2.3.2 - 图标集。
- `reactor-core` - 通过 Spring AI 传递依赖。提供 `Flux`/`Mono`。
- TypeScript ^5.6+ - 前端类型安全，镜像后端密封事件层次结构。
- `axios` ^1.7+（可选） - 用于非流式 REST 调用。SSE 使用原生 `fetch`。

### 冲突解决（跨文件）

**冲突 1 - DeepSeek 模型名称：**
- STACK.md：`deepseek-chat`/`deepseek-reasoner` 为 HIGH；`deepseek-v4-pro`/`deepseek-v4-flash` 为 MEDIUM。
- PITFALLS.md：v4-pro/flash 为 HIGH（声称直接从 api-docs.deepseek.com 获取）。
- **解决：** 视为开放问题。默认使用 `deepseek-chat`/`deepseek-reasoner`。若账户有 V4 访问权限则切换。在 Phase 1 验证。

**冲突 2 - SSE controller 方案：**
- STACK.md：首选 = `Flux<ServerSentEvent<String>>`（MVC 响应式返回类型，无 WebFlux，无 SseEmitter 样板）。
- ARCHITECTURE.md：首选 = `SseEmitter`。
- **解决：** 使用 `Flux<ServerSentEvent<String>>`（STACK.md 胜出）。仅当后续需要事件 ID/retry 提示时使用 SseEmitter。

**冲突 3 - MCP artifact 坐标：**
- ARCHITECTURE.md：使用遗留的 `spring-ai-mcp-client-spring-boot-starter`（2.0 之前）。
- STACK.md：使用 `spring-ai-starter-mcp-client`（2.0+ 重命名，已验证）。
- **解决：** 使用 `spring-ai-starter-mcp-client`（STACK.md 胜出）。

**冲突 4 - Spring AI 版本基线：**
- ARCHITECTURE.md：基于 Spring AI 1.0.x 文档撰写；部分 API 引用可能带有 1.0 风格。
- STACK.md + PITFALLS.md：两者都锁定 2.0.0。
- **解决：** 目标 2.0.0。在 Phase 5 规划期间重新验证确切类名（`MessageAggregator` vs `ChatClientMessageAggregator`）。

### 不应使用

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| LangChain4j | PROJECT.md 强制使用 Spring AI。 | Spring AI 2.0.0 |
| `spring-boot-starter-webflux`（作为主栈） | 与 MVC 冲突；非必要。 | `spring-boot-starter-web` + `Flux<ServerSentEvent>` |
| OpenAI 兼容的 DeepSeek | 丢失 `reasoning_content`。 | `spring-ai-starter-model-deepseek` |
| 任何数据库 | PROJECT.md："对话无需保存"。 | 内存版 `MessageWindowChatMemory` |
| Spring Security | PROJECT.md："教学演示用途"。 | 无 |
| WebSocket | 双向开销过大。 | SSE |
| `PromptChatMemoryAdvisor` | Spring AI 2.0 中已移除。 | `MessageChatMemoryAdvisor` |
| 旧 artifact ID | 2.0 中已重命名；无法解析。 | 新的 `spring-ai-starter-model-*`，`org.springframework.ai` group |
| Vue 2 / Options API / Webpack | 已 EOL 或被取代。 | Vue 3.5 Composition API + Vite 8 |

## 基础功能

### 发现与选择
- 7 模式选择器（LOW）
- 模式描述卡片（LOW）
- 模式对比矩阵（LOW）

### 输入与输出
- 问题输入 + 提交 + 清空（LOW）
- 预设示例问题 - 每个模式 3-5 个（LOW）
- 最终答案展示，与推理轨迹区分（LOW）
- 中文 UI（LOW）

### 流式传输与可视化（核心教学价值）
- SSE 流式返回 - `fetch + ReadableStream` 客户端 + `Flux<ServerSentEvent>` 服务器端（MEDIUM）
- 模式特定的推理可视化（HIGH）：
  - CoT：线性文本流
  - Self-Ask：子问题 -> 子答案链时间轴
  - ReAct：Thought / Action / Observation 块
  - Plan-and-Execute：带步骤状态的清单
  - ToT：带节点分数 + 剪枝分支的树
  - Reflexion：尝试 -> 评估 -> 反思循环时间轴
  - Role-playing：带彩色头像的多角色聊天

### 模式实现
- 7 个模式每个都最小化实现（整体 HIGH）
- 每个模式的系统 prompt，前端可见（LOW）
- ReAct 内置工具 - 天气（mock）、计算器（真实）、时间（MEDIUM）
- 每个模式的错误处理 - LLM/SSE/工具失败优雅降级（MEDIUM）

### 差异化特性（P2 - v1 之后）
- 跨模式对比（并行 SSE，并排展示） - 教学杀手级特性
- 参数配置（通过 `ChatOptions` 配置 temperature/maxTokens/model）
- Token 用量 + 每步耗时统计
- MCP 外部工具配置 UI
- 工具调用详情展开（ReAct）
- 每个模式的源码查看链接
- Reflexion 评估器可视化
- ToT 剪枝高亮

### 反特性（不在范围内）
用户登录、数据库持久化、生产部署、移动端响应式、多 LLM 切换、拖拽工具构建器、RAG、自定义 orchestrator、聊天历史导出、真实外部 API、i18n、暗色模式、实时多用户协作、用户反馈系统。

## 架构决策

### 决策 1：Strategy + Spring Plugin Registry（不可妥协）

`AgentPattern` 接口 + 每个模式一个 `@Component` + `AgentRegistry` 通过 Spring DI 收集 `List<AgentPattern>`。Controller 调用 `registry.require(patternId).execute(ctx)` - 永不 `if/else`。**新增一个模式 = 一个新的 `@Component` 类，零修改现有代码。**

### 决策 2：密封事件层次结构 + TypeScript 可辨识联合

`AgentEvent` 是 Java `sealed interface`，配 `record` 子类型。模式发射强类型事件；一个 `SseEventEmitter` helper 统一序列化。前端用 TypeScript 可辨识联合镜像。两种语言的穷举 `switch` 在编译时捕获缺失的处理器。

事件：`ReasoningEvent`、`ToolCallEvent`、`ToolResultEvent`、`SubQuestionEvent`、`PlanEvent`、`StepStartEvent`、`StepCompleteEvent`、`FinalAnswerEvent`、`ErrorEvent`。

### 决策 3：ToolRegistry 合并内置 + MCP

单个 `ToolRegistry` bean 从 (a) `@Tool` 注解方法（内置）和 (b) 任何 `ToolCallbackProvider` bean（MCP）组装 `List<ToolCallback>`。模式向 registry 询问工具，从不关心"工具来自哪里"。模式声明 `features()`（如 `TOOLS`）；registry 过滤 - CoT/Self-Ask 获得空列表。

### 决策 4：教学型模式的手动 Tool-Call 循环

对于 ReAct 和 Plan-and-Execute，**禁用** `ToolCallingAdvisor` 自动注册（`toolCallingAdvisorAutoRegister(false)`），使用 `ToolCallingManager` + `MessageAggregator` 手动驱动循环。每次迭代发射 `ReasoningEvent` -> `ToolCallEvent` -> `ToolResultEvent`。Role-playing 用自动 advisor 即可。

### 决策 5：前端 SSE 通过 `fetch + ReadableStream`（非 EventSource）

集中在 `composables/useSSEStream.ts`。手动 SSE 帧解析。无自动重连（agent 执行有状态）。模式切换时 `onBeforeUnmount(() => controller.abort())`。Vue 3 响应式：累积流式文本用 `shallowRef<string>`（不用 `ref<string>` + `+=`）。

### 项目结构（摘要）

**后端**（`src/main/java/com/atguigu/gulimall/agents/`）：
- `config/` - ChatClientConfig, McpClientConfig（Phase 7）, WebConfig（CORS）
- `api/` - PatternController, AgentController, dto/, GlobalExceptionHandler
- `agent/core/` - AgentPattern, AgentContext, AgentEvent（sealed）, events/, AgentRegistry
- `agent/patterns/<name>/` - 每个模式一个包
- `tool/` - ToolRegistry, builtin/, mcp/
- `streaming/` - SseEventEmitter

**前端**（`frontend/src/`）：
- `api/` - client.ts, patterns.ts, agent.ts（返回 ReadableStream）
- `composables/` - useSSEStream.ts, useAgentSession.ts
- `stores/agentSession.ts` - Pinia
- `views/` - HomeView，可选 PatternDetailView
- `components/` - PatternSelector, ChatPanel, MessageBubble, ReasoningTrace, events/*EventCard.vue
- `types/agent.ts` - 镜像后端 DTO

## 注意事项

按严重程度和影响阶段排序的顶级陷阱。

### 1. Spring AI 版本碎片化（CRITICAL - Phase 1）

三次破坏性 API 整理（1.0 -> 1.1 -> 2.0）。旧教程引用 GA 之前的 API，会**静默失败** - 工具回调注册但从不调用，无异常。`internalToolExecutionEnabled` 已移除，`.functions()` -> `.tools()`，`.options()` 需要 Builder 而非已构建实例，MCP groupId 已迁移。

**预防：** 锁定 `spring-ai-bom:2.0.0`。以升级说明为权威。不要复制任何超过 6 个月的博客代码。CI enforcer 规则保证版本一致性。

### 2. 错误的 DeepSeek 集成方式（CRITICAL - Phase 1）

OpenAI 兼容 shim 丢失 `reasoning_content`。CoT/Reflexion 教学价值依赖推理轨迹。

**预防：** 使用 `spring-ai-starter-model-deepseek`。Phase 1 验收：断言 `deepseek-reasoner` 的 `getReasoningContent()` 非空。

### 3. 多层 SSE 缓冲（CRITICAL - Phase 1）

症状：整包响应一次性返回，或每 N 秒返回一块，或本地正常但在 Nginx 后卡住。

**预防：** Controller `produces = MediaType.TEXT_EVENT_STREAM_VALUE`。配置 `server.compression.enabled=false`，`spring.mvc.async.request-timeout=-1`。Nginx `proxy_buffering off` + `X-Accel-Buffering: no`。Phase 1 验收：DevTools EventStream 逐个显示事件。

### 4. EventSource 限制（CRITICAL - Phase 1）

仅支持 GET，无自定义请求头，自动重连（抖动时重复输出）。

**预防：** `fetch + ReadableStream`。手动 SSE 帧解析。无自动重连。模式切换时 `onBeforeUnmount(() => controller.abort())`。CORS：显式 origin。

### 5. ReAct 死循环（HIGH - ReAct 阶段）

无 `max_iterations` 和工具调用去重，agent 在常见 prompt 上永远循环。

**预防：** 硬性 `max_iterations=8-10`。系统 prompt 含显式收敛条件。工具异常包装为 observation。去重 set：相同 tool+args -> "使用之前的结果"。Temperature 0.3-0.5。

### 6. 各模式失败模式（HIGH - 各模式阶段）

- **Plan-and-Execute：** 计划漂移。预防：结构化 JSON 计划，3-7 步，失败重规划，计划验证。
- **ToT：** 分支爆炸。预防：`branching=2-3, depth=3, top-K=2`，提前停止，共享增量上下文，成本显示。
- **Reflexion：** 自评估卡住。预防：`max_reflections=2-3`，结构化反思 JSON，最近 2 次的记忆窗口，improvement-epsilon 停止。

### 7. 教学项目过度工程化（HIGH - 所有阶段）

症状：只有一个实现的接口，强加于不适配模式上的 "Agent" 基类，`@Agent` AOP 魔法，每个模式 5+ 个类。

**预防：** 每个模式一个包。每个模式的核心逻辑 < 200 行单文件。优先显式调用而非反射。除非那正是教学点，否则不要生产级特性。

### 8. 流式 + 工具调用分块（MEDIUM - Phase 1 schema，ReAct 实现）

DeepSeek 流式将单个工具调用的参数 JSON 拆分到多个 chunk。

**预防：** 使用 Spring AI 的 `MessageAggregator`（或 `ChatClientMessageAggregator` - 在 Phase 5 规划期间验证 2.0 确切类名）。清晰的 SSE 事件 schema。前端按 `event.type` 路由。

### 9. DeepSeek 限流 + keep-alive（MEDIUM - Phase 1 配置）

并发用户触发 HTTP 429。长推理会收到 `: keep-alive` SSE 注释，破坏简单 JSON 解析器。

**预防：** `spring.ai.retry.max-attempts=3`，`backoff-interval=2000ms`。SSE 解析器跳过以 `:` 开头的行。每会话 `user_id` 通过 `extra_body`。`spring.ai.deepseek.timeout=600000`。友好的 429 提示。

### 10. Vue 3 响应式性能（MEDIUM - 所有流式阶段）

`ref<string>` + `+= chunk` 每个 token 触发完整重渲染；超过 5k 字符后 CPU 饱和。

**预防：** `shallowRef<string>` 或 `ref<string[]>` push + `join('')`。若 chunk 频率 > 50/秒，用 `requestAnimationFrame` 批量更新。

## 构建顺序建议

### Phase 1：骨架（DeepSeek + Vue + SSE 管道）

**理由：** 下游一切都依赖 DeepSeek 端到端可用。

**交付：**
- Spring Boot 应用以 `spring-ai-starter-model-deepseek` 启动
- `application.yml` 配置 `spring.ai.deepseek.api-key: ${DEEPSEEK_API_KEY}` + 选定模型
- `ChatClientConfig` 产出 `ChatClient` bean
- `GET /api/ping` 验证 DeepSeek 连通性
- Vue 3 + Vite + Element Plus 应用调用 `/api/ping`
- `composables/useSSEStream.ts` 接入 `/api/ping-stream` 测试端点
- CORS 配置允许 `http://localhost:5173`

**验收：**
- DevTools EventStream 逐个显示事件
- `deepseek-reasoner` 的 `getReasoningContent()` 非空
- 429 自动重试测试通过
- 代码库中无 `EventSource`

**规避：** 陷阱 1、2、3、4、11、12。

**研究标记：** LOW 风险。作为验收的一部分验证 DeepSeek 模型可用性。

### Phase 2：Agent 抽象（契约 + registry，无模式）

**理由：** 契约存在前无法添加模式。一次构建的基础设施。

**交付：**
- `AgentPattern` 接口
- `AgentEvent` sealed interface + 9 个 record 子类型
- `AgentRegistry`（目前为空 `List<AgentPattern>`）
- `PatternController` 返回空列表
- `AgentController` 含 `POST /api/agent/execute`（未找到返回 404）
- `SseEventEmitter` helper
- 前端 `types/agent.ts` 镜像 DTO

**规避：** 反模式 2（if/else 分发）、3（无类型事件）、5（god controller）。

**研究标记：** LOW 风险。

### Phase 3：首个模式 - CoT（最简单，无工具）

**理由：** 无工具、无循环、无结构化输出。最小的端到端验证。

**交付：**
- `CoTPattern` `@Component` - 单次 `chatClient.prompt().user(q).stream()` 调用
- 每个 chunk 作为 `ReasoningEvent`；最后作为 `FinalAnswerEvent`
- 前端 `PatternSelector`、`ChatPanel`、`ReasoningTrace` 含 `ReasoningEventCard` + `FinalAnswerEventCard`

**规避：** 陷阱 9（过度工程化） - CoT < 100 行，无 Agent 基类。

**研究标记：** LOW 风险。

### Phase 4：工具层（仅内置，无 MCP）

**理由：** ReAct 依赖工具。先构建依赖。

**交付：**
- `ToolRegistry`（不含 MCP 注入）
- `WeatherTool`、`CalculatorTool`、`TimeTool` 作为 `@Component` 类，含 `@Tool` 方法
- 测试端点验证直接工具调用

**规避：** 陷阱 1（使用新 `@Tool` API，非旧 `.functions()`）。

**研究标记：** LOW 风险。对照 Spring AI 2.0 验证 `@Tool` 注解确切签名。

### Phase 5：ReAct 模式（最高风险，验证工具层 + 手动循环）

**理由：** 首个使用工具和手动 `ToolCallingManager` 循环的模式。最高风险 - 尽早暴露 bug。

**交付：**
- `ReActPattern` 含手动工具调用循环（`toolCallingAdvisorAutoRegister(false)`，`ToolCallingManager`，`MessageAggregator`）
- 前端 `ToolCallEventCard`、`ToolResultEventCard`
- `max_iterations=8-10` 强制执行
- 工具调用去重 set
- 系统 prompt 含显式收敛条件

**验收：**
- 诱导循环的 prompt 在 10 次迭代内停止
- 前端正确解析多 chunk 参数的 `ToolCallEvent`
- 相同 tool+args 返回"使用之前的结果"

**规避：** 陷阱 5（ReAct 循环）、11（流式分块）。

**研究标记：** MEDIUM 风险。Spring AI 2.0 手动工具调用循环 API 需要验证。`MessageAggregator` vs `ChatClientMessageAggregator`。**建议 `/gsd:plan-phase --research-phase 5`。**

### Phase 6：剩余模式（可并行）

每个独立。此时契约已验证。

**6a：Self-Ask** - 结构化 Follow-up/Answer/Final 解析，无工具。`SubQuestionEvent`。（LOW 风险）

**6b：Plan-and-Execute** - 两阶段（Planner + 逐步 Executor）。`PlanEvent`、`StepStartEvent`、`StepCompleteEvent`。JSON 计划验证，失败重规划，3-7 步。（LOW 风险）

**6c：Tree of Thoughts** - 分支生成 + LLM 评估 + 贪心选择。`branching=2-3, depth=3, top-K=2`。树可视化。最高复杂度（3-5 天）。（MEDIUM 风险 - 搜索算法选择，树可视化方案。建议 `/gsd:plan-phase --research-phase 6c`。）

**6d：Reflexion** - 生成器 + LLM-as-judge 评估器 + 反思器 + 重试。`max_reflections=2-3`。结构化反思 JSON。最近 2 次的记忆窗口。用 `deepseek-reasoner` 获得自然反思轨迹。（MEDIUM 风险 - LLM-as-judge prompt 设计。建议 `/gsd:plan-phase --research-phase 6d`。）

**6e：Role-playing** - 3 个固定角色（PM/Dev/Tester），固定 5 轮顺序。每个角色独立系统 prompt + 彩色头像。自动 `ToolCallingAdvisor` 可用。（LOW 风险）

**规避：** 陷阱 6、7、8。

### Phase 7：MCP 集成（可选，扩展性）

**理由：** MCP 是扩展性故事，非核心教学。内置工具已演示工具使用。

**交付：**
- `spring-ai-starter-mcp-client` 依赖
- `application.yml` 中一个演示 MCP 服务器
- 若需要自定义处理器则 `McpClientConfig`
- `ToolRegistry` 已注入 `ToolCallbackProvider` - 无模式代码修改
- 前端 `ToolCallEventCard` 显示工具来源徽章
- `/api/mcp/tools` 端点列出已注册工具

**规避：** 陷阱 10（使用 `org.springframework.ai` group，Streamable HTTP transport，`request-timeout=30s`）。

**研究标记：** MEDIUM 风险。transport 选择和确切 2.0 配置键需要验证。建议 `/gsd:plan-phase --research-phase 7`。

### Phase 8：差异化特性（v1 之后，可并行）

- 跨模式对比（并行 SSE 通道，并排展示） - 杀手级特性
- 参数配置（temperature/maxTokens/model）
- Token 用量 + 每步耗时统计
- 工具调用详情展开
- 源码查看链接
- Reflexion 评估器可视化
- ToT 剪枝高亮

**研究标记：** 大部分 LOW 风险。跨模式对比 MEDIUM（并行 SSE - 浏览器连接限制，竞态条件）。

### 阶段排序理由

- Phase 1 优先：其他所有阶段都依赖 DeepSeek + SSE 端到端工作
- Phase 2 第二：契约存在前无法添加模式
- Phase 3（CoT）在 Phase 4（Tools）之前：CoT 无工具，最小端到端验证
- Phase 4（Tools）在 Phase 5（ReAct）之前：ReAct 依赖工具
- Phase 5（ReAct）在 Phase 6 之前：最高风险模式，尽早暴露 bug
- Phase 6 可并行：契约验证后每个独立
- Phase 7（MCP）最后：扩展性，非核心教学
- Phase 8 v1 之后：跨模式对比依赖全部 7 个模式稳定

### 研究标记总结

**需要更深入研究：**
- Phase 5（ReAct）：Spring AI 2.0 手动工具调用循环 API
- Phase 6c（ToT）：搜索算法，树可视化
- Phase 6d（Reflexion）：LLM-as-judge prompt 设计
- Phase 7（MCP）：transport 选择，2.0 配置键

**标准模式（跳过研究）：**
- Phase 1、2、3、4、6a、6b、6e

## 置信度评估

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Maven Central + npm 已验证；Spring AI 2.0 参考已通过 Context7 验证。 |
| Features | HIGH | PROJECT.md + Spring AI 文档 + 论文共识。 |
| Architecture | HIGH | Spring 风格。注意：ARCHITECTURE.md 基于 1.0.x - 部分类名可能在 2.0 中已变化。 |
| Pitfalls | HIGH | 已对照 Spring AI 文档、DeepSeek API 文档、WHATWG SSE 规范、MDN 验证。 |

**整体置信度：** HIGH。主要剩余不确定性：Spring AI 2.0 手动工具调用循环的确切 API 表面（Phase 5） - 因此设置研究标记。

### 待解决的 Gap

- **DeepSeek 模型可用性：** v4-pro/flash 在 Spring AI 2.0 enum 中，但账户可用性未验证。Phase 1：试 v4-pro，回退到 `deepseek-chat`。Reflexion 无论何情况都用 `deepseek-reasoner`。
- **DeepSeek 课堂规模限流：** 免费层对单学习者可能够用；课堂（10-50）未验证。缓解：每会话 `user_id`，带 backoff 重试。
- **Spring AI 2.0 手动工具循环 API：** `MessageAggregator` vs `ChatClientMessageAggregator`。Phase 5 规划期间通过 Context7 重新验证。
- **MCP transport 选择：** STDIO vs SSE vs Streamable HTTP。PITFALLS.md 推荐教学用 Streamable HTTP。Phase 7 验证。
- **工具名冲突策略：** 除非 Phase 7 出现真实冲突，否则推迟。仅在需要时加 `builtin:`/`mcp:<server>:` 前缀。
- **ToT 搜索算法：** v1 贪心；若贪心效果差可能需要升级到 BFS。
- **跨模式 SSE 浏览器限制：** Chrome 每 origin 6 个 - 3 个并行 OK，多 tab 可能触顶。Phase 8 设计需考虑此点。

## 来源

### 主要（HIGH 置信度）

- **Maven Central `maven-metadata.xml`** - `spring-ai-bom:2.0.0`（2026-06-12）、`spring-boot-starter-parent:4.1.0`、`spring-ai-starter-model-deepseek:2.0.0`。
- **npm registry** - `vue@3.5.40`、`element-plus@2.14.3`、`vite@8.2.0`、`@vitejs/plugin-vue@6.0.8`、`@element-plus/icons-vue@2.3.2`。
- **Context7 `/websites/spring_io_spring-ai_reference`** - Spring AI 2.0 兼容性、BOM、DeepSeek starter、ChatClient stream API、tool calling、MCP client、`MessageChatMemoryAdvisor`、迁移说明。
- **Spring AI Reference Docs**（docs.spring.io/spring-ai/reference/） - DeepSeek、ChatClient、Tools/ToolCallingManager、Advisors、MCP Client。
- **Spring AI Upgrade Notes** - 版本迁移指引。
- **Spring AI Examples - agentic-patterns/**（github.com/spring-projects/spring-ai-examples） - 直接检查了 `OrchestratorWorkers.java`、`EvaluatorOptimizer.java`、`ChainWorkflow.java`。
- **DeepSeek API Docs**（api-docs.deepseek.com） - V4 模型、限流（每用户 500 v4-pro / 2500 v4-flash）、定价、keep-alive。
- **WHATWG Server-Sent Events Spec** - SSE 帧格式，EventSource 限制。
- **MDN EventSource docs** - 仅 GET，无自定义请求头。
- **Anthropic - Building Effective Agents** - 工作流模式词汇。
- **ReAct paper**（Yao et al., 2022）、**ToT paper**（Yao et al., 2023）、**Reflexion paper**（Shinn et al., 2023）。

### 次要（MEDIUM 置信度）

- **DeepSeek 模型名 `deepseek-v4-pro`/`deepseek-v4-flash`** - 在 Spring AI 2.0 enum 中（HIGH），但账户可用性未验证。STACK.md MEDIUM；PITFALLS.md HIGH。在 Phase 1 验证前视为 MEDIUM。
- **Spring AI GitHub Examples** - 可能滞后于参考文档。
- **7 模式行业共识** - 基于 PROJECT.md 引用文章 + 论文共识。

### 第三级（LOW 置信度）

- **DeepSeek 课堂使用的定价/限流** - 未深入研究。
- **Spring Boot 4.0 -> 4.1 破坏性变更** - 未枚举；对全新项目无迁移顾虑。
- **Spring AI 2.0 手动工具调用循环确切类名** - `MessageAggregator` vs `ChatClientMessageAggregator`。Phase 5 规划期间通过 Context7 解决。

---
*Research completed: 2026-08-04*
*Ready for roadmap: yes*
