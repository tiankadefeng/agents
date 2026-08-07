package com.agents;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepSeek integration tests (LLM-01, LLM-04).
 * Requires DEEPSEEK_API_KEY environment variable.
 * Tagged with "integration" to skip in default mvn test (Surefire excludedGroups).
 */
@SpringBootTest
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekIntegrationTest {

    @Autowired
    ChatClient chatClient;

    /**
     * LLM-01: Verify native DeepSeek starter is used (not OpenAI shim).
     * If OpenAI shim was used, DeepSeekAssistantMessage class would not exist.
     */
    @Test
    void shouldUseNativeDeepSeekStarter() {
        Flux<ChatResponse> flux = chatClient.prompt()
            .user("1+1=?")
            .stream()
            .chatResponse();

        ChatResponse first = flux.blockFirst();
        assertThat(first).isNotNull();
        assertThat(first.getResult().getOutput())
            .isInstanceOf(DeepSeekAssistantMessage.class);
    }

    /**
     * LLM-04: Verify reasoning_content is non-empty for deepseek-reasoner model.
     * Uses a reasoning-heavy question to trigger reasoning_content output.
     */
    @Test
    void shouldReturnNonEmptyReasoningContent() {
        Flux<ChatResponse> flux = chatClient.prompt()
            .user("Prove that the square root of 2 is irrational")
            .stream()
            .chatResponse();

        Boolean hasReasoning = flux.toStream()
            .map(cr -> cr.getResult().getOutput())
            .filter(DeepSeekAssistantMessage.class::isInstance)
            .map(DeepSeekAssistantMessage.class::cast)
            .map(DeepSeekAssistantMessage::getReasoningContent)
            .anyMatch(r -> r != null && !r.isEmpty());

        assertThat(hasReasoning)
            .as("reasoning_content should be non-empty for deepseek-reasoner")
            .isTrue();
    }
}