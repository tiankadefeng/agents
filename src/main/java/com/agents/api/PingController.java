package com.agents.api;

import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.ReasoningEvent;
import com.agents.api.dto.PingRequest;
import com.agents.streaming.SseEventEmitter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Phase 2 重构 (D-05): 注入 SseEventEmitter 实例，发射 ReasoningEvent/FinalAnswerEvent/ErrorEvent。
 * 保留 /api/ping 端点。Phase 1 的 ping 事件废弃 - 空 chunk 跳过（Flux.empty()）。
 *
 * <p>D-01 推翻 Phase 1 D-02 的 event=message + data.type 方案，SSE event 字段改为 Java class
 * simple name（如 {@code ReasoningEvent}），由 {@link SseEventEmitter#fromAgentEvent} 统一序列化。
 *
 * <p>DeepSeek {@code reasoning_content} 包为 {@link ReasoningEvent}，{@code content} 包为
 * {@link FinalAnswerEvent}，错误包为 {@link ErrorEvent}。空 chunk（reasoning 与 content 均为空）
 * 跳过（{@link Mono#empty()}），不再发射 ping 事件。
 */
@RestController
public class PingController {

    private final ChatClient chatClient;
    private final SseEventEmitter sseEmitter;

    public PingController(ChatClient chatClient, SseEventEmitter sseEmitter) {
        this.chatClient = chatClient;
        this.sseEmitter = sseEmitter;
    }

    @PostMapping(value = "/api/ping", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ping(@RequestBody PingRequest req) {
        if (req.question() == null || req.question().isBlank()) {
            return Flux.just(sseEmitter.fromAgentEvent(
                new ErrorEvent(Instant.now(), "问题不能为空")
            ));
        }
        // Model is configured in application.yml (deepseek-reasoner per D-05)
        return chatClient.prompt()
            .user(req.question())
            .stream()
            .chatResponse()
            .flatMap(cr -> {
                var output = cr.getResult().getOutput();
                if (output instanceof DeepSeekAssistantMessage dsm) {
                    String reasoning = dsm.getReasoningContent();
                    if (reasoning != null && !reasoning.isEmpty()) {
                        return Mono.just(sseEmitter.fromAgentEvent(
                            new ReasoningEvent(Instant.now(), reasoning)
                        ));
                    }
                }
                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    return Mono.just(sseEmitter.fromAgentEvent(
                        new FinalAnswerEvent(Instant.now(), text)
                    ));
                }
                // Phase 1 的 ping 事件废弃 - D-01 不再有 ping event，空 chunk 跳过
                return Mono.empty();
            })
            .onErrorResume(ex -> Flux.just(sseEmitter.fromAgentEvent(
                new ErrorEvent(Instant.now(), ex.getMessage())
            )));
    }
}
