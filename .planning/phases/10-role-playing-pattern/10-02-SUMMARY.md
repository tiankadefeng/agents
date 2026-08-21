---
phase: 10-role-playing-pattern
plan: 02
subsystem: frontend-role-playing-datalayer
tags: [role-playing, sse-routing, typescript, vue3, frontend]
requires:
  - AgentEventName + AgentEvent union (15 types, Phase 9)
  - SSEStreamOptions callback routing pattern (useSSEStream)
  - PatternSelector isPatternEnabled + PHASE_ONLINE_MAP
  - Backend RolePmEvent/RoleDevEvent/RoleTesterEvent + roleplay pattern (plan 10-01)
provides:
  - RolePmEvent/RoleDevEvent/RoleTesterEvent TS interfaces (ts, round, role, content)
  - AgentEvent union + AgentEventName extended to 18 (3 Role-playing events)
  - useSSEStream routing: onRolePm/onRoleDev/onRoleTester optional callbacks
  - App.vue roleplay onReasoning guard (no reasoningText duplication)
  - roleplay mode enabled in PatternSelector (green '已上线' tag)
  - patternDetails roleplay entry (coreIdea + scenarios) + PatternDescriptionCard title
affects:
  - ReasoningPanel rendering of Role*Event (plan 10-03, 彩色头像/轮次分组)
  - GET /api/patterns consumers (roleplay now selectable)
tech-stack:
  added: []
  patterns:
    - "Optional-chained callback routing with `as` type assertion at parse boundary (same as Phase 8/9)"
    - "Pattern-id guard in onReasoning to suppress non-streaming patterns' ReasoningEvent"
key-files:
  created: []
  modified:
    - frontend/src/types/agent.ts
    - frontend/src/types/sse.ts
    - frontend/src/composables/useSSEStream.ts
    - frontend/src/App.vue
    - frontend/src/components/PatternSelector.vue
    - frontend/src/constants/patternDetails.ts
    - frontend/src/components/PatternDescriptionCard.vue
decisions:
  - "Role-playing events are NOT appended to reasoningText - content carried by Role*Event.content only (per plan must-have)"
  - "onReasoning guard extended to both reflexion and roleplay modes (same call()-once-no-stream rationale)"
  - "worktree fresh checkout had no node_modules; installed with `npm ci --legacy-peer-deps` (pre-existing oxlint peer conflict in package.json, no dependency changes committed)"
metrics:
  duration: 3m 4s
  completed: 2026-08-21T11:27:08Z
---

# Phase 10 Plan 02: Role-playing Frontend Datalayer Summary

**One-liner:** Role-playing 前端数据层--TS 类型镜像 3 个角色事件（AgentEvent/AgentEventName 扩至 18）、useSSEStream 事件路由、App.vue 回调 + roleplay 守卫、PatternSelector 启用 roleplay（绿色已上线）、patternDetails 填充与描述卡标题补全。

## What Was Built

### Task 1: 3 个 Role-playing TS 接口 + AgentEventName + useSSEStream 路由（commit d29f369）

- `types/agent.ts`：新增 `RolePmEvent` / `RoleDevEvent` / `RoleTesterEvent` 接口（字段 `ts: InstantString, round: number, role: string, content: string`，per D-03），位于 ReflexionReflectEvent 之后；`AgentEvent` 联合类型 15 -> 18；顶部注释同步 18 record subtypes。
- `types/sse.ts`：`AgentEventName` 在 'ReflexionReflectEvent' 之后新增 3 个字面量（15 -> 18），注释同步。
- `useSSEStream.ts`：import 3 个新类型；`SSEStreamOptions` 新增可选回调 `onRolePm` / `onRoleDev` / `onRoleTester`；switch 路由 3 个新事件名（可选链 `?.` 调用，`data as Role*Event` 断言在解析边界）；JSDoc 更新为 "Phase 10 routing covers 18 SSE event names"。
- vue-tsc --noEmit 零错误。

### Task 2: App.vue 3 个 Role-playing 回调 + onReasoning 守卫（commit 7176b57）

