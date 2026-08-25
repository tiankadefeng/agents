package com.agents.agent.patterns;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 共享流式原语：让"每次 LLM 调用内部"流式化的最小改造（quick-260825-gtx）。
 *
 * <p>解决"阻塞收集再发射"问题：调用期间逐 chunk 回调 delta（pattern 借此发射流式事件），
 * 同时用 {@link MessageAggregator} 聚合完整响应返回给调用方做控制流决策（解析 tool call、
 * 提取最终答案等）。控制流结构（命令式循环、可变历史）保持不动。
 *
 * <p>线程模型：{@code blockLast()} 在 {@code Flux.create} 订阅线程阻塞（与既有模式的
 * {@code .call()} / {@code blockLast()} 相同）；delta 回调从 WebClient nio 线程调用，
 * 调用方在其中执行 {@code sink.next(...)} 是安全的--FluxSink 内部 MPSC 队列保证
 * 线性化顺序，且 Reactive Streams 的 onNext-before-onComplete 保证 {@code blockLast()}
 * 返回晚于全部 delta，即 SSE 帧序严格为 {@code delta* -> 完整事件 -> 下一轮}。
 *
 * <p>chunk 解析与 {@code CoTAgentPattern} 同构：{@link DeepSeekAssistantMessage} 的
 * {@code reasoningContent} 走 reasoning delta，{@code getText()} 走 content delta，
 * null/空串跳过。
 */
public final class StreamingLlmCall {

    private StreamingLlmCall() {
    }

    /**
     * 流式消费 chunk 并阻塞至完成，返回聚合完整响应。
     *
     * @param responseFlux    LLM 流式响应（如 {@code chatModel.stream(prompt)}）
     * @param onReasoningDelta reasoning_content 增量回调（可为 no-op）
     * @param onContentDelta   content 增量回调
     * @return 聚合后的完整 {@link ChatResponse}；流为空时返回 null（调用方自行容错）
     * @throws reactor.core.Exceptions 异常直接传播（调用方 catch 后发 ErrorEvent）
     */
    public static ChatResponse streamAndAggregate(
            Flux<ChatResponse> responseFlux,
            Consumer<String> onReasoningDelta,
            Consumer<String> onContentDelta) {
        AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
        new MessageAggregator()
                .aggregate(
                        responseFlux.doOnNext(chunk -> emitDeltas(chunk, onReasoningDelta, onContentDelta)),
                        aggregated::set)
                .blockLast();
        return aggregated.get();
    }

    /**
     * 便捷重载：只消费 content delta，返回聚合文本。
     *
     * <p>per T-10-01: null 降级为空字符串，永不返回 null。
     */
    public static String streamContent(
            Flux<ChatResponse> responseFlux,
            Consumer<String> onContentDelta) {
        ChatResponse aggregated = streamAndAggregate(responseFlux, delta -> {
        }, onContentDelta);
        if (aggregated == null || aggregated.getResult() == null) {
            return "";
        }
        String text = aggregated.getResult().getOutput().getText();
        return text != null ? text : "";
    }

    private static void emitDeltas(
            ChatResponse chunk,
            Consumer<String> onReasoningDelta,
            Consumer<String> onContentDelta) {
        if (chunk == null || chunk.getResult() == null) {
            return;
        }
        AssistantMessage output = chunk.getResult().getOutput();
        if (output instanceof DeepSeekAssistantMessage dsm) {
            String reasoning = dsm.getReasoningContent();
            if (reasoning != null && !reasoning.isEmpty()) {
                onReasoningDelta.accept(reasoning);
            }
        }
        String text = output.getText();
        if (text != null && !text.isEmpty()) {
            onContentDelta.accept(text);
        }
    }
}
