package com.cola.agent.service;

import com.cola.agent.dto.CodeReviewRequest;
import com.cola.agent.dto.CodeReviewResponse;
import com.cola.agent.dto.GitDiffInfo;
import com.cola.agent.model.Project;
import com.cola.agent.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-powered code review service.
 * Provides comprehensive code analysis with actionable feedback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewService {

    private final ChatModel chatModel;
    private final ProjectRepository projectRepository;
    private final GitService gitService;

    /**
     * Perform AI code review on changes.
     */
    public CodeReviewResponse reviewCode(CodeReviewRequest request) {
        try {
            Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            GitDiffInfo diff = request.getDiff();
            if (diff == null) {
                diff = gitService.getStagedDiff(request.getProjectId());
            }

            if (diff.getTotalFilesChanged() == 0) {
                return createNoChangesReview();
            }

            String prompt = buildReviewPrompt(request, diff, project);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(getReviewSystemPrompt(request)));
            messages.add(new UserMessage(prompt));

            ChatResponse response = chatModel.call(new Prompt(messages));
            String content = response.getResult().getOutput().getContent();

            return parseReview(content, diff);

        } catch (Exception e) {
            log.error("Error performing code review", e);
            return createErrorReview();
        }
    }

    /**
     * Build code review prompt.
     */
    private String buildReviewPrompt(CodeReviewRequest request, GitDiffInfo diff, Project project) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Perform a code review of the following changes:\n\n");

        // Project context
        prompt.append("Project: ").append(project.getName()).append("\n");
        prompt.append("Language: ").append(project.getLanguage()).append("\n");
        if (project.getFramework() != null) {
            prompt.append("Framework: ").append(project.getFramework()).append("\n");
        }
        prompt.append("\n");

        // Review configuration
        prompt.append("Review Configuration:\n");
        prompt.append("- Depth: ").append(request.getDepth()).append("\n");
        prompt.append("- Focus Areas: ").append(String.join(", ",
            request.getFocusAreas().stream().map(Enum::name).toList())).append("\n");
        prompt.append("- Include Suggestions: ").append(request.isIncludeSuggestions()).append("\n");
        prompt.append("- Include Examples: ").append(request.isIncludeExamples()).append("\n");
        prompt.append("\n");

        // Custom guidelines
        if (request.getCustomGuidelines() != null) {
            prompt.append("Project Guidelines:\n").append(request.getCustomGuidelines()).append("\n\n");
        }

        // Changes summary
        prompt.append("Changes Summary:\n");
        prompt.append("- Files changed: ").append(diff.getTotalFilesChanged()).append("\n");
        prompt.append("- Lines added: ").append(diff.getLinesAdded()).append("\n");
        prompt.append("- Lines deleted: ").append(diff.getLinesDeleted()).append("\n\n");

        // Diff content (limit based on depth)
        int maxDiffLength = getMaxDiffLength(request.getDepth());
        String diffContent = diff.getRawDiff();
        if (diffContent.length() > maxDiffLength) {
            diffContent = diffContent.substring(0, maxDiffLength) + "\n... (diff truncated for review)";
        }

        prompt.append("Changes:\n```diff\n").append(diffContent).append("\n```\n\n");

        prompt.append("Please provide a comprehensive code review covering the specified focus areas.\n");

        return prompt.toString();
    }

    /**
     * Get system prompt for code review.
     */
    private String getReviewSystemPrompt(CodeReviewRequest request) {
        StringBuilder systemPrompt = new StringBuilder();

        systemPrompt.append("""
            You are an expert code reviewer with deep knowledge of software engineering best practices.
            Your role is to provide constructive, actionable feedback that helps improve code quality.

            Review Principles:
            1. Be constructive and specific
            2. Explain the "why" behind each issue
            3. Prioritize issues by severity (Critical, Major, Minor)
            4. Provide code examples for suggested fixes
            5. Acknowledge good practices when you see them
            6. Consider context and trade-offs

            Issue Severity Levels:
            - CRITICAL: Security vulnerabilities, data loss risks, critical bugs
            - MAJOR: Significant bugs, performance issues, poor design
            - MINOR: Code style, minor improvements, suggestions

            """);

        // Add focus-specific guidelines
        if (request.getFocusAreas().contains(CodeReviewRequest.ReviewFocus.SECURITY)) {
            systemPrompt.append("""
                Security Focus:
                - Check for SQL injection, XSS, CSRF vulnerabilities
                - Validate input/output handling
                - Review authentication and authorization
                - Check for exposed secrets or sensitive data
                - Verify secure dependencies

                """);
        }

        if (request.getFocusAreas().contains(CodeReviewRequest.ReviewFocus.PERFORMANCE)) {
            systemPrompt.append("""
                Performance Focus:
                - Identify N+1 queries and inefficient database access
                - Check for memory leaks and resource management
                - Review algorithm complexity
                - Look for unnecessary computations or iterations
                - Check caching opportunities

                """);
        }

        if (request.getFocusAreas().contains(CodeReviewRequest.ReviewFocus.CODE_QUALITY)) {
            systemPrompt.append("""
                Code Quality Focus:
                - Check code readability and maintainability
                - Review naming conventions
                - Identify code duplication
                - Check for proper error handling
                - Verify logging and monitoring
                - Review code organization and structure

                """);
        }

        if (request.getFocusAreas().contains(CodeReviewRequest.ReviewFocus.TESTING)) {
            systemPrompt.append("""
                Testing Focus:
                - Verify test coverage for new code
                - Check test quality and assertions
                - Review edge cases and error scenarios
                - Validate mock usage
                - Check for flaky tests

                """);
        }

        systemPrompt.append("""
            Output Format:
            Provide your review in the following structure:

            SUMMARY:
            <Brief overall summary>

            SCORE: <0-100>

            CRITICAL:
            FILE: <file path>:<line>
            CATEGORY: <category>
            MESSAGE: <issue description>
            SUGGESTION: <how to fix>
            EXAMPLE: <code example if requested>
            ---

            MAJOR:
            <same format>
            ---

            MINOR:
            <same format>
            ---

            POSITIVES:
            - <positive feedback>
            ---

            SUGGESTIONS:
            - <general suggestions>
            ---

            APPROVAL: <APPROVED|APPROVED_WITH_COMMENTS|CHANGES_REQUESTED|NEEDS_WORK>
            """);

        return systemPrompt.toString();
    }

    /**
     * Parse AI review response into structured format.
     */
    private CodeReviewResponse parseReview(String content, GitDiffInfo diff) {
        // Extract summary
        String summary = extractValue(content, "SUMMARY:", "SCORE:");

        // Extract overall score
        int overallScore = extractScore(content);

        // Extract issues by severity
        List<CodeReviewResponse.ReviewComment> criticalIssues = parseIssues(content, "CRITICAL:", "MAJOR:");
        List<CodeReviewResponse.ReviewComment> majorIssues = parseIssues(content, "MAJOR:", "MINOR:");
        List<CodeReviewResponse.ReviewComment> minorIssues = parseIssues(content, "MINOR:", "POSITIVES:");

        // Extract positives
        List<String> positives = parseList(content, "POSITIVES:", "SUGGESTIONS:");

        // Extract suggestions
        List<String> suggestions = parseList(content, "SUGGESTIONS:", "APPROVAL:");

        // Extract approval status
        CodeReviewResponse.ApprovalStatus approvalStatus = extractApprovalStatus(content);

        // Calculate metrics
        CodeReviewResponse.ReviewMetrics metrics = calculateMetrics(
            diff, criticalIssues, majorIssues, minorIssues, overallScore
        );

        return CodeReviewResponse.builder()
            .summary(summary)
            .overallScore(overallScore)
            .criticalIssues(criticalIssues)
            .majorIssues(majorIssues)
            .minorIssues(minorIssues)
            .positives(positives)
            .suggestions(suggestions)
            .metrics(metrics)
            .approvalStatus(approvalStatus)
            .build();
    }

    /**
     * Parse issues from content.
     */
    private List<CodeReviewResponse.ReviewComment> parseIssues(String content, String startMarker, String endMarker) {
        List<CodeReviewResponse.ReviewComment> issues = new ArrayList<>();

        String section = extractValue(content, startMarker, endMarker);
        if (section.isEmpty()) return issues;

        String[] blocks = section.split("---");

        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;

            String file = extractValue(block, "FILE:", "\n");
            String category = extractValue(block, "CATEGORY:", "\n");
            String message = extractValue(block, "MESSAGE:", "SUGGESTION:");
            String suggestion = extractValue(block, "SUGGESTION:", "EXAMPLE:");
            String example = extractValue(block, "EXAMPLE:", "---");

            // Parse file and line number
            Integer lineNumber = null;
            if (file.contains(":")) {
                String[] parts = file.split(":");
                file = parts[0].trim();
                if (parts.length > 1) {
                    try {
                        lineNumber = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }

            String severity = startMarker.contains("CRITICAL") ? "CRITICAL" :
                             startMarker.contains("MAJOR") ? "MAJOR" : "MINOR";

            issues.add(CodeReviewResponse.ReviewComment.builder()
                .file(file.trim())
                .lineNumber(lineNumber)
                .category(category.trim())
                .severity(severity)
                .message(message.trim())
                .suggestion(suggestion.trim())
                .codeExample(example.trim())
                .build());
        }

        return issues;
    }

    /**
     * Parse list items from content.
     */
    private List<String> parseList(String content, String startMarker, String endMarker) {
        List<String> items = new ArrayList<>();

        String section = extractValue(content, startMarker, endMarker);
        if (section.isEmpty()) return items;

        String[] lines = section.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("-") || line.startsWith("*")) {
                items.add(line.replaceFirst("^[-*]\\s*", ""));
            }
        }

        return items;
    }

    /**
     * Extract value between markers.
     */
    private String extractValue(String content, String startMarker, String endMarker) {
        int start = content.indexOf(startMarker);
        if (start == -1) return "";

        start += startMarker.length();

        int end = content.indexOf(endMarker, start);
        if (end == -1) {
            end = content.length();
        }

        return content.substring(start, end).trim();
    }

    /**
     * Extract score from content.
     */
    private int extractScore(String content) {
        Pattern scorePattern = Pattern.compile("SCORE:\\s*(\\d+)");
        Matcher matcher = scorePattern.matcher(content);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 70; // Default score
    }

    /**
     * Extract approval status.
     */
    private CodeReviewResponse.ApprovalStatus extractApprovalStatus(String content) {
        String approvalText = extractValue(content, "APPROVAL:", "\n");

        if (approvalText.contains("APPROVED_WITH_COMMENTS")) {
            return CodeReviewResponse.ApprovalStatus.APPROVED_WITH_COMMENTS;
        } else if (approvalText.contains("APPROVED")) {
            return CodeReviewResponse.ApprovalStatus.APPROVED;
        } else if (approvalText.contains("CHANGES_REQUESTED")) {
            return CodeReviewResponse.ApprovalStatus.CHANGES_REQUESTED;
        } else {
            return CodeReviewResponse.ApprovalStatus.NEEDS_WORK;
        }
    }

    /**
     * Calculate review metrics.
     */
    private CodeReviewResponse.ReviewMetrics calculateMetrics(
        GitDiffInfo diff,
        List<CodeReviewResponse.ReviewComment> critical,
        List<CodeReviewResponse.ReviewComment> major,
        List<CodeReviewResponse.ReviewComment> minor,
        int overallScore
    ) {
        int filesWithIssues = (int) Stream.concat(
            Stream.concat(critical.stream(), major.stream()),
            minor.stream()
        ).map(CodeReviewResponse.ReviewComment::getFile)
         .distinct()
         .count();

        return CodeReviewResponse.ReviewMetrics.builder()
            .totalFiles(diff.getTotalFilesChanged())
            .filesWithIssues(filesWithIssues)
            .totalIssues(critical.size() + major.size() + minor.size())
            .criticalCount(critical.size())
            .majorCount(major.size())
            .minorCount(minor.size())
            .codeQualityScore(calculateComponentScore(overallScore, minor.size()))
            .securityScore(calculateComponentScore(overallScore, critical.size()))
            .performanceScore(calculateComponentScore(overallScore, major.size()))
            .build();
    }

    /**
     * Calculate component score.
     */
    private int calculateComponentScore(int baseScore, int issueCount) {
        return Math.max(0, baseScore - (issueCount * 5));
    }

    /**
     * Get max diff length based on review depth.
     */
    private int getMaxDiffLength(CodeReviewRequest.ReviewDepth depth) {
        return switch (depth) {
            case QUICK -> 2000;
            case STANDARD -> 5000;
            case THOROUGH -> 10000;
        };
    }

    /**
     * Create review for no changes.
     */
    private CodeReviewResponse createNoChangesReview() {
        return CodeReviewResponse.builder()
            .summary("No changes to review")
            .overallScore(100)
            .criticalIssues(new ArrayList<>())
            .majorIssues(new ArrayList<>())
            .minorIssues(new ArrayList<>())
            .positives(List.of("No changes detected"))
            .suggestions(new ArrayList<>())
            .approvalStatus(CodeReviewResponse.ApprovalStatus.APPROVED)
            .metrics(CodeReviewResponse.ReviewMetrics.builder()
                .totalFiles(0)
                .filesWithIssues(0)
                .totalIssues(0)
                .criticalCount(0)
                .majorCount(0)
                .minorCount(0)
                .codeQualityScore(100)
                .securityScore(100)
                .performanceScore(100)
                .build())
            .build();
    }

    /**
     * Create error review.
     */
    private CodeReviewResponse createErrorReview() {
        return CodeReviewResponse.builder()
            .summary("Error performing code review")
            .overallScore(0)
            .criticalIssues(new ArrayList<>())
            .majorIssues(new ArrayList<>())
            .minorIssues(new ArrayList<>())
            .positives(new ArrayList<>())
            .suggestions(List.of("Please try again"))
            .approvalStatus(CodeReviewResponse.ApprovalStatus.NEEDS_WORK)
            .metrics(CodeReviewResponse.ReviewMetrics.builder().build())
            .build();
    }
}
