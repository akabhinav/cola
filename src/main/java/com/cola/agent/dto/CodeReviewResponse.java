package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI code review response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewResponse {

    // Overall summary
    private String summary;

    // Overall score (0-100)
    private int overallScore;

    // Critical issues (must fix)
    private List<ReviewComment> criticalIssues;

    // Major issues (should fix)
    private List<ReviewComment> majorIssues;

    // Minor issues (nice to fix)
    private List<ReviewComment> minorIssues;

    // Positive feedback
    private List<String> positives;

    // General suggestions
    private List<String> suggestions;

    // Metrics
    private ReviewMetrics metrics;

    // Approval recommendation
    private ApprovalStatus approvalStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewComment {
        private String file;
        private Integer lineNumber;
        private String category; // SECURITY, PERFORMANCE, BUG, QUALITY, etc.
        private String severity; // CRITICAL, MAJOR, MINOR
        private String message;
        private String suggestion;
        private String codeExample; // Suggested fix
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewMetrics {
        private int totalFiles;
        private int filesWithIssues;
        private int totalIssues;
        private int criticalCount;
        private int majorCount;
        private int minorCount;
        private int codeQualityScore; // 0-100
        private int securityScore;    // 0-100
        private int performanceScore; // 0-100
    }

    public enum ApprovalStatus {
        APPROVED,           // Looks good to merge
        APPROVED_WITH_COMMENTS,  // Minor issues, can merge
        CHANGES_REQUESTED,  // Must address issues before merge
        NEEDS_WORK          // Significant issues found
    }

    /**
     * Get total number of issues.
     */
    public int getTotalIssues() {
        return (criticalIssues != null ? criticalIssues.size() : 0) +
               (majorIssues != null ? majorIssues.size() : 0) +
               (minorIssues != null ? minorIssues.size() : 0);
    }

    /**
     * Get formatted markdown review.
     */
    public String getMarkdownReview() {
        StringBuilder md = new StringBuilder();

        md.append("# Code Review\n\n");
        md.append("**Overall Score:** ").append(overallScore).append("/100\n");
        md.append("**Status:** ").append(approvalStatus).append("\n\n");

        if (summary != null) {
            md.append("## Summary\n\n").append(summary).append("\n\n");
        }

        if (criticalIssues != null && !criticalIssues.isEmpty()) {
            md.append("## 🔴 Critical Issues (").append(criticalIssues.size()).append(")\n\n");
            criticalIssues.forEach(issue -> md.append(formatComment(issue)));
            md.append("\n");
        }

        if (majorIssues != null && !majorIssues.isEmpty()) {
            md.append("## 🟡 Major Issues (").append(majorIssues.size()).append(")\n\n");
            majorIssues.forEach(issue -> md.append(formatComment(issue)));
            md.append("\n");
        }

        if (minorIssues != null && !minorIssues.isEmpty()) {
            md.append("## ⚪ Minor Issues (").append(minorIssues.size()).append(")\n\n");
            minorIssues.forEach(issue -> md.append(formatComment(issue)));
            md.append("\n");
        }

        if (positives != null && !positives.isEmpty()) {
            md.append("## ✅ Positives\n\n");
            positives.forEach(positive -> md.append("- ").append(positive).append("\n"));
            md.append("\n");
        }

        if (suggestions != null && !suggestions.isEmpty()) {
            md.append("## 💡 Suggestions\n\n");
            suggestions.forEach(suggestion -> md.append("- ").append(suggestion).append("\n"));
            md.append("\n");
        }

        return md.toString();
    }

    private String formatComment(ReviewComment comment) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(comment.file);
        if (comment.lineNumber != null) {
            sb.append(":").append(comment.lineNumber);
        }
        sb.append("** - ").append(comment.category).append("\n\n");
        sb.append(comment.message).append("\n\n");

        if (comment.suggestion != null) {
            sb.append("*Suggestion:* ").append(comment.suggestion).append("\n\n");
        }

        if (comment.codeExample != null) {
            sb.append("```\n").append(comment.codeExample).append("\n```\n\n");
        }

        return sb.toString();
    }
}
