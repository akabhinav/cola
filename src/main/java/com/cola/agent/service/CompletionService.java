package com.cola.agent.service;

import com.cola.agent.dto.CompletionChunk;
import com.cola.agent.dto.CompletionRequest;
import com.cola.agent.dto.CompletionResponse;
import com.cola.agent.rag.model.CodeContext;
import com.cola.agent.rag.service.ContextAssemblyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for fast code completions with intelligent caching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompletionService {

    @Qualifier("fastCompletionModel")
    private final ChatModel fastCompletionModel;

    private final ContextAssemblyService contextAssemblyService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "completion:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * Generate code completion with caching.
     */
    public CompletionResponse complete(CompletionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Try cache first
            String cacheKey = generateCacheKey(request);
            String cached = getCachedCompletion(cacheKey);
            if (cached != null) {
                long latency = System.currentTimeMillis() - startTime;
                log.debug("Cache hit for completion ({}ms)", latency);
                return CompletionResponse.fromCache(cached, latency);
            }

            // Build intelligent prompt
            String prompt = buildCompletionPrompt(request);

            // Call fast model
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(COMPLETION_SYSTEM_PROMPT));
            messages.add(new UserMessage(prompt));

            ChatResponse response = fastCompletionModel.call(new Prompt(messages));
            String completion = response.getResult().getOutput().getContent();

            // Clean and format completion
            completion = cleanCompletion(completion, request);

            // Cache the result
            cacheCompletion(cacheKey, completion);

            long latency = System.currentTimeMillis() - startTime;
            int tokens = estimateTokens(completion);

            log.info("Completion generated in {}ms ({} tokens)", latency, tokens);

            return CompletionResponse.fromModel(completion, latency, tokens);

        } catch (Exception e) {
            log.error("Error generating completion", e);
            long latency = System.currentTimeMillis() - startTime;
            return CompletionResponse.builder()
                .completion("")
                .source(CompletionResponse.CompletionSource.FALLBACK)
                .latencyMs(latency)
                .build();
        }
    }

    /**
     * Stream code completion for real-time feel.
     */
    public Flux<CompletionChunk> streamCompletion(CompletionRequest request) {
        long startTime = System.currentTimeMillis();

        return Flux.create(sink -> {
            try {
                // Check cache first
                String cacheKey = generateCacheKey(request);
                String cached = getCachedCompletion(cacheKey);
                if (cached != null) {
                    // Emit cached result immediately
                    sink.next(CompletionChunk.fromString(cached));
                    sink.complete();
                    log.debug("Streamed from cache ({}ms)", System.currentTimeMillis() - startTime);
                    return;
                }

                // Build prompt
                String prompt = buildCompletionPrompt(request);

                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(COMPLETION_SYSTEM_PROMPT));
                messages.add(new UserMessage(prompt));

                // Stream from model
                AtomicInteger sequence = new AtomicInteger(0);
                AtomicLong firstChunkTime = new AtomicLong(0);
                StringBuilder fullCompletion = new StringBuilder();

                fastCompletionModel.stream(new Prompt(messages))
                    .doOnNext(response -> {
                        if (firstChunkTime.get() == 0) {
                            firstChunkTime.set(System.currentTimeMillis());
                            log.debug("First token latency: {}ms", firstChunkTime.get() - startTime);
                        }

                        String content = response.getResult().getOutput().getContent();
                        if (content != null && !content.isEmpty()) {
                            fullCompletion.append(content);
                            sink.next(CompletionChunk.of(content, sequence.getAndIncrement()));
                        }
                    })
                    .doOnComplete(() -> {
                        sink.next(CompletionChunk.complete());
                        sink.complete();

                        // Cache the full completion
                        String cleaned = cleanCompletion(fullCompletion.toString(), request);
                        cacheCompletion(cacheKey, cleaned);

                        long totalTime = System.currentTimeMillis() - startTime;
                        log.info("Completion streamed in {}ms (first token: {}ms)",
                            totalTime, firstChunkTime.get() - startTime);
                    })
                    .doOnError(error -> {
                        log.error("Error streaming completion", error);
                        sink.next(CompletionChunk.error(error.getMessage()));
                        sink.complete();
                    })
                    .subscribe();

            } catch (Exception e) {
                log.error("Error setting up completion stream", e);
                sink.next(CompletionChunk.error(e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * Build intelligent completion prompt with context.
     */
    private String buildCompletionPrompt(CompletionRequest request) {
        StringBuilder prompt = new StringBuilder();

        // Add language context
        prompt.append(String.format("Language: %s\n", request.getLanguage()));
        prompt.append(String.format("File: %s\n\n", request.getFilePath()));

        // Try to get relevant code context using RAG
        try {
            // Build a mini query from the prefix
            String contextQuery = extractContextQuery(request.getPrefix());
            CodeContext context = contextAssemblyService.buildCompletionContext(
                request.getProjectId(),
                request.getFilePath(),
                request.getPrefix(),
                request.getSuffix() != null ? request.getSuffix() : ""
            );

            // Add relevant code snippets (limited for speed)
            if (!context.getRelevantChunks().isEmpty()) {
                prompt.append("Relevant code context:\n");
                context.getRelevantChunks().stream()
                    .limit(3) // Limit to 3 chunks for speed
                    .forEach(chunk -> {
                        prompt.append(String.format("```%s\n%s\n```\n",
                            chunk.getLanguage(),
                            truncate(chunk.getContent(), 200)
                        ));
                    });
                prompt.append("\n");
            }
        } catch (Exception e) {
            log.debug("Could not fetch context for completion: {}", e.getMessage());
        }

        // Add code before cursor
        prompt.append("Code before cursor:\n");
        prompt.append("```").append(request.getLanguage()).append("\n");
        prompt.append(getLastLines(request.getPrefix(), 20)); // Last 20 lines
        prompt.append("\n```\n\n");

        // Add code after cursor if available
        if (request.getSuffix() != null && !request.getSuffix().isEmpty()) {
            prompt.append("Code after cursor:\n");
            prompt.append("```").append(request.getLanguage()).append("\n");
            prompt.append(getFirstLines(request.getSuffix(), 5)); // First 5 lines
            prompt.append("\n```\n\n");
        }

        // Add instruction
        if (request.isMultiLine()) {
            prompt.append("Complete the code with multiple lines if needed. ");
        } else {
            prompt.append("Complete the current line only. ");
        }
        prompt.append("Return ONLY the completion code, no explanations.");

        return prompt.toString();
    }

    /**
     * Clean and format completion result.
     */
    private String cleanCompletion(String completion, CompletionRequest request) {
        if (completion == null || completion.isEmpty()) {
            return "";
        }

        // Remove markdown code blocks if present
        completion = completion.replaceAll("```[a-zA-Z]*\\n", "");
        completion = completion.replaceAll("```", "");

        // Trim whitespace
        completion = completion.trim();

        // If single-line requested, take only first line
        if (!request.isMultiLine() && completion.contains("\n")) {
            completion = completion.split("\n")[0];
        }

        // Remove common artifacts
        completion = completion.replace("[COMPLETION]", "");
        completion = completion.replace("[END]", "");

        return completion;
    }

    /**
     * Extract a query from the prefix for context retrieval.
     */
    private String extractContextQuery(String prefix) {
        // Get last few lines as query
        String[] lines = prefix.split("\n");
        int start = Math.max(0, lines.length - 5);
        StringBuilder query = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            query.append(lines[i]).append(" ");
        }
        return query.toString().trim();
    }

    /**
     * Get last N lines from text.
     */
    private String getLastLines(String text, int n) {
        String[] lines = text.split("\n");
        int start = Math.max(0, lines.length - n);
        StringBuilder result = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            result.append(lines[i]);
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Get first N lines from text.
     */
    private String getFirstLines(String text, int n) {
        String[] lines = text.split("\n");
        int end = Math.min(n, lines.length);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < end; i++) {
            result.append(lines[i]);
            if (i < end - 1) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Truncate text to max characters.
     */
    private String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    /**
     * Generate cache key from request.
     */
    private String generateCacheKey(CompletionRequest request) {
        try {
            String signature = String.format("%s:%s:%s:%s:%s",
                request.getProjectId(),
                request.getFilePath(),
                request.getLanguage(),
                hashString(request.getPrefix()),
                request.getSuffix() != null ? hashString(request.getSuffix()) : "null"
            );
            return CACHE_PREFIX + hashString(signature);
        } catch (Exception e) {
            return CACHE_PREFIX + System.currentTimeMillis();
        }
    }

    /**
     * Hash a string for cache keys.
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * Get cached completion.
     */
    private String getCachedCompletion(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            return cached != null ? cached.toString() : null;
        } catch (Exception e) {
            log.debug("Cache read error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cache completion result.
     */
    private void cacheCompletion(String key, String completion) {
        try {
            redisTemplate.opsForValue().set(key, completion, CACHE_TTL);
        } catch (Exception e) {
            log.debug("Cache write error: {}", e.getMessage());
        }
    }

    /**
     * Estimate tokens (rough approximation).
     */
    private int estimateTokens(String text) {
        return text.length() / 4;
    }

    private static final String COMPLETION_SYSTEM_PROMPT = """
        You are an expert code completion engine. Your task is to predict and complete code accurately.

        Rules:
        1. Return ONLY the completion code, no explanations
        2. Match the existing code style and indentation
        3. Be concise but complete
        4. Consider the context before and after the cursor
        5. Follow language-specific conventions
        6. Don't repeat code that's already there
        7. For incomplete statements, complete them naturally
        8. For new statements, suggest logical next steps

        Your completions should feel natural and helpful to experienced developers.
        """;
}
