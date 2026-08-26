---
phase: quick-260825-gtx
plan: 01
subsystem: patterns, frontend
tags: [sse, streaming, react, roleplay, delta-events]
requires:
  - "MessageAggregator aggregate/透传契约（SSE-06 已验证）"
  - "SseEventEmitter.fromAgentEvent(AgentEvent)"
provides:
  - "ReAct 每轮 Thought 流式（ReasoningDeltaEvent 逐 chunk + 完整 ReasoningEvent 收口）"
  - "Role-play 每角色发言流式（RoleSpeechDeltaEvent + 完整 Role*Event 收口）+ 总结轮多 chunk FinalAnswerEvent"
  - "共享流式原语 StreamingLlmCall（其余 4 模式可低成本采纳）"
  - "前端临时卡片/气泡逐字生长渲染（activeThought/activeSpeech 分组协议）"
affects:
  - "前端 useSSEStream/App.vue/ReasoningPanel（新事件路由与渲染）"
  - "AgentEvent sealed permits（18 -> 20）"
tech-stack:
  added: []
  patterns:
    - "delta 临时态事件 + 完整权威态事件双发（旧前端静默忽略新事件名，向后兼容）"
    - "StreamingLlmCall: MessageAggregator + doOnNext(delta) + blockLast（命令式循环保留）"
key-files:
  created:
    - src/main/java/com/agents/agent/core/ReasoningDeltaEvent.java
    - src/main/java/com/agents/agent/core/RoleSpeechDeltaEvent.java
    - src/main/java/com/agents/agent/patterns/StreamingLlmCall.java
    - src/test/java/com/agents/agent/patterns/StreamingLlmCallTest.java
  modified:
    - src/main/java/com/agents/agent/core/AgentEvent.java
    - src/main/java/com/agents/agent/patterns/ReActAgentPattern.java
    - src/main/java/com/agents/agent/patterns/RolePlayingAgentPattern.java
    - src/test/java/com/agents/agent/patterns/ReActAgentPatternTest.java
    - src/test/java/com/agents/agent/patterns/RolePlayingAgentPatternTest.java
    - src/test/java/com/agents/agent/core/AgentEventTest.java
    - frontend/src/types/sse.ts
    - frontend/src/types/agent.ts
    - frontend/src/composables/useSSEStream.ts
    - frontend/src/App.vue
    - frontend/src/components/ReasoningPanel.vue
decisions:
  - "delta 事件为传输层临时态，完成后仍发完整事件（source of truth），前端用完整事件替换临时卡片--最终渲染与现状一致且旧前端向后兼容"
  - "复用 MessageAggregator 而非自写聚合；doOnNext 放 aggregate 之前作用于原始 chunk"
  - "不做 concatMap 全响应式重构--ReAct 命令式循环（可变历史/dedupCache/提前终止）是 D-01 教学可见性设计意图"
  - "ReAct reasoning_content 与 content chunk 均映射 ReasoningDeltaEvent；roleplay 丢弃 reasoning_content（与现状仅取 content 对齐）"
  - "roleplay 总结轮改多 chunk FinalAnswerEvent（与 CoT 契约一致，前端 onFinal 本就是 +=）"
metrics:
  duration: "~75 min（含分类器故障等待）"
  completed: 2026-08-25
commits:
  - "601aa56 feat: stream ReAct thought deltas via StreamingLlmCall helper"
  - "63fcbaa feat: stream Role-playing speeches and summary via delta events"
  - "e1f5760 feat: render streaming deltas as live thought cards and speech bubbles"
---

# Quick Task 260825-gtx: ReAct / Role-playing 真流式输出 Summary

**STATUS: COMPLETE.** 3 个原子提交（后端基建+ReAct / Roleplay 后端 / 前端渲染），
全套后端测试与前端类型检查、生产构建全绿。

## 背景与根因

用户报告"所有客户端的回答都不是流式的"（等很久后一次性出现）。排查确认：基础设施层
（filter/压缩/代理/前端渲染）干净，根因在 pattern 层--7 模式中仅 CoT 用 `.stream()` 逐 chunk
发射，其余 6 种为"阻塞 `.call()`/`blockLast()` + 收集完整回答后才发整段事件"。
用户决策：只修 ReAct + Role-playing（感知最明显的两个），并设计共享原语供其余模式后续采纳。
完整设计与决策见已批准计划（~/.claude/plans/warm-beaming-newt.md）。

## 实施内容

