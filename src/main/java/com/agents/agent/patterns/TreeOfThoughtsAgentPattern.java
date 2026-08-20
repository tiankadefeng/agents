package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.TotNodeEvent;
import com.agents.agent.core.TotPruneEvent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tree of Thoughts（树状思维）模式实现 - 多分支探索与评估，贪心剪枝选最优路径。
 *
 * <p>BFS 逐层扩展（branching=3, depth=3），每个节点独立 LLM 评估打分（1-10），
 * 每层 top-K=2 贪心剪枝并发射剪枝事件，最终从存活分支回溯最优路径生成最终答案。
 * 所有推理过程以 {@link TotNodeEvent}/{@link TotPruneEvent} 流式发射，前端可重建树。
 *
 * <p>D-04: 仅注入 {@link ChatClient.Builder} + {@link ObjectMapper}，不注入 ToolRegistry
 * （ToT 为纯 LLM 推理，探索类问题不需要外部工具）。
 */
@Component
public class TreeOfThoughtsAgentPattern implements AgentPattern {

    // ===== Constants =====

    /** 每存活节点生成分支数 */
    private static final int BRANCHING = 3;

    /** 扩展层数（level 0/1/2） */
    private static final int DEPTH = 3;

    /** 每层保留节点数 */
    private static final int TOP_K = 2;

    /** 总节点数保险丝上限 */
    private static final int MAX_TOTAL_NODES = 50;

    /** 评分解析失败兜底中位分 */
    private static final int DEFAULT_SCORE = 5;

    // ===== System Prompts =====

    private static final String GENERATE_SYSTEM_PROMPT =
            "你是一个 Tree of Thoughts 分支生成器。\n\n"
            + "原始问题: {question}\n\n"
            + "当前思路路径: {thoughtPath}\n\n"
            + "请基于当前思路，生成 3 个不同的后续思考方向。\n"
            + "每个方向应该是一个独立的、有潜力的推理分支。\n"
            + "分支之间应该有明显差异，覆盖不同的解决思路。\n\n"
            + "请严格按照以下 JSON 格式输出，不要包含任何额外的文字或 markdown 包裹：\n"
            + "{\n"
            + "  \"branches\": [\n"
            + "    \"分支 1 的完整思考内容...\",\n"
            + "    \"分支 2 的完整思考内容...\",\n"
            + "    \"分支 3 的完整思考内容...\"\n"
            + "  ]\n"
            + "}\n\n"
            + "每个分支应包含完整的推理思路，而非简短标题。\n"
            + "输出 ONLY 上述 JSON 对象，不要加 ```json 或任何其他标记。";

    private static final String EVALUATE_SYSTEM_PROMPT =
            "你是一个 Tree of Thoughts 评估器。请评估以下推理思路的质量。\n\n"
            + "原始问题: {question}\n\n"
            + "推理思路: {thought}\n\n"
            + "请从以下维度评估该思路的质量:\n"
            + "1. 逻辑合理性: 推理是否连贯、无矛盾\n"
            + "2. 与问题的相关性: 是否直接针对问题核心\n"
            + "3. 解决问题的潜力: 沿着这个思路走，有多大可能找到正确答案\n\n"
            + "请输出一个 1-10 的整数评分，其中:\n"
            + "- 10 = 非常优秀，几乎可以直接得到答案\n"
            + "- 7-9 = 很好，有很高的成功潜力\n"
            + "- 4-6 = 一般，可能有用但需要进一步探索\n"
            + "- 1-3 = 较差，偏离问题或逻辑有问题\n\n"
            + "请严格按照以下格式输出，只输出一个整数，不要包含任何其他文字或解释：\n"
            + "评分: [1-10 的整数]";

    private static final String SUMMARIZE_SYSTEM_PROMPT =
            "你是一个 Tree of Thoughts 汇总器。\n\n"
            + "原始问题: {question}\n\n"
            + "经过多分支探索和评估，以下是最优推理路径（从原始问题到最佳答案的完整思路链）:\n\n"
            + "{optimalPath}\n\n"
            + "请基于以上最优路径，给出一个完整、清晰的最终答案。";

