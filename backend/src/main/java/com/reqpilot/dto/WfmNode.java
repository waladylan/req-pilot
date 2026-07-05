package com.reqpilot.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record WfmNode(
    @NotBlank String id,
    @NotBlank String kind,
    @NotBlank String label,
    String actor,
    String description,
    Map<String, Object> metadata) {

  public WfmNode {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
