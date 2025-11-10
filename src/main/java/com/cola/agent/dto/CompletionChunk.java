package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Streaming chunk for code completion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionChunk {

    // Chunk of completion text
    private String text;

    // Whether this is the final chunk
    @Builder.Default
    private boolean isComplete = false;

    // Chunk sequence number
    private int sequence;

    // Error message if any
    private String error;

    /**
     * Create a text chunk.
     */
    public static CompletionChunk of(String text, int sequence) {
        return CompletionChunk.builder()
            .text(text)
            .sequence(sequence)
            .isComplete(false)
            .build();
    }

    /**
     * Create completion marker.
     */
    public static CompletionChunk complete() {
        return CompletionChunk.builder()
            .text("")
            .isComplete(true)
            .build();
    }

    /**
     * Create error chunk.
     */
    public static CompletionChunk error(String message) {
        return CompletionChunk.builder()
            .error(message)
            .isComplete(true)
            .build();
    }

    /**
     * Create from string for simple streaming.
     */
    public static CompletionChunk fromString(String text) {
        return CompletionChunk.builder()
            .text(text)
            .isComplete(true)
            .build();
    }
}
