# Requirements: Agent 设计模式教学案例库

**Defined:** 2026-08-04
**Core Value:** 让学习者通过可运行的案例，直观理解 7 种 agent 设计模式的工作原理与差异--能看清每种模式"怎么思考"、"为何这么设计"。

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### 架构骨架 (ARCH)

- [ ] **ARCH-01**: Spring Boot 4.1.0 + Spring AI 2.0.0 (BOM) + Java 21 项目骨架（pom.xml 配置 spring-boot-starter-parent 和 spring-ai-bom）
- [ ] **ARCH-02**: AgentPattern 接口定义（Strategy 契约，每个模式实现该接口）
- [ ] **ARCH-03**: AgentEvent sealed 接口 + 9 个 record 子类型（ReasoningEvent/ToolCallEvent/ToolResultEvent/SubQuestionEvent/PlanEvent/StepStartEvent/StepCompleteEvent/FinalAnswerEvent/ErrorEvent）
- [ ] **ARCH-04**: AgentRegistry 通过 Spring DI 自动收集 List<AgentPattern>，按 patternId 查找
- [ ] **ARCH-05**: PatternController 提供 GET /api/patterns 返回模式列表
- [ ] **ARCH-06**: AgentController 提供 POST /api/agent/execute 返回 Flux<ServerSentEvent>（SSE 流式）
- [ ] **ARCH-07**: SseEventEmitter 辅助类，将 AgentEvent 序列化为 SSE 事件
- [ ] **ARCH-08**: GlobalExceptionHandler 统一异常处理，返回 ErrorEvent
- [ ] **ARCH-09**: WebConfig 配置 CORS 允许 http://localhost:5173

### DeepSeek 集成 (LLM)

- [ ] **LLM-01**: 使用 spring-ai-starter-model-deepseek 原生 starter（非 OpenAI 兼容 shim）
- [ ] **LLM-02**: application.yml 配置 spring.ai.deepseek.api-key（从环境变量读取）和模型名
- [ ] **LLM-03**: ChatClientConfig 产出 ChatClient bean
- [ ] **LLM-04**: 验证 deepseek-reasoner 的 reasoning_content 字段可获取（CoT/Reflexion 依赖）
- [ ] **LLM-05**: 配置 retry（max-attempts=3, backoff-interval=2000ms）和 timeout（600s）
- [ ] **LLM-06**: HTTP 429 限流自动重试 + 友好错误提示

### SSE 流式通信 (SSE)

- [ ] **SSE-01**: 后端用 Flux<ServerSentEvent<String>> 返回流式响应（Spring MVC，非 WebFlux）
- [ ] **SSE-02**: 前端用 fetch + ReadableStream 消费 SSE（非 EventSource，因 POST 请求）
- [ ] **SSE-03**: 前端 composables/useSSEStream.ts 封装 SSE 帧解析逻辑
- [ ] **SSE-04**: SSE 事件 schema 定义（event type + data JSON，前端按 type 路由渲染）
- [ ] **SSE-05**: 全链路流式验证（DevTools EventStream 逐事件显示，非一次性返回）
- [ ] **SSE-06**: 流式 tool call 参数 chunk 聚合（MessageAggregator 处理多 chunk JSON）
- [x] **SSE-07**: 模式切换或用户中断时 abort 之前的 SSE 请求（AbortController）
- [ ] **SSE-08**: SSE 解析器跳过 DeepSeek keep-alive 注释行（以 `:` 开头）

### 前端界面 (UI)

- [ ] **UI-01**: Vue 3.5 + Vite 8 + Element Plus 2.14 + TypeScript 前端骨架（frontend/ 目录）
- [x] **UI-02**: 模式选择器组件（7 种 agent 模式可选，显示模式名和简要描述）
- [x] **UI-03**: 问题输入框 + 提交按钮 + 清空按钮
- [x] **UI-04**: 模式描述卡片（每种模式的核心思想和适用场景）
- [x] **UI-05**: 推理过程展示区（按 SSE 事件类型路由渲染不同组件）
- [x] **UI-06**: 最终答案展示区（与推理过程视觉分离）
- [x] **UI-07**: 预设示例问题（每种模式 3-5 个，点击即填入）
- [ ] **UI-08**: 中文界面
- [x] **UI-09**: 流式渲染性能优化（shallowRef 而非 ref +=，避免 5k+ 字符卡顿）
- [ ] **UI-10**: TypeScript 类型镜像后端 AgentEvent 层次（discriminated union）
- [x] **UI-11**: 每种模式的专属推理可视化组件：
  - CoT: 线性文本流
  - Self-Ask: 子问题->子答案链式时间线
  - ReAct: Thought/Action/Observation 分块
  - Plan-and-Execute: 步骤清单（带状态）
  - ToT: 树状结构（节点评分 + 剪枝分支）
  - Reflexion: 尝试->评估->反思循环时间线
  - Role-playing: 多角色对话（彩色头像）

