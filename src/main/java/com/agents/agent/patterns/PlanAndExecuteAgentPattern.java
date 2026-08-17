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

    private static final String PLANNER_SYSTEM_PROMPT =
            "你是一个 Plan-and-Execute Planner。你的任务是将用户问题分解为多个步骤，生成结构化计划。\n\n"
            + "请严格按照以下 JSON 格式输出，不要包含任何额外的文字、markdown 代码块包裹或解释：\n"
            + "{\n"
            + "  \"steps\": [\n"
            + "    {\"stepNumber\": 1, \"description\": \"步骤描述\", \"expectedOutput\": \"预期产出\"},\n"
            + "    {\"stepNumber\": 2, \"description\": \"步骤描述\", \"expectedOutput\": \"预期产出\"}\n"
            + "  ]\n"
            + "}\n\n"
            + "步骤数控制在 3-7 步。每步描述清晰、具体。预期输出描述该步骤完成后应产出的内容。\n"
            + "注意：输出 ONLY 上述 JSON 对象，不要加 ```json 或任何其他标记。";

    private static final String STEP_SYSTEM_PROMPT =
            "你是一个 Plan-and-Execute 步骤执行器。你的任务是执行给定的单个步骤，并输出执行结果。\n\n"
            + "可用工具：weather, calculator, time。\n"
            + "如果步骤需要工具，调用工具获取信息。工具结果仅供参考，最终输出要用你自己的话。\n"
            + "输出该步骤的执行结果文本。";

    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是一个 Plan-and-Execute 汇总器。你的任务是根据原始问题和所有步骤的执行结果，综合生成最终答案。\n\n"
            + "请根据原始问题，结合各步骤的产出，给出一个全面、连贯的最终答案。";

    private static final String SELF_EVAL_SYSTEM_PROMPT =
            "你是一个步骤结果评估器。请判断步骤的实际执行结果是否达到了预期输出。\n\n"
            + "如果结果符合预期，回答：yes\n"
            + "如果结果不符合预期，回答：no\n"
            + "先回答 yes 或 no，再给出简短解释。";

    private static final String REPLAN_SYSTEM_PROMPT =
            "你是一个 Plan-and-Execute Replanner。一个步骤执行失败，你需要为剩余步骤重新生成计划。\n\n"
            + "请根据原始问题、已完成步骤（含执行结果）和失败步骤信息，重新生成剩余步骤的计划。\n\n"
            + "请严格按照以下 JSON 格式输出，不要包含任何额外的文字、markdown 代码块包裹或解释：\n"
            + "{\n"
            + "  \"steps\": [\n"
            + "    {\"stepNumber\": 1, \"description\": \"步骤描述\", \"expectedOutput\": \"预期产出\"}\n"
            + "  ]\n"
            + "}\n\n"
            + "步骤号从 1 开始重新编号。新计划应覆盖失败步骤之后的所有剩余工作。\n"
            + "注意：输出 ONLY 上述 JSON 对象，不要加 ```json 或任何其他标记。";

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

    private static String extractJson(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first == -1 || last == -1 || last < first) return cleaned;
        return cleaned.substring(first, last + 1);
    }

    private List<Step> parseSteps(JsonNode arr) {
        List<Step> list = new ArrayList<>();
        for (JsonNode n : arr) {
            int sn = n.get("stepNumber") != null ? n.get("stepNumber").asInt() : 0;
            String d = n.get("description") != null ? n.get("description").asText() : "";
            String eo = n.get("expectedOutput") != null ? n.get("expectedOutput").asText() : "";
            if (sn > 0 && !d.isEmpty()) list.add(new Step(sn, d, eo));
        }
        return list;
    }

    private String fmt(List<StepResult> rs) {
        StringBuilder sb = new StringBuilder();
        for (StepResult sr : rs) {
            sb.append("步骤 ").append(sr.step.stepNumber())
              .append(": ").append(sr.step.description())
              .append("\n结果: ").append(sr.result).append("\n\n");
        }
        return sb.toString();
    }

    private record StepResult(Step step, String result) {}

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            try {
                // ===== Planner =====
                ChatClient planner = chatClientBuilder.defaultSystem(PLANNER_SYSTEM_PROMPT).build();
                String planRaw = planner.prompt().user(ctx.question()).call().content();
                if (planRaw == null || planRaw.isBlank()) {
                    sink.next(new ErrorEvent(Instant.now(), "Planner 未能生成计划，请重试"));
                    sink.complete(); return;
                }
                JsonNode planRoot = objectMapper.readTree(extractJson(planRaw));
                JsonNode arr = planRoot.get("steps");
                if (arr == null || !arr.isArray() || arr.isEmpty()) {
                    sink.next(new ErrorEvent(Instant.now(), "Planner 未能生成有效步骤列表，请重试"));
                    sink.complete(); return;
                }
                List<Step> steps = parseSteps(arr);
                if (steps.isEmpty()) {
                    sink.next(new ErrorEvent(Instant.now(), "Planner 生成的计划格式无效，请重试"));
                    sink.complete(); return;
                }
                sink.next(new PlanEvent(Instant.now(), steps));

                // ===== Executor loop =====
                List<StepResult> completed = new ArrayList<>();
                int replanCount = 0;
                List<ToolCallback> tools = toolRegistry.forPattern("planExecute");
                int idx = 0;

                while (idx < steps.size() && replanCount < MAX_REPLAN) {
                    Step step = steps.get(idx);
                    sink.next(new StepStartEvent(Instant.now(), step.stepNumber(), step.description()));

                    String result;
                    try {
                        result = chatClientBuilder.defaultSystem(STEP_SYSTEM_PROMPT).build().prompt()
                            .user(u -> u.text("问题: " + ctx.question()
                                + "\n步骤描述: " + step.description()
                                + "\n预期输出: " + step.expectedOutput()))
                            .tools(tools).call().content();
                    } catch (Exception e) { result = ""; }
                    if (result == null) result = "";

                    // Failure detection: blank result or LLM self-eval says "no"
                    boolean failed = result.isBlank();
                    if (!failed) {
                        String evalDesc = step.description();
                        String evalExp = step.expectedOutput();
                        String evalR = result;
                        String eval = chatClientBuilder.defaultSystem(SELF_EVAL_SYSTEM_PROMPT).build().prompt()
                            .user(u -> u.text("步骤: " + evalDesc + "\n预期输出: " + evalExp + "\n实际结果: " + evalR))
                            .call().content();
                        boolean hasNo = eval != null && eval.toLowerCase().contains("no");
                        boolean hasYes = eval != null && eval.toLowerCase().contains("yes");
                        failed = hasNo && !hasYes;
                    }

                    if (failed) {
                        sink.next(new StepCompleteEvent(Instant.now(), step.stepNumber(), "failed", result));
                        // Replan
                        String replanDesc = step.description();
                        int replanNum = step.stepNumber();
                        String replanR = result;
                        String replanStr = chatClientBuilder.defaultSystem(REPLAN_SYSTEM_PROMPT).build().prompt()
                            .user(u -> u.text("原始问题: " + ctx.question()
                                + "\n已完成步骤: " + fmt(completed)
                                + "\n失败步骤: " + replanDesc + " (步骤号 " + replanNum + ", 错误: " + replanR + ")"))
                            .call().content();
                        if (replanStr != null && !replanStr.isBlank()) {
                            JsonNode replanRoot = objectMapper.readTree(extractJson(replanStr));
                            JsonNode replanArr = replanRoot.get("steps");
                            List<Step> newSteps = replanArr != null && replanArr.isArray() ? parseSteps(replanArr) : null;
                            if (newSteps != null && !newSteps.isEmpty()) {
                                steps = newSteps;
                                idx = 0;
                                sink.next(new PlanEvent(Instant.now(), steps));
                            }
                        }
                        replanCount++;
                    } else {
                        sink.next(new StepCompleteEvent(Instant.now(), step.stepNumber(), "done", result));
                        completed.add(new StepResult(step, result));
                        idx++;
                    }
                }

                if (replanCount >= MAX_REPLAN && idx < steps.size()) {
                    sink.next(new ErrorEvent(Instant.now(), "重新规划次数已达上限，请简化问题后重试。"));
                    sink.complete(); return;
                }

                // ===== Summary =====
                if (!completed.isEmpty()) {
                    String summary = chatClientBuilder.defaultSystem(SUMMARY_SYSTEM_PROMPT).build().prompt()
                        .user(u -> u.text("原始问题: " + ctx.question() + "\n\n各步骤执行结果:\n" + fmt(completed)))
                        .call().content();
                    if (summary != null && !summary.isBlank()) {
                        sink.next(new FinalAnswerEvent(Instant.now(), summary));
                    } else {
                        sink.next(new ErrorEvent(Instant.now(), "所有步骤均执行失败，无法生成最终答案。请简化问题后重试。"));
                    }
                } else {
                    sink.next(new ErrorEvent(Instant.now(), "所有步骤均执行失败，无法生成最终答案。请简化问题后重试。"));
                }
                sink.complete();

            } catch (Exception ex) {
                sink.next(new ErrorEvent(Instant.now(), "Plan-and-Execute 模式执行异常: " + ex.getMessage()));
                sink.complete();
            }
        });
    }
}