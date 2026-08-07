package com.agents.agent.core;

/**
 * Thrown by {@code AgentRegistry.require(String id)} when the requested pattern ID
 * is not registered.
 *
 * <p>D-06: 由 {@code GlobalExceptionHandler} 捕获并转为 {@link ErrorEvent} SSE 帧
 * （D-08 兼容方式 - HTTP 200 + {@code text/event-stream} + 单个 ErrorEvent，非 HTTP 500）。
 *
 * <p>文案遵循 02-UI-SPEC.md §Copywriting Contract：{@code "未知模式：{id}"}。
 * 完整文案由 {@code GlobalExceptionHandler} 拼接为：
 * {@code "未知模式：{id}。请检查模式 ID 或刷新页面获取可用模式列表。"}
 */
public class NoSuchPatternException extends RuntimeException {

    /**
     * Construct with the unknown pattern ID.
     *
     * @param id 未知的模式 ID（用于错误消息展示）
     */
    public NoSuchPatternException(String id) {
        super("未知模式：" + id);
    }
}
