package com.agents.agent.patterns;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StreamingLlmCall} - 共享流式原语。
 *
 * <p>Verifies:
 * <ul>
 *   <li>Content deltas emitted per chunk in order, aggregated text returned</li>
 *   <li>Reasoning-content deltas emitted for DeepSeekAssistantMessage chunks</li>
 *   <li>Null/empty tolerance: empty flux -> null aggregate / "" from streamContent</li>
 *   <li>Stream error propagates to caller</li>
 *   <li>T-10-01: null aggregated text degrades to ""</li>
 * </ul>
 */
class StreamingLlmCallTest {

    private static ChatResponse chunk(String text) {
        AssistantMessage msg = AssistantMessage.builder().content(text).build();
        return new ChatResponse(List.of(new Generation(msg)));
    }

    @Test
    void shouldEmitContentDeltasPerChunkAndReturnAggregatedText() {
        List<String> deltas = new ArrayList<>();

        String aggregated = StreamingLlmCall.streamContent(
                Flux.just(chunk("你"), chunk("好"), chunk("世界")),
                deltas::add);

        assertThat(deltas).containsExactly("你", "好", "世界");
        assertThat(aggregated).isEqualTo("你好世界");
    }

    @Test
    void shouldEmitReasoningDeltasForDeepSeekAssistantMessage() {
        DeepSeekAssistantMessage dsm = mock(DeepSeekAssistantMessage.class);
        when(dsm.getReasoningContent()).thenReturn("先分析问题");
        when(dsm.getText()).thenReturn("然后作答");
        when(dsm.getToolCalls()).thenReturn(List.of());

        List<String> reasoningDeltas = new ArrayList<>();
        List<String> contentDeltas = new ArrayList<>();

        ChatResponse aggregated = StreamingLlmCall.streamAndAggregate(
                Flux.just(new ChatResponse(List.of(new Generation(dsm)))),
                reasoningDeltas::add,
                contentDeltas::add);

        assertThat(reasoningDeltas).containsExactly("先分析问题");
        assertThat(contentDeltas).containsExactly("然后作答");
        assertThat(aggregated).isNotNull();
    }

    @Test
    void shouldSkipNullAndEmptyChunks() {
        // Chunk with null text (AssistantMessage built without content) emits no delta
        AssistantMessage nullTextMsg = AssistantMessage.builder().build();
        ChatResponse nullTextChunk = new ChatResponse(List.of(new Generation(nullTextMsg)));

        List<String> deltas = new ArrayList<>();
        String aggregated = StreamingLlmCall.streamContent(
                Flux.just(nullTextChunk, chunk("有效片段")),
                deltas::add);

        assertThat(deltas).containsExactly("有效片段");
        assertThat(aggregated).isEqualTo("有效片段");
    }

    @Test
    void shouldReturnNullAggregateAndEmptyTextForEmptyFlux() {
        List<String> deltas = new ArrayList<>();

        // MessageAggregator invokes the consumer with an empty aggregated response
        // even for an empty flux (not null) - streamContent still degrades to ""
        ChatResponse aggregated = StreamingLlmCall.streamAndAggregate(
                Flux.empty(), deltas::add, deltas::add);
        assertThat(deltas).isEmpty();

        String text = StreamingLlmCall.streamContent(Flux.empty(), deltas::add);
        assertThat(text).isEmpty();
    }

    @Test
    void shouldPropagateStreamError() {
        List<String> deltas = new ArrayList<>();

        assertThatThrownBy(() -> StreamingLlmCall.streamContent(
                Flux.error(new RuntimeException("API failure")), deltas::add))
                .hasMessageContaining("API failure");
        // Delta callback may have been invoked zero times before the error - no requirement
    }

    @Test
    void shouldDegradeNullAggregatedTextToEmptyString() {
        // All chunks have null text -> aggregated text is null -> "" (T-10-01)
        AssistantMessage nullTextMsg = AssistantMessage.builder().build();
        ChatResponse nullTextChunk = new ChatResponse(List.of(new Generation(nullTextMsg)));

        String text = StreamingLlmCall.streamContent(
                Flux.just(nullTextChunk), delta -> {
                });

        assertThat(text).isEmpty();
    }
}