### Agent 模式 (PATTERN)

- [x] **PATTERN-01**: CoT（思维链）模式实现 - chatClient.prompt().user(q).stream() 单次流式调用，每 chunk 为 ReasoningEvent，末尾 FinalAnswerEvent
- [ ] **PATTERN-02**: Self-Ask（自问自答）模式实现 - 结构化 Follow-up/Answer/Final 解析，发射 SubQuestionEvent
- [ ] **PATTERN-03**: ReAct（推理+行动）模式实现 - 手动工具调用循环（禁用 ToolCallingAdvisor auto-registration，用 ToolCallingManager + MessageAggregator）
- [ ] **PATTERN-04**: Plan-and-Execute（计划与执行）模式实现 - 两阶段（Planner 生成 JSON 计划 + Executor 逐步执行），replan-on-failure
- [ ] **PATTERN-05**: Tree of Thoughts（树状思维）模式实现 - 分支生成 + LLM 评估 + 贪心选择（branching=2-3, depth=3, top-K=2 剪枝）
- [x] **PATTERN-06**: Reflexion（反思迭代）模式实现 - generator + LLM-as-judge evaluator + reflector + retry（max_reflections=2-3，improvement-epsilon 停止）
- [x] **PATTERN-07**: Role-playing（角色扮演）模式实现 - 3 个固定角色（PM/Dev/Tester），固定 5 轮对话，每角色独立 system prompt + 彩色头像

### 工具调用 (TOOL)

- [ ] **TOOL-01**: ToolRegistry 合并内置工具和 MCP 工具（List<ToolCallback>）
- [ ] **TOOL-02**: 内置 WeatherTool（mock 数据）+ CalculatorTool（真实计算）+ TimeTool（当前时间）
- [ ] **TOOL-03**: @Tool 注解注册工具方法（Spring AI 2.0 API）
- [ ] **TOOL-04**: ReAct 模式禁用 ToolCallingAdvisor auto-registration（toolCallingAdvisorAutoRegister(false)）
- [ ] **TOOL-05**: max_iterations=8-10 防止 ReAct 无限循环
- [ ] **TOOL-06**: 工具调用去重（same tool+args -> "use previous result"）
- [ ] **TOOL-07**: 系统提示包含显式收敛条件，temperature 0.3-0.5

### MCP 集成 (MCP)

- [ ] **MCP-01**: spring-ai-starter-mcp-client 依赖加入 pom.xml
- [ ] **MCP-02**: application.yml 配置一个 demo MCP server（Streamable HTTP 传输）
- [ ] **MCP-03**: ToolRegistry 自动注入 ToolCallbackProvider（MCP 工具无缝接入）
- [ ] **MCP-04**: 前端 ToolCallEventCard 显示工具来源标识（builtin vs mcp）
- [ ] **MCP-05**: GET /api/mcp/tools 端点列出已注册的 MCP 工具

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### 差异化功能 (DIFF)

