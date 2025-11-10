package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI-generated commit message response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessageResponse {

    // Primary commit message
    private String message;

    // Commit message subject/title
    private String subject;

    // Commit message body (detailed description)
    private String body;

    // Alternative commit message suggestions
    private List<String> alternatives;

    // Detected change type (feat, fix, refactor, etc.)
    private String changeType;

    // Detected scope (auth, api, ui, etc.)
    private String scope;

    // Confidence score
    private double confidence;

    // Files affected summary
    private String filesSummary;

    // Impact level (MINOR, MODERATE, MAJOR, BREAKING)
    private ImpactLevel impactLevel;

    public enum ImpactLevel {
        MINOR,      // Small changes, no API impact
        MODERATE,   // Medium changes, minimal API impact
        MAJOR,      // Significant changes, potential API changes
        BREAKING    // Breaking changes
    }

    /**
     * Get full formatted commit message.
     */
    public String getFullMessage() {
        if (body != null && !body.isEmpty()) {
            return subject + "\n\n" + body;
        }
        return subject != null ? subject : message;
    }
}
