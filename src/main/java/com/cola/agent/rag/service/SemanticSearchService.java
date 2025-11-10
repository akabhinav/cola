package com.cola.agent.rag.service;

import com.cola.agent.rag.model.CodeChunk;
import com.cola.agent.rag.model.SearchResult;
import com.cola.agent.repository.GeneratedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for semantic code search using vector similarity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final GeneratedFileRepository fileRepository;

    @Value("${cola.rag.retrieval.top-k:10}")
    private int defaultTopK;

    @Value("${cola.rag.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${cola.rag.retrieval.reranking-enabled:true}")
    private boolean rerankingEnabled;

    /**
     * Search for relevant code chunks using natural language query.
     */
    public List<SearchResult> search(UUID projectId, String query, int topK) {
        if (!vectorStoreService.isAvailable()) {
            log.warn("Vector store is not available, returning empty results");
            return new ArrayList<>();
        }

        try {
            log.debug("Searching for: '{}' in project {}", query, projectId);

            // Generate query embedding
            float[] queryEmbedding = embeddingService.embed(query);

            // Search vector store (get more results for reranking)
            int searchK = rerankingEnabled ? topK * 2 : topK;
            List<SearchResult> results = vectorStoreService.search(queryEmbedding, searchK, projectId);

            // Load full content for chunks
            results = loadFullContent(results);

            // Filter by similarity threshold
            results = results.stream()
                .filter(r -> r.getSimilarityScore() >= similarityThreshold)
                .collect(Collectors.toList());

            // Rerank results if enabled
            if (rerankingEnabled && results.size() > topK) {
                results = rerank(results, query);
            }

            // Limit to topK
            results = results.stream()
                .limit(topK)
                .collect(Collectors.toList());

            log.debug("Found {} relevant chunks", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error searching for query: {}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * Search with default topK.
     */
    public List<SearchResult> search(UUID projectId, String query) {
        return search(projectId, query, defaultTopK);
    }

    /**
     * Find similar code chunks to a given chunk.
     */
    public List<SearchResult> findSimilar(CodeChunk chunk, int topK) {
        if (chunk.getEmbedding() == null) {
            return new ArrayList<>();
        }

        try {
            List<SearchResult> results = vectorStoreService.search(
                chunk.getEmbedding(),
                topK + 1, // +1 to exclude the chunk itself
                chunk.getProjectId()
            );

            // Remove the query chunk itself
            results = results.stream()
                .filter(r -> !r.getChunk().getUniqueId().equals(chunk.getUniqueId()))
                .limit(topK)
                .collect(Collectors.toList());

            return loadFullContent(results);

        } catch (Exception e) {
            log.error("Error finding similar chunks", e);
            return new ArrayList<>();
        }
    }

    /**
     * Rerank search results using hybrid approach.
     */
    private List<SearchResult> rerank(List<SearchResult> results, String query) {
        String lowerQuery = query.toLowerCase();
        Set<String> queryTokens = new HashSet<>(Arrays.asList(lowerQuery.split("\\s+")));

        for (SearchResult result : results) {
            double score = result.getSimilarityScore();

            // Boost for exact keyword matches
            String content = result.getChunk().getContent().toLowerCase();
            long matchCount = queryTokens.stream()
                .filter(content::contains)
                .count();
            double keywordBoost = 1.0 + (matchCount * 0.05); // 5% boost per keyword

            // Boost for recent files
            double recencyBoost = calculateRecencyBoost(result.getChunk());

            // Boost for important chunk types
            double typeBoost = calculateTypeBoost(result.getChunk());

            // Combined score
            double rerankedScore = score * keywordBoost * recencyBoost * typeBoost;
            result.setRerankedScore(rerankedScore);

            log.trace("Reranked {} from {:.3f} to {:.3f}",
                result.getChunk().getFilePath(), score, rerankedScore);
        }

        // Sort by reranked score
        results.sort(Comparator.comparingDouble(SearchResult::getRerankedScore).reversed());

        return results;
    }

    /**
     * Calculate recency boost (favor recently modified files).
     */
    private double calculateRecencyBoost(CodeChunk chunk) {
        if (chunk.getTimestamp() == null) {
            return 1.0;
        }

        long ageMs = System.currentTimeMillis() - chunk.getTimestamp();
        long ageDays = ageMs / (24 * 60 * 60 * 1000);

        if (ageDays < 1) return 1.2;
        if (ageDays < 7) return 1.1;
        if (ageDays < 30) return 1.05;
        return 1.0;
    }

    /**
     * Calculate type boost (favor certain chunk types).
     */
    private double calculateTypeBoost(CodeChunk chunk) {
        return switch (chunk.getType()) {
            case FUNCTION, METHOD -> 1.2; // Functions are often most relevant
            case CLASS -> 1.1;
            case INTERFACE -> 1.05;
            default -> 1.0;
        };
    }

    /**
     * Load full content for search results.
     */
    private List<SearchResult> loadFullContent(List<SearchResult> results) {
        for (SearchResult result : results) {
            try {
                CodeChunk chunk = result.getChunk();
                if (chunk.getContent() == null || chunk.getContent().isEmpty()) {
                    // Load from database
                    var file = fileRepository.findByProjectIdAndFilePath(
                        chunk.getProjectId(),
                        chunk.getFilePath()
                    );

                    file.ifPresent(generatedFile -> {
                        // Extract the relevant portion
                        String[] lines = generatedFile.getContent().split("\n");
                        int start = Math.max(0, chunk.getStartLine() - 1);
                        int end = Math.min(lines.length, chunk.getEndLine());

                        StringBuilder content = new StringBuilder();
                        for (int i = start; i < end; i++) {
                            content.append(lines[i]).append("\n");
                        }

                        chunk.setContent(content.toString());
                    });
                }
            } catch (Exception e) {
                log.error("Error loading content for chunk", e);
            }
        }

        return results;
    }

    /**
     * Search by file path pattern.
     */
    public List<SearchResult> searchByPath(UUID projectId, String pathPattern) {
        // This would require additional indexing or database query
        // For now, return empty list
        return new ArrayList<>();
    }

    /**
     * Check if a project is indexed.
     */
    public boolean isProjectIndexed(UUID projectId) {
        return vectorStoreService.isAvailable();
    }
}
