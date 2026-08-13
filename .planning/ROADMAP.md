# Roadmap: Agent 设计模式教学案例库

## Overview

From empty Maven skeleton to a runnable teaching library where learners pick one of 7 agent design patterns, ask a question, and watch the pattern's reasoning unfold live in the browser via SSE. The build order front-loads risk: first prove DeepSeek + SSE works end-to-end (Phase 1), then lock in the Strategy + Plugin Registry contract (Phase 2), then ship the simplest pattern (CoT) to validate the full UI surface (Phase 3). Tools come before ReAct (Phase 4 -> 5) because ReAct is the highest-risk pattern and must validate the manual tool-call loop. With the contract proven, the remaining 5 patterns (Self-Ask, Plan-and-Execute, ToT, Reflexion, Role-playing) each become a focused, teachable unit. MCP integration lands last as the extensibility story.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Skeleton** - DeepSeek + Vue + SSE plumbing 端到端打通
- [ ] **Phase 2: Agent Abstraction** - AgentPattern 契约 + AgentEvent sealed 层次 + AgentRegistry
- [x] **Phase 3: CoT Pattern** - 第一个模式（最简），完整 UI 表面首次落地 (completed 2026-08-06)
- [x] **Phase 4: Tool Layer** - 内置工具（天气/计算器/时间）+ ToolRegistry (completed 2026-08-11)
- [ ] **Phase 5: ReAct Pattern** - 最高风险模式，手动工具调用循环，验证 Tool Layer
- [ ] **Phase 6: Self-Ask Pattern** - 子问题->子答案链式时间线
- [ ] **Phase 7: Plan-and-Execute Pattern** - 两阶段（Planner + Executor）+ replan-on-failure
- [ ] **Phase 8: Tree of Thoughts Pattern** - 分支生成 + LLM 评估 + 贪心剪枝
- [ ] **Phase 9: Reflexion Pattern** - generator + LLM-as-judge + reflector + retry
- [ ] **Phase 10: Role-playing Pattern** - 3 角色（PM/Dev/Tester）5 轮对话
- [ ] **Phase 11: MCP Integration** - 接入外部 MCP Server，工具生态扩展

## Phase Details

### Phase 1: Skeleton

**Goal**: 用户在浏览器中能看到 DeepSeek 通过 SSE 流式返回的文本，证明前后端 + LLM + 流式管道端到端打通（无 agent 模式）
**Depends on**: Nothing (first phase)
**Requirements**: ARCH-01, ARCH-09, LLM-01, LLM-02, LLM-03, LLM-04, LLM-05, LLM-06, SSE-01, SSE-02, SSE-03, SSE-05, SSE-08, UI-01, UI-08
**Success Criteria** (what must be TRUE):

  1. 后端启动后 POST /api/ping 返回 DeepSeek 的非空响应
  2. 前端启动后页面以中文展示，能调用 /api/ping 并显示响应
  3. DevTools EventStream 面板显示 SSE 事件逐个到达（非一次性返回）
  4. 使用 deepseek-reasoner 模型时，响应包含非空 reasoning_content 字段
  5. 模拟 HTTP 429 限流时，自动重试不超过 3 次并返回友好提示

**Plans**: 3 plans

Plans:
**Wave 1**

- [x] 01-01-PLAN.md - Backend skeleton (pom.xml + application.yml + ChatClientConfig + WebConfig + Wave 0 tests)
- [x] 01-03-PLAN.md - Frontend skeleton (Vite scaffold + useSSEStream composable + 4 components + App.vue)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md - Backend controller (PingController + SseEventEmitter + GlobalExceptionHandler + PingControllerTest + DeepSeekIntegrationTest)

### Phase 2: Agent Abstraction

**Goal**: 7 种模式的契约、事件层次和注册机制就绪，添加新模式只需新建一个 @Component 类，无需改控制器
**Depends on**: Phase 1
**Requirements**: ARCH-02, ARCH-03, ARCH-04, ARCH-05, ARCH-06, ARCH-07, ARCH-08, SSE-04, UI-10
**Success Criteria** (what must be TRUE):

  1. GET /api/patterns 返回空列表（暂无模式注册），证明端点工作
  2. POST /api/agent/execute 对未知 patternId 返回 404 + ErrorEvent
  3. AgentEvent sealed 接口有 9 个 record 子类型，前端 TypeScript 类型以 discriminated union 镜像后端
  4. 添加一个 mock @Component 实现 AgentPattern 后，GET /api/patterns 自动返回该模式（证明 Strategy + Plugin Registry 工作，无需改控制器）

