package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.ReflexionAttemptEvent;
import com.agents.agent.core.ReflexionEvaluateEvent;
import com.agents.agent.core.ReflexionReflectEvent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reflexion（反思迭代）模式实现 - Generator + Evaluator + Reflector 循环。
 *
 * <p>Generator 生成初始答案，Evaluator (LLM-as-judge) 独立评估并打分 1-10 + 反馈，
 * Reflector 在评估不通过时生成反思改进方向，下一轮 Generator 据此改进答案。
 * 循环最多 2 轮（{@link #MAX_REFLECTIONS}），改进幅度不足时 epsilon 提前停止。
 * 所有事件在 {@link Flux#create} 内同步顺序发射。
 *
 * <p>D-01: 仅注入 {@link ChatClient.Builder} + {@link ObjectMapper}，不注入 ToolRegistry
 * （Reflexion 为纯 LLM 推理，不需要外部工具）。
 */
@Component
public class ReflexionAgentPattern implements AgentPattern {

    // ===== Constants =====

    /** 最大反思轮次（硬上限，per D-04） */
    private static final int MAX_REFLECTIONS = 2;

    /** 评估通过阈值（>=8 即通过，per UI-SPEC 评分语义三元组） */
    private static final int PASS_THRESHOLD = 8;

    /** 改进幅度阈值（epsilon 提前停止，per D-04 Claude's Discretion） */
    private static final int EPSILON = 1;

    /** 评分解析失败兜底中位分 */
    private static final int DEFAULT_SCORE = 5;

    // ===== System Prompts =====

    private static final String GENERATOR_PROMPT =
            "你是一个 Reflexion Agent 的 Generator。请回答以下问题，尽可能给出完整、准确的答案。\n\n问题: {question}";

    private static final String GENERATOR_WITH_REFLECTION_PROMPT =
            "你是一个 Reflexion Agent 的 Generator。请回答以下问题。\n\n问题: {question}\n\n"
            + "你之前的回答以及评估反馈如下：\n"
            + "上一轮答案: {prevAnswer}\n"
            + "评估分数: {prevScore}/10\n"
            + "评估反馈: {prevFeedback}\n"
            + "反思改进方向: {reflection}\n\n"
            + "请基于以上反馈，改进你的答案，给出更准确、更完整的回答。";

    private static final String EVALUATOR_PROMPT =
            "你是一个 Reflexion Agent 的 Evaluator（LLM-as-judge）。\n\n"
            + "请评估以下答案的质量。\n\n问题: {question}\n答案: {answer}\n\n"
            + "请从完整性、准确性、逻辑性三个维度评估，输出一个 1-10 的整数评分和反馈意见。\n\n"
            + "请严格按照以下 JSON 格式输出，不要包含任何额外的文字或 markdown 包裹：\n"
            + "{\n  \"score\": <1-10 整数>,\n  \"feedback\": \"<详细的反馈意见，指出优点和不足>\"\n}\n\n"
            + "输出 ONLY 上述 JSON 对象。";

    private static final String REFLECTOR_PROMPT =
            "你是一个 Reflexion Agent 的 Reflector。\n\n"
            + "请根据评估反馈，生成具体的反思和改进方向。\n\n"
            + "问题: {question}\n"
            + "当前答案: {answer}\n"
            + "评估分数: {score}/10\n"
            + "评估反馈: {feedback}\n\n"
            + "请输出 2-3 条具体的、可操作的改进方向。\n"
            + "每条改进方向应该明确指出\"下一轮应该具体改变什么\"，而不是笼统地承认错误。\n\n"
            + "请严格按照以下 JSON 格式输出：\n"
            + "{\n  \"reflection\": \"<反思内容，包含具体的改进方向...>\"\n}";

    // ===== Injected Dependencies =====

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public ReflexionAgentPattern(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
    }

    // ===== AgentPattern Contract =====

    @Override
    public String id() {
        return "reflexion";
    }

    @Override
    public String displayName() {
        return "Reflexion 反思迭代";
    }

    @Override
    public String description() {
        return "犯错后自我纠错";
    }

    // ===== Internal Data Structure =====

    private record RoundResult(int round, String answer, int score, String feedback, String reflection) {}

    // ===== JSON Extraction =====

    private static String extractJson(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first == -1 || last == -1 || last < first) return cleaned;
        return cleaned.substring(first, last + 1);
    }

    // ===== Score Parsing (per threat model T-09-02, never throws) =====

    private int parseScoreSafely(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_SCORE;
        try {
            // 策略 1: JSON 提取（Evaluator prompt 要求 JSON 输出）
            String json = extractJson(raw);
            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode scoreNode = root.get("score");
                if (scoreNode != null && scoreNode.isInt()) {
                    return clamp(scoreNode.asInt());
                }
            }

            String cleaned = raw.trim().toLowerCase();

            // 策略 2: 匹配 "评分: N" 或 "评分：N"
            Matcher m1 = Pattern.compile("评分[:：]\\s*(\\d+)").matcher(cleaned);
            if (m1.find()) return clamp(Integer.parseInt(m1.group(1)));

            // 策略 3: 匹配 "N/10"
            Matcher m2 = Pattern.compile("\\b(\\d+)\\s*/\\s*10\\b").matcher(cleaned);
            if (m2.find()) return clamp(Integer.parseInt(m2.group(1)));

            // 策略 4: 提取第一个独立数字 1-10
            Matcher m3 = Pattern.compile("\\b([1-9]|10)\\b").matcher(cleaned);
            if (m3.find()) return clamp(Integer.parseInt(m3.group(1)));

            // 策略 5: 关键词匹配
            if (cleaned.contains("excellent") || cleaned.contains("优秀")) return 9;
            if (cleaned.contains("good") || cleaned.contains("好")) return 7;
            if (cleaned.contains("average") || cleaned.contains("一般")) return 5;
            if (cleaned.contains("poor") || cleaned.contains("差")) return 3;
        } catch (Exception e) {
            // ignore, fallback to DEFAULT_SCORE
        }
        return DEFAULT_SCORE;
    }

    private static int clamp(int score) {
        return Math.max(1, Math.min(10, score));
    }

    // ===== Feedback Extraction (per T-09-01, never throws) =====

    private String extractFeedback(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            String json = extractJson(raw);
            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode feedback = root.get("feedback");
                if (feedback != null) return feedback.asText();
            }
        } catch (Exception e) {
            // ignore, fallback below
        }
        return raw.trim();
    }

    // ===== Reflection Extraction (per T-09-03, never throws) =====

    private String extractReflection(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            String json = extractJson(raw);
            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode reflection = root.get("reflection");
                if (reflection != null) return reflection.asText();
            }
        } catch (Exception e) {
            // ignore, fallback below
        }
        return raw.trim();
    }

    // ===== Best-of-N Answer Selection =====

    private String selectBestAnswer(List<RoundResult> rounds) {
        return rounds.stream()
                .max(Comparator.comparingInt(RoundResult::score))
                .map(RoundResult::answer)
                .orElse(rounds.get(rounds.size() - 1).answer()); // fallback to last
    }

    // ===== Execute =====

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            try {
                List<RoundResult> rounds = new ArrayList<>();
                int prevScore = 0;

                // ===== Round 1: Generator =====
                String answer1 = chatClientBuilder
                        .defaultSystem(GENERATOR_PROMPT.replace("{question}", ctx.question()))
                        .build()
                        .prompt()
                        .user(u -> u.text(ctx.question()))
                        .call()
                        .content();

                String safeAnswer1 = answer1 != null ? answer1 : "";
                sink.next(new ReflexionAttemptEvent(Instant.now(), 1, safeAnswer1));

                // ===== Round 1: Evaluator =====
                String evalRaw1 = chatClientBuilder
                        .defaultSystem(EVALUATOR_PROMPT
                                .replace("{question}", ctx.question())
                                .replace("{answer}", safeAnswer1))
                        .build()
                        .prompt()
                        .user(u -> u.text("问题: " + ctx.question() + "\n答案: " + safeAnswer1))
                        .call()
                        .content();

                int score1 = parseScoreSafely(evalRaw1);
                String feedback1 = extractFeedback(evalRaw1);
                sink.next(new ReflexionEvaluateEvent(Instant.now(), 1, score1, feedback1));
                prevScore = score1;

                String reflection1 = "";

                // ===== Check if Round 1 passes =====
                if (score1 >= PASS_THRESHOLD) {
                    rounds.add(new RoundResult(1, safeAnswer1, score1, feedback1, ""));
                    String bestAnswer = selectBestAnswer(rounds);
                    sink.next(new FinalAnswerEvent(Instant.now(), bestAnswer));
                    sink.complete();
                    return;
                }

                // ===== Round 1: Reflector (if score < PASS_THRESHOLD && MAX_REFLECTIONS >= 1) =====
                if (MAX_REFLECTIONS >= 1) {
                    String reflectionRaw1 = chatClientBuilder
                            .defaultSystem(REFLECTOR_PROMPT
                                    .replace("{question}", ctx.question())
                                    .replace("{answer}", safeAnswer1)
                                    .replace("{score}", String.valueOf(score1))
                                    .replace("{feedback}", feedback1))
                            .build()
                            .prompt()
                            .user(u -> u.text("问题: " + ctx.question() + "\n答案: " + safeAnswer1))
                            .call()
                            .content();

                    reflection1 = extractReflection(reflectionRaw1);
                    sink.next(new ReflexionReflectEvent(Instant.now(), 1, reflection1));
                }

                rounds.add(new RoundResult(1, safeAnswer1, score1, feedback1, reflection1));

                // ===== Round 2: Generator (if MAX_REFLECTIONS >= 2) =====
                if (MAX_REFLECTIONS >= 2) {
                    RoundResult prevRound = rounds.get(0);
                    String improvedAnswer = chatClientBuilder
                            .defaultSystem(GENERATOR_WITH_REFLECTION_PROMPT
                                    .replace("{question}", ctx.question())
                                    .replace("{prevAnswer}", prevRound.answer())
                                    .replace("{prevScore}", String.valueOf(prevRound.score()))
                                    .replace("{prevFeedback}", prevRound.feedback())
                                    .replace("{reflection}", prevRound.reflection()))
                            .build()
                            .prompt()
                            .user(u -> u.text("问题: " + ctx.question()))
                            .call()
                            .content();

                    String safeImprovedAnswer = improvedAnswer != null ? improvedAnswer : "";
                    sink.next(new ReflexionAttemptEvent(Instant.now(), 2, safeImprovedAnswer));

                    // ===== Round 2: Evaluator =====
                    String evalRaw2 = chatClientBuilder
                            .defaultSystem(EVALUATOR_PROMPT
                                    .replace("{question}", ctx.question())
                                    .replace("{answer}", safeImprovedAnswer))
                            .build()
                            .prompt()
                            .user(u -> u.text("问题: " + ctx.question() + "\n答案: " + safeImprovedAnswer))
                            .call()
                            .content();

                    int score2 = parseScoreSafely(evalRaw2);
                    String feedback2 = extractFeedback(evalRaw2);
                    sink.next(new ReflexionEvaluateEvent(Instant.now(), 2, score2, feedback2));
                    rounds.add(new RoundResult(2, safeImprovedAnswer, score2, feedback2, ""));

                    // ===== Epsilon Check (per D-04 + RESEARCH.md Pitfall 4 mitigation) =====
                    // Only check epsilon when score2 < PASS_THRESHOLD
                    if (score2 >= PASS_THRESHOLD) {
                        String bestAnswer = selectBestAnswer(rounds);
                        sink.next(new FinalAnswerEvent(Instant.now(), bestAnswer));
                        sink.complete();
                        return;
                    }

                    if (Math.abs(score2 - prevScore) < EPSILON) {
                        // Epsilon early stop: select best answer, no more reflector
                        String bestAnswer = selectBestAnswer(rounds);
                        sink.next(new FinalAnswerEvent(Instant.now(), bestAnswer));
                        sink.complete();
                        return;
                    }
                }

                // ===== FINALIZE =====
                String bestAnswer = selectBestAnswer(rounds);
                sink.next(new FinalAnswerEvent(Instant.now(), bestAnswer));
                sink.complete();

            } catch (Exception ex) {
                sink.next(new ErrorEvent(Instant.now(),
                        "Reflexion 模式执行异常: " + ex.getMessage()));
                sink.complete();
            }
        });
    }
}