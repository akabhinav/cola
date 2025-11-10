package com.cola.agent.controller;

import com.cola.agent.dto.*;
import com.cola.agent.service.CodeReviewService;
import com.cola.agent.service.CommitMessageService;
import com.cola.agent.service.GitService;
import com.cola.agent.service.PRDescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for Git intelligence features.
 * Provides AI-powered commit messages, PR descriptions, and code reviews.
 */
@RestController
@RequestMapping("/api/v1/git")
@RequiredArgsConstructor
@Validated
@Slf4j
public class GitController {

    private final GitService gitService;
    private final CommitMessageService commitMessageService;
    private final PRDescriptionService prDescriptionService;
    private final CodeReviewService codeReviewService;

    /**
     * Get git status for a project.
     *
     * GET /api/v1/git/status/{projectId}
     *
     * @param projectId Project ID
     * @return Git status information
     */
    @GetMapping("/status/{projectId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID projectId) {
        try {
            Map<String, Object> status = gitService.getStatus(projectId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting git status", e);
            return ResponseEntity.ok(Map.of("hasChanges", false, "status", ""));
        }
    }

    /**
     * Get staged diff for a project.
     *
     * GET /api/v1/git/diff/staged/{projectId}
     *
     * @param projectId Project ID
     * @return Staged changes diff
     */
    @GetMapping("/diff/staged/{projectId}")
    public ResponseEntity<GitDiffInfo> getStagedDiff(@PathVariable UUID projectId) {
        try {
            GitDiffInfo diff = gitService.getStagedDiff(projectId);
            return ResponseEntity.ok(diff);
        } catch (Exception e) {
            log.error("Error getting staged diff", e);
            return ResponseEntity.ok(GitDiffInfo.builder().build());
        }
    }

    /**
     * Get unstaged diff for a project.
     *
     * GET /api/v1/git/diff/unstaged/{projectId}
     *
     * @param projectId Project ID
     * @return Unstaged changes diff
     */
    @GetMapping("/diff/unstaged/{projectId}")
    public ResponseEntity<GitDiffInfo> getUnstagedDiff(@PathVariable UUID projectId) {
        try {
            GitDiffInfo diff = gitService.getUnstagedDiff(projectId);
            return ResponseEntity.ok(diff);
        } catch (Exception e) {
            log.error("Error getting unstaged diff", e);
            return ResponseEntity.ok(GitDiffInfo.builder().build());
        }
    }

    /**
     * Get diff between two branches/commits.
     *
     * GET /api/v1/git/diff/{projectId}
     *
     * @param projectId Project ID
     * @param base Base branch/commit
     * @param head Head branch/commit
     * @return Diff between base and head
     */
    @GetMapping("/diff/{projectId}")
    public ResponseEntity<GitDiffInfo> getDiffBetween(
            @PathVariable UUID projectId,
            @RequestParam String base,
            @RequestParam String head) {

        try {
            GitDiffInfo diff = gitService.getDiffBetween(projectId, base, head);
            return ResponseEntity.ok(diff);
        } catch (Exception e) {
            log.error("Error getting diff", e);
            return ResponseEntity.ok(GitDiffInfo.builder().build());
        }
    }

    /**
     * Generate AI commit message.
     *
     * POST /api/v1/git/commit-message
     *
     * @param request Commit message generation request
     * @return AI-generated commit message
     */
    @PostMapping("/commit-message")
    public ResponseEntity<CommitMessageResponse> generateCommitMessage(
            @Valid @RequestBody CommitMessageRequest request) {

        log.info("Generating commit message for project: {}", request.getProjectId());

        try {
            CommitMessageResponse response = commitMessageService.generateCommitMessage(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generating commit message", e);

            CommitMessageResponse errorResponse = CommitMessageResponse.builder()
                .message("Error generating commit message")
                .subject("Update files")
                .confidence(0.0)
                .impactLevel(CommitMessageResponse.ImpactLevel.MINOR)
                .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Generate commit message alternatives.
     *
     * GET /api/v1/git/commit-message/alternatives/{projectId}
     *
     * @param projectId Project ID
     * @param count Number of alternatives to generate
     * @return List of alternative commit messages
     */
    @GetMapping("/commit-message/alternatives/{projectId}")
    public ResponseEntity<List<String>> getCommitMessageAlternatives(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "3") int count) {

        try {
            GitDiffInfo diff = gitService.getStagedDiff(projectId);
            List<String> alternatives = commitMessageService.generateAlternatives(projectId, diff, count);
            return ResponseEntity.ok(alternatives);

        } catch (Exception e) {
            log.error("Error generating alternatives", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Generate PR description.
     *
     * POST /api/v1/git/pr-description
     *
     * @param request PR description generation request
     * @return AI-generated PR description
     */
    @PostMapping("/pr-description")
    public ResponseEntity<PRDescriptionResponse> generatePRDescription(
            @Valid @RequestBody PRDescriptionRequest request) {

        log.info("Generating PR description for project: {}, branch: {}",
            request.getProjectId(), request.getBranch());

        try {
            PRDescriptionResponse response = prDescriptionService.generatePRDescription(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generating PR description", e);

            PRDescriptionResponse errorResponse = PRDescriptionResponse.builder()
                .title("Pull Request")
                .summary("Error generating PR description")
                .fullDescription("Error generating PR description. Please try again.")
                .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Perform AI code review.
     *
     * POST /api/v1/git/code-review
     *
     * @param request Code review request
     * @return AI code review results
     */
    @PostMapping("/code-review")
    public ResponseEntity<CodeReviewResponse> performCodeReview(
            @Valid @RequestBody CodeReviewRequest request) {

        log.info("Performing code review for project: {}", request.getProjectId());

        try {
            CodeReviewResponse response = codeReviewService.reviewCode(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error performing code review", e);

            CodeReviewResponse errorResponse = CodeReviewResponse.builder()
                .summary("Error performing code review")
                .overallScore(0)
                .approvalStatus(CodeReviewResponse.ApprovalStatus.NEEDS_WORK)
                .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Get current branch.
     *
     * GET /api/v1/git/branch/{projectId}
     *
     * @param projectId Project ID
     * @return Current branch name
     */
    @GetMapping("/branch/{projectId}")
    public ResponseEntity<Map<String, String>> getCurrentBranch(@PathVariable UUID projectId) {
        try {
            String branch = gitService.getCurrentBranch(projectId);
            return ResponseEntity.ok(Map.of("branch", branch));
        } catch (Exception e) {
            log.error("Error getting current branch", e);
            return ResponseEntity.ok(Map.of("branch", "main"));
        }
    }

    /**
     * Get all branches.
     *
     * GET /api/v1/git/branches/{projectId}
     *
     * @param projectId Project ID
     * @return List of branches
     */
    @GetMapping("/branches/{projectId}")
    public ResponseEntity<List<String>> getBranches(@PathVariable UUID projectId) {
        try {
            List<String> branches = gitService.getBranches(projectId);
            return ResponseEntity.ok(branches);
        } catch (Exception e) {
            log.error("Error getting branches", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get recent commits.
     *
     * GET /api/v1/git/commits/{projectId}
     *
     * @param projectId Project ID
     * @param limit Number of commits to retrieve
     * @return List of recent commit messages
     */
    @GetMapping("/commits/{projectId}")
    public ResponseEntity<List<String>> getRecentCommits(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            List<String> commits = gitService.getRecentCommits(projectId, limit);
            return ResponseEntity.ok(commits);
        } catch (Exception e) {
            log.error("Error getting commits", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Check if project is a git repository.
     *
     * GET /api/v1/git/is-repo/{projectId}
     *
     * @param projectId Project ID
     * @return Whether the project is a git repository
     */
    @GetMapping("/is-repo/{projectId}")
    public ResponseEntity<Map<String, Boolean>> isGitRepository(@PathVariable UUID projectId) {
        try {
            boolean isRepo = gitService.isGitRepository(projectId);
            return ResponseEntity.ok(Map.of("isGitRepository", isRepo));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("isGitRepository", false));
        }
    }

    /**
     * Health check endpoint.
     *
     * GET /api/v1/git/health
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Git intelligence service is healthy");
    }
}
