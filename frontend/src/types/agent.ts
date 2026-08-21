// frontend/src/types/agent.ts

import type { InstantString } from './sse'

// Re-export AgentEventName so agent.ts is the single source of truth for
// AgentEvent-related types. Plan 02-05 imports AgentEventName from
// '@/types/agent' (per 02-UI-SPEC.md §Phase 2 SSE Event Name Routing Contract).
export type { AgentEventName } from './sse'

/**
 * PatternInfo - mirrors backend com.agents.api.dto.PatternInfo record.
 * Returned by GET /api/patterns.
 */
export interface PatternInfo {
  id: string
  displayName: string
  description: string
}

/**
 * AgentRequest - mirrors backend com.agents.api.dto.AgentRequest record.
 * Sent to POST /api/agent/execute as request body.
 */
export interface AgentRequest {
  patternId: string
  question: string
  options?: Record<string, unknown>
}

/**
 * AgentEvent - discriminated union mirroring backend sealed interface
 * com.agents.agent.core.AgentEvent and its 18 record subtypes.
 *
 * Discriminant: SSE event field (= Java class simple name, PascalCase).
 * Phase 2 D-01 推翻 Phase 1 D-02 的 event=message + data.type 方案。
 */
export type AgentEvent =
  | ReasoningEvent
  | ToolCallEvent
  | ToolResultEvent
  | SubQuestionEvent
  | SubAnswerEvent
  | PlanEvent
  | StepStartEvent
  | StepCompleteEvent
  | TotNodeEvent        // Phase 8 新增
  | TotPruneEvent       // Phase 8 新增
  | ReflexionAttemptEvent    // Phase 9 新增
  | ReflexionEvaluateEvent   // Phase 9 新增
  | ReflexionReflectEvent    // Phase 9 新增
  | RolePmEvent         // Phase 10 新增
  | RoleDevEvent        // Phase 10 新增
  | RoleTesterEvent     // Phase 10 新增
  | FinalAnswerEvent
  | ErrorEvent

/**
 * ReasoningEvent - mirrors backend com.agents.agent.core.events.ReasoningEvent.
 * CoT 思维链 chunk (Phase 3 用).
 */
export interface ReasoningEvent {
  ts: InstantString
  content: string
}

/**
 * ToolCallEvent - mirrors backend com.agents.agent.core.events.ToolCallEvent.
 * ReAct 工具调用 (Phase 5 用).
 */
export interface ToolCallEvent {
  ts: InstantString
  toolName: string
  arguments: Record<string, unknown>
}

/**
 * ToolResultEvent - mirrors backend com.agents.agent.core.events.ToolResultEvent.
 * ReAct 工具结果 (Phase 5 用).
 */
export interface ToolResultEvent {
  ts: InstantString
  toolName: string
  result: string
  isError: boolean
}

/**
 * SubQuestionEvent - mirrors backend com.agents.agent.core.events.SubQuestionEvent.
 * Self-Ask 子问题 (Phase 6 用).
 */
export interface SubQuestionEvent {
  ts: InstantString
  question: string
}

/**
 * SubAnswerEvent - mirrors backend com.agents.agent.core.events.SubAnswerEvent.
 * Self-Ask 子问题答案 (Phase 6 用).
 */
export interface SubAnswerEvent {
  ts: InstantString
  question: string
  answer: string
}

/**
 * Step - Plan-and-Execute 步骤定义 (Phase 7).
 * 每步含步骤号、描述、预期输出。
 */
export interface Step {
  stepNumber: number
  description: string
  expectedOutput: string
}

/**
 * PlanEvent - mirrors backend com.agents.agent.core.events.PlanEvent.
 * Plan-and-Execute 计划生成 (Phase 7 用).
 * D-01: 从 String description 改为 List<Step> steps。
 */
export interface PlanEvent {
  ts: InstantString
  steps: Step[]
}

/**
 * StepStartEvent - mirrors backend com.agents.agent.core.events.StepStartEvent.
 * Plan-and-Execute 步骤开始 (Phase 7 用).
 */
export interface StepStartEvent {
  ts: InstantString
  stepNumber: number
  description: string
}

/**
 * StepStatus - Plan-and-Execute 步骤状态 (Phase 7).
 * "done" 表示成功完成，"failed" 表示失败。
 */
export type StepStatus = 'done' | 'failed'

/**
 * StepCompleteEvent - mirrors backend com.agents.agent.core.events.StepCompleteEvent.
 * Plan-and-Execute 步骤完成 (Phase 7 用).
 * D-08: 增加 result 字段，status 取值 "done" 或 "failed"。
 */
export interface StepCompleteEvent {
  ts: InstantString
  stepNumber: number
  status: StepStatus
  result: string        // Phase 7 新增
}

/**
 * TotNodeEvent - mirrors backend com.agents.agent.core.TotNodeEvent.
 * Tree of Thoughts 树节点 (Phase 8 用).
 * level=-1 表示根节点（原始问题），parentId 为 null 表示根节点。
 */
export interface TotNodeEvent {
  ts: InstantString
  level: number
  nodeId: number
  thought: string
  score: number
  parentId: number | null
}

/**
 * TotPruneEvent - mirrors backend com.agents.agent.core.TotPruneEvent.
 * Tree of Thoughts 剪枝事件 (Phase 8 用).
 */
export interface TotPruneEvent {
  ts: InstantString
  level: number
  prunedNodeIds: number[]
  reason: string
}

/**
 * ReflexionAttemptEvent - mirrors backend com.agents.agent.core.ReflexionAttemptEvent.
 * Reflexion 每轮尝试的答案内容 (Phase 9 用).
 */
export interface ReflexionAttemptEvent {
  ts: InstantString
  round: number
  answer: string
}

/**
 * ReflexionEvaluateEvent - mirrors backend com.agents.agent.core.ReflexionEvaluateEvent.
 * Reflexion 评估结果（分数 + 反馈）(Phase 9 用).
 */
export interface ReflexionEvaluateEvent {
  ts: InstantString
  round: number
  score: number
  feedback: string
}

/**
 * ReflexionReflectEvent - mirrors backend com.agents.agent.core.ReflexionReflectEvent.
 * Reflexion 反思内容（下一轮改进依据）(Phase 9 用).
 */
export interface ReflexionReflectEvent {
  ts: InstantString
  round: number
  reflection: string
}

/**
 * RolePmEvent - mirrors backend com.agents.agent.core.RolePmEvent.
 * PM（产品经理）角色发言事件 (Phase 10 用).
 */
export interface RolePmEvent {
  ts: InstantString
  round: number
  role: string
  content: string
}

/**
 * RoleDevEvent - mirrors backend com.agents.agent.core.RoleDevEvent.
 * Dev（开发者）角色发言事件 (Phase 10 用).
 */
export interface RoleDevEvent {
  ts: InstantString
  round: number
  role: string
  content: string
}

/**
 * RoleTesterEvent - mirrors backend com.agents.agent.core.RoleTesterEvent.
 * Tester（测试工程师）角色发言事件 (Phase 10 用).
 */
export interface RoleTesterEvent {
  ts: InstantString
  round: number
  role: string
  content: string
}

/**
 * FinalAnswerEvent - mirrors backend com.agents.agent.core.events.FinalAnswerEvent.
 * 最终答案 (所有模式末尾).
 */
export interface FinalAnswerEvent {
  ts: InstantString
  content: string
}

/**
 * ErrorEvent - mirrors backend com.agents.agent.core.events.ErrorEvent.
 * 错误事件 (含异常 type + message，无 stacktrace).
 */
export interface ErrorEvent {
  ts: InstantString
  message: string
}
