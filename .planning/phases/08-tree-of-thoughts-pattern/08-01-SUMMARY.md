---
phase: 08-tree-of-thoughts-pattern
plan: 01
subsystem: api
tags: [spring-ai, tree-of-thoughts, bfs, sse, agent-pattern, sealed-interface]

# Dependency graph
requires:
  - phase: 07-plan-and-execute-pattern
    provides: Flux.create multi-stage emission pattern, ChatClient.Builder + defaultSystem injection, extractJson() + ObjectMapper.readTree JSON parsing, FinalAnswerEvent/ErrorEvent fallback conventions
provides:
  - "TotNodeEvent / TotPruneEvent records as AgentEvent sealed subtypes (12 total)"
  - "TreeOfThoughtsAgentPattern: BFS expand (branching=3, depth=3), per-node LLM scoring (1-10), top-K=2 greedy pruning with TotPruneEvent, best-path backtracking + SUMMARIZE"
  - "MAX_TOTAL_NODES=50 fuse with graceful degradation to best-effort answer"
  - "Robust score/branch parsing (parseScoreSafely / parseBranches) that never throws into the stream"
affects: [08-02 (frontend tree visualization), phase 09 reflexion, phase 10 role-playing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BFS layered expansion with per-node LLM evaluation and greedy top-K pruning"
    - "Multi-strategy tolerant score parsing (regex -> keyword -> default) for unstructured LLM numeric output"
    - "Fuse pattern: hard node-count cap degrades to best-effort path answer instead of hard failure"

key-files:
  created:
    - src/main/java/com/agents/agent/core/TotNodeEvent.java
    - src/main/java/com/agents/agent/core/TotPruneEvent.java
    - src/main/java/com/agents/agent/patterns/TreeOfThoughtsAgentPattern.java
  modified:
    - src/main/java/com/agents/agent/core/AgentEvent.java
    - src/test/java/com/agents/agent/core/AgentEventTest.java
    - src/test/java/com/agents/api/PatternControllerWithMockTest.java

key-decisions:
  - "D-03: TotNodeEvent fields (ts, level, nodeId, thought, score, parentId); TotPruneEvent fields (ts, level, prunedNodeIds, reason); parentId null for root, frontend rebuilds tree"
  - "D-01: BFS (breadth-first layered expansion) chosen over DFS for teaching value"
  - "D-02: LLM scoring 1-10 per node with tolerant parsing; DEFAULT_SCORE=5 fallback, never throws"
  - "D-04: deepseek-chat only, no ToolRegistry injection (pure LLM reasoning)"
  - "BRANCHING=3, DEPTH=3, TOP_K=2, MAX_TOTAL_NODES=50 locked as constants for v1 (UI-adjustable deferred to v2)"

patterns-established:
  - "Event emission: every tree node emitted as TotNodeEvent with score; pruned nodes emitted as TotPruneEvent with reason"
  - "Graceful degradation: fuse trip, empty level, and LLM failure all produce best-effort path answer with ErrorEvent notice, never hard interruption"

requirements-completed: [PATTERN-05]

# Metrics
duration: 7min
completed: 2026-08-20
---

# Phase 8 Plan 1: Tree of Thoughts Summary

**ToT backend core: TotNodeEvent/TotPruneEvent event records (AgentEvent sealed 10→12 subtypes) + TreeOfThoughtsAgentPattern BFS executor with per-node LLM scoring (1-10), top-K=2 pruning events, and best-path backtracking to formatted final answers**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-08-20T01:51:41Z
- **Completed:** 2026-08-20T01:58:07Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- TotNodeEvent & TotPruneEvent records created per D-03; AgentEvent sealed interface extended from 10 to 12 permitted subtypes (compile-time exhaustiveness still enforced — verified by AgentEventTest pattern switch)
- TreeOfThoughtsAgentPattern (`@Component`, `id="tot"`) implements BFS: branching=3 per alive node, depth=3 levels, per-node independent LLM evaluation calls scoring 1-10
- top-K=2 greedy pruning per level (except last) with TotPruneEvent emission including pruned node IDs + reason; best surviving leaf backtracked via parentId to build optimal path, then SUMMARIZE call produces FinalAnswerEvent
- MAX_TOTAL_NODES=50 fuse trips degrade to best-effort path answer (ErrorEvent notice + continue), never hard failure; empty-level and LLM-call failures also degrade gracefully
- parseScoreSafely multi-strategy tolerant parsing (评分:N → N/10 → first standalone digit → keyword → DEFAULT_SCORE=5) and parseBranches JSON fallback guarantee the stream never breaks on unstructured LLM output
- Full regression: 42 tests pass (AgentEventTest 3 tests covering 12 records; PatternControllerWithMockTest pattern count 6)
- pom.xml unchanged (T-08-SC: no new dependencies)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create TotNodeEvent/TotPruneEvent records + extend AgentEvent permits** - `a312be2` (feat)
2. **Task 2: Create TreeOfThoughtsAgentPattern (BFS core)** - `ed3e9b3` (feat)
3. **Task 3: Update tests for 12 event records + pattern count 6** - `fb80532` (test)

## Files Created/Modified
- `src/main/java/com/agents/agent/core/TotNodeEvent.java` - ToT node event record (ts, level, nodeId, thought, score, parentId); level=-1 root with score=0, parentId=null
- `src/main/java/com/agents/agent/core/TotPruneEvent.java` - ToT prune event record (ts, level, prunedNodeIds, reason)
- `src/main/java/com/agents/agent/core/AgentEvent.java` - sealed permits 10 → 12 subtypes (TotNodeEvent, TotPruneEvent inserted); JavaDoc updated to 12 record subtypes
- `src/main/java/com/agents/agent/patterns/TreeOfThoughtsAgentPattern.java` - @Component BFS implementation (~349 lines): GENERATE/EVALUATE/SUMMARIZE prompts, TotNode record, extractJson, parseScoreSafely, parseBranches, execute() Flux.create
- `src/test/java/com/agents/agent/core/AgentEventTest.java` - renamed to shouldInstantiateAllTwelveEventRecords; accessor assertions for both new records; root parentId=null check; exhaustive switch case additions
- `src/test/java/com/agents/api/PatternControllerWithMockTest.java` - $.length() 5 → 6; JavaDoc pattern inventory updated with tot

## Decisions Made
None beyond plan — all D-01 through D-04 decisions from CONTEXT.md implemented as specified.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] JAVA_HOME pointed at JDK 8, breaking compilation**
- **Found during:** Task 1 verification (`mvn compile`)
- **Issue:** Shell environment had `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/...` (Java 1.8.0_411), producing 100+ spurious syntax errors on valid Java 21 source (sealed interfaces, records). Project requires Java 21.
- **Fix:** Sent every Maven invocation with `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home` (OpenJDK 21.0.10 available on machine). No source or config file changes.
- **Files modified:** none (environment-only)
- **Verification:** `mvn compile -q` clean, `mvn test` 42/42 green
- **Committed in:** n/a (pre-commit environment fix)

---

**Total deviations:** 1 auto-fixed (1 blocking, environment-only)
**Impact on plan:** No scope creep. Source code executed exactly per plan; the fix was confined to shell-level JAVA_HOME.

## Issues Encountered
- None beyond the JAVA_HOME environment issue documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Backend ToT support complete: events flow through generic SseEventEmitter (`getClass().getSimpleName()` + record JSON) with zero changes to that file, as planned
- Plan 08-02 (frontend) can consume TotNodeEvent/TotPruneEvent to rebuild and render the tree (composables + ReasoningPanel column layout)
- SC#1–SC#4 manual end-to-end verification (24-point question via UI DevTools EventStream) deferred to after Plan 08-02 per plan verification section

## Self-Check: PASSED

- Files verified: TotNodeEvent.java, TotPruneEvent.java, TreeOfThoughtsAgentPattern.java, AgentEvent.java, AgentEventTest.java, PatternControllerWithMockTest.java all FOUND on disk
- Commits verified: a312be2, ed3e9b3, fb80532 all FOUND in git log
- Full test suite: 42 tests, 0 failures (mvn test BUILD SUCCESS)

---
*Phase: 08-tree-of-thoughts-pattern*
*Completed: 2026-08-20*