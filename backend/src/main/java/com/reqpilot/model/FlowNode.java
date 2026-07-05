package com.reqpilot.model;

import java.util.Objects;

public record FlowNode(String id, String label, FlowNodeType type) {

  public FlowNode {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(label, "label is required");
    Objects.requireNonNull(type, "type is required");
  }
}
