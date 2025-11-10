package com.cola.agent.service;

import com.cola.agent.dto.GitDiffInfo;
import com.cola.agent.model.Project;
import com.cola.agent.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for Git repository operations.
 * Handles diff analysis, status checks, and repository information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitService {

    private final ProjectRepository projectRepository;

    @Value("${cola.storage.path:./cola-projects}")
    private String storagePath;

    /**
     * Get git diff for staged changes.
     */
    public GitDiffInfo getStagedDiff(UUID projectId) {
        return getDiff(projectId, "--cached");
    }

    /**
     * Get git diff for unstaged changes.
     */
    public GitDiffInfo getUnstagedDiff(UUID projectId) {
        return getDiff(projectId, null);
    }

    /**
     * Get git diff between two branches/commits.
     */
    public GitDiffInfo getDiffBetween(UUID projectId, String base, String head) {
        return getDiff(projectId, base + ".." + head);
    }

    /**
     * Get git diff with specified options.
     */
    private GitDiffInfo getDiff(UUID projectId, String options) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            File projectDir = new File(storagePath, project.getId().toString());

            // Build git diff command
            List<String> command = new ArrayList<>(Arrays.asList("git", "diff"));
            if (options != null) {
                command.addAll(Arrays.asList(options.split(" ")));
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

            String rawDiff = output.toString();

            // Parse diff into structured format
            return parseDiff(rawDiff, options);

        } catch (Exception e) {
            log.error("Error getting git diff", e);
            return GitDiffInfo.builder()
                .modifiedFiles(new ArrayList<>())
                .addedFiles(new ArrayList<>())
                .deletedFiles(new ArrayList<>())
                .linesAdded(0)
                .linesDeleted(0)
                .rawDiff("")
                .build();
        }
    }

    /**
     * Parse git diff output into structured format.
     */
    private GitDiffInfo parseDiff(String rawDiff, String baseCommit) {
        List<GitDiffInfo.FileChange> modifiedFiles = new ArrayList<>();
        List<GitDiffInfo.FileChange> addedFiles = new ArrayList<>();
        List<GitDiffInfo.FileChange> deletedFiles = new ArrayList<>();
        int totalLinesAdded = 0;
        int totalLinesDeleted = 0;

        if (rawDiff == null || rawDiff.trim().isEmpty()) {
            return GitDiffInfo.builder()
                .modifiedFiles(modifiedFiles)
                .addedFiles(addedFiles)
                .deletedFiles(deletedFiles)
                .linesAdded(0)
                .linesDeleted(0)
                .rawDiff("")
                .baseCommit(baseCommit)
                .build();
        }

        // Split into file diffs
        String[] fileDiffs = rawDiff.split("diff --git");

        for (String fileDiff : fileDiffs) {
            if (fileDiff.trim().isEmpty()) continue;

            // Extract file path
            Pattern filePattern = Pattern.compile("a/(.+?) b/(.+?)\\n");
            Matcher fileMatcher = filePattern.matcher(fileDiff);

            String filePath = null;
            if (fileMatcher.find()) {
                filePath = fileMatcher.group(2);
            }

            if (filePath == null) continue;

            // Count lines added/deleted
            int linesAdded = 0;
            int linesDeleted = 0;

            String[] lines = fileDiff.split("\n");
            for (String line : lines) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    linesAdded++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    linesDeleted++;
                }
            }

            totalLinesAdded += linesAdded;
            totalLinesDeleted += linesDeleted;

            // Determine change type
            String changeType;
            if (fileDiff.contains("new file mode")) {
                changeType = "ADDED";
            } else if (fileDiff.contains("deleted file mode")) {
                changeType = "DELETED";
            } else if (fileDiff.contains("rename from")) {
                changeType = "RENAMED";
            } else {
                changeType = "MODIFIED";
            }

            // Create file change object
            GitDiffInfo.FileChange fileChange = GitDiffInfo.FileChange.builder()
                .filePath(filePath)
                .changeType(changeType)
                .linesAdded(linesAdded)
                .linesDeleted(linesDeleted)
                .diffContent(fileDiff)
                .build();

            // Categorize the change
            switch (changeType) {
                case "ADDED":
                    addedFiles.add(fileChange);
                    break;
                case "DELETED":
                    deletedFiles.add(fileChange);
                    break;
                case "MODIFIED":
                case "RENAMED":
                    modifiedFiles.add(fileChange);
                    break;
            }
        }

        return GitDiffInfo.builder()
            .modifiedFiles(modifiedFiles)
            .addedFiles(addedFiles)
            .deletedFiles(deletedFiles)
            .linesAdded(totalLinesAdded)
            .linesDeleted(totalLinesDeleted)
            .rawDiff(rawDiff)
            .baseCommit(baseCommit)
            .build();
    }

    /**
     * Get git status.
     */
    public Map<String, Object> getStatus(UUID projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            File projectDir = new File(storagePath, project.getId().toString());

            ProcessBuilder pb = new ProcessBuilder("git", "status", "--porcelain");
            pb.directory(projectDir);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

            Map<String, Object> status = new HashMap<>();
            status.put("hasChanges", !output.toString().trim().isEmpty());
            status.put("status", output.toString());

            return status;

        } catch (Exception e) {
            log.error("Error getting git status", e);
            return Map.of("hasChanges", false, "status", "");
        }
    }

    /**
     * Get recent commit messages.
     */
    public List<String> getRecentCommits(UUID projectId, int limit) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            File projectDir = new File(storagePath, project.getId().toString());

            ProcessBuilder pb = new ProcessBuilder(
                "git", "log", "--format=%s", "-n", String.valueOf(limit)
            );
            pb.directory(projectDir);

            Process process = pb.start();

            List<String> commits = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    commits.add(line);
                }
            }

            process.waitFor();

            return commits;

        } catch (Exception e) {
            log.error("Error getting recent commits", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get current branch name.
     */
    public String getCurrentBranch(UUID projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            File projectDir = new File(storagePath, project.getId().toString());

            ProcessBuilder pb = new ProcessBuilder("git", "branch", "--show-current");
            pb.directory(projectDir);

            Process process = pb.start();

            String branch = "";
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                branch = reader.readLine();
            }

            process.waitFor();

            return branch != null ? branch.trim() : "main";

        } catch (Exception e) {
            log.error("Error getting current branch", e);
            return "main";
        }
    }

    /**
     * Get list of all branches.
     */
    public List<String> getBranches(UUID projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            File projectDir = new File(storagePath, project.getId().toString());

            ProcessBuilder pb = new ProcessBuilder("git", "branch", "-a");
            pb.directory(projectDir);

            Process process = pb.start();

            List<String> branches = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String branch = line.trim().replaceFirst("^\\* ", "");
                    branches.add(branch);
                }
            }

            process.waitFor();

            return branches;

        } catch (Exception e) {
            log.error("Error getting branches", e);
            return new ArrayList<>();
        }
    }

    /**
     * Check if directory is a git repository.
     */
    public boolean isGitRepository(UUID projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            File projectDir = new File(storagePath, project.getId().toString());
            File gitDir = new File(projectDir, ".git");

            return gitDir.exists() && gitDir.isDirectory();

        } catch (Exception e) {
            return false;
        }
    }
}
