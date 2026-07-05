package com.reqpilot.service;

import com.reqpilot.dto.ProjectCreateRequest;
import com.reqpilot.model.Project;
import com.reqpilot.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private final ProjectRepository projectRepository;

  public ProjectService(ProjectRepository projectRepository) {
    this.projectRepository = projectRepository;
  }

  @Transactional(readOnly = true)
  public List<Project> listProjects() {
    return projectRepository.findAll();
  }

  @Transactional(readOnly = true)
  public Project getProject(UUID projectId) {
    return projectRepository
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
  }

  @Transactional
  public Project createProject(ProjectCreateRequest request) {
    return projectRepository.save(new Project(request.name().trim(), blankToNull(request.description())));
  }

  @Transactional
  public Project updateProject(UUID projectId, ProjectCreateRequest request) {
    Project project = getProject(projectId);
    project.setName(request.name().trim());
    project.setDescription(blankToNull(request.description()));
    return project;
  }

  @Transactional
  public void deleteProject(UUID projectId) {
    Project project = getProject(projectId);
    projectRepository.delete(project);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
