package com.reqpilot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record WfmDefinition(
    @NotBlank String workflowName,
    @NotBlank String version,
    String summary,
    List<String> actors,
    @Valid List<WfmNode> nodes,
    @Valid List<WfmEdge> edges,
    List<String> businessRules,
    List<String> validations,
    List<String> assumptions,
    List<String> edgeCases,
    List<String> openQuestions,
    String riskLevel) {

  public WfmDefinition {
    actors = actors == null ? List.of() : List.copyOf(actors);
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
    businessRules = businessRules == null ? List.of() : List.copyOf(businessRules);
    validations = validations == null ? List.of() : List.copyOf(validations);
    assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    edgeCases = edgeCases == null ? List.of() : List.copyOf(edgeCases);
    openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
  }
}