    // ===== Injected Dependencies =====

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public TreeOfThoughtsAgentPattern(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
    }

    // ===== AgentPattern Contract =====

    @Override
    public String id() {
        return "tot";
    }

    @Override
    public String displayName() {
        return "Tree of Thoughts 树状思维";
    }

    @Override
    public String description() {
        return "多分支探索与评估，贪心剪枝选最优路径";
    }

    // ===== Internal Data Structure =====

    private record TotNode(int id, int level, Integer parentId, String thought, int score) {}

    // ===== JSON Extraction =====

    private static String extractJson(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first == -1 || last == -1 || last < first) return cleaned;
        return cleaned.substring(first, last + 1);
    }

    // ===== Score Parsing (per D-02, never throws) =====

    private int parseScoreSafely(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_SCORE;
        try {
            String cleaned = raw.trim().toLowerCase();

            // 策略 1: 匹配 "评分: N" 或 "评分：N"
            Matcher m1 = Pattern.compile("评分[:：]\\s*(\\d+)").matcher(cleaned);
            if (m1.find()) return clamp(Integer.parseInt(m1.group(1)));

            // 策略 2: 匹配 "N/10"
            Matcher m2 = Pattern.compile("\\b(\\d+)\\s*/\\s*10\\b").matcher(cleaned);
            if (m2.find()) return clamp(Integer.parseInt(m2.group(1)));

            // 策略 3: 提取第一个独立数字 1-10
            Matcher m3 = Pattern.compile("\\b([1-9]|10)\\b").matcher(cleaned);
            if (m3.find()) return clamp(Integer.parseInt(m3.group(1)));

            // 策略 4: 关键词匹配
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

    // ===== Branch Parsing (never throws) =====

    private List<String> parseBranches(String raw) {
        try {
            String json = extractJson(raw);
            if (json == null) return List.of(raw != null && !raw.isBlank() ? raw : "");
            JsonNode root = objectMapper.readTree(json);
            JsonNode branches = root.get("branches");
            if (branches != null && branches.isArray() && !branches.isEmpty()) {
                List<String> result = new ArrayList<>();
                for (JsonNode b : branches) {
                    String text = b.asText();
                    if (text != null && !text.isBlank()) {
                        result.add(text);
                    }
                }
                if (!result.isEmpty()) return result;
            }
        } catch (Exception e) {
            // ignore, fallback below
        }
        // 降级: 将整个原始文本作为单一分支返回
        return List.of(raw != null && !raw.isBlank() ? raw : "");
    }

    // ===== Execute =====

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            try {
                // ===== Data structures =====
                Map<Integer, TotNode> allNodes = new HashMap<>();
                int nodeSeq = 0;
                int totalNodes = 0;

                // ===== Root node =====
                TotNode root = new TotNode(nodeSeq++, -1, null, ctx.question(), 0);
                allNodes.put(root.id(), root);
                sink.next(new TotNodeEvent(Instant.now(), -1, root.id(), root.thought(), 0, null));
                totalNodes++;

                List<TotNode> aliveNodes = List.of(root);

                // ===== BFS main loop =====
                bfsLoop:
                for (int level = 0; level < DEPTH; level++) {
                    List<TotNode> nextLevel = new ArrayList<>();

                    // ---- EXPAND + SCORE ----
                    for (TotNode parent : aliveNodes) {
                        // Fuse check: total node limit
                        if (totalNodes >= MAX_TOTAL_NODES) {
                            sink.next(new ErrorEvent(Instant.now(),
                                    "Tree of Thoughts 节点数已达上限 (" + MAX_TOTAL_NODES
                                    + ")，将从已探索路径中生成答案。"));
                            break bfsLoop;
                        }

                        // Generate branches from parent
                        String raw;
                        try {
                            String generatePrompt = GENERATE_SYSTEM_PROMPT
                                    .replace("{question}", ctx.question())
                                    .replace("{thoughtPath}", parent.thought());
                            raw = chatClientBuilder.defaultSystem(generatePrompt).build()
                                    .prompt().user(u -> u.text("问题: " + ctx.question()
                                            + "\n当前推理路径: " + parent.thought()))
                                    .call().content();
                        } catch (Exception e) {
                            // Skip this parent if LLM call fails
                            continue;
                        }

                        List<String> branches = parseBranches(raw);

                        for (String branch : branches) {
                            // Fuse check: also check inside branch loop
                            if (totalNodes >= MAX_TOTAL_NODES) break;

                            // Score the branch
                            String scoreRaw;
                            try {
                                String evalPrompt = EVALUATE_SYSTEM_PROMPT
                                        .replace("{question}", ctx.question())
                                        .replace("{thought}", branch);
                                scoreRaw = chatClientBuilder.defaultSystem(evalPrompt).build()
                                        .prompt().user(u -> u.text("问题: " + ctx.question()
                                                + "\n推理思路: " + branch))
                                        .call().content();
                            } catch (Exception e) {
                                scoreRaw = null;
                            }

                            int score = parseScoreSafely(scoreRaw);
                            TotNode node = new TotNode(nodeSeq++, level, parent.id(), branch, score);
                            allNodes.put(node.id(), node);
                            sink.next(new TotNodeEvent(Instant.now(), level, node.id(), node.thought(), score, node.parentId()));
                            nextLevel.add(node);
                            totalNodes++;
                        }
                    }

                    // ---- Check if level produced any nodes ----
                    if (nextLevel.isEmpty()) {
                        sink.next(new ErrorEvent(Instant.now(),
                                "所有分支均已被剪枝，将使用当前评分最高的节点生成答案。"));
                        break; // Use current aliveNodes (previous level) for backtracking
                    }

                    // ---- PRUNE (except last level) ----
                    if (level < DEPTH - 1) {
                        nextLevel.sort(Comparator.comparingInt(TotNode::score).reversed());
                        if (nextLevel.size() > TOP_K) {
                            List<Integer> prunedIds = new ArrayList<>();
                            for (int i = TOP_K; i < nextLevel.size(); i++) {
                                prunedIds.add(nextLevel.get(i).id());
                            }
                            sink.next(new TotPruneEvent(Instant.now(), level, prunedIds,
                                    "评分低于 top-K=" + TOP_K + " 阈值"));
                            aliveNodes = new ArrayList<>(nextLevel.subList(0, TOP_K));
                        } else {
                            aliveNodes = nextLevel;
                        }
                    } else {
                        // Last level: keep all nodes
                        aliveNodes = nextLevel;
                    }
                }

                // ===== SELECT: Best path backtracking =====
                TotNode best = aliveNodes.stream()
                        .max(Comparator.comparingInt(TotNode::score))
                        .orElse(root);

                List<TotNode> path = new ArrayList<>();
                TotNode current = best;
                while (current != null) {
                    path.add(current);
                    current = current.parentId() != null ? allNodes.get(current.parentId()) : null;
                }
                Collections.reverse(path); // Root -> leaf

                // Build path text
                StringBuilder pathText = new StringBuilder();
                for (int i = 0; i < path.size(); i++) {
                    TotNode pn = path.get(i);
                    pathText.append("第 ").append(i + 1).append(" 步: ").append(pn.thought()).append("\n");
                }

                // ===== SUMMARIZE =====
                String summaryPrompt = SUMMARIZE_SYSTEM_PROMPT
                        .replace("{question}", ctx.question())
                        .replace("{optimalPath}", pathText.toString().trim());
                String summary = chatClientBuilder.defaultSystem(summaryPrompt).build()
                        .prompt().user(u -> u.text("原始问题: " + ctx.question()
                                + "\n\n最优推理路径:\n" + pathText.toString().trim()))
                        .call().content();

                if (summary != null && !summary.isBlank()) {
                    sink.next(new FinalAnswerEvent(Instant.now(), summary));
                } else {
                    sink.next(new ErrorEvent(Instant.now(),
                            "Tree of Thoughts 未能生成最终答案，请重试或更换问题。"));
                }
                sink.complete();

            } catch (Exception ex) {
                sink.next(new ErrorEvent(Instant.now(),
                        "Tree of Thoughts 模式执行异常: " + ex.getMessage()));
                sink.complete();
            }
        });
    }
}