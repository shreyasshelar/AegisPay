package com.aegispay.ai.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Disambiguates AI model beans.
 *
 * <p>Both spring-ai-starter-model-anthropic and spring-ai-starter-model-openai are on the
 * classpath (OpenAI is used for embeddings fallback). Spring AI auto-configures two
 * {@link ChatModel} beans — this configuration marks Anthropic as {@code @Primary} so that
 * unqualified injection points resolve without ambiguity.
 */
@Configuration
public class AiModelConfig {

    /**
     * Expose Anthropic as the primary {@link ChatModel} so that {@link ChatClient.Builder}
     * and any unqualified {@code ChatModel} injection point get the Anthropic implementation.
     */
    @Bean
    @Primary
    public ChatModel primaryChatModel(AnthropicChatModel anthropicChatModel) {
        return anthropicChatModel;
    }
}
