// frontend/src/composables/useSSEStream.ts

import type {
  AgentEventName,
  ReasoningEvent,
  FinalAnswerEvent,
  ErrorEvent,
  ToolCallEvent,
  ToolResultEvent,
} from '@/types/agent'

/**
 * SSE stream options - Phase 2 D-01 event-name routing.
 *
 * Phase 5 routing covers 5 SSE event names:
 * - ReasoningEvent -> onReasoning
 * - FinalAnswerEvent -> onFinal
 * - ErrorEvent -> onError
 * - ToolCallEvent -> onToolCall (Phase 5 ReAct)
 * - ToolResultEvent -> onToolResult (Phase 5 ReAct)
 *
 * Other event names (SubQuestionEvent etc.) are defined in the type system
 * but not emitted yet. They will be added in Phase 6+ when patterns
 * emit them. The composable silently ignores unmapped event names.
 *
 * 回调签名扩展为双参数（content + ev）- 02-UI-SPEC.md §Claude Discretion 决策 1：
 * Phase 2 仅用便捷字段（与 Phase 1 App.vue 调用兼容），Phase 3+ 需要 ev.ts 等
 * 元数据时不必改回调签名。
 * onToolCall/onToolResult 接收完整 event 对象（05-UI-SPEC.md §useSSEStream Refactor）：
 * ToolCallEvent/ToolResultEvent 有多个等重要字段，无单一"便捷字段"。
 */
export interface SSEStreamOptions {
  onReasoning: (content: string, ev: ReasoningEvent) => void
  onFinal: (content: string, ev: FinalAnswerEvent) => void
  onError: (message: string, ev: ErrorEvent) => void
  onToolCall?: (ev: ToolCallEvent) => void
  onToolResult?: (ev: ToolResultEvent) => void
  signal?: AbortSignal
}

/**
 * Start a POST-based SSE stream using fetch + ReadableStream.
 *
 * Phase 2 D-01 重构：按 SSE event name 路由（PascalCase class simple name），
 * 不再按 data.type 路由。回调签名扩展为双参数（content + ev）。未映射事件名
 * 静默忽略（前向兼容，02-UI-SPEC.md §Claude Discretion 决策 2）。
 *
 * Key features (沿用 Phase 1):
 * - Uses fetch (not EventSource) to support POST body (Pitfall #4)
 * - Skips keep-alive comment lines starting with ':' (SSE-08, D-12, Pitfall #5)
 * - Buffers chunks across frame boundaries (chunk != frame boundary)
 * - Parses 'event:' line (D-01 - Phase 1 ignored this line)
 *
 * @param url - The endpoint URL
 * @param body - The request body (will be JSON-stringified)
 * @param options - Stream callbacks and optional AbortSignal
 */
export async function startSSEStream(
  url: string,
  body: object,
  options: SSEStreamOptions
): Promise<void> {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: options.signal,
  })

  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`)
  }

  const reader = resp.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // SSE frames are delimited by \n\n
    // Chunk boundaries don't align with frame boundaries, must buffer
    const frames = buffer.split('\n\n')
    buffer = frames.pop() ?? ''

    for (const frame of frames) {
      const lines = frame.split('\n')
      let eventName: string | null = null
      let dataStr = ''

      for (const line of lines) {
        // SSE-08: Skip keep-alive comment lines (start with ':')
        // DeepSeek sends ': keep-alive' during long reasoning
        if (line.startsWith(':')) continue

        // D-01: Parse 'event:' line (Phase 1 ignored this line)
        // event field = ev.getClass().getSimpleName() (PascalCase)
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          // Parse 'data:' lines (may be multiline, joined by \n)
          dataStr += line.slice(5).trimStart()
        }
      }

      if (!eventName || !dataStr) continue

      try {
        const data = JSON.parse(dataStr)

        // D-01: route by event name (PascalCase class simple name)
        switch (eventName as AgentEventName) {
          case 'ReasoningEvent':
            options.onReasoning(data.content, data)
            break
          case 'FinalAnswerEvent':
            options.onFinal(data.content, data)
            break
          case 'ErrorEvent':
            options.onError(data.message, data)
            break
case 'ToolCallEvent':
            options.onToolCall?.(data as ToolCallEvent)
            break
          case 'ToolResultEvent':
            options.onToolResult?.(data as ToolResultEvent)
            break
          default:
          default:
            // Silently ignore unmapped event names (forward compat,
            // 02-UI-SPEC.md §Claude Discretion 决策 2)
            break
        }
      } catch {
        // JSON parse failed - chunk boundary issue, skip
      }
    }
  }
}
