package com.reqpilot.service;

import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.config.AiProperties;
import com.reqpilot.config.WfmGenerationProperties;
import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.model.WfmGenerationResult;
import com.reqpilot.wfmclient.WfmGenerationClient;
import com.reqpilot.wfmclient.WfmServiceContext;
import com.reqpilot.wfmclient.WfmServiceGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceGenerateResponse;
import com.reqpilot.wfmclient.WfmServiceMetadata;
import com.reqpilot.wfmclient.WfmServiceOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PythonAiWfmGenerator implements WfmGenerator {

  private static final String PROVIDER = "PYTHON_WFM_SERVICE";

  private final AiProperties aiProperties;
  private final WfmGenerationProperties generationProperties;
  private final WfmGenerationClient wfmGenerationClient;

  public PythonAiWfmGenerator(
      AiProperties aiProperties,
      WfmGenerationProperties generationProperties,
      WfmGenerationClient wfmGenerationClient) {
    this.aiProperties = aiProperties;
    this.generationProperties = generationProperties;
    this.wfmGenerationClient = wfmGenerationClient;
  }

  @Override
  public WfmGenerationResult generate(String requirement) {
    Instant startedAt = Instant.now();
    WfmServiceGenerateResponse response =
        wfmGenerationClient.generate(
            new WfmServiceGenerateRequest(
                requirement,
                new WfmServiceContext(null, null, null),
                new WfmServiceOptions(
                    generationProperties.mode().name(),
                    generationProperties.version(),
                    aiProperties.effectiveModel(),
                    aiProperties.effectiveTemperature())));

    if (response == null || response.wfm() == null || response.flowchart() == null) {
      throw new AiProviderException(
          AiErrorType.INVALID_RESPONSE, PROVIDER, "Python workflow engine returned an incomplete workflow");
    }

    return new WfmGenerationResult(response.wfm(), response.flowchart(), metadata(response.metadata(), startedAt));
  }

  private GenerationMetadata metadata(WfmServiceMetadata metadata, Instant startedAt) {
    return GenerationMetadata.workflowEngine(
        generationMode(metadata),
        configuredProvider(),
        model(metadata),
        promptVersion(metadata),
        wfmSource(metadata),
        flowchartSource(metadata),
        statusOrDefault(metadata == null ? null : metadata.validationStatus()),
        statusOrDefault(metadata == null ? null : metadata.canonicalizationStatus()),
        statusOrDefault(metadata == null ? null : metadata.normalizationStatus()),
        statusOrDefault(metadata == null ? null : metadata.mappingStatus()),
        warnings(metadata),
        latencyMs(startedAt));
  }

  private String configuredProvider() {
    return aiProperties.effectiveProvider().toUpperCase(java.util.Locale.ROOT);
  }

  private String model(WfmServiceMetadata metadata) {
    return metadata != null && hasText(metadata.model()) ? metadata.model() : aiProperties.effectiveModel();
  }

  private String promptVersion(WfmServiceMetadata metadata) {
    return metadata != null && hasText(metadata.promptVersion())
        ? metadata.promptVersion()
        : aiProperties.promptVersion();
  }

  private String generationMode(WfmServiceMetadata metadata) {
    return metadata != null && hasText(metadata.generationMode())
        ? metadata.generationMode()
        : generationProperties.mode().name();
  }

  private String wfmSource(WfmServiceMetadata metadata) {
    return metadata != null && hasText(metadata.wfmSource())
        ? metadata.wfmSource()
        : "python-workflow-engine";
  }

  private String flowchartSource(WfmServiceMetadata metadata) {
    return metadata != null && hasText(metadata.flowchartSource())
        ? metadata.flowchartSource()
        : "python-flowchart-mapper";
  }

  private String statusOrDefault(String value) {
    return hasText(value) ? value : "PASSED";
  }

  private List<String> warnings(WfmServiceMetadata metadata) {
    return metadata == null || metadata.warnings() == null ? List.of() : metadata.warnings();
  }

  private long latencyMs(Instant startedAt) {
    return Duration.between(startedAt, Instant.now()).toMillis();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
