package com.reqpilot.controller;

import com.reqpilot.dto.ProjectCreateRequest;
import com.reqpilot.dto.ProjectResponse;
import com.reqpilot.dto.RequirementCreateRequest;
import com.reqpilot.dto.RequirementResponse;
import com.reqpilot.model.Project;
import com.reqpilot.service.ProjectService;
import com.reqpilot.service.RequirementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

  private final ProjectService projectService;
  private final RequirementService requirementService;

  public ProjectController(ProjectService projectService, RequirementService requirementService) {
    this.projectService = projectService;
    this.requirementService = requirementService;
  }

  @GetMapping
  public List<ProjectResponse> listProjects() {
    return projectService.listProjects().stream().map(this::toResponse).toList();
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectCreateRequest request) {
    return ResponseEntity.ok(toResponse(projectService.createProject(request)));
  }

  @GetMapping("/{projectId}")
  public ProjectResponse getProject(@PathVariable UUID projectId) {
    return toResponse(projectService.getProject(projectId));
  }

  @PutMapping("/{projectId}")
  public ProjectResponse updateProject(
      @PathVariable UUID projectId, @Valid @RequestBody ProjectCreateRequest request) {
    return toResponse(projectService.updateProject(projectId, request));
  }

  @DeleteMapping("/{projectId}")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
    projectService.deleteProject(projectId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{projectId}/requirements")
  public List<RequirementResponse> listRequirements(@PathVariable UUID projectId) {
    return requirementService.listRequirements(projectId).stream()
        .map(requirementService::toResponse)
        .toList();
  }

  @PostMapping("/{projectId}/requirements")
  public ResponseEntity<RequirementResponse> createRequirement(
      @PathVariable UUID projectId, @Valid @RequestBody RequirementCreateRequest request) {
    return ResponseEntity.ok(requirementService.toResponse(requirementService.createRequirement(projectId, request)));
  }

  private ProjectResponse toResponse(Project project) {
    return new ProjectResponse(
        project.getId(),
        project.getName(),
        project.getDescription(),
        project.getCreatedAt(),
        project.getUpdatedAt());
  }
}
