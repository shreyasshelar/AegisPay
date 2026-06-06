package com.aegispay.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Disambiguates AI model beans per Spring profile and wires the fallback chain.
 *
 * <h3>Dev profile — fallback chain: OpenRouter → Groq → Gemini</h3>
 * <ol>
 *   <li><b>OpenRouter</b> (Claude Sonnet 4.5) — primary, highest quality</li>
 *   <li><b>Groq</b> (Llama 3.3 70B) — activated when {@code GROQ_API_KEY} is present;
 *       fast, generous free tier</li>
 *   <li><b>Gemini</b> (Gemini 2.0 Flash) — activated when {@code GEMINI_API_KEY} is present;
 *       Google free tier last resort</li>
 * </ol>
 * Providers with a blank or missing API key are silently skipped so the service starts
 * normally even before the GCP SM secrets are created.
 *
 * <h3>Prod / default profile — Anthropic Claude (direct)</h3>
 * {@code spring-ai-starter-model-anthropic} is {@code @Primary} outside dev.
 */
@Slf4j
@Configuration
public class AiModelConfig {

    // ── Prod / default — Anthropic Claude direct ──────────────────────────────

    @Bean
    @Primary
    @Profile("!dev")
    public ChatModel primaryChatModelProd(AnthropicChatModel anthropicChatModel) {
        return anthropicChatModel;
    }

    // ── Dev — OpenRouter → Groq → Gemini fallback chain ──────────────────────

    /**
     * Groq base URL (OpenAI-compatible).
     * Free tier: 14,400 req/day, 500 req/min, 6,000 tokens/min.
     */
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";

    /**
     * Gemini OpenAI-compatible endpoint.
     * Free tier: 15 RPM / 1,500 RPD (Gemini 2.0 Flash).
     */
    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai/";

    @Bean
    @Primary
    @Profile("dev")
    public ChatModel primaryChatModelDev(
            // Primary: auto-configured by spring.ai.openai.* (points to OpenRouter)
            OpenAiChatModel openRouterModel,

            // Fallback 1: Groq — optional; blank = provider skipped
            @Value("${GROQ_API_KEY:}") String groqApiKey,
            @Value("${aegispay.ai.providers.groq.model:llama-3.3-70b-versatile}") String groqModel,

            // Fallback 2: Gemini — optional; blank = provider skipped
            @Value("${GEMINI_API_KEY:}") String geminiApiKey,
            @Value("${aegispay.ai.providers.gemini.model:gemini-2.0-flash}") String geminiModel
    ) {
        List<FallbackChatModel.NamedProvider> providers = new ArrayList<>();

        // ── Provider 1: OpenRouter (always present — key injected via ESO) ──
        providers.add(new FallbackChatModel.NamedProvider("openrouter", openRouterModel));

        // ── Provider 2: Groq ─────────────────────────────────────────────────
        if (isPresent(groqApiKey)) {
            OpenAiApi groqApi = OpenAiApi.builder()
                    .baseUrl(GROQ_BASE_URL)
                    .apiKey(groqApiKey)
                    .build();
            OpenAiChatModel groqChatModel = OpenAiChatModel.builder()
                    .openAiApi(groqApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(groqModel)
                            .maxTokens(4096)
                            .temperature(0.1)
                            .build())
                    .build();
            providers.add(new FallbackChatModel.NamedProvider("groq", groqChatModel));
            log.info("AI provider Groq registered (model={})", groqModel);
        } else {
            log.info("AI provider Groq skipped — GROQ_API_KEY not configured");
        }

        // ── Provider 3: Gemini ───────────────────────────────────────────────
        if (isPresent(geminiApiKey)) {
            OpenAiApi geminiApi = OpenAiApi.builder()
                    .baseUrl(GEMINI_BASE_URL)
                    .apiKey(geminiApiKey)
                    .build();
            OpenAiChatModel geminiChatModel = OpenAiChatModel.builder()
                    .openAiApi(geminiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(geminiModel)
                            .maxTokens(4096)
                            .temperature(0.1)
                            .build())
                    .build();
            providers.add(new FallbackChatModel.NamedProvider("gemini", geminiChatModel));
            log.info("AI provider Gemini registered (model={})", geminiModel);
        } else {
            log.info("AI provider Gemini skipped — GEMINI_API_KEY not configured");
        }

        return new FallbackChatModel(providers);
    }

    /** Returns true if the string is non-null, non-blank, and not a placeholder value. */
    private static boolean isPresent(String key) {
        return key != null && !key.isBlank()
                && !key.equalsIgnoreCase("not-configured")
                && !key.equalsIgnoreCase("placeholder");
    }
}
