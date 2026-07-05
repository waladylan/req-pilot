package com.reqpilot.model;

import java.util.Objects;

public record FlowEdge(String id, String source, String target, String label, FlowEdgeType type) {

  public FlowEdge(String id, String source, String target, String label) {
    this(id, source, target, label, FlowEdgeType.DEFAULT);
  }

  public FlowEdge {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(source, "source is required");
    Objects.requireNonNull(target, "target is required");
    if (type == null) {
      type = FlowEdgeType.DEFAULT;
    }
  }
}
