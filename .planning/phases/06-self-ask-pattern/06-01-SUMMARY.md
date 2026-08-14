---
phase: 06-self-ask-pattern
plan: 01
subsystem: backend
tags: [self-ask, agent-pattern, json-parsing, jackson, spring-ai]

requires:
  - phase: 02-agent-abstraction
    provides: AgentEvent sealed interface, AgentPattern contract, SubQuestionEvent record, Flux<AgentEvent> streaming pattern
  - phase: 03-cot-pattern
    provides: ChatClient.Builder injection pattern, @Component auto-registration, system prompt strategy
  - phase: 05-react-pattern
    provides: Flux.create sink pattern, error handling pattern, tools.jackson import convention

provides:
  - SubAnswerEvent record (ts, question, answer) — paired with SubQuestionEvent
  - SelfAskAgentPattern (@Component, id=selfAsk) — single ChatClient.call() + JSON parsing + event emission
  - extractJson() preprocessing helper — strips markdown wrapping before Jackson parsing
  - ErrorEvent fallback for blank/invalid/empty JSON responses

affects: [phase 06 plan 02 (frontend integration)]

tech-stack:
  added: []
  patterns:
    - "Single LLM call + full JSON parse: ChatClient.call() -> ObjectMapper.readTree() -> per-element emission"
    - "extractJson() preprocessing: strip ```json/``` markers, extract first { to last } before Jackson parse"
    - "ErrorEvent fallback chain: blank response -> empty sub_questions -> invalid JSON -> generic Exception"

key-files:
  created:
    - src/main/java/com/agents/agent/core/SubAnswerEvent.java
    - src/main/java/com/agents/agent/patterns/SelfAskAgentPattern.java
    - src/test/java/com/agents/agent/patterns/SelfAskAgentPatternTest.java
  modified:
    - src/main/java/com/agents/agent/core/AgentEvent.java
    - src/test/java/com/agents/agent/core/AgentEventTest.java
    - src/test/java/com/agents/api/PatternControllerWithMockTest.java

key-decisions:
  - "SubAnswerEvent(ts, question, answer) record — question field mirrors SubQuestionEvent for frontend pairing display"
  - "Single ChatClient.call() (not stream()) — Self-Ask needs full JSON response before parsing, no per-chunk streaming benefit"
  - "Flux.create sink pattern (matching ReActAgentPattern) — explicit control over emission order and completion"
  - "package-private extractJson() — enables direct unit test without reflection"

patterns-established:
  - "SubAnswerEvent paired with SubQuestionEvent: each sub-question emits SubQuestionEvent then SubAnswerEvent at same timestamp"
  - "ErrorEvent fallback: blank -> empty sub_questions -> invalid JSON -> generic Exception, each with distinct Chinese message"
  - "Self-Ask JSON schema: { sub_questions: [{question, answer}], final_answer }"

requirements-completed: [PATTERN-02]

duration: 37min
completed: 2026-08-14
---

# Phase 6 Plan 1: Self-Ask Backend Core Summary

**SubAnswerEvent record + SelfAskAgentPattern with single ChatClient.call(), tools.jackson JSON parsing, and Flux.create event emission**

## Performance

- **Duration:** 37 min
- **Started:** 2026-08-14T09:28:00+08:00
- **Completed:** 2026-08-14T18:05:00+08:00
- **Tasks:** 2 (committed as 3 commits — 1 regression fix commit)
- **Files modified:** 6

## Accomplishments

- Created `SubAnswerEvent(ts, question, answer)` record implementing AgentEvent sealed interface, paired with SubQuestionEvent
- Updated AgentEvent permits clause to include SubAnswerEvent (10 record subtypes total)
- Updated AgentEventTest: 10-record instantiation, SubAnswerEvent component accessor assertions, exhaustive switch branch
- Implemented `SelfAskAgentPattern` with `@Component`, `id="selfAsk"`, `displayName="Self-Ask 自问自答"`
- Single `ChatClient.call()` returns full JSON, parsed by `tools.jackson ObjectMapper.readTree()`
- `extractJson()` package-private helper strips ```json/``` markers before Jackson parsing
- Per-element emission: SubQuestionEvent -> SubAnswerEvent -> ... -> FinalAnswerEvent
- ErrorEvent fallback chain: blank response, empty sub_questions, invalid JSON, generic Exception
- 4 unit tests covering valid JSON, invalid JSON, empty sub_questions, markdown-wrapped JSON
- Full regression suite (42 tests) green — PatternControllerWithMockTest updated to expect 4 patterns (cot, react, selfAsk, mock)

## Task Commits

Each task was committed atomically:

1. **Task 1: SubAnswerEvent + AgentEvent permits + AgentEventTest** - `e83c4cd` (feat)
2. **Task 2: SelfAskAgentPattern + SelfAskAgentPatternTest** - `3447d41` (feat)
3. **Regression fix: PatternControllerWithMockTest assertion** - `6711cb2` (fix)

