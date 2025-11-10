package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI-generated PR description response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PRDescriptionResponse {

    // PR title
    private String title;

    // PR description body
    private String description;

    // Summary section
    private String summary;

    // Changes section
    private List<String> changes;

    // Test plan section
    private String testPlan;

    // Checklist items
    private List<ChecklistItem> checklist;

    // Related issues/tickets
    private List<String> relatedIssues;

    // Screenshots/evidence section
    private String screenshotsSection;

    // Breaking changes section
    private String breakingChanges;

    // Migration guide (if breaking changes)
    private String migrationGuide;

    // Full formatted PR description
    private String fullDescription;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecklistItem {
        private String text;
        private boolean checked;
    }

    /**
     * Get formatted markdown PR description.
     */
    public String getMarkdownDescription() {
        if (fullDescription != null) {
            return fullDescription;
        }

        StringBuilder md = new StringBuilder();

        if (summary != null) {
            md.append("## Summary\n\n").append(summary).append("\n\n");
        }

        if (changes != null && !changes.isEmpty()) {
            md.append("## Changes\n\n");
            changes.forEach(change -> md.append("- ").append(change).append("\n"));
            md.append("\n");
        }

        if (testPlan != null) {
            md.append("## Test Plan\n\n").append(testPlan).append("\n\n");
        }

        if (checklist != null && !checklist.isEmpty()) {
            md.append("## Checklist\n\n");
            checklist.forEach(item ->
                md.append("- [").append(item.checked ? "x" : " ").append("] ")
                  .append(item.text).append("\n")
            );
            md.append("\n");
        }

        if (breakingChanges != null) {
            md.append("## ⚠️ Breaking Changes\n\n").append(breakingChanges).append("\n\n");
        }

        if (relatedIssues != null && !relatedIssues.isEmpty()) {
            md.append("## Related Issues\n\n");
            relatedIssues.forEach(issue -> md.append("- ").append(issue).append("\n"));
            md.append("\n");
        }

        return md.toString();
    }
}
