package com.agents.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the shared {@link ChatClient} bean for Phase 1+ controllers.
 *
 * <p>The Builder used here is auto-injected by {@code DeepSeekAutoConfiguration}
 * (prototype scope - each injection point gets a fresh builder clone). This class fixes
 * the system prompt so all callers share the same one - single source of truth. Plan 02's
 * PingController will inject {@link ChatClient} directly (not the Builder) and skip
 * calling {@code .system(...)} per request.
 *
 * <p>Do NOT manually construct {@code ChatClient.builder(chatModel)} - the Builder
 * auto-configuration handles DeepSeek chat model wiring. Do NOT use
 * {@code PromptChatMemoryAdvisor} (removed in Spring AI 2.0); Phase 2+ will introduce
 * {@code MessageChatMemoryAdvisor} if needed.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("你是一个友好的助手。请先思考再回答。")
            .build();
    }
}
