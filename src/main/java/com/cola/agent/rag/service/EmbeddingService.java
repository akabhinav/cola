package com.cola.agent.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating embeddings from text using OpenAI.
 */
@Service
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Value("${cola.rag.embeddings.dimension:1536}")
    private int embeddingDimension;

    @Value("${cola.rag.embeddings.batch-size:100}")
    private int batchSize;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Generate embedding for a single text with caching.
     */
    @Cacheable(value = "embeddings", key = "#text.hashCode()")
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[embeddingDimension];
        }

        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse response = embeddingModel.call(request);

            if (response.getResults().isEmpty()) {
                log.warn("No embedding returned for text");
                return new float[embeddingDimension];
            }

            List<Double> embedding = response.getResult().getOutput();
            return convertToFloatArray(embedding);

        } catch (Exception e) {
            log.error("Error generating embedding", e);
            return new float[embeddingDimension];
        }
    }

    /**
     * Generate embeddings for multiple texts in batches.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        log.info("Generating embeddings for {} texts in batches of {}", texts.size(), batchSize);

        List<float[]> allEmbeddings = new ArrayList<>();

        // Process in batches
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            log.debug("Processing batch {}-{}", i, end);

            try {
                EmbeddingRequest request = new EmbeddingRequest(batch, null);
                EmbeddingResponse response = embeddingModel.call(request);

                List<float[]> batchEmbeddings = response.getResults().stream()
                    .map(result -> convertToFloatArray(result.getOutput()))
                    .collect(Collectors.toList());

                allEmbeddings.addAll(batchEmbeddings);

                // Rate limiting - sleep between batches
                if (end < texts.size()) {
                    Thread.sleep(100); // 100ms delay between batches
                }

            } catch (Exception e) {
                log.error("Error generating batch embeddings for batch {}-{}", i, end, e);
                // Add zero embeddings for failed batch
                for (int j = 0; j < batch.size(); j++) {
                    allEmbeddings.add(new float[embeddingDimension]);
                }
            }
        }

        return allEmbeddings;
    }

    /**
     * Calculate cosine similarity between two embeddings.
     */
    public double cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have the same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Generate a cache key for text.
     */
    public String generateCacheKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toString(text.hashCode());
        }
    }

    /**
     * Convert List<Double> to float[] for storage efficiency.
     */
    private float[] convertToFloatArray(List<Double> embedding) {
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }

    /**
     * Get embedding dimension.
     */
    public int getDimension() {
        return embeddingDimension;
    }
}
