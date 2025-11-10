package com.cola.agent.service;

import com.cola.agent.dto.CommitMessageRequest;
import com.cola.agent.dto.CommitMessageResponse;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-powered commit message generation service.
 * Analyzes git diffs and generates meaningful commit messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommitMessageService {

    private final ChatModel chatModel;
    private final ProjectRepository projectRepository;
    private final GitService gitService;

    /**
     * Generate AI commit message from diff.
     */
    @Cacheable(value = "commitMessages", key = "#request.diff.rawDiff.hashCode()")
    public CommitMessageResponse generateCommitMessage(CommitMessageRequest request) {
        try {
            Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            GitDiffInfo diff = request.getDiff();
            if (diff == null) {
                // Get staged diff
                diff = gitService.getStagedDiff(request.getProjectId());
            }

            if (diff.getTotalFilesChanged() == 0) {
                return CommitMessageResponse.builder()
                    .message("No changes to commit")
                    .subject("No changes to commit")
                    .confidence(0.0)
                    .impactLevel(CommitMessageResponse.ImpactLevel.MINOR)
                    .build();
            }

            String prompt = buildCommitMessagePrompt(request, diff, project);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(getSystemPrompt(request.getStyle())));
            messages.add(new UserMessage(prompt));

            ChatResponse response = chatModel.call(new Prompt(messages));
            String content = response.getResult().getOutput().getContent();

            return parseCommitMessage(content, diff);

        } catch (Exception e) {
            log.error("Error generating commit message", e);
            return createFallbackCommitMessage(request.getDiff());
        }
    }

    /**
     * Generate multiple commit message alternatives.
     */
    public List<String> generateAlternatives(UUID projectId, GitDiffInfo diff, int count) {
        List<String> alternatives = new ArrayList<>();

        // Generate with different styles
        List<CommitMessageRequest.CommitStyle> styles = Arrays.asList(
            CommitMessageRequest.CommitStyle.CONVENTIONAL,
            CommitMessageRequest.CommitStyle.SEMANTIC,
            CommitMessageRequest.CommitStyle.DESCRIPTIVE
        );

        for (int i = 0; i < Math.min(count, styles.size()); i++) {
            CommitMessageRequest request = CommitMessageRequest.builder()
                .projectId(projectId)
                .diff(diff)
                .style(styles.get(i))
                .includeBody(false)
                .build();

            CommitMessageResponse response = generateCommitMessage(request);
            if (response.getSubject() != null) {
                alternatives.add(response.getSubject());
            }
        }

        return alternatives;
    }

    /**
     * Build prompt for commit message generation.
     */
    private String buildCommitMessagePrompt(CommitMessageRequest request, GitDiffInfo diff, Project project) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a commit message for the following changes:\n\n");

        // Project context
        prompt.append("Project: ").append(project.getName()).append("\n");
        prompt.append("Language: ").append(project.getLanguage()).append("\n");
        if (project.getFramework() != null) {
            prompt.append("Framework: ").append(project.getFramework()).append("\n");
        }
        prompt.append("\n");

        // Changes summary
        prompt.append("Changes:\n");
        prompt.append("- Files modified: ").append(diff.getModifiedFiles().size()).append("\n");
        prompt.append("- Files added: ").append(diff.getAddedFiles().size()).append("\n");
        prompt.append("- Files deleted: ").append(diff.getDeletedFiles().size()).append("\n");
        prompt.append("- Lines added: ").append(diff.getLinesAdded()).append("\n");
        prompt.append("- Lines deleted: ").append(diff.getLinesDeleted()).append("\n\n");

        // File list
        prompt.append("Modified files:\n");
        diff.getModifiedFiles().forEach(file ->
            prompt.append("- ").append(file.getFilePath())
                  .append(" (+").append(file.getLinesAdded())
                  .append(" -").append(file.getLinesDeleted()).append(")\n")
        );
        diff.getAddedFiles().forEach(file ->
            prompt.append("- ").append(file.getFilePath()).append(" (new)\n")
        );
        diff.getDeletedFiles().forEach(file ->
            prompt.append("- ").append(file.getFilePath()).append(" (deleted)\n")
        );

        prompt.append("\n");

        // Diff snippet (limited to avoid token overflow)
        String diffSnippet = diff.getRawDiff();
        if (diffSnippet.length() > 3000) {
            diffSnippet = diffSnippet.substring(0, 3000) + "\n... (diff truncated)";
        }
        prompt.append("Diff:\n```\n").append(diffSnippet).append("\n```\n\n");

        // Custom context
        if (request.getCustomContext() != null) {
            prompt.append("Additional context: ").append(request.getCustomContext()).append("\n\n");
        }

        // Requirements
        prompt.append("Requirements:\n");
        prompt.append("- Style: ").append(request.getStyle()).append("\n");
        prompt.append("- Max subject length: ").append(request.getMaxLength()).append(" chars\n");
        prompt.append("- Include scope: ").append(request.isIncludeScope()).append("\n");
        prompt.append("- Include body: ").append(request.isIncludeBody()).append("\n");

        return prompt.toString();
    }

    /**
     * Get system prompt based on commit style.
     */
    private String getSystemPrompt(CommitMessageRequest.CommitStyle style) {
        String basePrompt = """
            You are an expert at writing meaningful git commit messages.
            Analyze the changes and generate a clear, concise commit message.

            General rules:
            - Focus on WHAT changed and WHY (not HOW)
            - Use imperative mood (e.g., "Add feature" not "Added feature")
            - Be specific and meaningful
            - Avoid generic messages like "Update files" or "Fix bugs"

            """;

        return switch (style) {
            case CONVENTIONAL -> basePrompt + """
                Use Conventional Commits format:
                <type>(<scope>): <subject>

                <body>

                Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build

                Example:
                feat(auth): add JWT token authentication

                Implemented JWT-based authentication to secure API endpoints.
                Added token validation middleware and refresh token support.
                """;

            case SEMANTIC -> basePrompt + """
                Use semantic format:
                <Subject line>

                <Body with details>

                Example:
                Add JWT authentication system

                Implemented token-based authentication with refresh tokens.
                """;

            case DESCRIPTIVE -> basePrompt + """
                Use descriptive format with past tense:
                <Detailed description of what was done>

                Example:
                Added JWT authentication with token refresh functionality
                """;

            case GITMOJI -> basePrompt + """
                Use Gitmoji format with emoji prefixes:
                <emoji> <subject>

                Common emoji:
                ✨ feat, 🐛 fix, 📝 docs, 💄 style, ♻️ refactor, ✅ test, 🔧 chore

                Example:
                ✨ Add JWT authentication system
                """;

            case ANGULAR -> basePrompt + """
                Use Angular commit format:
                <type>(<scope>): <subject>

                <body>

                <footer>

                Example:
                feat(auth): implement JWT authentication

                Add token-based authentication with refresh tokens.
                Includes middleware for token validation.

                BREAKING CHANGE: Authentication now required for all API endpoints
                """;
        };
    }

    /**
     * Parse AI response into structured commit message.
     */
    private CommitMessageResponse parseCommitMessage(String content, GitDiffInfo diff) {
        String[] lines = content.split("\n");
        String subject = lines[0].trim();
        StringBuilder body = new StringBuilder();

        // Extract body (everything after first line)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                body.append(line).append("\n");
            }
        }

        // Detect change type from subject
        String changeType = detectChangeType(subject);

        // Detect scope
        String scope = detectScope(subject);

        // Determine impact level
        CommitMessageResponse.ImpactLevel impactLevel = determineImpactLevel(diff, content);

        // Generate files summary
        String filesSummary = String.format("%d file(s) changed: +%d -%d",
            diff.getTotalFilesChanged(), diff.getLinesAdded(), diff.getLinesDeleted());

        return CommitMessageResponse.builder()
            .message(content)
            .subject(subject)
            .body(body.toString().trim())
            .changeType(changeType)
            .scope(scope)
            .confidence(0.85)
            .filesSummary(filesSummary)
            .impactLevel(impactLevel)
            .alternatives(new ArrayList<>())
            .build();
    }

    /**
     * Detect change type from commit message.
     */
    private String detectChangeType(String subject) {
        String lower = subject.toLowerCase();

        if (lower.startsWith("feat") || lower.contains("add") || lower.contains("implement")) {
            return "feat";
        } else if (lower.startsWith("fix") || lower.contains("bug") || lower.contains("issue")) {
            return "fix";
        } else if (lower.startsWith("refactor") || lower.contains("refactor")) {
            return "refactor";
        } else if (lower.startsWith("docs") || lower.contains("document")) {
            return "docs";
        } else if (lower.startsWith("test") || lower.contains("test")) {
            return "test";
        } else if (lower.startsWith("perf") || lower.contains("performance")) {
            return "perf";
        } else if (lower.startsWith("chore") || lower.contains("chore")) {
            return "chore";
        }

        return "chore";
    }

    /**
     * Detect scope from commit message.
     */
    private String detectScope(String subject) {
        Pattern scopePattern = Pattern.compile("\\(([^)]+)\\)");
        Matcher matcher = scopePattern.matcher(subject);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Determine impact level based on changes.
     */
    private CommitMessageResponse.ImpactLevel determineImpactLevel(GitDiffInfo diff, String message) {
        String lower = message.toLowerCase();

        // Breaking changes
        if (lower.contains("breaking change") || lower.contains("breaking:")) {
            return CommitMessageResponse.ImpactLevel.BREAKING;
        }

        // Major changes
        if (diff.getTotalFilesChanged() > 10 ||
            diff.getLinesAdded() + diff.getLinesDeleted() > 500 ||
            lower.contains("major")) {
            return CommitMessageResponse.ImpactLevel.MAJOR;
        }

        // Moderate changes
        if (diff.getTotalFilesChanged() > 3 ||
            diff.getLinesAdded() + diff.getLinesDeleted() > 100) {
            return CommitMessageResponse.ImpactLevel.MODERATE;
        }

        return CommitMessageResponse.ImpactLevel.MINOR;
    }

    /**
     * Create fallback commit message when AI fails.
     */
    private CommitMessageResponse createFallbackCommitMessage(GitDiffInfo diff) {
        String subject = String.format("Update %d file(s)", diff.getTotalFilesChanged());

        return CommitMessageResponse.builder()
            .message(subject)
            .subject(subject)
            .changeType("chore")
            .confidence(0.5)
            .filesSummary(String.format("%d files: +%d -%d",
                diff.getTotalFilesChanged(), diff.getLinesAdded(), diff.getLinesDeleted()))
            .impactLevel(CommitMessageResponse.ImpactLevel.MINOR)
            .build();
    }
}
