---
phase: 05-react-pattern
plan: 01
created: 2026-08-13T08:55:00Z
status: completed
---

# Plan 05-01 Quick Summary

**Objective:** 实现 ReActAgentPattern 后端核心——手动工具调用循环

## Completed Tasks

### Task 1: Create ReAct system prompt constant and MessageAggregator unit test
**File:** `src/test/java/com/agents/agent/patterns/ReActManualLoopTest.java`
- Test 1 (shouldAggregateToolCallArgumentsFromMultipleChunks): MessageAggregator 聚合多 chunk tool_call 参数
- Test 2 (shouldBuildMessagesCorrectly): UserMessage → AssistantMessage → ToolResponseMessage 构造
- Test 3 (shouldTrackReasoningContentAcrossRounds): reasoning_content 跨轮次可访问

### Task 2: Create ReActAgentPattern unit tests
**File:** `src/test/java/com/agents/agent/patterns/ReActAgentPatternTest.java`
- Test 1: Event sequence (ReasoningEvent → ToolCallEvent → ToolResultEvent)
- Test 2: max_iterations=10 停止循环并发射 ErrorEvent
- Test 3: 相同 tool+args 去重返回 "use previous result"
- Test 4: `<final_answer>` 标签触发 FinalAnswerEvent
- Test 5: 无 tool_calls + 非空文本启发式触发 FinalAnswerEvent

### Task 3: Implement ReActAgentPattern backend
**File:** `src/main/java/com/agents/agent/patterns/ReActAgentPattern.java`
- @Component 实现 AgentPattern 接口，id="react"
- REACT_SYSTEM_PROMPT 包含工具列表、收敛规则、`<final_answer>` 格式
- execute() 使用 Sinks.Many + Flux.create 驱动多轮循环
- MessageAggregator 聚合多 chunk tool_call 参数
- `<final_answer>` 标签优先检测，无标签时启发式退避
- 手动工具调用（toolRegistry.byName.call），ToolCallEvent/ToolResultEvent 发射
- 去重缓存（Map<String, String>），相同 toolName+args 返回 "use previous result"
- max_iterations=10，超时发射 ErrorEvent
- deepseek-reasoner 兼容：AssistantMessage 完整加入消息历史
- onErrorResume 捕获异常发射 ErrorEvent

## Test Results

**8/8 tests passed:** 5 ReActAgentPatternTest + 3 ReActManualLoopTest
**Full suite:** 38 tests, 0 failures (after fixing PatternControllerWithMockTest assertion count from 2→3)

## Commits

- `20ac6a5` test(05-01): add MessageAggregator and manual loop mock tests
- `e4a9aa4` test(05-01): add failing ReActAgentPattern unit tests (RED)
- `06936b5` feat(05-01): implement ReActAgentPattern backend with manual tool-call loop

## Key Decisions

- D-01: 使用 DeepSeekChatModel 直接手动循环，而非 ToolCallingManager
- D-03: 去重缓存是 execute() 调用内局部 Map<String, String>
- D-05: `<final_answer>` 标签优先检测，无标签时退避到启发式
- D-06: 工具错误通过 ToolResponseMessage 返回给模型，不抛异常