- `submit()` 的 startSSEStream options 在 onReflexionReflect 之后新增 `onRolePm` / `onRoleDev` / `onRoleTester` 三个回调，均追加到 `agentEvents.value`（不追加 reasoningText）。
- `onReasoning` 守卫从 `reflexion` 单模式扩展为 `reflexion || roleplay`：两个模式的角色发言均为 `.call().content()` 一次性调用，不产生流式 ReasoningEvent；若收到则忽略，避免与 Role*Event 重复。
- vue-tsc --noEmit 零错误。

### Task 3: 启用 roleplay + patternDetails 填充 + 描述卡标题（commit 3a956dd）

- `PatternSelector.vue`：`isPatternEnabled()` 增加 `|| patternId === 'roleplay'`；PHASE_ONLINE_MAP 已有 `roleplay: 10`，模板自动切换为绿色 "已上线" tag。
- `patternDetails.ts`：roleplay 空 `coreIdea`/`scenarios` 条目替换为完整中文描述（三角色固定顺序协作、5 轮对话、多视角适用场景）。
- `PatternDescriptionCard.vue`：card-title 表达式在 reflexion 之后插入 `roleplay` 分支，显示 "Role-playing 角色扮演"（此前回退显示原始 id 字符串）。
- vue-tsc --noEmit 零错误。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] worktree 无 node_modules，vue-tsc 无法执行**
- **Found during:** Task 1 verification
- **Issue:** worktree 为全新检出，`frontend/node_modules` 不存在；`npx vue-tsc` 下载最新版与 TypeScript 6.0 不兼容（ERR_PACKAGE_PATH_NOT_EXPORTED）。
- **Fix:** 在 worktree 内执行 `npm ci --legacy-peer-deps`（既有 package.json/package-lock.json，无依赖变更；ERESOLVE 为 package.json 既有的 eslint-plugin-oxlint@1.73.0 vs oxlint@1.74.0 peer 冲突，非本计划引入）。node_modules 未提交（.gitignore 已覆盖）。
- **Files modified:** 无已提交文件变更
- **Commit:** N/A

### Plan 内部不一致（观察记录，非缺陷）

- must_haves.artifacts 中 `useSSEStream.ts min_lines: 230` 与 `patternDetails.ts min_lines: 100` 高于计划 <action> 规定改动后实际可达行数（223 / 76 行）。计划自身 action 精确规定了改动内容，实际行数即为按 action 执行的结果，未添加填充行。功能完整性与全部 truths/acceptance_criteria 均满足。

## Threat Model Compliance

- T-10-05 (mitigate)：回调可选链 `?.`；路由边界 `data as Role*Event` 断言；未映射事件名静默忽略 - 已实现。
- T-10-SC (mitigate/slopcheck)：`git diff 8af08c1 HEAD -- frontend/package.json frontend/package-lock.json` 为空 - 无新 npm 依赖。
- T-10-06 / T-10-07 (accept)：无 v-html，全部 Vue 文本插值；agentEvents 每次 submit 清空，有界。

## Known Stubs

None - 本计划为数据层与启用层，无 UI 渲染 stub。Role*Event 的可视化渲染（彩色头像、轮次分组）由 plan 10-03 交付（ReasoningPanel），不在本计划范围。

## Verification Results

- `npx vue-tsc --noEmit` 三个 task 后均零错误（覆盖全部 7 个修改文件）
- AgentEvent / AgentEventName 均为 18 个（15 既有 + 3 Role-playing），与后端 10-01 的 sealed interface 18 permits 一一对应
- useSSEStream switch 含 RolePmEvent/RoleDevEvent/RoleTesterEvent 三个 case，default 分支保持静默忽略
- App.vue roleplay 模式下 onReasoning 直接 return（不追加 reasoningText、不追加 agentEvents）
- PatternSelector roleplay 绿色 "已上线"；patternDetails roleplay 条目非空；PatternDescriptionCard 显示 "Role-playing 角色扮演"
- 手动验证（需 DEEPSEEK_API_KEY + 10-01 后端合并）：启动前后端，选择 Role-playing 模式提交问题，DevTools EventStream 确认 Role*Event 依次到达 - 留待 phase 验收
