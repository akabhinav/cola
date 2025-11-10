package com.cola.agent.rag.service;

import com.cola.agent.model.Conversation;
import com.cola.agent.rag.model.CodeChunk;
import com.cola.agent.rag.model.CodeContext;
import com.cola.agent.rag.model.SearchResult;
import com.cola.agent.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for assembling optimal context for LLM calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextAssemblyService {

    private final SemanticSearchService semanticSearchService;
    private final ConversationRepository conversationRepository;

    @Value("${cola.rag.retrieval.max-context-tokens:80000}")
    private int maxContextTokens;

    @Value("${cola.agent.max-context-tokens:128000}")
    private int totalMaxTokens;

    private static final int RESERVED_RESPONSE_TOKENS = 4096;
    private static final int RESERVED_CONVERSATION_TOKENS = 4000;

    /**
     * Build comprehensive context for a query.
     */
    public CodeContext buildContext(UUID projectId, String query) {
        log.debug("Building context for query in project {}", projectId);

        int availableTokens = maxContextTokens;

        // 1. Get recent conversation history (high priority)
        List<Conversation> conversationHistory = getRecentConversation(projectId);
        int conversationTokens = estimateTokens(conversationHistory);
        availableTokens -= conversationTokens;

        log.debug("Conversation history: {} messages, ~{} tokens",
            conversationHistory.size(), conversationTokens);

        // 2. Search for relevant code chunks
        List<SearchResult> searchResults = semanticSearchService.search(projectId, query, 20);

        // 3. Select and prioritize chunks within token budget
        List<CodeChunk> selectedChunks = selectChunks(searchResults, availableTokens);

        // 4. Calculate total tokens
        int chunksTokens = selectedChunks.stream()
            .mapToInt(CodeChunk::getEstimatedTokens)
            .sum();

        int totalTokens = conversationTokens + chunksTokens;

        log.info("Context assembled: {} conversation messages, {} code chunks, ~{} total tokens",
            conversationHistory.size(), selectedChunks.size(), totalTokens);

        return CodeContext.builder()
            .projectId(projectId)
            .query(query)
            .conversationHistory(conversationHistory)
            .relevantChunks(selectedChunks)
            .totalTokens(totalTokens)
            .maxContextTokens(maxContextTokens)
            .build();
    }

    /**
     * Build context for code completion (focused on current file).
     */
    public CodeContext buildCompletionContext(
        UUID projectId,
        String filePath,
        String prefix,
        String suffix
    ) {
        log.debug("Building completion context for file: {}", filePath);

        int availableTokens = maxContextTokens;

        // Build context-aware query from surrounding code
        String query = buildCompletionQuery(prefix, suffix);

        // Search for relevant code
        List<SearchResult> searchResults = semanticSearchService.search(projectId, query, 10);

        // Prioritize chunks from same file
        searchResults = prioritizeSameFile(searchResults, filePath);

        // Select chunks
        List<CodeChunk> selectedChunks = selectChunks(searchResults, availableTokens);

        int totalTokens = selectedChunks.stream()
            .mapToInt(CodeChunk::getEstimatedTokens)
            .sum();

        log.debug("Completion context: {} chunks, ~{} tokens", selectedChunks.size(), totalTokens);

        return CodeContext.builder()
            .projectId(projectId)
            .query(query)
            .relevantChunks(selectedChunks)
            .totalTokens(totalTokens)
            .maxContextTokens(maxContextTokens)
            .projectMetadata(Map.of(
                "currentFile", filePath,
                "prefix", truncate(prefix, 500),
                "suffix", truncate(suffix, 500)
            ))
            .build();
    }

    /**
     * Select chunks that fit within token budget.
     */
    private List<CodeChunk> selectChunks(List<SearchResult> searchResults, int availableTokens) {
        List<CodeChunk> selected = new ArrayList<>();
        int usedTokens = 0;

        for (SearchResult result : searchResults) {
            CodeChunk chunk = result.getChunk();
            int chunkTokens = chunk.getEstimatedTokens();

            if (usedTokens + chunkTokens <= availableTokens) {
                selected.add(chunk);
                usedTokens += chunkTokens;
            } else {
                // Try to fit a truncated version
                int remainingTokens = availableTokens - usedTokens;
                if (remainingTokens > 100) { // Minimum useful chunk
                    selected.add(truncateChunk(chunk, remainingTokens));
                    break;
                }
            }
        }

        return selected;
    }

    /**
     * Get recent conversation history.
     */
    private List<Conversation> getRecentConversation(UUID projectId) {
        List<Conversation> all = conversationRepository.findByProjectIdOrderByTimestampAsc(projectId);

        // Keep last N messages that fit in token budget
        int tokenBudget = RESERVED_CONVERSATION_TOKENS;
        List<Conversation> recent = new ArrayList<>();

        for (int i = all.size() - 1; i >= 0 && tokenBudget > 0; i--) {
            Conversation conv = all.get(i);
            int tokens = estimateTokens(conv.getMessage());

            if (tokens <= tokenBudget) {
                recent.add(0, conv); // Add at beginning to maintain order
                tokenBudget -= tokens;
            } else {
                break;
            }
        }

        return recent;
    }

    /**
     * Build query from code completion context.
     */
    private String buildCompletionQuery(String prefix, String suffix) {
        // Extract last few lines of prefix and first few of suffix
        String[] prefixLines = prefix.split("\n");
        String[] suffixLines = suffix.split("\n");

        int contextLines = 5;
        StringBuilder query = new StringBuilder();

        // Last N lines of prefix
        int prefixStart = Math.max(0, prefixLines.length - contextLines);
        for (int i = prefixStart; i < prefixLines.length; i++) {
            query.append(prefixLines[i]).append("\n");
        }

        query.append("\n[CURSOR]\n\n");

        // First N lines of suffix
        int suffixEnd = Math.min(contextLines, suffixLines.length);
        for (int i = 0; i < suffixEnd; i++) {
            query.append(suffixLines[i]).append("\n");
        }

        return query.toString();
    }

    /**
     * Prioritize search results from the same file.
     */
    private List<SearchResult> prioritizeSameFile(List<SearchResult> results, String filePath) {
        return results.stream()
            .sorted((a, b) -> {
                boolean aIsSameFile = a.getChunk().getFilePath().equals(filePath);
                boolean bIsSameFile = b.getChunk().getFilePath().equals(filePath);

                if (aIsSameFile && !bIsSameFile) return -1;
                if (!aIsSameFile && bIsSameFile) return 1;

                // If both same or both different, use reranked score
                return Double.compare(b.getRerankedScore(), a.getRerankedScore());
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Truncate a chunk to fit token budget.
     */
    private CodeChunk truncateChunk(CodeChunk chunk, int maxTokens) {
        int maxChars = maxTokens * 4; // Rough estimate
        String truncated = chunk.getContent();

        if (truncated.length() > maxChars) {
            truncated = truncated.substring(0, maxChars) + "\n... (truncated)";
        }

        return CodeChunk.builder()
            .projectId(chunk.getProjectId())
            .filePath(chunk.getFilePath())
            .content(truncated)
            .type(chunk.getType())
            .language(chunk.getLanguage())
            .name(chunk.getName())
            .startLine(chunk.getStartLine())
            .build();
    }

    /**
     * Estimate tokens for conversation messages.
     */
    private int estimateTokens(List<Conversation> conversations) {
        return conversations.stream()
            .mapToInt(c -> estimateTokens(c.getMessage()))
            .sum();
    }

    /**
     * Estimate tokens for text (rough approximation: 1 token ≈ 4 characters).
     */
    private int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0;
    }

    /**
     * Truncate text to max characters.
     */
    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}
