package com.reqpilot.dto;

import java.util.List;

public record GenerateFlowchartResponse(
    String workflowName,
    String format,
    ReactFlowDefinition flowchart,
    List<String> warnings,
    GenerateFlowchartDebug debug) {

  public GenerateFlowchartResponse {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
