package com.reqpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.config.AiProperties;
import com.reqpilot.config.WfmGenerationProperties;
import com.reqpilot.dto.FlowchartDto;
import com.reqpilot.dto.GenerationMetadataDto;
import com.reqpilot.dto.RequirementMapper;
import com.reqpilot.dto.RequirementResponse;
import com.reqpilot.dto.SavedRequirementGenerationResponse;
import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.model.Requirement;
import com.reqpilot.model.RequirementStatus;
import com.reqpilot.repository.RequirementRepository;
import com.reqpilot.wfmclient.WfmGenerationClient;
import com.reqpilot.wfmclient.WfmServiceContext;
import com.reqpilot.wfmclient.WfmServiceGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceGenerateResponse;
import com.reqpilot.wfmclient.WfmServiceMetadata;
import com.reqpilot.wfmclient.WfmServiceOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedRequirementGenerationService {

  private final RequirementRepository requirementRepository;
  private final RequirementService requirementService;
  private final WfmGenerationClient wfmGenerationClient;
  private final AiProperties aiProperties;
  private final WfmGenerationProperties generationProperties;
  private final RequirementMapper requirementMapper;
  private final ObjectMapper objectMapper;

  public SavedRequirementGenerationService(
      RequirementRepository requirementRepository,
      RequirementService requirementService,
      WfmGenerationClient wfmGenerationClient,
      AiProperties aiProperties,
      WfmGenerationProperties generationProperties,
      RequirementMapper requirementMapper,
      ObjectMapper objectMapper) {
    this.requirementRepository = requirementRepository;
    this.requirementService = requirementService;
    this.wfmGenerationClient = wfmGenerationClient;
    this.aiProperties = aiProperties;
    this.generationProperties = generationProperties;
    this.requirementMapper = requirementMapper;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public SavedRequirementGenerationResponse generateFlow(UUID requirementId) {
    Requirement requirement =
        requirementRepository
            .findById(requirementId)
            .orElseThrow(() -> new ResourceNotFoundException("Requirement not found"));

    if (requirement.getRequirementText() == null || requirement.getRequirementText().isBlank()) {
      throw new IllegalArgumentException("Requirement text must not be blank");
    }

    Instant startedAt = Instant.now();
    WfmServiceGenerateResponse response =
        wfmGenerationClient.generate(
            new WfmServiceGenerateRequest(
                requirement.getRequirementText(),
                new WfmServiceContext(
                    requirement.getProjectId().toString(),
                    requirement.getId().toString(),
                    null,
                    null),
                new WfmServiceOptions(
                    generationProperties.mode().name(),
                    generationProperties.version(),
                    aiProperties.effectiveModel(),
                    aiProperties.effectiveTemperature())));

    FlowchartDto flowchart = requirementMapper.toDto(response.flowchart());
    GenerationMetadataDto metadata = GenerationMetadataDto.from(metadata(response.metadata(), startedAt));

    requirement.setWfmVersion(wfmVersion(response.wfm()));
    requirement.setWfmJson(toJson(response.wfm()));
    requirement.setFlowchartJson(toJson(flowchart));
    requirement.setMetadataJson(toJson(metadata));
    requirement.setStatus(RequirementStatus.GENERATED);

    RequirementResponse requirementResponse = requirementService.toResponse(requirement);
    return new SavedRequirementGenerationResponse(requirementResponse, response.wfm(), flowchart, metadata);
  }

  private String wfmVersion(JsonNode wfm) {
    JsonNode version = wfm == null ? null : wfm.path("wfmVersion");
    return version != null && version.isTextual() && !version.asText().isBlank()
        ? version.asText()
        : generationProperties.version();
  }

  private GenerationMetadata metadata(WfmServiceMetadata metadata, Instant startedAt) {
    return GenerationMetadata.workflowEngine(
        valueOrDefault(metadata == null ? null : metadata.generationMode(), generationProperties.mode().name()),
        aiProperties.effectiveProvider().toUpperCase(java.util.Locale.ROOT),
        valueOrDefault(metadata == null ? null : metadata.model(), aiProperties.effectiveModel()),
        valueOrDefault(metadata == null ? null : metadata.promptVersion(), aiProperties.promptVersion()),
        valueOrDefault(metadata == null ? null : metadata.wfmSource(), "python-workflow-engine"),
        valueOrDefault(metadata == null ? null : metadata.flowchartSource(), "python-wfm-v2-flowchart-mapper"),
        valueOrDefault(metadata == null ? null : metadata.validationStatus(), "PASSED"),
        valueOrDefault(metadata == null ? null : metadata.canonicalizationStatus(), "PASSED"),
        valueOrDefault(metadata == null ? null : metadata.normalizationStatus(), "PASSED"),
        valueOrDefault(metadata == null ? null : metadata.mappingStatus(), "PASSED"),
        warnings(metadata),
        Duration.between(startedAt, Instant.now()).toMillis());
  }

  private List<String> warnings(WfmServiceMetadata metadata) {
    return metadata == null || metadata.warnings() == null ? List.of() : metadata.warnings();
  }

  private String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize requirement artifact", exception);
    }
  }
}
