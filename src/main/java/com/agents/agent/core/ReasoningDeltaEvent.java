package com.agents.agent.core;

import java.time.Instant;

/**
 * ReAct 每轮 Thought 的流式增量（Phase 11 前置 quick-260825-gtx 新增）。
 *
 * <p>临时态事件：LLM 调用期间逐 chunk 发射，一轮结束后由完整 {@link ReasoningEvent}
 * 收口，前端用完整事件替换 delta 拼接出的临时卡片（完整事件是 source of truth，
 * delta 丢帧不影响最终渲染）。reasoning_content 与 content chunk 均映射到此事件
 * （reasoner 先发完全部 reasoning 再发 content，无交错）。
 */
public record ReasoningDeltaEvent(Instant ts, String content) implements AgentEvent {}
