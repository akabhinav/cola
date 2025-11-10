package com.cola.agent.rag.model;

import com.cola.agent.model.Conversation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents the assembled context for an AI model call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeContext {

    private UUID projectId;
    private String query;

    @Builder.Default
    private List<Conversation> conversationHistory = new ArrayList<>();

    @Builder.Default
    private List<CodeChunk> relevantChunks = new ArrayList<>();

    private Map<String, Object> projectMetadata;

    private int totalTokens;
    private int maxContextTokens;

    /**
     * Get formatted context for LLM prompt.
     */
    public String formatForPrompt() {
        StringBuilder context = new StringBuilder();

        // Add project metadata
        if (projectMetadata != null && !projectMetadata.isEmpty()) {
            context.append("Project Information:\n");
            projectMetadata.forEach((key, value) ->
                context.append(String.format("- %s: %s\n", key, value))
            );
            context.append("\n");
        }

        // Add relevant code chunks
        if (!relevantChunks.isEmpty()) {
            context.append("Relevant Code Context:\n\n");
            for (int i = 0; i < relevantChunks.size(); i++) {
                CodeChunk chunk = relevantChunks.get(i);
                context.append(String.format("[%d] File: %s (Lines %d-%d)\n",
                    i + 1,
                    chunk.getFilePath(),
                    chunk.getStartLine(),
                    chunk.getEndLine()
                ));

                if (chunk.getName() != null) {
                    context.append(String.format("Type: %s - %s\n", chunk.getType(), chunk.getName()));
                }

                context.append("```").append(chunk.getLanguage()).append("\n");
                context.append(chunk.getContent());
                context.append("\n```\n\n");
            }
        }

        return context.toString();
    }

    /**
     * Check if context fits within token budget.
     */
    public boolean fitsWithinBudget() {
        return totalTokens <= maxContextTokens;
    }
}
