package com.reqpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.dto.RequirementCreateRequest;
import com.reqpilot.dto.RequirementResponse;
import com.reqpilot.dto.RequirementUpdateRequest;
import com.reqpilot.model.Project;
import com.reqpilot.model.Requirement;
import com.reqpilot.model.RequirementStatus;
import com.reqpilot.repository.RequirementRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementService {

  private final ProjectService projectService;
  private final RequirementRepository requirementRepository;
  private final ObjectMapper objectMapper;

  public RequirementService(
      ProjectService projectService,
      RequirementRepository requirementRepository,
      ObjectMapper objectMapper) {
    this.projectService = projectService;
    this.requirementRepository = requirementRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<Requirement> listRequirements(UUID projectId) {
    projectService.getProject(projectId);
    return requirementRepository.findByProject_IdOrderByOrderIndexAscCreatedAtAsc(projectId);
  }

  @Transactional(readOnly = true)
  public Requirement getRequirement(UUID requirementId) {
    return requirementRepository
        .findById(requirementId)
        .orElseThrow(() -> new ResourceNotFoundException("Requirement not found"));
  }

  @Transactional
  public Requirement createRequirement(UUID projectId, RequirementCreateRequest request) {
    Project project = projectService.getProject(projectId);
    int nextOrder = requirementRepository.countByProject_Id(projectId);
    Requirement requirement =
        new Requirement(project, request.title().trim(), request.requirementText().trim(), nextOrder);
    return requirementRepository.save(requirement);
  }

  @Transactional
  public Requirement updateRequirement(UUID requirementId, RequirementUpdateRequest request) {
    Requirement requirement = getRequirement(requirementId);
    if (request.title() != null) {
      if (request.title().isBlank()) {
        throw new IllegalArgumentException("Requirement title must not be blank");
      }
      requirement.setTitle(request.title().trim());
    }
    if (request.requirementText() != null) {
      if (request.requirementText().isBlank()) {
        throw new IllegalArgumentException("Requirement text must not be blank");
      }
      requirement.setRequirementText(request.requirementText().trim());
      if (requirement.getStatus() == RequirementStatus.GENERATED) {
        requirement.setStatus(RequirementStatus.EDITED);
      }
    }
    if (request.status() != null) {
      requirement.setStatus(parseStatus(request.status()));
    }
    if (request.orderIndex() != null) {
      requirement.setOrderIndex(Math.max(0, request.orderIndex()));
    }
    return requirement;
  }

  @Transactional
  public void deleteRequirement(UUID requirementId) {
    requirementRepository.delete(getRequirement(requirementId));
  }

  public RequirementResponse toResponse(Requirement requirement) {
    return new RequirementResponse(
        requirement.getId(),
        requirement.getProjectId(),
        requirement.getTitle(),
        requirement.getRequirementText(),
        requirement.getStatus().name(),
        requirement.getOrderIndex(),
        requirement.getWfmVersion(),
        parseJson(requirement.getWfmJson()),
        parseJson(requirement.getFlowchartJson()),
        parseJson(requirement.getMetadataJson()),
        parseJson(requirement.getTestCasesJson()),
        parseJson(requirement.getTestCaseMetadataJson()),
        requirement.getTestCasesGeneratedAt(),
        requirement.getTestCasesUpdatedAt(),
        requirement.getCreatedAt(),
        requirement.getUpdatedAt());
  }

  private RequirementStatus parseStatus(String value) {
    try {
      return RequirementStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unsupported requirement status: " + value);
    }
  }

  private JsonNode parseJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Stored requirement JSON is invalid", exception);
    }
  }
}
