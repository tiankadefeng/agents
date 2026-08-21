package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.RoleDevEvent;
import com.agents.agent.core.RolePmEvent;
import com.agents.agent.core.RoleTesterEvent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Role-playing（角色扮演）模式实现 - PM / Dev / Tester 三角色固定顺序协作对话。
 *
 * <p>D-01: 5 轮 × 3 角色 = 15 次发言，每轮固定顺序 PM -&gt; Dev -&gt; Tester，
 * 共 15 次 LLM 调用。D-02: 每角色发言时通过 user message 传入完整对话历史
 * （所有已发言轮次，以纯文本格式），确保协作连贯。D-04: 5 轮对话结束后
 * 发起一次独立总结 LLM 调用，以 {@link FinalAnswerEvent} 发射结论，
 * 总调用量 = 16 次。
 *
 * <p>D-05: 仅注入 {@link ChatClient.Builder}，不注入 ToolRegistry
 * （Role-playing 为纯多角色协作，不需要外部工具）。使用默认模型
 * （application.yml 已配置 deepseek-chat，不额外指定模型）。
 *
 * <p>所有角色发言均为 {@code .call().content()} 一次性完整返回（非流式），
 * 事件逐个发射（非全部完成后一次性发射），前端收到完整事件后立即渲染。
 */
@Component
public class RolePlayingAgentPattern implements AgentPattern {

    // ===== Constants =====

    /** 固定对话轮次（per D-01 + ROADMAP） */
    private static final int ROUNDS = 5;

    /** PM（产品经理）角色名 */
    private static final String ROLE_PM = "PM";

    /** Dev（开发者）角色名 */
    private static final String ROLE_DEV = "Dev";

    /** Tester（测试工程师）角色名 */
    private static final String ROLE_TESTER = "Tester";

    // ===== System Prompts (per D-07 持续协作模式) =====

    private static final String PM_SYSTEM_PROMPT =
            "你是一个软件项目的产品经理 (PM)。你的职责是：\n"
            + "1. 第一轮：提出初始需求，明确功能和目标\n"
            + "2. 后续轮次：根据开发者的进展和测试者的反馈，追问细节、澄清需求、调整优先级\n"
            + "3. 始终保持对项目全局的掌控，确保最终交付物满足需求\n\n"
            + "当前讨论的问题：{question}\n\n"
            + "请以产品经理的身份发言，语言简洁专业，聚焦于\"要什么\"和\"为什么\"。";

    private static final String DEV_SYSTEM_PROMPT =
            "你是一个软件项目的开发者 (Dev)。你的职责是：\n"
            + "1. 第一轮：根据 PM 的需求，提出设计方案和技术实现思路\n"
            + "2. 后续轮次：根据 PM 的追问和测试者的 bug 反馈，逐步改进实现方案\n"
            + "3. 关注技术可行性、实现细节、性能优化\n\n"
            + "当前讨论的问题：{question}\n\n"
            + "请以开发者的身份发言，语言简洁专业，聚焦于\"怎么做\"和\"技术方案\"。";

    private static final String TESTER_SYSTEM_PROMPT =
            "你是一个软件项目的测试工程师 (Tester)。你的职责是：\n"
            + "1. 第一轮：根据 PM 的需求和 Dev 的方案，提出测试思路和验收标准\n"
            + "2. 后续轮次：验证 Dev 的实现是否满足需求，提出 bug 和改进建议\n"
            + "3. 关注质量、边界情况、用户体验\n\n"
            + "当前讨论的问题：{question}\n\n"
            + "请以测试工程师的身份发言，语言简洁专业，聚焦于\"怎么验证\"和\"质量保障\"。";

    private static final String SUMMARY_PROMPT =
            "你是一个专业的会议记录员。请根据以下角色扮演对话内容，生成一份简洁的总结。\n\n"
            + "原始问题：{question}\n\n"
            + "对话内容：\n"
            + "{conversation}\n\n"
            + "请从以下三个方面总结：\n"
            + "1. 需求概要：最终确定的需求是什么\n"
            + "2. 设计方案：最终采用的技术方案是什么\n"
            + "3. 质量保障：测试方案和关键关注点\n\n"
            + "请输出简洁的总结报告。";

    // ===== Injected Dependencies =====

    private final ChatClient.Builder chatClientBuilder;

