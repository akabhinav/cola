package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response with shell command translated from natural language.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaturalLanguageResponse {

    // The primary translated command
    private String command;

    // Explanation of what the command does
    private String explanation;

    // Step-by-step breakdown
    private List<String> steps;

    // Alternative commands that achieve similar results
    private List<CommandSuggestion> alternatives;

    // Warnings or caveats
    private List<String> warnings;

    // Whether the command is safe to execute
    @Builder.Default
    private boolean safe = true;

    // Confidence in the translation
    private double confidence;

    // Example usage
    private String example;
}
