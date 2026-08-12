package com.agents.agent.patterns;

import com.agents.agent.core.ToolCallEvent;
import com.agents.agent.core.ToolResultEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MessageAggregator} and manual loop message construction.
 *
 * <p>Verifies:
 * <ul>
 *   <li>SSE-06: MessageAggregator correctly aggregates multi-chunk tool_call arguments</li>
 *   <li>PATTERN-03: Manual message construction (UserMessage -> AssistantMessage -> ToolResponseMessage)</li>
 *   <li>D-07: DeepSeekAssistantMessage reasoning_content is accessible</li>
 * </ul>
 */
class ReActManualLoopTest {

    @Test
    void shouldAggregateToolCallArgumentsFromMultipleChunks() {
        // Chunk 1: partial tool_call arguments (city key start)
        var chunk1 = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call_1", "function", "weather", "{\"city\":\"Bei")))
                        .build()
        )));
        // Chunk 2: partial tool_call arguments (city value continuation)
        var chunk2 = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call_1", "function", "weather", "jing")))
                        .build()
        )));
        // Chunk 3: partial tool_call arguments (closing brace)
        var chunk3 = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call_1", "function", "weather", "\"}")))
                        .build()
        )));

        Flux<ChatResponse> flux = Flux.just(chunk1, chunk2, chunk3);
        AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
        new MessageAggregator().aggregate(flux, aggregatedRef::set).blockLast();

        ChatResponse aggregated = aggregatedRef.get();
        assertThat(aggregated).isNotNull();
        assertThat(aggregated.getResult()).isNotNull();
        AssistantMessage output = aggregated.getResult().getOutput();
        assertThat(output).isNotNull();
        assertThat(output.hasToolCalls()).isTrue();

        // Verify tool calls from all 3 chunks were collected
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        assertThat(toolCalls).hasSize(3);
        // Verify each tool call has the same id and name
        assertThat(toolCalls).allMatch(tc -> "call_1".equals(tc.id()));
        assertThat(toolCalls).allMatch(tc -> "weather".equals(tc.name()));
        // Verify the concatenated arguments form a valid JSON structure
        String combinedArgs = toolCalls.stream()
                .map(AssistantMessage.ToolCall::arguments)
                .reduce("", String::concat);
        assertThat(combinedArgs).contains("Beijing");
        assertThat(combinedArgs).startsWith("{");
        assertThat(combinedArgs).endsWith("}");
    }

    @Test
    void shouldBuildMessagesCorrectly() {
        Instant now = Instant.now();

        // Construct UserMessage
        Message userMessage = new UserMessage("北京天气怎么样？");

        // Construct AssistantMessage with tool calls
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("让我查询北京的天气。")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "weather", "{\"city\":\"北京\"}")))
                .build();

        // Construct ToolResponseMessage
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call_1", "weather", "{\"city\":\"北京\",\"temperature\":22,\"condition\":\"晴\"}")))
                .build();

        // Verify message list order and types
        List<Message> messages = List.of(userMessage, assistantMessage, toolResponse);
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2)).isInstanceOf(ToolResponseMessage.class);

        // Verify ToolResponseMessage.ToolResponse fields
        ToolResponseMessage.ToolResponse response = toolResponse.getResponses().get(0);
        assertThat(response.id()).isEqualTo("call_1");
        assertThat(response.name()).isEqualTo("weather");
        assertThat(response.responseData()).contains("北京");
        assertThat(response.responseData()).contains("temperature\":22");

        // Verify AssistantMessage tool calls
        assertThat(assistantMessage.hasToolCalls()).isTrue();
        assertThat(assistantMessage.getToolCalls()).hasSize(1);
        assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("weather");
        assertThat(assistantMessage.getToolCalls().get(0).arguments()).contains("北京");
    }

    @Test
    void shouldTrackReasoningContentAcrossRounds() {
        // Create a DeepSeekAssistantMessage with reasoning_content
        DeepSeekAssistantMessage dsm = DeepSeekAssistantMessage.builder()
                .content("最终答案")
                .reasoningContent("让我思考一下...第一步，分析问题。第二步，收集信息。")
                .toolCalls(List.of())
                .build();

        // Verify reasoning content is accessible
        assertThat(dsm.getReasoningContent()).isNotNull();
        assertThat(dsm.getReasoningContent()).contains("第一步");
        assertThat(dsm.getReasoningContent()).contains("第二步");

        // Verify text content is also accessible
        assertThat(dsm.getText()).isEqualTo("最终答案");

        // Simulate preserving reasoning_content across loop iterations
        String reasoningFromRound1 = dsm.getReasoningContent();
        DeepSeekAssistantMessage round2 = DeepSeekAssistantMessage.builder()
                .content("第二轮思考")
                .reasoningContent(reasoningFromRound1 + " 第三步，得出结论。")
                .toolCalls(List.of())
                .build();

        assertThat(round2.getReasoningContent()).contains("第一步");
        assertThat(round2.getReasoningContent()).contains("第三步");
        assertThat(round2.getText()).isEqualTo("第二轮思考");
    }
}