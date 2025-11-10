package com.cola.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to convert natural language to shell command.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaturalLanguageRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "Natural language query is required")
    private String query;

    // Current shell type (bash, zsh, powershell, etc.)
    @Builder.Default
    private String shell = "bash";

    // Operating system context
    @Builder.Default
    private String os = "linux";

    // Current working directory for context
    private String workingDirectory;

    // Include examples in response
    @Builder.Default
    private boolean includeExamples = true;
}
