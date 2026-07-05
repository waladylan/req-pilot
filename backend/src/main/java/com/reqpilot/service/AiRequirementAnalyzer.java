package com.reqpilot.service;

import com.reqpilot.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmNormalizer;
import com.reqpilot.wfm.WfmToFlowchartMapper;
import com.reqpilot.wfm.WfmValidationError;
import com.reqpilot.wfm.WfmValidator;
import com.reqpilot.wfmclient.WfmGenerationClient;
import com.reqpilot.wfmclient.WfmServiceContext;
import com.reqpilot.wfmclient.WfmServiceGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceGenerateResponse;
import com.reqpilot.wfmclient.WfmServiceMetadata;
import com.reqpilot.wfmclient.WfmServiceOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiRequirementAnalyzer implements WfmRequirementAnalyzer {

  private static final Logger LOGGER = LoggerFactory.getLogger(AiRequirementAnalyzer.class);

  private final AiProperties aiProperties;
  private final WfmGenerationClient wfmGenerationClient;
  private final WfmNormalizer wfmNormalizer;
  private final WfmValidator wfmValidator;
  private final WfmToFlowchartMapper flowchartMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AiRequirementAnalyzer(
      AiProperties aiProperties,
      WfmGenerationClient wfmGenerationClient,
      WfmNormalizer wfmNormalizer,
      WfmValidator wfmValidator,
      WfmToFlowchartMapper flowchartMapper) {
    this.aiProperties = aiProperties;
    this.wfmGenerationClient = wfmGenerationClient;
    this.wfmNormalizer = wfmNormalizer;
    this.wfmValidator = wfmValidator;
    this.flowchartMapper = flowchartMapper;
  }

  @Override
  public Flowchart analyze(String requirement) {
    return flowchartMapper.toFlowchart(analyzeToWfm(requirement));
  }

  @Override
  public WfmDocument analyzeToWfm(String requirement) {
    return analyzeRequirement(requirement).wfm();
  }

  @Override
  public RequirementAnalysis analyzeRequirement(String requirement) {
    Instant startedAt = Instant.now();
    WfmServiceGenerateResponse response =
        wfmGenerationClient.generate(
            new WfmServiceGenerateRequest(
                requirement,
                new WfmServiceContext(null, null, null),
                new WfmServiceOptions(null, "1.0", aiProperties.effectiveModel(), aiProperties.effectiveTemperature())));
    WfmDocument validWfm = normalizeAndValidate(toWfmDocument(response.wfm()));
    List<String> validationWarnings = combinedWarnings(response.metadata(), validationWarnings(validWfm));
    GenerationMetadata metadata =
        aiMetadata(
            configuredProvider(),
            model(response.metadata()),
            promptVersion(response.metadata()),
            validationWarnings,
            List.of(),
            latencyMs(startedAt));
    logCompletion(metadata);
    return new RequirementAnalysis(validWfm, metadata);
  }

  private WfmDocument normalizeAndValidate(WfmDocument wfm) {
    WfmDocument normalized = wfmNormalizer.normalize(wfm);
    return wfmValidator.validateOrThrow(normalized);
  }

  private WfmDocument toWfmDocument(com.fasterxml.jackson.databind.JsonNode wfm) {
    try {
      return objectMapper.treeToValue(wfm, WfmDocument.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Python WFM service returned non-v1 WFM for legacy analyzer", exception);
    }
  }

  private GenerationMetadata aiMetadata(
      String provider,
      String model,
      String promptVersion,
      List<String> validationWarnings,
      List<String> validationErrors,
      Long latencyMs) {
    return GenerationMetadata.ai(
        provider,
        model,
        promptVersion,
        false,
        false,
        null,
        validationWarnings,
        validationWarnings,
        validationErrors,
        latencyMs);
  }

  private String configuredProvider() {
    return aiProperties.effectiveProvider().toUpperCase(java.util.Locale.ROOT);
  }

  private List<String> validationWarnings(WfmDocument wfm) {
    return wfmValidator.validate(wfm).warnings().stream().map(WfmValidationError::message).toList();
  }

  private List<String> combinedWarnings(WfmServiceMetadata metadata, List<String> javaValidationWarnings) {
    List<String> warnings = new ArrayList<>();
    if (metadata != null && metadata.warnings() != null) {
      warnings.addAll(metadata.warnings());
    }
    warnings.addAll(javaValidationWarnings);
    return List.copyOf(warnings);
  }

  private String model(WfmServiceMetadata metadata) {
    return metadata != null && hasText(metadata.model()) ? metadata.model() : aiProperties.effectiveModel();
  }

  private String promptVersion(WfmServiceMetadata metadata) {
    return metadata != null && hasText(metadata.promptVersion())
        ? metadata.promptVersion()
        : aiProperties.promptVersion();
  }

  private long latencyMs(Instant startedAt) {
    return Duration.between(startedAt, Instant.now()).toMillis();
  }

  private void logCompletion(GenerationMetadata metadata) {
    LOGGER.info(
        "AI requirement analysis completed provider={} model={} cacheHit={} fallbackUsed={} latencyMs={}",
        metadata.provider(),
        metadata.model(),
        metadata.cacheHit(),
        metadata.fallbackUsed(),
        metadata.latencyMs());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