**Plans**: 5 plans
**UI hint**: yes

Plans:
**Wave 1** *(parallel - no file conflicts)*

- [x] 02-01-PLAN.md - Backend AgentEvent contract layer (sealed interface AgentEvent + 9 records + AgentPattern + AgentContext + NoSuchPatternException)
- [x] 02-02-PLAN.md - Frontend TS type mirror (types/agent.ts + types/sse.ts refactor for D-01 event name routing)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 02-03-PLAN.md - Backend SSE emitter refactor (SseEventEmitter @Component + ObjectMapper + PingController/GlobalExceptionHandler upgrade to AgentEvent)

**Wave 3** *(parallel - no file conflicts, blocked on Wave 2 completion)*

- [x] 02-04-PLAN.md - Backend AgentRegistry + PatternController + AgentController + MockPattern + 4 Success Criteria verification
- [x] 02-05-PLAN.md - Frontend useSSEStream refactor + App.vue callback adaptation + end-to-end verification

### Phase 3: CoT Pattern

**Goal**: 用户能选择 CoT 模式，输入问题，看到思维链流式推理过程和最终答案（首次完整 UI 落地）
**Depends on**: Phase 2
**Requirements**: PATTERN-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07, UI-09, SSE-07, UI-11 (CoT 线性文本流部分)
**Success Criteria** (what must be TRUE):

  1. 模式选择器显示 CoT 模式（含名称和描述），用户可选中
  2. 用户输入问题并提交后，推理过程区流式显示思维链文本（每个 chunk 实时到达，非一次性）
  3. 推理结束后，最终答案在视觉分离的区域显示
  4. 用户切换模式或主动中断时，之前的 SSE 请求被 abort（AbortController 生效，不继续消耗带宽）
  5. CoT 推理过程超过 5000 字符时，前端不卡顿（shallowRef 优化生效）

**Plans**: 5 plans
**UI hint**: yes

Plans:
**Wave 1** *(parallel - no file conflicts)*

- [x] 03-01-PLAN.md - Backend CoTAgentPattern implementation (ChatClient.Builder injection + dedicated system prompt + ReasoningEvent/FinalAnswerEvent streaming)
- [x] 03-02-PLAN.md - Frontend patternDetails constants (PatternDetail interface + PATTERN_DETAILS map with CoT examples)

**Wave 2** *(parallel - no file conflicts, blocked on Wave 1 completion)*

- [x] 03-03-PLAN.md - Frontend PatternDescriptionCard + PatternSelector refactor (API integration + CoT enabled + description card at sidebar bottom)
- [x] 03-04-PLAN.md - Frontend QuestionInput refactor (example questions above textarea + selectExample event)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 03-05-PLAN.md - Frontend App.vue integration (selectedPatternId state + SSE abort on pattern switch + /api/agent/execute endpoint + examples prop passing)

### Phase 03.1: 根据设计规范优化前端页面 (INSERTED)

**Goal**: 按【综合积分权益平台工作台】的 PC 端设计规范（位于 `设计规范/` 目录的 14 张参考图片：色彩和文字、栅格和布局、组件库 1-4、业务组件 1-3、图标库、图表库、数据格式、骨架屏、图形化）优化现有 Agent 教学案例库前端。保留 Phase 1-3 实现的 7 种 pattern 功能（PatternSelector、QuestionInput、ReasoningPanel、FinalAnswer、PatternDescriptionCard），仅按设计规范重写样式、配色、字体、圆角、间距、布局。约束：(1) 所有配色、字体、圆角、间距完全沿用参考组件，禁止自定义数值；(2) 保持响应式 PC 布局，全部模块使用自动布局。
**Requirements**: TBD
**Depends on**: Phase 3
**Plans**: 3 plans

Plans:
**Wave 1**

- [x] 03.1-01-PLAN.md - Global styles infrastructure + Element Plus theme override (variables.css, reset.css, typography.css, layout.css, index.css, main.ts, index.html)

**Wave 2** *(parallel, blocked on Wave 1)*

- [x] 03.1-02-PLAN.md - App.vue layout restyle (header, aside, main, main-content)
- [x] 03.1-03-PLAN.md - Component-level restyle (PatternSelector, PatternDescriptionCard, QuestionInput, ReasoningPanel, FinalAnswer)

