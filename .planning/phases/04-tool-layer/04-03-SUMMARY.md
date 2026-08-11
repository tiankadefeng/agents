---
phase: 04-tool-layer
plan: 03
type: execute
subsystem: api
tags: [spring-ai, tool-calling, toolcallback, controller, rest]
---

# Dependency graph
requires:
  - phase: 04-01
    provides: WeatherTool / CalculatorTool / TimeTool @Component classes with @Tool methods
  - phase: 04-02
    provides: ToolRegistry @Component, NoSuchToolException, ToolInfo / ToolInvokeRequest DTO records
provides:
  - ToolController @RestController with GET /api/tools (list) + POST /api/tools/{toolName}/invoke (call)
  - ToolControllerTest with 5 WebTestClient integration tests
affects: [05-react-pattern]

# Tech tracking
tech-stack:
  added: []
  removed: []
  changed:
    - ToolRegistry: switched from injecting List<Object> toolBeans to explicit BUILTIN_TOOL_BEANS list
      (avoided circular dependency where ToolController @RestController was collected as a tool bean)

# Design decisions
decisions:
  - ToolRegistry uses explicit BUILTIN_TOOL_BEANS list instead of Spring DI List<Object> injection
    (rationale: avoid circular dependency with ToolController @RestController)

# Success criteria
success-criteria:
  - "SC#3: Tool endpoints accessible without LLM ✓"
  - "GET /api/tools returns 3 tool metadata ✓"
  - "POST /api/tools/weather/invoke returns weather JSON ✓"
  - "POST /api/tools/calculator/invoke returns result ✓"
  - "POST /api/tools/time/invoke returns current time ✓"
  - "POST /api/tools/unknown/invoke returns 404 ✓"
  - "PHP-04-03: 5/5 WebTestClient tests pass ✓"

# Key files
key-files:
  created:
    - src/main/java/com/agents/api/ToolController.java
    - src/test/java/com/agents/api/ToolControllerTest.java
  modified:
    - src/main/java/com/agents/tool/ToolRegistry.java (BUILTIN_TOOL_BEANS list, no more List<Object> injection)

# Self-check
self-check:
  status: PASSED
  checks:
    - 5/5 ToolControllerTest tests pass
    - 30/30 total tests pass
    - All 3 Phase 4 success criteria met
    - Deviations: ToolRegistry constructor signature changed (no List<Object> param, uses static BUILTIN_TOOL_BEANS instead)