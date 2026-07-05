package com.reqpilot.dto;

import java.util.List;

public record ReactFlowDefinition(
    String workflowName,
    String version,
    String format,
    String direction,
    List<ReactFlowNode> nodes,
    List<ReactFlowEdge> edges,
    List<String> warnings) {

  public ReactFlowDefinition {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
