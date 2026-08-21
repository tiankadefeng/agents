package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.ReflexionAttemptEvent;
import com.agents.agent.core.ReflexionEvaluateEvent;
import com.agents.agent.core.ReflexionReflectEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReflexionAgentPattern} - Generator + Evaluator + Reflector loop.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Full 2-round reflexion cycle with correct event order</li>
 *   <li>Early stop when Round 1 score >= PASS_THRESHOLD</li>
 *   <li>Epsilon early stop when score improvement &lt; 1</li>
 *   <li>max_reflections=2 hard limit</li>
 *   <li>ErrorEvent on ChatClient failure</li>
 *   <li>Best-of-N answer selection</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReflexionAgentPatternTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReflexionAgentPattern reflexionAgentPattern;

    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Delegate readTree to the real mapper so JSON parsing is genuinely exercised
        // (lenient: some tests never reach JSON parsing, e.g. error path)
        lenient().when(objectMapper.readTree(anyString())).thenAnswer(inv ->
                realMapper.readTree((String) inv.getArgument(0)));
    }

    private ChatClient.ChatClientRequestSpec mockRequestChain(ChatClient chatClient,
                                                              ChatClient.CallResponseSpec callResponseSpec) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        return requestSpec;
    }

    @Test
    void shouldExecuteFullReflexionCycleAndEmitEvents() {
        // Arrange: Round 1 low score -> Reflector -> Round 2 high score -> FinalAnswer
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        // 5 call responses: G1, E1, R1, G2, E2
        when(callResponseSpec.content())
                .thenReturn("initial answer")
                .thenReturn("{\"score\": 5, \"feedback\": \"lacks detail\"}")
                .thenReturn("{\"reflection\": \"add more detail\"}")
                .thenReturn("improved answer")
                .thenReturn("{\"score\": 9, \"feedback\": \"much better now\"}");

        // Act
        Flux<AgentEvent> events = reflexionAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(6);

        // Check order: Attempt(1) -> Evaluate(1,5) -> Reflect(1) -> Attempt(2) -> Evaluate(2,9) -> FinalAnswer
        assertThat(eventList.get(0)).isInstanceOf(ReflexionAttemptEvent.class);
        assertThat(((ReflexionAttemptEvent) eventList.get(0)).round()).isEqualTo(1);
        assertThat(((ReflexionAttemptEvent) eventList.get(0)).answer()).isEqualTo("initial answer");

        assertThat(eventList.get(1)).isInstanceOf(ReflexionEvaluateEvent.class);
        assertThat(((ReflexionEvaluateEvent) eventList.get(1)).round()).isEqualTo(1);
        assertThat(((ReflexionEvaluateEvent) eventList.get(1)).score()).isEqualTo(5);

        assertThat(eventList.get(2)).isInstanceOf(ReflexionReflectEvent.class);
        assertThat(((ReflexionReflectEvent) eventList.get(2)).round()).isEqualTo(1);
        assertThat(((ReflexionReflectEvent) eventList.get(2)).reflection()).isEqualTo("add more detail");

        assertThat(eventList.get(3)).isInstanceOf(ReflexionAttemptEvent.class);
        assertThat(((ReflexionAttemptEvent) eventList.get(3)).round()).isEqualTo(2);
        assertThat(((ReflexionAttemptEvent) eventList.get(3)).answer()).isEqualTo("improved answer");

        assertThat(eventList.get(4)).isInstanceOf(ReflexionEvaluateEvent.class);
        assertThat(((ReflexionEvaluateEvent) eventList.get(4)).round()).isEqualTo(2);
        assertThat(((ReflexionEvaluateEvent) eventList.get(4)).score()).isEqualTo(9);

        assertThat(eventList.get(5)).isInstanceOf(FinalAnswerEvent.class);
        // Best-of-N: score 9 > 5, so take Round 2 answer
        assertThat(((FinalAnswerEvent) eventList.get(5)).content()).isEqualTo("improved answer");
    }

    @Test
    void shouldStopEarlyOnHighScore() {
        // Arrange: Round 1 score >= PASS_THRESHOLD, no reflector or round 2
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        // Only 2 calls: G1, E1 (high score)
        when(callResponseSpec.content())
                .thenReturn("good answer")
                .thenReturn("{\"score\": 9, \"feedback\": \"excellent\"}");

        // Act
        Flux<AgentEvent> events = reflexionAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(3); // Attempt(1) + Evaluate(1,9) + FinalAnswer

        assertThat(eventList.get(0)).isInstanceOf(ReflexionAttemptEvent.class);
        assertThat(((ReflexionAttemptEvent) eventList.get(0)).round()).isEqualTo(1);

        assertThat(eventList.get(1)).isInstanceOf(ReflexionEvaluateEvent.class);
        assertThat(((ReflexionEvaluateEvent) eventList.get(1)).score()).isEqualTo(9);

        assertThat(eventList.get(2)).isInstanceOf(FinalAnswerEvent.class);

        // No ReflexionReflectEvent or Round 2 events
        assertThat(eventList.stream().filter(e -> e instanceof ReflexionReflectEvent)).isEmpty();
    }

    @Test
    void shouldStopOnEpsilon() {
        // Arrange: Round 2 score same as Round 1 (diff < EPSILON) and < PASS_THRESHOLD
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        // 5 calls: G1, E1(6), R1, G2, E2(6) -- diff = 0 < EPSILON=1
        when(callResponseSpec.content())
                .thenReturn("answer1")
                .thenReturn("{\"score\": 6, \"feedback\": \"ok\"}")
                .thenReturn("{\"reflection\": \"improve\"}")
                .thenReturn("answer2")
                .thenReturn("{\"score\": 6, \"feedback\": \"still ok\"}");

        // Act
        Flux<AgentEvent> events = reflexionAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(6); // Attempt(1) + Evaluate(1,6) + Reflect(1) + Attempt(2) + Evaluate(2,6) + FinalAnswer

        // No Round 2 Reflector (epsilon early stop)
        assertThat(eventList.get(5)).isInstanceOf(FinalAnswerEvent.class);
    }

    @Test
    void shouldRespectMaxReflectionsLimit() {
        // Arrange: 2 rounds max, both low score
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        // 5 calls: G1, E1(4), R1, G2, E2(5)
        when(callResponseSpec.content())
                .thenReturn("answer1")
                .thenReturn("{\"score\": 4, \"feedback\": \"needs work\"}")
                .thenReturn("{\"reflection\": \"improve\"}")
                .thenReturn("answer2")
                .thenReturn("{\"score\": 5, \"feedback\": \"better\"}");

        // Act
        Flux<AgentEvent> events = reflexionAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert: exactly 2 rounds (5 events + 1 FinalAnswer = 6), no 3rd Generator call
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(6);
    }

    @Test
    void shouldEmitErrorOnChatClientFailure() {
        // Arrange: ChatClient call throws
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);
        when(callResponseSpec.content()).thenThrow(new RuntimeException("API failure"));

        // Act
        Flux<AgentEvent> events = reflexionAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(1);
        assertThat(eventList.get(0)).isInstanceOf(ErrorEvent.class);
        assertThat(((ErrorEvent) eventList.get(0)).message()).contains("Reflexion");
    }

    @Test
    void shouldSelectBestAnswerFromRounds() {
        // Arrange: Round 1 score 3, Round 2 score 7 -> best-of-N picks Round 2
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        when(callResponseSpec.content())
                .thenReturn("poor answer")
                .thenReturn("{\"score\": 3, \"feedback\": \"poor\"}")
                .thenReturn("{\"reflection\": \"improve much\"}")
                .thenReturn("decent answer")
                .thenReturn("{\"score\": 7, \"feedback\": \"decent\"}");

        // Act
        Flux<AgentEvent> events = reflexionAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(6);
        assertThat(eventList.get(5)).isInstanceOf(FinalAnswerEvent.class);
        // Best-of-N: Round 2 score 7 > Round 1 score 3
        assertThat(((FinalAnswerEvent) eventList.get(5)).content()).isEqualTo("decent answer");
    }
}