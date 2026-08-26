---
phase: quick-260825-gtx
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/com/agents/agent/core/ReasoningDeltaEvent.java
  - src/main/java/com/agents/agent/core/RoleSpeechDeltaEvent.java
  - src/main/java/com/agents/agent/core/AgentEvent.java
  - src/main/java/com/agents/agent/patterns/StreamingLlmCall.java
  - src/main/java/com/agents/agent/patterns/ReActAgentPattern.java
  - src/main/java/com/agents/agent/patterns/RolePlayingAgentPattern.java
  - src/test/java/com/agents/agent/patterns/StreamingLlmCallTest.java
  - src/test/java/com/agents/agent/patterns/ReActAgentPatternTest.java
  - src/test/java/com/agents/agent/patterns/RolePlayingAgentPatternTest.java
  - frontend/src/types/sse.ts
  - frontend/src/types/agent.ts
  - frontend/src/composables/useSSEStream.ts
  - frontend/src/App.vue
  - frontend/src/components/ReasoningPanel.vue
autonomous: true
requirements: [D-01, D-04, D-05]
must_haves:
  truths:
    - "ReAct 每轮 LLM 调用期间逐 chunk 发射 ReasoningDeltaEvent（thought 卡片逐字生长），轮末仍发完整 ReasoningEvent，控制流（tool call 解析/final_answer 提取/MAX_ITERATIONS）行为不变"
    - "Role-play 每个角色发言期间逐 chunk 发射 RoleSpeechDeltaEvent(round, role, content)（气泡逐字生长），发言完成仍发完整 Role*Event，总结轮以多 chunk FinalAnswerEvent 流式发射"
    - "MessageAggregator 聚合结果与 delta 透传协作正确：多 chunk tool_call 参数聚合后为完整 JSON"
    - "前端：delta 事件渲染为临时卡片/气泡，完整事件到达后替换；事件时间线（agentEvents）不含 delta 事件"
    - "CoT/PingController/AgentController/SseEventEmitter/其余 4 模式零改动，现有测试（除 RolePlayingAgentPatternTest 重写外）保持绿色"
  artifacts:
    - path: "src/main/java/com/agents/agent/patterns/StreamingLlmCall.java"
      provides: "共享流式原语（streamAndAggregate / streamContent）"
      contains: "MessageAggregator"
    - path: "src/main/java/com/agents/agent/core/ReasoningDeltaEvent.java"
      provides: "ReAct thought 流式增量事件"
      contains: "record ReasoningDeltaEvent"
    - path: "src/main/java/com/agents/agent/core/RoleSpeechDeltaEvent.java"
      provides: "Role-play 发言流式增量事件"
      contains: "record RoleSpeechDeltaEvent"
  key_links:
    - from: "src/main/java/com/agents/agent/patterns/ReActAgentPattern.java"
      to: "src/main/java/com/agents/agent/patterns/StreamingLlmCall.java"
      via: "streamAndAggregate(chatModel.stream(prompt), deltaSink, deltaSink)"
      pattern: "StreamingLlmCall\\.streamAndAggregate"
    - from: "src/main/java/com/agents/agent/patterns/RolePlayingAgentPattern.java"
      to: "src/main/java/com/agents/agent/patterns/StreamingLlmCall.java"
      via: "streamContent(flux, delta -> RoleSpeechDeltaEvent)"
      pattern: "StreamingLlmCall\\.streamContent"
    - from: "frontend/src/composables/useSSEStream.ts"
      to: "frontend/src/App.vue"
      via: "onReasoningDelta/onRoleSpeechDelta 回调路由"
      pattern: "onReasoningDelta"
---

<objective>
让 ReAct 与 Role-playing 模式实现真正的流式输出。完整设计与决策见用户已批准计划：
/Users/weizhipeng/.claude/plans/warm-beaming-newt.md（本 PLAN 为其 GSD 执行化转录，设计决策 LOCKED）。

核心方案：新增 ReasoningDeltaEvent / RoleSpeechDeltaEvent 两个临时态 delta 事件 + 共享原语
StreamingLlmCall（MessageAggregator 透传 chunk 做 delta + 聚合完整结果做控制流决策），
完成后仍发现有的完整事件（前端用完整事件替换 delta 拼接的临时卡片，最终渲染与现状一致，
旧前端静默忽略新事件名，向后兼容）。

