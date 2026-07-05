package com.reqpilot.dto;

import java.util.List;

public record RequirementAnalysisModuleDto(
    String name,
    String description,
    List<String> screens,
    List<String> businessRules,
    List<String> validations,
    List<String> workflowSteps,
    List<String> edgeCases) {

  public RequirementAnalysisModuleDto {
    screens = screens == null ? List.of() : List.copyOf(screens);
    businessRules = businessRules == null ? List.of() : List.copyOf(businessRules);
    validations = validations == null ? List.of() : List.copyOf(validations);
    workflowSteps = workflowSteps == null ? List.of() : List.copyOf(workflowSteps);
    edgeCases = edgeCases == null ? List.of() : List.copyOf(edgeCases);
  }
}
