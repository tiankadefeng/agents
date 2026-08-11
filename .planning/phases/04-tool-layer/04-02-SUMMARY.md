---
phase: 04-tool-layer
plan: 02
subsystem: api
tags: [spring-ai, tool-calling, toolcallback, registry, dto]

# Dependency graph
requires:
  - phase: 04-01
    provides: WeatherTool / CalculatorTool / TimeTool @Component classes with @Tool methods
provides:
  - ToolRegistry @Component aggregating all built-in @Tool beans into List<ToolCallback>
  - NoSuchToolException RuntimeException for unknown tool lookups
  - ToolInfo / ToolInvokeRequest DTO records for Plan 03 ToolController
  - ToolRegistryTest unit tests (6 tests) verifying aggregation/filter/lookup
affects: [04-03-tool-controller]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ToolCallbacks.from(bean) reflection collection (org.springframework.ai.support package)"
    - "Registry pattern: constructor injection + toUnmodifiableMap + custom exception"
    - "DTO record pattern (ToolInfo / ToolInvokeRequest)"

key-files:
  created:
    - src/main/java/com/agents/tool/ToolRegistry.java
    - src/main/java/com/agents/tool/NoSuchToolException.java
    - src/main/java/com/agents/api/dto/ToolInfo.java
    - src/main/java/com/agents/api/dto/ToolInvokeRequest.java
    - src/test/java/com/agents/tool/ToolRegistryTest.java
  modified: []

key-decisions:
  - "ToolRegistry uses ToolCallbacks.from() (org.springframework.ai.support) to reflectively collect @Tool methods (D-01)"
  - "forPattern('cot') returns empty, forPattern(other) returns full list (D-08)"
  - "byName throws NoSuchToolException with message 未知工具：{toolName} on miss"
  - "Constructor reserves @Autowired(required=false) List<ToolCallbackProvider> for Phase 11 MCP"

patterns-established:
  - "Registry analog: toUnmodifiableMap immutable registry, O(1) byName lookup (mirrors AgentRegistry)"
  - "Pure unit test (no @SpringBootTest) with direct instantiation + AssertJ"

requirements-completed: [TOOL-01, TOOL-03]

# Metrics
duration: 50min
completed: 2026-08-11
---

# Phase 04 Plan 02: ToolRegistry + DTOs + Unit Tests Summary

**ToolRegistry aggregates 3 built-in @Tool beans via ToolCallbacks.from() into an immutable List<ToolCallback>, with forPattern() filtering and byName() lookup throwing NoSuchToolException, plus ToolInfo/ToolInvokeRequest DTO records and 6 passing unit tests**

## Performance

- **Duration:** 50 min
- **Started:** 2026-08-11T06:14:00Z
- **Completed:** 2026-08-11T06:32:27Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- ToolRegistry @Component aggregates 3 built-in @Tool beans (weather/calculator/time) into immutable List<ToolCallback> via ToolCallbacks.from() (D-01)
- forPattern() filtering: "cot" returns empty, others return full list (D-08)
- byName() O(1) Map lookup throws NoSuchToolException (message 未知工具：{toolName}) on miss
- Constructor reserves @Autowired(required=false) List<ToolCallbackProvider> for Phase 11 MCP extension (A3 null-safe)
- ToolInfo / ToolInvokeRequest DTO records ready for Plan 03 ToolController
- 6 unit tests all pass: aggregation, forPattern filtering, byName lookup + unknown throw, ToolCallbacks.from() path

## Task Commits

1. **Task 1: Create ToolRegistry + NoSuchToolException + 2 DTO records** - `780bb43` (feat)
2. **Task 2: Create ToolRegistryTest unit tests** - `78b30f2` (test)

## Files Created/Modified
- `src/main/java/com/agents/tool/ToolRegistry.java` - @Component registry, ToolCallbacks.from() aggregation, all()/forPattern()/byName()
- `src/main/java/com/agents/tool/NoSuchToolException.java` - RuntimeException, message 未知工具：{toolName}
- `src/main/java/com/agents/api/dto/ToolInfo.java` - record (name, description, inputSchema)
- `src/main/java/com/agents/api/dto/ToolInvokeRequest.java` - record (arguments Map)
- `src/test/java/com/agents/tool/ToolRegistryTest.java` - 6 unit tests

## Decisions Made
- Used `org.springframework.ai.support.ToolCallbacks` (NOT `org.springframework.ai.tool.ToolCallbacks`) - Pitfall 1 package path
- ToolRegistry constructor reserves `List<ToolCallbackProvider>` param with null-safe handling for Phase 11 MCP
- inputSchema kept as String in ToolInfo (frontend can JSON.parse if needed)
- byName uses toUnmodifiableMap per T-4-Map (immutable registry, mirrors AgentRegistry T-2-15)

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
- JAVA_HOME defaults to JDK 1.8 on this machine; Maven compile/test required explicit `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home` (environment setup, not a code issue)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ToolRegistry + DTOs ready for Plan 03 ToolController (GET /api/tools + POST /api/tools/{toolName}/invoke)
- NoSuchToolException caught by ToolController to return HTTP 404
- 6 ToolRegistryTest tests green

## Self-Check: PASSED

- All 5 files created: confirmed on disk
- All 2 commits present: `780bb43` (feat) + `78b30f2` (test)
- ToolRegistry compiles: `mvn -B compile` exits 0
- ToolRegistryTest passes: 6 tests, 0 failures, 0 errors

---
*Phase: 04-tool-layer*
*Completed: 2026-08-11*