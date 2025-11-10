package com.cola.agent.service;

import com.cola.agent.dto.ProjectCreateRequest;
import com.cola.agent.dto.ProjectResponse;
import com.cola.agent.model.Project;
import com.cola.agent.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing projects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request, UUID userId) {
        log.info("Creating project '{}' for user {}", request.getName(), userId);

        Project project = Project.builder()
            .name(request.getName())
            .description(request.getDescription())
            .language(request.getLanguage())
            .framework(request.getFramework())
            .status(Project.ProjectStatus.PLANNING)
            .userId(userId)
            .build();

        project = projectRepository.save(project);
        log.info("Project created with ID: {}", project.getId());

        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects(UUID userId) {
        log.info("Fetching all projects for user {}", userId);
        return projectRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(ProjectResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, UUID userId) {
        log.info("Fetching project {} for user {}", projectId, userId);
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, ProjectCreateRequest request, UUID userId) {
        log.info("Updating project {} for user {}", projectId, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setLanguage(request.getLanguage());
        project.setFramework(request.getFramework());

        project = projectRepository.save(project);
        return ProjectResponse.from(project);
    }

    @Transactional
    public void deleteProject(UUID projectId, UUID userId) {
        log.info("Deleting project {} for user {}", projectId, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        projectRepository.delete(project);
        log.info("Project {} deleted successfully", projectId);
    }

    @Transactional
    public ProjectResponse updateProjectStatus(UUID projectId, Project.ProjectStatus status, UUID userId) {
        log.info("Updating project {} status to {} for user {}", projectId, status, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        project.setStatus(status);
        project = projectRepository.save(project);

        return ProjectResponse.from(project);
    }
}
