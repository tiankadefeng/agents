package com.agents.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 unit test for {@link ChatClientConfig} (LLM-03).
 *
 * <p>Verifies that the {@link ChatClient} bean is auto-configured end-to-end:
 * {@code DeepSeekAutoConfiguration} injects the Builder (prototype scope), and
 * {@link ChatClientConfig} builds it with the Chinese system prompt. If the
 * auto-configuration fails to inject the Builder, this test fails - catches the
 * A3 assumption in 01-RESEARCH.md Assumptions Log.
 *
 * <p>Do NOT manually construct the client via {@code ChatClient.builder(chatModel).build()}
 * in this test - the assertion must verify Spring's auto-configuration works end-to-end.
 */
@SpringBootTest
class ChatClientConfigTest {

    @Autowired
    ChatClient chatClient;

    @Test
    void shouldCreateChatClientBean() {
        // LLM-03: ChatClientConfig produces a ChatClient bean
        assertThat(chatClient).isNotNull();
    }
}
