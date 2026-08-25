// frontend/src/types/sse.ts

/**
 * SSE event name string literals - mirror backend AgentEvent class simple names.
 * D-01: SSE event field = ev.getClass().getSimpleName() (PascalCase).
 * Phase 2 D-01 推翻 Phase 1 D-02 的 event=message 方案。
 *
 * 20 个 event name 与后端 record 类名严格一致（大小写敏感）。
 * 流式改造新增 ReasoningDeltaEvent / RoleSpeechDeltaEvent（临时态增量事件，
 * 完成后由对应完整事件收口，前端用完整事件替换 delta 拼接的临时卡片）。
 */
export type AgentEventName =
  | 'ReasoningEvent'
  | 'ReasoningDeltaEvent' // 流式改造新增（ReAct Thought 增量）
  | 'ToolCallEvent'
  | 'ToolResultEvent'
  | 'SubQuestionEvent'
  | 'SubAnswerEvent'
  | 'PlanEvent'
  | 'StepStartEvent'
  | 'StepCompleteEvent'
  | 'TotNodeEvent'       // Phase 8 新增
  | 'TotPruneEvent'      // Phase 8 新增
  | 'ReflexionAttemptEvent'    // Phase 9 新增
  | 'ReflexionEvaluateEvent'   // Phase 9 新增
  | 'ReflexionReflectEvent'    // Phase 9 新增
  | 'RolePmEvent'       // Phase 10 新增
  | 'RoleDevEvent'      // Phase 10 新增
  | 'RoleTesterEvent'   // Phase 10 新增
  | 'RoleSpeechDeltaEvent' // 流式改造新增（Role-play 发言增量）
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
