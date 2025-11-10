package com.cola.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request to generate PR description.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PRDescriptionRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "Branch name is required")
    private String branch;

    // Base branch (usually main/master)
    @Builder.Default
    private String baseBranch = "main";

    // Commit messages in the PR
    private List<String> commitMessages;

    // Git diff for the PR
    private GitDiffInfo diff;

    // PR title (optional, will be generated if not provided)
    private String title;

    // Template style (STANDARD, DETAILED, MINIMAL, JIRA)
    @Builder.Default
    private TemplateStyle templateStyle = TemplateStyle.STANDARD;

    // Include test plan section
    @Builder.Default
    private boolean includeTestPlan = true;

    // Include checklist
    @Builder.Default
    private boolean includeChecklist = true;

    // Related issue/ticket numbers
    private List<String> relatedIssues;

    // Custom context
    private String customContext;

    public enum TemplateStyle {
        STANDARD,   // What/Why/How format
        DETAILED,   // Comprehensive with all sections
        MINIMAL,    // Brief description only
        JIRA,       // JIRA-compatible format
        GITHUB      // GitHub best practices
    }
}
