package com.aegispay.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Multi-provider AI chat model with automatic fallback.
 *
 * <p>Tries each registered provider in declaration order. If a provider throws any exception
 * (spending limit exceeded, rate limit, network error, model unavailable), the next provider
 * is attempted transparently. All failures are logged at WARN level.
 *
 * <p><b>Provider order (dev profile):</b> OpenRouter → Groq → Gemini
 *
 * <ul>
 *   <li>OpenRouter — Claude Sonnet 4.5 (best quality, OpenRouter free tier)
 *   <li>Groq — Llama 3.3 70B (ultra-fast, generous free tier)
 *   <li>Gemini — Gemini 2.0 Flash (Google free tier fallback)
 * </ul>
 *
 * <p><b>Streaming</b>: uses reactive {@code onErrorResume} to fall through to the next
 * provider if the stream errors before or after the first token. Mid-stream switching is
 * best-effort — if OpenRouter successfully emits tokens and then errors, those partial tokens
 * are lost and the next provider starts fresh.
 */
@Slf4j
public class FallbackChatModel implements ChatModel {

    /** A named AI provider (name used in logs only). */
    public record NamedProvider(String name, ChatModel model) {}

    private final List<NamedProvider> providers;

    public FallbackChatModel(List<NamedProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("FallbackChatModel requires at least one provider");
        }
        this.providers = List.copyOf(providers);
        log.info("AI fallback chain configured: {}",
                providers.stream().map(NamedProvider::name).toList());
    }

    // ── Blocking call ─────────────────────────────────────────────────────────

    @Override
    public ChatResponse call(Prompt prompt) {
        Exception lastException = null;
        for (NamedProvider provider : providers) {
            try {
                log.debug("AI call → provider={}", provider.name());
                ChatResponse response = provider.model().call(prompt);
                if (lastException != null) {
                    // We used a fallback — log at INFO so ops can see the provider chain in action
                    log.info("AI fallback succeeded on provider={} (primary failed: {})",
                            provider.name(), lastException.getMessage());
                }
                return response;
            } catch (Exception e) {
                log.warn("AI provider='{}' failed — trying next. reason={}",
                        provider.name(), e.getMessage());
                lastException = e;
            }
        }
        // All providers exhausted
        throw new RuntimeException(
                "All AI providers exhausted. Chain: "
                        + providers.stream().map(NamedProvider::name).toList()
                        + ". Last error: "
                        + (lastException != null ? lastException.getMessage() : "unknown"),
                lastException);
    }

    // ── Reactive streaming call ───────────────────────────────────────────────

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return streamWithFallback(prompt, 0);
    }

    private Flux<ChatResponse> streamWithFallback(Prompt prompt, int index) {
        if (index >= providers.size()) {
            return Flux.error(new RuntimeException(
                    "All AI providers exhausted for streaming. Chain: "
                            + providers.stream().map(NamedProvider::name).toList()));
        }
        NamedProvider provider = providers.get(index);
        return Flux.defer(() -> provider.model().stream(prompt))
                .onErrorResume(e -> {
                    log.warn("AI stream provider='{}' failed — trying next. reason={}",
                            provider.name(), e.getMessage());
                    return streamWithFallback(prompt, index + 1);
                });
    }

    // ── Options ───────────────────────────────────────────────────────────────

    /**
     * Returns default options from the first (primary) provider.
     * Used by {@code ChatClient.Builder} when no explicit options are set.
     */
    @Override
    public ChatOptions getDefaultOptions() {
        return providers.get(0).model().getDefaultOptions();
    }
}
