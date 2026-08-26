---
phase: quick-260824-tz8
plan: 01
subsystem: api
tags: [sse, exception-handling, regression-test, bugfix]
requires:
  - "SseEventEmitter.fromAgentEvent(AgentEvent)"
  - "ErrorEvent(ts, message) record"
provides:
  - "Pre-stream exception SSE error contract (D-08) on /api/agent/execute error path"
affects:
  - "frontend useSSEStream.ts error path (now receives ErrorEvent frame instead of HTTP 400/500 throw)"
tech-stack:
  added: []
  patterns:
    - "void @ExceptionHandler + HttpServletResponse direct SSE wire-format write (exception-resolver path has no reactive type support)"
key-files:
  created:
    - src/test/java/com/agents/api/GlobalExceptionHandlerTest.java
  modified:
    - src/main/java/com/agents/api/GlobalExceptionHandler.java
decisions:
  - "Handler returns void and writes SSE frame directly via HttpServletResponse - @ExceptionHandler return values bypass ReactiveTypeHandler, so Flux returns are serialized as FluxJust objects and fail with HttpMessageNotWritableException"
  - "Response marked fully handled by declaring HttpServletResponse parameter - Spring performs no further return-value processing"
metrics:
  duration: "~50 min (incl. classifier-outage stalls)"
  completed: 2026-08-24
commits:
  - "ae64088 test: add regression test for pre-stream exception SSE contract (D-08)"
  - "4228602 fix: write SSE ErrorEvent frame directly via HttpServletResponse in GlobalExceptionHandler"
---

# Quick Task 260824-tz8: Fix GlobalExceptionHandler Flux Serialization Summary

**STATUS: COMPLETE.** Code changes were written by the executor; verification (RED +
GREEN + full suite) and both atomic commits were completed by the orchestrator after
the executor's session was cut short by a Bash safety-classifier outage (commands
recovered in the orchestrator session).

## What Was Done

### Task 1: Regression test (RED verified)

- Created `src/test/java/com/agents/api/GlobalExceptionHandlerTest.java`
- `shouldReturnErrorEventFrameForMalformedJsonBody`: POST `/api/agent/execute` with
  `Content-Type: application/json` and raw body `not-json` triggers
  `HttpMessageNotReadableException` during `@RequestBody` resolution (before the
  controller method runs)
- Asserts: HTTP 200, `Content-Type` compatible with `text/event-stream`, body contains
  `event:ErrorEvent`, `HttpMessageNotReadableException`, `data:{`, and ends with `\n\n`
- RED machine-verified by the orchestrator: with the HEAD (old) handler temporarily
  restored, the test fails with
  `AssertionError: Response header 'Content-Type'=[application/json] is not compatible with [text/event-stream]`
  (the advice itself fails, `ExceptionHandlerExceptionResolver` returns null, and
  `DefaultHandlerExceptionResolver` answers plain 400 JSON). The fixed handler was then
  restored from a temp copy.

### Task 2: Fix handler (GREEN verified)

- Rewrote `src/main/java/com/agents/api/GlobalExceptionHandler.java`:
  - Signature: `public void handle(Exception ex, HttpServletResponse response) throws IOException`
    (removed `Flux` return and its import; `grep Flux` on the file returns 0 matches)
  - `log.warn("Pre-stream exception on SSE endpoint, degrading to ErrorEvent frame (D-08)", ex)`
    - full stacktrace server-side only (T-2-06); `com.agents: DEBUG` in application.yml surfaces it
  - `response.setStatus(200)` per D-08, `setContentType(MediaType.TEXT_EVENT_STREAM_VALUE)`,
    `setCharacterEncoding("UTF-8")` before `getWriter()` (exception messages may contain Chinese)
  - Frame built via existing `sseEmitter.fromAgentEvent(new ErrorEvent(Instant.now(),
    ex.getClass().getSimpleName() + ": " + ex.getMessage()))` - message format byte-identical
    (type + message, no stacktrace to client)
  - New private `sseWireFormat(ServerSentEvent<String>)`: `id:{ts}\nevent:{name}\ndata:{json}\n\n`
    (no space after colons - frontend `line.slice(6)/slice(5)`)
  - Class javadoc rewritten: removed the incorrect claim that bare-Flux-with-HttpServletResponse
    works; documented the actual root cause (exception-resolver path lacks reactive return-value
    handling; declaring HttpServletResponse marks the response fully handled)
- Untouched: `AgentController.java`, `PingController.java`, `SseEventEmitter.java` (zero diffs)

## Environment Findings (Deviations)

**1. [Rule 3 - Blocking issue] JDK 8 JAVA_HOME breaks mvn compile**
- `~/.zshrc` sources `~/.bash_profile`, which exports `JAVA_HOME` to JDK 1.8. Maven runs on
  JDK 8, whose javac cannot parse text blocks -> total compile failure with misleading
  "illegal character" errors on every pattern class containing Chinese text-block prompts.
- Fix applied (per-invocation, not persisted): `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
  (Homebrew openjdk@21 = 21.0.10, verified via its `release` file).
- Future executors MUST prefix mvn commands with this JAVA_HOME (or fix the shell profile).

**2. [Tool outage] Bash safety classifier unavailable**
- Every non-allowlisted Bash command failed with "claude-opus-4-7[1m] is temporarily
  unavailable" for the remainder of the session. No tests were run, no commits were made.

## Remaining Steps

None - all steps completed by the orchestrator (see Verification below). The
JDK 8 `JAVA_HOME` note above remains relevant for future mvn invocations.

## Verification

All commands run with `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

1. **RED**: old handler temporarily restored (`git checkout HEAD --` after backing up
   the fix to a temp copy) -> `mvn test -Dtest=GlobalExceptionHandlerTest` ->
   `Tests run: 1, Failures: 1` with `Content-Type'=[application/json] is not compatible
   with [text/event-stream]`. Fix restored afterward (`git status` shows the file
   modified again). Regression coverage is real.
2. **GREEN (targeted)**: `mvn test -Dtest='GlobalExceptionHandlerTest,AgentControllerTest'`
   -> surefire: `GlobalExceptionHandlerTest tests=1 errors=0 failures=0`,
   `AgentControllerTest tests=1 errors=0 failures=0`. Console output includes the new
   WARN log with the full original `HttpMessageNotReadableException` stacktrace
   (server-side only) - previously this exception was completely invisible in logs.
3. **Full suite**: `mvn test` -> 20 test classes, 54 tests, 0 errors, 0 failures,
   0 skipped (integration-tagged DeepSeek tests excluded by Surefire as configured).
   The only console ERROR is the known-harmless netty macOS DNS-native-library warning.
4. `git status` after commits: only unrelated untracked items (`agent介绍/`,
   `风格候选.html`) remain - nothing from this task leaked into or out of scope.

## Self-Check

- Commit `ae64088`: `src/test/java/com/agents/api/GlobalExceptionHandlerTest.java` only
  (65 insertions).
- Commit `4228602`: `src/main/java/com/agents/api/GlobalExceptionHandler.java` only
  (47 insertions, 14 deletions).
- `git log --oneline -2` matches; working tree clean of task files after commits.
- `AgentController.java`, `PingController.java`, `SseEventEmitter.java`: zero diffs.
