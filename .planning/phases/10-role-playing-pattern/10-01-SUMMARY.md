---
phase: 10-role-playing-pattern
plan: 01
subsystem: backend-role-playing-core
tags: [role-playing, agent-pattern, sealed-interface, spring-ai]
requires:
  - AgentEvent sealed interface (15 subtypes, Phase 9)
  - AgentPattern contract + AgentRegistry auto-registration
  - ChatClient.Builder auto-configuration (deepseek-chat default)
provides:
  - RolePmEvent / RoleDevEvent / RoleTesterEvent records (ts, round, role, content)
  - AgentEvent sealed interface with 18 permitted subtypes
  - RolePlayingAgentPattern (@Component id="roleplay")
  - 5 rounds x 3 roles fixed-order dialogue + independent summary call
affects:
  - GET /api/patterns (now returns 8 patterns including roleplay)
  - Frontend plans 10-02/10-03 (event routing, ReasoningPanel rendering)
tech-stack:
  added: []
  patterns:
    - "Flux.create sink sequential emission (same as Reflexion/ToT)"
    - "Per-role defaultSystem(...) ChatClient with history-as-user-message text"
key-files:
  created:
    - src/main/java/com/agents/agent/core/RolePmEvent.java
    - src/main/java/com/agents/agent/core/RoleDevEvent.java
    - src/main/java/com/agents/agent/core/RoleTesterEvent.java
    - src/main/java/com/agents/agent/patterns/RolePlayingAgentPattern.java
    - src/test/java/com/agents/agent/patterns/RolePlayingAgentPatternTest.java
  modified:
    - src/main/java/com/agents/agent/core/AgentEvent.java
    - src/test/java/com/agents/agent/core/AgentEventTest.java
    - src/test/java/com/agents/api/PatternControllerWithMockTest.java
decisions:
  - "Utterance record made package-private to allow direct unit testing of buildHistoryPrompt (plan explicitly allowed this option)"
  - "History passed as plain-text user message (not MessageChatMemory) to avoid conflict with per-role system prompts"
metrics:
  duration: 6m 15s
  completed: 2026-08-21T11:19:50Z
---

# Phase 10 Plan 01: Role-playing Backend Core Summary

**One-liner:** Role-playing 模式后端核心——新增 3 个角色事件 record（AgentEvent 扩至 18 子类型）+ RolePlayingAgentPattern（5 轮 × PM/Dev/Tester 固定顺序对话、完整历史纯文本传递、第 16 次 LLM 调用生成 FinalAnswerEvent 总结）。

## What Was Built

### Task 1: 3 个角色事件 record + AgentEvent permits 18（commit a285084）

- `RolePmEvent` / `RoleDevEvent` / `RoleTesterEvent`：字段均为 `(Instant ts, int round, String role, String content)`，per D-03。`round` 为 1-5，`role` 固定为 "PM"/"Dev"/"Tester"。
- `AgentEvent.java`：permits 子句在 ReflexionReflectEvent 之后、FinalAnswerEvent 之前插入 3 个新 subtype（15 -> 18），JavaDoc 同步更新。
- sealed 穷尽性在编译期生效：Task 3 的 pattern switch 测试必须加 3 个 case 才能编译通过。

### Task 2: RolePlayingAgentPattern（commit 52d3ce3）

- `@Component` 实现 `AgentPattern`：`id()="roleplay"`、`displayName()="Role-playing 角色扮演"`、`description()="多智能体分工协作"`。AgentRegistry 自动注册，`GET /api/patterns` 返回 8 个模式。
- **仅注入 `ChatClient.Builder`**（无 ToolRegistry，per D-05），使用默认 deepseek-chat 模型。
- 5 轮循环（`ROUNDS=5`），每轮固定顺序 PM -> Dev -> Tester，共 15 次角色 LLM 调用；每次调用以 `defaultSystem(角色prompt).build().prompt().user(历史文本).call().content()` 完整返回后立即 `sink.next(角色事件)`。
- 对话历史以 `List<Utterance>` 维护（内部 record：round/role/content），`buildHistoryPrompt` 格式化为纯文本 "Round N Role: content" 传入下一角色的 user message（per D-02，完整历史传递）。
- 3 个独立 system prompt（per D-07 持续协作：PM 追问 / Dev 迭代改进 / Tester 逐轮验证），均含 `{question}` 占位符替换。
- 第 16 次独立总结调用（`SUMMARY_PROMPT` 含完整对话文本）以 `FinalAnswerEvent` 发射结论（per D-04）。
- 威胁缓解：null content 降级为 `""`（T-10-01）；整体 try-catch 兜底发射 `ErrorEvent` 并 complete（T-10-02）；顺序同步循环无乱序可能（T-10-03）；`buildHistoryPrompt` 独立方法格式单一（T-10-04）；pom.xml 未变，无新增依赖（T-10-SC）。

### Task 3: 测试更新（commit 06e0c38）

