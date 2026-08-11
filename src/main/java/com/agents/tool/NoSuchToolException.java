package com.agents.tool;

/**
 * Thrown by {@code ToolRegistry.byName(String toolName)} when the requested tool name
 * is not registered in the tool registry.
 *
 * <p>D-06: 由 {@code ToolController} (Plan 03) 捕获并返回 HTTP 404 + JSON 错误响应
 * {@code {"error":"未知工具：{toolName}"}}（非 SSE ErrorEvent - ToolController 是普通 REST 端点，
 * 不同于 AgentController 的 SSE 流式端点）。
 *
 * <p>文案遵循 {@code "未知工具：{toolName}"} 格式，与 {@code NoSuchPatternException} 的
 * {@code "未知模式：{id}"} 风格一致，保持前端 copywriting 统一。
 *
 * <p>T-4-04 (Information Disclosure): 未知工具名返回 404，不泄露已注册工具列表。
 * 仅 {@code GET /api/tools} 设计意图暴露工具列表。
 */
public class NoSuchToolException extends RuntimeException {

    /**
     * Construct with the unknown tool name.
     *
     * @param toolName 未知的工具名称（用于错误消息展示）
     */
    public NoSuchToolException(String toolName) {
        super("未知工具：" + toolName);
    }
}