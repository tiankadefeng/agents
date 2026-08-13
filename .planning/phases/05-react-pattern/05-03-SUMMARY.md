---
phase: 05-react-pattern
plan: 03
subsystem: ui
tags: [vue, sse, timeline, react, agent-event]

requires:
  - phase: 05-02
    provides: ToolCallEventCard, ToolResultEventCard components, SSE event routing infrastructure
affects: []

tech-stack:
  added: []
  patterns: [vertical timeline rendering, AgentEvent list collection, conditional timeline/text rendering]

key-files:
  created: []
  modified:
    - frontend/src/App.vue
    - frontend/src/components/ReasoningPanel.vue
    - frontend/src/composables/useSSEStream.ts

key-decisions:
  - "AgentEvent list uses ref (not shallowRef) due to small event count (< 50 per ReAct cycle)"
  - "onToolCall/onToolCallbacks receive full event object (no single convenience field)"
  - "ReasoningPanel uses template-based conditional rendering (v-if events) for backward compat"

requirements-completed: [UI-11]

duration: 28min
completed: 2026-08-13
---

# Phase 5 Plan 3: ReasoningPanel Vertical Timeline Integration

**Integrated App.vue event collection with ReasoningPanel vertical timeline rendering, enabling Thought/Action/Observation block display for ReAct mode while preserving CoT plain text backward compatibility.**

## Performance

- **Duration:** 28 min
- **Started:** 2026-08-13T09:25:44Z
- **Completed:** 2026-08-13T09:53:33Z
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments

- App.vue now collects all AgentEvent types (ReasoningEvent, ToolCallEvent, ToolResultEvent) into a reactive `agentEvents` array and passes them to ReasoningPanel via the `:events` prop
- SSEStream composable extended with `onToolCall` and `onToolResult` optional callbacks, routing ToolCallEvent/ToolResultEvent SSE events from the stream parser
- ReasoningPanel supports dual rendering modes: vertical timeline (when `events` prop is present) and legacy plain text (backward compatible for CoT)
- Timeline renders 3 block types: Thought (amber dot + ReasoningEvent content), Action (blue dot + ToolCallEventCard), Observation (green dot + ToolResultEventCard)
- Timeline CSS includes vertical connector line, colored circular dots, stage labels, and proper spacing
- TypeScript compilation passes with zero errors; frontend production build succeeds

## Task Commits

1. **Task 1: Refactor App.vue with agentEvents state and event callbacks** - `d9f0dc7` (feat)
2. **Task 2: Refactor ReasoningPanel.vue with vertical timeline** - `5e8d55b` (feat)

## Files Created/Modified

- `frontend/src/App.vue` - Added `agentEvents` ref, `onToolCall`/`onToolResult` callbacks in `submit()`, `:events` prop on ReasoningPanel, cleared on new request
- `frontend/src/composables/useSSEStream.ts` - Added `onToolCall`/`onToolResult` optional callbacks to SSEStreamOptions, ToolCallEvent/ToolResultEvent routing cases in SSE parser
- `frontend/src/components/ReasoningPanel.vue` - Added optional `events` prop, conditional rendering (timeline vs text), timeline CSS, subtitle context-aware text

## Deviations from Plan

None - plan executed exactly as written.

## Threat Flags

None found. All event content rendered as text (no v-html), threat model T-05-01 (XSS) and T-05-02 (overflow) are addressed: reasoning uses text interpolation, event count is bounded by ReAct iteration limit.

## Self-Check: PASSED

- `frontend/src/App.vue` exists and contains `agentEvents`, `onToolCall`, `onToolResult`, `:events` prop
- `frontend/src/composables/useSSEStream.ts` exists and contains `onToolCall?`, `onToolResult?`, ToolCallEvent/ToolResultEvent routing
- `frontend/src/components/ReasoningPanel.vue` exists and contains `events?` prop, timeline template, timeline CSS
- Commit `d9f0dc7` verified
- Commit `5e8d55b` verified
- TypeScript compilation passes (vue-tsc --noEmit exit 0)
- Frontend production build succeeds (vite build exit 0)