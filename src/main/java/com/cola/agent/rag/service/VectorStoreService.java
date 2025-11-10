package com.cola.agent.rag.service;

import com.cola.agent.rag.model.CodeChunk;
import com.cola.agent.rag.model.SearchResult;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_control.client.model.IndexModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for interacting with Pinecone vector database.
 */
@Service
@Slf4j
public class VectorStoreService {

    private Pinecone pinecone;
    private Index index;

    @Value("${cola.rag.vector-db.pinecone.api-key:}")
    private String apiKey;

    @Value("${cola.rag.vector-db.pinecone.environment:us-west1-gcp}")
    private String environment;

    @Value("${cola.rag.vector-db.pinecone.index-name:cola-code-index}")
    private String indexName;

    @Value("${cola.rag.vector-db.pinecone.dimension:1536}")
    private int dimension;

    @Value("${cola.rag.enabled:true}")
    private boolean ragEnabled;

    @PostConstruct
    public void initialize() {
        if (!ragEnabled || apiKey == null || apiKey.isEmpty()) {
            log.warn("RAG is disabled or Pinecone API key is not configured");
            return;
        }

        try {
            log.info("Initializing Pinecone client");
            pinecone = new Pinecone.Builder(apiKey).build();

            // Check if index exists, create if not
            ensureIndexExists();

            // Get index connection
            index = pinecone.getIndexConnection(indexName);
            log.info("Successfully connected to Pinecone index: {}", indexName);

        } catch (Exception e) {
            log.error("Failed to initialize Pinecone", e);
        }
    }

    /**
     * Ensure the Pinecone index exists, create if not.
     */
    private void ensureIndexExists() {
        try {
            List<IndexModel> indexes = pinecone.listIndexes().getIndexes();
            boolean exists = indexes.stream()
                .anyMatch(idx -> idx.getName().equals(indexName));

            if (!exists) {
                log.info("Creating Pinecone index: {}", indexName);
                pinecone.createServerlessIndex(
                    indexName,
                    "cosine", // similarity metric
                    dimension,
                    environment,
                    "aws"
                );
                log.info("Pinecone index created successfully");

                // Wait for index to be ready
                Thread.sleep(10000);
            }
        } catch (Exception e) {
            log.warn("Could not ensure index exists: {}", e.getMessage());
        }
    }

    /**
     * Upsert a code chunk into the vector database.
     */
    public void upsert(CodeChunk chunk) {
        if (index == null || chunk.getEmbedding() == null) {
            return;
        }

        try {
            Map<String, Object> metadata = buildMetadata(chunk);

            String id = chunk.getUniqueId();
            List<Float> embeddingList = Arrays.stream(chunk.getEmbedding())
                .boxed()
                .collect(Collectors.toList());

            index.upsert(id, embeddingList, null, null, metadata);

            log.debug("Upserted chunk {} to Pinecone", id);

        } catch (Exception e) {
            log.error("Error upserting chunk to Pinecone", e);
        }
    }

    /**
     * Upsert multiple code chunks in batch.
     */
    public void upsertBatch(List<CodeChunk> chunks) {
        if (index == null || chunks.isEmpty()) {
            return;
        }

        log.info("Upserting {} chunks to Pinecone", chunks.size());

        // Process in batches of 100 (Pinecone limit)
        int batchSize = 100;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<CodeChunk> batch = chunks.subList(i, end);

            try {
                for (CodeChunk chunk : batch) {
                    if (chunk.getEmbedding() != null) {
                        upsert(chunk);
                    }
                }

                log.debug("Upserted batch {}-{}", i, end);

            } catch (Exception e) {
                log.error("Error upserting batch to Pinecone", e);
            }
        }

        log.info("Successfully upserted {} chunks", chunks.size());
    }

    /**
     * Search for similar code chunks.
     */
    public List<SearchResult> search(float[] queryEmbedding, int topK, UUID projectId) {
        if (index == null || queryEmbedding == null) {
            return new ArrayList<>();
        }

        try {
            List<Float> embeddingList = Arrays.stream(queryEmbedding)
                .boxed()
                .collect(Collectors.toList());

            // Build filter for project
            Map<String, Object> filter = new HashMap<>();
            if (projectId != null) {
                filter.put("projectId", projectId.toString());
            }

            // Query Pinecone
            QueryResponseWithUnsignedIndices response = index.query(
                topK,
                embeddingList,
                null,
                null,
                null,
                null,
                filter,
                true,
                true
            );

            // Convert results to SearchResult objects
            return response.getMatches().stream()
                .map(this::convertToSearchResult)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching Pinecone", e);
            return new ArrayList<>();
        }
    }

    /**
     * Delete vectors for a project.
     */
    public void deleteByProject(UUID projectId) {
        if (index == null) {
            return;
        }

        try {
            Map<String, Object> filter = new HashMap<>();
            filter.put("projectId", projectId.toString());

            index.deleteByFilter(filter);
            log.info("Deleted vectors for project {}", projectId);

        } catch (Exception e) {
            log.error("Error deleting vectors from Pinecone", e);
        }
    }

    /**
     * Get statistics about the index.
     */
    public Map<String, Object> getIndexStats() {
        if (index == null) {
            return new HashMap<>();
        }

        try {
            var stats = index.describeIndexStats();
            Map<String, Object> result = new HashMap<>();
            result.put("totalVectorCount", stats.getTotalVectorCount());
            result.put("dimension", stats.getDimension());
            return result;
        } catch (Exception e) {
            log.error("Error getting index stats", e);
            return new HashMap<>();
        }
    }

    /**
     * Build metadata map from code chunk.
     */
    private Map<String, Object> buildMetadata(CodeChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("projectId", chunk.getProjectId().toString());
        metadata.put("filePath", chunk.getFilePath());
        metadata.put("language", chunk.getLanguage());
        metadata.put("type", chunk.getType().name());

        if (chunk.getStartLine() != null) {
            metadata.put("startLine", chunk.getStartLine());
        }
        if (chunk.getEndLine() != null) {
            metadata.put("endLine", chunk.getEndLine());
        }
        if (chunk.getName() != null) {
            metadata.put("name", chunk.getName());
        }
        if (chunk.getTimestamp() != null) {
            metadata.put("timestamp", chunk.getTimestamp());
        }

        return metadata;
    }

    /**
     * Convert Pinecone result to SearchResult.
     */
    private SearchResult convertToSearchResult(ScoredVectorWithUnsignedIndices match) {
        Map<String, Object> metadata = match.getMetadata();

        CodeChunk chunk = CodeChunk.builder()
            .id(match.getId())
            .projectId(UUID.fromString((String) metadata.get("projectId")))
            .filePath((String) metadata.get("filePath"))
            .language((String) metadata.get("language"))
            .type(CodeChunk.ChunkType.valueOf((String) metadata.get("type")))
            .startLine(metadata.get("startLine") != null ?
                ((Number) metadata.get("startLine")).intValue() : null)
            .endLine(metadata.get("endLine") != null ?
                ((Number) metadata.get("endLine")).intValue() : null)
            .name((String) metadata.get("name"))
            .build();

        return new SearchResult(chunk, match.getScore());
    }

    /**
     * Check if vector store is available.
     */
    public boolean isAvailable() {
        return ragEnabled && index != null;
    }
}
