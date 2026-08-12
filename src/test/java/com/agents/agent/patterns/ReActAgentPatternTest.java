package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.ToolCallEvent;
import com.agents.agent.core.ToolResultEvent;
import com.agents.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReActAgentPattern} - manual tool-call loop.
 *
 * <p>Verifies:
 * <ul>
 *   <li>PATTERN-03: Manual loop emits ToolCallEvent -> ToolResultEvent -> next round</li>
 *   <li>TOOL-05: max_iterations=10 forces stop with ErrorEvent</li>
 *   <li>TOOL-06: dedup returns "use previous result"</li>
 *   <li>D-05: final answer tag detection</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReActAgentPatternTest {

    @Mock
    private DeepSeekChatModel chatModel;

    @Mock
    private ToolRegistry toolRegistry;

    @InjectMocks
    private ReActAgentPattern reActAgentPattern;

    @Test
    void shouldEmitToolCallThenToolResultThenNextRound() {
        // Arrange: mock ChatModel to return a response with tool calls
        AssistantMessage assistantMsg = AssistantMessage.builder()
                .content("让我查询天气")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "weather", "{\"city\":\"北京\"}")))
                .build();
        ChatResponse toolCallResponse = new ChatResponse(List.of(new Generation(assistantMsg)));

        // Mock tool registry
        ToolCallback mockTool = mock(ToolCallback.class);
        when(toolRegistry.forPattern("react")).thenReturn(List.of(mockTool));
        when(toolRegistry.byName("weather")).thenReturn(mockTool);
        when(mockTool.call("{\"city\":\"北京\"}")).thenReturn("{\"temperature\":22,\"condition\":\"晴\"}");

        // Mock ChatModel to return tool call response first, then final answer
        AssistantMessage finalMsg = AssistantMessage.builder()
                .content("北京天气22度，晴天。")
                .toolCalls(List.of())
                .build();
        ChatResponse finalResponse = new ChatResponse(List.of(new Generation(finalMsg)));

        Flux<ChatResponse> toolCallFlux = Flux.just(toolCallResponse);
        Flux<ChatResponse> finalFlux = Flux.just(finalResponse);
        when(chatModel.stream(any(Prompt.class))).thenReturn(toolCallFlux, finalFlux);

        // Act
        Flux<AgentEvent> events = reActAgentPattern.execute(new AgentContext("北京天气怎么样？", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).isNotEmpty();

        // Verify event sequence contains expected types
        assertThat(eventList).anyMatch(ev -> ev instanceof ToolCallEvent);
        assertThat(eventList).anyMatch(ev -> ev instanceof ToolResultEvent);
        assertThat(eventList).anyMatch(ev -> ev instanceof FinalAnswerEvent);

        // Verify order: ToolCallEvent before ToolResultEvent
        int toolCallIdx = -1;
        int toolResultIdx = -1;
        int finalAnswerIdx = -1;
        for (int i = 0; i < eventList.size(); i++) {
            AgentEvent ev = eventList.get(i);
            if (ev instanceof ToolCallEvent) toolCallIdx = i;
            if (ev instanceof ToolResultEvent) toolResultIdx = i;
            if (ev instanceof FinalAnswerEvent) finalAnswerIdx = i;
        }
        assertThat(toolCallIdx).isLessThan(toolResultIdx);
        assertThat(toolResultIdx).isLessThan(finalAnswerIdx);
    }

    @Test
    void shouldStopAtMaxIterations() {
        // Arrange: mock ChatModel to always return tool calls (never final answer)
        AssistantMessage assistantMsg = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "weather", "{\"city\":\"北京\"}")))
                .build();
        ChatResponse toolCallResponse = new ChatResponse(List.of(new Generation(assistantMsg)));

        ToolCallback mockTool = mock(ToolCallback.class);
        when(toolRegistry.forPattern("react")).thenReturn(List.of(mockTool));
        when(toolRegistry.byName("weather")).thenReturn(mockTool);
        when(mockTool.call(any())).thenReturn("{\"temperature\":22}");

        // Return tool calls every time (never final answer)
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(toolCallResponse));

        // Act
        Flux<AgentEvent> events = reActAgentPattern.execute(new AgentContext("北京天气怎么样？", Map.of()));

        // Assert: expect ErrorEvent with max iteration message
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(10));
        assertThat(eventList).isNotNull();
        assertThat(eventList).isNotEmpty();

        // Last event should be ErrorEvent
        AgentEvent lastEvent = eventList.get(eventList.size() - 1);
        assertThat(lastEvent).isInstanceOf(ErrorEvent.class);
        assertThat(((ErrorEvent) lastEvent).message()).contains("最大迭代次数");
    }

    @Test
    void shouldDeduplicateSameToolCall() {
        // Arrange: mock ChatModel to return the same tool call twice
        AssistantMessage assistantMsg = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "weather", "{\"city\":\"北京\"}")))
                .build();
        ChatResponse toolCallResponse = new ChatResponse(List.of(new Generation(assistantMsg)));

        ToolCallback mockTool = mock(ToolCallback.class);
        when(toolRegistry.forPattern("react")).thenReturn(List.of(mockTool));
        when(toolRegistry.byName("weather")).thenReturn(mockTool);
        when(mockTool.call("{\"city\":\"北京\"}")).thenReturn("{\"temperature\":22}");

        // Return tool calls twice, then final answer
        AssistantMessage finalMsg = AssistantMessage.builder()
                .content("北京天气22度。")
                .toolCalls(List.of())
                .build();
        ChatResponse finalResponse = new ChatResponse(List.of(new Generation(finalMsg)));

        Flux<ChatResponse> toolCallFlux = Flux.just(toolCallResponse);
        Flux<ChatResponse> finalFlux = Flux.just(finalResponse);
        when(chatModel.stream(any(Prompt.class))).thenReturn(toolCallFlux, toolCallFlux, finalFlux);

        // Act
        Flux<AgentEvent> events = reActAgentPattern.execute(new AgentContext("北京天气怎么样？", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();

        // Find ToolResultEvents
        List<ToolResultEvent> toolResults = eventList.stream()
                .filter(ToolResultEvent.class::isInstance)
                .map(ToolResultEvent.class::cast)
                .toList();

        // First call should have real result, second should be "use previous result"
        assertThat(toolResults).hasSize(2);
        assertThat(toolResults.get(0).result()).contains("temperature");
        assertThat(toolResults.get(1).result()).isEqualTo("use previous result");

        // Verify the tool method was invoked only once
        verify(mockTool, times(1)).call(any());
        verifyNoMoreInteractions(mockTool);
    }

    @Test
    void shouldDetectFinalAnswerTag() {
        // Arrange: mock ChatModel to return response with <final_answer> tag
        AssistantMessage finalMsg = AssistantMessage.builder()
                .content("这是思考过程。<final_answer>北京天气22度，晴天。</final_answer>")
                .toolCalls(List.of())
                .build();
        ChatResponse finalResponse = new ChatResponse(List.of(new Generation(finalMsg)));

        ToolCallback mockTool = mock(ToolCallback.class);
        when(toolRegistry.forPattern("react")).thenReturn(List.of(mockTool));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(finalResponse));

        // Act
        Flux<AgentEvent> events = reActAgentPattern.execute(new AgentContext("北京天气怎么样？", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).isNotEmpty();

        // Should contain FinalAnswerEvent with content from within <final_answer> tags
        boolean hasFinalAnswer = eventList.stream()
                .anyMatch(ev -> ev instanceof FinalAnswerEvent
                        && ((FinalAnswerEvent) ev).content().contains("北京天气22度"));
        assertThat(hasFinalAnswer).isTrue();
    }

    @Test
    void shouldFallbackToHeuristicWhenNoToolCalls() {
        // Arrange: mock ChatModel to return response with non-empty text, no tool calls, no <final_answer> tag
        AssistantMessage finalMsg = AssistantMessage.builder()
                .content("北京天气22度，晴天。适合户外活动。")
                .toolCalls(List.of())
                .build();
        ChatResponse finalResponse = new ChatResponse(List.of(new Generation(finalMsg)));

        ToolCallback mockTool = mock(ToolCallback.class);
        when(toolRegistry.forPattern("react")).thenReturn(List.of(mockTool));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(finalResponse));

        // Act
        Flux<AgentEvent> events = reActAgentPattern.execute(new AgentContext("北京天气怎么样？", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).isNotEmpty();

        // Should contain FinalAnswerEvent (heuristic fallback: no tool calls + non-empty text)
        boolean hasFinalAnswer = eventList.stream()
                .anyMatch(ev -> ev instanceof FinalAnswerEvent
                        && ((FinalAnswerEvent) ev).content().contains("北京天气22度"));
        assertThat(hasFinalAnswer).isTrue();
    }
}