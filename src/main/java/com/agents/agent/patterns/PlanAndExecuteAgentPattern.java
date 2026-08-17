package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.PlanEvent;
import com.agents.agent.core.PlanEvent.Step;
import com.agents.agent.core.StepCompleteEvent;
import com.agents.agent.core.StepStartEvent;
import com.agents.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan-and-Execute（计划与执行）模式实现 - 先规划再逐步执行，步骤失败可重新规划。
 *
 * <p>Planner 生成结构化 JSON 计划（步骤列表），Executor 逐步执行（子 agent 独立 LLM 调用 + 工具调用），
 * 步骤失败时触发 replan，所有步骤完成后汇总生成最终答案。
 */
@Component
public class PlanAndExecuteAgentPattern implements AgentPattern {

    private static final int MAX_REPLAN = 3;

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是一个 Plan-and-Execute Planner。你的任务是将用户问题分解为多个步骤，生成结构化计划。

            请严格按照以下 JSON 格式输出，不要包含任何额外的文字、markdown 代码块包裹或解释：
            {
              "steps": [
                {"stepNumber": 1, "description": "步骤描述", "expectedOutput": "预期产出"},
                {"stepNumber": 2, "description": "步骤描述", "expectedOutput": "预期产出"}
              ]
            }

            步骤数控制在 3-7 步。每步描述清晰、具体。预期输出描述该步骤完成后应产出的内容。
            注意：输出 ONLY 上述 JSON 对象，不要加 ```json 或任何其他标记。
            """;

    private static final String STEP_SYSTEM_PROMPT = """
            你是一个 Plan-and-Execute 步骤执行器。你的任务是执行给定的单个步骤，并输出执行结果。

            可用工具：weather, calculator, time。
            如果步骤需要工具，调用工具获取信息。工具结果仅供参考，最终输出要用你自己的话。
            输出该步骤的执行结果文本。
            """;

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是一个 Plan-and-Execute 汇总器。你的任务是根据原始问题和所有步骤的执行结果，综合生成最终答案。

            请根据原始问题，结合各步骤的产出，给出一个全面、连贯的最终答案。
            """;

    private static final String SELF_EVAL_SYSTEM_PROMPT = """
            你是一个步骤结果评估器。请判断步骤的实际执行结果是否达到了预期输出。

            如果结果符合预期，回答：yes
            如果结果不符合预期，回答：no
            先回答 yes 或 no，再给出简短解释。
            """;

    private static final String REPLAN_SYSTEM_PROMPT = """
            你是一个 Plan-and-Execute Replanner。一个步骤执行失败，你需要为剩余步骤重新生成计划。

            请根据原始问题、已完成步骤（含执行结果）和失败步骤信息，重新生成剩余步骤的计划。

            请严格按照以下 JSON 格式输出，不要包含任何额外的文字、markdown 代码块包裹或解释：
            {
              "steps": [
                {"stepNumber": 1, "description": "步骤描述", "expectedOutput": "预期产出"}
              ]
            }

            步骤号从 1 开始重新编号。新计划应覆盖失败步骤之后的所有剩余工作。
            注意：输出 ONLY 上述 JSON 对象，不要加 ```json 或任何其他标记。
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;