- `AgentEventTest`：`shouldInstantiateAllFifteenEventRecords` 重命名为 `shouldInstantiateAllEighteenEventRecords`，3 个新 record 的实例化 + ts 断言 + 访问器断言 + sealed switch 穷尽性 case 全部覆盖。
- 新建 `RolePlayingAgentPatternTest`（5 个测试方法）：
  1. `shouldExecuteFullRolePlayCycleAndEmitEvents` -- 16 次调用完整循环，事件顺序 PM(1)→Dev(1)→Tester(1)→…→Tester(5)→FinalAnswer，round/role/content 逐项断言，`verify(callResponseSpec, times(16))`。
  2. `shouldEmitRoleEventsInCorrectOrder` -- 每轮内固定 PM→Dev→Tester 顺序 + round 递增 1-5。
  3. `shouldEmitErrorOnChatClientFailure` -- 首次调用抛异常 -> 1 个 ErrorEvent，message 含 "Role-playing"。
  4. `shouldBuildHistoryPromptCorrectly` -- 空历史（首轮）与多轮历史的格式化输出（含 "原始问题: …"、"Round N Role: content" 行序断言）。
  5. `shouldHandleNullContentGracefully` -- PM 首轮返回 null -> content 为 `""` 而非 null，后续角色与总结正常执行（16 事件完整）。
- `PatternControllerWithMockTest`：模式计数断言 7 -> 8，JavaDoc 补充 roleplay（Phase 10）。
- 全量回归：`mvn test` 53 个测试全部通过（BUILD SUCCESS）。

## Verification

- `mvn compile` 成功（sealed permits 18 subtypes 编译期校验通过）。
- `mvn test` 全量通过：Tests run: 53, Failures: 0, Errors: 0。
- `SseEventEmitter.java` 未做任何修改（已验证通用序列化 `getClass().getSimpleName()` 自动处理 3 个新 record 类型）。
- `pom.xml` 未做任何修改（T-10-SC slopcheck：无新增依赖）。
- 手动验证（需 DEEPSEEK_API_KEY + 前端 Phase 10 Plan 02/03 完成后联调）：选择 Role-playing 模式，确认 SSE EventStream 出现 RolePmEvent(1) -> RoleDevEvent(1) -> RoleTesterEvent(1) -> … -> RoleTesterEvent(5) -> FinalAnswerEvent 序列。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 默认 JAVA_HOME 指向 JDK 1.8 导致 mvn compile 失败**
- **Found during:** Task 1 验证
- **Issue:** worktree 环境中 `mvn -version` 显示 Java 1.8.0_411——所有含 `record` 关键字的源文件（含既有 15 个 record）报 "需要class, interface或enum"。这是环境问题（主仓库终端可能另有 JDK 配置），非代码问题。
- **Fix:** 所有 mvn 命令前置 `export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home`（`/usr/libexec/java_home -V` 确认系统已装 JDK 21）。无任何代码变更。
- **Files modified:** 无
- **Note:** 后续在本地复现/联调时需同样设置 JAVA_HOME 为 JDK 21。

**2. [计划内自由裁量] Utterance record 设为 package-private**
- **Found during:** Task 3
- **Issue:** 计划 Task 3 场景 4 要求"如果设计为 package-private 可测试，直接测试 buildHistoryPrompt 输出格式"。private record 会导致测试无法构造历史列表。
- **Fix:** `Utterance` 从 `private record` 改为 package-private `record`，测试直接构造并断言 `buildHistoryPrompt` 输出格式。计划明确将此列为可选设计，非偏离设计意图。
- **Files modified:** `RolePlayingAgentPattern.java`（Task 2 commit 内）

## TDD Gate Compliance

本计划 frontmatter `type: execute`（非 tdd 计划），各任务非 tdd="true"，无 RED/GREEN/REFACTOR 门禁要求。测试与实现同任务提交，覆盖度满足验收标准。

## Known Stubs

无。后端核心完整实现，无占位符/TODO/mock 数据（MockPattern 为既有测试专用组件，非本计划产物）。

## Threat Flags

无新增威胁面。pom.xml 未变（无新依赖）；无新端点（复用既有 SSE 流式端点与 `GET /api/patterns`）；威胁登记表 T-10-01 至 T-10-04、T-10-SC 全部按计划缓解。

## Self-Check: PASSED

- 文件存在性：RolePmEvent.java / RoleDevEvent.java / RoleTesterEvent.java / RolePlayingAgentPattern.java / RolePlayingAgentPatternTest.java 均已创建（git commit 记录确认）。
- 提交存在性：a285084 / 52d3ce3 / 06e0c38 均在 git log 中。
- 测试：53/53 通过。

## Commits

| Task | Commit | Message |
| ---- | ------ | ------- |
| 1 | a285084 | feat(10-01): add 3 Role-playing event records and extend AgentEvent permits to 18 |
| 2 | 52d3ce3 | feat(10-01): implement RolePlayingAgentPattern (5 rounds x 3 roles + summary) |
| 3 | 06e0c38 | test(10-01): update tests for 18 event records, pattern count 8, add RolePlayingAgentPattern tests |
