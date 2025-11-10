package com.cola.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to generate AI commit message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessageRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    // Git diff to analyze
    private GitDiffInfo diff;

    // Commit message style (conventional, semantic, descriptive, etc.)
    @Builder.Default
    private CommitStyle style = CommitStyle.CONVENTIONAL;

    // Maximum length of commit message
    @Builder.Default
    private int maxLength = 72;

    // Whether to include scope in conventional commits
    @Builder.Default
    private boolean includeScope = true;

    // Whether to include body/description
    @Builder.Default
    private boolean includeBody = true;

    // Custom context or instructions
    private String customContext;

    public enum CommitStyle {
        CONVENTIONAL,  // feat: add new feature
        SEMANTIC,      // Add new feature
        DESCRIPTIVE,   // Added authentication with JWT tokens
        GITMOJI,       // ✨ Add new feature
        ANGULAR        // feat(auth): add JWT authentication
    }
}