根因背景：7 模式中仅 CoT 真流式；ReAct 每轮被 MessageAggregator.blockLast() 阻塞整轮，
Role-play 16 次 .call() 全阻塞，用户"等很久后一次性看到全部"，违背项目"实时展示推理过程"核心价值。
</objective>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: 后端基建 + ReAct 真流式</name>
  <files>
    src/main/java/com/agents/agent/core/ReasoningDeltaEvent.java (新建)
    src/main/java/com/agents/agent/patterns/StreamingLlmCall.java (新建)
    src/main/java/com/agents/agent/core/AgentEvent.java (permits + javadoc 18->20)
    src/main/java/com/agents/agent/patterns/ReActAgentPattern.java (:124-127 替换)
    src/test/java/com/agents/agent/patterns/StreamingLlmCallTest.java (新建)
    src/test/java/com/agents/agent/patterns/ReActAgentPatternTest.java (新增 3 用例)
  </files>
  <action>
    1. 新建 ReasoningDeltaEvent(Instant ts, String content) record（com.agents.agent.core 包，sealed 同包约束）
    2. 新建 StreamingLlmCall：streamAndAggregate(Flux<ChatResponse>, Consumer<String> onReasoningDelta, Consumer<String> onContentDelta) 返回聚合 ChatResponse；
       streamContent(Flux<ChatResponse>, Consumer<String> onContentDelta) 返回聚合文本（null 降级 ""）。
       实现：MessageAggregator().aggregate(flux.doOnNext(chunk -> emitDeltas(...)), aggregated::set).blockLast()，
       doOnNext 在 aggregate 之前；chunk 解析与 CoTAgentPattern:68-83 同构（DeepSeekAssistantMessage.getReasoningContent() / getText()，null/空跳过）
    3. AgentEvent permits 加 ReasoningDeltaEvent，javadoc 计数与新增说明
    4. ReActAgentPattern :124-127 替换为 StreamingLlmCall.streamAndAggregate(chatModel.stream(prompt),
       reasoningDelta -> sink.next(new ReasoningDeltaEvent(...)), contentDelta -> sink.next(new ReasoningDeltaEvent(...)))；
       :141/:148 完整 ReasoningEvent 与全部控制流不动；javadoc 补流式说明
    5. 新建 StreamingLlmCallTest（delta 提取、多 chunk 聚合、null/空容错、Flux.error 传播、null 降级）
    6. ReActAgentPatternTest 新增 3 用例：多 chunk 流（内容 3 chunk + toolCall 参数 2 chunk）-> delta 逐 chunk 且全在 ToolCallEvent 前、
       聚合后 tool 参数完整（关键回归）；reasoningContent chunk -> ReasoningDeltaEvent；Flux.error -> 单个 ErrorEvent
  </action>
  <verify>JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -Dtest='StreamingLlmCallTest,ReActAgentPatternTest' -q</verify>
  <done>新事件 + helper + ReAct 改造完成，新旧测试全绿，提交 1：feat: stream ReAct thought deltas via StreamingLlmCall helper</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Roleplay 后端真流式</name>
  <files>
    src/main/java/com/agents/agent/core/RoleSpeechDeltaEvent.java (新建)
    src/main/java/com/agents/agent/core/AgentEvent.java (permits + javadoc)
    src/main/java/com/agents/agent/patterns/RolePlayingAgentPattern.java (callRole -> streamRole，总结轮流式)
    src/test/java/com/agents/agent/patterns/RolePlayingAgentPatternTest.java (mock 链重写)
  </files>
  <action>
    1. 新建 RoleSpeechDeltaEvent(Instant ts, int round, String role, String content) record
    2. AgentEvent permits 加 RoleSpeechDeltaEvent，javadoc 计数 20
    3. RolePlayingAgentPattern：callRole 改 streamRole(systemPrompt, userPrompt, round, role, sink)——
       chatClientBuilder.defaultSystem(...).build().prompt().user(...).stream().chatResponse() ->
       StreamingLlmCall.streamContent(flux, delta -> sink.next(new RoleSpeechDeltaEvent(Instant.now(), round, role, delta)))，
       返回聚合文本，history.add 不变；三处调用点传 round+role；完整 Role*Event 发射保留；
       总结调用改 streamContent(flux, delta -> sink.next(new FinalAnswerEvent(Instant.now(), delta)))；
       javadoc 删除"非流式"陈述（:32-33）、修正 deepseek-chat 为 deepseek-reasoner
    4. RolePlayingAgentPatternTest 重写：mockRequestChain 改 .stream() 链（requestSpec.stream() 返回 StreamResponseSpec，
       .chatResponse() 返回 Flux<ChatResponse>）；断言改 filter-by-type（Role*Event 15 个顺序/round/role/content、
       RoleSpeechDeltaEvent 按 (round, role) 拼接 == 完整 content 且先于完整事件、FinalAnswerEvent 拼接 == summary）；
       verify(streamResponseSpec, times(16)).chatResponse()；错误测试 Flux.error -> 单个 ErrorEvent；
       空内容 Flux.empty() -> RolePmEvent content ""（T-10-01 保持）
  </action>
  <verify>JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -Dtest='RolePlayingAgentPatternTest' -q</verify>
  <done>Roleplay 流式完成，测试全绿，提交 2：feat: stream Role-playing speeches and summary via delta events</done>
