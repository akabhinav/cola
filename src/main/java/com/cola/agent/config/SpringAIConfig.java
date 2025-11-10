package com.cola.agent.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI configuration for Claude (Anthropic) and OpenAI integration.
 */
@Configuration
public class SpringAIConfig {

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${cola.agent.primary-provider:anthropic}")
    private String primaryProvider;

    /**
     * Configure Anthropic Claude chat model.
     */
    @Bean
    @Primary
    public ChatModel anthropicChatModel() {
        if (anthropicApiKey == null || anthropicApiKey.isEmpty()) {
            throw new IllegalStateException("Anthropic API key is not configured. " +
                "Please set ANTHROPIC_API_KEY environment variable.");
        }

        var anthropicApi = new AnthropicApi(anthropicApiKey);

        var options = AnthropicChatOptions.builder()
            .withModel("claude-3-5-sonnet-20241022")
            .withTemperature(0.7)
            .withMaxTokens(4096)
            .withTopP(1.0)
            .build();

        return new AnthropicChatModel(anthropicApi, options);
    }

    /**
     * Configure OpenAI GPT chat model as fallback.
     */
    @Bean
    public ChatModel openaiChatModel() {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            // OpenAI is optional, can be used as fallback
            return null;
        }

        var openAiApi = new OpenAiApi(openaiApiKey);

        var options = OpenAiChatOptions.builder()
            .withModel("gpt-4-turbo-preview")
            .withTemperature(0.7)
            .withMaxTokens(4096)
            .build();

        return new OpenAiChatModel(openAiApi, options);
    }

    /**
     * Configure chat model for code generation with lower temperature for consistency.
     */
    @Bean
    public ChatModel codeGenerationChatModel() {
        if (anthropicApiKey == null || anthropicApiKey.isEmpty()) {
            throw new IllegalStateException("Anthropic API key is not configured.");
        }

        var anthropicApi = new AnthropicApi(anthropicApiKey);

        var options = AnthropicChatOptions.builder()
            .withModel("claude-3-5-sonnet-20241022")
            .withTemperature(0.3)  // Lower temperature for more consistent code
            .withMaxTokens(8192)    // More tokens for longer code
            .withTopP(0.95)
            .build();

        return new AnthropicChatModel(anthropicApi, options);
    }

    /**
     * Configure fast completion model using Claude Haiku for real-time inline completions.
     * Optimized for speed (< 200ms target) with minimal tokens.
     */
    @Bean
    public ChatModel fastCompletionModel() {
        if (anthropicApiKey == null || anthropicApiKey.isEmpty()) {
            throw new IllegalStateException("Anthropic API key is not configured.");
        }

        var anthropicApi = new AnthropicApi(anthropicApiKey);

        var options = AnthropicChatOptions.builder()
            .withModel("claude-3-haiku-20240307")  // Fastest model
            .withTemperature(0.2)  // Low temperature for predictable completions
            .withMaxTokens(512)     // Smaller token limit for speed
            .withTopP(0.95)
            .build();

        return new AnthropicChatModel(anthropicApi, options);
    }
}
