package com.cola.agent.dto;

import com.cola.agent.model.Project;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProjectResponse {
    private UUID id;
    private String name;
    private String description;
    private String language;
    private String framework;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long fileCount;
    private long conversationCount;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
            .id(project.getId())
            .name(project.getName())
            .description(project.getDescription())
            .language(project.getLanguage())
            .framework(project.getFramework())
            .status(project.getStatus().name())
            .createdAt(project.getCreatedAt())
            .updatedAt(project.getUpdatedAt())
            .fileCount(project.getGeneratedFiles() != null ? project.getGeneratedFiles().size() : 0)
            .conversationCount(project.getConversations() != null ? project.getConversations().size() : 0)
            .build();
    }
}
