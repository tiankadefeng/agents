package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.ReasoningEvent;
import com.agents.streaming.SseEventEmitter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * CoT（思维链）模式实现 - 一步步展示推理过程。
 *
 * <p>使用 DeepSeek reasoner 模型的 {@code reasoning_content} 字段发射 {@link ReasoningEvent}，
 * 最终答案发射 {@link FinalAnswerEvent}。
 *
 * <p>D-01: 注入 {@link ChatClient.Builder}（prototype scope），每请求构建独立 ChatClient 实例，
 * 覆盖默认系统提示为 CoT 专用提示。
 */
@Component
public class CoTAgentPattern implements AgentPattern {

    private static final String COT_SYSTEM_PROMPT = "请一步步思考并展示推理过程。每个推理步骤清晰标注。";

    private final ChatClient.Builder chatClientBuilder;
    private final SseEventEmitter sseEmitter;

    public CoTAgentPattern(ChatClient.Builder chatClientBuilder, SseEventEmitter sseEmitter) {
        this.chatClientBuilder = chatClientBuilder;
        this.sseEmitter = sseEmitter;
    }

    @Override
    public String id() {
        return "cot";
    }

    @Override
    public String displayName() {
        return "CoT 思维链";
    }

    @Override
    public String description() {
        return "一步步写推理过程，适合数学/逻辑问题";
    }

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        // Build ChatClient with CoT-specific system prompt
        ChatClient chatClient = chatClientBuilder
            .defaultSystem(COT_SYSTEM_PROMPT)
            .build();

        // Follow PingController streaming pattern
        return chatClient.prompt()
            .user(ctx.question())
            .stream()
            .chatResponse()
            .<AgentEvent>flatMap(cr -> {
                var output = cr.getResult().getOutput();
                // Handle DeepSeek reasoning_content -> emit ReasoningEvent
                if (output instanceof DeepSeekAssistantMessage dsm) {
                    String reasoning = dsm.getReasoningContent();
                    if (reasoning != null && !reasoning.isEmpty()) {
                        return Mono.just(new ReasoningEvent(Instant.now(), reasoning));
                    }
                }
                // Handle text content -> emit FinalAnswerEvent
                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    return Mono.just(new FinalAnswerEvent(Instant.now(), text));
                }
                // Skip empty chunks
                return Mono.empty();
            })
            .onErrorResume(ex -> Flux.just(new ErrorEvent(Instant.now(), ex.getMessage())));
    }
}