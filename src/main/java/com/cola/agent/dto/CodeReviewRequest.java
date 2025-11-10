package com.cola.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request for AI code review.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    // Git diff to review
    private GitDiffInfo diff;

    // Specific files to review (if empty, review all changes)
    private List<String> filesToReview;

    // Review focus areas
    @Builder.Default
    private List<ReviewFocus> focusAreas = List.of(
        ReviewFocus.CODE_QUALITY,
        ReviewFocus.BUGS,
        ReviewFocus.SECURITY,
        ReviewFocus.PERFORMANCE
    );

    // Review depth (QUICK, STANDARD, THOROUGH)
    @Builder.Default
    private ReviewDepth depth = ReviewDepth.STANDARD;

    // Include suggestions for improvements
    @Builder.Default
    private boolean includeSuggestions = true;

    // Include code examples in suggestions
    @Builder.Default
    private boolean includeExamples = true;

    // Project-specific guidelines or rules
    private String customGuidelines;

    public enum ReviewFocus {
        CODE_QUALITY,     // Clean code, readability, maintainability
        BUGS,             // Potential bugs and edge cases
        SECURITY,         // Security vulnerabilities
        PERFORMANCE,      // Performance issues
        TESTING,          // Test coverage and quality
        DOCUMENTATION,    // Code comments and docs
        ARCHITECTURE,     // Design patterns and architecture
        BEST_PRACTICES    // Language/framework best practices
    }

    public enum ReviewDepth {
        QUICK,      // Basic issues only (< 30s)
        STANDARD,   // Common issues (< 2min)
        THOROUGH    // Deep analysis (< 5min)
    }
}
