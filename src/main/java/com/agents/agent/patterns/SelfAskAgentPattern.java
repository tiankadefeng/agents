package com.agents.agent.patterns;

import com.agents.agent.core.AgentContext;
import com.agents.agent.core.AgentEvent;
import com.agents.agent.core.AgentPattern;
import com.agents.agent.core.ErrorEvent;
import com.agents.agent.core.FinalAnswerEvent;
import com.agents.agent.core.SubAnswerEvent;
import com.agents.agent.core.SubQuestionEvent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Self-Ask（自问自答）模式实现 - 子问题分解与链式推理。
 *
 * <p>D-02: 使用单次 LLM 调用（{@link ChatClient#call()}），获取完整 JSON 响应后，
 * 由 Jackson 解析并逐个发射 SubQuestionEvent -> SubAnswerEvent -> ... -> FinalAnswerEvent。
 *
 * <p>D-03: 使用结构化 JSON 输出格式，system prompt 指定 JSON Schema，Jackson 解析。
 *
 * <p>Unlike CoT (streaming per-chunk), Self-Ask is a single call that returns a structured
 * JSON object containing all sub-questions, their answers, and the final answer at once.
 */
@Component
public class SelfAskAgentPattern implements AgentPattern {

    private static final String SELF_ASK_SYSTEM_PROMPT = """
            你是一个 Self-Ask（自问自答）Agent。你的任务是将用户的问题分解为多个子问题，逐一回答每个子问题，然后综合所有子问题的答案得出最终答案。

            请严格按照以下 JSON 格式输出，不要包含任何额外的文字、markdown 代码块包裹或解释：
            {
              "sub_questions": [
                {"question": "子问题文本", "answer": "子问题答案"}
              ],
              "final_answer": "综合所有子问题答案得出的最终答案"
            }

            注意：输出 ONLY 上述 JSON 对象，不要加 ```json 或任何其他标记，不要加任何解释性文字。
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public SelfAskAgentPattern(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "selfAsk";
    }

    @Override
    public String displayName() {
        return "Self-Ask 自问自答";
    }

    @Override
    public String description() {
        return "子问题分解与链式推理";
    }

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            try {
                ChatClient chatClient = chatClientBuilder
                        .defaultSystem(SELF_ASK_SYSTEM_PROMPT)
                        .build();

                String response = chatClient.prompt()
                        .user(ctx.question())
                        .call()
                        .content();

                if (response == null || response.isBlank()) {
                    sink.next(new ErrorEvent(Instant.now(), "Self-Ask 模式未能生成响应，请重试"));
                    sink.complete();
                    return;
                }

                String cleaned = extractJson(response);
                JsonNode root = objectMapper.readTree(cleaned);

                JsonNode subQuestions = root.get("sub_questions");
                if (subQuestions == null || !subQuestions.isArray() || subQuestions.isEmpty()) {
                    sink.next(new ErrorEvent(Instant.now(),
                            "Self-Ask 模式未能生成有效子问题。请尝试换个问题重试。"));
                    sink.complete();
                    return;
                }

                for (JsonNode sq : subQuestions) {
                    Instant now = Instant.now();
                    String question = sq.get("question") != null ? sq.get("question").asText() : "";
                    String answer = sq.get("answer") != null ? sq.get("answer").asText() : "";
                    sink.next(new SubQuestionEvent(now, question));
                    sink.next(new SubAnswerEvent(now, question, answer));
                }

                String finalAnswerText = root.get("final_answer") != null
                        ? root.get("final_answer").asText() : "";
                sink.next(new FinalAnswerEvent(Instant.now(), finalAnswerText));
                sink.complete();

            } catch (Exception ex) {
                sink.next(new ErrorEvent(Instant.now(),
                        "Self-Ask 模式解析失败：模型输出格式不正确。请重试或更换模式。"));
                sink.complete();
            }
        });
    }

    /**
     * Preprocess raw LLM response: remove markdown code block markers, then extract
     * content between first '{' and last '}'.
     *
     * @param raw raw LLM response string, may be null
     * @return cleaned JSON string, or null if input is null
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
}