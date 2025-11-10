package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI-powered command suggestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandSuggestion {

    // The suggested command
    private String command;

    // Description of what the command does
    private String description;

    // Confidence score (0.0 to 1.0)
    private double confidence;

    // Category (git, file, build, test, etc.)
    private String category;

    // Whether the command is potentially dangerous
    @Builder.Default
    private boolean dangerous = false;

    // Expected execution time
    private String estimatedDuration;

    // Alternative variations
    private List<String> alternatives;

    /**
     * Create a simple suggestion.
     */
    public static CommandSuggestion of(String command, String description, double confidence) {
        return CommandSuggestion.builder()
            .command(command)
            .description(description)
            .confidence(confidence)
            .build();
    }
}
