package com.agents.agent.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentEvent} sealed interface and its 9 record subtypes.
 *
 * <p>Verifies:
 * <ul>
 *   <li>D-04: All 9 records can be instantiated and implement AgentEvent</li>
 *   <li>D-02: ts() accessor is available on all records (only common method)</li>
 *   <li>D-04: Record component accessors return values passed to constructor</li>
 * </ul>
 *
 * <p>This is a pure unit test (no Spring context) - tests only record semantics.
 */
class AgentEventTest {

    @Test
    void shouldInstantiateAllNineEventRecords() {
        Instant now = Instant.now();

        // D-04: 9 record subtypes, each implements AgentEvent
        AgentEvent reasoning = new ReasoningEvent(now, "thinking...");
        AgentEvent toolCall = new ToolCallEvent(now, "weather", Map.of("city", "Beijing"));
        AgentEvent toolResult = new ToolResultEvent(now, "weather", "25°C", false);
        AgentEvent subQuestion = new SubQuestionEvent(now, "What is the capital?");
        AgentEvent plan = new PlanEvent(now, "Step 1: search; Step 2: summarize");
        AgentEvent stepStart = new StepStartEvent(now, 1, "searching");
        AgentEvent stepComplete = new StepCompleteEvent(now, 1, "success");
        AgentEvent finalAnswer = new FinalAnswerEvent(now, "The answer is 42");
        AgentEvent error = new ErrorEvent(now, "Something went wrong");

        // All 9 instances are AgentEvent subtypes
        assertThat(reasoning).isInstanceOf(AgentEvent.class);
        assertThat(toolCall).isInstanceOf(AgentEvent.class);
        assertThat(toolResult).isInstanceOf(AgentEvent.class);
        assertThat(subQuestion).isInstanceOf(AgentEvent.class);
        assertThat(plan).isInstanceOf(AgentEvent.class);
        assertThat(stepStart).isInstanceOf(AgentEvent.class);
        assertThat(stepComplete).isInstanceOf(AgentEvent.class);
        assertThat(finalAnswer).isInstanceOf(AgentEvent.class);
        assertThat(error).isInstanceOf(AgentEvent.class);

        // D-02: ts() is the only common method, returns non-null for all records
        assertThat(reasoning.ts()).isNotNull();
        assertThat(toolCall.ts()).isNotNull();
        assertThat(toolResult.ts()).isNotNull();
        assertThat(subQuestion.ts()).isNotNull();
        assertThat(plan.ts()).isNotNull();
        assertThat(stepStart.ts()).isNotNull();
        assertThat(stepComplete.ts()).isNotNull();
        assertThat(finalAnswer.ts()).isNotNull();
        assertThat(error.ts()).isNotNull();

        // ts() returns the exact Instant passed to constructor
        assertThat(reasoning.ts()).isEqualTo(now);
        assertThat(error.ts()).isEqualTo(now);
    }

    @Test
    void shouldAccessRecordComponentsViaAccessors() {
        Instant now = Instant.now();

        // ReasoningEvent: ts + content
        ReasoningEvent reasoning = new ReasoningEvent(now, "step by step");
        assertThat(reasoning.content()).isEqualTo("step by step");

        // ToolCallEvent: ts + toolName + arguments
        ToolCallEvent toolCall = new ToolCallEvent(
            now,
            "calculator",
            Map.of("expression", "1+1")
        );
        assertThat(toolCall.toolName()).isEqualTo("calculator");
        assertThat(toolCall.arguments()).containsEntry("expression", "1+1");

        // ToolResultEvent: ts + toolName + result + isError
        ToolResultEvent toolResult = new ToolResultEvent(now, "calculator", "2", false);
        assertThat(toolResult.toolName()).isEqualTo("calculator");
        assertThat(toolResult.result()).isEqualTo("2");
        assertThat(toolResult.isError()).isFalse();

        // SubQuestionEvent: ts + question
        SubQuestionEvent subQuestion = new SubQuestionEvent(now, "sub-question?");
        assertThat(subQuestion.question()).isEqualTo("sub-question?");

        // PlanEvent: ts + description (placeholder - Phase 7 will expand)
        PlanEvent plan = new PlanEvent(now, "plan description");
        assertThat(plan.description()).isEqualTo("plan description");

        // StepStartEvent: ts + stepNumber + description
        StepStartEvent stepStart = new StepStartEvent(now, 3, "executing step 3");
        assertThat(stepStart.stepNumber()).isEqualTo(3);
        assertThat(stepStart.description()).isEqualTo("executing step 3");

        // StepCompleteEvent: ts + stepNumber + status (placeholder - Phase 7 may expand)
        StepCompleteEvent stepComplete = new StepCompleteEvent(now, 3, "success");
        assertThat(stepComplete.stepNumber()).isEqualTo(3);
        assertThat(stepComplete.status()).isEqualTo("success");

        // FinalAnswerEvent: ts + content
        FinalAnswerEvent finalAnswer = new FinalAnswerEvent(now, "final answer");
        assertThat(finalAnswer.content()).isEqualTo("final answer");

        // ErrorEvent: ts + message (no stacktrace - T-2-02)
        ErrorEvent error = new ErrorEvent(now, "error message");
        assertThat(error.message()).isEqualTo("error message");
    }

    @Test
    void shouldVerifySealedInterfaceExhaustivenessInPatternSwitch() {
        // This test verifies that pattern matching switch on AgentEvent is exhaustive
        // at compile time (sealed interface guarantee). If a new record is added to
        // the permits clause without updating this switch, compilation fails.

        Instant now = Instant.now();
        AgentEvent[] events = {
            new ReasoningEvent(now, "r"),
            new ToolCallEvent(now, "t", Map.of()),
            new ToolResultEvent(now, "t", "r", false),
            new SubQuestionEvent(now, "q"),
            new PlanEvent(now, "d"),
            new StepStartEvent(now, 1, "d"),
            new StepCompleteEvent(now, 1, "s"),
            new FinalAnswerEvent(now, "a"),
            new ErrorEvent(now, "e"),
        };

        for (AgentEvent ev : events) {
            String label = describeEvent(ev);
            assertThat(label).isNotBlank();
        }
    }

    /**
     * Helper that uses pattern matching switch on AgentEvent.
     * The switch is exhaustive due to sealed interface - no default case needed.
     * If a new record subtype is added to the permits clause, this method won't
     * compile until the new case is added, enforcing exhaustiveness.
     */
    private String describeEvent(AgentEvent ev) {
        return switch (ev) {
            case ReasoningEvent r -> "reasoning:" + r.content();
            case ToolCallEvent t -> "tool-call:" + t.toolName();
            case ToolResultEvent t -> "tool-result:" + t.toolName();
            case SubQuestionEvent s -> "sub-question:" + s.question();
            case PlanEvent p -> "plan:" + p.description();
            case StepStartEvent s -> "step-start:" + s.stepNumber();
            case StepCompleteEvent s -> "step-complete:" + s.stepNumber();
            case FinalAnswerEvent f -> "final:" + f.content();
            case ErrorEvent e -> "error:" + e.message();
        };
    }
}
