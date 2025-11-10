package com.cola.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about Git diff/changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitDiffInfo {

    // Files that were modified
    private List<FileChange> modifiedFiles;

    // Files that were added
    private List<FileChange> addedFiles;

    // Files that were deleted
    private List<FileChange> deletedFiles;

    // Total lines added
    private int linesAdded;

    // Total lines deleted
    private int linesDeleted;

    // Raw diff output
    private String rawDiff;

    // Commit hash (if comparing with a specific commit)
    private String baseCommit;

    /**
     * Individual file change information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileChange {
        private String filePath;
        private String changeType; // ADDED, MODIFIED, DELETED, RENAMED
        private int linesAdded;
        private int linesDeleted;
        private String diffContent;
        private String oldPath; // For renamed files
    }

    /**
     * Get total number of files changed.
     */
    public int getTotalFilesChanged() {
        return (modifiedFiles != null ? modifiedFiles.size() : 0) +
               (addedFiles != null ? addedFiles.size() : 0) +
               (deletedFiles != null ? deletedFiles.size() : 0);
    }
}
