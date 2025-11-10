package com.cola.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request for code completion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "File path is required")
    private String filePath;

    @NotBlank(message = "Language is required")
    private String language;

    @NotNull(message = "Cursor position is required")
    private CursorPosition cursorPosition;

    // Content before cursor
    @NotBlank(message = "Prefix is required")
    private String prefix;

    // Content after cursor
    private String suffix;

    // Enable streaming response (default: true for real-time feel)
    @Builder.Default
    private boolean streaming = true;

    // Maximum completion length in tokens
    @Builder.Default
    private int maxTokens = 256;

    // Temperature for completion (lower = more deterministic)
    @Builder.Default
    private double temperature = 0.2;

    // Enable multi-line completion
    @Builder.Default
    private boolean multiLine = true;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CursorPosition {
        private int line;
        private int column;
    }
}
