package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.RoleDevEvent;
import com.agents.agent.core.RolePmEvent;
import com.agents.agent.core.RoleSpeechDeltaEvent;
import com.agents.agent.core.RoleTesterEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RolePlayingAgentPattern} - 5 rounds x 3 roles + summary.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Full 5-round role-play cycle: 15 role events + FinalAnswerEvent (16 LLM calls)</li>
 *   <li>Fixed role order PM -&gt; Dev -&gt; Tester within each round, round 1-5</li>
 *   <li>流式改造: RoleSpeechDeltaEvent per speech, replaced by complete Role*Event</li>
 *   <li>ErrorEvent on ChatClient failure</li>
 *   <li>buildHistoryPrompt output format (empty and populated history)</li>
 *   <li>Null LLM content degraded to empty string (T-10-01)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePlayingAgentPatternTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @InjectMocks
    private RolePlayingAgentPattern rolePlayingAgentPattern;

    private ChatClient.ChatClientRequestSpec mockRequestChain(ChatClient chatClient,
                                                              ChatClient.StreamResponseSpec streamResponseSpec) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        return requestSpec;
    }

    private ChatResponse chatResponseWithContent(String content) {
        AssistantMessage msg = AssistantMessage.builder().content(content).build();
        return new ChatResponse(List.of(new Generation(msg)));
    }

    @Test
    void shouldExecuteFullRolePlayCycleAndEmitEvents() {
        // Arrange: 16 calls = 15 role utterances + 1 summary
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, streamResponseSpec);

        // Round r, role i: "r-i"; summary: "summary text"
        List<Flux<ChatResponse>> fluxes = new ArrayList<>();
        for (int r = 1; r <= 5; r++) {
            fluxes.add(Flux.just(chatResponseWithContent("pm-" + r)));
            fluxes.add(Flux.just(chatResponseWithContent("dev-" + r)));
            fluxes.add(Flux.just(chatResponseWithContent("tester-" + r)));
        }
        fluxes.add(Flux.just(chatResponseWithContent("summary text")));
        when(streamResponseSpec.chatResponse()).thenReturn(
                fluxes.get(0), fluxes.subList(1, fluxes.size()).toArray(new Flux[0]));

        // Act
        Flux<AgentEvent> events = rolePlayingAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();

        // Filter-by-type: 15 complete Role*Events
        List<AgentEvent> roleEvents = eventList.stream()
                .filter(ev -> ev instanceof RolePmEvent || ev instanceof RoleDevEvent || ev instanceof RoleTesterEvent)
                .toList();
        assertThat(roleEvents).hasSize(15);

        // Verify exact order: PM(1) -> Dev(1) -> Tester(1) -> PM(2) -> ... -> Tester(5)
        for (int r = 1; r <= 5; r++) {
            int base = (r - 1) * 3;
            assertThat(roleEvents.get(base)).isInstanceOf(RolePmEvent.class);
            assertThat(((RolePmEvent) roleEvents.get(base)).round()).isEqualTo(r);
            assertThat(((RolePmEvent) roleEvents.get(base)).role()).isEqualTo("PM");
            assertThat(((RolePmEvent) roleEvents.get(base)).content()).isEqualTo("pm-" + r);

            assertThat(roleEvents.get(base + 1)).isInstanceOf(RoleDevEvent.class);
            assertThat(((RoleDevEvent) roleEvents.get(base + 1)).round()).isEqualTo(r);
            assertThat(((RoleDevEvent) roleEvents.get(base + 1)).role()).isEqualTo("Dev");
            assertThat(((RoleDevEvent) roleEvents.get(base + 1)).content()).isEqualTo("dev-" + r);

            assertThat(roleEvents.get(base + 2)).isInstanceOf(RoleTesterEvent.class);
            assertThat(((RoleTesterEvent) roleEvents.get(base + 2)).round()).isEqualTo(r);
            assertThat(((RoleTesterEvent) roleEvents.get(base + 2)).role()).isEqualTo("Tester");
            assertThat(((RoleTesterEvent) roleEvents.get(base + 2)).content()).isEqualTo("tester-" + r);
        }

        // FinalAnswerEvent: summary (single-chunk in this mock)
        List<FinalAnswerEvent> finalEvents = eventList.stream()
                .filter(FinalAnswerEvent.class::isInstance)
                .map(FinalAnswerEvent.class::cast)
                .toList();
        assertThat(finalEvents).hasSize(1);
        assertThat(finalEvents.get(0).content()).isEqualTo("summary text");

        // 流式改造: 15 RoleSpeechDeltaEvents (one per role speech)
        List<RoleSpeechDeltaEvent> deltas = eventList.stream()
                .filter(RoleSpeechDeltaEvent.class::isInstance)
                .map(RoleSpeechDeltaEvent.class::cast)
                .toList();
        assertThat(deltas).hasSize(15);

        // Exactly 16 LLM calls (15 role + 1 summary)
        verify(streamResponseSpec, times(16)).chatResponse();
    }

    @Test
    void shouldStreamSpeechDeltasBeforeCompleteEvents() {
        // Arrange: PM round 1 speech in 3 chunks, then remaining speeches single-chunk
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, streamResponseSpec);

        // PM round 1: 3 chunks
        List<Flux<ChatResponse>> fluxes = new ArrayList<>();
        fluxes.add(Flux.just(
                chatResponseWithContent("需求"),
                chatResponseWithContent("是"),
                chatResponseWithContent("登录功能")));
        for (int r = 1; r <= 5; r++) {
            if (r != 1) fluxes.add(Flux.just(chatResponseWithContent("pm-" + r)));
            fluxes.add(Flux.just(chatResponseWithContent("dev-" + r)));
            fluxes.add(Flux.just(chatResponseWithContent("tester-" + r)));
        }
        fluxes.add(Flux.just(chatResponseWithContent("summary")));
        when(streamResponseSpec.chatResponse()).thenReturn(
                fluxes.get(0), fluxes.subList(1, fluxes.size()).toArray(new Flux[0]));

        // Act
        List<AgentEvent> eventList = rolePlayingAgentPattern.execute(
                        new AgentContext("test question", Map.of()))
                .collectList().block(Duration.ofSeconds(5));

        // Assert: PM round 1 delta chunks concatenate to full content, all before complete RolePmEvent
        List<AgentEvent> round1PmDeltas = eventList.stream()
                .filter(ev -> ev instanceof RoleSpeechDeltaEvent d && d.round() == 1 && "PM".equals(d.role()))
                .toList();
        assertThat(round1PmDeltas).hasSize(3);
        String concatenated = round1PmDeltas.stream()
                .map(ev -> ((RoleSpeechDeltaEvent) ev).content())
                .reduce("", String::concat);
        assertThat(concatenated).isEqualTo("需求是登录功能");

        // Complete PM event content is the aggregated text
        RolePmEvent pmEvent = eventList.stream()
                .filter(RolePmEvent.class::isInstance)
                .map(RolePmEvent.class::cast)
                .filter(e -> e.round() == 1)
                .findFirst().orElseThrow();
        assertThat(pmEvent.content()).isEqualTo("需求是登录功能");

        // All 3 deltas arrive before the complete PM event
        int lastDeltaIdx = eventList.indexOf(round1PmDeltas.get(2));
        int pmEventIdx = eventList.indexOf(pmEvent);
        assertThat(lastDeltaIdx).isLessThan(pmEventIdx);
    }

    @Test
    void shouldEmitErrorOnChatClientFailure() {
        // Arrange: first stream call errors
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, streamResponseSpec);
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.error(new RuntimeException("API failure")));

        // Act
        Flux<AgentEvent> events = rolePlayingAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(1);
        assertThat(eventList.get(0)).isInstanceOf(ErrorEvent.class);
        assertThat(((ErrorEvent) eventList.get(0)).message()).contains("Role-playing");
    }

    @Test
    void shouldBuildHistoryPromptCorrectly() {
        // Empty history: first-round prompt
        String empty = RolePlayingAgentPattern.buildHistoryPrompt(List.of(), "设计登录功能");
        assertThat(empty).contains("原始问题: 设计登录功能");
        assertThat(empty).contains("暂无");

        // Populated history: full round-role-content lines in order
        List<RolePlayingAgentPattern.Utterance> history = List.of(
                new RolePlayingAgentPattern.Utterance(1, "PM", "需求是登录"),
                new RolePlayingAgentPattern.Utterance(1, "Dev", "方案是JWT"),
                new RolePlayingAgentPattern.Utterance(1, "Tester", "要测边界"),
                new RolePlayingAgentPattern.Utterance(2, "PM", "追问token过期"));
        String prompt = RolePlayingAgentPattern.buildHistoryPrompt(history, "设计登录功能");

        assertThat(prompt).startsWith("原始问题: 设计登录功能");
        assertThat(prompt).contains("当前对话历史");
        assertThat(prompt).contains("Round 1 PM: 需求是登录");
        assertThat(prompt).contains("Round 1 Dev: 方案是JWT");
        assertThat(prompt).contains("Round 1 Tester: 要测边界");
        assertThat(prompt).contains("Round 2 PM: 追问token过期");

        // Ordering: Round 1 PM before Round 1 Dev before Round 2 PM
        int idx1 = prompt.indexOf("Round 1 PM:");
        int idx2 = prompt.indexOf("Round 1 Dev:");
        int idx3 = prompt.indexOf("Round 2 PM:");
        assertThat(idx1).isLessThan(idx2);
        assertThat(idx2).isLessThan(idx3);
    }

    @Test
    void shouldHandleNullContentGracefully() {
        // Arrange: Round 1 PM returns empty (null content -> empty string), others return normal content
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, streamResponseSpec);

        List<Flux<ChatResponse>> fluxes = new ArrayList<>();
        fluxes.add(Flux.just(chatResponseWithContent(null))); // PM round 1 -> null content
        for (int r = 1; r <= 5; r++) {
            if (r != 1) fluxes.add(Flux.just(chatResponseWithContent("pm-" + r)));
            fluxes.add(Flux.just(chatResponseWithContent("dev-" + r)));
            fluxes.add(Flux.just(chatResponseWithContent("tester-" + r)));
        }
        fluxes.add(Flux.just(chatResponseWithContent("summary")));
        when(streamResponseSpec.chatResponse()).thenReturn(
                fluxes.get(0), fluxes.subList(1, fluxes.size()).toArray(new Flux[0]));

        // Act
        List<AgentEvent> eventList = rolePlayingAgentPattern.execute(
                        new AgentContext("test question", Map.of()))
                .collectList().block(Duration.ofSeconds(5));

        // Assert: null degraded to empty string, subsequent roles still executed
        assertThat(eventList).isNotNull();
        List<AgentEvent> roleEvents = eventList.stream()
                .filter(ev -> ev instanceof RolePmEvent || ev instanceof RoleDevEvent || ev instanceof RoleTesterEvent)
                .toList();
        assertThat(roleEvents).hasSize(15);

        RolePmEvent firstPm = (RolePmEvent) roleEvents.get(0);
        assertThat(firstPm.content()).isNotNull();
        assertThat(firstPm.content()).isEmpty(); // null degraded to ""

        // Subsequent Dev round 1 still executed with correct content
        assertThat(roleEvents.get(1)).isInstanceOf(RoleDevEvent.class);
        assertThat(((RoleDevEvent) roleEvents.get(1)).content()).isEqualTo("dev-1");

        // Summary still emitted
        List<FinalAnswerEvent> finalEvents = eventList.stream()
                .filter(FinalAnswerEvent.class::isInstance)
                .map(FinalAnswerEvent.class::cast)
                .toList();
        assertThat(finalEvents).hasSize(1);
        assertThat(finalEvents.get(0).content()).isEqualTo("summary");
    }
}
