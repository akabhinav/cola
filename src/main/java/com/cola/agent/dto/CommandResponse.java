package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from command execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResponse {

    // Command that was executed
    private String command;

    // Standard output
    private String stdout;

    // Standard error
    private String stderr;

    // Exit code
    private int exitCode;

    // Execution time in milliseconds
    private long executionTimeMs;

    // Whether the command succeeded
    private boolean success;

    // Session ID
    private String sessionId;

    // Error message if command failed
    private String errorMessage;

    /**
     * Create a success response.
     */
    public static CommandResponse success(String command, String stdout, long executionTime, String sessionId) {
        return CommandResponse.builder()
            .command(command)
            .stdout(stdout)
            .stderr("")
            .exitCode(0)
            .executionTimeMs(executionTime)
            .success(true)
            .sessionId(sessionId)
            .build();
    }

    /**
     * Create an error response.
     */
    public static CommandResponse error(String command, String errorMessage, int exitCode, String sessionId) {
        return CommandResponse.builder()
            .command(command)
            .stdout("")
            .stderr(errorMessage)
            .exitCode(exitCode)
            .executionTimeMs(0)
            .success(false)
            .sessionId(sessionId)
            .errorMessage(errorMessage)
            .build();
    }
}
