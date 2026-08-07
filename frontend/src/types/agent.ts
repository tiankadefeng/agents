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
 * com.agents.agent.core.AgentEvent and its 9 record subtypes.
 *
 * Discriminant: SSE event field (= Java class simple name, PascalCase).
 * Phase 2 D-01 推翻 Phase 1 D-02 的 event=message + data.type 方案。
 */
export type AgentEvent =
  | ReasoningEvent
  | ToolCallEvent
  | ToolResultEvent
  | SubQuestionEvent
  | PlanEvent
  | StepStartEvent
  | StepCompleteEvent
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
 * PlanEvent - mirrors backend com.agents.agent.core.events.PlanEvent.
 * Plan-and-Execute 计划生成 (Phase 7 用).
 */
export interface PlanEvent {
  ts: InstantString
  /** placeholder - Phase 7 将扩展为 List<String> steps */
  description: string
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
 * StepCompleteEvent - mirrors backend com.agents.agent.core.events.StepCompleteEvent.
 * Plan-and-Execute 步骤完成 (Phase 7 用).
 */
export interface StepCompleteEvent {
  ts: InstantString
  stepNumber: number
  /** placeholder - Phase 7 可能加 String result */
  status: string
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
