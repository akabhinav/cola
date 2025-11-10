package com.cola.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Request to execute a shell command.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "Command is required")
    private String command;

    // Working directory (relative to project root)
    private String workingDirectory;

    // Environment variables
    private Map<String, String> environment;

    // Timeout in milliseconds (default: 30 seconds)
    @Builder.Default
    private long timeoutMs = 30000;

    // Whether to capture output in real-time
    @Builder.Default
    private boolean streaming = true;

    // Session ID for maintaining terminal state
    private String sessionId;
}