    public RolePlayingAgentPattern(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    // ===== AgentPattern Contract =====

    @Override
    public String id() {
        return "roleplay";
    }

    @Override
    public String displayName() {
        return "Role-playing 角色扮演";
    }

    @Override
    public String description() {
        return "多智能体分工协作";
    }

    // ===== Internal Data Structure =====

    /**
     * 单次角色发言记录（内部数据结构，用于维护对话历史）。
     * D-02: 完整历史通过 {@link #buildHistoryPrompt} 格式化为纯文本
     * 传入后续角色调用的 user message。
     *
     * <p>package-private 以便单元测试直接构造 {@link Utterance} 验证
     * {@code buildHistoryPrompt} 的输出格式。
     */
    record Utterance(int round, String role, String content) {}

    // ===== History Prompt Builder (per T-10-04, 独立方法格式单一) =====

    /**
     * 将历史对话列表格式化为 user message 文本。
     *
     * <p>格式：
     * <pre>
     * 原始问题: {question}
     *
     * 当前对话历史：
     * Round 1 PM: ...
     * Round 1 Dev: ...
     * ...
     * </pre>
     */
    static String buildHistoryPrompt(List<Utterance> history, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("原始问题: ").append(question);
        if (history.isEmpty()) {
            sb.append("\n\n当前对话历史：（暂无，这是第一轮发言）");
            return sb.toString();
        }
        sb.append("\n\n当前对话历史：\n");
        for (Utterance u : history) {
            sb.append("Round ").append(u.round()).append(" ").append(u.role())
              .append(": ").append(u.content()).append("\n");
        }
        return sb.toString();
    }

    // ===== Role Call Helper =====

    /**
     * 执行单次角色 LLM 调用（per T-10-01: null 降级为空字符串，永不传 null）。
     */
    private String callRole(String systemPrompt, String userPrompt) {
        String content = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build()
                .prompt()
                .user(u -> u.text(userPrompt))
                .call()
                .content();
        return content != null ? content : "";
    }

    // ===== Execute =====

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            try {
                // D-02: 完整对话历史 -- 每角色调用时 history 包含所有已发言轮次
                List<Utterance> history = new ArrayList<>();

                for (int round = 1; round <= ROUNDS; round++) {

                    // ===== PM 调用 =====
                    String pmUserPrompt = buildHistoryPrompt(history, ctx.question());
                    String pmContent = callRole(
                            PM_SYSTEM_PROMPT.replace("{question}", ctx.question()),
                            pmUserPrompt);
                    sink.next(new RolePmEvent(Instant.now(), round, ROLE_PM, pmContent));
                    history.add(new Utterance(round, ROLE_PM, pmContent));

                    // ===== Dev 调用 =====
                    String devUserPrompt = buildHistoryPrompt(history, ctx.question());
                    String devContent = callRole(
                            DEV_SYSTEM_PROMPT.replace("{question}", ctx.question()),
                            devUserPrompt);
                    sink.next(new RoleDevEvent(Instant.now(), round, ROLE_DEV, devContent));
                    history.add(new Utterance(round, ROLE_DEV, devContent));

                    // ===== Tester 调用 =====
                    String testerUserPrompt = buildHistoryPrompt(history, ctx.question());
                    String testerContent = callRole(
                            TESTER_SYSTEM_PROMPT.replace("{question}", ctx.question()),
                            testerUserPrompt);
                    sink.next(new RoleTesterEvent(Instant.now(), round, ROLE_TESTER, testerContent));
                    history.add(new Utterance(round, ROLE_TESTER, testerContent));
                }

                // ===== 总结调用 (per D-04) =====
                String conversation = formatConversation(history);
                String summary = chatClientBuilder
                        .defaultSystem(SUMMARY_PROMPT
                                .replace("{question}", ctx.question())
                                .replace("{conversation}", conversation))
                        .build()
                        .prompt()
                        .user(u -> u.text("请总结以上对话"))
                        .call()
                        .content();
                sink.next(new FinalAnswerEvent(Instant.now(), summary != null ? summary : ""));
                sink.complete();

            } catch (Exception ex) {
                // per T-10-02: 任何 LLM 调用异常发射 ErrorEvent 并 complete
                sink.next(new ErrorEvent(Instant.now(),
                        "Role-playing 模式执行异常: " + ex.getMessage()));
                sink.complete();
            }
        });
    }

    // ===== Conversation Formatter =====

    /**
     * 将完整对话历史格式化为总结 prompt 的对话文本。
     */
    private static String formatConversation(List<Utterance> history) {
        StringBuilder sb = new StringBuilder();
        for (Utterance u : history) {
            sb.append("Round ").append(u.round()).append(" ").append(u.role())
              .append(": ").append(u.content()).append("\n");
        }
        return sb.toString();
    }
}