### Phase 4: Tool Layer

**Goal**: 内置工具（天气/计算器/时间）注册到 ToolRegistry，可通过测试端点直接调用，为 ReAct 铺路
**Depends on**: Phase 2
**Requirements**: TOOL-01, TOOL-02, TOOL-03
**Success Criteria** (what must be TRUE):

  1. WeatherTool 返回 mock 天气数据，CalculatorTool 真实计算（如 2+3=5），TimeTool 返回当前时间
  2. 工具方法用 @Tool 注解注册（Spring AI 2.0 API，非旧版 .functions()）
  3. ToolRegistry 暴露 List<ToolCallback>，通过测试端点可直接调用任一工具并获取结果

**Plans**: 3 plans

Plans:
**Wave 1**

- [x] 04-01-PLAN.md — 3 内置工具类（WeatherTool/CalculatorTool/TimeTool）+ 单元测试

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 04-02-PLAN.md — ToolRegistry 聚合 + NoSuchToolException + DTO records + ToolRegistryTest

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 04-03-PLAN.md — ToolController RESTful 端点 + WebTestClient 集成测试

### Phase 5: ReAct Pattern

**Goal**: 用户能选择 ReAct 模式，看到 Thought/Action/Observation 交替的推理过程，工具调用正确执行，且不会无限循环
**Depends on**: Phase 4
**Requirements**: PATTERN-03, TOOL-04, TOOL-05, TOOL-06, TOOL-07, SSE-06, UI-11 (ReAct Thought/Action/Observation 分块部分)
**Success Criteria** (what must be TRUE):

  1. ReAct 模式按 Thought -> Action -> Observation 循环展示推理过程，每个阶段视觉分块
  2. 工具调用的多 chunk JSON 参数被正确聚合为完整 ToolCallEvent（MessageAggregator 生效）
  3. 触发无限循环的 prompt 在 8-10 次迭代内停止（max_iterations 生效）
  4. 相同工具+相同参数的重复调用返回 "use previous result"（去重生效）
  5. 工具调用结果在 ToolCallEventCard 和 ToolResultEventCard 中正确显示

**Plans**: 3 plans
**UI hint**: yes

Plans:
**Wave 1** *(parallel - no file conflicts)*

- [x] 05-01-PLAN.md - ReActAgentPattern 后端 + 单元测试（手动循环、MessageAggregator、去重、max_iterations=10）
- [x] 05-02-PLAN.md - 前端新组件（ToolCallEventCard/ToolResultEventCard）+ useSSEStream 事件路由 + patternDetails + PatternSelector 启用

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 05-03-PLAN.md - 前端 App.vue + ReasoningPanel 集成（垂直时间线 Thought/Action/Observation 分块）

### Phase 6: Self-Ask Pattern

**Goal**: 用户能选择 Self-Ask 模式，看到大问题被拆为子问题，子问题->子答案按链式时间线展示
**Depends on**: Phase 5
**Requirements**: PATTERN-02, UI-11 (Self-Ask 子问题->子答案链式时间线部分)
**Success Criteria** (what must be TRUE):

  1. Self-Ask 模式将大问题拆解为子问题，每个子问题以 SubQuestionEvent 发射
  2. 每个子问题有对应的子答案，按时间线链式展示（子问题->子答案->下一子问题）
  3. 最终答案综合所有子问题答案得出，在最终答案区显示

**Plans**: 3 plans
**UI hint**: yes

### Phase 7: Plan-and-Execute Pattern

**Goal**: 用户能选择 Plan-and-Execute 模式，看到先规划再逐步执行的过程，步骤失败可重新规划
**Depends on**: Phase 5
**Requirements**: PATTERN-04, UI-11 (Plan-and-Execute 步骤清单带状态部分)
**Success Criteria** (what must be TRUE):

  1. Planner 生成结构化 JSON 计划（3-7 步骤），以 PlanEvent 展示完整计划
  2. Executor 逐步执行，每步以 StepStartEvent/StepCompleteEvent 展示当前状态（pending/running/done/failed）
  3. 步骤失败时触发 replan，新计划在前端可见（用户能看到计划变更）

**Plans**: 3 plans
**UI hint**: yes

### Phase 8: Tree of Thoughts Pattern

