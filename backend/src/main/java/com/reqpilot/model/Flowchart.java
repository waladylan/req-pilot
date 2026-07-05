package com.reqpilot.model;

import java.util.List;
import java.util.Objects;

public record Flowchart(List<FlowNode> nodes, List<FlowEdge> edges, String mermaid) {

  public Flowchart {
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes are required"));
    edges = List.copyOf(Objects.requireNonNull(edges, "edges are required"));
    Objects.requireNonNull(mermaid, "mermaid is required");
  }
}
