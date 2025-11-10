package com.cola.agent.controller;

import com.cola.agent.dto.ApiResponse;
import com.cola.agent.dto.ProjectCreateRequest;
import com.cola.agent.dto.ProjectResponse;
import com.cola.agent.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for project management operations.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management APIs")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectCreateRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        ProjectResponse response = projectService.createProject(request, userId);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Project created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all projects for the user")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getAllProjects(
            @RequestHeader("X-User-Id") UUID userId) {

        List<ProjectResponse> projects = projectService.getAllProjects(userId);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        ProjectResponse project = projectService.getProject(id, userId);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectCreateRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        ProjectResponse response = projectService.updateProject(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        projectService.deleteProject(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully", null));
    }
}
