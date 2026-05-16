package com.aegispay.ai.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Disambiguates AI model beans per Spring profile.
 *
 * <p><b>Default / prod profile</b>: Anthropic Claude is {@code @Primary}.
 * <br><b>onprem profile</b>: OpenRouter (via OpenAI-compatible adapter) is {@code @Primary}.
 *
 * <p>Both {@code spring-ai-starter-model-anthropic} and {@code spring-ai-starter-model-openai}
 * are on the classpath; the active profile determines which implementation wins unqualified
 * {@link ChatModel} injection points (e.g. {@link ChatClient.Builder}).
 */
@Configuration
public class AiModelConfig {

    // ── Prod / default — Anthropic Claude ────────────────────────────────────

    /**
     * Anthropic Claude is the primary chat model on prod and any non-onprem profile.
     * The {@code !onprem} condition ensures this bean is NOT registered when
     * the onprem profile is active, avoiding duplicate {@code @Primary} beans.
     */
    @Bean
    @Primary
    @Profile("!onprem")
    public ChatModel primaryChatModelProd(AnthropicChatModel anthropicChatModel) {
        return anthropicChatModel;
    }

    // ── On-prem — OpenRouter (OpenAI-compatible) ──────────────────────────────

    /**
     * On onprem profile, the OpenAI-adapter bean (pointing to OpenRouter) is primary.
     * {@code application-onprem.yml} configures:
     *   spring.ai.openai.base-url = https://openrouter.ai/api/v1
     *   spring.ai.openai.api-key  = ${OPENROUTER_API_KEY}
     *   spring.ai.openai.chat.options.model = meta-llama/llama-3.1-8b-instruct:free
     */
    @Bean
    @Primary
    @Profile("onprem")
    public ChatModel primaryChatModelOnprem(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
