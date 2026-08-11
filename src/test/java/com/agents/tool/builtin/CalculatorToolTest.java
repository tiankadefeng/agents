package com.agents.tool.builtin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link CalculatorTool}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>D-04: exp4j real calculation (2+3=5, sqrt(16)=4)</li>
 *   <li>Pitfall 4 (04-RESEARCH.md): invalid expressions return error JSON (not exception)</li>
 *   <li>D-07: returns JSON string with expression/result fields</li>
 * </ul>
 *
 * <p>Pure unit test (no Spring context) - direct instantiation of {@link CalculatorTool}.
 */
class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();

    @Test
    void shouldEvaluateSimpleArithmetic() {
        String result = tool.calculate("2+3");
        assertThat(result).contains("\"result\":5");
    }

    @Test
    void shouldEvaluateSqrt() {
        String result = tool.calculate("sqrt(16)");
        assertThat(result).contains("\"result\":4");
    }

    @Test
    void shouldReturnErrorForInvalidExpression() {
        // Pitfall 4 verification: must NOT throw exception
        // Use an expression that exp4j genuinely rejects (unclosed parenthesis)
        assertThatNoException().isThrownBy(() -> {
            String result = tool.calculate("(2+3");
            assertThat(result).contains("\"error\"");
        });
    }
}