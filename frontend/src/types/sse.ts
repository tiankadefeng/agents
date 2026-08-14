// frontend/src/types/sse.ts

/**
 * SSE event name string literals - mirror backend AgentEvent class simple names.
 * D-01: SSE event field = ev.getClass().getSimpleName() (PascalCase).
 * Phase 2 D-01 推翻 Phase 1 D-02 的 event=message 方案。
 *
 * 9 个 event name 与后端 record 类名严格一致（大小写敏感）。
 */
export type AgentEventName =
  | 'ReasoningEvent'
  | 'ToolCallEvent'
  | 'ToolResultEvent'
  | 'SubQuestionEvent'
  | 'SubAnswerEvent'
  | 'PlanEvent'
  | 'StepStartEvent'
  | 'StepCompleteEvent'
  | 'FinalAnswerEvent'
  | 'ErrorEvent'

/**
 * ISO-8601 instant string (e.g., "2026-08-06T07:47:00.957Z").
 * Mirrors backend java.time.Instant serialized via Jackson.
 */
export type InstantString = string

/**
 * Stream status for UI state management.
 * 沿用 Phase 1 - 4 态状态机 + aborted/error。
 */
export type StreamStatus =
  | 'idle'
  | 'thinking'
  | 'answering'
  | 'completed'
  | 'aborted'
  | 'error'