### Commit 1 (601aa56): 后端基建 + ReAct 真流式

- `ReasoningDeltaEvent(ts, content)` record（agent.core，sealed 同包约束）
- `StreamingLlmCall.streamAndAggregate(flux, onReasoningDelta, onContentDelta)` /
  `streamContent(flux, onContentDelta)`（MessageAggregator + doOnNext delta + blockLast）
- `ReActAgentPattern` 每轮调用期间逐 chunk 发 ReasoningDeltaEvent，轮末仍发完整
  ReasoningEvent；控制流（tool call 解析/final_answer 提取/MAX_ITERATIONS）零改动
- 测试：StreamingLlmCallTest 6 用例 + ReActAgentPatternTest 新增 2 用例
  （deltas 先于 ToolCallEvent 且聚合参数完整；Flux.error -> 单 ErrorEvent）

### Commit 2 (63fcbaa): Roleplay 后端真流式

- `RoleSpeechDeltaEvent(ts, round, role, content)` record（(round, role) 为气泡分组键）
- `RolePlayingAgentPattern.callRole -> streamRole`（StreamingLlmCall.streamContent +
  RoleSpeechDeltaEvent delta），完整 Role*Event 保留；总结轮多 chunk FinalAnswerEvent
- RolePlayingAgentPatternTest 重写为 `.stream()` mock 链 + filter-by-type 断言；
  新增 3-chunk delta 拼接先于完整事件的用例

### Commit 3 (e1f5760): 前端流式渲染

- `useSSEStream`：onReasoningDelta/onRoleSpeechDelta 可选回调（20 事件名路由）
- `App.vue`：activeThought/activeSpeech 临时态 + 分组协议（非 delta 事件关闭 thought 卡片；
  匹配 (round, role) 的完整事件关闭气泡）；delta 不进 agentEvents 时间线
- `ReasoningPanel`：ReAct timeline 末尾"思考中"卡片（光标 ▍ 动画）；roleplay 分支
  临时发言气泡（复用 roleplay-card + 角色颜色 + "正在发言..."徽标）；isRoleplayMode
  放宽为首个 delta 即建卡

## 验证

1. `JAVA_HOME=...openjdk@21... mvn test -q`：22 个测试类全绿（0 errors 0 failures），
   含新增 StreamingLlmCallTest(6)、重写 RolePlayingAgentPatternTest(5)、
   ReActAgentPatternTest(7，含 2 新用例)、AgentEventTest(3，20 records 穷尽)
2. `npx vue-tsc --noEmit` 通过；`npm run build` 成功（chunk 大小警告为既有问题）
3. 冒烟（需用户重启后端后进行）：浏览器选 ReAct 提问观察 thought 卡片逐字生长 +
   工具卡片随后出现；Role-playing 观察角色气泡逐字生长、轮次切换、总结流式

## Deviations

- **执行方式**：因 Bash/Agent 安全分类器多次故障，未走标准 gsd-planner/gsd-executor
  agent 流程，由 orchestrator 直接执行（PLAN.md 手动转录已批准计划）。GSD 工件
  （PLAN/SUMMARY/STATE.md）照常维护。
- **ReActAgentPatternTest 新增用例数**：计划为 3 个（含 reasoningContent chunk 用例），
  实际 2 个--reasoning delta 路径已由 StreamingLlmCallTest 的
  shouldEmitReasoningDeltasForDeepSeekAssistantMessage 覆盖（避免 DeepSeekAssistantMessage
  构造不确定性在两处重复）。
- **AgentEventTest**：计划未列出此文件，但 sealed permits 扩展触及其穷尽 switch 与
  record 实例化断言，必须同步更新（编译期即失败，符合该测试的设计意图）。
- **StreamingLlmCallTest 空 Flux 断言**：MessageAggregator 对空 Flux 也会调用 consumer
  （生成空 ChatResponse 而非 null），测试按实际行为断言（streamContent 仍降级 ""）。

## Self-Check

- Commit 601aa56：7 files（3 新建 + 4 修改），326 insertions
- Commit 63fcbaa：3 files（1 新建 + 2 修改），181 insertions / 105 deletions
- Commit e1f5760：5 files（全修改），176 insertions
- 工作区干净（仅无关未跟踪项 agent介绍/、风格候选.html 与 gitignored .planning）
- 未触碰：AgentController、SseEventEmitter、GlobalExceptionHandler、CoTAgentPattern、
  PingController、其余 4 模式、application.yml
