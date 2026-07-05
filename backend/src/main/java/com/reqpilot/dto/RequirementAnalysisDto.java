package com.reqpilot.dto;

import java.util.List;

public record RequirementAnalysisDto(
    String summary,
    List<String> actors,
    List<RequirementAnalysisModuleDto> modules,
    List<String> assumptions,
    List<String> openQuestions,
    String riskLevel) {

  public RequirementAnalysisDto {
    actors = actors == null ? List.of() : List.copyOf(actors);
    modules = modules == null ? List.of() : List.copyOf(modules);
    assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
  }
}
