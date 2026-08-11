package com.agents.tool.builtin;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 数学表达式计算工具（使用 exp4j 0.4.8 真实计算）。
 *
 * <p>D-04: 使用第三方表达式库 {@code exp4j 0.4.8} 实现真实计算。
 * 基于 Dijkstra Shunting Yard 算法，纯 Java 安全表达式求值，无反射/字节码安全风险。
 * 支持四则运算、括号、幂运算、数学函数（sqrt, sin, cos, tan, log, log10, exp, abs, ceil, floor）。
 *
 * <p>Pitfall 4 (04-RESEARCH.md): exp4j 非法表达式（如 {@code 2++3}）抛
 * {@link IllegalArgumentException} / {@link ArithmeticException}。本方法 catch 后
 * 返回错误 JSON 字符串（不抛异常），让 LLM 在 observation 中看到错误信息可自我纠正
 * （Phase 5 ReAct 依赖此行为）。
 *
 * <p>D-07: 返回结构化 JSON 字符串（String），便于 LLM 解析。
 * 工具名为简短英文 {@code calculator}。
 */
@Component
public class CalculatorTool {

    @Tool(name = "calculator",
          description = "计算数学表达式。支持四则运算 (+,-,*,/)、括号、幂运算 (^)、取模 (%) 及数学函数 (sqrt, sin, cos, tan, log, log10, exp, abs, ceil, floor)。返回 JSON 字符串，含 expression, result 字段。")
    public String calculate(@ToolParam(description = "数学表达式，如 2+3*4 或 sqrt(16)+log(100)") String expression) {
        if (expression == null || expression.isBlank()) {
            return "{\"error\":\"表达式不能为空\",\"expression\":\"\"}";
        }
        try {
            // exp4j 0.4.8 API: new ExpressionBuilder(expr).build().evaluate()
            // 无变量场景下不需要 .variables(...) 调用
            Expression e = new ExpressionBuilder(expression).build();
            double result = e.evaluate();
            // 检查结果是否为整数（教学友好显示）
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return "{\"expression\":\"" + escapeJson(expression) + "\",\"result\":" + (long) result + "}";
            }
            return "{\"expression\":\"" + escapeJson(expression) + "\",\"result\":" + result + "}";
        } catch (IllegalArgumentException | ArithmeticException ex) {
            // Pitfall 4: 非法表达式包装为 JSON 错误返回，不抛异常到上层
            return "{\"error\":\"表达式非法：" + escapeJson(ex.getMessage()) + "\",\"expression\":\"" + escapeJson(expression) + "\"}";
        }
    }

    /**
     * Escape special characters in a string for safe JSON embedding.
     * Handles backslash, double quote, newline, carriage return, tab.
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}