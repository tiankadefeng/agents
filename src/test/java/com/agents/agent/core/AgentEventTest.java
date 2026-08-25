package com.agents.agent.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentEvent} sealed interface and its 20 record subtypes.
 *
 * <p>Verifies:
 * <ul>
 *   <li>D-04: All 20 records can be instantiated and implement AgentEvent
 *       (流式改造: +ReasoningDeltaEvent, +RoleSpeechDeltaEvent)</li>
 *   <li>D-02: ts() accessor is available on all records (only common method)</li>
 *   <li>D-04: Record component accessors return values passed to constructor</li>
 * </ul>
 *
 * <p>This is a pure unit test (no Spring context) - tests only record semantics.
 */
class AgentEventTest {

    @Test
    void shouldInstantiateAllEighteenEventRecords() {
        Instant now = Instant.now();

        // D-04: 18 record subtypes, each implements AgentEvent (Phase 10: +RolePmEvent, +RoleDevEvent, +RoleTesterEvent)
        AgentEvent reasoning = new ReasoningEvent(now, "thinking...");
        AgentEvent toolCall = new ToolCallEvent(now, "weather", Map.of("city", "Beijing"));
        AgentEvent toolResult = new ToolResultEvent(now, "weather", "25°C", false);
        AgentEvent subQuestion = new SubQuestionEvent(now, "What is the capital?");
        AgentEvent subAnswer = new SubAnswerEvent(now, "What is the capital?", "Beijing");
        AgentEvent plan = new PlanEvent(now, List.of(new PlanEvent.Step(1, "Step 1: search", "search result")));
        AgentEvent stepStart = new StepStartEvent(now, 1, "searching");
        AgentEvent stepComplete = new StepCompleteEvent(now, 1, "done", "result text");
        AgentEvent totNode = new TotNodeEvent(now, 0, 1, "分支思考内容", 8, null);
        AgentEvent totPrune = new TotPruneEvent(now, 0, List.of(2, 3), "评分低于 top-K=2 阈值");
        AgentEvent reflexionAttempt = new ReflexionAttemptEvent(now, 1, "attempt answer");
        AgentEvent reflexionEvaluate = new ReflexionEvaluateEvent(now, 1, 8, "good answer");
        AgentEvent reflexionReflect = new ReflexionReflectEvent(now, 1, "improve clarity");
        AgentEvent rolePm = new RolePmEvent(now, 1, "PM", "pm content");
        AgentEvent roleDev = new RoleDevEvent(now, 1, "Dev", "dev content");
        AgentEvent roleTester = new RoleTesterEvent(now, 1, "Tester", "tester content");
        AgentEvent reasoningDelta = new ReasoningDeltaEvent(now, "thought delta");
        AgentEvent roleSpeechDelta = new RoleSpeechDeltaEvent(now, 1, "PM", "speech delta");
        AgentEvent finalAnswer = new FinalAnswerEvent(now, "The answer is 42");
        AgentEvent error = new ErrorEvent(now, "Something went wrong");

        // All 20 instances are AgentEvent subtypes
        assertThat(reasoning).isInstanceOf(AgentEvent.class);
        assertThat(toolCall).isInstanceOf(AgentEvent.class);
        assertThat(toolResult).isInstanceOf(AgentEvent.class);
        assertThat(subQuestion).isInstanceOf(AgentEvent.class);
        assertThat(subAnswer).isInstanceOf(AgentEvent.class);
        assertThat(plan).isInstanceOf(AgentEvent.class);
        assertThat(stepStart).isInstanceOf(AgentEvent.class);
        assertThat(stepComplete).isInstanceOf(AgentEvent.class);
        assertThat(totNode).isInstanceOf(AgentEvent.class);
        assertThat(totPrune).isInstanceOf(AgentEvent.class);
        assertThat(reflexionAttempt).isInstanceOf(AgentEvent.class);
        assertThat(reflexionEvaluate).isInstanceOf(AgentEvent.class);
        assertThat(reflexionReflect).isInstanceOf(AgentEvent.class);
        assertThat(rolePm).isInstanceOf(AgentEvent.class);
        assertThat(roleDev).isInstanceOf(AgentEvent.class);
        assertThat(roleTester).isInstanceOf(AgentEvent.class);
        assertThat(reasoningDelta).isInstanceOf(AgentEvent.class);
        assertThat(roleSpeechDelta).isInstanceOf(AgentEvent.class);
        assertThat(finalAnswer).isInstanceOf(AgentEvent.class);
        assertThat(error).isInstanceOf(AgentEvent.class);

        // D-02: ts() is the only common method, returns non-null for all records
        assertThat(reasoning.ts()).isNotNull();
        assertThat(toolCall.ts()).isNotNull();
        assertThat(toolResult.ts()).isNotNull();
        assertThat(subQuestion.ts()).isNotNull();
        assertThat(subAnswer.ts()).isNotNull();
        assertThat(plan.ts()).isNotNull();
        assertThat(stepStart.ts()).isNotNull();
        assertThat(stepComplete.ts()).isNotNull();
        assertThat(totNode.ts()).isNotNull();
        assertThat(totPrune.ts()).isNotNull();
        assertThat(reflexionAttempt.ts()).isNotNull();
        assertThat(reflexionEvaluate.ts()).isNotNull();
        assertThat(reflexionReflect.ts()).isNotNull();
        assertThat(rolePm.ts()).isNotNull();
        assertThat(roleDev.ts()).isNotNull();
        assertThat(roleTester.ts()).isNotNull();
        assertThat(reasoningDelta.ts()).isNotNull();
        assertThat(roleSpeechDelta.ts()).isNotNull();
        assertThat(finalAnswer.ts()).isNotNull();
        assertThat(error.ts()).isNotNull();

        // ts() returns the exact Instant passed to constructor
        assertThat(reasoning.ts()).isEqualTo(now);
        assertThat(subAnswer.ts()).isEqualTo(now);
        assertThat(totNode.ts()).isEqualTo(now);
        assertThat(totPrune.ts()).isEqualTo(now);
        assertThat(reflexionAttempt.ts()).isEqualTo(now);
        assertThat(reflexionEvaluate.ts()).isEqualTo(now);
        assertThat(reflexionReflect.ts()).isEqualTo(now);
        assertThat(rolePm.ts()).isEqualTo(now);
        assertThat(roleDev.ts()).isEqualTo(now);
        assertThat(roleTester.ts()).isEqualTo(now);
        assertThat(reasoningDelta.ts()).isEqualTo(now);
        assertThat(roleSpeechDelta.ts()).isEqualTo(now);
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

        // SubAnswerEvent: ts + question + answer
        SubAnswerEvent subAnswer = new SubAnswerEvent(now, "sub-question?", "sub-answer");
        assertThat(subAnswer.question()).isEqualTo("sub-question?");
        assertThat(subAnswer.answer()).isEqualTo("sub-answer");

        // PlanEvent: ts + steps (Phase 7: List<Step>)
        PlanEvent plan = new PlanEvent(now, List.of(
            new PlanEvent.Step(1, "step 1", "output 1"),
            new PlanEvent.Step(2, "step 2", "output 2")
        ));
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).stepNumber()).isEqualTo(1);
        assertThat(plan.steps().get(0).description()).isEqualTo("step 1");
        assertThat(plan.steps().get(0).expectedOutput()).isEqualTo("output 1");

        // StepStartEvent: ts + stepNumber + description
        StepStartEvent stepStart = new StepStartEvent(now, 3, "executing step 3");
        assertThat(stepStart.stepNumber()).isEqualTo(3);
        assertThat(stepStart.description()).isEqualTo("executing step 3");

        // StepCompleteEvent: ts + stepNumber + status + result (Phase 7: expanded)
        StepCompleteEvent stepComplete = new StepCompleteEvent(now, 3, "done", "step result");
        assertThat(stepComplete.stepNumber()).isEqualTo(3);
        assertThat(stepComplete.status()).isEqualTo("done");
        assertThat(stepComplete.result()).isEqualTo("step result");

        // FinalAnswerEvent: ts + content
        FinalAnswerEvent finalAnswer = new FinalAnswerEvent(now, "final answer");
        assertThat(finalAnswer.content()).isEqualTo("final answer");

        // ErrorEvent: ts + message (no stacktrace - T-2-02)
        ErrorEvent error = new ErrorEvent(now, "error message");
        assertThat(error.message()).isEqualTo("error message");

        // TotNodeEvent: ts + level + nodeId + thought + score + parentId (Phase 8)
        TotNodeEvent totNode = new TotNodeEvent(now, 1, 4, "先尝试乘法组合", 9, 2);
        assertThat(totNode.level()).isEqualTo(1);
        assertThat(totNode.nodeId()).isEqualTo(4);
        assertThat(totNode.thought()).isEqualTo("先尝试乘法组合");
        assertThat(totNode.score()).isEqualTo(9);
        assertThat(totNode.parentId()).isEqualTo(2);

        // TotPruneEvent: ts + level + prunedNodeIds + reason (Phase 8)
        TotPruneEvent totPrune = new TotPruneEvent(now, 1, List.of(5, 9), "评分低于阈值");
        assertThat(totPrune.level()).isEqualTo(1);
        assertThat(totPrune.prunedNodeIds()).containsExactly(5, 9);
        assertThat(totPrune.reason()).isEqualTo("评分低于阈值");

        // ReflexionAttemptEvent: ts + round + answer (Phase 9)
        ReflexionAttemptEvent reflexionAttempt = new ReflexionAttemptEvent(now, 1, "attempt answer");
        assertThat(reflexionAttempt.round()).isEqualTo(1);
        assertThat(reflexionAttempt.answer()).isEqualTo("attempt answer");

        // ReflexionEvaluateEvent: ts + round + score + feedback (Phase 9)
        ReflexionEvaluateEvent reflexionEvaluate = new ReflexionEvaluateEvent(now, 1, 8, "good");
        assertThat(reflexionEvaluate.round()).isEqualTo(1);
        assertThat(reflexionEvaluate.score()).isEqualTo(8);
        assertThat(reflexionEvaluate.feedback()).isEqualTo("good");

        // ReflexionReflectEvent: ts + round + reflection (Phase 9)
        ReflexionReflectEvent reflexionReflect = new ReflexionReflectEvent(now, 1, "improve");
        assertThat(reflexionReflect.round()).isEqualTo(1);
        assertThat(reflexionReflect.reflection()).isEqualTo("improve");

        // RolePmEvent: ts + round + role + content (Phase 10)
        RolePmEvent rolePm = new RolePmEvent(now, 1, "PM", "需求分析");
        assertThat(rolePm.round()).isEqualTo(1);
        assertThat(rolePm.role()).isEqualTo("PM");
        assertThat(rolePm.content()).isEqualTo("需求分析");

        // RoleDevEvent: ts + round + role + content (Phase 10)
        RoleDevEvent roleDev = new RoleDevEvent(now, 1, "Dev", "设计方案");
        assertThat(roleDev.round()).isEqualTo(1);
        assertThat(roleDev.role()).isEqualTo("Dev");
        assertThat(roleDev.content()).isEqualTo("设计方案");

        // RoleTesterEvent: ts + round + role + content (Phase 10)
        RoleTesterEvent roleTester = new RoleTesterEvent(now, 1, "Tester", "测试方案");
        assertThat(roleTester.round()).isEqualTo(1);
        assertThat(roleTester.role()).isEqualTo("Tester");
        assertThat(roleTester.content()).isEqualTo("测试方案");

        // 根节点 parentId 为 null
        TotNodeEvent root = new TotNodeEvent(now, -1, 0, "原始问题", 0, null);
        assertThat(root.parentId()).isNull();
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
            new SubAnswerEvent(now, "q", "a"),
            new PlanEvent(now, List.of(new PlanEvent.Step(1, "d", "o"))),
            new StepStartEvent(now, 1, "d"),
            new StepCompleteEvent(now, 1, "s", "r"),
            new TotNodeEvent(now, 0, 1, "b", 8, null),
            new TotPruneEvent(now, 0, List.of(2), "low score"),
            new ReflexionAttemptEvent(now, 1, "attempt answer"),
            new ReflexionEvaluateEvent(now, 1, 8, "good"),
            new ReflexionReflectEvent(now, 1, "improve"),
            new RolePmEvent(now, 1, "PM", "pm content"),
            new RoleDevEvent(now, 1, "Dev", "dev content"),
            new RoleTesterEvent(now, 1, "Tester", "tester content"),
            new ReasoningDeltaEvent(now, "thought delta"),
            new RoleSpeechDeltaEvent(now, 1, "PM", "speech delta"),
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
            case SubAnswerEvent s -> "sub-answer:" + s.question() + " -> " + s.answer();
            case PlanEvent p -> "plan:" + p.steps().size() + " steps";
            case StepStartEvent s -> "step-start:" + s.stepNumber();
            case StepCompleteEvent s -> "step-complete:" + s.stepNumber() + ":" + s.status();
            case TotNodeEvent t -> "tot-node:" + t.nodeId() + ":" + t.score();
            case TotPruneEvent t -> "tot-prune:" + t.prunedNodeIds().size();
            case ReflexionAttemptEvent r -> "reflexion-attempt:" + r.round() + ":" + r.answer();
            case ReflexionEvaluateEvent r -> "reflexion-evaluate:" + r.round() + ":" + r.score();
            case ReflexionReflectEvent r -> "reflexion-reflect:" + r.round() + ":" + r.reflection();
            case RolePmEvent r -> "role-pm:" + r.round() + ":" + r.content();
            case RoleDevEvent r -> "role-dev:" + r.round() + ":" + r.content();
            case RoleTesterEvent r -> "role-tester:" + r.round() + ":" + r.content();
            case ReasoningDeltaEvent r -> "reasoning-delta:" + r.content();
            case RoleSpeechDeltaEvent r -> "role-speech-delta:" + r.round() + ":" + r.role() + ":" + r.content();
            case FinalAnswerEvent f -> "final:" + f.content();
            case ErrorEvent e -> "error:" + e.message();
        };
    }
}