    public PlanAndExecuteAgentPattern(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper, ToolRegistry toolRegistry) {
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String id() {
        return "planExecute";
    }

    @Override
    public String displayName() {
        return "Plan-and-Execute 计划与执行";
    }

    @Override
    public String description() {
        return "先规划再逐步执行，步骤失败可重新规划";
    }

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            try {
                // ===== Step 1: Planner =====
                ChatClient planner = chatClientBuilder
                        .defaultSystem(PLANNER_SYSTEM_PROMPT)
                        .build();

                String planResponse = planner.prompt()
                        .user(ctx.question())
                        .call()
                        .content();

                if (planResponse == null || planResponse.isBlank()) {
                    sink.next(new ErrorEvent(Instant.now(), "Planner 未能生成计划，请重试"));
                    sink.complete();
                    return;
                }

                String cleanedPlan = extractJson(planResponse);
                JsonNode planRoot = objectMapper.readTree(cleanedPlan);
                JsonNode stepsArray = planRoot.get("steps");
                if (stepsArray == null || !stepsArray.isArray() || stepsArray.isEmpty()) {
                    sink.next(new ErrorEvent(Instant.now(), "Planner 未能生成有效步骤列表，请重试"));
                    sink.complete();
                    return;
                }

                List<Step> steps = parsePlanJson(stepsArray);
                if (steps == null || steps.isEmpty()) {
                    sink.next(new ErrorEvent(Instant.now(), "Planner 生成的计划格式无效，请重试"));
                    sink.complete();
                    return;
                }

                // Emit initial PlanEvent
                sink.next(new PlanEvent(Instant.now(), steps));

                // ===== Step 2: Executor loop =====
                List<StepResult> completedResults = new ArrayList<>();
                int replanCount = 0;
                List<ToolCallback> toolCallbacks = toolRegistry.forPattern("planExecute");
                int currentStepIndex = 0;

                while (currentStepIndex < steps.size() && replanCount < MAX_REPLAN) {
                    Step step = steps.get(currentStepIndex);

                    // Emit StepStartEvent
                    sink.next(new StepStartEvent(Instant.now(), step.stepNumber(), step.description()));

                    // Sub-agent execution
                    ChatClient stepClient = chatClientBuilder
                            .defaultSystem(STEP_SYSTEM_PROMPT)
                            .build();

                    String result;
                    try {
                        result = stepClient.prompt()
                                .user(u -> u.text("问题: " + ctx.question()
                                        + "\n步骤描述: " + step.description()
                                        + "\n预期输出: " + step.expectedOutput()))
                                .tools(toolCallbacks)
                                .call()
                                .content();
                    } catch (Exception ex) {
                        result = "";
                    }
                    if (result == null) {
                        result = "";
                    }

                    // Failure detection: exception + LLM self-evaluation
                    boolean failed;
                    if (result.isBlank()) {
                        failed = true;
                    } else {
                        ChatClient evalClient = chatClientBuilder
                                .defaultSystem(SELF_EVAL_SYSTEM_PROMPT)
                                .build();
                        String stepDescForEval = step.description();
                        String expectedForEval = step.expectedOutput();
                        String resultForEval = result;
                        String evalResponse = evalClient.prompt()
                                .user(u -> u.text("步骤: " + stepDescForEval
                                        + "\n预期输出: " + expectedForEval
                                        + "\n实际结果: " + resultForEval))
                                .call()
                                .content();
                        boolean hasNo = evalResponse != null && evalResponse.toLowerCase().contains("no");
                        boolean hasYes = evalResponse != null && evalResponse.toLowerCase().contains("yes");
                        failed = hasNo && !hasYes;
                    }

                    if (failed) {
                        // Emit StepCompleteEvent with failed status
                        sink.next(new StepCompleteEvent(Instant.now(), step.stepNumber(), "failed", result));

                        // Replan
                        String stepDescForReplan = step.description();
                        int stepNumForReplan = step.stepNumber();
                        String resultForReplan = result;
                        ChatClient replanClient = chatClientBuilder
                                .defaultSystem(REPLAN_SYSTEM_PROMPT)
                                .build();
                        String replanResponse = replanClient.prompt()
                                .user(u -> u.text("原始问题: " + ctx.question()
                                        + "\n已完成步骤: " + formatResults(completedResults)
                                        + "\n失败步骤: " + stepDescForReplan
                                        + " (步骤号 " + stepNumForReplan + ", 错误: " + resultForReplan + ")"))
                                .call()
                                .content();

                        if (replanResponse != null && !replanResponse.isBlank()) {
                            String cleanedReplan = extractJson(replanResponse);
                            JsonNode replanRoot = objectMapper.readTree(cleanedReplan);
                            JsonNode replanStepsArray = replanRoot.get("steps");
                            List<Step> newSteps = replanStepsArray != null && replanStepsArray.isArray()
                                    ? parsePlanJson(replanStepsArray) : null;
                            if (newSteps != null && !newSteps.isEmpty()) {
                                steps = newSteps;
                                currentStepIndex = 0;
                                sink.next(new PlanEvent(Instant.now(), steps));
                            }
                        }
                        replanCount++;
                    } else {
                        // Emit StepCompleteEvent with done status
                        sink.next(new StepCompleteEvent(Instant.now(), step.stepNumber(), "done", result));
                        completedResults.add(new StepResult(step, result));
                        currentStepIndex++;
                    }
                }

                // Check if replan exhausted
                if (replanCount >= MAX_REPLAN && currentStepIndex < steps.size()) {
                    sink.next(new ErrorEvent(Instant.now(),
                            "重新规划次数已达上限，请简化问题后重试。"));
                    sink.complete();
                    return;
                }

                // ===== Step 3: Summary =====
                if (!completedResults.isEmpty()) {
                    ChatClient summaryClient = chatClientBuilder
                            .defaultSystem(SUMMARY_SYSTEM_PROMPT)
                            .build();
                    String summaryResult = summaryClient.prompt()
                            .user(u -> u.text("原始问题: " + ctx.question()
                                    + "\n\n各步骤执行结果:\n" + formatResults(completedResults)))
                            .call()
                            .content();
                    if (summaryResult != null && !summaryResult.isBlank()) {
                        sink.next(new FinalAnswerEvent(Instant.now(), summaryResult));
                    } else {
                        sink.next(new ErrorEvent(Instant.now(), "所有步骤均执行失败，无法生成最终答案。请简化问题后重试。"));
                    }
                } else {
                    sink.next(new ErrorEvent(Instant.now(), "所有步骤均执行失败，无法生成最终答案。请简化问题后重试。"));
                }

                sink.complete();

            } catch (Exception ex) {
                sink.next(new ErrorEvent(Instant.now(),
                        "Plan-and-Execute 模式执行异常: " + ex.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * Parse JSON steps array into List<Step>.
     */
    private List<Step> parsePlanJson(JsonNode stepsArray) {
        List<Step> steps = new ArrayList<>();
        for (JsonNode node : stepsArray) {
            int stepNumber = node.get("stepNumber") != null ? node.get("stepNumber").asInt() : 0;
            String description = node.get("description") != null ? node.get("description").asText() : "";
            String expectedOutput = node.get("expectedOutput") != null ? node.get("expectedOutput").asText() : "";
            if (stepNumber > 0 && !description.isEmpty()) {
                steps.add(new Step(stepNumber, description, expectedOutput));
            }
        }
        return steps;
    }

    /**
     * Format completed step results as a string for prompts.
     */
    private String formatResults(List<StepResult> results) {
        StringBuilder sb = new StringBuilder();
        for (StepResult sr : results) {
            sb.append("步骤 ").append(sr.step().stepNumber())
                    .append(": ").append(sr.step().description())
                    .append("\n结果: ").append(sr.result())
                    .append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Preprocess raw LLM response: remove markdown code block markers, then extract
     * content between first '{' and last '}'.
     */
    static String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
            return cleaned;
        }
        return cleaned.substring(firstBrace, lastBrace + 1);
    }

    /**
     * Holds a completed step and its result.
     */
    private record StepResult(Step step, String result) {}
}