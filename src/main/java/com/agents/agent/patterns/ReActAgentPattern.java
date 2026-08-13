package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.ReasoningEvent;
import com.agents.agent.core.ToolCallEvent;
import com.agents.agent.core.ToolResultEvent;
import com.agents.tool.ToolRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ReAct（推理+行动）模式实现 - 手动工具调用循环。
 *
 * <p>使用 DeepSeekChatModel 直接手动循环（D-01），每轮流式发射 Thought（ReasoningEvent），
 * 工具调用时发射 ToolCallEvent/ToolResultEvent，使用 MessageAggregator 聚合多 chunk tool_call
 * 参数（SSE-06），max_iterations=10 防止无限循环（TOOL-05），工具调用去重（TOOL-06），
 * system prompt 包含收敛条件（TOOL-07），<final_answer> 标签优先检测最终答案（D-05）。
 *
 * <p>D-01: 使用 DeepSeekChatModel 直接手动循环，而非 ToolCallingManager 或 ToolCallingAdvisor。
 * 完全控制工具调用生命周期，保留教学可见性。
 */
@Component
public class ReActAgentPattern implements AgentPattern {

    private static final int MAX_ITERATIONS = 10;

    private static final String REACT_SYSTEM_PROMPT = """
            你是一个 ReAct (Reasoning + Acting) Agent。你用 Thought -> Action -> Observation 循环回答用户问题。

            可用工具：weather, calculator, time。

            思考格式：
            Thought: 分析当前情况，决定下一步。
            Action: 调用工具，格式为 工具名(参数JSON)。
            Observation: 观察工具返回结果。

            规则：
            1. 只在需要外部信息时才调用工具。
            2. 如果某个工具+参数已经调用过，直接复用上次结果，不要重复调用。
            3. 当你有足够信息回答用户问题时，停止调用工具，输出最终答案，并用 <final_answer> 标签包裹。
            4. 最多调用 10 次工具。
            5. 工具结果仅供参考，最终答案要用你自己的话。

            最终答案格式：<final_answer>你的答案</final_answer>
            """;

    private final DeepSeekChatModel chatModel;
    private final ToolRegistry toolRegistry;

    public ReActAgentPattern(DeepSeekChatModel chatModel, ToolRegistry toolRegistry) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String id() {
        return "react";
    }

    @Override
    public String displayName() {
        return "ReAct 推理+行动";
    }

    @Override
    public String description() {
        return "结合推理与工具调用，模型可调用工具获取外部信息";
    }

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            try {
                // Collect tool callbacks for this pattern
                List<ToolCallback> toolCallbacks = toolRegistry.forPattern("react");

                // Build initial messages
                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(REACT_SYSTEM_PROMPT));
                messages.add(new UserMessage(ctx.question()));

                // Build DeepSeekChatOptions with tools
                DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                        .model(DeepSeekApi.ChatModel.DEEPSEEK_REASONER.getValue())
                        .temperature(0.4)
                        .toolCallbacks(toolCallbacks)
                        .build();

                // Dedup cache: key = toolName + arguments, value = result
                Map<String, String> dedupCache = new HashMap<>();

                // Main loop
                for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
                    // Create prompt with current message history and options
                    Prompt prompt = new Prompt(messages, options);

                    // Stream and aggregate using MessageAggregator
                    AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
                    new MessageAggregator()
                            .aggregate(chatModel.stream(prompt), aggregatedRef::set)
                            .blockLast();

                    ChatResponse aggregated = aggregatedRef.get();
                    if (aggregated == null || aggregated.getResult() == null) {
                        break;
                    }

                    // Extract AssistantMessage from aggregated response
                    AssistantMessage assistantMsg = aggregated.getResult().getOutput();
                    String content = assistantMsg.getText() != null ? assistantMsg.getText() : "";

                    // Emit reasoning content as ReasoningEvent during streaming
                    // (content from the aggregated response is the thought)
                    if (!content.isEmpty()) {
                        sink.next(new ReasoningEvent(Instant.now(), content));
                    }

                    // Handle DeepSeek reasoning_content if available
                    if (assistantMsg instanceof DeepSeekAssistantMessage dsm) {
                        String reasoningContent = dsm.getReasoningContent();
                        if (reasoningContent != null && !reasoningContent.isEmpty()) {
                            sink.next(new ReasoningEvent(Instant.now(), reasoningContent));
                        }
                    }

                    // Check for <final_answer> tag (D-05, priority detection)
                    String finalAnswer = extractFinalAnswer(content);
                    if (finalAnswer != null) {
                        sink.next(new FinalAnswerEvent(Instant.now(), finalAnswer));
                        sink.complete();
                        return;
                    }

                    // Check for tool calls (D-01)
                    if (assistantMsg.hasToolCalls()) {
                        List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();

                        // Build tool response for each tool call
                        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

                        for (AssistantMessage.ToolCall tc : toolCalls) {
                            String toolName = tc.name();
                            String arguments = tc.arguments() != null ? tc.arguments() : "";
                            String dedupKey = toolName + arguments;

                            // Emit ToolCallEvent (D-02)
                            sink.next(new ToolCallEvent(Instant.now(), toolName, Map.of()));

                            // Check dedup cache (D-03, TOOL-06)
                            String result;
                            boolean isError = false;
                            if (dedupCache.containsKey(dedupKey)) {
                                // Dedup hit: reuse previous result
                                result = "use previous result";
                            } else {
                                // Execute tool manually (D-06)
                                try {
                                    ToolCallback tool = toolRegistry.byName(toolName);
                                    result = tool.call(arguments);
                                    dedupCache.put(dedupKey, result);
                                } catch (Exception ex) {
                                    // Return error to model (D-06)
                                    result = "工具执行错误: " + ex.getMessage();
                                    isError = true;
                                }
                            }

                            // Emit ToolResultEvent (D-02)
                            sink.next(new ToolResultEvent(Instant.now(), toolName, result, isError));

                            // Add tool response for model history
                            toolResponses.add(new ToolResponseMessage.ToolResponse(
                                    tc.id(), toolName, result));
                        }

                        // Append assistant message and tool response to message history
                        messages.add(assistantMsg);
                        messages.add(ToolResponseMessage.builder()
                                .responses(toolResponses)
                                .build());

                        // Continue to next iteration
                        continue;
                    }

                    // No tool calls and non-empty text: heuristic fallback (D-05)
                    if (!content.isEmpty()) {
                        sink.next(new FinalAnswerEvent(Instant.now(), content));
                        sink.complete();
                        return;
                    }

                    // No tool calls and empty text: safety valve, break loop
                    break;
                }

                // Max iterations reached without final answer (D-04, TOOL-05)
                sink.next(new ErrorEvent(Instant.now(),
                        "ReAct 循环已达最大迭代次数 " + MAX_ITERATIONS + "，请简化问题后重试"));
                sink.complete();

            } catch (Exception ex) {
                sink.next(new ErrorEvent(Instant.now(),
                        "ReAct 模式执行异常: " + ex.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * Extract content between <final_answer> and </final_answer> tags.
     * Returns null if no complete tag pair is found.
     */
    private static String extractFinalAnswer(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        int startTag = content.indexOf("<final_answer>");
        if (startTag == -1) {
            return null;
        }
        int endTag = content.indexOf("</final_answer>", startTag);
        if (endTag == -1) {
            return null;
        }
        // Extract content between tags
        return content.substring(startTag + "<final_answer>".length(), endTag).trim();
    }
}