## Files Created/Modified

- `src/main/java/com/agents/agent/core/SubAnswerEvent.java` (NEW) - record(Instant ts, String question, String answer) implements AgentEvent
- `src/main/java/com/agents/agent/core/AgentEvent.java` (MODIFIED) - permits clause includes SubAnswerEvent
- `src/main/java/com/agents/agent/patterns/SelfAskAgentPattern.java` (NEW) - @Component AgentPattern, single call + JSON parse + event emission
- `src/test/java/com/agents/agent/core/AgentEventTest.java` (MODIFIED) - 10-record instantiation, SubAnswerEvent assertions, switch branch
- `src/test/java/com/agents/agent/patterns/SelfAskAgentPatternTest.java` (NEW) - 4 test methods
- `src/test/java/com/agents/api/PatternControllerWithMockTest.java` (MODIFIED) - pattern count assertion 2 -> 4

## Decisions Made

- **SubAnswerEvent includes question field** — mirrors SubQuestionEvent.question for frontend pairing display (sub-question -> sub-answer visual chain)
- **Single ChatClient.call() not stream()** — Self-Ask needs full JSON response before parsing; no per-chunk streaming benefit unlike CoT/ReAct
- **Flux.create sink pattern** — matches ReActAgentPattern; explicit control over emission order (SubQuestion then SubAnswer sequentially)
- **package-private extractJson()** — enables direct unit test from same package without reflection, while keeping it non-public API surface
- **ChatClient.ChatClientRequestSpec mock** — uses Spring AI 2.0's fluent API (`prompt()` -> `ChatClientRequestSpec` -> `user()` -> `call()` -> `CallResponseSpec` -> `content()`)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] PatternControllerWithMockTest hardcoded pattern count**
- **Found during:** Task 2 verification (full regression suite)
- **Issue:** `PatternControllerWithMockTest` asserted `$.length() == 2` (cot + mock), but adding selfAsk makes it 3 registered patterns (cot, react, selfAsk) + mock = 4
- **Fix:** Updated assertion from 2 to 4
- **Files modified:** `src/test/java/com/agents/api/PatternControllerWithMockTest.java`
- **Verification:** Full regression suite passes (42 tests)
- **Committed in:** `6711cb2` (separate fix commit after Task 2)

**2. [Rule 3 - Blocking] ChatClient fluent API inner class names differ from plan**
- **Found during:** Task 2 test compilation
- **Issue:** Plan assumed `ChatClient.PromptSpec` / `ChatClient.PromptUserSpec` / `ChatClient.PromptSpec` inner types, but Spring AI 2.0 uses `ChatClient.ChatClientRequestSpec` (which combines prompt/user/call) and `ChatClient.CallResponseSpec`
- **Fix:** Refactored mock to use `ChatClient.ChatClientRequestSpec` for the `prompt()` -> `user()` -> `call()` chain, and `ChatClient.CallResponseSpec` for `content()`
- **Files modified:** `src/test/java/com/agents/agent/patterns/SelfAskAgentPatternTest.java` (inline in Task 2 commit)
- **Verification:** All 4 SelfAskAgentPatternTest tests pass
- **Committed in:** `3447d41` (part of Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — blocking)
**Impact on plan:** Both auto-fixes required for correct compilation and regression. No scope creep.

## Issues Encountered

- Spring AI 2.0's `ChatClient` fluent API uses `ChatClientRequestSpec` (not separate `PromptSpec`/`PromptUserSpec` as the plan described). The `prompt()` method returns `ChatClientRequestSpec` which combines `.user()`, `.call()`, and `.stream()` in one interface. Mock chain: `prompt()` -> `ChatClientRequestSpec` -> `user()` -> `ChatClientRequestSpec` -> `call()` -> `CallResponseSpec` -> `content()`.

## User Setup Required

None — no external service configuration required. This plan is pure backend code with no new dependencies.

## Next Phase Readiness

- Backend Self-Ask core is ready for Phase 6 Plan 2 (frontend integration)
- `id="selfAsk"` pattern is auto-registered via `@Component` — `AgentRegistry` will discover it
- Frontend will need: `SubAnswerEvent` TS type, `useSSEStream` routing, `ReasoningPanel` timeline branches, `patternDetails` content, `PatternSelector` enablement

## Self-Check: PASSED

### Created files exist
- `src/main/java/com/agents/agent/core/SubAnswerEvent.java` — FOUND
- `src/main/java/com/agents/agent/patterns/SelfAskAgentPattern.java` — FOUND
- `src/test/java/com/agents/agent/patterns/SelfAskAgentPatternTest.java` — FOUND

### Commits exist
- `e83c4cd` — FOUND
- `3447d41` — FOUND
- `6711cb2` — FOUND

### Regression suite
- 42 tests, 0 failures, 0 errors — PASSED

---
*Phase: 06-self-ask-pattern (Plan 01)*
*Completed: 2026-08-14*