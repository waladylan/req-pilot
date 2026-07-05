package com.reqpilot.model;

import java.util.ArrayList;
import java.util.List;

public record GenerationMetadata(
    String source,
    String analyzer,
    String provider,
    String model,
    String promptVersion,
    boolean cacheHit,
    boolean fallbackUsed,
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

  public GenerationMetadata {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    validationWarnings = validationWarnings == null ? List.of() : List.copyOf(validationWarnings);
    validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    if (generationMode == null || generationMode.isBlank()) {
      generationMode = source;
    }
  }

  public static GenerationMetadata ruleBased(List<String> validationWarnings) {
    return new GenerationMetadata(
        "RULE_BASED",
        "RULE_BASED",
        null,
        null,
        null,
        false,
        false,
        null,
        validationWarnings,
        validationWarnings,
        List.of(),
        null,
        "RULE_BASED",
        null,
        "spring-boot-rule-engine",
        null,
        "PASSED",
        null,
        "PASSED",
        null);
  }

  public static GenerationMetadata ai(
      String provider,
      String model,
      String promptVersion,
      boolean cacheHit,
      boolean fallbackUsed,
      String fallbackReason,
      List<String> warnings,
      List<String> validationWarnings,
      List<String> validationErrors,
      Long latencyMs) {
    return new GenerationMetadata(
        "AI",
        "AI",
        provider,
        model,
        promptVersion,
        cacheHit,
        fallbackUsed,
        fallbackReason,
        warnings,
        validationWarnings,
        validationErrors,
        latencyMs,
        "AI",
        null,
        "python-wfm-service",
        null,
        "PASSED",
        null,
        "PASSED",
        null);
  }

  public static GenerationMetadata workflowEngine(
      String generationMode,
      String provider,
      String model,
      String promptVersion,
      String wfmSource,
      String flowchartSource,
      String validationStatus,
      String canonicalizationStatus,
      String normalizationStatus,
      String mappingStatus,
      List<String> warnings,
      Long latencyMs) {
    String source = generationMode == null || generationMode.isBlank() ? "AI" : generationMode;
    return new GenerationMetadata(
        source,
        source,
        provider,
        model,
        promptVersion,
        false,
        false,
        null,
        warnings,
        List.of(),
        List.of(),
        latencyMs,
        source,
        null,
        wfmSource,
        flowchartSource,
        validationStatus,
        canonicalizationStatus,
        normalizationStatus,
        mappingStatus);
  }

  public GenerationMetadata withValidation(List<String> validationWarnings, List<String> validationErrors) {
    List<String> mergedWarnings = mergeWarnings(warnings, validationWarnings);
    List<String> safeValidationErrors = validationErrors == null ? List.of() : validationErrors;
    String nextValidationStatus = safeValidationErrors.isEmpty() ? "PASSED" : "FAILED";
    return new GenerationMetadata(
        source,
        analyzer,
        provider,
        model,
        promptVersion,
        cacheHit,
        fallbackUsed,
        fallbackReason,
        mergedWarnings,
        validationWarnings,
        validationErrors,
        latencyMs,
        generationMode,
        originalGenerationMode,
        wfmSource,
        flowchartSource,
        nextValidationStatus,
        canonicalizationStatus,
        normalizationStatus,
        mappingStatus);
  }

  public GenerationMetadata asRuleBasedFallback(String reason) {
    return new GenerationMetadata(
        "RULE_BASED_FALLBACK",
        "RULE_BASED",
        provider,
        model,
        promptVersion,
        cacheHit,
        true,
        reason,
        warnings,
        validationWarnings,
        validationErrors,
        latencyMs,
        "RULE_BASED_FALLBACK",
        "AI",
        "spring-boot-rule-engine",
        flowchartSource,
        validationStatus,
        canonicalizationStatus,
        normalizationStatus,
        mappingStatus);
  }

  private static List<String> mergeWarnings(List<String> existingWarnings, List<String> nextValidationWarnings) {
    List<String> merged = new ArrayList<>();
    addAllUnique(merged, existingWarnings);
    addAllUnique(merged, nextValidationWarnings);
    return List.copyOf(merged);
  }

  private static void addAllUnique(List<String> target, List<String> values) {
    if (values == null) {
      return;
    }
    for (String value : values) {
      if (value != null && !value.isBlank() && !target.contains(value)) {
        target.add(value);
      }
    }
  }
}
