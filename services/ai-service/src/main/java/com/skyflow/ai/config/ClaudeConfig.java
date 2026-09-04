package com.skyflow.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class ClaudeConfig {

    private static final Logger log = LoggerFactory.getLogger(ClaudeConfig.class);

    /**
     * Wrapped in an {@link Optional} so the service still starts without a key: the AI endpoints
     * report that the assistant is unavailable while everything else keeps working.
     */
    @Bean
    public Optional<AnthropicClient> anthropicClient(ClaudeProperties properties) {
        if (!properties.isConfigured()) {
            log.warn("ANTHROPIC_API_KEY is not set; the AI endpoints will report 502 until it is");
            return Optional.empty();
        }
        return Optional.of(AnthropicOkHttpClient.builder()
                .apiKey(properties.getApiKey())
                .build());
    }
}
