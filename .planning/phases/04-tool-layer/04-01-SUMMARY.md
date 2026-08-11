---
phase: 04-tool-layer
plan: 01
subsystem: tool
tags: [spring-ai, exp4j, @tool, weather, calculator, time]
requires:
  - phase: 02-agent-abstraction
    provides: ToolCallEvent/ToolResultEvent records, @Component injection pattern
  - phase: 03-cot-pattern
    provides: @Component class pattern (CoTAgentPattern)
provides:
  - 3 built-in @Component tool classes with @Tool annotation (Spring AI 2.0 API)
  - exp4j 0.4.8 dependency for safe math expression evaluation
  - 4 unit test files verifying tool behavior and @Tool annotation presence
affects:
  - phase: 04-tool-layer (plan 02 - ToolRegistry aggregation)
  - phase: 04-tool-layer (plan 03 - ToolController REST endpoints)
  - phase: 05-react-pattern (ReAct tool calling loop)

tech-stack:
  added: [net.objecthunter:exp4j:0.4.8]
  patterns:
    - "@Component + @Tool annotation for Spring AI 2.0 declarative tool methods"
    - "JSON string return from @Tool methods (D-07)"
    - "exp4j ExpressionBuilder for safe math expression evaluation"
    - "Exception-to-error-JSON pattern (catch exp4j exceptions, return error JSON, never propagate)"

key-files:
  created:
    - src/main/java/com/agents/tool/builtin/WeatherTool.java
    - src/main/java/com/agents/tool/builtin/CalculatorTool.java
    - src/main/java/com/agents/tool/builtin/TimeTool.java
    - src/test/java/com/agents/tool/builtin/WeatherToolTest.java
    - src/test/java/com/agents/tool/builtin/CalculatorToolTest.java
    - src/test/java/com/agents/tool/builtin/TimeToolTest.java
    - src/test/java/com/agents/tool/ToolAnnotationTest.java
  modified:
    - pom.xml

key-decisions:
  - "D-02: Each tool independent @Component class (3 files in tool/builtin/)"
  - "D-03: WeatherTool all mock data (switch expression mapping 北京/上海/广州/New York + default fallback)"
  - "D-04: CalculatorTool using exp4j 0.4.8 (ExpressionBuilder, Shunting Yard algorithm)"
  - "D-05: TimeTool fixed system default timezone (ZoneId.systemDefault(), no parameters)"
  - "D-07: All @Tool methods return String (JSON), tool names in short English (weather/calculator/time)"

patterns-established:
  - "Tool class pattern: @Component + @Tool(name=..., description=...) on method returning String"
  - "Exception handling pattern: catch IllegalArgumentException|ArithmeticException, return error JSON (not exception propagation)"
  - "Pure unit test pattern: no @SpringBootTest, direct new ToolClass(), AssertJ assertions, should... naming"

requirements-completed: [TOOL-02, TOOL-03]

duration: 15min
completed: 2026-08-11
---

# Phase 4 Plan 1: Built-in Tool Classes Summary

**3 @Component tool classes (WeatherTool, CalculatorTool, TimeTool) with Spring AI 2.0 @Tool annotations, exp4j 0.4.8 dependency, and 4 unit test files verifying mock data, real calculation, time format, and @Tool annotation presence**

## Performance

- **Duration:** 15 min
- **Started:** 2026-08-11T05:30:00Z
- **Completed:** 2026-08-11T05:47:34Z
- **Tasks:** 2
- **Files modified:** 8 (4 source + 4 test)

## Accomplishments
- Added exp4j 0.4.8 dependency to pom.xml for safe math expression evaluation (Shunting Yard algorithm, ~46KB, Apache License 2.0)
- Created WeatherTool with mock data for 4 known cities (北京/上海/广州/New York) and default fallback for unknown cities
- Created CalculatorTool using exp4j ExpressionBuilder, with proper error handling for invalid expressions (returns error JSON, never propagates exceptions)
- Created TimeTool using ZoneId.systemDefault() and LocalDateTime.now(), returning ISO-8601 datetime + timezone + weekday
- Created 4 unit test files: 3 tool-specific tests + 1 reflection-based @Tool annotation verification test
- All 8 tests pass (8/8, 0 failures, 0 errors)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add exp4j dependency + create 3 tool classes** - `e7cdd67` (feat)
2. **Task 2: Create 4 unit test files** - `c7146c7` (test)

