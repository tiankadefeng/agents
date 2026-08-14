package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.SubAnswerEvent;
import com.agents.agent.core.SubQuestionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SelfAskAgentPattern} - single LLM call + JSON parsing + event emission.
 *
 * <p>Verifies:
 * <ul>
 *   <li>D-02: Single ChatClient.call() returns full JSON, parsed by Jackson</li>
 *   <li>D-03: Structured JSON output with sub_questions array + final_answer</li>
 *   <li>T-06-01: Invalid JSON and empty sub_questions route to ErrorEvent</li>
 *   <li>extractJson() preprocessing handles markdown-wrapped responses</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SelfAskAgentPatternTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SelfAskAgentPattern selfAskAgentPattern;

    private final ObjectMapper realMapper = new ObjectMapper();

    private ChatClient.ChatClientRequestSpec mockRequestChain(ChatClient chatClient,
                                                              ChatClient.CallResponseSpec callResponseSpec) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        return requestSpec;
    }

    @Test
    void shouldParseValidJsonAndEmitEvents() throws Exception {
        // Arrange: mock ChatClient fluent chain
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);
        when(callResponseSpec.content()).thenReturn("""
                {"sub_questions":[{"question":"北京人口多少？","answer":"约2000万"},{"question":"上海人口多少？","answer":"约2500万"}],"final_answer":"中国人口最多的城市是上海，约2500万。"}
                """);

        // Build a real JsonNode for the mock return
        JsonNode root = realMapper.readTree("""
                {"sub_questions":[{"question":"北京人口多少？","answer":"约2000万"},{"question":"上海人口多少？","answer":"约2500万"}],"final_answer":"中国人口最多的城市是上海，约2500万。"}
                """);
        when(objectMapper.readTree(anyString())).thenReturn(root);

        // Act
        Flux<AgentEvent> events = selfAskAgentPattern.execute(
                new AgentContext("中国人口最多的城市是？", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(5); // 2 SubQuestion + 2 SubAnswer + 1 FinalAnswer

        // Check order: SubQuestion[0] < SubAnswer[0] < SubQuestion[1] < SubAnswer[1] < FinalAnswer
        assertThat(eventList.get(0)).isInstanceOf(SubQuestionEvent.class);
        assertThat(eventList.get(1)).isInstanceOf(SubAnswerEvent.class);
        assertThat(eventList.get(2)).isInstanceOf(SubQuestionEvent.class);
        assertThat(eventList.get(3)).isInstanceOf(SubAnswerEvent.class);
        assertThat(eventList.get(4)).isInstanceOf(FinalAnswerEvent.class);

        // Check SubAnswerEvent values
        assertThat(((SubAnswerEvent) eventList.get(1)).question()).isEqualTo("北京人口多少？");
        assertThat(((SubAnswerEvent) eventList.get(1)).answer()).isEqualTo("约2000万");
        assertThat(((SubAnswerEvent) eventList.get(3)).question()).isEqualTo("上海人口多少？");
        assertThat(((SubAnswerEvent) eventList.get(3)).answer()).isEqualTo("约2500万");

        // Check FinalAnswerEvent content
        assertThat(((FinalAnswerEvent) eventList.get(4)).content())
                .isEqualTo("中国人口最多的城市是上海，约2500万。");
    }

    @Test
    void shouldEmitErrorOnInvalidJson() throws Exception {
        // Arrange: mock ChatClient chain
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);
        when(callResponseSpec.content()).thenReturn("not valid json at all");

        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("Parse error"));

        // Act
        Flux<AgentEvent> events = selfAskAgentPattern.execute(
                new AgentContext("test", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(1);
        assertThat(eventList.get(0)).isInstanceOf(ErrorEvent.class);
        assertThat(((ErrorEvent) eventList.get(0)).message()).contains("解析失败");
    }

    @Test
    void shouldEmitErrorOnEmptySubQuestions() throws Exception {
        // Arrange: mock ChatClient chain
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);
        when(callResponseSpec.content()).thenReturn("{\"sub_questions\":[],\"final_answer\":\"\"}");

        JsonNode root = realMapper.readTree("{\"sub_questions\":[],\"final_answer\":\"\"}");
        when(objectMapper.readTree(anyString())).thenReturn(root);

        // Act
        Flux<AgentEvent> events = selfAskAgentPattern.execute(
                new AgentContext("test", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(1);
        assertThat(eventList.get(0)).isInstanceOf(ErrorEvent.class);
        assertThat(((ErrorEvent) eventList.get(0)).message()).contains("子问题");
    }

    @Test
    void shouldExtractJsonFromMarkdownWrappedResponse() {
        // extractJson is package-private, callable directly from same package
        String input = "```json\n{\"key\": \"value\"}\n```";
        String result = SelfAskAgentPattern.extractJson(input);
        assertThat(result).isEqualTo("{\"key\": \"value\"}");

        // null input returns null
        assertThat(SelfAskAgentPattern.extractJson(null)).isNull();
    }
}