**Goal**: 用户能选择 ToT 模式，看到多分支探索 + LLM 评估 + 贪心剪枝的完整过程
**Depends on**: Phase 5
**Requirements**: PATTERN-05, UI-11 (ToT 树状结构带评分和剪枝部分)
**Success Criteria** (what must be TRUE):

  1. ToT 生成 branching=2-3 个分支，depth=3 层，节点总数受控
  2. 每个分支节点有 LLM 评估分数，前端树状结构展示节点评分
  3. top-K=2 剪枝生效，被剪枝的分支在前端有视觉标识（如灰色或删除线）
  4. 最终答案从存活分支中选出最优路径

**Plans**: 3 plans
**UI hint**: yes

### Phase 9: Reflexion Pattern

**Goal**: 用户能选择 Reflexion 模式，看到尝试->评估->反思的循环过程，循环有界不会卡死
**Depends on**: Phase 5
**Requirements**: PATTERN-06, UI-11 (Reflexion 尝试->评估->反思循环时间线部分)
**Success Criteria** (what must be TRUE):

  1. Generator 生成初始答案，Evaluator (LLM-as-judge) 评估并给出分数/反馈
  2. 评估不通过时，Reflector 生成反思，下一轮 Generator 据此改进
  3. max_reflections=2-3 生效，循环不会无限进行
  4. 改进幅度低于 epsilon 时提前停止（避免无意义迭代）
  5. 前端时间线展示 attempt -> evaluate -> reflect 完整循环

**Plans**: 3 plans
**UI hint**: yes

### Phase 10: Role-playing Pattern

**Goal**: 用户能选择 Role-playing 模式，看到 3 个角色（PM/Dev/Tester）按固定顺序 5 轮对话协作产出结论
**Depends on**: Phase 5
**Requirements**: PATTERN-07, UI-11 (Role-playing 多角色对话彩色头像部分)
**Success Criteria** (what must be TRUE):

  1. 3 个固定角色（PM/Dev/Tester）按固定顺序发言，每角色有独立 system prompt
  2. 每个角色有独特的彩色头像，前端清晰区分各角色发言
  3. 共 5 轮对话，最终由最后一个角色输出结论
  4. 每轮发言以流式方式实时显示（非等所有轮次完成后一次性显示）

**Plans**: 3 plans
**UI hint**: yes

### Phase 11: MCP Integration

**Goal**: 用户能在前端看到 MCP 工具与内置工具并存，且 ReAct 模式能调用 MCP 工具完成推理（无缝扩展故事）
**Depends on**: Phase 5
**Requirements**: MCP-01, MCP-02, MCP-03, MCP-04, MCP-05
**Success Criteria** (what must be TRUE):

  1. spring-ai-starter-mcp-client 依赖加入 pom.xml，application.yml 配置一个 demo MCP server（Streamable HTTP 传输）
  2. ToolRegistry 自动注入 ToolCallbackProvider，MCP 工具与内置工具合并为统一 List<ToolCallback>
  3. GET /api/mcp/tools 端点返回已注册的 MCP 工具列表
  4. 前端 ToolCallEventCard 显示工具来源标识（builtin vs mcp），用户能区分工具来源
  5. ReAct 模式可调用 MCP 工具完成推理，无需修改 ReAct 模式代码（无缝集成）

**Plans**: 3 plans

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 3.1 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 11

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Skeleton | 2/3 | In Progress|  |
| 2. Agent Abstraction | 0/5 | Planned | - |
| 3. CoT Pattern | 5/5 | Complete    | 2026-08-06 |
| 03.1. ui-polish | 3/3 | Complete    | 2026-08-10 |
| 4. Tool Layer | 3/3 | Complete   | 2026-08-11 |
| 5. ReAct Pattern | 2/3 | In Progress|  |
| 6. Self-Ask Pattern | 0/0 | Not started | - |
| 7. Plan-and-Execute Pattern | 0/0 | Not started | - |
| 8. Tree of Thoughts Pattern | 0/0 | Not started | - |
| 9. Reflexion Pattern | 0/0 | Not started | - |
| 10. Role-playing Pattern | 0/0 | Not started | - |
| 11. MCP Integration | 0/0 | Not started | - |

---

*Roadmap created: 2026-08-04*
*Granularity: fine (11 phases within 8-12 range)*
*Build order follows research/SUMMARY.md recommendation*
