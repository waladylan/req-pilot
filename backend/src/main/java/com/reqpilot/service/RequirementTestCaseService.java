package com.reqpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.dto.RequirementResponse;
import com.reqpilot.dto.RequirementTestCasesResponse;
import com.reqpilot.model.Requirement;
import com.reqpilot.repository.RequirementRepository;
import com.reqpilot.wfmclient.WfmServiceTestCaseContext;
import com.reqpilot.wfmclient.WfmServiceTestCaseGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceTestCaseGenerateResponse;
import com.reqpilot.wfmclient.WfmServiceTestCaseOptions;
import com.reqpilot.wfmclient.WfmTestCaseGenerationClient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementTestCaseService {

  private final RequirementRepository requirementRepository;
  private final RequirementService requirementService;
  private final WfmTestCaseGenerationClient testCaseGenerationClient;
  private final ObjectMapper objectMapper;

  public RequirementTestCaseService(
      RequirementRepository requirementRepository,
      RequirementService requirementService,
      WfmTestCaseGenerationClient testCaseGenerationClient,
      ObjectMapper objectMapper) {
    this.requirementRepository = requirementRepository;
    this.requirementService = requirementService;
    this.testCaseGenerationClient = testCaseGenerationClient;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public RequirementTestCasesResponse generateTestCases(UUID requirementId) {
    Requirement requirement = getRequirement(requirementId);
    JsonNode wfm = savedWfmV2(requirement);

    WfmServiceTestCaseGenerateResponse response =
        testCaseGenerationClient.generateTestCases(
            new WfmServiceTestCaseGenerateRequest(
                wfm,
                new WfmServiceTestCaseContext(
                    requirement.getProjectId().toString(), requirement.getId().toString(), null),
                new WfmServiceTestCaseOptions()));

    if (response.testCaseSet() == null) {
      throw new IllegalStateException("Python workflow engine returned no testCaseSet");
    }

    Instant now = Instant.now();
    requirement.setTestCasesJson(toJson(response.testCaseSet()));
    requirement.setTestCaseMetadataJson(toJson(response.metadata()));
    requirement.setTestCasesGeneratedAt(now);
    requirement.setTestCasesUpdatedAt(now);

    RequirementResponse requirementResponse = requirementService.toResponse(requirement);
    return new RequirementTestCasesResponse(
        requirementResponse, response.testCaseSet(), response.metadata());
  }

  @Transactional(readOnly = true)
  public RequirementTestCasesResponse getTestCases(UUID requirementId) {
    Requirement requirement = getRequirement(requirementId);
    RequirementResponse requirementResponse = requirementService.toResponse(requirement);
    return new RequirementTestCasesResponse(
        requirementResponse, requirementResponse.testCaseSet(), requirementResponse.testCaseMetadata());
  }

  private Requirement getRequirement(UUID requirementId) {
    return requirementRepository
        .findById(requirementId)
        .orElseThrow(() -> new ResourceNotFoundException("Requirement not found"));
  }

  private JsonNode savedWfmV2(Requirement requirement) {
    if (requirement.getProjectId() == null) {
      throw new IllegalArgumentException("Requirement must belong to a project");
    }
    if (requirement.getWfmJson() == null || requirement.getWfmJson().isBlank()) {
      throw new IllegalArgumentException("Generate flow first before generating test cases.");
    }
    if (!"2.0".equals(requirement.getWfmVersion())) {
      throw new IllegalArgumentException("Generate WFM v2 flow before generating test cases.");
    }

    JsonNode wfm = parseJson(requirement.getWfmJson());
    if (!"2.0".equals(wfm.path("wfmVersion").asText())) {
      throw new IllegalArgumentException("Generate WFM v2 flow before generating test cases.");
    }
    return wfm;
  }

  private JsonNode parseJson(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored WFM JSON is invalid", exception);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize test case artifact", exception);
    }
  }
}
