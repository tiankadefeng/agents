package com.agents.api;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.AgentRegistry;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.NoSuchPatternException;
import com.agents.api.dto.AgentRequest;
import com.agents.streaming.SseEventEmitter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
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
 * <p><strong>Success Criteria #2 落地:</strong> 未知 {@code patternId} 时返回
 * HTTP <strong>404</strong> + {@code Content-Type: text/event-stream} + 单个 {@link ErrorEvent}
 * SSE 帧。404 状态码正确表达 "资源不存在" 语义，SSE content-type 与 ErrorEvent 帧保持
 * D-08 SSE 错误契约（流外异常通过 SSE error 事件返回，非 HTTP 500）。
 *
 * <p><strong>404 文案（02-UI-SPEC.md §Copywriting Contract 锁定）:</strong>
 * {@code "未知模式：{patternId}。请检查模式 ID 或刷新页面获取可用模式列表。"}
 *
 * <p>直接返回 {@code Flux<ServerSentEvent<String>>}（而非包装在 {@code ResponseEntity} 中），
 * 因为 Spring MVC 的 {@code HttpEntityMethodProcessor} 无法识别 {@code ResponseEntity<Flux<...>>}
 * 为响应式类型，会尝试用 {@code HttpMessageConverter} 序列化 {@code FluxJust} 失败。
 * 通过 {@link HttpServletResponse#setStatus} 设置自定义状态码。
 *
 * <p>反模式规避 (RESEARCH.md §反模式 5 god controller): 控制器仅做 HTTP &lt;-&gt; 领域转换 -
 * 解析请求、查 {@link AgentRegistry}、回流 SSE。不构造提示词、不直接调用 ChatClient
 * （pattern 内部负责）。
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
     * <p>{@code registry.require(patternId)} 同步调用，找不到时立即返回 404 + ErrorEvent 帧；
     * 找到时进入 reactive chain，由 pattern 控制流式输出。
     *
     * @param req      请求体（{@code patternId} + {@code question} + 可选 {@code options}）
     * @param response 用于设置 404 状态码（{@code ResponseEntity} 包装会破坏 {@code Flux} 流式识别）
     * @return {@code Flux<ServerSentEvent<String>>} - 正常流或 ErrorEvent 帧
     */
    @PostMapping(value = "/api/agent/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> execute(@RequestBody AgentRequest req, HttpServletResponse response) {
        AgentPattern pattern;
        try {
            pattern = registry.require(req.patternId());
        } catch (NoSuchPatternException ex) {
            response.setStatus(404);
            return Flux.just(sseEmitter.fromAgentEvent(new ErrorEvent(
                Instant.now(),
                "未知模式：" + req.patternId() + "。请检查模式 ID 或刷新页面获取可用模式列表。"
            )));
        }
        return pattern
            .execute(new AgentContext(req.question(), req.options()))
            .map(sseEmitter::fromAgentEvent)
            .onErrorResume(ex -> Flux.just(sseEmitter.fromAgentEvent(new ErrorEvent(
                Instant.now(),
                ex.getClass().getSimpleName() + ": " + ex.getMessage()
            ))));
    }
}
