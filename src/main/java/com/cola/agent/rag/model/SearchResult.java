package com.cola.agent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a search result from vector similarity search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private CodeChunk chunk;
    private double similarityScore;
    private double rerankedScore;
    private String relevanceExplanation;

    public SearchResult(CodeChunk chunk, double similarityScore) {
        this.chunk = chunk;
        this.similarityScore = similarityScore;
        this.rerankedScore = similarityScore;
    }
}