## Files Created/Modified

- `pom.xml` - Added exp4j 0.4.8 dependency after spring-ai-starter-model-deepseek
- `src/main/java/com/agents/tool/builtin/WeatherTool.java` - @Component with @Tool(name="weather"), mock data switch expression
- `src/main/java/com/agents/tool/builtin/CalculatorTool.java` - @Component with @Tool(name="calculator"), exp4j ExpressionBuilder, try-catch error JSON
- `src/main/java/com/agents/tool/builtin/TimeTool.java` - @Component with @Tool(name="time"), ZoneId.systemDefault(), ISO-8601 format
- `src/test/java/com/agents/tool/builtin/WeatherToolTest.java` - shouldReturnMockWeatherForBeijing, shouldReturnDefaultForUnknownCity
- `src/test/java/com/agents/tool/builtin/CalculatorToolTest.java` - shouldEvaluateSimpleArithmetic, shouldEvaluateSqrt, shouldReturnErrorForInvalidExpression
- `src/test/java/com/agents/tool/builtin/TimeToolTest.java` - shouldReturnCurrentTime, shouldHaveValidIsoDatetime
- `src/test/java/com/agents/tool/ToolAnnotationTest.java` - shouldHaveToolAnnotationOnAllToolMethods (reflection-based)

## Decisions Made

- All decisions followed the plan exactly. No deviations from D-02, D-03, D-04, D-05, D-07.
- CalculatorTool includes a JSON escape helper (`escapeJson`) to safely embed expression values and error messages within JSON strings, preventing injection issues when expressions contain special characters.

## Deviations from Plan

### Rule 1 - Bug Fix: Invalid expression test case

**1. [Rule 1 - Bug] Fixed `shouldReturnErrorForInvalidExpression` test case**
- **Found during:** Task 2 (CalculatorToolTest)
- **Issue:** The test used `"2++3"` as an invalid expression, but exp4j actually evaluates `"2++3"` as `5` (it treats `++` as unary plus followed by unary plus). This is exp4j's parser behavior, not an error condition.
- **Fix:** Changed the test case from `"2++3"` to `"(2+3"` (unclosed parenthesis), which exp4j genuinely rejects with an exception.
- **Files modified:** `src/test/java/com/agents/tool/builtin/CalculatorToolTest.java`
- **Verification:** `mvn -B test -Dtest=CalculatorToolTest` passes (3/3)
- **Committed in:** `c7146c7` (part of Task 2 commit)

### Rule 3 - Auto-fix: Added JSON escape helper in CalculatorTool

**1. [Rule 3 - Blocking] Added JSON escape helper for safe string embedding**
- **Found during:** Task 1 (CalculatorTool implementation)
- **Issue:** When an expression or error message contains special characters (double quotes, backslashes, newlines), embedding them directly in a JSON string via `String.format` would produce malformed JSON. The plan's code examples used `String.format` with raw expression values, which is a correctness issue.
- **Fix:** Added a private `escapeJson(String)` helper method that escapes backslash, double quote, newline, carriage return, and tab characters before embedding in JSON strings.
- **Files modified:** `src/main/java/com/agents/tool/builtin/CalculatorTool.java`
- **Verification:** The `escapeJson` method is used in all 3 return paths (success integral, success decimal, error)
- **Committed in:** `e7cdd67` (part of Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both fixes necessary for correctness. No scope creep.

## Issues Encountered

- **Pre-existing test failures:** Two unrelated test failures exist in `PatternControllerTest` and `PatternControllerWithMockTest` due to the CoT pattern being auto-registered (the tests expected empty/mock-only lists). These are pre-existing issues from Phase 3 and are out of scope for this plan.
- **Java version mismatch:** The system `java -version` reports Java 8, but the project requires Java 21. Maven was configured with `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home` for the build.

## Next Phase Readiness

- Plan 01 complete: 3 tool classes ready for ToolRegistry aggregation (Plan 02)
- ToolRegistry will use `ToolCallbacks.from()` to collect `@Tool` methods from these 3 `@Component` beans
- Plan 02 dependencies: ToolRegistry.java, NoSuchToolException.java, ToolRegistryTest.java

---
*Phase: 04-tool-layer*
*Completed: 2026-08-11*