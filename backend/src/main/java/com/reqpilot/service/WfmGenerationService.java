package com.reqpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiJsonValidator;
import com.reqpilot.ai.AiProvider;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.ai.AiRequest;
import com.reqpilot.ai.AiResponse;
import com.reqpilot.ai.WfmPrompts;
import com.reqpilot.config.AiProperties;
import com.reqpilot.dto.RequirementAnalysisDto;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.wfm.WfmDefinitionNormalizer;
import com.reqpilot.wfm.WfmNormalizationResult;
import com.reqpilot.wfm.WfmQualityReport;
import com.reqpilot.wfm.WfmSemanticValidator;
import com.reqpilot.wfm.WfmValidator;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WfmGenerationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WfmGenerationService.class);
  private static final double WFM_GENERATION_TEMPERATURE = 0.1;

  private final AiProvider aiProvider;
  private final AiProperties aiProperties;
  private final AiJsonValidator jsonValidator;
  private final AiUsageLogService usageLogService;
  private final ObjectMapper objectMapper;
  private final WfmValidator wfmValidator;
  private final WfmSemanticValidator wfmSemanticValidator;
  private final WfmDefinitionNormalizer wfmDefinitionNormalizer;

  @Autowired
  public WfmGenerationService(
      AiProvider aiProvider,
      AiProperties aiProperties,
      AiJsonValidator jsonValidator,
      AiUsageLogService usageLogService,
      ObjectMapper objectMapper,
      WfmValidator wfmValidator,
      WfmSemanticValidator wfmSemanticValidator,
      WfmDefinitionNormalizer wfmDefinitionNormalizer) {
    this.aiProvider = aiProvider;
    this.aiProperties = aiProperties;
    this.jsonValidator = jsonValidator;
    this.usageLogService = usageLogService;
    this.objectMapper = objectMapper;
    this.wfmValidator = wfmValidator;
    this.wfmSemanticValidator = wfmSemanticValidator;
    this.wfmDefinitionNormalizer = wfmDefinitionNormalizer;
  }

  public WfmGenerationService(
      AiProvider aiProvider,
      AiProperties aiProperties,
      AiJsonValidator jsonValidator,
      AiUsageLogService usageLogService,
      ObjectMapper objectMapper,
      WfmValidator wfmValidator) {
    this(
        aiProvider,
        aiProperties,
        jsonValidator,
        usageLogService,
        objectMapper,
        wfmValidator,
        new WfmSemanticValidator(),
        new WfmDefinitionNormalizer());
  }

  public WfmDefinition generateFromRequirementAnalysis(RequirementAnalysisDto requirementAnalysis) {
    if (requirementAnalysis == null) {
      throw new IllegalArgumentException("Requirement analysis is required");
    }
    return generateFromRequirementAnalysisJson(serialize(requirementAnalysis));
  }

  public WfmDefinition generateFromRequirementAnalysisJson(String requirementAnalysisJson) {
    if (requirementAnalysisJson == null || requirementAnalysisJson.isBlank()) {
      throw new IllegalArgumentException("Requirement analysis JSON is required");
    }

    RequirementAnalysisDto requirementAnalysis = parseRequirementAnalysisJson(requirementAnalysisJson);
    return generateFromValidatedJson(serialize(requirementAnalysis));
  }

  private WfmDefinition generateFromValidatedJson(String requirementAnalysisJson) {
    AiResponse response =
        aiProvider.generate(
            new AiRequest(
                WfmPrompts.TASK_TYPE,
                WfmPrompts.systemPromptForWfmGeneration(),
                WfmPrompts.userPrompt(requirementAnalysisJson),
                aiProperties.effectiveModel(),
                aiProperties.effectiveMaxTokens(),
                WFM_GENERATION_TEMPERATURE));
    WfmDefinition definition =
        jsonValidator.parseObject(response.content(), WfmDefinition.class, response.provider());
    WfmDefinition validDraft = wfmValidator.validateDefinitionOrThrow(definition, response.provider());
    WfmQualityReport draftQuality = wfmSemanticValidator.validate(validDraft);
    if (!draftQuality.valid() && !draftQuality.repairable()) {
      throw invalidSemanticWfm(response.provider(), draftQuality);
    }

    WfmNormalizationResult normalizationResult = wfmDefinitionNormalizer.normalize(validDraft);
    WfmDefinition normalized =
        wfmValidator.validateDefinitionOrThrow(normalizationResult.wfm(), response.provider());
    WfmQualityReport normalizedQuality = wfmSemanticValidator.validate(normalized);
    if (!normalizedQuality.valid()) {
      throw invalidSemanticWfm(response.provider(), normalizedQuality);
    }

    logQualityReport(draftQuality, normalizationResult.report(), normalizedQuality);
    usageLogService.log(
        new AiUsageLogEntry(
            null,
            null,
            WfmPrompts.TASK_TYPE,
            response.provider(),
            response.model(),
            response.promptTokens(),
            response.completionTokens(),
            response.totalTokens(),
            Instant.now()));
    return normalized;
  }

  private AiProviderException invalidSemanticWfm(String provider, WfmQualityReport report) {
    return new AiProviderException(
        com.reqpilot.ai.AiErrorType.INVALID_RESPONSE,
        provider,
        "AI response WFM has semantic quality issues: " + report.errors());
  }

  private void logQualityReport(
      WfmQualityReport draftQuality,
      WfmQualityReport normalizationReport,
      WfmQualityReport normalizedQuality) {
    if (!draftQuality.warnings().isEmpty()) {
      LOGGER.warn("Draft WFM semantic warnings: {}", draftQuality.warnings());
    }
    if (!normalizationReport.repairs().isEmpty()) {
      LOGGER.info("WFM normalization repairs: {}", normalizationReport.repairs());
    }
    if (!normalizedQuality.warnings().isEmpty()) {
      LOGGER.warn("Normalized WFM semantic warnings: {}", normalizedQuality.warnings());
    }
  }

  private RequirementAnalysisDto parseRequirementAnalysisJson(String requirementAnalysisJson) {
    try {
      JsonNode node = objectMapper.readTree(requirementAnalysisJson);
      if (!node.isObject()) {
        throw new IllegalArgumentException("Requirement analysis JSON must be an object");
      }
      return objectMapper.treeToValue(node, RequirementAnalysisDto.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Requirement analysis JSON is invalid", exception);
    }
  }

  private String serialize(RequirementAnalysisDto requirementAnalysis) {
    try {
      return objectMapper.writeValueAsString(requirementAnalysis);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize requirement analysis", exception);
    }
  }
}
