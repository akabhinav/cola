package com.cola.agent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a semantically meaningful chunk of code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunk {

    private String id;
    private UUID projectId;
    private String filePath;
    private String content;
    private ChunkType type;
    private String language;

    // Metadata
    private Integer startLine;
    private Integer endLine;
    private String name; // Function/class name
    private String docstring;
    private List<String> symbols; // Variables, functions referenced
    private List<String> dependencies; // Imports, dependencies
    private Map<String, Object> metadata;

    // Embedding
    private float[] embedding;
    private Long timestamp;

    public enum ChunkType {
        FUNCTION,
        CLASS,
        METHOD,
        IMPORT,
        INTERFACE,
        ENUM,
        CONSTANT,
        TYPE_DEFINITION,
        COMMENT_BLOCK,
        FULL_FILE
    }

    /**
     * Get a unique identifier for this chunk.
     */
    public String getUniqueId() {
        if (id != null) {
            return id;
        }
        return String.format("%s:%s:%d-%d",
            projectId,
            filePath,
            startLine != null ? startLine : 0,
            endLine != null ? endLine : 0
        );
    }

    /**
     * Get token estimate for this chunk.
     */
    public int getEstimatedTokens() {
        // Rough estimate: 1 token ≈ 4 characters
        return content != null ? content.length() / 4 : 0;
    }
}
