package com.agents.api;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.AgentRegistry;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.NoSuchPatternException;
import com.agents.api.dto.AgentRequest;
import com.agents.streaming.SseEventEmitter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Instant;

/**
 * {@code POST /api/agent/execute} - SSE 流式执行 agent 模式。
 *
 * <p>请求体为 {@link AgentRequest}（{@code patternId} + {@code question} + 可选 {@code options}），
 * 返回 {@code text/event-stream} 流，包含 {@link com.agents.agent.core.AgentEvent} 子类型 SSE 帧。
 *
 * <p><strong>Success Criteria #2 落地（D-06 兼容方式）:</strong> 未知 {@code patternId} 时返回
 * HTTP <strong>404</strong> + {@code Content-Type: text/event-stream} + 单个 {@link ErrorEvent}
 * SSE 帧。404 状态码正确表达 "资源不存在" 语义，SSE content-type 与 ErrorEvent 帧保持
 * D-08 SSE 错误契约（流外异常通过 SSE error 事件返回，非 HTTP 500）。
 *
 * <p><strong>404 文案（02-UI-SPEC.md §Copywriting Contract 锁定）:</strong>
 * {@code "未知模式：{patternId}。请检查模式 ID 或刷新页面获取可用模式列表。"}
 *
 * <p>正常路径：{@code ResponseEntity.ok()} + {@code text/event-stream} +
 * {@code pattern.execute(ctx).map(sseEmitter::fromAgentEvent).onErrorResume(...)}。
 * In-stream 异常通过 {@code onErrorResume} 转为 {@link ErrorEvent} SSE 帧（与 D-08 一致），
 * message 含异常 type + message，不含 stacktrace（T-2-13 信息泄露防护，ASVS L1 V7.1 合规）。
 *
 * <p>反模式规避 (RESEARCH.md §反模式 5 god controller): 控制器仅做 HTTP &lt;-&gt; 领域转换 -
 * 解析请求、查 {@link AgentRegistry}、回流 SSE。不构造提示词、不直接调用 ChatClient
 * （pattern 内部负责）。反模式 2 规避：禁用 {@code switch(patternId)} 硬编码分发，必须经
 * {@link AgentRegistry#require(String)} 查找。
 */
@RestController
public class AgentController {

    private final AgentRegistry registry;
    private final SseEventEmitter sseEmitter;

    public AgentController(AgentRegistry registry, SseEventEmitter sseEmitter) {
        this.registry = registry;
        this.sseEmitter = sseEmitter;
    }

    /**
     * SSE 流式执行 agent 模式。
     *
     * <p>返回 {@link ResponseEntity} 以支持 404 状态码 + SSE body（{@code Flux<ServerSentEvent>}）。
     * {@code registry.require(patternId)} 同步调用，找不到时立即返回 404 + ErrorEvent 帧；
     * 找到时进入 reactive chain，由 pattern 控制流式输出。
     *
     * @param req 请求体（{@code patternId} + {@code question} + 可选 {@code options}）
     * @return {@code ResponseEntity<Flux<ServerSentEvent<String>>>} - 200 + 正常流，或 404 + ErrorEvent 帧
     */
    @PostMapping(value = "/api/agent/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> execute(@RequestBody AgentRequest req) {
        AgentPattern pattern;
        try {
            pattern = registry.require(req.patternId());
        } catch (NoSuchPatternException ex) {
            // SC#2 落地（D-06 兼容方式）: 404 + text/event-stream + 单个 ErrorEvent SSE 帧
            // 02-UI-SPEC.md §Copywriting Contract 锁定文案
            Flux<ServerSentEvent<String>> errorFlux = Flux.just(sseEmitter.fromAgentEvent(new ErrorEvent(
                Instant.now(),
                "未知模式：" + req.patternId() + "。请检查模式 ID 或刷新页面获取可用模式列表。"
            )));
            return ResponseEntity.status(404)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(errorFlux);
        }
        // 正常分支：200 + text/event-stream + pattern.execute(ctx) 流式 SSE 帧
        Flux<ServerSentEvent<String>> stream = pattern
            .execute(new AgentContext(req.question(), req.options()))
            .map(sseEmitter::fromAgentEvent)
            .onErrorResume(ex -> Flux.just(sseEmitter.fromAgentEvent(new ErrorEvent(
                Instant.now(),
                ex.getClass().getSimpleName() + ": " + ex.getMessage()
            ))));
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(stream);
    }
}
