package com.cola.agent.service;

import com.cola.agent.dto.GitDiffInfo;
import com.cola.agent.dto.PRDescriptionRequest;
import com.cola.agent.dto.PRDescriptionResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI-powered Pull Request description generation service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PRDescriptionService {

    private final ChatModel chatModel;
    private final ProjectRepository projectRepository;
    private final GitService gitService;

    /**
     * Generate PR description from changes.
     */
    public PRDescriptionResponse generatePRDescription(PRDescriptionRequest request) {
        try {
            Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            // Get diff if not provided
            GitDiffInfo diff = request.getDiff();
            if (diff == null) {
                diff = gitService.getDiffBetween(
                    request.getProjectId(),
                    request.getBaseBranch(),
                    request.getBranch()
                );
            }

            // Get commit messages if not provided
            List<String> commitMessages = request.getCommitMessages();
            if (commitMessages == null || commitMessages.isEmpty()) {
                commitMessages = gitService.getRecentCommits(request.getProjectId(), 20);
            }

            String prompt = buildPRPrompt(request, diff, commitMessages, project);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(getPRSystemPrompt(request.getTemplateStyle())));
            messages.add(new UserMessage(prompt));

            ChatResponse response = chatModel.call(new Prompt(messages));
            String content = response.getResult().getOutput().getContent();

            return parsePRDescription(content, request, diff);

        } catch (Exception e) {
            log.error("Error generating PR description", e);
            return createFallbackPRDescription(request);
        }
    }

    /**
     * Build PR description prompt.
     */
    private String buildPRPrompt(
        PRDescriptionRequest request,
        GitDiffInfo diff,
        List<String> commitMessages,
        Project project
    ) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Pull Request description for the following changes:\n\n");

        // Project context
        prompt.append("Project: ").append(project.getName()).append("\n");
        prompt.append("Language: ").append(project.getLanguage()).append("\n");
        if (project.getFramework() != null) {
            prompt.append("Framework: ").append(project.getFramework()).append("\n");
        }
        prompt.append("\n");

        // PR info
        prompt.append("Branch: ").append(request.getBranch()).append("\n");
        prompt.append("Base Branch: ").append(request.getBaseBranch()).append("\n\n");

        // Changes summary
        prompt.append("Changes Overview:\n");
        prompt.append("- Total files changed: ").append(diff.getTotalFilesChanged()).append("\n");
        prompt.append("- Lines added: ").append(diff.getLinesAdded()).append("\n");
        prompt.append("- Lines deleted: ").append(diff.getLinesDeleted()).append("\n\n");

        // Commit messages
        if (!commitMessages.isEmpty()) {
            prompt.append("Commit Messages:\n");
            commitMessages.stream().limit(10).forEach(commit ->
                prompt.append("- ").append(commit).append("\n")
            );
            prompt.append("\n");
        }

        // File changes
        prompt.append("Files Changed:\n");
        diff.getModifiedFiles().forEach(file ->
            prompt.append("- MODIFIED: ").append(file.getFilePath()).append("\n")
        );
        diff.getAddedFiles().forEach(file ->
            prompt.append("- ADDED: ").append(file.getFilePath()).append("\n")
        );
        diff.getDeletedFiles().forEach(file ->
            prompt.append("- DELETED: ").append(file.getFilePath()).append("\n")
        );

        // Related issues
        if (request.getRelatedIssues() != null && !request.getRelatedIssues().isEmpty()) {
            prompt.append("\nRelated Issues:\n");
            request.getRelatedIssues().forEach(issue ->
                prompt.append("- ").append(issue).append("\n")
            );
        }

        // Custom context
        if (request.getCustomContext() != null) {
            prompt.append("\nAdditional Context:\n").append(request.getCustomContext()).append("\n");
        }

        return prompt.toString();
    }

    /**
     * Get system prompt for PR description based on template style.
     */
    private String getPRSystemPrompt(PRDescriptionRequest.TemplateStyle style) {
        String basePrompt = """
            You are an expert at writing clear, informative Pull Request descriptions.
            Your goal is to help reviewers understand what changed, why, and how to test it.

            General guidelines:
            - Write in clear, professional language
            - Focus on the business value and technical impact
            - Be specific about what changed and why
            - Include testing instructions
            - Highlight breaking changes or migrations needed
            - Use markdown formatting for better readability

            """;

        return switch (style) {
            case STANDARD -> basePrompt + """
                Use the What/Why/How format:

                ## Summary
                Brief overview of the changes

                ## Changes
                - List of specific changes

                ## Test Plan
                How to test these changes

                ## Checklist
                - [ ] Tests added/updated
                - [ ] Documentation updated
                - [ ] No breaking changes
                """;

            case DETAILED -> basePrompt + """
                Use comprehensive format with all sections:

                ## Summary
                ## Motivation
                ## Changes
                ## Technical Details
                ## Test Plan
                ## Screenshots (if UI changes)
                ## Breaking Changes (if any)
                ## Migration Guide (if needed)
                ## Checklist
                ## Related Issues
                """;

            case MINIMAL -> basePrompt + """
                Use brief format:

                Brief description of changes in 2-3 sentences.

                **Changes:**
                - Key changes only
                """;

            case JIRA -> basePrompt + """
                Use JIRA-compatible format:

                *Summary:* Brief overview

                *Changes:*
                - Change 1
                - Change 2

                *Testing:* How to test

                *Related:* JIRA-123, JIRA-456
                """;

            case GITHUB -> basePrompt + """
                Use GitHub best practices format:

                ## What
                What this PR does

                ## Why
                Why this change is needed

                ## How
                How it was implemented

                ## Testing
                How to test

                ## Screenshots
                (if applicable)

                Fixes #123
                """;
        };
    }

    /**
     * Parse AI response into structured PR description.
     */
    private PRDescriptionResponse parsePRDescription(
        String content,
        PRDescriptionRequest request,
        GitDiffInfo diff
    ) {
        // Extract title (first line or first heading)
        String title = request.getTitle();
        if (title == null || title.isEmpty()) {
            String[] lines = content.split("\n");
            title = lines[0].replaceFirst("^#+\\s*", "").trim();
        }

        // Extract summary
        String summary = extractSection(content, "Summary", "## ");

        // Extract changes
        List<String> changes = extractListItems(content, "Changes");

        // Extract test plan
        String testPlan = extractSection(content, "Test Plan", "## ");

        // Extract breaking changes
        String breakingChanges = extractSection(content, "Breaking Changes", "## ");

        // Build checklist
        List<PRDescriptionResponse.ChecklistItem> checklist = buildChecklist(request);

        // Build full description
        String fullDescription = content;

        return PRDescriptionResponse.builder()
            .title(title)
            .description(fullDescription)
            .summary(summary)
            .changes(changes)
            .testPlan(testPlan)
            .checklist(checklist)
            .relatedIssues(request.getRelatedIssues())
            .breakingChanges(breakingChanges)
            .fullDescription(fullDescription)
            .build();
    }

    /**
     * Extract a section from markdown content.
     */
    private String extractSection(String content, String sectionName, String headerPrefix) {
        String[] lines = content.split("\n");
        StringBuilder section = new StringBuilder();
        boolean inSection = false;

        for (String line : lines) {
            if (line.startsWith(headerPrefix)) {
                if (line.toLowerCase().contains(sectionName.toLowerCase())) {
                    inSection = true;
                    continue;
                } else if (inSection) {
                    break;
                }
            }

            if (inSection && !line.trim().isEmpty()) {
                section.append(line).append("\n");
            }
        }

        return section.toString().trim();
    }

    /**
     * Extract list items from a section.
     */
    private List<String> extractListItems(String content, String sectionName) {
        List<String> items = new ArrayList<>();
        String section = extractSection(content, sectionName, "## ");

        String[] lines = section.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("-") || line.trim().startsWith("*")) {
                items.add(line.trim().replaceFirst("^[-*]\\s*", ""));
            }
        }

        return items;
    }

    /**
     * Build checklist items.
     */
    private List<PRDescriptionResponse.ChecklistItem> buildChecklist(PRDescriptionRequest request) {
        List<PRDescriptionResponse.ChecklistItem> items = new ArrayList<>();

        if (request.isIncludeChecklist()) {
            items.add(PRDescriptionResponse.ChecklistItem.builder()
                .text("Tests added or updated")
                .checked(false)
                .build());

            items.add(PRDescriptionResponse.ChecklistItem.builder()
                .text("Documentation updated")
                .checked(false)
                .build());

            items.add(PRDescriptionResponse.ChecklistItem.builder()
                .text("No breaking changes (or documented)")
                .checked(false)
                .build());

            items.add(PRDescriptionResponse.ChecklistItem.builder()
                .text("Code follows project style guidelines")
                .checked(false)
                .build());

            items.add(PRDescriptionResponse.ChecklistItem.builder()
                .text("Self-review completed")
                .checked(false)
                .build());
        }

        return items;
    }

    /**
     * Create fallback PR description.
     */
    private PRDescriptionResponse createFallbackPRDescription(PRDescriptionRequest request) {
        String title = request.getTitle() != null ? request.getTitle() :
            "Update from " + request.getBranch();

        String summary = "This PR includes changes from the " + request.getBranch() + " branch.";

        return PRDescriptionResponse.builder()
            .title(title)
            .summary(summary)
            .changes(List.of("Changes included in this PR"))
            .testPlan("Test manually")
            .checklist(buildChecklist(request))
            .fullDescription(summary)
            .build();
    }
}