</task>

<task type="auto">
  <name>Task 3: 前端流式渲染</name>
  <files>
    frontend/src/types/sse.ts (AgentEventName +2)
    frontend/src/types/agent.ts (新事件接口 + AgentEvent union)
    frontend/src/composables/useSSEStream.ts (2 个可选回调 + 2 个 case)
    frontend/src/App.vue (activeThought/activeSpeech 状态 + 分组协议 + prop 传递)
    frontend/src/components/ReasoningPanel.vue (临时卡片/气泡渲染 + isRoleplayMode 放宽)
  </files>
  <action>
    1. types：AgentEventName 加 'ReasoningDeltaEvent' | 'RoleSpeechDeltaEvent'（18->20）；新增 ReasoningDeltaEvent{ts, content}、
       RoleSpeechDeltaEvent{ts, round, role, content} 接口并入 AgentEvent union
    2. useSSEStream：SSEStreamOptions 加 onReasoningDelta?/onRoleSpeechDelta? 可选回调，switch 加 2 个 case，头注释 18->20
    3. App.vue：shallowRef 新状态 activeThought(string)/activeSpeech({round, role, content}|null)；
       onReasoningDelta -> activeThought += content（不进 agentEvents/reasoningText）；
       onRoleSpeechDelta -> 同 (round, role) 追加否则新建；分组协议：onReasoning/onToolCall/onToolResult/onFinal/onError
       开头关闭 activeThought；onRolePm/Dev/Tester 匹配 (round, role) 则 activeSpeech=null；submit() 重置两状态；
       :82-86 防御 guard 保留不动；传 2 个新 prop 给 ReasoningPanel
    4. ReasoningPanel：新 props activeThought?/activeSpeech?；ReAct timeline 末尾渲染"思考中"卡片
       （复用 .timeline-item.thought 样式 + 光标符 ▍，activeThought 非空时隐藏 empty-state）；
       roleplay 分支已完成轮次后渲染临时气泡（复用 .roleplay-card + roleConfig 颜色 + "正在发言..."徽标）；
       isRoleplayMode 放宽为 (有 Role*Event || activeSpeech 非空)
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit && npm run build</verify>
  <done>前端类型检查与构建通过，提交 3：feat: render streaming deltas as live thought cards and speech bubbles</done>
</task>

</tasks>

<verification>
1. JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -q 全绿
2. cd frontend && npx vue-tsc --noEmit 通过
3. 冒烟：启动后端 + npm run dev，ReAct 提问观察 thought 卡片逐字生长 + 工具卡片随后出现；Roleplay 观察气泡逐字生长
4. 传输层：curl -N POST /api/agent/execute {"patternId":"react",...} 字节渐进到达
5. 回归：CoT 流式不受影响；AgentControllerTest（404+ErrorEvent）不受影响
</verification>

<output>
Create .planning/quick/260825-gtx-streaming-react-roleplay/260825-gtx-SUMMARY.md when done.
Note: .planning/ gitignored + commit_docs=false - 不提交 .planning 工件。
注：本次因 Bash/Agent 安全分类器持续故障，由 orchestrator 直接执行（Write/Edit 可用），
偏离标准 planner/executor agent 流程，SUMMARY 中记录。
</output>