- **DIFF-01**: 跨模式对比（并行 SSE 通道，同问题并排展示 7 种模式输出）- 教学杀手锏
- **DIFF-02**: 参数配置（temperature/maxTokens/model 通过 ChatOptions 配置）
- **DIFF-03**: Token 用量 + 每步耗时统计
- **DIFF-04**: 工具调用详情展开（ReAct 模式）
- **DIFF-05**: 每模式源码查看链接
- **DIFF-06**: Reflexion 评估器可视化
- **DIFF-07**: ToT 剪枝分支高亮
- **DIFF-08**: MCP 外部工具配置 UI（无需改代码即可加 MCP server）

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| 用户登录/认证 | 教学演示用途，无需多用户隔离 |
| 数据库持久化 | 对话无需保存，重启丢失可接受，降低部署门槛 |
| 生产部署/容器化 | 本地运行即可，不做 Docker/K8s/监控 |
| 移动端适配 | 桌面端优先，教学场景 |
| 多 LLM 切换 | 只用 DeepSeek，聚焦教学而非多供应商 |
| RAG（检索增强） | 不在 7 模式范围，增加复杂度 |
| 自定义工具构建器 | 教学用固定工具集，不做拖拽式工具配置 |
| 实时多用户协作 | 单用户教学场景 |
| i18n（国际化） | 中文界面，教学受众明确 |
| 暗色模式 | 视觉偏好，非教学核心 |
| 对话历史导出 | 无数据库，不保存历史 |
| 真实外部 API | 用 mock 工具（如 WeatherTool mock），避免依赖外部服务 |
| Spring Security | 教学项目无需安全防护 |
| WebSocket | SSE 足够单向流式，WebSocket 双向开销不必要 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ARCH-01 | Phase 1: Skeleton | Pending |
| ARCH-02 | Phase 2: Agent Abstraction | Pending |
| ARCH-03 | Phase 2: Agent Abstraction | Pending |
| ARCH-04 | Phase 2: Agent Abstraction | Pending |
| ARCH-05 | Phase 2: Agent Abstraction | Pending |
| ARCH-06 | Phase 2: Agent Abstraction | Pending |
| ARCH-07 | Phase 2: Agent Abstraction | Pending |
| ARCH-08 | Phase 2: Agent Abstraction | Pending |
| ARCH-09 | Phase 1: Skeleton | Pending |
| LLM-01 | Phase 1: Skeleton | Pending |
| LLM-02 | Phase 1: Skeleton | Pending |
| LLM-03 | Phase 1: Skeleton | Pending |
| LLM-04 | Phase 1: Skeleton | Pending |
| LLM-05 | Phase 1: Skeleton | Pending |
| LLM-06 | Phase 1: Skeleton | Pending |
| SSE-01 | Phase 1: Skeleton | Pending |
| SSE-02 | Phase 1: Skeleton | Pending |
| SSE-03 | Phase 1: Skeleton | Pending |
| SSE-04 | Phase 2: Agent Abstraction | Pending |
| SSE-05 | Phase 1: Skeleton | Pending |
| SSE-06 | Phase 5: ReAct Pattern | Pending |
| SSE-07 | Phase 3: CoT Pattern | Complete |
| SSE-08 | Phase 1: Skeleton | Pending |
| UI-01 | Phase 1: Skeleton | Pending |
| UI-02 | Phase 3: CoT Pattern | Complete |
| UI-03 | Phase 3: CoT Pattern | Complete |
| UI-04 | Phase 3: CoT Pattern | Complete |
| UI-05 | Phase 3: CoT Pattern | Complete |
| UI-06 | Phase 3: CoT Pattern | Complete |
| UI-07 | Phase 3: CoT Pattern | Complete |
| UI-08 | Phase 1: Skeleton | Pending |
| UI-09 | Phase 3: CoT Pattern | Complete |
| UI-10 | Phase 2: Agent Abstraction | Pending |
| UI-11 (CoT 部分) | Phase 3: CoT Pattern | Pending |
| UI-11 (ReAct 部分) | Phase 5: ReAct Pattern | Pending |
| UI-11 (Self-Ask 部分) | Phase 6: Self-Ask Pattern | Pending |
| UI-11 (Plan-and-Execute 部分) | Phase 7: Plan-and-Execute Pattern | Pending |
| UI-11 (ToT 部分) | Phase 8: Tree of Thoughts Pattern | Pending |
| UI-11 (Reflexion 部分) | Phase 9: Reflexion Pattern | Pending |
| UI-11 (Role-playing 部分) | Phase 10: Role-playing Pattern | Pending |
| PATTERN-01 | Phase 3: CoT Pattern | Complete |
| PATTERN-02 | Phase 6: Self-Ask Pattern | Pending |
| PATTERN-03 | Phase 5: ReAct Pattern | Pending |
| PATTERN-04 | Phase 7: Plan-and-Execute Pattern | Pending |
| PATTERN-05 | Phase 8: Tree of Thoughts Pattern | Pending |
| PATTERN-06 | Phase 9: Reflexion Pattern | Complete |
| PATTERN-07 | Phase 10: Role-playing Pattern | Complete |
| TOOL-01 | Phase 4: Tool Layer | Pending |
| TOOL-02 | Phase 4: Tool Layer | Pending |
| TOOL-03 | Phase 4: Tool Layer | Pending |
| TOOL-04 | Phase 5: ReAct Pattern | Pending |
| TOOL-05 | Phase 5: ReAct Pattern | Pending |
| TOOL-06 | Phase 5: ReAct Pattern | Pending |
| TOOL-07 | Phase 5: ReAct Pattern | Pending |
| MCP-01 | Phase 11: MCP Integration | Pending |
| MCP-02 | Phase 11: MCP Integration | Pending |
| MCP-03 | Phase 11: MCP Integration | Pending |
| MCP-04 | Phase 11: MCP Integration | Pending |
| MCP-05 | Phase 11: MCP Integration | Pending |

**Coverage:**
- v1 requirements: 53 total (ARCH=9, LLM=6, SSE=8, UI=11, PATTERN=7, TOOL=7, MCP=5)
- Mapped to phases: 53
- Unmapped: 0

**Note on UI-11:** UI-11 is a single requirement listing 7 per-pattern visualization sub-components. Each sub-component is mapped to its corresponding pattern phase (3, 5-10). The requirement is considered fully delivered when all 7 sub-components ship across their respective phases.

---
*Requirements defined: 2026-08-04*
*Last updated: 2026-08-04 after roadmap creation*
