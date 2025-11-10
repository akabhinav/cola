package com.cola.agent.rag.service;

import com.cola.agent.model.GeneratedFile;
import com.cola.agent.rag.model.CodeChunk;
import com.cola.agent.repository.GeneratedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for indexing code projects for RAG.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeIndexingService {

    private final CodeChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final GeneratedFileRepository generatedFileRepository;

    @Value("${cola.storage.local.base-path:./cola-projects}")
    private String projectsBasePath;

    @Value("${cola.rag.indexing.batch-size:50}")
    private int indexingBatchSize;

    /**
     * Index an entire project asynchronously.
     */
    @Async("aiTaskExecutor")
    public CompletableFuture<IndexingResult> indexProject(UUID projectId) {
        log.info("Starting indexing for project {}", projectId);
        long startTime = System.currentTimeMillis();

        try {
            // Get all files for the project
            List<GeneratedFile> files = generatedFileRepository.findByProjectIdOrderByFilePathAsc(projectId);

            if (files.isEmpty()) {
                log.warn("No files found for project {}", projectId);
                return CompletableFuture.completedFuture(
                    IndexingResult.builder()
                        .projectId(projectId)
                        .success(true)
                        .filesProcessed(0)
                        .chunksCreated(0)
                        .message("No files to index")
                        .build()
                );
            }

            // Process files and create chunks
            List<CodeChunk> allChunks = new ArrayList<>();
            for (GeneratedFile file : files) {
                try {
                    String language = determineLanguage(file.getFilePath(), file.getLanguage());
                    List<CodeChunk> chunks = chunkingService.chunkFile(
                        projectId,
                        file.getFilePath(),
                        file.getContent(),
                        language
                    );
                    allChunks.addAll(chunks);
                } catch (Exception e) {
                    log.error("Error chunking file: {}", file.getFilePath(), e);
                }
            }

            log.info("Created {} chunks from {} files", allChunks.size(), files.size());

            // Generate embeddings in batches
            int totalChunks = allChunks.size();
            for (int i = 0; i < totalChunks; i += indexingBatchSize) {
                int end = Math.min(i + indexingBatchSize, totalChunks);
                List<CodeChunk> batch = allChunks.subList(i, end);

                // Extract content for embedding
                List<String> contents = batch.stream()
                    .map(CodeChunk::getContent)
                    .collect(Collectors.toList());

                // Generate embeddings
                List<float[]> embeddings = embeddingService.embedBatch(contents);

                // Assign embeddings to chunks
                for (int j = 0; j < batch.size(); j++) {
                    batch.get(j).setEmbedding(embeddings.get(j));
                }

                // Upsert to vector store
                vectorStoreService.upsertBatch(batch);

                log.info("Indexed batch {}-{} of {}", i, end, totalChunks);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully indexed project {} in {}ms ({} files, {} chunks)",
                projectId, duration, files.size(), allChunks.size());

            return CompletableFuture.completedFuture(
                IndexingResult.builder()
                    .projectId(projectId)
                    .success(true)
                    .filesProcessed(files.size())
                    .chunksCreated(allChunks.size())
                    .durationMs(duration)
                    .message("Indexing completed successfully")
                    .build()
            );

        } catch (Exception e) {
            log.error("Error indexing project {}", projectId, e);
            return CompletableFuture.completedFuture(
                IndexingResult.builder()
                    .projectId(projectId)
                    .success(false)
                    .message("Indexing failed: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Index a single file.
     */
    public void indexFile(UUID projectId, String filePath, String content, String language) {
        try {
            log.debug("Indexing file: {}", filePath);

            // Chunk the file
            List<CodeChunk> chunks = chunkingService.chunkFile(projectId, filePath, content, language);

            if (chunks.isEmpty()) {
                return;
            }

            // Generate embeddings
            List<String> contents = chunks.stream()
                .map(CodeChunk::getContent)
                .collect(Collectors.toList());

            List<float[]> embeddings = embeddingService.embedBatch(contents);

            // Assign embeddings
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setEmbedding(embeddings.get(i));
            }

            // Upsert to vector store
            vectorStoreService.upsertBatch(chunks);

            log.debug("Successfully indexed file: {} ({} chunks)", filePath, chunks.size());

        } catch (Exception e) {
            log.error("Error indexing file: {}", filePath, e);
        }
    }

    /**
     * Re-index a specific file (when it's updated).
     */
    public void reindexFile(UUID projectId, String filePath) {
        try {
            GeneratedFile file = generatedFileRepository
                .findByProjectIdAndFilePath(projectId, filePath)
                .orElse(null);

            if (file == null) {
                log.warn("File not found for reindexing: {}", filePath);
                return;
            }

            // Delete old chunks for this file
            // (In production, you'd want to query and delete specific vectors)

            // Index the updated file
            String language = determineLanguage(file.getFilePath(), file.getLanguage());
            indexFile(projectId, file.getFilePath(), file.getContent(), language);

            log.info("Successfully reindexed file: {}", filePath);

        } catch (Exception e) {
            log.error("Error reindexing file: {}", filePath, e);
        }
    }

    /**
     * Delete index for a project.
     */
    public void deleteProjectIndex(UUID projectId) {
        try {
            vectorStoreService.deleteByProject(projectId);
            log.info("Deleted index for project {}", projectId);
        } catch (Exception e) {
            log.error("Error deleting index for project {}", projectId, e);
        }
    }

    /**
     * Get indexing statistics.
     */
    public IndexingStats getIndexingStats(UUID projectId) {
        try {
            long fileCount = generatedFileRepository.countByProjectId(projectId);
            var vectorStats = vectorStoreService.getIndexStats();

            return IndexingStats.builder()
                .projectId(projectId)
                .totalFiles(fileCount)
                .totalVectors((Long) vectorStats.getOrDefault("totalVectorCount", 0L))
                .indexed(fileCount > 0)
                .build();

        } catch (Exception e) {
            log.error("Error getting indexing stats", e);
            return IndexingStats.builder()
                .projectId(projectId)
                .indexed(false)
                .build();
        }
    }

    /**
     * Determine language from file path and metadata.
     */
    private String determineLanguage(String filePath, String metadataLanguage) {
        if (metadataLanguage != null && !metadataLanguage.isEmpty()) {
            return metadataLanguage;
        }

        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();

        return switch (extension) {
            case "java" -> "java";
            case "kt", "kts" -> "kotlin";
            case "py" -> "python";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "go" -> "go";
            case "rs" -> "rust";
            case "cpp", "cc", "cxx" -> "cpp";
            case "c" -> "c";
            case "cs" -> "csharp";
            case "rb" -> "ruby";
            case "php" -> "php";
            case "swift" -> "swift";
            default -> "text";
        };
    }

    /**
     * Indexing result DTO.
     */
    @lombok.Data
    @lombok.Builder
    public static class IndexingResult {
        private UUID projectId;
        private boolean success;
        private int filesProcessed;
        private int chunksCreated;
        private long durationMs;
        private String message;
    }

    /**
     * Indexing statistics DTO.
     */
    @lombok.Data
    @lombok.Builder
    public static class IndexingStats {
        private UUID projectId;
        private long totalFiles;
        private long totalVectors;
        private boolean indexed;
    }
}
