package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.RoleDevEvent;
import com.agents.agent.core.RolePmEvent;
import com.agents.agent.core.RoleTesterEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
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
 *   <li>Full 5-round role-play cycle: 15 role events + 1 FinalAnswerEvent (16 LLM calls)</li>
 *   <li>Fixed role order PM -&gt; Dev -&gt; Tester within each round, round 1-5</li>
 *   <li>ErrorEvent on ChatClient failure</li>
 *   <li>buildHistoryPrompt output format (empty and populated history)</li>
 *   <li>Null LLM content degraded to empty string (T-10-01)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RolePlayingAgentPatternTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @InjectMocks
    private RolePlayingAgentPattern rolePlayingAgentPattern;

    private ChatClient.ChatClientRequestSpec mockRequestChain(ChatClient chatClient,
                                                              ChatClient.CallResponseSpec callResponseSpec) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        return requestSpec;
    }

    @Test
    void shouldExecuteFullRolePlayCycleAndEmitEvents() {
        // Arrange: 16 calls = 15 role utterances + 1 summary
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        // Round r, role i: "r-i"; summary: "summary text"
        var contents = new java.util.ArrayList<String>();
        for (int r = 1; r <= 5; r++) {
            contents.add("pm-" + r);
            contents.add("dev-" + r);
            contents.add("tester-" + r);
        }
        contents.add("summary text");
        when(callResponseSpec.content()).thenReturn(contents.get(0),
                contents.subList(1, contents.size()).toArray(new String[0]));

        // Act
        Flux<AgentEvent> events = rolePlayingAgentPattern.execute(
                new AgentContext("test question", Map.of()));

        // Assert
        List<AgentEvent> eventList = events.collectList().block(Duration.ofSeconds(5));
        assertThat(eventList).isNotNull();
        // 15 role events + 1 FinalAnswerEvent
        assertThat(eventList).hasSize(16);

        // Verify exact order: PM(1) -> Dev(1) -> Tester(1) -> PM(2) -> ... -> Tester(5) -> FinalAnswer
        for (int r = 1; r <= 5; r++) {
            int base = (r - 1) * 3;
            assertThat(eventList.get(base)).isInstanceOf(RolePmEvent.class);
            assertThat(((RolePmEvent) eventList.get(base)).round()).isEqualTo(r);
            assertThat(((RolePmEvent) eventList.get(base)).role()).isEqualTo("PM");
            assertThat(((RolePmEvent) eventList.get(base)).content()).isEqualTo("pm-" + r);

            assertThat(eventList.get(base + 1)).isInstanceOf(RoleDevEvent.class);
            assertThat(((RoleDevEvent) eventList.get(base + 1)).round()).isEqualTo(r);
            assertThat(((RoleDevEvent) eventList.get(base + 1)).role()).isEqualTo("Dev");
            assertThat(((RoleDevEvent) eventList.get(base + 1)).content()).isEqualTo("dev-" + r);

            assertThat(eventList.get(base + 2)).isInstanceOf(RoleTesterEvent.class);
            assertThat(((RoleTesterEvent) eventList.get(base + 2)).round()).isEqualTo(r);
            assertThat(((RoleTesterEvent) eventList.get(base + 2)).role()).isEqualTo("Tester");
            assertThat(((RoleTesterEvent) eventList.get(base + 2)).content()).isEqualTo("tester-" + r);
        }

        assertThat(eventList.get(15)).isInstanceOf(FinalAnswerEvent.class);
        assertThat(((FinalAnswerEvent) eventList.get(15)).content()).isEqualTo("summary text");

        // Exactly 16 LLM calls (15 role + 1 summary)
        verify(callResponseSpec, times(16)).content();
    }

    @Test
    void shouldEmitRoleEventsInCorrectOrder() {
        // Arrange: distinct contents per role to verify ordering within each round
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        var contents = new java.util.ArrayList<String>();
        for (int r = 1; r <= 5; r++) {
            contents.add("PM说的" + r);
            contents.add("Dev说的" + r);
            contents.add("Tester说的" + r);
        }
        contents.add("总结");
        when(callResponseSpec.content()).thenReturn(contents.get(0),
                contents.subList(1, contents.size()).toArray(new String[0]));

        // Act
        List<AgentEvent> eventList = rolePlayingAgentPattern.execute(
                new AgentContext("test question", Map.of()))
                .collectList().block(Duration.ofSeconds(5));

        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(16);

        // Round numbers increase 1..5, each round has exactly PM -> Dev -> Tester
        for (int r = 1; r <= 5; r++) {
            int base = (r - 1) * 3;
            assertThat(eventList.get(base)).isInstanceOf(RolePmEvent.class);
            assertThat(eventList.get(base + 1)).isInstanceOf(RoleDevEvent.class);
            assertThat(eventList.get(base + 2)).isInstanceOf(RoleTesterEvent.class);
            assertThat(((RolePmEvent) eventList.get(base)).round()).isEqualTo(r);
            assertThat(((RoleDevEvent) eventList.get(base + 1)).round()).isEqualTo(r);
            assertThat(((RoleTesterEvent) eventList.get(base + 2)).round()).isEqualTo(r);
            assertThat(((RolePmEvent) eventList.get(base)).content()).isEqualTo("PM说的" + r);
            assertThat(((RoleDevEvent) eventList.get(base + 1)).content()).isEqualTo("Dev说的" + r);
            assertThat(((RoleTesterEvent) eventList.get(base + 2)).content()).isEqualTo("Tester说的" + r);
        }
    }

    @Test
    void shouldEmitErrorOnChatClientFailure() {
        // Arrange: first call throws
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);
        when(callResponseSpec.content()).thenThrow(new RuntimeException("API failure"));

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
        // Arrange: Round 1 PM returns null, others return normal content
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        mockRequestChain(chatClient, callResponseSpec);

        var contents = new java.util.ArrayList<String>();
        contents.add(null); // PM round 1 -> null
        for (int r = 1; r <= 5; r++) {
            if (r != 1) contents.add("pm-" + r);
            contents.add("dev-" + r);
            contents.add("tester-" + r);
        }
        contents.add("summary");
        when(callResponseSpec.content()).thenReturn(contents.get(0),
                contents.subList(1, contents.size()).toArray(new String[0]));

        // Act
        List<AgentEvent> eventList = rolePlayingAgentPattern.execute(
                new AgentContext("test question", Map.of()))
                .collectList().block(Duration.ofSeconds(5));

        // Assert: null degraded to empty string, subsequent roles still executed
        assertThat(eventList).isNotNull();
        assertThat(eventList).hasSize(16); // no truncation, all events emitted

        RolePmEvent firstPm = (RolePmEvent) eventList.get(0);
        assertThat(firstPm.content()).isNotNull();
        assertThat(firstPm.content()).isEqualTo("");

        // Subsequent Dev round 1 still executed with correct content
        assertThat(eventList.get(1)).isInstanceOf(RoleDevEvent.class);
        assertThat(((RoleDevEvent) eventList.get(1)).content()).isEqualTo("dev-1");

        assertThat(eventList.get(15)).isInstanceOf(FinalAnswerEvent.class);
        assertThat(((FinalAnswerEvent) eventList.get(15)).content()).isEqualTo("summary");
    }
}
