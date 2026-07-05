package com.reqpilot.dto;

import com.reqpilot.model.GenerationMetadata;
import java.util.List;

public record GenerationMetadataDto(
    String source,
    String analyzer,
    String provider,
    String model,
    String promptVersion,
    Boolean cacheHit,
    Boolean fallbackUsed,
    String fallbackReason,
    List<String> warnings,
    List<String> validationWarnings,
    List<String> validationErrors,
    Long latencyMs,
    String generationMode,
    String originalGenerationMode,
    String wfmSource,
    String flowchartSource,
    String validationStatus,
    String canonicalizationStatus,
    String normalizationStatus,
    String mappingStatus) {

  public GenerationMetadataDto {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    validationWarnings = validationWarnings == null ? List.of() : List.copyOf(validationWarnings);
    validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
  }

  public GenerationMetadataDto(String source, List<String> warnings) {
    this(
        source,
        source,
        null,
        null,
        null,
        false,
        false,
        null,
        warnings,
        warnings,
        List.of(),
        null,
        source,
        null,
        null,
        null,
        "PASSED",
        null,
        "PASSED",
        null);
  }

  public static GenerationMetadataDto from(GenerationMetadata metadata) {
    return new GenerationMetadataDto(
        metadata.source(),
        metadata.analyzer(),
        metadata.provider(),
        metadata.model(),
        metadata.promptVersion(),
        metadata.cacheHit(),
        metadata.fallbackUsed(),
        metadata.fallbackReason(),
        metadata.warnings(),
        metadata.validationWarnings(),
        metadata.validationErrors(),
        metadata.latencyMs(),
        metadata.generationMode(),
        metadata.originalGenerationMode(),
        metadata.wfmSource(),
        metadata.flowchartSource(),
        metadata.validationStatus(),
        metadata.canonicalizationStatus(),
        metadata.normalizationStatus(),
        metadata.mappingStatus());
  }
}
