package com.reqpilot.dto;

import java.util.List;

public record TestCaseGenerationMetadataDto(
    String source, String workflowId, int pathCount, List<String> warnings) {

  public TestCaseGenerationMetadataDto {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
